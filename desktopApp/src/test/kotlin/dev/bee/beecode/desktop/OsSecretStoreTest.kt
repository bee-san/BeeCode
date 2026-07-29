package dev.bee.beecode.desktop

import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.python.jvm.ProcessPythonRunner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop credential store, tested without a keyring.
 *
 * This host has no `secret-tool` and no `security`, and neither does CI, so a test that
 * needed a real secret service would skip everywhere and assert nothing — the exact failure
 * mode that put nine never-executed Android UI tests in this repo. The `CommandRunner` seam
 * exists for that reason: the backends' *command contract* is what can be got wrong, and it
 * is checkable in-process.
 *
 * What is therefore verified here, and what is not:
 *
 * - **Verified**: the arguments each backend sends, that the Linux password goes over stdin
 *   rather than a command line, that a store which cannot be read back is not trusted, that
 *   the database ends up holding a marker instead of the password, and that clearing removes
 *   both.
 * - **Not verified here**: that a real `secret-tool` or `security` honours those commands.
 *   That is an assertion about someone else's binary, and it is stated as an assumption in
 *   the README rather than pretended to be tested.
 */
class OsSecretStoreTest {

    /** Records what was asked of the client and replays a keyring's behaviour. */
    private class FakeKeyring(
        var storeExitCode: Int = 0,
        /** Simulates a client that exits 0 without persisting anything. */
        var persist: Boolean = true,
    ) : OsSecretStore.CommandRunner {
        val commands = mutableListOf<List<String>>()
        val stdins = mutableListOf<String?>()
        var secret: String? = null

        override fun run(command: List<String>, stdin: String?): OsSecretStore.CommandResult {
            commands += command
            stdins += stdin
            val verb = command.getOrNull(1).orEmpty()
            return when {
                verb == "store" -> {
                    if (storeExitCode == 0 && persist) secret = stdin
                    OsSecretStore.CommandResult(storeExitCode, "")
                }
                verb == "add-generic-password" -> {
                    // -w takes the password as the following argument.
                    if (storeExitCode == 0 && persist) {
                        secret = command.getOrNull(command.indexOf("-w") + 1)
                    }
                    OsSecretStore.CommandResult(storeExitCode, "")
                }
                verb == "lookup" || verb == "find-generic-password" ->
                    secret?.let { OsSecretStore.CommandResult(0, it) }
                        ?: OsSecretStore.CommandResult(1, "")
                verb == "clear" || verb == "delete-generic-password" -> {
                    secret = null
                    OsSecretStore.CommandResult(0, "")
                }
                else -> OsSecretStore.CommandResult(1, "")
            }
        }
    }

    // An executable path that exists, so isAvailable() is answered by the filesystem rather
    // than by whether this particular host happens to ship a keyring client.
    private val presentExecutable = "/bin/sh"

    private fun linux(runner: OsSecretStore.CommandRunner) = OsSecretStore(
        backend = OsSecretStore.Backend.SecretService(presentExecutable),
        runner = runner,
    )

    private fun mac(runner: OsSecretStore.CommandRunner) = OsSecretStore(
        backend = OsSecretStore.Backend.MacKeychain(presentExecutable),
        runner = runner,
    )

    @Test
    fun aSavedPasswordComesBackAndTheDatabaseNeverSeesIt() {
        val keyring = FakeKeyring()
        val store = linux(keyring)

        val stored = store.save("correct horse battery staple")

        // What goes in the profile is the marker, and the marker is not the password.
        assertEquals(OsSecretStore.DELEGATED, stored)
        assertNotEquals("correct horse battery staple", stored)
        assertTrue(
            "correct horse battery staple" !in stored.orEmpty(),
            "the marker must not embed the password",
        )
        // And the marker resolves back to the real thing.
        assertEquals("correct horse battery staple", store.resolve(stored!!))
    }

    @Test
    fun theLinuxPasswordIsNeverPutOnACommandLine() {
        // /proc/<pid>/cmdline is readable by the same user, so a password in argv is visible
        // to any other process of that account for as long as the write takes. secret-tool
        // reads the secret from stdin precisely so it does not have to be.
        val keyring = FakeKeyring()
        linux(keyring).save("s3cr3t-pa55w0rd")

        val storeCommand = keyring.commands.first { it.getOrNull(1) == "store" }
        assertTrue(
            storeCommand.none { it.contains("s3cr3t-pa55w0rd") },
            "the password must not appear in argv, got: $storeCommand",
        )
        assertEquals("s3cr3t-pa55w0rd", keyring.stdins.first(), "it should arrive over stdin")
    }

    @Test
    fun theBackendsAreAddressedWithTheirOwnCommands() {
        // The two CLIs share no syntax, so a copy-paste between them would be silently wrong
        // on whichever platform was not the developer's.
        val linuxKeyring = FakeKeyring()
        linux(linuxKeyring).save("pw")
        val lookup = linuxKeyring.commands.first { it.getOrNull(1) == "lookup" }
        // Attributes must match between store and lookup or the secret is unfindable.
        assertEquals(
            linuxKeyring.commands.first { it.getOrNull(1) == "store" }.filter { it !in setOf("store") }
                .filterNot { it.startsWith("--label=") },
            lookup.filterNot { it == "lookup" },
            "store and lookup must use identical attributes",
        )

        val macKeyring = FakeKeyring()
        mac(macKeyring).save("pw")
        val add = macKeyring.commands.first { it.getOrNull(1) == "add-generic-password" }
        // -U, or changing a password fails as a duplicate item.
        assertTrue("-U" in add, "an existing keychain item must be updated, got: $add")
        assertTrue(
            macKeyring.commands.any { it.getOrNull(1) == "find-generic-password" && "-w" in it },
            "the lookup must ask for the password only",
        )
    }

    @Test
    fun aStoreThatCannotBeReadBackIsNotTrusted() {
        // The dangerous case: a client that exits 0 and persists nothing. Trusting it would
        // drop the plaintext from the database while the keyring holds nothing, leaving the
        // learner with a credential that exists nowhere. Worse than plaintext.
        val store = linux(FakeKeyring(persist = false))

        assertNull(store.save("pw"), "an unverifiable store must not be trusted")
    }

    @Test
    fun aRefusedStoreFallsBackInsteadOfLosingTheCredential() {
        // A locked keyring exits non-zero. Sync must keep working the way it did before.
        assertNull(linux(FakeKeyring(storeExitCode = 1)).save("pw"))
        assertNull(mac(FakeKeyring(storeExitCode = 1)).save("pw"))
    }

    @Test
    fun aLegacyPlaintextCredentialStillWorks() {
        // Installs that stored a plaintext password before this existed must keep syncing.
        // Anything that is not the marker is returned unchanged.
        assertEquals("legacy-plaintext", linux(FakeKeyring()).resolve("legacy-plaintext"))
    }

    @Test
    fun aMarkerWithNoSecretBehindItResolvesToNothing() {
        // Cleared keyring, or a profile copied to another machine. Being asked again is the
        // correct outcome, and returning the marker itself would send it as the password.
        val store = linux(FakeKeyring())
        val resolved = store.resolve(OsSecretStore.DELEGATED)
        assertNull(resolved, "a missing secret must not resolve to the marker")
    }

    @Test
    fun clearingRemovesTheSecretFromTheKeyringToo() {
        // Otherwise "I deleted my password" leaves a live credential in the learner's keyring
        // that nothing in the UI mentions any more.
        val keyring = FakeKeyring()
        val store = linux(keyring)
        store.save("pw")
        store.clear()

        assertNull(keyring.secret, "the OS secret must be removed as well")
    }

    @Test
    fun aTrailingNewlineFromTheClientIsNotPartOfThePassword() {
        // A password with a stray newline fails to authenticate in a way that looks exactly
        // like a wrong password, which is a miserable thing to debug.
        val keyring = object : OsSecretStore.CommandRunner {
            override fun run(command: List<String>, stdin: String?) =
                OsSecretStore.CommandResult(0, "pw\n")
        }
        assertEquals("pw", linux(keyring).resolve(OsSecretStore.DELEGATED))
    }

    @Test
    fun noBackendMeansNoClaimOfProtection() {
        // Windows, and headless Linux. isAvailable() must be false so the UI shows the
        // plaintext warning rather than promising a keyring that is not there.
        val none = OsSecretStore(backend = null, runner = FakeKeyring())
        assertTrue(!none.isAvailable())
        assertNull(none.backendName())
        assertNull(none.save("pw"), "with no backend nothing may be delegated")
        // And a legacy value still passes through, so sync keeps working.
        assertEquals("pw", none.resolve("pw"))
    }

    @Test
    fun anAbsentClientIsReportedUnavailableRatherThanAssumed() {
        // The backend exists for this OS but the client is not installed — the common case on
        // a server. Detection is by PATH lookup, so a name that resolves nowhere is absent.
        val missing = OsSecretStore(
            backend = OsSecretStore.Backend.SecretService("beecode-no-such-client-xyz"),
            runner = FakeKeyring(),
        )
        assertTrue(!missing.isAvailable(), "an uninstalled client must not look available")
    }

    // ---- Through the real settings repository --------------------------------

    private lateinit var databaseFile: File

    @BeforeTest
    fun setUp() {
        databaseFile = kotlin.io.path.createTempFile("beecode-secret-", ".db").toFile()
        databaseFile.delete()
    }

    @AfterTest
    fun tearDown() {
        databaseFile.delete()
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
    }

    @Test
    fun theProfileDatabaseHoldsNoPasswordAfterAStore() {
        // The claim that matters, made against the real database rather than a fake: a copy of
        // this file must not contain the credential. Read back as bytes, because asserting on
        // the accessor would pass even if the value were sitting in some other column.
        val keyring = FakeKeyring()
        val store = linux(keyring)
        openProfile().use { profile ->
            SyncCredential.store(profile.settings, "unmistakable-pw-42", store = store)

            assertEquals(
                OsSecretStore.DELEGATED,
                profile.settings.syncWebDavPassword(),
                "the database should hold the marker",
            )
            assertEquals(
                "unmistakable-pw-42",
                SyncCredential.resolve(profile.settings, store = store),
                "and it must still resolve to the password",
            )
        }

        val bytes = databaseFile.readBytes().decodeToString()
        assertTrue(
            "unmistakable-pw-42" !in bytes,
            "the password must not appear anywhere in the profile database",
        )
    }

    @Test
    fun clearingThePasswordEmptiesBothPlaces() {
        val keyring = FakeKeyring()
        val store = linux(keyring)
        openProfile().use { profile ->
            SyncCredential.store(profile.settings, "pw", store = store)
            SyncCredential.store(profile.settings, "", store = store)

            assertNull(profile.settings.syncWebDavPassword(), "blank must clear the setting")
            assertNull(keyring.secret, "blank must clear the OS secret")
            assertNull(SyncCredential.resolve(profile.settings, store = store))
        }
    }

    @Test
    fun withoutAKeyringTheCredentialIsStoredAsItAlwaysWas() {
        // The fallback, end to end. A learner on a machine with no secret service must still
        // be able to sync — losing a feature to a hardening measure would be a regression.
        val none = OsSecretStore(backend = null, runner = FakeKeyring())
        openProfile().use { profile ->
            SyncCredential.store(profile.settings, "plain-pw", store = none)

            assertEquals("plain-pw", profile.settings.syncWebDavPassword())
            assertEquals("plain-pw", SyncCredential.resolve(profile.settings, store = none))
            assertTrue(SyncCredential.storesPlaintext(none), "and the UI must be told to warn")
        }
    }

    private fun openProfile(): BeeCodeProfile = BeeCodeProfile.open(
        databasePath = databaseFile.absolutePath,
        catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE),
        runner = ProcessPythonRunner(),
    )
}
