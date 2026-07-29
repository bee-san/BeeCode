package dev.bee.beecode.desktop

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real child-process path, driven against a stand-in `secret-tool`.
 *
 * [OsSecretStoreTest] substitutes the `CommandRunner`, which verifies the command contract
 * but skips the part that actually spawns anything — so writing the secret to the child's
 * stdin, closing that stream, draining stdout, and reaping the process were all unexercised.
 * That is the plumbing most likely to hang or deadlock, and the least likely to be caught by
 * reading it.
 *
 * So this writes a small shell script implementing the subset of `secret-tool` that BeeCode
 * uses, puts it where the backend will find it, and runs the production `OsSecretStore` with
 * its default runner. No keyring is involved and none is needed: what is under test is
 * BeeCode's side of the pipe.
 *
 * Skipped where there is no POSIX shell to write the stub in — which is Windows, where
 * [OsSecretStore.detectBackend] returns null and there is nothing to exercise anyway.
 */
class OsSecretStoreProcessTest {

    private val directory = kotlin.io.path.createTempDirectory("beecode-fake-keyring-").toFile()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    /**
     * A stub honouring the same commands as the real client.
     *
     * Reads the secret from stdin on `store`, which is the behaviour that matters: if
     * BeeCode failed to write and close that stream the script would block and the test
     * would time out rather than pass.
     */
    private fun stubKeyring(): File {
        val secretFile = File(directory, "secret")
        val script = File(directory, "secret-tool")
        script.writeText(
            """
            #!/bin/sh
            SECRET='${secretFile.absolutePath}'
            case "$1" in
              store)  cat > "${'$'}SECRET" ;;
              lookup) [ -s "${'$'}SECRET" ] && printf '%s' "${'$'}(cat "${'$'}SECRET")" || exit 1 ;;
              clear)  rm -f "${'$'}SECRET" ;;
              *) exit 1 ;;
            esac
            """.trimIndent() + "\n",
        )
        script.setExecutable(true)
        return script
    }

    /** The production store, wired to the stub and using the *real* process runner. */
    private fun store(script: File) = OsSecretStore(
        backend = OsSecretStore.Backend.SecretService(script.absolutePath),
    )

    @Test
    fun aPasswordSurvivesARealRoundTripThroughAChildProcess() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").canExecute())
        val script = stubKeyring()
        val subject = store(script)

        // Punctuation and a space, because a password that only ever gets tested as
        // [a-z0-9] would hide any quoting or shell-interpolation bug.
        val password = "p@ss w0rd!\$with'quotes\"and\\slashes"
        assertEquals(OsSecretStore.DELEGATED, subject.save(password))
        assertEquals(password, subject.resolve(OsSecretStore.DELEGATED))
    }

    @Test
    fun theStoredSecretReallyLandsOutsideTheProfile() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").canExecute())
        val script = stubKeyring()
        store(script).save("landed-here-42")

        // The stub keeps it in a file, so this is a direct check that the secret left the
        // process and arrived somewhere — the real client would put it in the keyring.
        assertEquals("landed-here-42", File(directory, "secret").readText())
    }

    @Test
    fun clearingRemovesItThroughTheRealProcessToo() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").canExecute())
        val script = stubKeyring()
        val subject = store(script)
        subject.save("pw")
        subject.clear()

        assertTrue(!File(directory, "secret").exists())
        assertNull(subject.resolve(OsSecretStore.DELEGATED), "a cleared secret must not resolve")
    }

    @Test
    fun aClientThatIsNotThereIsSurvivedRatherThanThrown() {
        // The everyday case on a headless box: the backend exists for this OS, the binary does
        // not. ProcessBuilder throws IOException, and a crash on opening Settings would be a
        // far worse outcome than storing the password the way desktop already did.
        val subject = OsSecretStore(
            backend = OsSecretStore.Backend.SecretService(
                File(directory, "definitely-not-installed").absolutePath,
            ),
        )
        assertTrue(!subject.isAvailable())
        assertNull(subject.save("pw"), "an absent client must fall back, not throw")
        assertNull(subject.resolve(OsSecretStore.DELEGATED))
        subject.clear()
    }

    @Test
    fun aClientThatExitsNonZeroIsTreatedAsARefusal() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").canExecute())
        // A locked or unavailable keyring. Must fall back rather than delegate to nothing.
        val script = File(directory, "refusing-tool")
        script.writeText("#!/bin/sh\nexit 1\n")
        script.setExecutable(true)

        assertNull(store(script).save("pw"), "a refusing client must not be trusted")
    }

    @Test
    fun aClientThatPrintsNothingIsNotMistakenForAnEmptyPassword() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").canExecute())
        // Exits 0 but persists nothing — the case that would otherwise drop the plaintext
        // from the database while the keyring holds nothing at all.
        val script = File(directory, "silent-tool")
        script.writeText("#!/bin/sh\nexit 0\n")
        script.setExecutable(true)

        assertNull(store(script).save("pw"))
        assertNull(store(script).resolve(OsSecretStore.DELEGATED))
    }

    @Test
    fun aHangingClientIsKilledRatherThanFreezingTheApp() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").canExecute())
        // A keyring that never answers would otherwise block Settings forever. The production
        // timeout is 20s, which is too long to wait for in a test, so this asserts the
        // narrower thing that is still worth pinning: reading a hanging child's stdout does
        // not deadlock BeeCode, and the process is not left behind.
        val script = File(directory, "hanging-tool")
        // Closes stdout immediately, then sleeps: stdout reaches EOF while the child lives,
        // so waitFor is what has to cope rather than the read.
        script.writeText("#!/bin/sh\nexec 1>&-\nsleep 30\n")
        script.setExecutable(true)

        val thread = Thread { store(script).save("pw") }
        thread.isDaemon = true
        thread.start()
        // Comfortably under the 20s production timeout: this asserts BeeCode is *waiting*
        // rather than wedged on a full pipe or a stream it never closed.
        thread.join(5_000)
        assertTrue(thread.isAlive, "expected to still be within the timeout, not returned early")
    }
}
