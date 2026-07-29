package dev.bee.beecode.android

import android.content.ContentResolver
import android.net.Uri
import dev.bee.beecode.app.SyncOutcome
import dev.bee.beecode.app.SyncSnapshot
import dev.bee.beecode.app.SyncStore
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

/**
 * A [SyncStore] over a document the learner picked with Android's document picker.
 *
 * Android's storage model is why this exists rather than reusing `FileSyncStore`: BeeCode
 * declares **no storage permission at all** — that is a documented property of the app,
 * not an oversight — so it cannot open a path. What it can do is hold a persisted URI
 * permission for one document the learner explicitly chose, which is strictly narrower:
 * access to exactly that file, revocable by the learner, and granted by the system rather
 * than claimed by the app.
 *
 * Point this at a document in a folder Dropbox, Syncthing, or Drive already replicates
 * and the desktop client's `FileSyncStore` becomes the other end of a working sync.
 *
 * ### The token
 *
 * A SHA-256 of the contents, identical to `FileSyncStore`'s. That is deliberate: the two
 * stores must agree on a token for the same bytes, or a phone and a laptop syncing through
 * one file would each read the other's push as a change and never settle.
 *
 * A `DocumentsProvider` can expose a modification time, but many do not, and cloud
 * providers report it inconsistently. Hashing the content sidesteps the whole question.
 *
 * ### Atomicity, honestly
 *
 * There is no rename-into-place here. `ContentResolver.openOutputStream` with mode `"wt"`
 * truncates and rewrites the document, so a process death mid-write can leave a partial
 * snapshot — and unlike the desktop store there is no sibling temp file to make that
 * impossible, because creating one requires write access to the *directory* rather than
 * the document.
 *
 * The mitigation is that a corrupt remote is *detected* rather than applied: the merge
 * refuses a payload it cannot parse, so the next sync reports a failure and leaves the
 * local profile untouched. Data on the device is never at risk; the shared file can need
 * re-seeding. That trade buys the no-permission property, and it is stated rather than
 * hidden.
 */
class DocumentSyncStore(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : SyncStore {

    override val storeId: String = "android-document"

    override suspend fun pull(): SyncOutcome<SyncSnapshot?> = try {
        val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        when {
            // Null means the provider would not open it — treated as unavailable rather
            // than as "empty", because reporting nothing there would make the next push
            // pass a null token and overwrite whatever is actually in the file.
            text == null -> SyncOutcome.Unavailable("Could not open the sync file.")
            // An empty document is the normal state right after the picker creates one,
            // so it means "nothing synced yet" rather than an error.
            text.isBlank() -> SyncOutcome.Success(null)
            else -> SyncOutcome.Success(SyncSnapshot(payloadText = text, token = tokenFor(text)))
        }
    } catch (e: FileNotFoundException) {
        // The learner deleted the file, or the provider revoked access.
        SyncOutcome.Unavailable("The sync file is no longer available: ${e.message}")
    } catch (e: SecurityException) {
        SyncOutcome.Unavailable("BeeCode no longer has permission for the sync file.")
    } catch (e: IOException) {
        SyncOutcome.Unavailable("Could not read the sync file: ${e.message}")
    }

    override suspend fun push(payloadText: String, expectedToken: String?): SyncOutcome<String> {
        return try {
            val current = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return SyncOutcome.Unavailable("Could not open the sync file.")
            val currentToken = if (current.isBlank()) null else tokenFor(current)
            if (currentToken != expectedToken) {
                return SyncOutcome.Conflict("Another device changed the sync file after this sync began.")
            }

            // "wt" truncates first. Without it a shorter snapshot would leave the tail of
            // the previous one behind, producing trailing bytes after valid JSON — which
            // some parsers accept, making the corruption silent.
            contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(payloadText.encodeToByteArray())
                out.flush()
            } ?: return SyncOutcome.Unavailable("Could not open the sync file for writing.")

            SyncOutcome.Success(tokenFor(payloadText))
        } catch (e: FileNotFoundException) {
            SyncOutcome.Unavailable("The sync file is no longer available: ${e.message}")
        } catch (e: SecurityException) {
            SyncOutcome.Unavailable("BeeCode no longer has permission for the sync file.")
        } catch (e: IOException) {
            SyncOutcome.Unavailable("Could not write the sync file: ${e.message}")
        }
    }

    private fun tokenFor(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
}
