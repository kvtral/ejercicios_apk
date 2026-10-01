package com.kvtral.ejercicios.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kvtral.ejercicios.EjerciciosApp
import com.kvtral.ejercicios.data.ExerciseRepository

@Composable
fun rememberRepository(): ExerciseRepository =
    (LocalContext.current.applicationContext as EjerciciosApp).repository
