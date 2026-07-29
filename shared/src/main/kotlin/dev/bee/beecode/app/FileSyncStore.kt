package dev.bee.beecode.app

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * A [SyncStore] backed by a single file.
 *
 * The first backend on purpose. ADR 0002 predicted it: "WebDAV or a plain file is the
 * likely first target because it is testable without a Google Cloud project." A file in
 * a directory that Dropbox, Syncthing, iCloud Drive, or a mounted network share already
 * replicates gives working cross-device sync with **no credentials, no OAuth, and no
 * BeeCode server** — and it exercises every part of the loop that a networked backend
 * will, including the compare-and-swap.
 *
 * ### The token is a content hash
 *
 * An HTTP backend would use an ETag; a file has no such header. A SHA-256 of the
 * contents is better than a modification time for this purpose: it is stable across
 * filesystems with coarse timestamp resolution, it does not go backwards when a
 * synchroniser rewrites a file with a preserved mtime, and two devices that computed the
 * same merged snapshot get the same token — which is exactly the property
 * [SnapshotMerge]'s determinism was designed to provide.
 *
 * ### The write is atomic
 *
 * Writes go to a sibling temporary file and are then moved into place, because a
 * truncated snapshot is worse than a stale one: it looks like valid JSON right up to the
 * point it does not, and a learner would restore it believing it was a backup. The move
 * is same-directory so it stays a rename rather than a copy.
 *
 * The compare-and-swap is **not** atomic against another process writing the same file
 * in the same instant, and cannot be made so without file locking that behaves
 * differently on every platform and network filesystem. It is a read-verify-write, which
 * closes the realistic window — two devices syncing minutes apart — but not a
 * simultaneous one. That is stated rather than hidden, and is a reason a real HTTP
 * backend with a genuine ETag is the better long-term target.
 */
class FileSyncStore(private val file: File) : SyncStore {

    override val storeId: String = "file"

    override suspend fun pull(): SyncOutcome<SyncSnapshot?> = try {
        if (!file.isFile) {
            // Absent is a normal first-run state, not a failure: nothing has synced yet.
            SyncOutcome.Success(null)
        } else {
            val text = file.readText()
            SyncOutcome.Success(SyncSnapshot(payloadText = text, token = tokenFor(text)))
        }
    } catch (e: IOException) {
        SyncOutcome.Unavailable("Could not read ${file.name}: ${e.message}")
    } catch (e: SecurityException) {
        SyncOutcome.Unavailable("Not allowed to read ${file.name}: ${e.message}")
    }

    override suspend fun push(payloadText: String, expectedToken: String?): SyncOutcome<String> {
        return try {
            val currentToken = if (file.isFile) tokenFor(file.readText()) else null
            if (currentToken != expectedToken) {
                return SyncOutcome.Conflict(
                    if (expectedToken == null) {
                        "Another device created the snapshot after this sync began."
                    } else {
                        "Another device changed the snapshot after this sync began."
                    },
                )
            }

            file.parentFile?.mkdirs()
            // Same directory, so the move below is a rename and not a copy that could
            // itself be interrupted half-written.
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(payloadText)
            if (!temporary.renameTo(file)) {
                // Windows and some network filesystems refuse a rename onto an existing
                // file. Falling back to delete-then-rename reopens a small window where
                // the file is absent, which pull() already treats as "nothing synced
                // yet" rather than as corruption.
                file.delete()
                if (!temporary.renameTo(file)) {
                    temporary.delete()
                    return SyncOutcome.Unavailable("Could not replace ${file.name}.")
                }
            }
            SyncOutcome.Success(tokenFor(payloadText))
        } catch (e: IOException) {
            SyncOutcome.Unavailable("Could not write ${file.name}: ${e.message}")
        } catch (e: SecurityException) {
            SyncOutcome.Unavailable("Not allowed to write ${file.name}: ${e.message}")
        }
    }

    private fun tokenFor(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
}
