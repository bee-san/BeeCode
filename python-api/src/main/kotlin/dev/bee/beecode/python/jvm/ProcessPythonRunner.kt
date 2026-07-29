package dev.bee.beecode.python.jvm

import dev.bee.beecode.python.HarnessProtocol
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunResult
import dev.bee.beecode.python.RunnerCapability
import dev.bee.beecode.python.RunnerProbe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs learner Python in a disposable child process.
 *
 * Used by the desktop client, and by JVM tests as the reference implementation
 * that Android's runner must agree with.
 *
 * The topology is deliberately simple: one fresh `python3` process per run, in a
 * fresh temporary working directory, with a clean environment. Nothing is
 * reused between runs, so state cannot leak from one attempt to the next and a
 * wedged interpreter cannot poison the following review.
 *
 * ### What this does and does not contain
 *
 * It reliably stops honest mistakes: an infinite loop is killed at the deadline,
 * runaway output is bounded, and the process tree is destroyed rather than
 * orphaned. It does **not** contain hostile code — the child runs with the
 * user's own privileges and can read their files and open sockets. The capability
 * is reported as [RunnerCapability.SEPARATE_PROCESS] so the UI can say so
 * plainly.
 *
 * ### Why the source goes in a file rather than `-c`
 *
 * Learner source is passed to the harness as JSON on stdin, and the harness is
 * written to a temporary file. Neither the source nor anything derived from it is
 * ever interpolated into a command line, so there is no shell to quote for and no
 * argument-length limit to hit.
 */
class ProcessPythonRunner(
    private val pythonExecutable: String = defaultPythonExecutable(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PythonRunner {

    override val runnerId: String = "desktop-process"

    override val capability: RunnerCapability = RunnerCapability.SEPARATE_PROCESS

    override suspend fun probe(): RunnerProbe = withContext(dispatcher) {
        try {
            val process = ProcessBuilder(pythonExecutable, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext unavailable(
                    "'$pythonExecutable --version' did not respond within " +
                        "$PROBE_TIMEOUT_SECONDS seconds.",
                )
            }
            if (process.exitValue() != 0) {
                return@withContext unavailable(
                    "'$pythonExecutable --version' exited with ${process.exitValue()}.",
                )
            }
            val version = PYTHON_VERSION_PATTERN.find(output)?.groupValues?.get(1)
                ?: return@withContext unavailable(
                    "Could not read a version from '$pythonExecutable --version': ${output.trim()}",
                )
            RunnerProbe(
                available = true,
                pythonVersion = version,
                capability = capability,
                unavailableReason = null,
            )
        } catch (e: IOException) {
            // The overwhelmingly common case: Python is simply not installed, or
            // not on PATH. This is a normal condition to report, not a crash.
            unavailable(
                "Python 3 was not found. BeeCode looked for '$pythonExecutable'. " +
                    "Install Python 3 or set its path in Settings. (${e.message})",
            )
        }
    }

    override suspend fun execute(request: RunRequest): RunResult = withContext(dispatcher) {
        val startedAt = System.nanoTime()
        fun elapsedMillis(): Long = (System.nanoTime() - startedAt) / 1_000_000

        // A fresh workspace per run. The child's working directory is here, so
        // code that writes a file cannot touch the learner's real files by
        // accident or reach BeeCode's own database.
        val workspace = try {
            Files.createTempDirectory("beecode-run-").toFile()
        } catch (e: IOException) {
            return@withContext HarnessProtocol.workerFailure(
                request = request,
                durationMillis = elapsedMillis(),
                runnerId = runnerId,
                diagnostic = "Could not create a temporary workspace: ${e.message}",
            )
        }

        var process: Process? = null
        try {
            val harnessFile = File(workspace, "beecode_harness.py")
            harnessFile.writeText(HarnessProtocol.harnessSource())

            process = ProcessBuilder(
                pythonExecutable,
                // Unbuffered, so output captured from a killed process is not
                // lost in a buffer that never flushed.
                "-u",
                // Ignore the user's PYTHON* environment and any local sitecustomize,
                // so a learner's global setup cannot change how BeeCode judges.
                "-I",
                harnessFile.absolutePath,
            )
                .directory(workspace)
                .redirectErrorStream(true)
                .apply { environment().clean() }
                .start()

            val stdin = process.outputStream
            val encoded = HarnessProtocol.encodeRequest(request)
            // Written on this thread and closed immediately: the harness reads
            // stdin to EOF, so failing to close would deadlock both sides.
            try {
                stdin.write(encoded.encodeToByteArray())
                stdin.flush()
            } finally {
                runCatching { stdin.close() }
            }

            // Read concurrently with waiting. A child that fills the pipe buffer
            // blocks forever if nobody drains it, which would turn a chatty
            // solution into a hang the deadline could not explain.
            val reader = OutputCollector(process.inputStream, request.limits.maxOutputBytes)
            val readerThread = Thread(reader, "beecode-run-output").apply {
                isDaemon = true
                start()
            }

            val finished = awaitExit(process, request.limits.wallClockMillis)
            if (!finished) {
                destroyTree(process)
                readerThread.join(READER_JOIN_MILLIS)
                return@withContext HarnessProtocol.timeout(
                    request = request,
                    durationMillis = elapsedMillis(),
                    runnerId = runnerId,
                    output = reader.text(),
                )
            }

            readerThread.join(READER_JOIN_MILLIS)
            val stdout = reader.text()
            val duration = elapsedMillis()

            val payload = HarnessProtocol.extractPayload(stdout)
                ?: return@withContext HarnessProtocol.workerFailure(
                    request = request,
                    durationMillis = duration,
                    runnerId = runnerId,
                    // No frame means the interpreter died before the harness could
                    // report: a segfault, an OOM kill, or a hard exit. That is not
                    // the learner getting the answer wrong.
                    diagnostic = buildString {
                        append("The Python worker exited with code ${process.exitValue()} ")
                        append("without returning a result.")
                        if (reader.truncated) append(" Its output was truncated.")
                    },
                    output = stdout,
                )

            HarnessProtocol.decodeResult(
                payload = payload,
                request = request,
                durationMillis = duration,
                runnerId = runnerId,
                limits = request.limits.maxOutputBytes,
            )
        } catch (e: IOException) {
            HarnessProtocol.workerFailure(
                request = request,
                durationMillis = elapsedMillis(),
                runnerId = runnerId,
                diagnostic = "The Python worker could not be started: ${e.message}",
            )
        } finally {
            // Cancellation arrives as a thread interrupt or coroutine
            // cancellation. Either way the child must die and the workspace must
            // go, so cleanup runs in NonCancellable: a cancelled coroutine that
            // leaves an orphaned Python process is exactly the bug this class
            // exists to prevent.
            withContext(NonCancellable) {
                process?.let { if (it.isAlive) destroyTree(it) }
                workspace.deleteRecursively()
            }
        }
    }

    /**
     * Wait for the child, remaining responsive to coroutine cancellation.
     *
     * A single blocking `Process.waitFor(timeout)` looks correct and is not:
     * `Dispatchers.IO` does not interrupt the underlying thread when the coroutine
     * is cancelled, so the wait runs to completion and the child keeps burning CPU
     * until its own deadline. Cancelling a run would then leak a Python process —
     * exactly what this class exists to prevent.
     *
     * Polling in short slices lets `delay` observe cancellation, which propagates
     * out and lets the `finally` block destroy the process tree.
     *
     * @return true if the process exited, false if the deadline elapsed first.
     */
    private suspend fun awaitExit(process: Process, timeoutMillis: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (true) {
            if (!process.isAlive) return true
            if (System.nanoTime() >= deadline) return !process.isAlive
            // A cancellation point. Kept short so cancellation is felt promptly,
            // but not so short that polling costs measurable CPU.
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private fun unavailable(reason: String): RunnerProbe = RunnerProbe(
        available = false,
        pythonVersion = null,
        capability = capability,
        unavailableReason = reason,
    )

    companion object {
        private const val PROBE_TIMEOUT_SECONDS = 10L

        /** Grace period for the output reader to finish after the child exits. */
        private const val READER_JOIN_MILLIS = 2_000L

        private const val DESTROY_GRACE_MILLIS = 500L

        /** How often the exit wait yields, so cancellation is observed promptly. */
        private const val POLL_INTERVAL_MILLIS = 25L

        private val PYTHON_VERSION_PATTERN = Regex("""Python (\d+\.\d+\.\d+)""")

        fun defaultPythonExecutable(): String =
            if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "python" else "python3"

        /**
         * Kill the child and everything it spawned.
         *
         * A learner using `multiprocessing` leaves grandchildren that keep running
         * — and keep holding the pipe open — after the parent dies. Destroying
         * descendants first, then the parent, is what makes a timeout actually
         * stop the work rather than just stop the waiting.
         */
        private fun destroyTree(process: Process) {
            val descendants = runCatching { process.descendants().toList() }.getOrDefault(emptyList())
            descendants.forEach { runCatching { it.destroy() } }
            process.destroy()
            if (!process.waitFor(DESTROY_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                descendants.forEach { runCatching { it.destroyForcibly() } }
                process.destroyForcibly()
            }
            // Anything still alive after SIGTERM gets SIGKILL. Python code in a
            // tight C loop can ignore the polite signal entirely.
            descendants.filter { it.isAlive }.forEach { runCatching { it.destroyForcibly() } }
        }

        /**
         * Strip the inherited environment.
         *
         * The worker has no business seeing the learner's shell configuration, and
         * BeeCode has no business leaking anything to it. PATH is retained
         * because resolving a bare `python3` needs it; a locale is forced so
         * output encoding is stable across machines.
         */
        private fun MutableMap<String, String>.clean() {
            val path = this["PATH"] ?: this["Path"]
            val systemRoot = this["SystemRoot"]
            clear()
            if (path != null) this["PATH"] = path
            // Windows breaks without SystemRoot on PATH-resolved executables.
            if (systemRoot != null) this["SystemRoot"] = systemRoot
            this["PYTHONIOENCODING"] = "utf-8"
            this["PYTHONDONTWRITEBYTECODE"] = "1"
            this["LC_ALL"] = "C.UTF-8"
        }
    }
}

/**
 * Drains a stream on a dedicated thread, keeping at most a bounded tail.
 *
 * Bounded on purpose: a `while True: print(x)` produces gigabytes, and buffering
 * it all would take down the UI process with an OutOfMemoryError while trying to
 * report that the learner's code misbehaved. Keeping the tail matches
 * [HarnessProtocol.truncateOutput] — the interesting part of runaway output is
 * what it said last.
 *
 * The cap is generous relative to the Problem's own output limit because the
 * harness's framed result also arrives on this stream and must not be evicted by
 * learner noise.
 */
private class OutputCollector(
    private val stream: java.io.InputStream,
    outputLimitBytes: Int,
) : Runnable {
    private val capacity = outputLimitBytes.toLong() * TAIL_CAPACITY_MULTIPLIER
    private val builder = StringBuilder()

    @Volatile
    var truncated: Boolean = false
        private set

    override fun run() {
        try {
            stream.bufferedReader().use { reader ->
                val chunk = CharArray(CHUNK_CHARS)
                while (true) {
                    val read = reader.read(chunk)
                    if (read < 0) break
                    synchronized(builder) {
                        builder.appendRange(chunk, 0, read)
                        if (builder.length > capacity) {
                            builder.delete(0, builder.length - capacity.toInt())
                            truncated = true
                        }
                    }
                }
            }
        } catch (_: IOException) {
            // Expected when the process is destroyed mid-read. Whatever was
            // already collected is still the best evidence available.
        }
    }

    fun text(): String = synchronized(builder) { builder.toString() }

    private companion object {
        const val CHUNK_CHARS = 8_192

        /**
         * Headroom over the Problem's output limit so the framed result survives
         * even when learner output would otherwise fill the buffer.
         */
        const val TAIL_CAPACITY_MULTIPLIER = 4L
    }
}
