package dev.bee.beecode.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bee.beecode.app.AchievementProjection
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.FinalizeResult
import dev.bee.beecode.app.RunOutcome
import dev.bee.beecode.app.ProfileTransfer
import dev.bee.beecode.android.KeystoreSecretStore
import dev.bee.beecode.app.LeaderboardService
import dev.bee.beecode.app.OutboxStatus
import dev.bee.beecode.app.SyncReport
import dev.bee.beecode.app.SyncService
import dev.bee.beecode.app.SyncStore
import dev.bee.beecode.app.RestoreResult
import dev.bee.beecode.app.RunnerStatus
import dev.bee.beecode.app.StudyQueue
import dev.bee.beecode.app.StudyStatistics
import dev.bee.beecode.design.ThemeChoice
import dev.bee.beecode.design.setThemeChoice
import dev.bee.beecode.design.themeChoice
import dev.bee.beecode.domain.ExecutionRun
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemReviewFinalized
import dev.bee.beecode.domain.ProblemSchedule
import dev.bee.beecode.domain.ReviewRating
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

/**
 * UI state for the study loop.
 *
 * Holds no study logic of its own: every decision — which ratings are permitted,
 * whether an attempt counts as solved, when a schedule advances — belongs to the
 * shared [dev.bee.beecode.app.StudyService], so Android and desktop cannot drift.
 * This class translates between that service and Compose state.
 */
class StudyViewModel(private val profile: BeeCodeProfile) : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Queue)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _queue = MutableStateFlow<StudyQueue?>(null)
    val queue: StateFlow<StudyQueue?> = _queue.asStateFlow()

    private val _problem = MutableStateFlow<ProblemUiState?>(null)
    val problem: StateFlow<ProblemUiState?> = _problem.asStateFlow()

    private val _statistics = MutableStateFlow<StudyStatistics?>(null)
    val statistics: StateFlow<StudyStatistics?> = _statistics.asStateFlow()

    private val _achievements = MutableStateFlow<AchievementProjection?>(null)
    val achievements: StateFlow<AchievementProjection?> = _achievements.asStateFlow()

    private val _runnerStatus = MutableStateFlow<RunnerStatus?>(null)
    val runnerStatus: StateFlow<RunnerStatus?> = _runnerStatus.asStateFlow()

    private var runJob: Job? = null

    init {
        refresh()
        viewModelScope.launch { _runnerStatus.value = profile.study.runnerStatus() }
    }

    fun refresh() {
        _queue.value = profile.study.queue()
        _statistics.value = profile.statistics()
        _achievements.value = profile.achievements()
    }

    fun showQueue() {
        _screen.value = Screen.Queue
        refresh()
    }

    fun showStatistics() {
        refresh()
        _screen.value = Screen.Statistics
    }

    fun showSettings() {
        _screen.value = Screen.Settings
    }

    fun openProblem(problemId: ProblemId) {
        val opened = profile.study.open(problemId) ?: return
        _problem.value = ProblemUiState(
            problem = opened.problem,
            source = opened.draft.source,
            schedule = opened.schedule,
            history = opened.history,
            latestRun = null,
            isRunning = false,
            revealedExplanation = null,
            aided = opened.session.aided,
            message = null,
            finalized = null,
        )
        _screen.value = Screen.Problem
    }

    /**
     * Record an edit.
     *
     * Kept in memory here and persisted on run, on close, and on process pause.
     * Writing on every keystroke would fsync on each character — `synchronous =
     * FULL` is deliberate for reviews but the wrong trade for typing.
     */
    fun editSource(source: String) {
        _problem.value = _problem.value?.copy(source = source, message = null)
    }

    /**
     * Persist the current draft. Called on leaving the Problem and on ViewModel teardown.
     *
     * Through the service, which creates the draft row on demand. Fetching the row first
     * and returning when it was absent meant a Problem opened and typed into but never
     * run silently discarded everything the learner had written.
     */
    fun persistDraft() {
        val state = _problem.value ?: return
        profile.study.saveSource(state.problem.id, state.source)
    }

    fun run() {
        val state = _problem.value ?: return
        if (state.isRunning) return

        _problem.value = state.copy(isRunning = true, message = null)
        runJob = viewModelScope.launch {
            when (val outcome = profile.study.run(state.problem.id, state.source)) {
                is RunOutcome.Completed -> {
                    val run = outcome.run
                    _problem.value = _problem.value?.copy(
                        isRunning = false,
                        latestRun = run,
                        permittedRatings = profile.study.permittedRatings(state.problem.id, run.id),
                        suggestedRating = profile.study.defaultRating(state.problem.id, run.id),
                    )
                }
                is RunOutcome.AlreadyFinalized -> _problem.value = _problem.value?.copy(
                    isRunning = false,
                    message = "This review is already finished. Go back and open it again to practise more.",
                )
                is RunOutcome.NoSession, is RunOutcome.UnknownProblem -> _problem.value =
                    _problem.value?.copy(
                        isRunning = false,
                        message = "BeeCode lost track of this attempt. Go back and open the Problem again.",
                    )
            }
        }
    }

    /**
     * Stop waiting for a run.
     *
     * On Android this cannot kill the interpreter — see
     * [dev.bee.beecode.android.ChaquopyPythonRunner]. The UI becomes responsive and
     * the learner keeps their code; the abandoned thread is the acknowledged cost.
     */
    fun cancelRun() {
        runJob?.cancel()
        runJob = null
        _problem.value = _problem.value?.copy(
            isRunning = false,
            message = "Stopped waiting for your code. On Android BeeCode cannot force Python to " +
                "stop, so restart the app if running code stops working.",
        )
    }

    fun reveal() {
        val state = _problem.value ?: return
        val revealed = profile.study.reveal(state.problem.id) ?: return
        _problem.value = state.copy(
            revealedExplanation = revealed.explanationMarkdown,
            aided = true,
            // The rating ceiling drops the moment the answer is revealed, so
            // recompute rather than leaving stale buttons enabled.
            permittedRatings = state.latestRun
                ?.let { profile.study.permittedRatings(state.problem.id, it.id) }
                ?: state.permittedRatings,
            suggestedRating = state.latestRun
                ?.let { profile.study.defaultRating(state.problem.id, it.id) },
        )
    }

    fun finalize(rating: ReviewRating) {
        val state = _problem.value ?: return
        val run = state.latestRun ?: return

        when (val result = profile.study.finalize(state.problem.id, run.id, rating)) {
            is FinalizeResult.Finalized -> {
                _problem.value = state.copy(
                    finalized = FinalizedUiState(
                        review = result.review,
                        schedule = result.schedule,
                    ),
                )
                refresh()
            }
            is FinalizeResult.Rejected -> _problem.value = state.copy(message = result.reason)
            is FinalizeResult.NoSession -> _problem.value = state.copy(
                message = "BeeCode lost track of this attempt. Go back and open the Problem again.",
            )
        }
    }

    fun resetToStarter() {
        val state = _problem.value ?: return
        val reset = profile.study.resetToStarter(state.problem.id) ?: return
        _problem.value = state.copy(source = reset.source, message = null)
    }

    fun closeProblem() {
        persistDraft()
        _problem.value?.let { profile.study.abandon(it.problem.id) }
        _problem.value = null
        showQueue()
    }

    fun setDailyLimit(limit: Int?) {
        profile.settings.setDailyReviewLimit(limit, kotlinx.datetime.Clock.System.now())
        refresh()
    }

    fun dailyLimit(): Int? = profile.settings.dailyReviewLimit()

    /**
     * The learner's theme preference, as state so the whole tree recomposes on a change.
     *
     * A `StateFlow` rather than a getter because the theme control lives *inside* the tree
     * the theme wraps — a plain read would store the choice and go on rendering the old
     * palette until the next launch, which is a setting that looks broken while working.
     */
    private val _themeChoice = MutableStateFlow(profile.settings.themeChoice())
    val themeChoice: StateFlow<ThemeChoice> = _themeChoice.asStateFlow()

    fun setThemeChoice(choice: ThemeChoice) {
        profile.settings.setThemeChoice(choice, kotlinx.datetime.Clock.System.now())
        _themeChoice.value = choice
    }

    /**
     * A Problem's title, for the few places statistics carry ids rather than Problems.
     *
     * Narrower than exposing the catalogue: the schedule card names its leeches, and a
     * leech the learner cannot identify is a number they can do nothing about.
     */
    fun problemTitle(problemId: ProblemId): String? = profile.catalogue.problem(problemId)?.title

    /**
     * Serialize the whole profile for the learner to save.
     *
     * The caller writes the bytes wherever the learner chose. This returns the
     * payload rather than taking a path because file access on Android goes through
     * a document picker the Activity owns.
     *
     * The payload contains the learner's source code, so whatever writes it must
     * treat it as sensitive.
     */
    fun exportProfile(): String =
        ProfileTransfer.export(profile, kotlinx.datetime.Clock.System.now())

    /** Restore a payload the learner picked, then refresh every derived view. */
    fun restoreProfile(payload: String): RestoreResult {
        val result = ProfileTransfer.restore(profile, payload, kotlinx.datetime.Clock.System.now())
        refresh()
        return result
    }

    // ---- Sync -----------------------------------------------------------

    /** The document URI this device syncs through, or null when sync is off. */
    fun syncTarget(): String? = profile.settings.syncFilePath()

    /** Remember the document the learner picked, so sync survives a restart. */
    fun setSyncTarget(uri: String?) {
        profile.settings.setSyncFilePath(uri, kotlinx.datetime.Clock.System.now())
    }

    // ---- Leaderboard ----------------------------------------------------

    /** Whether this profile has joined a Leaderboard. Off by default. */
    fun leaderboardJoined(): Boolean = profile.settings.leaderboardLinkedAt() != null

    /** Queue counts for the status lines. */
    fun leaderboardStatus(): OutboxStatus = LeaderboardService(profile).status()

    /**
     * Join, recording *now* as the cutoff.
     *
     * The cutoff is the current instant and not epoch zero, which is what keeps a learner's
     * pre-join history private: reviews finalized before this are never uploaded.
     *
     * @return how many solves were queued, which is zero unless the learner solved something
     *   in the same instant — stated because a UI showing a count should not have to guess.
     */
    fun joinLeaderboard(): Int {
        val now = kotlinx.datetime.Clock.System.now()
        profile.settings.setLeaderboardLinkedAt(now, now)
        return profile.refreshLeaderboardActivity(now)
    }

    /** Leave, discarding the queue. Reviews, schedules, and achievements are untouched. */
    fun leaveLeaderboard() {
        profile.settings.setLeaderboardLinkedAt(null, kotlinx.datetime.Clock.System.now())
        LeaderboardService(profile).forget()
    }

    /** Bring the queue up to date with the review log. Safe to call repeatedly. */
    fun refreshLeaderboard(): Int = profile.refreshLeaderboardActivity()

    /** Return stuck items to the queue after an outage. */
    fun retryStuckLeaderboardItems(): Int =
        LeaderboardService(profile).retryParked(kotlinx.datetime.Clock.System.now())

    /** The WebDAV file URL this device syncs through, or null when unset. */
    fun webDavUrl(): String? = profile.settings.syncWebDavUrl()

    fun webDavUsername(): String? = profile.settings.syncWebDavUsername()

    /**
     * The WebDAV password, decrypted.
     *
     * Returns null when a stored ciphertext will not decrypt, which is expected rather than
     * exceptional: the keystore key is destroyed on uninstall, factory reset, and on some
     * devices when the screen lock is removed. The learner is asked again rather than shown
     * a crash or, worse, having an unusable value sent to their server as a password.
     */
    fun webDavPassword(): String? =
        profile.settings.syncWebDavPassword()?.let { KeystoreSecretStore.decrypt(it) }

    /**
     * Remember WebDAV settings so a learner does not retype them.
     *
     * Blank values clear to null rather than storing an empty string, which would read as
     * "configured" everywhere else in the app.
     */
    fun setWebDav(url: String?, username: String?, password: String?) {
        val now = kotlinx.datetime.Clock.System.now()
        profile.settings.setSyncWebDavUrl(url?.ifBlank { null }, now)
        profile.settings.setSyncWebDavUsername(username?.ifBlank { null }, now)
        // Encrypted with a key in the Android Keystore, which on most devices is
        // hardware-backed: a database copied off the device decrypts to nothing.
        //
        // Falls back to plaintext when the platform refuses to provide a key, because a
        // learner on such a device should still be able to sync rather than lose a feature
        // to a hardening measure. That is the same behaviour as before this existed, so it
        // is a strict improvement rather than a new failure mode.
        val stored = password?.ifBlank { null }?.let { KeystoreSecretStore.encrypt(it) ?: it }
        profile.settings.setSyncWebDavPassword(stored, now)
    }

    /**
     * Run one sync against [store], then refresh every derived view.
     *
     * The refresh is the point of doing this here rather than in the UI: a sync can add
     * reviews from another device, which changes the queue, the statistics, and the
     * achievements. Without it the learner would sync successfully and see nothing move.
     */
    suspend fun sync(store: SyncStore): SyncReport {
        val report = SyncService(store, profile).sync(kotlinx.datetime.Clock.System.now())
        refresh()
        return report
    }

    /** A suggested file name, so exports are self-describing on disk. */
    fun suggestedExportName(): String {
        val today = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(profile.settings.streakZone())
            .date
        return "beecode-$today.json"
    }

    override fun onCleared() {
        persistDraft()
        super.onCleared()
    }
}

sealed interface Screen {
    data object Queue : Screen

    data object Problem : Screen

    data object Statistics : Screen

    data object Settings : Screen
}

data class ProblemUiState(
    val problem: ProblemDefinition,
    val source: String,
    val schedule: ProblemSchedule?,
    val history: List<ProblemReviewFinalized>,
    val latestRun: ExecutionRun?,
    val isRunning: Boolean,
    val revealedExplanation: String?,
    val aided: Boolean,
    /** A message for the learner: a rejection reason or a platform limitation. */
    val message: String?,
    val finalized: FinalizedUiState?,
    val permittedRatings: Set<ReviewRating> = emptySet(),
    val suggestedRating: ReviewRating? = null,
) {
    val canFinalize: Boolean get() = latestRun != null && permittedRatings.isNotEmpty() && finalized == null
}

data class FinalizedUiState(
    val review: ProblemReviewFinalized,
    val schedule: ProblemSchedule?,
)
