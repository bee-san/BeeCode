package dev.bee.beecode.desktop

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Hands the WebDAV credential to the operating system's own secret service.
 *
 * The desktop client stored that password as plaintext in the profile database, and the
 * reason given for it was that no cross-platform JVM keystore exists which is not either a
 * large dependency or a keystore protected by a password stored beside it. That reasoning
 * is sound and it is also about the wrong kind of thing: it is about *libraries*. macOS and
 * most Linux desktops already run a secret service, and each ships a command-line client
 * for it. BeeCode already spawns child processes to run Python, so reaching one costs no
 * new dependency at all.
 *
 * ### Why this is stronger than encrypting in place
 *
 * Encrypting the credential inside the profile with a key that also lives on the machine
 * only moves the problem. This does not encrypt the credential — it *removes* it. What
 * stays in the database is [DELEGATED], a fixed marker with no secret in it, so the
 * remaining exposure the desktop UI had to warn about is gone: a copied profile, a backup,
 * or a synced snapshot now carries nothing to recover. The secret lives where the OS
 * already guards the learner's other passwords, encrypted at rest under their login.
 *
 * ### What it does not defend against
 *
 * Nothing here stops the learner's *own* account from reading the secret — `secret-tool
 * lookup` run by that user returns it, by design, because that is the same account the app
 * runs as. The threat this closes is the credential travelling in a file: a backup, a
 * snapshot, another user on a shared machine. Malware already running as the learner is out
 * of scope for every keychain, including Android's.
 *
 * ### Why failure falls back rather than blocking
 *
 * No secret service is *guaranteed*: a headless Linux box typically has none, Windows has
 * no equivalent CLI here, and a locked keyring can refuse. Every entry point reports
 * failure instead of throwing, and the caller keeps the previous plaintext behaviour, so a
 * learner never loses sync to a hardening measure. The UI then states which of the two
 * actually happened rather than making one promise for both — see [isAvailable].
 *
 * ### Why storing is verified by reading back
 *
 * [save] stores, then looks the value up and compares. A CLI that exits 0 without
 * persisting anything — a keyring that accepted the write into a session collection about
 * to disappear, say — would otherwise leave the credential silently unrecoverable *and*
 * absent from the database, which is worse than plaintext. Confirming the round trip means
 * [save] returns [DELEGATED] only when the value can genuinely be read back.
 */
internal class OsSecretStore(
    private val backend: Backend? = detectBackend(),
    private val runner: CommandRunner = ProcessCommandRunner,
) {

    /**
     * Whether a secret service looks usable, for choosing what the UI promises.
     *
     * Deliberately cheap and deliberately not authoritative: it checks that a backend was
     * detected and its client is on `PATH`, not that a keyring is unlocked. Settings
     * renders on the UI thread, and spawning a process for a round-trip probe on every
     * recomposition to answer a question that [save] answers for real is the wrong trade.
     */
    fun isAvailable(): Boolean = backend != null && backend.executablePath() != null

    /** A short name for the backend, so the UI can say where the password went. */
    fun backendName(): String? = backend?.displayName

    /**
     * Move [password] into the OS secret service.
     *
     * @return [DELEGATED] to store in the profile in place of the password, or null if the
     *   secret service could not take it — in which case the caller stores the password as
     *   before, because losing sync is worse than storing it the way it already was.
     */
    fun save(password: String): String? {
        val backend = backend ?: return null
        val stored = runCatching { backend.store(runner, password) }.getOrDefault(false)
        if (!stored) return null
        // Confirm it can be read back before telling the caller to drop the plaintext.
        return if (lookup() == password) DELEGATED else null
    }

    /**
     * Turn what is in the profile back into a usable password.
     *
     * A value that is not [DELEGATED] is returned unchanged. That is a plaintext credential
     * from before this existed, or from a machine with no secret service, and returning it
     * as-is is what makes this fix existing installs rather than only new ones.
     *
     * Null means the marker was present but the secret is gone — the learner cleared their
     * keyring, or moved the profile to another machine. Being asked for the password again
     * is the correct outcome there, and it is exactly why the marker carries no secret.
     */
    fun resolve(stored: String): String? = if (stored == DELEGATED) lookup() else stored

    /** Remove the stored secret, for when the learner clears the password. */
    fun clear() {
        val backend = backend ?: return
        runCatching { backend.clear(runner) }
    }

    private fun lookup(): String? =
        backend?.let { runCatching { it.lookup(runner) }.getOrNull() }

    /**
     * One operating system's secret-service client.
     *
     * The executable is a constructor parameter rather than a hardcoded name so a test can
     * point a backend at a stand-in that implements the same command contract. That is what
     * makes this testable on a host with no secret service of its own — which is every host
     * this was written on.
     */
    internal sealed class Backend(val displayName: String, val executable: String) {

        abstract fun store(runner: CommandRunner, password: String): Boolean

        abstract fun lookup(runner: CommandRunner): String?

        abstract fun clear(runner: CommandRunner)

        /** The resolved executable, or null if it is not on `PATH`. */
        fun executablePath(): String? {
            if (executable.contains(File.separatorChar)) {
                return executable.takeIf { File(it).canExecute() }
            }
            val path = System.getenv("PATH") ?: return null
            return path.split(File.pathSeparatorChar)
                .firstOrNull { directory -> File(directory, executable).canExecute() }
                ?.let { File(it, executable).path }
        }

        /**
         * The Secret Service API, spoken through `secret-tool`.
         *
         * `secret-tool store` reads the secret from **stdin**, so the password never appears
         * in a command line and never reaches `/proc/<pid>/cmdline`, where any process of
         * the same user could read it while the write was in flight.
         */
        class SecretService(executable: String = "secret-tool") :
            Backend("your desktop keyring", executable) {

            override fun store(runner: CommandRunner, password: String): Boolean =
                runner.run(
                    listOf(executable, "store", "--label=$LABEL") + ATTRIBUTES,
                    stdin = password,
                ).exitCode == 0

            override fun lookup(runner: CommandRunner): String? {
                val result = runner.run(listOf(executable, "lookup") + ATTRIBUTES, stdin = null)
                if (result.exitCode != 0) return null
                // secret-tool writes the secret with no trailing newline, but a stand-in or a
                // future version may add one, and a password with a stray newline would fail
                // to authenticate in a way that looks like a wrong password.
                return result.stdout.removeSuffix("\n").takeIf { it.isNotEmpty() }
            }

            override fun clear(runner: CommandRunner) {
                runner.run(listOf(executable, "clear") + ATTRIBUTES, stdin = null)
            }
        }

        /**
         * The macOS Keychain, through `security`.
         *
         * `-U` updates an existing item rather than failing with a duplicate, which is what
         * changing a password must do.
         *
         * Unlike the `secret-tool` path, the password goes on the argument list: `security`
         * only reads it interactively otherwise, and its `-i` mode would mean quoting the
         * password into a command string, which breaks on the punctuation a good password
         * has. `ProcessBuilder` passes the arguments directly with no shell, so nothing is
         * logged to a history file — but the arguments are briefly visible to the learner's
         * own account and to root. Both can already read the keychain as that user, so this
         * exposes nothing that was not already reachable, and it is a real difference from
         * the Linux path rather than one worth papering over.
         */
        class MacKeychain(executable: String = "security") :
            Backend("your macOS Keychain", executable) {

            override fun store(runner: CommandRunner, password: String): Boolean =
                runner.run(
                    listOf(
                        executable, "add-generic-password",
                        "-a", ACCOUNT, "-s", SERVICE, "-U", "-w", password,
                    ),
                    stdin = null,
                ).exitCode == 0

            override fun lookup(runner: CommandRunner): String? {
                val result = runner.run(
                    listOf(executable, "find-generic-password", "-a", ACCOUNT, "-s", SERVICE, "-w"),
                    stdin = null,
                )
                if (result.exitCode != 0) return null
                return result.stdout.removeSuffix("\n").takeIf { it.isNotEmpty() }
            }

            override fun clear(runner: CommandRunner) {
                runner.run(
                    listOf(executable, "delete-generic-password", "-a", ACCOUNT, "-s", SERVICE),
                    stdin = null,
                )
            }
        }
    }

    /** How a command gets run, so tests can observe the arguments without a real keyring. */
    internal fun interface CommandRunner {
        fun run(command: List<String>, stdin: String?): CommandResult
    }

    internal data class CommandResult(val exitCode: Int, val stdout: String)

    /**
     * Runs the client as a child process.
     *
     * Bounded by a timeout because a locked keyring can put up a dialog, and a Settings
     * screen frozen on an invisible prompt is indistinguishable from a hung app. The
     * timeout is generous enough for a learner to actually answer such a prompt; expiring
     * kills the child and reports failure, which falls back to the previous behaviour.
     */
    private object ProcessCommandRunner : CommandRunner {
        override fun run(command: List<String>, stdin: String?): CommandResult {
            val process = try {
                ProcessBuilder(command).redirectErrorStream(false).start()
            } catch (e: IOException) {
                // The client is not installed. Ordinary on a headless box, not an error.
                return CommandResult(exitCode = -1, stdout = "")
            }
            return try {
                process.outputStream.use { output ->
                    if (stdin != null) output.write(stdin.encodeToByteArray())
                }
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                // Drained so a client that writes a diagnostic cannot block on a full pipe.
                process.errorStream.use { it.readBytes() }
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return CommandResult(exitCode = -1, stdout = "")
                }
                CommandResult(exitCode = process.exitValue(), stdout = stdout)
            } catch (e: IOException) {
                process.destroyForcibly()
                CommandResult(exitCode = -1, stdout = "")
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    internal companion object {
        /**
         * What sits in the profile in place of the password.
         *
         * A fixed marker rather than a column or a schema change: the value has to be
         * distinguishable from a legacy plaintext credential, and a migration that can
         * half-run over a credential is worse than a sentinel that cannot. Versioned so a
         * later scheme can be told apart from this one.
         *
         * It is not a plausible password — anything a learner could realistically type must
         * not be mistaken for the marker.
         */
        const val DELEGATED: String = "beecode-os-secret-v1"

        private const val LABEL = "BeeCode WebDAV password"
        private const val SERVICE = "dev.bee.beecode.webdav"
        private const val ACCOUNT = "beecode-sync"
        private val ATTRIBUTES = listOf("service", SERVICE, "account", ACCOUNT)
        private const val TIMEOUT_SECONDS = 20L

        /**
         * Pick a backend for this operating system, or null where there is none to pick.
         *
         * Windows returns null. It has a credential store, but no shipped command-line
         * client that can put a secret in and read it back, and the DPAPI route needs
         * PowerShell — a different mechanism, with the ciphertext staying in the profile,
         * which is the weaker "encrypt in place" this deliberately avoids. Claiming a
         * protection there that was never tested would be worse than the honest fallback.
         */
        fun detectBackend(): Backend? {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                os.contains("mac") -> Backend.MacKeychain()
                os.contains("win") -> null
                else -> Backend.SecretService()
            }
        }
    }
}
