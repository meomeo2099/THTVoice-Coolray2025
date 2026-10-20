package com.voicegeely.app.update

import android.content.Context
import com.voicegeely.app.security.LicenseConfig
import com.voicegeely.app.security.LicenseManager
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Client for the existing Cloudflare update proxy. GitHub credentials remain server-side. */
class UpdateApiClient(
    context: Context,
    private val licenseManager: LicenseManager = LicenseManager(context)
) {
    private val appContext = context.applicationContext

    fun check(currentVersionCode: Long): UpdateCheckResult {
        val headers = licenseManager.authHeaders()
        if (headers.isEmpty()) return UpdateCheckResult(false, message = "License chưa sẵn sàng để kiểm tra cập nhật.")

        val endpoint = LicenseConfig.endpoint("api/update") + "?current_code=$currentVersionCode"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6500
                readTimeout = 10000
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "THTVoice-Update/1.0")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (status !in 200..299 || !json.optBoolean("ok", false)) {
                return UpdateCheckResult(false, message = json.optString("message", "Update HTTP $status"))
            }

            val available = json.optBoolean("updateAvailable", false)
            if (!available) {
                return UpdateCheckResult(true, updateAvailable = false, message = "THT Voice đang ở bản mới nhất.")
            }

            val versionCode = json.optLong("versionCode", 0L)
            val versionName = json.optString("versionName", "").trim()
            val sha256 = json.optString("sha256", "").trim()
            val downloadUrl = json.optString("downloadUrl", "").trim()
            val sizeBytes = json.optLong("sizeBytes", 0L).coerceAtLeast(0L)
            if (versionCode <= currentVersionCode || versionName.isBlank() ||
                UpdatePackageVerifier.normalizeSha256(sha256) == null || !isTrustedWorkerUrl(downloadUrl)
            ) {
                return UpdateCheckResult(false, message = "Metadata cập nhật từ máy chủ không hợp lệ.")
            }

            UpdateCheckResult(
                ok = true,
                updateAvailable = true,
                metadata = UpdateMetadata(
                    packageName = appContext.packageName,
                    versionCode = versionCode,
                    versionName = versionName,
                    sha256 = sha256,
                    downloadUrl = downloadUrl,
                    sizeBytes = sizeBytes
                ),
                changelog = json.optString("changelog", ""),
                forceUpdate = json.optBoolean("forceUpdate", false),
                sizeBytes = sizeBytes,
                releaseTag = json.optString("releaseTag", ""),
                message = "Có bản THT Voice $versionName."
            )
        } catch (t: Throwable) {
            UpdateCheckResult(false, message = "Không kiểm tra được cập nhật: ${t.message.orEmpty()}")
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private val ASSET_QUERY = Regex("^asset_id=[1-9][0-9]*$")

        fun isTrustedWorkerUrl(value: String): Boolean = try {
            val expected = URL(LicenseConfig.WORKER_URL)
            val actual = URL(value)
            actual.protocol.equals("https", true) &&
                actual.protocol.equals(expected.protocol, true) &&
                actual.host.equals(expected.host, true) &&
                effectivePort(actual) == effectivePort(expected) &&
                actual.path == "/api/update/apk" &&
                ASSET_QUERY.matches(actual.query.orEmpty()) &&
                actual.ref == null
        } catch (_: Throwable) {
            false
        }

        private fun effectivePort(url: URL): Int =
            if (url.port >= 0) url.port else url.defaultPort
    }
}

data class UpdateCheckResult(
    val ok: Boolean,
    val updateAvailable: Boolean = false,
    val metadata: UpdateMetadata? = null,
    val changelog: String = "",
    val forceUpdate: Boolean = false,
    val sizeBytes: Long = 0L,
    val releaseTag: String = "",
    val message: String = ""
)
