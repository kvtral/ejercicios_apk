package com.kvtral.joystick.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kvtral.joystick.auth.AuthException
import com.kvtral.joystick.auth.DeviceCode
import com.kvtral.joystick.auth.StreamingToken
import com.kvtral.joystick.auth.XboxAuth
import com.kvtral.joystick.net.HttpException
import com.kvtral.joystick.stream.Button
import com.kvtral.joystick.stream.GamepadState
import com.kvtral.joystick.stream.InputPacket
import com.kvtral.joystick.stream.StreamClient
import com.kvtral.joystick.stream.StreamStatus
import com.kvtral.joystick.xhome.XHomeApi
import com.kvtral.joystick.xhome.XboxConsole
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface Screen {
    data class Login(val code: DeviceCode? = null, val busy: Boolean = false) : Screen
    data class Consoles(val consoles: List<XboxConsole> = emptyList(), val loading: Boolean = true) : Screen
    data class Gamepad(val console: XboxConsole) : Screen
}

class JoystickViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = XboxAuth(app)
    private val prefs = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    val stream = StreamClient(app, viewModelScope, ::vibrate)

    private val _screen = MutableStateFlow<Screen>(if (auth.isSignedIn) Screen.Consoles() else Screen.Login())
    val screen: StateFlow<Screen> = _screen

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val status: StateFlow<StreamStatus> = stream.status
    val gamertag get() = auth.gamertag

    private val _showVideo = MutableStateFlow(prefs.getBoolean("video", false))
    val showVideo: StateFlow<Boolean> = _showVideo

    private val _haptics = MutableStateFlow(prefs.getBoolean("haptics", true))
    val haptics: StateFlow<Boolean> = _haptics

    private var token: StreamingToken? = null
    private var loginJob: Job? = null

    init {
        stream.mediaEnabled = _showVideo.value
        if (auth.isSignedIn) refreshConsoles()
    }

    fun dismissError() { _error.value = null }

    fun startLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _screen.value = Screen.Login(busy = true)
            try {
                val code = auth.requestDeviceCode()
                _screen.value = Screen.Login(code = code)
                auth.pollDeviceCode(code)
                _screen.value = Screen.Consoles()
                refreshConsoles()
            } catch (e: Exception) {
                _screen.value = Screen.Login()
                report(e)
            }
        }
    }

    fun signOut() {
        stream.close()
        auth.signOut()
        token = null
        _screen.value = Screen.Login()
    }

    private suspend fun streamingToken(): StreamingToken {
        token?.let { if (System.currentTimeMillis() < it.expiresAtMs - 60_000) return it }
        return auth.streamingToken().also { token = it }
    }

    fun refreshConsoles() {
        viewModelScope.launch {
            _screen.value = Screen.Consoles(loading = true)
            try {
                val t = streamingToken()
                val consoles = XHomeApi(t.baseUri, t.gsToken).consoles()
                _screen.value = Screen.Consoles(consoles, loading = false)
            } catch (e: Exception) {
                _screen.value = Screen.Consoles(loading = false)
                report(e)
                if (!auth.isSignedIn) _screen.value = Screen.Login()
            }
        }
    }

    fun connect(console: XboxConsole) {
        _screen.value = Screen.Gamepad(console)
        viewModelScope.launch {
            try {
                val t = streamingToken()
                stream.connect(XHomeApi(t.baseUri, t.gsToken), console.serverId) { auth.consoleTransferToken() }
            } catch (e: Exception) {
                report(e)
                disconnect()
            }
        }
    }

    fun disconnect() {
        stream.close()
        touch = GamepadState()
        physical = GamepadState()
        _screen.value = Screen.Consoles(loading = true)
        refreshConsoles()
    }

    fun setShowVideo(show: Boolean) {
        _showVideo.value = show
        prefs.edit().putBoolean("video", show).apply()
        stream.mediaEnabled = show
    }

    fun setHaptics(on: Boolean) {
        _haptics.value = on
        prefs.edit().putBoolean("haptics", on).apply()
    }

    private fun report(e: Exception) {
        _error.value = when (e) {
            is AuthException -> e.message
            is HttpException -> when (e.code) {
                401, 403 -> "Xbox ha rechazado la petición (${e.code}). Prueba a cerrar sesión y volver a entrar."
                else -> "Error del servidor de Xbox (${e.code})."
            }
            is IOException -> "Sin conexión a Internet: ${e.message}"
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    // --- Mando: táctil + mando físico conectado al móvil ---

    private var touch = GamepadState()
    private var physical = GamepadState()

    private val _pressed = MutableStateFlow(GamepadState())
    /** Estado combinado, para iluminar los botones en pantalla. */
    val pressed: StateFlow<GamepadState> = _pressed

    fun setButton(button: Button, down: Boolean) = updateTouch { it.with(button, down) }

    fun setLeftStick(x: Float, y: Float) = updateTouch { it.copy(leftX = x, leftY = y) }

    fun setRightStick(x: Float, y: Float) = updateTouch { it.copy(rightX = x, rightY = y) }

    fun setTrigger(left: Boolean, value: Float) =
        updateTouch { if (left) it.copy(leftTrigger = value) else it.copy(rightTrigger = value) }

    private inline fun updateTouch(change: (GamepadState) -> GamepadState) {
        touch = change(touch)
        publish()
    }

    fun updatePhysical(change: (GamepadState) -> GamepadState) {
        physical = change(physical)
        publish()
    }

    private fun publish() {
        fun pick(a: Float, b: Float) = if (abs(a) >= abs(b)) a else b
        val merged = GamepadState(
            buttons = touch.buttons or physical.buttons,
            leftX = pick(touch.leftX, physical.leftX),
            leftY = pick(touch.leftY, physical.leftY),
            rightX = pick(touch.rightX, physical.rightX),
            rightY = pick(touch.rightY, physical.rightY),
            leftTrigger = maxOf(touch.leftTrigger, physical.leftTrigger),
            rightTrigger = maxOf(touch.rightTrigger, physical.rightTrigger),
        )
        stream.updateGamepad(merged)
        _pressed.update { merged }
    }

    private val vibrator: Vibrator? by lazy {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun vibrate(v: InputPacket.Vibration) {
        if (!_haptics.value) return
        val vib = vibrator ?: return
        val strength = maxOf(v.leftMotor, v.rightMotor, v.leftTrigger, v.rightTrigger)
        if (strength <= 0f || v.durationMs == 0) {
            vib.cancel()
            return
        }
        val amplitude = (strength * 255).roundToInt().coerceIn(1, 255)
        vib.vibrate(VibrationEffect.createOneShot(v.durationMs.toLong().coerceAtMost(2000), amplitude))
    }

    override fun onCleared() {
        stream.release()
    }
}
