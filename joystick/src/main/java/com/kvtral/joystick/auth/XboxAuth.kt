package com.kvtral.joystick.auth

import android.content.Context
import com.kvtral.joystick.net.Http
import com.kvtral.joystick.net.HttpException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

data class Region(val name: String, val baseUri: String, val isDefault: Boolean)

/** Token de la plataforma de streaming (gssv) que usan las llamadas de Remote Play. */
data class StreamingToken(
    val gsToken: String,
    val regions: List<Region>,
    val expiresAtMs: Long,
) {
    val baseUri: String
        get() = (regions.firstOrNull { it.isDefault } ?: regions.first()).baseUri
}

class AuthException(message: String) : Exception(message)

/**
 * Inicio de sesión con la cuenta Microsoft igual que el cliente web de xbox.com:
 * código de dispositivo (MSAL) → token de usuario Xbox Live → XSTS para gssv → gsToken de "xhome".
 * Solo se guarda el refresh token; el resto se pide de nuevo en cada arranque.
 */
class XboxAuth(context: Context) {

    private val prefs = context.getSharedPreferences("xbox_auth", Context.MODE_PRIVATE)

    var gamertag: String?
        get() = prefs.getString("gamertag", null)
        private set(value) = prefs.edit().putString("gamertag", value).apply()

    val isSignedIn: Boolean
        get() = prefs.getString(KEY_REFRESH, null) != null

    fun signOut() = prefs.edit().clear().apply()

    suspend fun requestDeviceCode(): DeviceCode {
        val res = Http.postForm(
            "$MS_LOGIN/devicecode",
            mapOf("client_id" to CLIENT_ID, "scope" to SCOPE),
        ).json().jsonObject
        return DeviceCode(
            deviceCode = res.str("device_code"),
            userCode = res.str("user_code"),
            verificationUri = res.str("verification_uri"),
            intervalSeconds = res["interval"]?.jsonPrimitive?.int ?: 5,
            expiresInSeconds = res["expires_in"]?.jsonPrimitive?.int ?: 900,
        )
    }

    /** Espera a que el usuario introduzca el código en microsoft.com/link. */
    suspend fun pollDeviceCode(code: DeviceCode) {
        var interval = code.intervalSeconds.coerceAtLeast(1)
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            try {
                val res = Http.postForm(
                    "$MS_LOGIN/token",
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id" to CLIENT_ID,
                        "device_code" to code.deviceCode,
                    ),
                ).json().jsonObject
                saveMsaTokens(res)
                return
            } catch (e: HttpException) {
                val error = runCatching {
                    Http.json.parseToJsonElement(e.body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                when (error) {
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    "authorization_declined" -> throw AuthException("Has rechazado el inicio de sesión.")
                    "expired_token" -> throw AuthException("El código ha caducado. Vuelve a intentarlo.")
                    else -> throw e
                }
            }
        }
        throw AuthException("El código ha caducado. Vuelve a intentarlo.")
    }

    private var msaAccessToken: String? = null
    private var msaExpiresAtMs = 0L

    private fun saveMsaTokens(res: JsonObject) {
        msaAccessToken = res.str("access_token")
        msaExpiresAtMs = System.currentTimeMillis() + (res["expires_in"]?.jsonPrimitive?.int ?: 3600) * 1000L
        res["refresh_token"]?.jsonPrimitive?.contentOrNull?.let {
            prefs.edit().putString(KEY_REFRESH, it).apply()
        }
    }

    private suspend fun msaAccessToken(): String {
        msaAccessToken?.let { if (System.currentTimeMillis() < msaExpiresAtMs - 60_000) return it }
        val refresh = prefs.getString(KEY_REFRESH, null) ?: throw AuthException("Inicia sesión primero.")
        val res = try {
            Http.postForm(
                "$MS_LOGIN/token",
                mapOf(
                    "client_id" to CLIENT_ID,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refresh,
                    "scope" to SCOPE,
                ),
            ).json().jsonObject
        } catch (e: HttpException) {
            if (e.code == 400) {
                signOut()
                throw AuthException("La sesión ha caducado. Vuelve a iniciar sesión.")
            }
            throw e
        }
        saveMsaTokens(res)
        return res.str("access_token")
    }

    private suspend fun xblUserToken(): String {
        val accessToken = msaAccessToken()
        val body = buildJsonObject {
            putJsonObject("Properties") {
                put("AuthMethod", "RPS")
                put("RpsTicket", "d=$accessToken")
                put("SiteName", "user.auth.xboxlive.com")
            }
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }
        return Http.postJson(
            "https://user.auth.xboxlive.com/user/authenticate", body, XBL_HEADERS,
        ).json().jsonObject.str("Token")
    }

    private suspend fun xsts(userToken: String, relyingParty: String): JsonObject {
        val body = buildJsonObject {
            putJsonObject("Properties") {
                put("SandboxId", "RETAIL")
                putJsonArray("UserTokens") { add(JsonPrimitive(userToken)) }
            }
            put("RelyingParty", relyingParty)
            put("TokenType", "JWT")
        }
        return try {
            Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body, XBL_HEADERS).json().jsonObject
        } catch (e: HttpException) {
            throw AuthException(xstsError(e))
        }
    }

    private fun xstsError(e: HttpException): String {
        val xerr = runCatching {
            Http.json.parseToJsonElement(e.body).jsonObject["XErr"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (xerr) {
            "2148916233" -> "Esta cuenta Microsoft no tiene perfil de Xbox. Entra una vez en xbox.com para crearlo."
            "2148916235" -> "Xbox Live no está disponible en tu país."
            "2148916236", "2148916237" -> "La cuenta necesita verificación de edad en xbox.com."
            "2148916238" -> "Cuenta infantil: un adulto debe añadirla a una familia Microsoft."
            else -> "Error de autorización de Xbox (${e.code}${xerr?.let { ", XErr $it" } ?: ""})."
        }
    }

    /** Pide un gsToken nuevo para Remote Play. */
    suspend fun streamingToken(): StreamingToken {
        val xsts = xsts(xblUserToken(), "http://gssv.xboxlive.com/")
        xsts["DisplayClaims"]?.jsonObject?.get("xui")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("gtg")?.jsonPrimitive?.contentOrNull?.let { gamertag = it }

        val body = buildJsonObject {
            put("token", xsts.str("Token"))
            put("offeringId", "xhome")
        }
        val res = Http.postJson(
            "https://xhome.gssv-play-prod.xboxlive.com/v2/login/user",
            body,
            mapOf("x-gssv-client" to "XboxComBrowser"),
        ).json().jsonObject
        val regions = res["offeringSettings"]!!.jsonObject["regions"]!!.jsonArray.map {
            val r = it.jsonObject
            Region(
                name = r.str("name"),
                baseUri = r.str("baseUri"),
                isDefault = r["isDefault"]?.jsonPrimitive?.contentOrNull == "true",
            )
        }
        if (regions.isEmpty()) throw AuthException("Remote Play no está disponible para esta cuenta.")
        val seconds = res["durationInSeconds"]?.jsonPrimitive?.int ?: 3600
        return StreamingToken(res.str("gsToken"), regions, System.currentTimeMillis() + seconds * 1000L)
    }

    /** Token que piden algunas sesiones en el estado "ReadyToConnect". */
    suspend fun consoleTransferToken(): String {
        val refresh = prefs.getString(KEY_REFRESH, null) ?: throw AuthException("Inicia sesión primero.")
        val res = Http.postForm(
            "https://login.live.com/oauth20_token.srf",
            mapOf(
                "client_id" to CLIENT_ID,
                "scope" to "service::http://Passport.NET/purpose::PURPOSE_XBOX_CLOUD_CONSOLE_TRANSFER_TOKEN",
                "grant_type" to "refresh_token",
                "refresh_token" to refresh,
            ),
        ).json().jsonObject
        return res.str("access_token")
    }

    companion object {
        /** Id de aplicación del cliente web de Xbox (xbox.com/play). */
        private const val CLIENT_ID = "1f907974-e22b-4810-a9de-d9647380c97e"
        private const val SCOPE = "xboxlive.signin openid profile offline_access"
        private const val MS_LOGIN = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
        private const val KEY_REFRESH = "refresh_token"

        private val XBL_HEADERS = mapOf(
            "x-xbl-contract-version" to "1",
            "Origin" to "https://www.xbox.com",
            "Referer" to "https://www.xbox.com/",
        )
    }
}

internal fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: throw AuthException("Respuesta inesperada: falta \"$key\".")
