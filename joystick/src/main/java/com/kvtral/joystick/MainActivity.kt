package com.kvtral.joystick

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvtral.joystick.ui.ConsolesScreen
import com.kvtral.joystick.ui.GamepadScreen
import com.kvtral.joystick.ui.JoystickViewModel
import com.kvtral.joystick.ui.LoginScreen
import com.kvtral.joystick.ui.PhysicalGamepad
import com.kvtral.joystick.ui.Screen

private val Colors = darkColorScheme(
    primary = Color(0xFF6CC24A),
    onPrimary = Color(0xFF0B2A00),
    primaryContainer = Color(0xFF107C10),
    background = Color(0xFF101010),
    surface = Color(0xFF101010),
)

class MainActivity : ComponentActivity() {

    private val vm: JoystickViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = Colors) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val screen by vm.screen.collectAsStateWithLifecycle()
                    val error by vm.error.collectAsStateWithLifecycle()

                    when (val s = screen) {
                        is Screen.Login -> LoginScreen(s, vm::startLogin)
                        is Screen.Consoles -> ConsolesScreen(
                            s, vm.gamertag, vm::refreshConsoles, vm::connect, vm::signOut,
                        )
                        is Screen.Gamepad -> {
                            BackHandler(onBack = vm::disconnect)
                            GamepadScreen(s.console, vm)
                        }
                    }

                    error?.let {
                        AlertDialog(
                            onDismissRequest = vm::dismissError,
                            confirmButton = { TextButton(onClick = vm::dismissError) { Text("Vale") } },
                            text = { Text(it) },
                        )
                    }
                }
            }
        }
    }

    // Un mando físico conectado al móvil se reenvía a la Xbox mientras se está en la pantalla del mando.

    private val inGamepad get() = vm.screen.value is Screen.Gamepad

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (inGamepad && PhysicalGamepad.isGamepad(event.source)) {
            PhysicalGamepad.key(event)?.let {
                if (event.repeatCount == 0) vm.updatePhysical(it)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (inGamepad && PhysicalGamepad.isGamepad(event.source) && event.action == MotionEvent.ACTION_MOVE) {
            vm.updatePhysical(PhysicalGamepad.motion(event))
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
}
