package com.kvtral.ejercicios.workout

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kvtral.ejercicios.EjerciciosApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WorkoutUiState(
    val routineName: String = "",
    val phase: Phase? = null,
    val nextPhase: Phase? = null,
    val remainingMs: Long = 0,
    val progress: Float = 0f,
    val elapsedMs: Long = 0,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val totalExercises: Int = 0,
    val totalSets: Int = 0,
)

class WorkoutViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    val repository = (app as EjerciciosApp).repository
    private val routine = repository.routine(checkNotNull(savedState.get<String>("id")))
    private val session = WorkoutSession(
        routine?.let { buildPhases(it, repository.exercisesById) }.orEmpty(),
    )
    private val feedback = Feedback(app)

    private val _state = MutableStateFlow(WorkoutUiState(routineName = routine?.name.orEmpty()))
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    private var ticker: Job? = null

    init {
        publish()
        if (!session.isFinished) {
            session.currentPhase?.let { emit(listOf(WorkoutEvent.PhaseStarted(it))) }
            start()
        }
    }

    fun togglePause() {
        if (session.isFinished) return
        if (ticker?.isActive == true) {
            ticker?.cancel()
            ticker = null
            _state.value = _state.value.copy(isPaused = true)
        } else {
            start()
        }
    }

    fun skipForward() = emit(session.skipForward()).also { publish() }

    fun skipBack() {
        val wasFinished = session.isFinished
        emit(session.skipBack())
        publish()
        if (wasFinished) start()
    }

    private fun start() {
        _state.value = _state.value.copy(isPaused = false)
        ticker = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive && !session.isFinished) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                emit(session.tick(now - last))
                last = now
                publish()
            }
        }
    }

    private fun emit(events: List<WorkoutEvent>) {
        val sound = repository.soundEnabled.value
        val vibrate = repository.vibrationEnabled.value
        events.forEach { e ->
            when (e) {
                is WorkoutEvent.Countdown -> feedback.countdown(sound)
                is WorkoutEvent.PhaseStarted -> feedback.phaseStart(e.phase.type, sound, vibrate)
                WorkoutEvent.Finished -> feedback.finished(sound, vibrate)
            }
        }
    }

    private fun publish() {
        val phases = session.phases
        _state.value = _state.value.copy(
            phase = session.currentPhase,
            nextPhase = phases.getOrNull(session.phaseIndex + 1),
            remainingMs = session.remainingMs,
            progress = session.progress,
            elapsedMs = session.elapsedMs,
            isFinished = session.isFinished,
            isPaused = _state.value.isPaused && !session.isFinished,
            totalExercises = phases.maxOfOrNull { it.exerciseIndex }?.plus(1) ?: 0,
            totalSets = phases.count { it.type == PhaseType.WORK },
        )
    }

    override fun onCleared() {
        feedback.release()
    }
}
