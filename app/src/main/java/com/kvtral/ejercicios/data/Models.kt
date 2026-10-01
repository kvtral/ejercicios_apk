package com.kvtral.ejercicios.data

import kotlinx.serialization.Serializable

enum class Equipment(val label: String) {
    BODYWEIGHT("Peso corporal"),
    DUMBBELL("Mancuernas"),
    KETTLEBELL("Kettlebell"),
    BAND("Bandas elásticas"),
}

/**
 * Pose de la figura animada. Todos los ángulos son absolutos, en grados:
 * 0 = el segmento apunta hacia abajo, 90 = hacia delante (derecha),
 * 180 = hacia arriba, -90 = hacia atrás. "F" es la extremidad cercana y "B"
 * la lejana (en vista frontal, derecha e izquierda respectivamente).
 */
@Serializable
data class Pose(
    val torso: Float = 180f,
    val head: Float? = null,
    val armF: Float = 0f,
    val foreF: Float = 0f,
    val armB: Float = 0f,
    val foreB: Float = 0f,
    val thighF: Float = 0f,
    val shinF: Float = 0f,
    val thighB: Float = 0f,
    val shinB: Float = 0f,
    val lift: Float = 0f,
)

@Serializable
data class AnimationSpec(
    /** "side" o "front". */
    val view: String = "side",
    /** Articulación cuya posición horizontal permanece fija. */
    val anchor: String = "hip",
    /** Duración de un ciclo completo por todos los fotogramas clave. */
    val periodMs: Int = 2000,
    /** none, dumbbells, kettlebell, band_feet, band_knees. */
    val prop: String = "none",
    val mat: Boolean = false,
    val frames: List<Pose>,
)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val equipment: Equipment,
    val muscles: List<String> = emptyList(),
    val description: String = "",
    val tips: List<String> = emptyList(),
    val animation: AnimationSpec,
)

@Serializable
data class RoutineItem(
    val exerciseId: String,
    val sets: Int = 3,
    val workSeconds: Int = 40,
    /** Descanso entre series del mismo ejercicio. */
    val restSeconds: Int = 20,
)

@Serializable
data class Routine(
    val id: String,
    val name: String,
    val description: String = "",
    /** Descanso al pasar de un ejercicio al siguiente. */
    val restBetweenExercises: Int = 60,
    /** Cuenta atrás inicial antes del primer ejercicio. */
    val prepSeconds: Int = 10,
    val items: List<RoutineItem>,
    val isCustom: Boolean = false,
)

@Serializable
data class Catalog(
    val exercises: List<Exercise>,
    val routines: List<Routine>,
)

/** Material necesario para una rutina (unión del material de sus ejercicios). */
fun Routine.requiredEquipment(exercises: Map<String, Exercise>): Set<Equipment> =
    items.mapNotNull { exercises[it.exerciseId]?.equipment }.toSet()

/** Duración total estimada en segundos (igual a la del plan de entrenamiento). */
fun Routine.totalSeconds(): Int {
    var total = prepSeconds
    items.forEachIndexed { index, item ->
        total += item.sets * item.workSeconds + (item.sets - 1) * item.restSeconds
        if (index < items.lastIndex) total += restBetweenExercises
    }
    return total
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}
