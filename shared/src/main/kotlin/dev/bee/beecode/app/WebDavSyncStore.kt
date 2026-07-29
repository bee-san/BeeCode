package dev.bee.beecode.app

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * A [SyncStore] over WebDAV, using the server's own ETag for the compare-and-swap.
 *
 * This is the backend ADR 0002 named as the right long-term target, and the reason is
 * specific: `FileSyncStore`'s compare-and-swap is a read-verify-write, which closes the
 * realistic window (two devices minutes apart) but not a simultaneous one, and cannot be
 * made atomic without file locking that behaves differently on every platform and network
 * filesystem. WebDAV has a genuine atomic primitive — `If-Match` — so the server refuses a
 * stale write rather than BeeCode hoping it noticed.
 *
 * Nextcloud, ownCloud, Synology, `rclone serve webdav`, and Apache's `mod_dav` all speak
 * it, so "self-hosted sync" needs no BeeCode server. That matters: the Leaderboard is the
 * only part of the plan that ever justified running one.
 *
 * ### Why HttpURLConnection
 *
 * No new dependency, and it works on every Android version BeeCode supports.
 * `java.net.http.HttpClient` is nicer but arrived on Android at API 34, and minSdk is 26 —
 * so using it would either raise the floor or need two implementations. Ktor would add a
 * client the app otherwise has no use for.
 *
 * ### Why the token is the ETag, not a content hash
 *
 * The other two stores hash their contents because neither has anything better. Here the
 * server has already assigned an identity to the version it holds, and using anything else
 * would be strictly worse: a hash tells BeeCode whether the *content* changed, while an
 * ETag tells the *server* whether it is still holding what the client read. Only the latter
 * can be enforced atomically, and only at the server.
 *
 * One consequence, stated because it is a real behavioural difference: two devices that
 * compute an identical merge get identical tokens from `FileSyncStore` and *different*
 * ETags here, because a weak ETag typically encodes a revision rather than a digest. That
 * is fine — the CAS is about "has the server moved", not "is the content the same" — but it
 * means an idle sync can still push once after another device wrote the same bytes. It
 * settles on the next round.
 *
 * ### Credentials
 *
 * Basic auth over **HTTPS only**, refused otherwise. Basic auth is what WebDAV servers
 * universally accept, and it sends the password on every request — which is acceptable
 * under TLS and indefensible without it. So [create] rejects an `http://` URL rather than
 * leaving that as a configuration mistake a learner could make silently.
 */
class WebDavSyncStore private constructor(
    private val url: URL,
    private val authorization: String?,
    private val timeoutMillis: Int,
) : SyncStore {

    override val storeId: String = "webdav"

    override suspend fun pull(): SyncOutcome<SyncSnapshot?> = request("GET") { connection ->
        when (val status = connection.responseCode) {
            HttpURLConnection.HTTP_OK -> {
                val body = connection.inputStream.use { it.readBytes().decodeToString() }
                // An ETag is not optional for BeeCode's purposes: without one there is no
                // compare-and-swap, and silently degrading to last-write-wins would lose a
                // device's work without saying so.
                val etag = connection.getHeaderField("ETag")
                    ?: return@request SyncOutcome.Unavailable(
                        "The server did not return an ETag, so BeeCode cannot sync safely " +
                            "against it without risking overwriting another device.",
                    )
                if (body.isBlank()) {
                    SyncOutcome.Success(null)
                } else {
                    SyncOutcome.Success(SyncSnapshot(payloadText = body, token = etag))
                }
            }
            // Nothing synced yet. A normal first-run state, not an error.
            HttpURLConnection.HTTP_NOT_FOUND -> SyncOutcome.Success(null)
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                SyncOutcome.Unavailable("The server rejected the credentials ($status).")
            else -> SyncOutcome.Unavailable("The server returned $status.")
        }
    }

    override suspend fun push(payloadText: String, expectedToken: String?): SyncOutcome<String> =
        request("PUT", configure = { connection ->
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            // The whole point of this backend. If-Match makes the *server* refuse a stale
            // write; If-None-Match:* makes it refuse to clobber a file that appeared after
            // this sync began. Either way the refusal is atomic and BeeCode never guesses.
            if (expectedToken != null) {
                connection.setRequestProperty("If-Match", expectedToken)
            } else {
                connection.setRequestProperty("If-None-Match", "*")
            }
            connection.outputStream.use { it.write(payloadText.encodeToByteArray()) }
        }) { connection ->
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK,
                HttpURLConnection.HTTP_CREATED,
                HttpURLConnection.HTTP_NO_CONTENT,
                -> {
                    // Most servers return the new ETag on a PUT. Those that do not force a
                    // re-read, which is a round trip rather than a failure — and better
                    // than inventing a token the server would not recognise.
                    connection.getHeaderField("ETag")?.let { SyncOutcome.Success(it) }
                        ?: when (val reread = pull()) {
                            is SyncOutcome.Success -> reread.value?.token
                                ?.let { SyncOutcome.Success(it) }
                                ?: SyncOutcome.Unavailable("The snapshot vanished immediately after writing it.")
                            is SyncOutcome.Unavailable -> reread
                            is SyncOutcome.Conflict -> reread
                        }
                }
                // 412 is the CAS firing, which is success for the system even though it is
                // a failure for this attempt. 409 usually means a missing parent collection,
                // reported separately because the fix is different.
                HttpURLConnection.HTTP_PRECON_FAILED -> SyncOutcome.Conflict(
                    "Another device changed the snapshot after this sync began.",
                )
                HttpURLConnection.HTTP_CONFLICT -> SyncOutcome.Unavailable(
                    "The folder holding the sync file does not exist on the server.",
                )
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    SyncOutcome.Unavailable("The server rejected the credentials ($status).")
                else -> SyncOutcome.Unavailable("The server returned $status.")
            }
        }

    /**
     * Run one request, mapping every failure to an outcome rather than throwing.
     *
     * A [SyncStore] must never throw — a sync that crashes the app is worse than one that
     * reports it could not run — and network failures are the *ordinary* case here, not the
     * exceptional one.
     */
    private inline fun <T> request(
        method: String,
        configure: (HttpURLConnection) -> Unit = {},
        handle: (HttpURLConnection) -> SyncOutcome<T>,
    ): SyncOutcome<T> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                // Redirects are not followed: a redirect on a PUT can silently drop the
                // If-Match header, turning a guarded write into an unguarded one.
                instanceFollowRedirects = false
                authorization?.let { setRequestProperty("Authorization", it) }
            }
            configure(connection)
            handle(connection)
        } catch (e: IOException) {
            SyncOutcome.Unavailable("Could not reach the server: ${e.message ?: e::class.java.simpleName}")
        } catch (e: SecurityException) {
            SyncOutcome.Unavailable("Network access was refused: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        /** Ten seconds: long enough for a slow home server, short enough not to feel hung. */
        const val DEFAULT_TIMEOUT_MILLIS: Int = 10_000

        /**
         * Build a store against an already-validated URL, bypassing [create]'s checks.
         *
         * Exists for tests, and `internal` so no client can reach it. A test server on
         * loopback cannot present a valid certificate, so testing the HTTP behaviour over
         * `https` would mean either weakening [create]'s HTTPS requirement — which protects
         * a password sent on every request — or trusting a self-signed certificate through
         * machinery larger than the class under test.
         *
         * The requirement itself is not untested: [create]'s refusals have their own cases.
         */
        internal fun forTesting(
            url: URL,
            authorization: String? = null,
            timeoutMillis: Int = 5_000,
        ): WebDavSyncStore = WebDavSyncStore(url, authorization, timeoutMillis)

        /**
         * Build a store, refusing a configuration that cannot be used safely.
         *
         * Validation happens here rather than at first sync so a learner finds out while
         * they are looking at the settings screen, not days later when a sync fails.
         */
        fun create(
            url: String,
            username: String? = null,
            password: String? = null,
            timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
        ): Result<WebDavSyncStore> {
            val parsed = runCatching { URL(url) }.getOrNull()
                ?: return Result.failure(IllegalArgumentException("That is not a valid URL."))

            // HTTPS only, and this is not negotiable rather than merely recommended:
            // Basic auth sends the password on every single request, and the payload is the
            // learner's source code.
            if (!parsed.protocol.equals("https", ignoreCase = true)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Sync needs an https:// address. Over plain http your password and " +
                            "your solutions would travel unencrypted.",
                    ),
                )
            }
            if (parsed.path.isBlank() || parsed.path.endsWith("/")) {
                return Result.failure(
                    IllegalArgumentException(
                        "The address must point at a file, not a folder — for example " +
                            ".../remote.php/dav/files/you/beecode-sync.json",
                    ),
                )
            }
            if ((username == null) != (password == null)) {
                return Result.failure(
                    IllegalArgumentException("Give both a username and a password, or neither."),
                )
            }

            val authorization = username?.let {
                "Basic " + Base64.getEncoder()
                    .encodeToString("$it:$password".toByteArray())
            }
            return Result.success(WebDavSyncStore(parsed, authorization, timeoutMillis))
        }
    }
}
