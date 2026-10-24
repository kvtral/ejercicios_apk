package com.kvtral.joystick.stream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.UUID

/**
 * Canales de texto "message" y "control". El canal de mensajes empieza con un Handshake;
 * cuando llega el HandshakeAck se configura la sesión y se conecta el mando virtual.
 */
class MessageProtocol {
    private val cvPrefix = UUID.randomUUID().toString().replace("-", "")
    private var cvCounter = 0
    private val installId = UUID.randomUUID().toString()

    private fun nextCv() = "$cvPrefix.${++cvCounter}"

    private fun incrementCv(cv: String?): String {
        val parts = cv?.split('.')?.toMutableList() ?: return nextCv()
        val last = parts.last().toIntOrNull() ?: return nextCv()
        parts[parts.size - 1] = (last + 1).toString()
        return parts.joinToString(".")
    }

    fun handshake(): String = buildJsonObject {
        put("type", "Handshake")
        put("version", VERSION)
        put("id", UUID.randomUUID().toString())
        put("cv", nextCv())
    }.toString()

    sealed interface Incoming {
        data object HandshakeAck : Incoming
        /** Mensaje o transacción que no sabemos atender: hay que responder "Unhandled". */
        data class NeedsReply(val reply: String) : Incoming
        data object Ignored : Incoming
    }

    fun parse(raw: String): Incoming {
        val clean = raw.replace("\u0000", "").trim()
        if (clean.isEmpty()) return Incoming.Ignored
        val msg = runCatching { Json.parseToJsonElement(clean).jsonObject }.getOrNull() ?: return Incoming.Ignored
        return when (msg.string("type")) {
            "HandshakeAck" -> if (msg.string("version") == VERSION) Incoming.HandshakeAck else Incoming.Ignored
            "Message", "TransactionStart" -> Incoming.NeedsReply(
                buildJsonObject {
                    put("type", "Unhandled")
                    put("id", msg.string("id"))
                    put("target", msg.string("target"))
                    put("cv", incrementCv(msg.string("cv")))
                }.toString(),
            )
            else -> Incoming.Ignored
        }
    }

    /** Mensajes de configuración que manda el cliente web justo después del HandshakeAck. */
    fun sessionSetup(width: Int = 1280, height: Int = 720): List<String> = listOf(
        message("/streaming/systemUi/configuration") {
            putJsonArray("version") { listOf(0, 2, 0).forEach { add(JsonPrimitive(it)) } }
            // Lista vacía: los menús del sistema (teclado, diálogos) se quedan en la tele.
            putJsonArray("systemUis") {}
        },
        message("/streaming/properties/clientappinstallidchanged") { put("clientAppInstallId", installId) },
        message("/streaming/characteristics/orientationchanged") { put("orientation", 0) },
        message("/streaming/characteristics/touchinputenabledchanged") { put("touchInputEnabled", false) },
        message("/streaming/characteristics/clientdevicecapabilities") {},
        message("/streaming/characteristics/dimensionschanged") {
            put("horizontal", width)
            put("vertical", height)
            put("preferredWidth", width)
            put("preferredHeight", height)
            put("safeAreaLeft", 0)
            put("safeAreaTop", 0)
            put("safeAreaRight", width)
            put("safeAreaBottom", height)
            put("supportsCustomResolution", true)
        },
    )

    private fun message(target: String, content: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("type", "Message")
        put("content", buildJsonObject(content).toString())
        put("id", UUID.randomUUID().toString())
        put("target", target)
        put("cv", nextCv())
    }.toString()

    companion object {
        const val VERSION = "messageV1"

        /** Clave fija que envía el cliente web de Xbox al abrir el canal de control. */
        private const val CONTROL_ACCESS_KEY = "4BDB3609-C1F1-4195-9B37-FEFF45DA8B8E"

        fun authorizationRequest() = buildJsonObject {
            put("message", "authorizationRequest")
            put("accessKey", CONTROL_ACCESS_KEY)
        }.toString()

        fun gamepadChanged(index: Int, added: Boolean) = buildJsonObject {
            put("message", "gamepadChanged")
            put("gamepadIndex", index)
            put("wasAdded", added)
        }.toString()

        fun keyframeRequest() = buildJsonObject {
            put("message", "videoKeyframeRequested")
            put("ifrRequested", false)
        }.toString()
    }
}

private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
