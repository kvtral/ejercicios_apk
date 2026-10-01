package com.kvtral.ejercicios

import com.kvtral.ejercicios.data.AnimationSpec
import com.kvtral.ejercicios.data.Equipment
import com.kvtral.ejercicios.data.Exercise
import com.kvtral.ejercicios.data.Pose
import com.kvtral.ejercicios.data.Routine
import com.kvtral.ejercicios.data.RoutineItem
import com.kvtral.ejercicios.data.totalSeconds
import com.kvtral.ejercicios.workout.PhaseType
import com.kvtral.ejercicios.workout.WorkoutEvent
import com.kvtral.ejercicios.workout.WorkoutSession
import com.kvtral.ejercicios.workout.buildPhases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionTest {

    private fun ex(id: String) = Exercise(id, id, Equipment.BODYWEIGHT, animation = AnimationSpec(frames = listOf(Pose())))
    private val exercises = listOf(ex("a"), ex("b")).associateBy { it.id }
    private val routine = Routine(
        id = "r", name = "R", restBetweenExercises = 30, prepSeconds = 5,
        items = listOf(RoutineItem("a", sets = 2, workSeconds = 20, restSeconds = 10), RoutineItem("b", sets = 1, workSeconds = 15)),
    )

    @Test
    fun phasesFollowSetsAndRests() {
        val phases = buildPhases(routine, exercises)
        assertEquals(
            listOf(PhaseType.PREPARE, PhaseType.WORK, PhaseType.REST_SET, PhaseType.WORK, PhaseType.REST_EXERCISE, PhaseType.WORK),
            phases.map { it.type },
        )
        assertEquals(listOf(5, 20, 10, 20, 30, 15), phases.map { it.durationSeconds })
        // Los descansos anuncian la serie/ejercicio siguiente.
        assertEquals(2, phases[2].set)
        assertEquals("b", phases[4].exercise.id)
        assertEquals(routine.totalSeconds(), phases.sumOf { it.durationSeconds })
    }

    @Test
    fun zeroRestsAreSkippedAndUnknownExercisesIgnored() {
        val r = routine.copy(prepSeconds = 0, restBetweenExercises = 0,
            items = routine.items.map { it.copy(restSeconds = 0) } + RoutineItem("missing"))
        val phases = buildPhases(r, exercises)
        assertTrue(phases.all { it.type == PhaseType.WORK })
        assertEquals(3, phases.size)
    }

    @Test
    fun tickEmitsCountdownAndPhaseChanges() {
        val session = WorkoutSession(buildPhases(routine, exercises))
        val events = session.tick(5_000)
        assertEquals(
            listOf(WorkoutEvent.Countdown(3), WorkoutEvent.Countdown(2), WorkoutEvent.Countdown(1)),
            events.filterIsInstance<WorkoutEvent.Countdown>(),
        )
        val started = events.filterIsInstance<WorkoutEvent.PhaseStarted>().single()
        assertEquals(PhaseType.WORK, started.phase.type)
        assertEquals(20_000, session.remainingMs)
    }

    @Test
    fun largeTickCarriesOverAndFinishes() {
        val session = WorkoutSession(buildPhases(routine, exercises))
        session.tick(6_000)
        assertEquals(1, session.phaseIndex)
        assertEquals(19_000, session.remainingMs)
        val events = session.tick(1_000_000)
        assertTrue(session.isFinished)
        assertEquals(WorkoutEvent.Finished, events.last())
        assertEquals(session.totalMs, session.elapsedMs)
        assertEquals(1f, session.progress)
    }

    @Test
    fun skipControls() {
        val session = WorkoutSession(buildPhases(routine, exercises))
        session.skipForward()
        assertEquals(1, session.phaseIndex)
        session.tick(5_000)
        session.skipBack() // más de 2 s dentro: reinicia la fase
        assertEquals(1, session.phaseIndex)
        assertEquals(20_000, session.remainingMs)
        session.skipBack() // recién empezada: vuelve a la anterior
        assertEquals(0, session.phaseIndex)
        repeat(10) { session.skipForward() }
        assertTrue(session.isFinished)
        session.skipBack()
        assertFalse(session.isFinished)
        assertEquals(session.phases.lastIndex, session.phaseIndex)
    }
}
