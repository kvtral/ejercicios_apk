package com.kvtral.joystick.xhome

import com.kvtral.joystick.auth.str
import com.kvtral.joystick.net.Http
import com.kvtral.joystick.net.HttpResponse
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class XboxConsole(
    val serverId: String,
    val name: String,
    val consoleType: String,
    /** "On", "ConnectedStandby", "Off"... */
    val powerState: String,
)

/** Candidato ICE tal como lo intercambia el servicio de Xbox. */
data class IceCandidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int)

class StreamException(message: String) : Exception(message)

/**
 * Señalización de Remote Play ("xhome"): la consola y el móvil se encuentran a través de los
 * servidores de Xbox, pero el vídeo y los mandos viajan directos por la red local (WebRTC).
 */
class XHomeApi(private val baseUri: String, private val gsToken: String) {

    private var sessionUrl: String? = null

    var keepAliveSeconds = 20
        private set

    private val auth get() = mapOf("Authorization" to "Bearer $gsToken")

    private fun url(resource: String) =
        "${sessionUrl ?: throw StreamException("No hay sesión abierta")}/$resource"

    suspend fun consoles(): List<XboxConsole> {
        val res = Http.get("$baseUri/v6/servers/home?mr=50", auth + deviceInfo).json().jsonObject
        return res["results"]?.jsonArray.orEmpty().map {
            val c = it.jsonObject
            XboxConsole(
                serverId = c.str("serverId"),
                name = c["deviceName"]?.jsonPrimitive?.contentOrNull ?: "Xbox",
                consoleType = c["consoleType"]?.jsonPrimitive?.contentOrNull ?: "",
                powerState = c["powerState"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }
    }

    /** Abre la sesión y espera a que la consola esté lista para recibir la oferta WebRTC. */
    suspend fun startSession(
        serverId: String,
        transferToken: suspend () -> String,
        onState: (String) -> Unit,
    ) {
        val body = buildJsonObject {
            put("clientSessionId", "")
            put("titleId", "")
            put("systemUpdateGroup", "")
            putJsonObject("settings") {
                put("nanoVersion", "V3;WebrtcTransport.dll")
                put("enableTextToSpeech", false)
                put("highContrast", 0)
                put("locale", java.util.Locale.getDefault().toLanguageTag())
                put("useIceConnection", false)
                put("timezoneOffsetMinutes", java.util.TimeZone.getDefault().rawOffset / 60_000)
                put("sdkType", "web")
                put("osName", "android")
            }
            put("serverId", serverId)
            putJsonArray("fallbackRegionNames") {}
        }
        val res = Http.postJson("$baseUri/v5/sessions/home/play", body, auth + deviceInfo).json().jsonObject
        val path = res.str("sessionPath")
        sessionUrl = "$baseUri/${path.trimStart('/')}"

        var connectSent = false
        while (true) {
            val state = Http.get(url("state"), auth).json().jsonObject
            val name = state["state"]?.jsonPrimitive?.contentOrNull ?: ""
            onState(name)
            when (name) {
                "Provisioned" -> break
                "WaitingForResources", "Provisioning" -> delay(1000)
                "ReadyToConnect" -> {
                    if (!connectSent) {
                        val token = buildJsonObject { put("userToken", transferToken()) }
                        Http.postJson(url("connect"), token, auth)
                        connectSent = true
                    }
                    delay(1000)
                }
                "Failed" -> {
                    val msg = state["errorDetails"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                    throw StreamException("La consola rechazó la sesión: ${msg ?: "error desconocido"}")
                }
                else -> throw StreamException("Estado de sesión inesperado: $name")
            }
        }

        runCatching {
            Http.get(url("configuration"), auth).json().jsonObject["keepAlivePulseInSeconds"]
                ?.jsonPrimitive?.intOrNull?.let { keepAliveSeconds = it.coerceAtLeast(5) }
        }
    }

    /** Envía la oferta SDP y devuelve la respuesta (answer) de la consola. */
    suspend fun exchangeSdp(offerSdp: String): String {
        val body = buildJsonObject {
            put("messageType", "offer")
            put("sdp", offerSdp)
            putJsonObject("configuration") {
                putJsonObject("chatConfiguration") {
                    put("bytesPerSample", 2)
                    put("expectedClipDurationMs", 20)
                    putJsonObject("format") {
                        put("codec", "opus")
                        put("container", "webm")
                    }
                    put("numChannels", 1)
                    put("sampleFrequencyHz", 24000)
                }
                version("chat", 1, 1)
                version("control", 1, 3)
                version("input", 1, 8)
                version("message", 1, 1)
            }
        }
        Http.postJson(url("sdp"), body, auth)
        val exchange = pollExchange("sdp").jsonObject
        return exchange["sdp"]?.jsonPrimitive?.contentOrNull
            ?: throw StreamException(
                "La consola rechazó la conexión WebRTC (${exchange["status"]?.jsonPrimitive?.contentOrNull})",
            )
    }

    suspend fun exchangeIce(local: List<IceCandidate>): List<IceCandidate> {
        val body = buildJsonObject {
            put("messageType", "iceCandidate")
            putJsonArray("candidate") {
                local.forEach { c ->
                    add(buildJsonObject {
                        put("candidate", c.candidate)
                        put("sdpMid", c.sdpMid)
                        put("sdpMLineIndex", c.sdpMLineIndex)
                    })
                }
            }
        }
        Http.postJson(url("ice"), body, auth)
        return pollExchange("ice").jsonArray.map {
            val c = it.jsonObject
            IceCandidate(
                candidate = c.str("candidate"),
                sdpMid = c["sdpMid"]?.jsonPrimitive?.contentOrNull,
                sdpMLineIndex = c["sdpMLineIndex"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            )
        }
    }

    /** El servicio responde a SDP e ICE de forma asíncrona: hay que preguntar hasta que llega. */
    private suspend fun pollExchange(resource: String): JsonElement {
        repeat(60) {
            val res: HttpResponse = Http.get(url(resource), auth)
            if (res.body.isNotBlank()) {
                val exchange = res.json().jsonObject["exchangeResponse"]?.jsonPrimitive?.contentOrNull
                if (exchange != null) return Http.json.parseToJsonElement(exchange)
            }
            delay(1000)
        }
        throw StreamException("La consola no respondió ($resource)")
    }

    suspend fun keepAlive() {
        Http.postJson(url("keepalive"), JsonObject(emptyMap()), auth)
    }

    suspend fun stop() {
        val url = sessionUrl ?: return
        sessionUrl = null
        runCatching { Http.delete(url, auth) }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.version(name: String, min: Int, max: Int) =
        putJsonObject(name) {
            put("minVersion", min)
            put("maxVersion", max)
        }

    companion object {
        /** Nos presentamos como el cliente web de xbox.com, que es el que acepta el servicio. */
        private val deviceInfo = mapOf(
            "X-MS-Device-Info" to buildJsonObject {
                putJsonObject("appInfo") {
                    putJsonObject("env") {
                        put("clientAppId", "www.xbox.com")
                        put("clientAppType", "browser")
                        put("clientAppVersion", "26.1.97")
                        put("clientSdkVersion", "10.3.7")
                        put("httpEnvironment", "prod")
                        put("sdkInstallId", "")
                    }
                }
                putJsonObject("dev") {
                    putJsonObject("hw") {
                        put("make", "Microsoft")
                        put("model", "unknown")
                        put("sdktype", "web")
                    }
                    putJsonObject("os") {
                        put("name", "android")
                        put("ver", "22631.2715")
                        put("platform", "desktop")
                    }
                    putJsonObject("displayInfo") {
                        putJsonObject("dimensions") {
                            put("widthInPixels", 1280)
                            put("heightInPixels", 720)
                        }
                        putJsonObject("pixelDensity") {
                            put("dpiX", 1)
                            put("dpiY", 1)
                        }
                    }
                    putJsonObject("browser") {
                        put("browserName", "chrome")
                        put("browserVersion", "130.0")
                    }
                }
            }.toString(),
        )
    }
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
