package com.kvtral.joystick.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, val body: String, val url: String) :
    IOException("HTTP $code en $url: ${body.take(300)}")

class HttpResponse(val code: Int, val body: String, val headers: Map<String, List<String>>) {
    fun json(): JsonElement = Http.json.parseToJsonElement(body)
}

/** Cliente HTTP mínimo sobre [HttpURLConnection]: las APIs de Xbox solo necesitan JSON y formularios. */
object Http {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private const val TIMEOUT_MS = 15_000

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()) =
        request("GET", url, headers, null)

    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()) =
        request("DELETE", url, headers, null)

    suspend fun postJson(url: String, body: JsonElement, headers: Map<String, String> = emptyMap()) =
        request("POST", url, headers + ("Content-Type" to "application/json"), body.toString())

    suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()) =
        request(
            "POST", url,
            headers + ("Content-Type" to "application/x-www-form-urlencoded"),
            form.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" },
        )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Cache-Control", "no-store, must-revalidate, no-cache")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, text, url)
            HttpResponse(code, text, conn.headerFields.filterKeys { it != null })
        } finally {
            conn.disconnect()
        }
    }
}
