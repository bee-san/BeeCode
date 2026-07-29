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

    const val DATABASE_NAME: String = "beecode.db"
}
