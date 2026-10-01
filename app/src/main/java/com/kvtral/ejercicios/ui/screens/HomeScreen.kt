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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvtral.ejercicios.data.Exercise
import com.kvtral.ejercicios.data.Routine
import com.kvtral.ejercicios.data.formatDuration
import com.kvtral.ejercicios.data.requiredEquipment
import com.kvtral.ejercicios.data.totalSeconds
import com.kvtral.ejercicios.ui.anim.StickFigure
import com.kvtral.ejercicios.ui.components.EquipmentChips
import com.kvtral.ejercicios.ui.components.figureColors
import com.kvtral.ejercicios.ui.components.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bottomBar: @Composable () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onCreateRoutine: () -> Unit,
) {
    val repo = rememberRepository()
    val owned by repo.ownedEquipment.collectAsStateWithLifecycle()
    val custom by repo.customRoutines.collectAsStateWithLifecycle()
    val available = { r: Routine -> r.requiredEquipment(repo.exercisesById).all { it in owned } }
    val visibleCustom = custom.filter(available)
    val visiblePresets = repo.presetRoutines.filter(available)
    val hidden = custom.size + repo.presetRoutines.size - visibleCustom.size - visiblePresets.size

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ejercicios en Casa", fontWeight = FontWeight.Bold) }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRoutine,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Crear rutina") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "¿Qué material tienes en casa?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                EquipmentChips(owned, repo::setEquipmentOwned)
            }
            if (visibleCustom.isNotEmpty()) {
                item { SectionTitle("Mis rutinas") }
                items(visibleCustom, key = { it.id }) { r ->
                    RoutineCard(r, repo.exercisesById, onClick = { onOpenRoutine(r.id) })
                }
            }
            item { SectionTitle("Rutinas recomendadas") }
            items(visiblePresets, key = { it.id }) { r ->
                RoutineCard(r, repo.exercisesById, onClick = { onOpenRoutine(r.id) })
            }
            if (hidden > 0) {
                item {
                    Text(
                        "$hidden rutina(s) ocultas porque necesitan material que no has marcado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
fun RoutineCard(routine: Routine, exercises: Map<String, Exercise>, onClick: () -> Unit) {
    val first = routine.items.firstNotNullOfOrNull { exercises[it.exerciseId] }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (first != null) {
                    StickFigure(first.animation, figureColors(), Modifier.fillMaxSize().padding(4.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (routine.description.isNotBlank()) {
                    Text(
                        routine.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Timer, null, Modifier.size(16.dp))
                    Text(
                        "${formatDuration(routine.totalSeconds())} · ${routine.items.size} ejercicios",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    routine.requiredEquipment(exercises).sortedBy { it.ordinal }.forEach {
                        Icon(it.icon(), it.label, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
