package com.ps4stream.mobile

import android.net.Uri
import org.json.JSONArray

object AppConfig {
    const val RENDER_URL = "https://ps4-stream.onrender.com"
    val defaultHosts = listOf("hdrezka-fly.net", "hdrezka.ag", "rezka.ag", "rezka.fi", "rezka-tv.org")

    fun normalizeHost(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) throw IllegalArgumentException("Укажи адрес зеркала")
        val value = if (raw.contains("://")) raw else "https://$raw"
        val uri = Uri.parse(value)
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Некорректный адрес")
        if (uri.scheme != "https" || uri.port != -1 || uri.userInfo != null ||
            !host.contains('.') || !host.matches(Regex("[a-z0-9.-]{4,253}")) || host.contains("..") ||
            host.endsWith(".onrender.com") || host == "onrender.com"
        ) throw IllegalArgumentException("Разрешены только обычные HTTPS-домены зеркал")
        return host
    }

    fun allowedUrl(url: String, hosts: List<String>): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" && hosts.any { host == it || host.endsWith(".$it") }
    }

    fun allHosts(prefs: android.content.SharedPreferences): List<String> {
        val custom = try {
            val a = JSONArray(prefs.getString("mirrors", "[]"))
            (0 until a.length()).mapNotNull { i -> runCatching { normalizeHost(a.getString(i)) }.getOrNull() }
        } catch (_: Exception) { emptyList() }
        return (defaultHosts + custom).distinct()
    }

    fun saveHosts(prefs: android.content.SharedPreferences, custom: List<String>) {
        val a = JSONArray()
        custom.filter { it !in defaultHosts }.distinct().forEach { a.put(it) }
        prefs.edit().putString("mirrors", a.toString()).apply()
    }
}
