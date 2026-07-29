package dev.bee.beecode.desktop

import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.FileSyncStore
import dev.bee.beecode.app.ProfileTransfer
import dev.bee.beecode.app.RestoreResult
import dev.bee.beecode.app.SyncReport
import dev.bee.beecode.app.SyncService
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Native file dialogs for export and restore.
 *
 * AWT's `FileDialog` rather than Swing's `JFileChooser`: it is the platform's own
 * dialog, so it looks and behaves the way the learner expects and honours their
 * bookmarks and recent locations. Compose Desktop already runs on AWT, so this adds
 * no dependency.
 *
 * Every function returns a message for the UI rather than throwing. Choosing an
 * unwritable location or cancelling are both ordinary outcomes, and a stack trace is
 * the wrong response to either.
 */
internal object ProfileFiles {

    /**
     * Ask where to save, then write the export.
     *
     * @return a message for the learner, or null if they cancelled.
     */
    fun exportTo(profile: BeeCodeProfile): String? {
        val target = chooseSaveFile(suggestedName(profile)) ?: return null
        return try {
            val payload = ProfileTransfer.export(profile, Clock.System.now())
            target.writeText(payload)
            // Say plainly that the file is sensitive. It contains the learner's
            // source code, which is the whole point of a backup and also the reason
            // it should not be left in a shared folder.
            "Exported to ${target.name}. This file contains your solutions, so keep it " +
                "somewhere private."
        } catch (e: Exception) {
            "Could not write ${target.name}: ${e.message}"
        }
    }

    /**
     * Ask which file to read, then restore it.
     *
     * @return a message for the learner, or null if they cancelled.
     */
    fun restoreFrom(profile: BeeCodeProfile): String? {
        val source = chooseOpenFile() ?: return null
        return try {
            when (val result = ProfileTransfer.restore(profile, source.readText(), Clock.System.now())) {
                is RestoreResult.Restored -> result.describe()
                is RestoreResult.Failed -> result.reason
            }
        } catch (e: Exception) {
            "Could not read ${source.name}: ${e.message}"
        }
    }

    /**
     * Run one sync against the configured shared file.
     *
     * Blocking on purpose: the caller runs it off the UI thread, and a file sync is
     * milliseconds. A networked backend would need progress reporting; this does not,
     * and pretending otherwise would add machinery for a case that does not exist yet.
     *
     * @return a message for the learner. Never null — unlike export and restore there is
     *   no dialog to cancel.
     */
    suspend fun sync(profile: BeeCodeProfile, target: File): String {
        val report = SyncService(FileSyncStore(target), profile).sync(Clock.System.now())
        return report.describe(target)
    }

    /**
     * Turn a sync report into something worth reading.
     *
     * Deliberately specific about *what moved*. "Synced" tells a learner nothing, and the
     * difference between "nothing to do" and "received 12 reviews" is the difference
     * between trusting sync and wondering whether it ran.
     */
    private fun SyncReport.describe(target: File): String = when (this) {
        is SyncReport.Completed -> {
            val received = merge?.let { merge ->
                buildList {
                    if (merge.reviewsFromRemote > 0) add("${merge.reviewsFromRemote} reviews")
                    if (merge.draftsFromRemote > 0) add("${merge.draftsFromRemote} drafts")
                    if (merge.settingsFromRemote > 0) add("${merge.settingsFromRemote} settings")
                }
            }.orEmpty()
            when {
                received.isNotEmpty() && pushed ->
                    "Synced with ${target.name}: received ${received.joinToString(", ")}, and sent this device\'s changes."
                received.isNotEmpty() ->
                    "Synced with ${target.name}: received ${received.joinToString(", ")}."
                pushed -> "Synced with ${target.name}: sent this device\'s changes."
                else -> "Already up to date with ${target.name}."
            }
        }
        // Nothing was lost: whatever was pulled is already applied locally.
        is SyncReport.Conflicted ->
            "Another device kept updating ${target.name} while syncing, so this device\'s " +
                "changes were not sent. Everything it had was received, and the next sync " +
                "will try again."
        is SyncReport.Failed -> "Could not sync with ${target.name}: $reason"
    }

    /** Ask which shared file to sync against. */
    fun chooseSyncFile(): File? {
        val dialog = FileDialog(null as Frame?, "Choose a shared BeeCode sync file", FileDialog.SAVE).apply {
            file = DEFAULT_SYNC_NAME
            isVisible = true
        }
        val directory = dialog.directory ?: return null
        val name = dialog.file ?: return null
        val withExtension = if (name.endsWith(".json", ignoreCase = true)) name else "$name.json"
        return File(directory, withExtension)
    }

    /**
     * The suggested filename, shared across devices.
     *
     * Not dated, unlike an export\'s name: this is one file two devices keep writing to,
     * so a name that changed daily would silently start a new sync history.
     */
    const val DEFAULT_SYNC_NAME: String = "beecode-sync.json"

    /** A self-describing default name, so exports are identifiable on disk. */
    private fun suggestedName(profile: BeeCodeProfile): String {
        val today = Clock.System.now().toLocalDateTime(profile.settings.streakZone()).date
        return "beecode-$today.json"
    }

    private fun chooseSaveFile(defaultName: String): File? {
        val dialog = FileDialog(null as Frame?, "Export BeeCode profile", FileDialog.SAVE).apply {
            file = defaultName
            isVisible = true
        }
        val directory = dialog.directory ?: return null
        val name = dialog.file ?: return null
        // Some platforms return the name without the extension the learner expects.
        val withExtension = if (name.endsWith(".json", ignoreCase = true)) name else "$name.json"
        return File(directory, withExtension)
    }

    private fun chooseOpenFile(): File? {
        val dialog = FileDialog(null as Frame?, "Restore BeeCode profile", FileDialog.LOAD).apply {
            isVisible = true
        }
        val directory = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(directory, name).takeIf { it.isFile }
    }
}
