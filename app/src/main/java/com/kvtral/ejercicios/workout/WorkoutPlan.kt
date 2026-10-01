package com.kvtral.ejercicios.workout

import com.kvtral.ejercicios.data.Exercise
import com.kvtral.ejercicios.data.Routine

enum class PhaseType { PREPARE, WORK, REST_SET, REST_EXERCISE }

/**
 * Un tramo cronometrado del entrenamiento. En las fases de descanso y
 * preparación, [exercise] y [set] se refieren a la próxima serie de trabajo.
 */
data class Phase(
    val type: PhaseType,
    val exercise: Exercise,
    val exerciseIndex: Int,
    val set: Int,
    val totalSets: Int,
    val durationSeconds: Int,
)

fun buildPhases(routine: Routine, exercisesById: Map<String, Exercise>): List<Phase> {
    val items = routine.items.mapNotNull { item ->
        exercisesById[item.exerciseId]?.let { item to it }
    }.filter { (item, _) -> item.sets > 0 && item.workSeconds > 0 }
    if (items.isEmpty()) return emptyList()

    val phases = mutableListOf<Phase>()
    val (firstItem, firstExercise) = items.first()
    if (routine.prepSeconds > 0) {
        phases += Phase(PhaseType.PREPARE, firstExercise, 0, 1, firstItem.sets, routine.prepSeconds)
    }
    items.forEachIndexed { index, (item, exercise) ->
        for (set in 1..item.sets) {
            phases += Phase(PhaseType.WORK, exercise, index, set, item.sets, item.workSeconds)
            if (set < item.sets && item.restSeconds > 0) {
                phases += Phase(PhaseType.REST_SET, exercise, index, set + 1, item.sets, item.restSeconds)
            }
        }
        val next = items.getOrNull(index + 1)
        if (next != null && routine.restBetweenExercises > 0) {
            phases += Phase(PhaseType.REST_EXERCISE, next.second, index + 1, 1, next.first.sets, routine.restBetweenExercises)
        }
    }
    return phases
}
