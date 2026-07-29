package dev.bee.beecode.desktop

import dev.bee.beecode.persistence.SettingsRepository
import kotlinx.datetime.Clock

/**
 * The one place the desktop client reads or writes the WebDAV password.
 *
 * Both directions go through here for the same reason Android routes them through a single
 * pair of `StudyViewModel` methods: a credential that is written encrypted and read raw
 * somewhere else is a bug that looks like a working feature until the learner's next sync,
 * and there is nothing in the type system to stop it. Storing and resolving in one file
 * means the marker written by [store] can only be interpreted by [resolve].
 *
 * This is deliberately *not* in `:persistence`. That module has no access to platform
 * facilities and should not grow one — the same reason Android's keystore code lives in
 * `:androidApp`. `SettingsRepository` stores whatever it is handed and says so.
 */
internal object SyncCredential {

    /**
     * The password to sync with, or null if there is none to use.
     *
     * Null when a delegated secret has vanished — a cleared keyring, or a profile copied to
     * another machine. The learner is asked again, which is correct: the marker left in the
     * profile deliberately carries no secret, so there is nothing to recover.
     */
    fun resolve(settings: SettingsRepository, store: OsSecretStore = OsSecretStore()): String? =
        settings.syncWebDavPassword()?.let { store.resolve(it) }

    /**
     * Persist [password], preferring the operating system's secret service.
     *
     * Blank clears both the stored value and the OS secret, so "clear the password" does not
     * leave a live credential in the learner's keyring that nothing in the UI mentions any
     * more.
     *
     * Falls back to storing the password in the profile when there is no secret service, or
     * when it refuses. That is the behaviour desktop already had, so this is strictly an
     * improvement rather than a new way to fail — and [storesPlaintext] exists so the UI can
     * say which of the two actually happened instead of warning about both.
     */
    fun store(
        settings: SettingsRepository,
        password: String?,
        store: OsSecretStore = OsSecretStore(),
        now: kotlinx.datetime.Instant = Clock.System.now(),
    ) {
        val cleaned = password?.ifBlank { null }
        if (cleaned == null) {
            store.clear()
            settings.setSyncWebDavPassword(null, now)
            return
        }
        settings.setSyncWebDavPassword(store.save(cleaned) ?: cleaned, now)
    }

    /**
     * Whether the password would end up in the profile database in the clear.
     *
     * Drives the warning in Settings. Asked of the store rather than of what is currently
     * stored, because the learner needs to know before typing where the credential is about
     * to go — a warning shown only after the fact is the thing the original text got right
     * and this must not regress.
     */
    fun storesPlaintext(store: OsSecretStore = OsSecretStore()): Boolean = !store.isAvailable()

    /** Where the credential goes, for the UI to name it. Null when it stays in the profile. */
    fun backendName(store: OsSecretStore = OsSecretStore()): String? =
        store.backendName()?.takeIf { store.isAvailable() }
}
