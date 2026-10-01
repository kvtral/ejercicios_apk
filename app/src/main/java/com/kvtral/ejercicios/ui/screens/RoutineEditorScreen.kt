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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvtral.ejercicios.data.Equipment
import com.kvtral.ejercicios.data.Exercise
import com.kvtral.ejercicios.data.Routine
import com.kvtral.ejercicios.data.RoutineItem
import com.kvtral.ejercicios.data.formatDuration
import com.kvtral.ejercicios.data.totalSeconds
import com.kvtral.ejercicios.ui.anim.StickFigure
import com.kvtral.ejercicios.ui.components.EquipmentChips
import com.kvtral.ejercicios.ui.components.NumberStepper
import com.kvtral.ejercicios.ui.components.figureColors

/**
 * Crea o edita una rutina. Con [sourceId] de una rutina predefinida se crea
 * una copia editable; con el de una personalizada se edita en sitio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(sourceId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val repo = rememberRepository()
    val source = remember(sourceId) { sourceId?.let { repo.routine(it) } }
    val editing = source?.isCustom == true

    var name by rememberSaveable {
        mutableStateOf(source?.let { if (editing) it.name else "${it.name} (copia)" } ?: "Mi rutina")
    }
    var restBetween by rememberSaveable { mutableIntStateOf(source?.restBetweenExercises ?: 60) }
    var prep by rememberSaveable { mutableIntStateOf(source?.prepSeconds ?: 10) }
    val items = remember { mutableStateListOf<RoutineItem>().apply { source?.let { addAll(it.items) } } }
    var showPicker by remember { mutableStateOf(false) }

    val draft = Routine(
        id = if (editing) source!!.id else "",
        name = name.trim().ifEmpty { "Mi rutina" },
        description = source?.description.orEmpty(),
        restBetweenExercises = restBetween,
        prepSeconds = prep,
        items = items.toList(),
        isCustom = editing,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Editar rutina" else "Nueva rutina") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    TextButton(
                        onClick = { onSaved(repo.saveRoutine(draft).id) },
                        enabled = items.isNotEmpty(),
                    ) { Text("Guardar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumberStepper("Preparación inicial", prep, { prep = it }, 0..60, 5, { "${it}s" })
                        NumberStepper("Descanso entre ejercicios", restBetween, { restBetween = it }, 0..300, 5,
                            { "${it}s" })
                        Text(
                            "Duración total: ${formatDuration(draft.totalSeconds())}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            itemsIndexed(items, key = { index, item -> "$index-${item.exerciseId}" }) { index, item ->
                val exercise = repo.exercisesById[item.exerciseId] ?: return@itemsIndexed
                EditorItem(
                    index = index,
                    exercise = exercise,
                    item = item,
                    isFirst = index == 0,
                    isLast = index == items.lastIndex,
                    onChange = { items[index] = it },
                    onMove = { delta ->
                        val target = index + delta
                        if (target in items.indices) items.add(target, items.removeAt(index))
                    },
                    onRemove = { items.removeAt(index) },
                )
            }
            item {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Añadir ejercicio")
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            initialFilter = repo.ownedEquipment.collectAsStateWithLifecycle().value,
            exercises = repo.exercises,
            onDismiss = { showPicker = false },
            onPick = { ex ->
                items += RoutineItem(ex.id)
                showPicker = false
            },
        )
    }
}

@Composable
private fun EditorItem(
    index: Int,
    exercise: Exercise,
    item: RoutineItem,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: (RoutineItem) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    StickFigure(exercise.animation, figureColors(), Modifier.fillMaxSize().padding(3.dp))
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}. ${exercise.name}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(exercise.equipment.label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Subir")
                }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Bajar")
                }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Quitar") }
            }
            HorizontalDivider()
            NumberStepper("Series", item.sets, { onChange(item.copy(sets = it)) }, 1..10)
            NumberStepper("Trabajo", item.workSeconds, { onChange(item.copy(workSeconds = it)) }, 5..300, 5,
                { "${it}s" })
            NumberStepper("Descanso entre series", item.restSeconds, { onChange(item.copy(restSeconds = it)) },
                0..300, 5, { "${it}s" })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    initialFilter: Set<Equipment>,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
) {
    var filter by remember { mutableStateOf(initialFilter.ifEmpty { Equipment.entries.toSet() }) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(true)) {
        Text("Elige un ejercicio", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp))
        EquipmentChips(filter, { eq, on -> filter = if (on) filter + eq else filter - eq },
            Modifier.padding(vertical = 8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(exercises.filter { it.equipment in filter }, key = { it.id }) { ex ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(ex) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StickFigure(ex.animation, figureColors(), Modifier.size(56.dp))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(ex.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${ex.equipment.label} · ${ex.muscles.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
