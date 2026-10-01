package com.kvtral.ejercicios.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvtral.ejercicios.data.formatDuration
import com.kvtral.ejercicios.ui.anim.StickFigure
import com.kvtral.ejercicios.ui.components.figureColors
import com.kvtral.ejercicios.workout.PhaseType
import com.kvtral.ejercicios.workout.WorkoutUiState
import com.kvtral.ejercicios.workout.WorkoutViewModel
import kotlin.math.ceil

@Composable
fun WorkoutScreen(onExit: () -> Unit, vm: WorkoutViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sound by vm.repository.soundEnabled.collectAsStateWithLifecycle()
    val vibration by vm.repository.vibrationEnabled.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }

    KeepScreenOn()
    BackHandler(enabled = !state.isFinished) { confirmExit = true }

    if (state.isFinished) {
        FinishedContent(state, onExit, onRepeatLast = vm::skipBack)
        return
    }
    val phase = state.phase ?: run {
        // Rutina sin ejercicios válidos: no hay nada que cronometrar.
        LaunchedEffect(Unit) { onExit() }
        return
    }

    val isWork = phase.type == PhaseType.WORK
    val scheme = MaterialTheme.colorScheme
    val accent by animateColorAsState(
        when (phase.type) {
            PhaseType.WORK -> scheme.primary
            PhaseType.PREPARE -> scheme.secondary
            else -> scheme.tertiary
        },
        label = "accent",
    )
    val title = when (phase.type) {
        PhaseType.PREPARE -> "PREPÁRATE"
        PhaseType.WORK -> "¡A TOPE!"
        PhaseType.REST_SET -> "DESCANSO"
        PhaseType.REST_EXERCISE -> "DESCANSO"
    }

    Column(
        Modifier.fillMaxSize().background(scheme.background).systemBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmExit = true }) { Icon(Icons.Filled.Close, "Salir") }
            Column(Modifier.weight(1f)) {
                Text(state.routineName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(4.dp)),
                    color = accent,
                )
            }
            IconButton(onClick = { vm.repository.setSoundEnabled(!sound) }) {
                Icon(
                    if (sound) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    if (sound) "Silenciar" else "Activar sonido",
                )
            }
            IconButton(onClick = { vm.repository.setVibrationEnabled(!vibration) }) {
                Icon(
                    if (vibration) Icons.Filled.Vibration else Icons.Filled.MobileOff,
                    if (vibration) "Desactivar vibración" else "Activar vibración",
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(title, color = accent, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 2.sp)
        Text(
            (if (isWork) "" else "Siguiente: ") + phase.exercise.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Ejercicio ${phase.exerciseIndex + 1}/${state.totalExercises} · Serie ${phase.set}/${phase.totalSets}",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = accent.copy(alpha = 0.10f),
        ) {
            StickFigure(
                phase.exercise.animation,
                figureColors(scheme.onSurface),
                Modifier.fillMaxSize().padding(16.dp),
                playing = !state.isPaused,
                // En descanso se muestra el siguiente ejercicio más lento, como vista previa.
                speed = if (isWork) 1f else 0.6f,
            )
        }

        Spacer(Modifier.height(12.dp))
        CountdownRing(state, accent, Modifier.size(170.dp))
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(onClick = vm::skipBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Anterior", Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = vm::togglePause,
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
            ) {
                Icon(
                    if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    if (state.isPaused) "Continuar" else "Pausa",
                    Modifier.size(44.dp),
                )
            }
            IconButton(onClick = vm::skipForward, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, "Siguiente", Modifier.size(36.dp))
            }
        }
        val next = state.nextPhase
        Text(
            when {
                !isWork -> "Próxima serie: ${next?.durationSeconds ?: 0}s de trabajo"
                next == null -> "¡Último esfuerzo!"
                next.type != PhaseType.WORK -> "Después: descanso ${next.durationSeconds}s"
                else -> "Después: ${next.exercise.name}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Terminar entrenamiento?") },
            text = { Text("Perderás el progreso de esta sesión.") },
            confirmButton = { TextButton(onClick = onExit) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir") } },
        )
    }
}

@Composable
private fun CountdownRing(state: WorkoutUiState, color: Color, modifier: Modifier) {
    val phase = state.phase ?: return
    val totalMs = phase.durationSeconds * 1000f
    val fraction = (state.remainingMs / totalMs).coerceIn(0f, 1f)
    val track = color.copy(alpha = 0.18f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
            drawArc(color, -90f, 360f * fraction, false, topLeft, arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                ceil(state.remainingMs / 1000.0).toInt().toString(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = color,
            )
            if (state.isPaused) Text("EN PAUSA", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FinishedContent(state: WorkoutUiState, onExit: () -> Unit, onRepeatLast: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.EmojiEvents, null, Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("¡Rutina completada!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(state.routineName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Text("Tiempo total: ${formatDuration((state.elapsedMs / 1000).toInt())}", style = MaterialTheme.typography.titleLarge)
        Text("${state.totalExercises} ejercicios · ${state.totalSets} series", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Terminar") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRepeatLast, modifier = Modifier.fillMaxWidth()) { Text("Repetir última serie") }
    }
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
