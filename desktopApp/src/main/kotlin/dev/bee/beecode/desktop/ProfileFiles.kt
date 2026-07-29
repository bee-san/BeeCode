package dev.bee.beecode.desktop

import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProfileTransfer
import dev.bee.beecode.app.RestoreResult
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
