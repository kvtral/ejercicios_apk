package com.kvtral.ejercicios

import android.app.Application
import com.kvtral.ejercicios.data.ExerciseRepository

class EjerciciosApp : Application() {
    val repository: ExerciseRepository by lazy { ExerciseRepository(this) }
}
