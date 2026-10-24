package com.kvtral.joystick

import com.kvtral.joystick.stream.Button
import com.kvtral.joystick.stream.GamepadState
import com.kvtral.joystick.stream.InputPacket
import com.kvtral.joystick.stream.MessageProtocol
import com.kvtral.joystick.stream.Teredo
import com.kvtral.joystick.xhome.IceCandidate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InputPacketTest {

    @Test
    fun clientMetadataHas15Bytes() {
        val p = InputPacket.clientMetadata(sequence = 0, timeMs = 1.5, maxTouchPoints = 0)
            .order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(15, p.remaining())
        assertEquals(InputPacket.REPORT_CLIENT_METADATA, p.getShort(0).toInt())
        assertEquals(0, p.getInt(2))
        assertEquals(1.5, p.getDouble(6), 0.0)
    }

    @Test
    fun gamepadPacketLayout() {
        val state = GamepadState(leftX = 1f, leftY = -1f, rightTrigger = 1f)
            .with(Button.A, true)
            .with(Button.DPadUp, true)
        val p = InputPacket.gamepad(sequence = 7, timeMs = 0.0, states = listOf(state))
            .order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(14 + 1 + 23, p.remaining())
        assertEquals(InputPacket.REPORT_GAMEPAD, p.getShort(0).toInt())
        assertEquals(7, p.getInt(2))
        assertEquals(1, p.get(14).toInt()) // número de mandos
        assertEquals(0, p.get(15).toInt()) // índice del mando
        assertEquals(16 or 256, p.getShort(16).toInt() and 0xFFFF)
        assertEquals(32767, p.getShort(18).toInt())
        assertEquals(-32767, p.getShort(20).toInt())
        assertEquals(0, p.getShort(22).toInt())
        assertEquals(0, p.getShort(24).toInt())
        assertEquals(0, p.getShort(26).toInt() and 0xFFFF) // gatillo izquierdo
        assertEquals(65535, p.getShort(28).toInt() and 0xFFFF) // gatillo derecho
        val physicality = p.getInt(30)
        assertTrue(physicality and 0x1000 != 0) // A
        assertTrue(physicality and 0x1 != 0) // cruceta arriba
        assertTrue(physicality and 0x20000 != 0) // gatillo derecho
        assertTrue(physicality and 0xC0000 == 0xC0000) // stick izquierdo
        assertEquals(0, p.getInt(34))
    }

    @Test
    fun axesAreClamped() {
        assertEquals(32767.toShort(), InputPacket.axis(3f))
        assertEquals((-32767).toShort(), InputPacket.axis(-3f))
        assertEquals(0.toShort(), InputPacket.trigger(-1f))
    }

    @Test
    fun parsesVibration() {
        val b = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(InputPacket.REPORT_VIBRATION.toShort())
        b.put(0) // tipo
        b.put(0) // mando
        b.put(50); b.put(100); b.put(0); b.put(0)
        b.putShort(250) // duración
        b.putShort(0) // retardo
        b.put(0) // repeticiones
        b.flip()
        val v = InputPacket.parseVibration(b)!!
        assertEquals(0.5f, v.leftMotor, 0.001f)
        assertEquals(1f, v.rightMotor, 0.001f)
        assertEquals(250, v.durationMs)
    }

    @Test
    fun ignoresPacketsWithoutVibration() {
        val b = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(InputPacket.REPORT_SERVER_METADATA.toShort())
        b.putInt(720); b.putInt(1280)
        b.flip()
        assertNull(InputPacket.parseVibration(b))
    }
}

class TeredoTest {

    @Test
    fun decodesTeredoAddress() {
        // Ejemplo de la RFC 4380: servidor 65.54.227.120, cliente 192.0.2.45:40000.
        val a = Teredo.parse("2001:0:4136:e378:8000:63bf:3fff:fdd2")!!
        assertEquals("65.54.227.120", a.server)
        assertEquals("192.0.2.45", a.client)
        assertEquals(40000, a.clientPort)
    }

    @Test
    fun ignoresOtherAddresses() {
        assertNull(Teredo.parse("192.168.1.20"))
        assertNull(Teredo.parse("fe80::1"))
        assertNull(Teredo.parse("typ"))
    }

    @Test
    fun addsIpv4CandidatesAfterTeredoOne() {
        val original = IceCandidate(
            "a=candidate:1 1 UDP 1 2001:0:4136:e378:8000:63bf:3fff:fdd2 9002 typ host ", "0", 0,
        )
        val local = IceCandidate("a=candidate:2 1 UDP 1 192.168.1.20 9002 typ host ", "0", 0)
        val out = Teredo.expand(listOf(original, local))
        assertEquals(original, out.first())
        assertEquals(local, out.last())
        assertEquals(2 + 5, out.size)
        assertTrue(out[1].candidate.contains("192.0.2.45 40000 typ host"))
    }
}

class MessageProtocolTest {

    private val json = Json

    @Test
    fun handshakeAndAck() {
        val p = MessageProtocol()
        val hs = json.parseToJsonElement(p.handshake()).jsonObject
        assertEquals("Handshake", hs["type"]!!.jsonPrimitive.content)
        assertEquals("messageV1", hs["version"]!!.jsonPrimitive.content)

        val ack = """{"type":"HandshakeAck","version":"messageV1","id":"x","cv":"a.1"}""" + "\u0000"
        assertEquals(MessageProtocol.Incoming.HandshakeAck, p.parse(ack))
    }

    @Test
    fun repliesUnhandledToUnknownTransactions() {
        val p = MessageProtocol()
        val msg = """{"type":"TransactionStart","id":"42","target":"/foo","content":"{}","cv":"abc.3"}"""
        val reply = p.parse(msg) as MessageProtocol.Incoming.NeedsReply
        val r = json.parseToJsonElement(reply.reply).jsonObject
        assertEquals("Unhandled", r["type"]!!.jsonPrimitive.content)
        assertEquals("42", r["id"]!!.jsonPrimitive.content)
        assertEquals("abc.4", r["cv"]!!.jsonPrimitive.content)
    }

    @Test
    fun sessionSetupSendsDimensionsLast() {
        val msgs = MessageProtocol().sessionSetup().map { json.parseToJsonElement(it).jsonObject }
        assertEquals("/streaming/characteristics/dimensionschanged", msgs.last()["target"]!!.jsonPrimitive.content)
        val content = json.parseToJsonElement(msgs.last()["content"]!!.jsonPrimitive.content).jsonObject
        assertEquals(1280, content["horizontal"]!!.jsonPrimitive.content.toInt())
    }
}
