package dev.bee.beecode.android

import android.app.Application
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import java.io.File

/**
 * The Android entry point.
 *
 * Owns the single [BeeCodeProfile] for the process and the two Android-specific
 * decisions the shared code deliberately does not make: where study state lives,
 * and where the JDBC driver may extract its native library.
 */
class BeeCodeApplication : Application() {

    /** Created on first access so a slow open does not delay process start. */
    val profile: BeeCodeProfile by lazy { openProfile() }

    /** The Problem pack, compiled into assets at build time. */
    val catalogue: ProblemCatalogue by lazy {
        assets.open(PACK_ASSET).bufferedReader().use { ProblemCatalogue.fromPackJson(it.readText()) }
    }

    val runner: ChaquopyPythonRunner by lazy { ChaquopyPythonRunner(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // sqlite-jdbc extracts a bundled native library into java.io.tmpdir, which
        // Android does not set to anything writable by default. Without this, the
        // first database open fails with an UnsatisfiedLinkError — see ADR 0003.
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)
        System.setProperty("org.sqlite.tmpdir", cacheDir.absolutePath)
    }

    private fun openProfile(): BeeCodeProfile {
        // App-private storage. Not external storage: a backup may contain the
        // learner's source, which the plan treats as sensitive.
        val databaseFile = File(filesDir, "beecode.db")
        return BeeCodeProfile.open(
            databasePath = databaseFile.absolutePath,
            catalogue = catalogue,
            runner = runner,
        )
    }

    companion object {
        const val PACK_ASSET = "problems.json"

        /**
         * The process-wide instance.
         *
         * Needed because Chaquopy's `AndroidPlatform` requires a `Context` at
         * interpreter start, and the runner is constructed from places that have no
         * Context to hand. Set in `onCreate`, which Android guarantees runs before
         * any component.
         */
        lateinit var instance: BeeCodeApplication
            private set
    }
}
