package com.voicegeely.app.tts

import android.content.Context
import android.util.AtomicFile
import com.voicegeely.app.security.LicenseConfig
import com.voicegeely.app.security.LicenseManager
import com.voicegeely.app.settings.TtsVoice
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject

internal object Mp3VoicePacks {
    private const val MAX_PACK = 24 * 1024 * 1024
    private val filePattern = Regex("[a-zA-Z0-9_-]{1,100}\\.(?:mp3|m4a)")
    private val idPattern = Regex("[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+")
    private val cached = mutableMapOf<TtsVoice, Pack>()
    private val downloadLock = Any()

    data class Pack(val index: Map<String, String>, val clips: Map<String, ByteArray>, val version: String? = null, val kinds: Map<String, String> = emptyMap(), val format: String = "mp3") {
        fun isSentence(id: String) = kinds[id] == "cau"
        fun extension(@Suppress("UNUSED_PARAMETER") id: String) = format
    }
    private class HttpFailure(val status: Int) : IllegalStateException("Máy chủ tải giọng trả mã $status.")
    class LicenseRequired : IllegalStateException("Cần kích hoạt THT Voice để tải gói giọng.")

    private fun slug(voice: TtsVoice) = if (voice == TtsVoice.NGOC_HUYEN) "ngoc-huyen" else "diem-trinh"
    private fun packFile(context: Context, voice: TtsVoice): AtomicFile =
        AtomicFile(File(File(context.filesDir, "mp3-voice").apply { mkdirs() }, slug(voice) + "-m4a.zip"))

    @Synchronized
    fun load(context: Context, voice: TtsVoice): Pack? {
        cached[voice]?.let { return it }
        return runCatching { readPack(packFile(context, voice).openRead(), voice) }.recoverCatching { readPack(File(File(context.filesDir, "mp3-voice"), slug(voice) + ".zip").inputStream(), voice) }
            .getOrNull()?.also { cached[voice] = it }
    }

    @Synchronized
    fun isReady(voice: TtsVoice): Boolean = cached.containsKey(voice)

    fun status(context: Context, voice: TtsVoice): String {
        val pack = load(context, voice) ?: return "Chưa có gói giọng · Bấm Tải gói"
        val version = pack.version?.uppercase(java.util.Locale.ROOT) ?: "Không rõ phiên bản"
        return "Đã cài: $version · ${pack.format.uppercase(java.util.Locale.ROOT)} · ${pack.clips.size} đoạn · Dùng ngoại tuyến"
    }

    /**
     * THTVoice: background check by the service. Downloads the pack after install (release APKs carry
     * none) and newer revisions once a day. Returns the update message, or null when it was not time.
     */
    fun autoUpdate(context: Context, voice: TtsVoice, licensed: Boolean, online: Boolean,
                   now: Long = System.currentTimeMillis()): String? {
        val preferences = context.getSharedPreferences("m4a-voice-auto", Context.MODE_PRIVATE)
        val key = slug(voice) + "-checked"
        val hasPack = load(context, voice) != null
        if (!Mp3AutoUpdatePolicy.shouldCheck(licensed, online, hasPack, preferences.getLong(key, 0L), now)) return null
        preferences.edit().putLong(key, now).apply()
        return runCatching { update(context, voice) }.getOrElse { it.message ?: "Không tải được gói giọng." }
    }

    fun update(context: Context, voice: TtsVoice): String = synchronized(downloadLock) {
        check(LicenseConfig.isConfigured()) { "Chưa cấu hình máy chủ tải giọng." }
        val headers = LicenseManager(context).authHeaders()
        if (headers.isEmpty()) throw LicenseRequired()
        val metaUrl = LicenseConfig.endpoint("api/voice/" + slug(voice))
        var missingRoute = false
        val metadata = try {
            JSONObject(get("$metaUrl?format=m4a", 16 * 1024, headers).toString(Charsets.UTF_8))
        } catch (error: HttpFailure) {
            if (!M4aRelease.usePublicFallback(error.status, null, LicenseManager(context).isLicensed())) throw error
            missingRoute = true
            JSONObject()
        }
        // Old Workers ignore format=m4a and return valid MP3 metadata. Only that successful,
        // licensed response (or a missing route with a valid local license) may use pinned media.
        val legacy = missingRoute || M4aRelease.legacyMetadata(metadata.optInt("schema"),
            metadata.optString("format"), metadata.optString("sha256"), metadata.optInt("size"))
        val publicAsset = if (legacy && M4aRelease.usePublicFallback(
                if (missingRoute) 404 else 200, metadata.optString("format"), true))
            M4aRelease.asset(voice == TtsVoice.NGOC_HUYEN) else null
        if (publicAsset != null) metadata.put("schema", 1).put("format", "m4a")
            .put("size", publicAsset.size).put("sha256", publicAsset.sha256).put("version", "r6")
        require(metadata.optString("format") == "m4a") { "Máy chủ chưa có gói M4A; gói đã cài vẫn dùng được." }
        val size = metadata.getInt("size")
        require(size in 1..MAX_PACK) { "Dung lượng gói giọng không hợp lệ." }
        val sha = metadata.getString("sha256").lowercase()
        require(sha.matches(Regex("[0-9a-f]{64}")) && metadata.getInt("schema") == 1) { "Gói giọng không tương thích." }
        val revision = metadata.getString("version").takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,32}")) }
            ?: error("Phiên bản gói không hợp lệ.")
        val preferences = context.getSharedPreferences("mp3-voice", Context.MODE_PRIVATE)
        val slug = slug(voice)
        val currentIsValid = synchronized(this) {
            preferences.getString("$slug-sha", null) == sha && runCatching {
                Mp3PackIntegrity.matches(packFile(context, voice).openRead(), sha, MAX_PACK) &&
                    readPack(packFile(context, voice).openRead(), voice).let { parsed ->
                        cached[voice] = parsed
                        true
                    }
            }.getOrDefault(false)
        }
        if (currentIsValid) {
            return@synchronized "Gói giọng đã là bản mới nhất."
        }
        val zip = if (publicAsset == null) get("$metaUrl/file?format=m4a&sha256=$sha", size, headers, allowAssetRedirect = true)
            else try { get(publicAsset.url, size, emptyMap(), allowAssetRedirect = true) }
            catch (error: Exception) { throw IllegalStateException("Chưa truy cập được gói M4A. Gói đã cài vẫn sử dụng được.", error) }
        require(zip.size == size) { "Gói giọng tải về chưa đầy đủ." }
        val actual = MessageDigest.getInstance("SHA-256").digest(zip).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(actual == sha) { "Mã kiểm tra gói giọng không khớp." }
        val parsed = readPack(zip.inputStream(), voice)
        require(parsed.format == "m4a")
        synchronized(this) {
            val file = packFile(context, voice)
            val stream = file.startWrite()
            try {
                stream.write(zip)
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
            cached[voice] = parsed
            preferences.edit().putString(slug, revision).putString("$slug-sha", sha).apply()
        }
        "Đã cài gói giọng M4A."
    }

    private fun get(
        address: String,
        limit: Int,
        headers: Map<String, String>,
        allowAssetRedirect: Boolean = false
    ): ByteArray {
        val url = URL(address)
        require(url.protocol == "https" && url.userInfo == null) { "Đường dẫn tải giọng không hợp lệ." }
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_TEMP && allowAssetRedirect) {
                val asset = runCatching { URL(connection.getHeaderField("Location")) }.getOrNull()
                require(asset != null && asset.protocol == "https" && asset.userInfo == null &&
                    asset.port in listOf(-1, 443) &&
                    asset.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
                    "Đường dẫn tệp MP3 từ máy chủ không hợp lệ."
                }
                return get(asset.toExternalForm(), limit, emptyMap())
            }
            check(code != 401) { "License chưa có quyền tải gói MP3." }
            if (code == 404) throw HttpFailure(code)
            check(code != 503) { "Máy chủ chưa cấu hình kho gói MP3." }
            check(code == 200) { "Máy chủ tải giọng trả mã $code." }
            connection.inputStream.use { bounded(it, limit) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readPack(input: InputStream, voice: TtsVoice): Pack {
        val assets = mutableMapOf<String, ByteArray>()
        var manifest: JSONObject? = null
        var total = 0
        var entries = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 4000) { "Gói giọng có quá nhiều tệp." }
                val name = entry.name
                val limit = when {
                    name == "manifest.json" -> 1024 * 1024
                    (name.startsWith("mp3/") || name.startsWith("m4a/")) && filePattern.matches(name.substringAfter("/")) -> 256 * 1024
                    else -> 512 * 1024
                }
                val bytes = bounded(zip, limit)
                total += bytes.size
                require(total <= MAX_PACK) { "Gói giọng quá lớn." }
                if (name == "manifest.json") {
                    require(manifest == null)
                    manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                } else if ((name.startsWith("mp3/") || name.startsWith("m4a/")) && filePattern.matches(name.substringAfter("/"))) {
                    require(bytes.isNotEmpty() && assets.put(name.substringAfter("/"), bytes) == null)
                }
            }
        }
        val info = requireNotNull(manifest) { "Gói giọng thiếu danh mục." }
        require(info.getInt("speaker") == OfflineTtsModelSpec.speakerId(voice)) { "Gói tải về sai giọng." }
        val format = info.optString("format", "mp3")
        require(format == "mp3" || format == "m4a")
        val rows = info.getJSONArray("clips")
        val index = mutableMapOf<String, String>()
        val clips = mutableMapOf<String, ByteArray>()
        val kinds = mutableMapOf<String, String>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val id = row.getString("id")
            val name = row.getString("file")
            require(filePattern.matches(name) && name.endsWith(".$format"))
            val key = Mp3ReplyText.key(row.getString("loi_doc"))
            require(idPattern.matches(id) && key.isNotBlank())
            require(clips.put(id, requireNotNull(assets[name])) == null)
            require(index.put(key, id) == null)
            kinds[id] = row.optString("loai")
        }
        require(listOf("common/sent", "common/confirmed", "common/rejected").all(clips::containsKey)) { "Gói giọng thiếu câu phản hồi bắt buộc." }
        val version = info.optString("version").takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,32}")) }
        return Pack(index, clips, version, kinds, format)
    }

    private fun bounded(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return out.toByteArray()
            require(out.size() + count <= limit) { "Tệp giọng vượt giới hạn dung lượng." }
            out.write(buffer, 0, count)
        }
    }
}
