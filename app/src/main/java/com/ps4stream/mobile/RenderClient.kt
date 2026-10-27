package com.ps4stream.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.SecureRandom

/** Synchronous I/O functions: only call from a background Executor. */
object RenderClient {
    private val random = SecureRandom()

    data class Subtitle(val lang: String, val url: String)
    data class Media(val title: String, val quality: String, val url: String, val subs: List<Subtitle>, val pageUrl: String)
    data class Result(val pin: String, val warnings: List<String>)

    private fun safeRemoteUrl(raw: String): URL {
        val uri = URI(raw)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && (uri.port == -1 || uri.port == 443)) {
            "Разрешены только HTTPS-ссылки на публичные файлы"
        }
        val host = uri.host.lowercase()
        require(host != "localhost" && !host.endsWith(".localhost")) { "Локальные ссылки запрещены" }
        // Avoid private IP targets; CDN hostnames are checked again by the HTTPS client.
        if (host.matches(Regex("[0-9.]+"))) {
            val addr = InetAddress.getByName(host)
            require(!addr.isAnyLocalAddress && !addr.isLoopbackAddress && !addr.isSiteLocalAddress && !addr.isLinkLocalAddress) {
                "Локальные IP запрещены"
            }
        }
        return uri.toURL()
    }

    private fun subtitle(sub: Subtitle, referer: String): String {
        val url = safeRemoteUrl(sub.url)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 9000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        connection.setRequestProperty("Referer", referer.substringBefore('#'))
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            val length = connection.contentLengthLong
            if (length > 750_000) throw IllegalStateException("Файл субтитров слишком большой")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 750_000) throw IllegalStateException("Файл больше 750 КБ")
                    output.write(buffer, 0, count)
                }
            }
            val bytes = output.toByteArray()
            val utf = bytes.toString(Charsets.UTF_8)
            val decoded = if ('\uFFFD' in utf) bytes.toString(charset("windows-1251")) else utf
            if (!decoded.contains("-->")) throw IllegalStateException("Нет временных меток SRT/VTT")
            return decoded
        } finally { connection.disconnect() }
    }

    fun send(media: Media, selectedSubs: List<Subtitle>, apiKey: String): Result {
        require(apiKey.length >= 12) { "Сначала сохрани API_KEY в настройках" }
        safeRemoteUrl(media.url)
        val warnings = mutableListOf<String>()
        val subs = JSONArray()
        selectedSubs.take(8).forEach { entry ->
            try {subs.put(JSONObject().put("lang", entry.lang.take(80)).put("text", subtitle(entry, media.pageUrl)))}
            catch (e: Exception) {warnings.add("${entry.lang}: ${e.message ?: "не удалось получить"}")}
        }
        repeat(5) {
            val pin = random.nextInt(10_000).toString().padStart(4, '0')
            val payload = JSONObject()
                .put("pin", pin).put("title", media.title.take(180))
                .put("quality", media.quality.take(40))
                .put("url", media.url).put("subtitles", subs)
            val connection = URL(AppConfig.RENDER_URL + "/api/launch").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000 // Render Free may need to wake up.
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val response: String
            val code: Int
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                code = connection.responseCode
                val input = if (code in 200..299) connection.inputStream else connection.errorStream
                response = input?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(20_000) } ?: "{}"
            } finally { connection.disconnect() }
            val json = runCatching { JSONObject(response) }.getOrElse { JSONObject() }
            if (code == 409) return@repeat
            if (code !in 200..299 || !json.optBoolean("ok")) {
                throw IllegalStateException(json.optString("error").ifEmpty { "Render: HTTP $code" })
            }
            return Result(pin, warnings)
        }
        throw IllegalStateException("Не удалось создать свободный PIN. Попробуй повторить отправку.")
    }
}
