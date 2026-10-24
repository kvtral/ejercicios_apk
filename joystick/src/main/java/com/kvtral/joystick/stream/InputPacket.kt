package com.kvtral.joystick.stream

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Paquetes binarios del canal de datos "input" (little endian).
 *
 * Cabecera de 14 bytes: tipo de informe (u16), número de secuencia (u32), marca de tiempo en ms (f64).
 * Cada mando ocupa 23 bytes: índice (u8), botones (u16), 4 ejes (i16), 2 gatillos (u16)
 * y dos máscaras de "fisicalidad" (u32) que indican qué controles se están tocando.
 */
object InputPacket {
    const val REPORT_GAMEPAD = 2
    const val REPORT_CLIENT_METADATA = 8
    const val REPORT_SERVER_METADATA = 16
    const val REPORT_VIBRATION = 128

    private const val HEADER = 14
    private const val GAMEPAD_SIZE = 23

    /** Primer paquete tras abrir el canal: anuncia cuántos puntos táctiles admite el cliente. */
    fun clientMetadata(sequence: Int, timeMs: Double, maxTouchPoints: Int = 0): ByteBuffer =
        header(REPORT_CLIENT_METADATA, sequence, timeMs, HEADER + 1).apply {
            put(maxTouchPoints.toByte())
            flip()
        }

    fun gamepad(sequence: Int, timeMs: Double, states: List<GamepadState>, firstIndex: Int = 0): ByteBuffer {
        val buf = header(REPORT_GAMEPAD, sequence, timeMs, HEADER + 1 + GAMEPAD_SIZE * states.size)
        buf.put(states.size.toByte())
        states.forEachIndexed { i, s ->
            buf.put((firstIndex + i).toByte())
            buf.putShort(s.buttons.toShort())
            buf.putShort(axis(s.leftX))
            buf.putShort(axis(s.leftY))
            buf.putShort(axis(s.rightX))
            buf.putShort(axis(s.rightY))
            buf.putShort(trigger(s.leftTrigger))
            buf.putShort(trigger(s.rightTrigger))
            buf.putInt(physicality(s))
            buf.putInt(0)
        }
        buf.flip()
        return buf
    }

    private fun header(type: Int, sequence: Int, timeMs: Double, size: Int): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(type.toShort())
            putInt(sequence)
            putDouble(timeMs)
        }

    internal fun axis(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()

    internal fun trigger(v: Float): Short = (v.coerceIn(0f, 1f) * 65535f).roundToInt().toShort()

    /** Bits de GamepadInputPhysicality: el orden no coincide con la máscara de botones. */
    internal fun physicality(s: GamepadState): Int {
        var p = 0
        fun bit(on: Boolean, mask: Int) { if (on) p = p or mask }
        bit(s.isPressed(Button.DPadUp), 0x1)
        bit(s.isPressed(Button.DPadDown), 0x2)
        bit(s.isPressed(Button.DPadLeft), 0x4)
        bit(s.isPressed(Button.DPadRight), 0x8)
        bit(s.isPressed(Button.Menu), 0x10)
        bit(s.isPressed(Button.View), 0x20)
        bit(s.isPressed(Button.LeftThumb), 0x40)
        bit(s.isPressed(Button.RightThumb), 0x80)
        bit(s.isPressed(Button.LeftShoulder), 0x100)
        bit(s.isPressed(Button.RightShoulder), 0x200)
        bit(s.isPressed(Button.Nexus), 0x400)
        bit(s.isPressed(Button.A), 0x1000)
        bit(s.isPressed(Button.B), 0x2000)
        bit(s.isPressed(Button.X), 0x4000)
        bit(s.isPressed(Button.Y), 0x8000)
        bit(s.leftTrigger > 0f, 0x10000)
        bit(s.rightTrigger > 0f, 0x20000)
        bit(hypot(s.leftX, s.leftY) > 0f, 0x40000 or 0x80000)
        bit(hypot(s.rightX, s.rightY) > 0f, 0x100000 or 0x200000)
        return p
    }

    data class Vibration(
        val gamepadIndex: Int,
        val leftMotor: Float,
        val rightMotor: Float,
        val leftTrigger: Float,
        val rightTrigger: Float,
        val durationMs: Int,
    )

    /** Lee la vibración que manda la consola (si el paquete la incluye). */
    fun parseVibration(data: ByteBuffer): Vibration? {
        val b = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        if (b.remaining() < 2) return null
        val type = b.short.toInt() and 0xFFFF
        if (type and REPORT_SERVER_METADATA != 0) {
            if (b.remaining() < 8) return null
            b.position(b.position() + 8)
        }
        if (type and REPORT_VIBRATION == 0 || b.remaining() < 10) return null
        b.get() // tipo de vibración: 0 = cuatro motores
        val index = b.get().toInt() and 0xFF
        fun pct() = (b.get().toInt() and 0xFF) / 100f
        val left = pct()
        val right = pct()
        val lt = pct()
        val rt = pct()
        val duration = b.short.toInt() and 0xFFFF
        return Vibration(index, left, right, lt, rt, duration)
    }
}
