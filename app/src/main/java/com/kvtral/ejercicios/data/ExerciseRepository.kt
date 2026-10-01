package com.kvtral.ejercicios.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/**
 * Catálogo de ejercicios y rutinas. Las rutinas predefinidas vienen de
 * assets/exercises.json; las personalizadas se guardan en SharedPreferences.
 */
class ExerciseRepository(context: Context) {

    private val prefs = context.getSharedPreferences("ejercicios", Context.MODE_PRIVATE)
    private val catalog: Catalog =
        context.assets.open("exercises.json").bufferedReader().use { parseCatalog(it.readText()) }

    val exercises: List<Exercise> = catalog.exercises
    val exercisesById: Map<String, Exercise> = exercises.associateBy { it.id }
    val presetRoutines: List<Routine> = catalog.routines

    private val _customRoutines = MutableStateFlow(loadCustomRoutines())
    val customRoutines: StateFlow<List<Routine>> = _customRoutines.asStateFlow()

    private val _ownedEquipment = MutableStateFlow(loadOwnedEquipment())
    /** Material que el usuario tiene en casa; filtra rutinas y ejercicios. */
    val ownedEquipment: StateFlow<Set<Equipment>> = _ownedEquipment.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    fun routine(id: String): Routine? =
        presetRoutines.firstOrNull { it.id == id } ?: _customRoutines.value.firstOrNull { it.id == id }

    fun saveRoutine(routine: Routine): Routine {
        val toSave = if (routine.isCustom && routine.id.isNotBlank()) routine
        else routine.copy(id = "custom_" + UUID.randomUUID(), isCustom = true)
        _customRoutines.update { list ->
            val index = list.indexOfFirst { it.id == toSave.id }
            if (index >= 0) list.toMutableList().also { it[index] = toSave } else list + toSave
        }
        persistCustomRoutines()
        return toSave
    }

    fun deleteRoutine(id: String) {
        _customRoutines.update { list -> list.filterNot { it.id == id } }
        persistCustomRoutines()
    }

    fun setEquipmentOwned(equipment: Equipment, owned: Boolean) {
        _ownedEquipment.update { if (owned) it + equipment else it - equipment }
        prefs.edit().putStringSet(KEY_EQUIPMENT, _ownedEquipment.value.map { it.name }.toSet()).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    private fun loadOwnedEquipment(): Set<Equipment> {
        val stored = prefs.getStringSet(KEY_EQUIPMENT, null) ?: return Equipment.entries.toSet()
        return stored.mapNotNull { name -> Equipment.entries.firstOrNull { it.name == name } }.toSet()
    }

    private fun loadCustomRoutines(): List<Routine> {
        val text = prefs.getString(KEY_ROUTINES, null) ?: return emptyList()
        return runCatching { AppJson.decodeFromString(ListSerializer(Routine.serializer()), text) }
            .getOrDefault(emptyList())
            // Descarta ejercicios que ya no existan en el catálogo.
            .map { r -> r.copy(items = r.items.filter { it.exerciseId in exercisesById }) }
    }

    private fun persistCustomRoutines() {
        val text = AppJson.encodeToString(ListSerializer(Routine.serializer()), _customRoutines.value)
        prefs.edit().putString(KEY_ROUTINES, text).apply()
    }

    private companion object {
        const val KEY_ROUTINES = "custom_routines"
        const val KEY_EQUIPMENT = "owned_equipment"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRATION = "vibration"
    }
}
