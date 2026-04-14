package com.android.cheburgate.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val tagName: String, val htmlUrl: String)

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/garik128/CheburGate/releases/latest"

    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val url = json.getString("html_url")
            ReleaseInfo(tag, url)
        } catch (_: Exception) {
            null
        }
    }

    /** Возвращает true, если тег релиза отличается от текущей версии */
    fun isNewer(latestTag: String, currentVersion: String): Boolean {
        val normalized = latestTag.trimStart('v', 'V')
        return normalized != currentVersion
    }
}
