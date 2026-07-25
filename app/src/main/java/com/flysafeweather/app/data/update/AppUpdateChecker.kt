package com.flysafeweather.app.data.update

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
)

object AppUpdateChecker {
    private const val VERSION_JSON_URL =
        "https://github.com/doorcountydrone/FlySafeWeatherApp/releases/latest/download/version.json"
    const val APK_DOWNLOAD_URL =
        "https://github.com/doorcountydrone/FlySafeWeatherApp/releases/latest/download/FlySafeWeather.apk"

    /**
     * Returns an update when GitHub's latest release is newer than [currentVersionCode].
     * Fails quietly (returns null) if offline or the file is missing.
     */
    fun check(currentVersionCode: Int): AvailableUpdate? {
        return runCatching {
            val json = JSONObject(String(getBytes(VERSION_JSON_URL), Charsets.UTF_8))
            val remoteCode = json.getInt("versionCode")
            if (remoteCode <= currentVersionCode) return null
            AvailableUpdate(
                versionCode = remoteCode,
                versionName = json.optString("versionName", "new").ifBlank { "new" },
                apkUrl = json.optString("apkUrl", APK_DOWNLOAD_URL)
                    .ifBlank { APK_DOWNLOAD_URL },
            )
        }.getOrNull()
    }

    /** Simple GET that follows GitHub's cross-host release redirects. */
    fun getBytes(
        url: String,
        connectTimeoutMs: Int = 20_000,
        readTimeoutMs: Int = 180_000,
    ): ByteArray {
        var current = url
        repeat(6) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "FlySafeWeather-Updater")
            try {
                val code = connection.responseCode
                if (code in 301..308) {
                    current = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect without location")
                    return@repeat
                }
                require(code in 200..299) { "HTTP $code for $current" }
                connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    input.copyTo(out)
                    return out.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects for $url")
    }
}
