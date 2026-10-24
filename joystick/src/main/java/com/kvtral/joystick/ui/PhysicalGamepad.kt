package com.kvtral.joystick.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.kvtral.joystick.stream.Button
import com.kvtral.joystick.stream.GamepadState

/**
 * Traduce un mando Bluetooth/USB conectado al móvil, para reenviarlo a la Xbox
 * (útil con mandos que la consola no admite directamente).
 */
object PhysicalGamepad {

    fun isGamepad(source: Int) =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private val keyMap = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to Button.A,
        KeyEvent.KEYCODE_BUTTON_B to Button.B,
        KeyEvent.KEYCODE_BUTTON_X to Button.X,
        KeyEvent.KEYCODE_BUTTON_Y to Button.Y,
        KeyEvent.KEYCODE_BUTTON_L1 to Button.LeftShoulder,
        KeyEvent.KEYCODE_BUTTON_R1 to Button.RightShoulder,
        KeyEvent.KEYCODE_BUTTON_THUMBL to Button.LeftThumb,
        KeyEvent.KEYCODE_BUTTON_THUMBR to Button.RightThumb,
        KeyEvent.KEYCODE_BUTTON_START to Button.Menu,
        KeyEvent.KEYCODE_BUTTON_SELECT to Button.View,
        KeyEvent.KEYCODE_BUTTON_MODE to Button.Nexus,
        KeyEvent.KEYCODE_DPAD_UP to Button.DPadUp,
        KeyEvent.KEYCODE_DPAD_DOWN to Button.DPadDown,
        KeyEvent.KEYCODE_DPAD_LEFT to Button.DPadLeft,
        KeyEvent.KEYCODE_DPAD_RIGHT to Button.DPadRight,
    )

    /** Devuelve la transformación del estado, o null si la tecla no es de mando. */
    fun key(event: KeyEvent): ((GamepadState) -> GamepadState)? {
        val down = event.action == KeyEvent.ACTION_DOWN
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> { s -> s.copy(leftTrigger = if (down) 1f else 0f) }
            KeyEvent.KEYCODE_BUTTON_R2 -> { s -> s.copy(rightTrigger = if (down) 1f else 0f) }
            else -> keyMap[event.keyCode]?.let { button -> { s: GamepadState -> s.with(button, down) } }
        }
    }

    fun motion(event: MotionEvent): (GamepadState) -> GamepadState = { s ->
        fun axis(a: Int): Float {
            val v = event.getAxisValue(a)
            val flat = event.device?.getMotionRange(a, event.source)?.flat ?: 0.1f
            return if (kotlin.math.abs(v) <= flat) 0f else v
        }
        val hatX = axis(MotionEvent.AXIS_HAT_X)
        val hatY = axis(MotionEvent.AXIS_HAT_Y)
        s.copy(
            leftX = axis(MotionEvent.AXIS_X),
            leftY = -axis(MotionEvent.AXIS_Y),
            rightX = axis(MotionEvent.AXIS_Z),
            rightY = -axis(MotionEvent.AXIS_RZ),
            leftTrigger = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE)),
            rightTrigger = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS)),
        )
            .with(Button.DPadLeft, hatX < -0.5f)
            .with(Button.DPadRight, hatX > 0.5f)
            .with(Button.DPadUp, hatY < -0.5f)
            .with(Button.DPadDown, hatY > 0.5f)
    }
}
