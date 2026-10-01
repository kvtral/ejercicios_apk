package com.kvtral.ejercicios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kvtral.ejercicios.ui.screens.ExerciseDetailScreen
import com.kvtral.ejercicios.ui.screens.HomeScreen
import com.kvtral.ejercicios.ui.screens.LibraryScreen
import com.kvtral.ejercicios.ui.screens.RoutineDetailScreen
import com.kvtral.ejercicios.ui.screens.RoutineEditorScreen
import com.kvtral.ejercicios.ui.screens.WorkoutScreen
import com.kvtral.ejercicios.ui.theme.EjerciciosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EjerciciosTheme { AppNavigation() }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val EXERCISE = "exercise/{id}"
    const val ROUTINE = "routine/{id}"
    const val EDITOR = "editor?source={source}"
    const val WORKOUT = "workout/{id}"
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    val bottomBar: @Composable () -> Unit = { BottomBar(nav) }
    val idArg = listOf(navArgument("id") { type = NavType.StringType })

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                bottomBar = bottomBar,
                onOpenRoutine = { nav.navigate("routine/$it") },
                onCreateRoutine = { nav.navigate("editor") },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(bottomBar = bottomBar, onOpenExercise = { nav.navigate("exercise/$it") })
        }
        composable(Routes.EXERCISE, idArg) { entry ->
            ExerciseDetailScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.ROUTINE, idArg) { entry ->
            RoutineDetailScreen(
                routineId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onStart = { nav.navigate("workout/$it") },
                onEdit = { nav.navigate("editor?source=$it") },
                onDuplicate = { nav.navigate("editor?source=$it") },
                onOpenExercise = { nav.navigate("exercise/$it") },
            )
        }
        composable(
            Routes.EDITOR,
            listOf(navArgument("source") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            RoutineEditorScreen(
                sourceId = entry.arguments?.getString("source"),
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    // Sustituye el editor (y una ficha anterior de la misma rutina) por la ficha guardada.
                    nav.popBackStack()
                    if (nav.currentBackStackEntry?.arguments?.getString("id") == id) return@RoutineEditorScreen
                    nav.navigate("routine/$id")
                },
            )
        }
        composable(Routes.WORKOUT, idArg) {
            WorkoutScreen(onExit = { nav.popBackStack() })
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    NavigationBar {
        listOf(
            Triple(Routes.HOME, "Rutinas", Icons.AutoMirrored.Filled.List),
            Triple(Routes.LIBRARY, "Ejercicios", Icons.Filled.FitnessCenter),
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = current == route,
                onClick = {
                    nav.navigate(route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, null) },
                label = { Text(label) },
            )
        }
    }
}
