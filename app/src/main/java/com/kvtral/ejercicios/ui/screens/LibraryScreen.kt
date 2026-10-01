package com.kvtral.ejercicios.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kvtral.ejercicios.data.Equipment
import com.kvtral.ejercicios.data.Exercise
import com.kvtral.ejercicios.ui.anim.StickFigure
import com.kvtral.ejercicios.ui.components.EquipmentChips
import com.kvtral.ejercicios.ui.components.figureColors
import com.kvtral.ejercicios.ui.components.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(bottomBar: @Composable () -> Unit, onOpenExercise: (String) -> Unit) {
    val repo = rememberRepository()
    var filter by rememberSaveable { mutableStateOf(Equipment.entries.map { it.name }.toSet()) }
    val shown = repo.exercises.filter { it.equipment.name in filter }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ejercicios", fontWeight = FontWeight.Bold) }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EquipmentChips(
                    selected = filter.mapNotNull { n -> Equipment.entries.firstOrNull { it.name == n } }.toSet(),
                    onToggle = { eq, on -> filter = if (on) filter + eq.name else filter - eq.name },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(shown, key = { it.id }) { ex -> ExerciseTile(ex) { onOpenExercise(ex.id) } }
        }
    }
}

@Composable
fun ExerciseTile(exercise: Exercise, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            StickFigure(
                exercise.animation,
                figureColors(),
                Modifier.fillMaxWidth().aspectRatio(1.2f).padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(exercise.equipment.icon(), exercise.equipment.label, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(6.dp))
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
