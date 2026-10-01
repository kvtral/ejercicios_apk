package com.kvtral.ejercicios.ui.anim

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.kvtral.ejercicios.data.AnimationSpec
import kotlin.math.atan2
import kotlin.math.min

data class FigureColors(
    val body: Color,
    val farLimb: Color,
    val prop: Color,
    val band: Color,
    val ground: Color,
    val mat: Color,
)

/**
 * Figura animada que demuestra un ejercicio. Mientras [playing] es falso la
 * animación se congela en el fotograma actual.
 */
@Composable
fun StickFigure(
    spec: AnimationSpec,
    colors: FigureColors,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    speed: Float = 1f,
) {
    var timeMs by remember(spec) { mutableLongStateOf(0L) }
    LaunchedEffect(spec, playing, speed) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                timeMs += ((now - last) * speed).toLong()
                last = now
            }
        }
    }
    val bounds = remember(spec) { animationBounds(spec) }
    val front = spec.view == "front"

    Canvas(modifier) {
        val skeleton = buildSkeleton(poseAt(spec, timeMs), front, spec.anchor)
        drawFigure(spec, skeleton, bounds, colors)
    }
}

private fun DrawScope.drawFigure(spec: AnimationSpec, s: Skeleton, b: Bounds, c: FigureColors) {
    val padX = 10f
    val padTop = 6f
    val groundSpace = 6f
    val width = (b.maxX - b.minX) + padX * 2
    val height = (b.maxY - b.minY) + padTop + groundSpace
    val scale = min(size.width / width, size.height / height)
    val originX = size.width / 2 - (b.minX + b.maxX) / 2 * scale
    val groundY = size.height / 2 + ((height / 2) - groundSpace) * scale
    fun t(p: Pt) = Offset(originX + p.x * scale, groundY + p.y * scale)

    val stroke = 3.4f * scale
    val ground = 1.2f * scale

    if (spec.mat) {
        val left = t(Pt(b.minX - 6f, 0f)).x
        val right = t(Pt(b.maxX + 6f, 0f)).x
        drawRoundRect(c.mat, Offset(left, groundY - 1.4f * scale), Size(right - left, 1.8f * scale),
            CornerRadius(0.9f * scale))
    }
    drawLine(c.ground, Offset(0f, groundY + ground / 2), Offset(size.width, groundY + ground / 2), ground)

    fun bone(a: Pt, z: Pt, color: Color, w: Float = stroke) =
        drawLine(color, t(a), t(z), w, StrokeCap.Round)

    val front = spec.view == "front"
    val farColor = if (front) c.body else c.farLimb

    // Extremidades lejanas primero para que queden por detrás.
    bone(s.shoulderB, s.elbowB, farColor); bone(s.elbowB, s.handB, farColor)
    bone(s.hipB, s.kneeB, farColor); bone(s.kneeB, s.footB, farColor)
    if (spec.prop == "band_feet") {
        drawLine(c.band.copy(alpha = if (front) 1f else 0.5f), t(s.footB), t(s.handB), 1.1f * scale)
    }
    if (spec.prop == "dumbbells") dumbbell(t(s.handB), s.elbowB, s.handB, scale, c.prop.copy(alpha = if (front) 1f else 0.6f))

    bone(s.hip, s.shoulder, c.body, stroke * 1.25f)
    if (front) {
        bone(s.shoulderB, s.shoulderF, c.body)
        bone(s.hipB, s.hipF, c.body)
    }
    drawCircle(c.body, Body.HEAD_R * scale, t(s.head))

    bone(s.shoulderF, s.elbowF, c.body); bone(s.elbowF, s.handF, c.body)
    bone(s.hipF, s.kneeF, c.body); bone(s.kneeF, s.footF, c.body)

    when (spec.prop) {
        "dumbbells" -> dumbbell(t(s.handF), s.elbowF, s.handF, scale, c.prop)
        "kettlebell" -> kettlebell(t(s.handF), scale, c.prop)
        "band_feet" -> drawLine(c.band, t(s.footF), t(s.handF), 1.1f * scale)
        "band_knees" -> drawCircle(c.band, 3.2f * scale, t(s.kneeF), style = Stroke(1.4f * scale))
    }
}

/** Mancuerna perpendicular al antebrazo. */
private fun DrawScope.dumbbell(at: Offset, elbow: Pt, hand: Pt, scale: Float, color: Color) {
    val angle = Math.toDegrees(atan2((hand.y - elbow.y).toDouble(), (hand.x - elbow.x).toDouble())).toFloat()
    rotate(angle + 90f, at) {
        drawLine(color, Offset(at.x - 4.5f * scale, at.y), Offset(at.x + 4.5f * scale, at.y), 1.2f * scale)
        for (sign in listOf(-1f, 1f)) {
            drawRoundRect(color, Offset(at.x + sign * 4f * scale - 1.3f * scale, at.y - 2.6f * scale),
                Size(2.6f * scale, 5.2f * scale), CornerRadius(0.8f * scale))
        }
    }
}

/** Kettlebell colgando de la mano. */
private fun DrawScope.kettlebell(at: Offset, scale: Float, color: Color) {
    drawCircle(color, 2.2f * scale, Offset(at.x, at.y + 1.6f * scale), style = Stroke(1f * scale))
    drawCircle(color, 4.4f * scale, Offset(at.x, at.y + 7f * scale))
}
