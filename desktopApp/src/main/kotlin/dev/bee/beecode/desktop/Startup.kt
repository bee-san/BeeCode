package dev.bee.beecode.desktop

import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.python.jvm.ProcessPythonRunner
import java.io.File

/**
 * Everything the desktop client does before a window exists.
 *
 * Extracted from `main` so it can be exercised without a display. The UI needs X11
 * or equivalent; opening the profile, reading the packaged Problem pack, and
 * resolving the learner's chosen interpreter do not — and those are the parts that
 * can actually be misconfigured.
 */
internal object Startup {

    /**
     * Open the learner's profile from the platform data directory.
     *
     * Two-phase because the interpreter the learner chose is itself stored in the
     * profile: open once to read the setting, and if a custom path is configured,
     * reopen with a runner bound to it. Reopening rather than mutating keeps the
     * runner immutable for the profile's lifetime, which is what makes the runner
     * identity recorded on each run trustworthy.
     */
    fun openProfile(dataDirectory: File = profileDirectory()): BeeCodeProfile {
        dataDirectory.mkdirs()
        restrictToOwner(dataDirectory)
        val catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE)
        val databaseFile = File(dataDirectory, DATABASE_NAME)

        val bootstrap = BeeCodeProfile.open(
            databasePath = databaseFile.absolutePath,
            catalogue = catalogue,
            runner = ProcessPythonRunner(),
        )
        val chosen = bootstrap.settings.pythonExecutable() ?: return bootstrap

        bootstrap.close()
        return BeeCodeProfile.open(
            databasePath = databaseFile.absolutePath,
            catalogue = catalogue,
            runner = ProcessPythonRunner(pythonExecutable = chosen),
        )
    }

    /**
     * Make [path] readable only by the user who owns it.
     *
     * `mkdirs` uses the process umask, which is `022` on a typical Linux or macOS account —
     * so the profile directory is created `0755` and **any other user on the machine can read
     * it**. That directory holds every solution the learner has written and, on desktop, the
     * WebDAV password in the clear. On a shared machine or a multi-user server that is a real
     * exposure, and it is invisible: nothing about the app suggests the files are readable.
     *
     * Android needs no equivalent — app-private storage is already per-UID — which is why
     * this lives in `:desktopApp` rather than in the shared layer.
     *
     * Best-effort by design. A `FileSystem` without POSIX permissions (Windows, some network
     * mounts) throws `UnsupportedOperationException`, and on Windows the equivalent ACL work
     * is a different API with different semantics. Failing to tighten permissions must not
     * stop a learner opening their profile, so this reports nothing and changes nothing on
     * platforms it cannot help.
     *
     * @return true if the permissions were applied, for the test to distinguish "did it" from
     *   "silently could not".
     */
    internal fun restrictToOwner(path: File): Boolean = runCatching {
        java.nio.file.Files.setPosixFilePermissions(
            path.toPath(),
            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"),
        )
        true
    }.getOrDefault(false)

    const val DATABASE_NAME: String = "beecode.db"
}
