"""BeeCode test harness.

Runs inside the learner's Python interpreter and judges one attempt. Identical on
desktop and Android: only the way the interpreter is started differs, which is
what lets the two platforms agree on review semantics.

Protocol: read one JSON request object from stdin, write one framed response to
stdout, exit 0.

Framing matters more than it looks. Learner code writes freely to the same stream,
so the result must be impossible to confuse with, or forge from, learner output.
Two properties give that:

- The response JSON is **base64-encoded** before it is written. Base64's alphabet
  cannot contain the sentinel, so the payload region is guaranteed sentinel-free
  no matter what the learner printed or what ends up quoted inside the captured
  output field.
- The framed response is the **last** thing written. A reader takes the text after
  the final sentinel, so anything a learner emitted earlier -- even by reaching
  past the capture to sys.__stdout__ -- cannot displace it.

Without the encoding these two rules conflict: a learner who prints the sentinel
has it echoed back inside the captured-output field, which lands *after* the real
sentinel and makes a naive last-occurrence scan read garbage.

The harness is trusted code shipped with BeeCode. The learner's source is not, but
the containment is provided by the process boundary outside this file, not by
anything here. This file does not attempt to sandbox; it attempts to be correct
and to fail in a classified way.
"""

import base64
import io
import json
import math
import sys
import traceback

RESULT_SENTINEL = "__BEECODE_RESULT__"
HARNESS_VERSION = 2

# Outcome kinds. These must match dev.bee.beecode.domain.ExecutionOutcome; a typo
# here would surface as a mystery state in the UI.
PASSED = "PASSED"
FAILED = "FAILED"
SYNTAX_ERROR = "SYNTAX_ERROR"
RUNTIME_ERROR = "RUNTIME_ERROR"

# Bound on any single rendered value in a failure message. A test that returns a
# million-element list should still produce a readable diff.
MAX_VALUE_CHARS = 2000

# Fallback cap on captured learner output when the request does not state one.
DEFAULT_MAX_OUTPUT_CHARS = 65536


class BoundedWriter(io.TextIOBase):
    """Captures at most the last `limit` characters written.

    Learner output must be bounded *here*, not only on the Kotlin side. Two
    reasons, both learned from a failing test:

    - An unbounded StringIO lets `while True: print(x)` exhaust the interpreter's
      memory, so the harness dies before it can report anything.
    - The captured output is embedded in the response, and the response is framed
      at the very end of stdout. An unbounded capture makes the response larger
      than the reader's own buffer, so the frame is evicted and a *passing* run
      is misreported as a worker failure.

    The tail is kept rather than the head: when a program prints in a loop and
    then crashes, the informative part is what it said last.
    """

    def __init__(self, limit):
        super().__init__()
        self._limit = max(1, limit)
        self._chunks = []
        self._length = 0
        self.truncated = False

    def writable(self):
        return True

    def write(self, text):
        if not isinstance(text, str):
            text = str(text)
        self._chunks.append(text)
        self._length += len(text)
        if self._length > self._limit * 2:
            self._compact()
        return len(text)

    def _compact(self):
        joined = "".join(self._chunks)
        if len(joined) > self._limit:
            joined = joined[-self._limit:]
            self.truncated = True
        self._chunks = [joined]
        self._length = len(joined)

    def flush(self):
        pass

    def getvalue(self):
        self._compact()
        return self._chunks[0] if self._chunks else ""

# Relative tolerance for APPROXIMATE_NUMERIC, matching the Kotlin comparator ID.
APPROX_REL_TOL = 1e-9
APPROX_ABS_TOL = 1e-12


def compare(comparator_id, expected, actual):
    """Judge one test. Returns (passed, message_or_None).

    Comparators are selected by ID from this trusted set. Content can choose a
    comparator but can never supply one, so a Problem pack cannot introduce
    executable judge logic.
    """
    if comparator_id == "EXACT":
        return (expected == actual, None)

    if comparator_id == "UNORDERED_LIST":
        if not isinstance(actual, list):
            return (False, "expected a list, got %s" % type(actual).__name__)
        if not isinstance(expected, list):
            return (False, "the problem's expected value is not a list")
        if len(expected) != len(actual):
            return (False, "expected %d items, got %d" % (len(expected), len(actual)))
        # Sort by the JSON rendering so unhashable and mixed-type items still
        # compare. Slower than a set, but total.
        try:
            return (
                sorted(expected, key=_sort_key) == sorted(actual, key=_sort_key),
                None,
            )
        except TypeError as exc:
            return (False, "items are not comparable: %s" % exc)

    if comparator_id == "APPROXIMATE_NUMERIC":
        return _compare_approx(expected, actual)

    if comparator_id == "ANY_OF":
        if not isinstance(expected, list):
            return (False, "ANY_OF requires the expected value to be a list of accepted answers")
        return (any(candidate == actual for candidate in expected), None)

    # An unknown comparator must fail loudly rather than pass by default. A pack
    # built against a newer BeeCode should not silently mark everything correct.
    return (False, "unknown comparator '%s'" % comparator_id)


def _sort_key(value):
    return json.dumps(value, sort_keys=True, default=repr)


def _compare_approx(expected, actual):
    if isinstance(expected, (int, float)) and isinstance(actual, (int, float)):
        if isinstance(expected, bool) != isinstance(actual, bool):
            return (False, "expected %r, got %r" % (expected, actual))
        if math.isnan(expected) and math.isnan(actual):
            return (True, None)
        return (
            math.isclose(expected, actual, rel_tol=APPROX_REL_TOL, abs_tol=APPROX_ABS_TOL),
            None,
        )
    if isinstance(expected, list) and isinstance(actual, list):
        if len(expected) != len(actual):
            return (False, "expected %d items, got %d" % (len(expected), len(actual)))
        for i, (e, a) in enumerate(zip(expected, actual)):
            ok, message = _compare_approx(e, a)
            if not ok:
                return (False, "at index %d: %s" % (i, message or "values differ"))
        return (True, None)
    return (expected == actual, None)


def render(value):
    """Render a value as bounded JSON text for the UI."""
    try:
        text = json.dumps(value, default=repr)
    except (TypeError, ValueError):
        text = repr(value)
    if len(text) > MAX_VALUE_CHARS:
        return text[:MAX_VALUE_CHARS] + "... (truncated)"
    return text


def format_user_traceback(exc_info):
    """Format a traceback with harness frames removed.

    The learner should see their own code, not our machinery. Keeping harness
    frames would bury the one line that matters under BeeCode internals.
    """
    exc_type, exc_value, exc_tb = exc_info
    frames = traceback.extract_tb(exc_tb)
    user_frames = [f for f in frames if f.filename == "<solution>"]
    parts = ["Traceback (most recent call last):"]
    for frame in user_frames:
        parts.append('  File "%s", line %d, in %s' % (frame.filename, frame.lineno, frame.name))
        if frame.line:
            parts.append("    %s" % frame.line.strip())
    parts.extend(traceback.format_exception_only(exc_type, exc_value))
    return "".join(p if p.endswith("\n") else p + "\n" for p in parts).rstrip()


def diagnostic(message, start=None, end=None):
    """Build the v2 typed diagnostic value."""
    value = {"message": message}
    if start is not None:
        source_range = {"start": start}
        if end is not None:
            source_range["end"] = end
        value["sourceRange"] = source_range
    return value


def position(line, column=None):
    value = {"line": max(1, int(line))}
    if column is not None:
        value["column"] = max(1, int(column))
    return value


def traceback_diagnostic(exc_info):
    """Format an exception and locate its innermost learner frame."""
    frames = traceback.extract_tb(exc_info[2])
    user_frames = [frame for frame in frames if frame.filename == "<solution>"]
    frame = user_frames[-1] if user_frames else None
    start = position(frame.lineno) if frame is not None else None
    return diagnostic(format_user_traceback(exc_info), start=start)


def run(request):
    source = request["source"]
    entry_point = request["entryPoint"]
    tests = request["tests"]

    # Compile first so a syntax error is reported as exactly that, before any
    # test is attributed a failure. The learner made a typo; they did not get the
    # algorithm wrong.
    try:
        code = compile(source, "<solution>", "exec")
    except SyntaxError as exc:
        start = position(exc.lineno or 1, exc.offset)
        # end_lineno/end_offset arrived after Python 3.9. Desktop uses the
        # learner's installed Python, so the protocol cannot assume they exist.
        end_line = getattr(exc, "end_lineno", None)
        end_offset = getattr(exc, "end_offset", None)
        end = position(end_line, end_offset) if end_line is not None else None
        return {
            "outcome": SYNTAX_ERROR,
            "testResults": [],
            "diagnostic": diagnostic(
                "%s (line %s)" % (exc.msg, exc.lineno),
                start=start,
                end=end,
            ),
        }

    namespace = {"__name__": "__solution__"}
    try:
        exec(code, namespace)
    except BaseException:
        # Module-level code raised: a bad import, a typo at top level.
        exc_info = sys.exc_info()
        return {
            "outcome": RUNTIME_ERROR,
            "testResults": [],
            "diagnostic": traceback_diagnostic(exc_info),
        }

    function = namespace.get(entry_point)
    if function is None:
        # Overwhelmingly the most common real failure: the learner renamed or
        # deleted the required function. Say so plainly.
        return {
            "outcome": RUNTIME_ERROR,
            "testResults": [],
            "diagnostic": diagnostic(
                "No function named '%s' was defined. BeeCode calls '%s' to test "
                "your solution, so it must exist with that exact name." % (entry_point, entry_point)
            ),
        }
    if not callable(function):
        return {
            "outcome": RUNTIME_ERROR,
            "testResults": [],
            "diagnostic": diagnostic("'%s' is defined but is not a function." % entry_point),
        }

    results = []
    fatal = None
    fatal_diagnostic = None
    for test in tests:
        args = json.loads(test["argumentsJson"])
        expected = json.loads(test["expectedJson"])
        hidden = bool(test.get("hidden", False))
        started = _now_ms()
        try:
            actual = function(*args)
            passed, message = compare(test["comparatorId"], expected, actual)
            rendered_actual = render(actual)
        except BaseException:
            # One test raising does not stop the rest: the learner learns more
            # from "3 of 5 passed" than from the first exception alone.
            passed = False
            exc_info = sys.exc_info()
            message = format_user_traceback(exc_info)
            rendered_actual = None
            if fatal is None:
                fatal = message
                fatal_diagnostic = traceback_diagnostic(exc_info)
        duration = _now_ms() - started

        if not passed and message is None:
            message = "expected %s but got %s" % (render(expected), rendered_actual)

        results.append(
            {
                "name": test["name"],
                "passed": passed,
                "hidden": hidden,
                # A hidden test withholds its values so the Problem cannot be
                # solved by reading the assertions, but it still reports whether
                # it passed.
                "expectedJson": None if hidden else render(expected),
                "actualJson": None if hidden else rendered_actual,
                "message": None if (hidden and not passed) else message,
                "durationMillis": duration,
            }
        )

    if all(r["passed"] for r in results):
        outcome = PASSED
    elif fatal is not None and not any(r["passed"] for r in results):
        # Everything raised: this is a broken solution, not a set of wrong
        # answers, and RUNTIME_ERROR points the learner at the traceback.
        outcome = RUNTIME_ERROR
    else:
        outcome = FAILED

    return {
        "outcome": outcome,
        "testResults": results,
        "diagnostic": fatal_diagnostic if outcome == RUNTIME_ERROR else None,
    }


def _now_ms():
    import time

    return int(time.monotonic() * 1000)


def main():
    raw = sys.stdin.read()
    # Learner output and the harness result share one stream, so capture stdout
    # while the learner's code runs and emit the result after a sentinel line.
    # The capture is bounded so runaway printing can neither exhaust memory nor
    # push the framed result out of the reader's buffer.
    captured = BoundedWriter(DEFAULT_MAX_OUTPUT_CHARS)
    real_stdout = sys.stdout
    real_stderr = sys.stderr
    try:
        request = json.loads(raw)
        captured = BoundedWriter(int(request.get("maxOutputChars") or DEFAULT_MAX_OUTPUT_CHARS))
        if request.get("harnessVersion") != HARNESS_VERSION:
            response = {
                "outcome": RUNTIME_ERROR,
                "testResults": [],
                "diagnostic": diagnostic(
                    "Harness version mismatch: BeeCode sent %r, this harness is %d."
                    % (request.get("harnessVersion"), HARNESS_VERSION)
                ),
            }
        else:
            sys.stdout = captured
            sys.stderr = captured
            try:
                response = run(request)
            finally:
                sys.stdout = real_stdout
                sys.stderr = real_stderr
        response["output"] = captured.getvalue()
        response["outputTruncated"] = captured.truncated
        response["pythonVersion"] = "%d.%d.%d" % sys.version_info[:3]
    except BaseException:
        sys.stdout = real_stdout
        sys.stderr = real_stderr
        # The harness itself failed. Report it as such rather than blaming the
        # learner's code: the Kotlin side maps this to WORKER_FAILURE, which the
        # domain refuses to record as a wrong answer.
        response = {
            "outcome": "HARNESS_ERROR",
            "testResults": [],
            "diagnostic": diagnostic(traceback.format_exc()),
            "output": captured.getvalue(),
            "pythonVersion": "%d.%d.%d" % sys.version_info[:3],
        }

    # Base64 so the payload region cannot contain the sentinel, even when the
    # learner's own output is quoted inside it.
    encoded = base64.b64encode(json.dumps(response).encode("utf-8")).decode("ascii")
    real_stdout.write("\n" + RESULT_SENTINEL + "\n")
    real_stdout.write(encoded)
    real_stdout.write("\n")
    real_stdout.flush()


if __name__ == "__main__":
    main()
