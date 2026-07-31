package dev.bee.beecode.app

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The WebDAV store, against a real HTTP server.
 *
 * A fake that returned canned `SyncOutcome`s would test nothing about this class — the
 * whole content of [WebDavSyncStore] is *which HTTP status and header means what*, so the
 * only honest test speaks HTTP. The JDK's `com.sun.net.httpserver` is enough for that and
 * adds no dependency.
 *
 * The server here implements the one WebDAV behaviour BeeCode relies on: ETags plus
 * `If-Match`/`If-None-Match` preconditions, answered with 412. That is deliberately a
 * *server-side* check, because that is the point of choosing WebDAV over a file — the
 * refusal is atomic and the client never has to guess.
 *
 * These run over `http://` against localhost, so they use the internal constructor path via
 * [WebDavSyncStore.create]'s validation being tested separately. HTTPS is enforced for real
 * configurations and that enforcement has its own tests below.
 */
class WebDavSyncStoreTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    /** The single "file" the fake server holds, with its ETag. */
    private var stored: String? = null
    private var etag: String? = null
    private val revision = AtomicInteger(0)
    private var forcedStatus: Int? = null
    private var omitEtagOnPut = false
    private var requireAuth: String? = null

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/beecode-sync.json") { exchange -> handle(exchange) }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/beecode-sync.json"
    }

    @AfterTest
    fun tearDown() = server.stop(0)

    // ---- Reading -------------------------------------------------------------

    @Test
    fun anAbsentSnapshotReadsAsNothingSyncedYet() {
        // 404 is the normal first-run state, not a failure: reporting it as one would make
        // a learner think sync is broken before they have ever used it.
        val result = assertIs<SyncOutcome.Success<SyncSnapshot?>>(runBlocking { store().pull() })
        assertEquals(null, result.value)
    }

    @Test
    fun aBlankButExistingRemoteIsSeededRatherThanConflictingForever() {
        // A zero-byte file already on the server — what a WebDAV client or folder-sync tool
        // leaves when a learner creates the sync file ahead of time.
        //
        // pull() rightly reports "nothing synced yet" for a blank body, so the loop pushes
        // with expectedToken = null, which sends `If-None-Match: *`. The server refuses that
        // because the resource exists, so before the fix *every* sync reported a conflict and
        // no snapshot was ever written. Re-guarding on the blank resource's ETag seeds it.
        stored = ""
        etag = "\"rev-0\""

        val pulled = assertIs<SyncOutcome.Success<SyncSnapshot?>>(runBlocking { store().pull() })
        assertEquals(null, pulled.value, "a blank body holds no snapshot")

        val pushed = assertIs<SyncOutcome.Success<String>>(
            runBlocking { store().push("""{"formatVersion":2}""", expectedToken = null) },
        )
        assertEquals("""{"formatVersion":2}""", stored)
        assertEquals(etag, pushed.value)
    }

    @Test
    fun seedingDoesNotOverwriteARealSnapshotThatAppearedFirst() {
        // The retry above must not become an unguarded write. A device that pulled "nothing
        // there" and then finds a *real* snapshot has genuinely lost the race, and forcing
        // would discard the other device's work — the one failure mode sync must not have.
        stored = """{"formatVersion":2,"reviews":[]}"""
        etag = "\"rev-9\""

        val result = assertIs<SyncOutcome.Conflict>(
            runBlocking { store().push("""{"mine":true}""", expectedToken = null) },
        )
        assertTrue(result.reason.contains("changed"), result.reason)
        assertEquals("""{"formatVersion":2,"reviews":[]}""", stored, "the remote must be untouched")
    }

    @Test
    fun aStoredSnapshotReadsBackWithItsServerEtag() {
        stored = """{"formatVersion":1}"""
        etag = "\"rev-1\""

        val result = assertIs<SyncOutcome.Success<SyncSnapshot?>>(runBlocking { store().pull() })
        val snapshot = requireNotNull(result.value)
        assertEquals(stored, snapshot.payloadText)
        // The token is the server's ETag verbatim, not a hash BeeCode computed. Only the
        // server's own identifier can be enforced atomically, and only by the server.
        assertEquals("\"rev-1\"", snapshot.token)
    }

    @Test
    fun aServerWithoutEtagsIsRefusedRatherThanSilentlyDegraded() {
        // Without an ETag there is no compare-and-swap. Falling back to last-write-wins
        // would lose a device's work without telling anyone, so this refuses instead.
        stored = """{"formatVersion":1}"""
        etag = null

        val result = assertIs<SyncOutcome.Unavailable>(runBlocking { store().pull() })
        assertTrue(result.reason.contains("ETag"), result.reason)
        assertTrue(result.reason.contains("overwriting"), result.reason)
    }

    @Test
    fun anEmptyFileReadsAsNothingSyncedYet() {
        // Some servers and sync clients create a zero-byte placeholder.
        stored = ""
        etag = "\"rev-0\""
        val result = assertIs<SyncOutcome.Success<SyncSnapshot?>>(runBlocking { store().pull() })
        assertEquals(null, result.value)
    }

    // ---- Writing and the compare-and-swap ------------------------------------

    @Test
    fun seedingAnEmptyRemoteUsesIfNoneMatch() {
        val pushed = assertIs<SyncOutcome.Success<String>>(
            runBlocking { store().push("""{"a":1}""", expectedToken = null) },
        )
        assertEquals("""{"a":1}""", stored)
        assertEquals(pushed.value, etag)
    }

    @Test
    fun seedingRefusesWhenAnotherDeviceGotThereFirst() {
        // If-None-Match:* must stop this device clobbering a snapshot that appeared after
        // its pull returned nothing.
        stored = """{"theirs":true}"""
        etag = "\"rev-1\""

        val conflict = assertIs<SyncOutcome.Conflict>(
            runBlocking { store().push("""{"mine":true}""", expectedToken = null) },
        )
        assertTrue(conflict.reason.contains("Another device"), conflict.reason)
        assertEquals("""{"theirs":true}""", stored, "the other device's snapshot must survive")
    }

    @Test
    fun aStaleTokenIsRefusedByTheServerNotByGuesswork() {
        // The reason this backend exists. The client sends the token it read; the server
        // compares and returns 412. No read-verify-write window.
        stored = """{"v":1}"""
        etag = "\"rev-1\""

        val conflict = assertIs<SyncOutcome.Conflict>(
            runBlocking { store().push("""{"v":2}""", expectedToken = "\"rev-old\"") },
        )
        assertTrue(conflict.reason.contains("changed"), conflict.reason)
        assertEquals("""{"v":1}""", stored, "a stale write must not land")
    }

    @Test
    fun aMatchingTokenWritesAndReturnsTheNewEtag() {
        stored = """{"v":1}"""
        // Seeded past the fake server's counter so the new ETag cannot coincidentally equal
        // the old one — otherwise "the token must advance" would pass by accident.
        etag = "\"rev-1\""
        revision.set(1)

        val pushed = assertIs<SyncOutcome.Success<String>>(
            runBlocking { store().push("""{"v":2}""", expectedToken = "\"rev-1\"") },
        )
        assertEquals("""{"v":2}""", stored)
        assertTrue(pushed.value != "\"rev-1\"", "the token must advance")
        assertEquals(pushed.value, etag)
    }

    @Test
    fun aServerThatOmitsTheEtagOnPutIsRereadRatherThanGuessed() {
        // Not every server returns an ETag on a PUT. Inventing one would produce a token
        // the server does not recognise, so the next push would conflict forever.
        omitEtagOnPut = true
        val pushed = assertIs<SyncOutcome.Success<String>>(
            runBlocking { store().push("""{"v":1}""", expectedToken = null) },
        )
        assertEquals(etag, pushed.value, "the token must come from a re-read, not from thin air")
    }

    // ---- Failure mapping -----------------------------------------------------

    @Test
    fun badCredentialsSayThatRatherThanAStatusCode() {
        requireAuth = "Basic " + Base64.getEncoder().encodeToString("real:secret".toByteArray())
        val result = assertIs<SyncOutcome.Unavailable>(
            runBlocking { store(username = "wrong", password = "wrong").pull() },
        )
        assertTrue(result.reason.contains("credentials"), result.reason)
    }

    @Test
    fun correctCredentialsAreAccepted() {
        requireAuth = "Basic " + Base64.getEncoder().encodeToString("real:secret".toByteArray())
        stored = """{"v":1}"""
        etag = "\"rev-1\""
        assertIs<SyncOutcome.Success<SyncSnapshot?>>(
            runBlocking { store(username = "real", password = "secret").pull() },
        )
    }

    @Test
    fun aMissingParentFolderIsReportedDistinctlyFromAConflict() {
        // 409 on a PUT almost always means the collection does not exist, and the fix is
        // different from a CAS loss — so conflating them would send a learner looking for
        // the wrong problem.
        forcedStatus = 409
        val result = assertIs<SyncOutcome.Unavailable>(
            runBlocking { store().push("{}", expectedToken = null) },
        )
        assertTrue(result.reason.contains("folder"), result.reason)
    }

    @Test
    fun anUnreachableServerFailsWithoutThrowing() {
        // A SyncStore must never throw: a sync that crashes the app is worse than one that
        // reports it could not run. Port 1 is reserved and nothing listens there.
        val store = WebDavSyncStore.create(
            url = "https://127.0.0.1:1/beecode-sync.json",
            timeoutMillis = 500,
        ).getOrThrow()
        val result = assertIs<SyncOutcome.Unavailable>(runBlocking { store.pull() })
        assertTrue(result.reason.contains("reach"), result.reason)
    }

    @Test
    fun anUnexpectedStatusIsReportedWithItsCode() {
        forcedStatus = 507
        val result = assertIs<SyncOutcome.Unavailable>(runBlocking { store().pull() })
        assertTrue(result.reason.contains("507"), result.reason)
    }

    // ---- Configuration validation --------------------------------------------

    @Test
    fun plainHttpIsRefusedBecauseBasicAuthWouldLeakTheCredentials() {
        // Not merely discouraged. Basic auth sends the password on every request, and the
        // payload is the learner's source code.
        val failure = WebDavSyncStore.create("http://example.com/beecode-sync.json").exceptionOrNull()
        assertTrue(
            failure?.message?.contains("https") == true,
            "expected an https requirement, got: ${failure?.message}",
        )
        assertTrue(failure?.message?.contains("unencrypted") == true)
    }

    @Test
    fun aFolderUrlIsRefusedWithAWorkedExample() {
        // A learner pasting their Nextcloud files URL is the likely mistake, and an error
        // that shows the shape of a correct address is worth more than one that says "no".
        val failure = WebDavSyncStore.create("https://cloud.example.com/remote.php/dav/files/me/")
            .exceptionOrNull()
        assertTrue(failure?.message?.contains("file, not a folder") == true, "${failure?.message}")
        assertTrue(failure?.message?.contains("beecode-sync.json") == true)
    }

    @Test
    fun halfSuppliedCredentialsAreRefused() {
        // A username with no password is a configuration mistake, not an anonymous request.
        assertTrue(
            WebDavSyncStore.create("https://x.example.com/a.json", username = "me").isFailure,
        )
        assertTrue(
            WebDavSyncStore.create("https://x.example.com/a.json", password = "p").isFailure,
        )
        assertTrue(
            WebDavSyncStore.create("https://x.example.com/a.json").isSuccess,
            "anonymous WebDAV is legitimate",
        )
    }

    @Test
    fun garbageIsRefusedRatherThanThrowing() {
        assertTrue(WebDavSyncStore.create("not a url at all").isFailure)
    }

    // ---- The loop, against a real server -------------------------------------

    @Test
    fun theSyncLoopRunsEndToEndOverHttp() {
        // Proves the store satisfies SyncService's expectations, not just its own tests:
        // seed from one profile, then a second pulls, merges, and pushes the union.
        val catalogue = ProblemCatalogue.fromSourceDirectory(
            java.io.File(repoRoot(), "content/packs/core"),
        )
        val phone = BeeCodeProfile.inMemory(catalogue = catalogue, runner = PassingRunner())
        val laptop = BeeCodeProfile.inMemory(catalogue = catalogue, runner = PassingRunner())
        try {
            runBlocking {
                val first = SyncService(store(), phone).sync(NOW)
                assertIs<SyncReport.Completed>(first)
                assertTrue(stored != null, "the server must hold a snapshot")

                val second = SyncService(store(), laptop).sync(NOW)
                assertIs<SyncReport.Completed>(second)
            }
        } finally {
            phone.close()
            laptop.close()
        }
    }

    // ---- The fake WebDAV server ----------------------------------------------

    /**
     * Implements the ETag and precondition behaviour BeeCode depends on, and nothing else.
     *
     * Kept minimal on purpose: a fuller WebDAV emulation would be more code than the class
     * under test and would mostly assert its own correctness.
     */
    private fun handle(exchange: HttpExchange) {
        try {
            requireAuth?.let { expected ->
                if (exchange.requestHeaders.getFirst("Authorization") != expected) {
                    exchange.sendResponseHeaders(401, -1)
                    return
                }
            }
            forcedStatus?.let { status ->
                exchange.sendResponseHeaders(status, -1)
                return
            }

            when (exchange.requestMethod) {
                "GET" -> {
                    val body = stored
                    if (body == null) {
                        exchange.sendResponseHeaders(404, -1)
                    } else {
                        etag?.let { exchange.responseHeaders.add("ETag", it) }
                        val bytes = body.toByteArray()
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    }
                }

                "PUT" -> {
                    val body = exchange.requestBody.use { it.readBytes().decodeToString() }
                    val ifMatch = exchange.requestHeaders.getFirst("If-Match")
                    val ifNoneMatch = exchange.requestHeaders.getFirst("If-None-Match")

                    // The server-side precondition check. This is what makes the CAS atomic
                    // rather than a read-verify-write the client hopes was quick enough.
                    val precondition = when {
                        ifNoneMatch == "*" -> stored == null
                        ifMatch != null -> ifMatch == etag
                        else -> true
                    }
                    if (!precondition) {
                        exchange.sendResponseHeaders(412, -1)
                        return
                    }

                    stored = body
                    etag = "\"rev-${revision.incrementAndGet()}\""
                    if (!omitEtagOnPut) exchange.responseHeaders.add("ETag", etag)
                    exchange.sendResponseHeaders(204, -1)
                }

                else -> exchange.sendResponseHeaders(405, -1)
            }
        } finally {
            exchange.close()
        }
    }

    /**
     * A store pointed at the local test server.
     *
     * Uses the `internal` [WebDavSyncStore.forTesting] seam, because [WebDavSyncStore.create]
     * refuses `http://` — correctly, and that refusal has its own tests above. A test server
     * on loopback cannot present a valid certificate, so the alternative would be weakening
     * the production rule to make testing convenient.
     */
    private fun store(username: String? = null, password: String? = null): WebDavSyncStore =
        WebDavSyncStore.forTesting(
            url = java.net.URL(baseUrl),
            authorization = username?.let {
                "Basic " + Base64.getEncoder().encodeToString("$it:$password".toByteArray())
            },
        )

    /** Passes everything, so a test about HTTP needs no interpreter. */
    private class PassingRunner : dev.bee.beecode.python.PythonRunner {
        override val runnerId = "scripted"
        override val capability = dev.bee.beecode.python.RunnerCapability.SEPARATE_PROCESS

        override suspend fun probe() = dev.bee.beecode.python.RunnerProbe(
            available = true,
            pythonVersion = "3.12.0 (scripted)",
            capability = capability,
            unavailableReason = null,
        )

        override suspend fun execute(
            request: dev.bee.beecode.python.RunRequest,
        ) = dev.bee.beecode.python.RunResult(
            runId = request.runId,
            outcome = dev.bee.beecode.domain.ExecutionOutcome.PASSED,
            testResults = request.tests.map {
                dev.bee.beecode.domain.TestCaseResult(
                    name = it.name,
                    passed = true,
                    hidden = it.hidden,
                    expectedJson = if (it.hidden) null else it.expectedJson,
                    actualJson = if (it.hidden) null else it.expectedJson,
                    message = null,
                    durationMillis = 1,
                )
            },
            output = "",
            outputTruncated = false,
            durationMillis = 1,
            runnerId = runnerId,
            pythonVersion = "3.12.0 (scripted)",
            diagnostic = null,
        )
    }

    private companion object {
        val NOW: kotlinx.datetime.Instant = kotlinx.datetime.Instant.parse("2026-07-29T12:00:00Z")

        fun repoRoot(): java.io.File {
            System.getProperty("beecode.repoRoot")?.let { return java.io.File(it) }
            var candidate = java.io.File(".").absoluteFile
            repeat(6) {
                if (java.io.File(candidate, "content/packs/core").isDirectory) return candidate
                candidate = candidate.parentFile ?: return candidate
            }
            return java.io.File(".").absoluteFile
        }
    }
}
