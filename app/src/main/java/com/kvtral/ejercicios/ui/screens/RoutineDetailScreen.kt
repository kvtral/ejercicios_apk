package com.kvtral.ejercicios.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvtral.ejercicios.data.formatDuration
import com.kvtral.ejercicios.data.requiredEquipment
import com.kvtral.ejercicios.data.totalSeconds
import com.kvtral.ejercicios.ui.anim.StickFigure
import com.kvtral.ejercicios.ui.components.figureColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    routineId: String,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val repo = rememberRepository()
    // Se observa para refrescar tras editar.
    val custom by repo.customRoutines.collectAsStateWithLifecycle()
    val routine = custom.firstOrNull { it.id == routineId } ?: repo.routine(routineId) ?: return
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routine.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (routine.isCustom) {
                        IconButton(onClick = { onEdit(routine.id) }) { Icon(Icons.Filled.Edit, "Editar") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
                    } else {
                        IconButton(onClick = { onDuplicate(routine.id) }) {
                            Icon(Icons.Filled.ContentCopy, "Duplicar y editar")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onStart(routine.id) },
                    enabled = routine.items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Empezar entrenamiento", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (routine.description.isNotBlank()) {
                    Text(routine.description, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Duración", formatDuration(routine.totalSeconds()), Modifier.weight(1f))
                    Stat("Ejercicios", routine.items.size.toString(), Modifier.weight(1f))
                    Stat("Series", routine.items.sumOf { it.sets }.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Material: " + routine.requiredEquipment(repo.exercisesById)
                        .sortedBy { it.ordinal }.joinToString { it.label },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Preparación ${routine.prepSeconds}s · Descanso entre ejercicios ${routine.restBetweenExercises}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(routine.items) { index, item ->
                val exercise = repo.exercisesById[item.exerciseId] ?: return@itemsIndexed
                Card(Modifier.fillMaxWidth().clickable { onOpenExercise(exercise.id) }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            StickFigure(exercise.animation, figureColors(), Modifier.fillMaxSize().padding(4.dp))
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${exercise.name}", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.sets} series × ${item.workSeconds}s · descanso ${item.restSeconds}s",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(exercise.equipment.label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (index < routine.items.lastIndex && routine.restBetweenExercises > 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.SelfImprovement, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.size(4.dp))
                        Text("Descanso ${routine.restBetweenExercises}s", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar rutina?") },
            text = { Text("Se borrará \"${routine.name}\" de forma permanente.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repo.deleteRoutine(routine.id)
                    onBack()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
