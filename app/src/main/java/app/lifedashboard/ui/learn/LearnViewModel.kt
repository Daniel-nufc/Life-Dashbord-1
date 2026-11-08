package app.lifedashboard.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.ExamEntity
import app.lifedashboard.data.db.GradeEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TopicEntity
import app.lifedashboard.data.repository.StudyOverview
import app.lifedashboard.data.repository.StudyRepository
import app.lifedashboard.data.repository.TimetableRepository
import app.lifedashboard.planner.ReviewRating
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand des Fokus-Timers (Pomodoro 25/5, nach 4 Runden 15 Min Pause). */
data class FocusState(
    val phase: Phase = Phase.IDLE,
    val remainingSeconds: Int = FOCUS_SECONDS,
    val totalSeconds: Int = FOCUS_SECONDS,
    val running: Boolean = false,
    val completedRounds: Int = 0,
    val subjectId: Long? = null,
) {
    enum class Phase { IDLE, FOCUS, BREAK }

    companion object {
        const val FOCUS_SECONDS = 25 * 60
        const val SHORT_BREAK = 5 * 60
        const val LONG_BREAK = 15 * 60
    }
}

@HiltViewModel
class LearnViewModel @Inject constructor(
    private val study: StudyRepository,
    private val timetable: TimetableRepository,
) : ViewModel() {

    val overview: StateFlow<StudyOverview?> = study.overview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _focus = MutableStateFlow(FocusState())
    val focus: StateFlow<FocusState> = _focus.asStateFlow()
    private var ticker: Job? = null
    private var focusStartedAt = 0L

    // ---------------------------------------------------------------- Klausuren & Themen
    fun saveExam(exam: ExamEntity, newTopics: List<String>) = launch { study.saveExam(exam, newTopics) }
    fun deleteExam(id: Long) = launch { study.deleteExam(id) }
    fun addTopic(examId: Long, title: String) = launch {
        study.saveTopic(TopicEntity(examId = examId, title = title, effortMinutes = 60, progress = 0, intervalDays = 0, nextReviewEpochDay = null, lastReviewedEpochDay = null))
    }
    fun saveTopic(topic: TopicEntity) = launch { study.saveTopic(topic) }
    fun deleteTopic(id: Long) = launch { study.deleteTopic(id) }
    fun setProgress(topic: TopicEntity, progress: Int) = launch { study.setProgress(topic, progress) }
    fun review(topic: TopicEntity, rating: ReviewRating) = launch { study.review(topic, rating) }

    // ---------------------------------------------------------------- Noten & Fächer
    fun saveGrade(grade: GradeEntity) = launch { study.saveGrade(grade) }
    fun deleteGrade(id: Long) = launch { study.deleteGrade(id) }
    fun saveSubject(subject: SubjectEntity) = launch { timetable.saveSubject(subject) }

    // ---------------------------------------------------------------- Fokus-Timer
    fun selectFocusSubject(id: Long?) {
        _focus.value = _focus.value.copy(subjectId = id)
    }

    fun startOrPause() {
        val state = _focus.value
        if (state.running) {
            ticker?.cancel()
            _focus.value = state.copy(running = false)
            return
        }
        val phase = if (state.phase == FocusState.Phase.IDLE) FocusState.Phase.FOCUS else state.phase
        if (phase == FocusState.Phase.FOCUS && state.remainingSeconds == state.totalSeconds) {
            focusStartedAt = System.currentTimeMillis()
        }
        _focus.value = state.copy(phase = phase, running = true)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            var last = System.currentTimeMillis()
            while (_focus.value.running && _focus.value.remainingSeconds > 0) {
                delay(250)
                val now = System.currentTimeMillis()
                val passed = ((now - last) / 1000).toInt()
                if (passed > 0) {
                    last += passed * 1000L
                    _focus.value = _focus.value.copy(remainingSeconds = (_focus.value.remainingSeconds - passed).coerceAtLeast(0))
                }
            }
            if (_focus.value.remainingSeconds == 0) finishPhase()
        }
    }

    fun reset() {
        ticker?.cancel()
        _focus.value = FocusState(subjectId = _focus.value.subjectId, completedRounds = _focus.value.completedRounds)
    }

    fun skipBreak() {
        ticker?.cancel()
        _focus.value = _focus.value.copy(
            phase = FocusState.Phase.IDLE,
            running = false,
            remainingSeconds = FocusState.FOCUS_SECONDS,
            totalSeconds = FocusState.FOCUS_SECONDS,
        )
    }

    private suspend fun finishPhase() {
        val state = _focus.value
        if (state.phase == FocusState.Phase.FOCUS) {
            study.recordSession(state.subjectId, state.totalSeconds / 60, focusStartedAt)
            val rounds = state.completedRounds + 1
            val breakSeconds = if (rounds % 4 == 0) FocusState.LONG_BREAK else FocusState.SHORT_BREAK
            _focus.value = state.copy(
                phase = FocusState.Phase.BREAK,
                running = false,
                completedRounds = rounds,
                remainingSeconds = breakSeconds,
                totalSeconds = breakSeconds,
            )
        } else {
            skipBreak()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
