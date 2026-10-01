package com.kvtral.ejercicios.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material3.FilterChipDefaults
import com.kvtral.ejercicios.data.Equipment
import com.kvtral.ejercicios.ui.anim.FigureColors

fun Equipment.icon() = when (this) {
    Equipment.BODYWEIGHT -> Icons.AutoMirrored.Filled.DirectionsRun
    Equipment.DUMBBELL -> Icons.Filled.FitnessCenter
    Equipment.KETTLEBELL -> Icons.Filled.SportsHandball
    Equipment.BAND -> Icons.Filled.Cable
}

@Composable
fun figureColors(body: Color = MaterialTheme.colorScheme.onSurface): FigureColors {
    val scheme = MaterialTheme.colorScheme
    return FigureColors(
        body = body,
        farLimb = body.copy(alpha = 0.4f),
        prop = scheme.primary,
        band = Color(0xFFE91E63),
        ground = body.copy(alpha = 0.25f),
        mat = scheme.tertiary.copy(alpha = 0.45f),
    )
}

@Composable
fun EquipmentChips(
    selected: Set<Equipment>,
    onToggle: (Equipment, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(Equipment.entries) { eq ->
            val isSelected = eq in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(eq, !isSelected) },
                label = { Text(eq.label) },
                leadingIcon = { Icon(eq.icon(), null, Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.size(36.dp),
        ) { Icon(Icons.Filled.Remove, "Menos") }
        Text(
            format(value),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.size(36.dp),
        ) { Icon(Icons.Filled.Add, "Más") }
    }
}
