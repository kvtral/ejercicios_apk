package com.kvtral.joystick.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvtral.joystick.stream.Button as Btn
import com.kvtral.joystick.stream.StreamStatus
import com.kvtral.joystick.xhome.XboxConsole
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

private val XboxGreen = Color(0xFF6CC24A)
private val XboxRed = Color(0xFFE74C3C)
private val XboxBlue = Color(0xFF3498DB)
private val XboxYellow = Color(0xFFF1C40F)

@Composable
fun GamepadScreen(console: XboxConsole, vm: JoystickViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    val showVideo by vm.showVideo.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()

    ImmersiveMode()

    Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        if (showVideo) VideoView(vm)

        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Izquierda: gatillo, bumper y stick izquierdo.
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadButton("LT", pressed.leftTrigger > 0f, { vm.setTrigger(true, if (it) 1f else 0f) },
                        size = 44.dp, width = 84.dp, shape = pillShape(), haptics = haptics)
                    PadButton("LB", pressed.isPressed(Btn.LeftShoulder), { vm.setButton(Btn.LeftShoulder, it) },
                        size = 44.dp, width = 84.dp, shape = pillShape(), haptics = haptics)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    VirtualStick(vm::setLeftStick, Modifier.fillMaxSize(), label = "Stick izquierdo")
                    PadButton("L3", pressed.isPressed(Btn.LeftThumb), { vm.setButton(Btn.LeftThumb, it) },
                        Modifier.align(Alignment.BottomStart), size = 40.dp, haptics = haptics)
                }
            }

            // Centro: botones de sistema, cruceta y stick derecho.
            Column(Modifier.weight(1.1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setShowVideo(!showVideo) }) {
                        Icon(if (showVideo) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            "Mostrar vídeo", tint = Color.White.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = { vm.setHaptics(!haptics) }) {
                        Icon(Icons.Default.Vibration, "Vibración",
                            tint = Color.White.copy(alpha = if (haptics) 0.7f else 0.25f))
                    }
                    IconButton(onClick = vm::disconnect) {
                        Icon(Icons.Default.Close, "Desconectar", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PadButton("View", pressed.isPressed(Btn.View), { vm.setButton(Btn.View, it) },
                        size = 40.dp, haptics = haptics)
                    PadButton("Xbox", pressed.isPressed(Btn.Nexus), { vm.setButton(Btn.Nexus, it) },
                        size = 54.dp, color = XboxGreen, haptics = haptics)
                    PadButton("Menu", pressed.isPressed(Btn.Menu), { vm.setButton(Btn.Menu, it) },
                        size = 40.dp, haptics = haptics)
                }
                Spacer(Modifier.height(4.dp))
                StatusLine(console, status)
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    DPad(pressed, vm, haptics, Modifier.padding(bottom = 4.dp))
                    VirtualStick(vm::setRightStick, Modifier.weight(1f).fillMaxHeight(), radius = 56.dp,
                        label = "Stick derecho")
                }
            }

            // Derecha: gatillo, bumper, ABXY y R3.
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadButton("RB", pressed.isPressed(Btn.RightShoulder), { vm.setButton(Btn.RightShoulder, it) },
                        size = 44.dp, width = 84.dp, shape = pillShape(), haptics = haptics)
                    PadButton("RT", pressed.rightTrigger > 0f, { vm.setTrigger(false, if (it) 1f else 0f) },
                        size = 44.dp, width = 84.dp, shape = pillShape(), haptics = haptics)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FaceButtons(pressed, vm, haptics)
                    PadButton("R3", pressed.isPressed(Btn.RightThumb), { vm.setButton(Btn.RightThumb, it) },
                        Modifier.align(Alignment.BottomEnd), size = 40.dp, haptics = haptics)
                }
            }
        }

        StatusOverlay(status, onRetry = { vm.connect(console) }, onBack = vm::disconnect)
    }
}

@Composable
private fun FaceButtons(pressed: com.kvtral.joystick.stream.GamepadState, vm: JoystickViewModel, haptics: Boolean) {
    val s = 62.dp
    Diamond(
        size = s,
        top = { PadButton("Y", pressed.isPressed(Btn.Y), { vm.setButton(Btn.Y, it) }, size = s, color = XboxYellow, haptics = haptics) },
        left = { PadButton("X", pressed.isPressed(Btn.X), { vm.setButton(Btn.X, it) }, size = s, color = XboxBlue, haptics = haptics) },
        right = { PadButton("B", pressed.isPressed(Btn.B), { vm.setButton(Btn.B, it) }, size = s, color = XboxRed, haptics = haptics) },
        bottom = { PadButton("A", pressed.isPressed(Btn.A), { vm.setButton(Btn.A, it) }, size = s, color = XboxGreen, haptics = haptics) },
    )
}

@Composable
private fun DPad(pressed: com.kvtral.joystick.stream.GamepadState, vm: JoystickViewModel, haptics: Boolean, modifier: Modifier) {
    val s = 50.dp
    val shape = RoundedCornerShape(10.dp)
    Diamond(
        size = s,
        modifier = modifier,
        top = { PadButton("▲", pressed.isPressed(Btn.DPadUp), { vm.setButton(Btn.DPadUp, it) }, size = s, shape = shape, haptics = haptics) },
        left = { PadButton("◀", pressed.isPressed(Btn.DPadLeft), { vm.setButton(Btn.DPadLeft, it) }, size = s, shape = shape, haptics = haptics) },
        right = { PadButton("▶", pressed.isPressed(Btn.DPadRight), { vm.setButton(Btn.DPadRight, it) }, size = s, shape = shape, haptics = haptics) },
        bottom = { PadButton("▼", pressed.isPressed(Btn.DPadDown), { vm.setButton(Btn.DPadDown, it) }, size = s, shape = shape, haptics = haptics) },
    )
}

/** Cuatro controles en cruz (cruceta o botones ABXY). */
@Composable
private fun Diamond(
    size: Dp,
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        top()
        Row {
            left()
            Spacer(Modifier.width(size))
            right()
        }
        bottom()
    }
}

@Composable
private fun StatusLine(console: XboxConsole, status: StreamStatus) {
    val (color, text) = when (status) {
        StreamStatus.Connected -> XboxGreen to "Conectado a ${console.name}"
        is StreamStatus.Connecting -> XboxYellow to "Conectando…"
        is StreamStatus.Failed -> XboxRed to "Sin conexión"
        StreamStatus.Idle -> Color.Gray to "Desconectado"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusOverlay(status: StreamStatus, onRetry: () -> Unit, onBack: () -> Unit) {
    if (status == StreamStatus.Connected) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Card(Modifier.width(420.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (status) {
                    is StreamStatus.Connecting, StreamStatus.Idle -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text((status as? StreamStatus.Connecting)?.step ?: "Preparando…", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onBack) { Text("Cancelar") }
                    }
                    is StreamStatus.Failed -> {
                        Text(status.message, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onBack) { Text("Volver") }
                            Button(onClick = onRetry) { Text("Reintentar") }
                        }
                    }
                    StreamStatus.Connected -> Unit
                }
            }
        }
    }
}

@Composable
private fun VideoView(vm: JoystickViewModel) {
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(vm.stream.eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                vm.stream.setVideoSink(this)
            }
        },
        onRelease = {
            vm.stream.setVideoSink(null)
            it.release()
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Pantalla completa y siempre encendida mientras se juega. */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}
