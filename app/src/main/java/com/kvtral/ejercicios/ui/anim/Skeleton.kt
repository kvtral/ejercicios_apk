package com.kvtral.ejercicios.ui.anim

import com.kvtral.ejercicios.data.AnimationSpec
import com.kvtral.ejercicios.data.Pose
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Punto 2D en unidades de figura; y crece hacia abajo y el suelo está en y = 0. */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
}

/** Posiciones de articulaciones calculadas por cinemática directa. */
data class Skeleton(
    val hip: Pt,
    val shoulder: Pt,
    val head: Pt,
    val shoulderF: Pt, val elbowF: Pt, val handF: Pt,
    val shoulderB: Pt, val elbowB: Pt, val handB: Pt,
    val hipF: Pt, val kneeF: Pt, val footF: Pt,
    val hipB: Pt, val kneeB: Pt, val footB: Pt,
) {
    val points: List<Pt>
        get() = listOf(hip, shoulder, head, shoulderF, elbowF, handF, shoulderB, elbowB, handB,
            hipF, kneeF, footF, hipB, kneeB, footB)

    fun map(f: (Pt) -> Pt) = Skeleton(
        f(hip), f(shoulder), f(head),
        f(shoulderF), f(elbowF), f(handF),
        f(shoulderB), f(elbowB), f(handB),
        f(hipF), f(kneeF), f(footF),
        f(hipB), f(kneeB), f(footB),
    )

    fun joint(name: String): Pt = when (name) {
        "shoulder" -> shoulder
        "head" -> head
        "footF" -> footF
        "footB" -> footB
        "handF" -> handF
        "handB" -> handB
        "kneeF" -> kneeF
        "kneeB" -> kneeB
        else -> hip
    }
}

/** Medidas de la figura (mismas que tools/preview_poses.py). */
object Body {
    const val TORSO = 30f
    const val NECK = 10f
    const val HEAD_R = 6f
    const val UPPER_ARM = 14f
    const val FOREARM = 13f
    const val THIGH = 20f
    const val SHIN = 20f
    const val SHOULDER_HALF = 7f
    const val HIP_HALF = 4f
}

private fun dir(degrees: Float, mirror: Boolean = false): Pt {
    val r = Math.toRadians(degrees.toDouble())
    val x = sin(r).toFloat()
    return Pt(if (mirror) -x else x, cos(r).toFloat())
}

/**
 * Construye el esqueleto de una pose, apoyado en el suelo (el punto más bajo
 * en y = 0, menos [Pose.lift]) y con la articulación ancla en x = 0.
 */
fun buildSkeleton(pose: Pose, front: Boolean, anchor: String): Skeleton {
    val hip = Pt(0f, 0f)
    val neck = hip + dir(pose.torso) * Body.TORSO
    val head = neck + dir(pose.head ?: pose.torso) * Body.NECK
    val shF = if (front) neck + Pt(Body.SHOULDER_HALF, 0f) else neck
    val shB = if (front) neck + Pt(-Body.SHOULDER_HALF, 0f) else neck
    val hipF = if (front) hip + Pt(Body.HIP_HALF, 0f) else hip
    val hipB = if (front) hip + Pt(-Body.HIP_HALF, 0f) else hip
    val elbowF = shF + dir(pose.armF) * Body.UPPER_ARM
    val handF = elbowF + dir(pose.foreF) * Body.FOREARM
    val elbowB = shB + dir(pose.armB, front) * Body.UPPER_ARM
    val handB = elbowB + dir(pose.foreB, front) * Body.FOREARM
    val kneeF = hipF + dir(pose.thighF) * Body.THIGH
    val footF = kneeF + dir(pose.shinF) * Body.SHIN
    val kneeB = hipB + dir(pose.thighB, front) * Body.THIGH
    val footB = kneeB + dir(pose.shinB, front) * Body.SHIN
    val raw = Skeleton(hip, neck, head, shF, elbowF, handF, shB, elbowB, handB,
        hipF, kneeF, footF, hipB, kneeB, footB)

    var maxY = raw.head.y + Body.HEAD_R
    for (p in raw.points) maxY = max(maxY, p.y)
    val ax = raw.joint(anchor).x
    return raw.map { Pt(it.x - ax, it.y - maxY - pose.lift) }
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun lerpPose(a: Pose, b: Pose, t: Float) = Pose(
    torso = lerp(a.torso, b.torso, t),
    head = lerp(a.head ?: a.torso, b.head ?: b.torso, t),
    armF = lerp(a.armF, b.armF, t),
    foreF = lerp(a.foreF, b.foreF, t),
    armB = lerp(a.armB, b.armB, t),
    foreB = lerp(a.foreB, b.foreB, t),
    thighF = lerp(a.thighF, b.thighF, t),
    shinF = lerp(a.shinF, b.shinF, t),
    thighB = lerp(a.thighB, b.thighB, t),
    shinB = lerp(a.shinB, b.shinB, t),
    lift = lerp(a.lift, b.lift, t),
)

/** Suavizado ease-in-out para que el movimiento se vea natural. */
fun easeInOut(t: Float): Float = (0.5f - 0.5f * cos(Math.PI * t).toFloat())

/** Pose interpolada en el instante [timeMs] del ciclo de animación. */
fun poseAt(spec: AnimationSpec, timeMs: Long): Pose {
    val frames = spec.frames
    if (frames.size == 1) return frames[0]
    val n = frames.size
    val cycle = ((timeMs % spec.periodMs).toFloat() / spec.periodMs) * n
    val k = cycle.toInt().coerceIn(0, n - 1)
    val t = easeInOut(cycle - k)
    return lerpPose(frames[k], frames[(k + 1) % n], t)
}

data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

/** Caja que contiene todos los fotogramas, para encuadrar la figura sin saltos. */
fun animationBounds(spec: AnimationSpec): Bounds {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = 0f
    val front = spec.view == "front"
    // Muestrea también posiciones intermedias: los arcos pueden salirse de los extremos.
    for (i in 0 until spec.frames.size * 8) {
        val s = buildSkeleton(poseAt(spec, i * spec.periodMs.toLong() / (spec.frames.size * 8)), front, spec.anchor)
        for (p in s.points) {
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
        }
        minX = min(minX, s.head.x - Body.HEAD_R); maxX = max(maxX, s.head.x + Body.HEAD_R)
        minY = min(minY, s.head.y - Body.HEAD_R)
    }
    return Bounds(minX, minY, maxX, maxY)
}
