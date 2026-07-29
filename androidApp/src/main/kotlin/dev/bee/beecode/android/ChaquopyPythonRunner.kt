package dev.bee.beecode.android

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dev.bee.beecode.python.HarnessProtocol
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunResult
import dev.bee.beecode.python.RunnerCapability
import dev.bee.beecode.python.RunnerProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs learner Python on Android through Chaquopy's embedded CPython.
 *
 * ### The honest capability statement
 *
 * This is [RunnerCapability.IN_PROCESS], and that label is the important part of
 * this class.
 *
 * Chaquopy embeds CPython **in the application process**. There is no separate
 * UID, no process boundary, and no way to `kill(2)` a runaway interpreter. The
 * plan is explicit that same-process execution must not be called a sandbox, so
 * BeeCode does not call it one: the UI shows this capability level verbatim, and
 * the Problem pack is trusted content shipped with the app rather than
 * user-supplied code.
 *
 * ### What termination can and cannot do
 *
 * A Python `while True: pass` holds the GIL and will not yield to a polite
 * request. Chaquopy offers no forcible thread kill — nor could it safely, since
 * killing a thread mid-C-call would corrupt the interpreter.
 *
 * So the deadline is enforced at the **UI boundary** rather than in the
 * interpreter: the coroutine stops waiting, the learner gets a TIMEOUT result and
 * a responsive app, and the orphaned Python thread is abandoned to burn until the
 * OS reclaims the process. That is a real, acknowledged limitation:
 *
 * - the learner's source and review state are never at risk, because the deadline
 *   returns a normal typed result and everything durable is already written;
 * - but an abandoned thread costs battery, and enough of them would degrade the
 *   app until it is restarted.
 *
 * Mitigation: the abandoned thread's executor is discarded and a fresh one created,
 * so later runs are not queued behind a thread that will never finish. See
 * [abandon] — an earlier version got this wrong in a way worth remembering.
 * [abandonedRunCount] lets the UI say the app is degraded rather than just feeling
 * slow.
 *
 * The plan's fallback ladder — an isolated service, a separate no-permission
 * runner APK — remains the route to a genuinely killable boundary. This class is
 * the honest bottom rung, implemented so the product works now while being
 * labelled accurately.
 */
class ChaquopyPythonRunner(
    private val context: Context,
) : PythonRunner {

    override val runnerId: String = "android-chaquopy"

    override val capability: RunnerCapability = RunnerCapability.IN_PROCESS

    /**
     * The executor that enters the interpreter.
     *
     * Single-threaded so runs are serialised — two concurrent runs would contend on
     * the GIL and interleave the stdout capture, and a learner only runs one attempt
     * at a time.
     *
     * It is *replaceable*, and that is the important part. When a run is abandoned
     * after a timeout, its thread is still spinning inside CPython and cannot be
     * killed. Reusing the same single-threaded executor would queue every later run
     * behind that thread forever, so a single infinite loop would permanently break
     * running code. Instead the executor is discarded and a fresh thread is created;
     * the abandoned one is left to burn.
     *
     * This works because CPython releases the GIL periodically even in a tight
     * bytecode loop, so a new thread still makes progress — with contention, which
     * is a real cost but a survivable one.
     */
    private val executor = AtomicReference(newExecutor())

    /**
     * How many threads have been abandoned and are presumed still running.
     *
     * Only ever increases within a process. Surfaced so the UI can be honest that
     * the app is degraded and a restart would help, rather than silently getting
     * slower.
     */
    private val abandonedThreads = AtomicInteger(0)

    /** Number of runs abandoned this process, for the honest degradation warning. */
    val abandonedRunCount: Int get() = abandonedThreads.get()

    /**
     * Whether any run has finished, so the warm-up allowance is spent.
     *
     * Chaquopy's first entry into CPython imports the standard library out of the
     * APK, which on a cold emulator takes tens of seconds. That cost is BeeCode's,
     * not the learner's, so it gets a one-off allowance on top of the Problem's own
     * time limit rather than eating into it.
     */
    @Volatile
    private var hasCompletedARun = false

    override suspend fun probe(): RunnerProbe = withContext(Dispatchers.IO) {
        try {
            ensureStarted()
            val python = Python.getInstance()
            // Ask Python to format its own version rather than indexing
            // version_info from Kotlin: PyObject.get is generic over the key type
            // and integer indices need an explicit cast that reads far worse than
            // this does.
            val version = python.getModule("platform")
                .callAttr("python_version")
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext unavailable("Chaquopy started but reported no Python version.")
            RunnerProbe(
                available = true,
                pythonVersion = version,
                capability = capability,
                unavailableReason = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // An UnsatisfiedLinkError here means the ABI's CPython did not ship or
            // did not load. Reporting it beats crashing at first review.
            unavailable("The embedded Python runtime failed to start: ${e.message ?: e::class.java.simpleName}")
        }
    }

    override suspend fun execute(request: RunRequest): RunResult {
        val startedAt = System.nanoTime()
        fun elapsedMillis(): Long = (System.nanoTime() - startedAt) / 1_000_000

        return withContext(Dispatchers.IO) {
            try {
                ensureStarted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return@withContext HarnessProtocol.workerFailure(
                    request = request,
                    durationMillis = elapsedMillis(),
                    runnerId = runnerId,
                    diagnostic = "The embedded Python runtime failed to start: ${e.message}",
                )
            }

            val encoded = HarnessProtocol.encodeRequest(request)
            val currentExecutor = executor.get()
            val future: Future<String> = currentExecutor.submit<String> { runHarness(encoded) }

            // Wait without blocking this thread, so the deadline and cancellation
            // are both observed. Polling because Future.get(timeout) would block a
            // dispatcher thread and ignore coroutine cancellation.
            //
            // The deadline starts here, after the request is queued, and includes a
            // one-off allowance for interpreter warm-up. Chaquopy's first entry into
            // CPython on a cold emulator takes tens of seconds — importing the
            // standard library from the APK — and charging that to the learner's 5s
            // Problem limit would time out a correct solution on the first run of
            // every session. The allowance applies only until a run has completed;
            // after that the Problem's own limit is the whole budget.
            val warmUpAllowance = if (hasCompletedARun) 0L else WARM_UP_ALLOWANCE_MILLIS
            val deadline = System.nanoTime() +
                (request.limits.wallClockMillis + warmUpAllowance) * 1_000_000
            try {
                while (!future.isDone) {
                    if (System.nanoTime() >= deadline) {
                        abandon(currentExecutor, future)
                        return@withContext HarnessProtocol.timeout(
                            request = request,
                            durationMillis = elapsedMillis(),
                            runnerId = runnerId,
                        )
                    }
                    delay(POLL_INTERVAL_MILLIS)
                }
            } catch (e: CancellationException) {
                // The learner cancelled. Same platform limitation as a timeout.
                withContext(NonCancellable) {
                    if (!future.isDone) abandon(currentExecutor, future) else future.cancel(true)
                }
                throw e
            }

            val stdout = try {
                future.get()
            } catch (e: Throwable) {
                return@withContext HarnessProtocol.workerFailure(
                    request = request,
                    durationMillis = elapsedMillis(),
                    runnerId = runnerId,
                    diagnostic = "The Python worker failed: ${e.cause?.message ?: e.message}",
                )
            }

            val payload = HarnessProtocol.extractPayload(stdout)
                ?: return@withContext HarnessProtocol.workerFailure(
                    request = request,
                    durationMillis = elapsedMillis(),
                    runnerId = runnerId,
                    diagnostic = "The Python worker returned no result.",
                    output = stdout,
                )

            hasCompletedARun = true

            HarnessProtocol.decodeResult(
                payload = payload,
                request = request,
                durationMillis = elapsedMillis(),
                runnerId = runnerId,
                limits = request.limits.maxOutputBytes,
            )
        }
    }

    /**
     * Execute the shared harness inside the embedded interpreter.
     *
     * The *same* harness source both platforms use, driven through a tiny bridge
     * rather than a subprocess. Reusing it verbatim is what makes desktop and
     * Android agree on outcomes: there is one implementation of judging, one
     * traceback format, and one framing.
     */
    private fun runHarness(encodedRequest: String): String {
        val python = Python.getInstance()
        val builtins = python.getBuiltins()
        val globals: PyObject = builtins.callAttr("dict")

        // Compile and run the bridge, which feeds the request in on a fake stdin
        // and returns everything the harness wrote to stdout.
        val bridge = """
            import io, sys

            def __beecode_run(harness_source, request_text):
                real_stdin, real_stdout = sys.stdin, sys.stdout
                sys.stdin = io.StringIO(request_text)
                captured = io.StringIO()
                sys.stdout = captured
                try:
                    namespace = {"__name__": "__beecode_harness__"}
                    exec(compile(harness_source, "<beecode_harness>", "exec"), namespace)
                    namespace["main"]()
                finally:
                    sys.stdin, sys.stdout = real_stdin, real_stdout
                return captured.getvalue()
        """.trimIndent()

        builtins.callAttr("exec", bridge, globals)
        val runner = globals.callAttr("__getitem__", "__beecode_run")
        return runner.call(HarnessProtocol.harnessSource(), encodedRequest).toString()
    }

    /**
     * Give up on a run whose thread cannot be stopped.
     *
     * `Future.cancel(true)` interrupts the thread, which CPython ignores while it
     * holds the GIL in a tight loop. Crucially, the future then reports `isDone`
     * even though the thread is still spinning — so relying on that flag to decide
     * whether the interpreter is free is wrong, and was: it let later runs queue
     * behind a thread that would never finish, and one infinite loop permanently
     * broke running code for the rest of the process.
     *
     * So the executor is *replaced* rather than reused. The abandoned thread keeps
     * burning until the process ends; a fresh thread makes progress alongside it
     * because CPython releases the GIL periodically even mid-loop.
     *
     * `shutdownNow` on the old executor stops it accepting work and interrupts its
     * thread once more. Neither is expected to succeed against a GIL-bound loop;
     * both are correct for the far more common case of code blocked in a sleep or
     * an interruptible call.
     */
    private fun abandon(stale: ExecutorService, future: Future<*>) {
        future.cancel(true)
        // Swap first, so a concurrent execute() cannot pick up the stale executor.
        executor.compareAndSet(stale, newExecutor())
        stale.shutdownNow()
        abandonedThreads.incrementAndGet()
    }

    private fun unavailable(reason: String): RunnerProbe = RunnerProbe(
        available = false,
        pythonVersion = null,
        capability = capability,
        unavailableReason = reason,
    )

    private companion object {
        /** How often the wait yields, so cancellation and the deadline are felt. */
        const val POLL_INTERVAL_MILLIS = 25L

        /**
         * Extra time allowed for the very first run in a process.
         *
         * Generous because it is paid at most once and the alternative is timing out
         * a correct solution. Measured at roughly 29s on a cold x86_64 emulator, so
         * 60s leaves headroom for slower hardware without being unbounded.
         */
        const val WARM_UP_ALLOWANCE_MILLIS = 60_000L

        /**
         * A fresh single-threaded executor for entering the interpreter.
         *
         * Daemon so an abandoned spinning thread cannot keep the JVM alive.
         */
        private fun newExecutor(): ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "beecode-python").apply { isDaemon = true }
            }

        @Volatile
        private var started = false

        /**
         * Start the embedded interpreter once per process.
         *
         * Chaquopy rejects a second `start`, and the runner may be constructed more
         * than once across configuration changes, so this is idempotent and
         * synchronized.
         */
        @Synchronized
        fun ensureStarted() {
            if (started) return
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(BeeCodeApplication.instance))
            }
            started = true
        }
    }
}
