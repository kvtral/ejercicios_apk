package com.kvtral.joystick.stream

/** Botones del mando con el bit que usa el protocolo de entrada de Xbox. */
enum class Button(val mask: Int) {
    Nexus(2), Menu(4), View(8),
    A(16), B(32), X(64), Y(128),
    DPadUp(256), DPadDown(512), DPadLeft(1024), DPadRight(2048),
    LeftShoulder(4096), RightShoulder(8192),
    LeftThumb(16384), RightThumb(32768),
}

/**
 * Estado completo del mando. Ejes de -1 a 1 con Y positivo hacia ARRIBA
 * (como en el mando real), gatillos de 0 a 1.
 */
data class GamepadState(
    val buttons: Int = 0,
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
) {
    fun isPressed(button: Button) = buttons and button.mask != 0

    fun with(button: Button, pressed: Boolean) =
        copy(buttons = if (pressed) buttons or button.mask else buttons and button.mask.inv())
}
