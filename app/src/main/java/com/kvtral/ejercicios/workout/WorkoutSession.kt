package com.kvtral.ejercicios.workout

sealed interface WorkoutEvent {
    /** Quedan [secondsLeft] segundos (3, 2, 1) en la fase actual. */
    data class Countdown(val secondsLeft: Int) : WorkoutEvent
    data class PhaseStarted(val phase: Phase) : WorkoutEvent
    data object Finished : WorkoutEvent
}

/**
 * Máquina de estados del cronómetro, independiente de Android. Se alimenta
 * con [tick] usando el tiempo real transcurrido y devuelve los eventos que
 * deben generar sonido o vibración.
 */
class WorkoutSession(val phases: List<Phase>) {

    var phaseIndex: Int = 0
        private set
    var remainingMs: Long = phases.firstOrNull()?.durationSeconds?.times(1000L) ?: 0L
        private set
    var isFinished: Boolean = phases.isEmpty()
        private set
    /** Tiempo total entrenado (incluye descansos, excluye pausas). */
    var elapsedMs: Long = 0L
        private set

    val currentPhase: Phase? get() = phases.getOrNull(phaseIndex)

    val totalMs: Long = phases.sumOf { it.durationSeconds * 1000L }

    /** Progreso global 0..1 según el tiempo planificado. */
    val progress: Float
        get() {
            if (isFinished || totalMs == 0L) return 1f
            val done = phases.take(phaseIndex).sumOf { it.durationSeconds * 1000L } +
                (phases[phaseIndex].durationSeconds * 1000L - remainingMs)
            return (done.toFloat() / totalMs).coerceIn(0f, 1f)
        }

    fun tick(deltaMs: Long): List<WorkoutEvent> {
        if (isFinished || deltaMs <= 0) return emptyList()
        val events = mutableListOf<WorkoutEvent>()
        var delta = deltaMs
        while (delta > 0 && !isFinished) {
            val before = remainingMs
            val step = minOf(delta, remainingMs)
            remainingMs -= step
            elapsedMs += step
            delta -= step
            // Pitidos al cruzar 3, 2 y 1 segundos restantes.
            for (s in 3 downTo 1) {
                val mark = s * 1000L
                if (before > mark && remainingMs <= mark) events += WorkoutEvent.Countdown(s)
            }
            if (remainingMs <= 0) {
                advance(events)
            }
        }
        return events
    }

    fun skipForward(): List<WorkoutEvent> {
        if (isFinished) return emptyList()
        return mutableListOf<WorkoutEvent>().also { advance(it) }
    }

    /** Reinicia la fase actual o, si apenas ha empezado, vuelve a la anterior. */
    fun skipBack(): List<WorkoutEvent> {
        if (phases.isEmpty()) return emptyList()
        if (isFinished) {
            isFinished = false
            phaseIndex = phases.lastIndex
        } else {
            val phase = phases[phaseIndex]
            val elapsedInPhase = phase.durationSeconds * 1000L - remainingMs
            if (elapsedInPhase < 2000 && phaseIndex > 0) phaseIndex--
        }
        remainingMs = phases[phaseIndex].durationSeconds * 1000L
        return listOf(WorkoutEvent.PhaseStarted(phases[phaseIndex]))
    }

    private fun advance(events: MutableList<WorkoutEvent>) {
        if (phaseIndex >= phases.lastIndex) {
            remainingMs = 0
            isFinished = true
            events += WorkoutEvent.Finished
        } else {
            phaseIndex++
            remainingMs = phases[phaseIndex].durationSeconds * 1000L
            events += WorkoutEvent.PhaseStarted(phases[phaseIndex])
        }
    }
}
