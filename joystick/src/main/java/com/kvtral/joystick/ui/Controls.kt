package com.kvtral.joystick.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.min

/** Botón que avisa al pulsar y al soltar (cada dedo es independiente: admite multitáctil). */
@Composable
fun PadButton(
    label: String,
    pressed: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    width: Dp = size,
    color: Color = Color.White,
    shape: Shape = CircleShape,
    haptics: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(onChange)
    Box(
        modifier
            .size(width, size)
            .background(if (pressed) color.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.35f), shape)
            .border(2.dp, color.copy(alpha = 0.8f), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    current(true)
                    // Esperamos a que se levanten todos los dedos que tocan este botón.
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    current(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) Color.Black else color,
            fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 2) 13.sp else 20.sp,
        )
    }
}

fun pillShape() = RoundedCornerShape(50)

/**
 * Stick analógico: devuelve x e y entre -1 y 1 con Y positivo hacia arriba.
 * La zona táctil es todo el [modifier]; el dibujo se centra en el punto donde se toca.
 */
@Composable
fun VirtualStick(
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 64.dp,
    label: String = "",
) {
    val current by rememberUpdatedState(onMove)
    var center by remember { mutableStateOf<Offset?>(null) }
    var knob by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier.pointerInput(Unit) {
            val r = radius.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                // Stick flotante: el centro está donde se apoya el dedo, sin salirse de la zona.
                val origin = Offset(
                    down.position.x.coerceIn(r, size.width - r),
                    down.position.y.coerceIn(r, size.height - r),
                )
                center = origin
                fun update(pos: Offset) {
                    var d = pos - origin
                    val len = hypot(d.x, d.y)
                    if (len > r) d *= r / len
                    knob = d
                    current(d.x / r, -d.y / r)
                }
                update(down.position)
                val id = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == id } ?: break
                    if (!change.pressed) break
                    if (change.positionChange() != Offset.Zero) update(change.position)
                    change.consume()
                }
                center = null
                knob = Offset.Zero
                current(0f, 0f)
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = radius.toPx()
            val c = center ?: Offset(size.width / 2, size.height / 2)
            drawCircle(Color.White.copy(alpha = if (center != null) 0.25f else 0.12f), r, c)
            drawCircle(Color.White.copy(alpha = 0.6f), r, c, style = Stroke(3f))
            drawCircle(Color.White.copy(alpha = 0.8f), min(r * 0.45f, 48.dp.toPx()), c + knob)
        }
        if (label.isNotEmpty()) {
            Text(label, Modifier.align(Alignment.BottomCenter), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
    }
}
