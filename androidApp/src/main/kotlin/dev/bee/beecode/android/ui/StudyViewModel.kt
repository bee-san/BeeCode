package dev.bee.beecode.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bee.beecode.app.AchievementProjection
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.FinalizeResult
import dev.bee.beecode.app.RunOutcome
import dev.bee.beecode.app.RunnerStatus
import dev.bee.beecode.app.StudyQueue
import dev.bee.beecode.app.StudyStatistics
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

    /** Persist the current draft. Called on pause and on leaving the Problem. */
    fun persistDraft() {
        val state = _problem.value ?: return
        val draft = profile.drafts.draft(state.problem.id) ?: return
        profile.study.saveDraft(draft.copy(source = state.source))
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
