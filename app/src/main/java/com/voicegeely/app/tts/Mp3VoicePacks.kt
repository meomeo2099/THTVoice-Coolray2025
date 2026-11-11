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
    private const val MAX_PACK = 8 * 1024 * 1024
    private val filePattern = Regex("[a-zA-Z0-9_-]{1,100}\\.mp3")
    private val idPattern = Regex("[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+")
    private val cached = mutableMapOf<TtsVoice, Pack>()
    private val downloadLock = Any()

    data class Pack(val index: Map<String, String>, val clips: Map<String, ByteArray>, val version: String? = null)
    class LicenseRequired : IllegalStateException("Cần kích hoạt THT Voice để tải gói MP3.")

    private fun slug(voice: TtsVoice) = if (voice == TtsVoice.NGOC_HUYEN) "ngoc-huyen" else "diem-trinh"
    private fun packFile(context: Context, voice: TtsVoice): AtomicFile =
        AtomicFile(File(File(context.filesDir, "mp3-voice").apply { mkdirs() }, slug(voice) + ".zip"))

    @Synchronized
    fun load(context: Context, voice: TtsVoice): Pack? {
        cached[voice]?.let { return it }
        return runCatching { readPack(packFile(context, voice).openRead(), voice) }
            .getOrNull()?.also { cached[voice] = it }
    }

    @Synchronized
    fun isReady(voice: TtsVoice): Boolean = cached.containsKey(voice)

    fun status(context: Context, voice: TtsVoice): String {
        val pack = load(context, voice) ?: return "Chưa có gói MP3 · Bấm Tải gói"
        val version = pack.version?.uppercase(java.util.Locale.ROOT) ?: "Không rõ phiên bản"
        return "Đã cài: $version · ${pack.clips.size} đoạn · Dùng ngoại tuyến"
    }

    fun update(context: Context, voice: TtsVoice): String = synchronized(downloadLock) {
        check(LicenseConfig.isConfigured()) { "Chưa cấu hình máy chủ tải giọng." }
        val headers = LicenseManager(context).authHeaders()
        if (headers.isEmpty()) throw LicenseRequired()
        val metaUrl = LicenseConfig.endpoint("api/voice/" + slug(voice))
        val metadata = JSONObject(get(metaUrl, 16 * 1024, headers).toString(Charsets.UTF_8))
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
        val zip = get("$metaUrl/file?sha256=$sha", size, headers)
        require(zip.size == size) { "Gói giọng tải về chưa đầy đủ." }
        val actual = MessageDigest.getInstance("SHA-256").digest(zip).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(actual == sha) { "Mã kiểm tra gói giọng không khớp." }
        val parsed = readPack(zip.inputStream(), voice)
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
        "Đã cài gói giọng MP3."
    }

    private fun get(address: String, limit: Int, headers: Map<String, String>): ByteArray {
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
            check(code != 401) { "License chưa có quyền tải gói MP3." }
            check(code != 404) { "Máy chủ chưa có gói MP3 cho giọng này." }
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
                require(++entries <= 1500) { "Gói giọng có quá nhiều tệp." }
                val name = entry.name
                val limit = when {
                    name == "manifest.json" -> 512 * 1024
                    name.startsWith("mp3/") && filePattern.matches(name.removePrefix("mp3/")) -> 256 * 1024
                    else -> 512 * 1024
                }
                val bytes = bounded(zip, limit)
                total += bytes.size
                require(total <= MAX_PACK) { "Gói giọng quá lớn." }
                if (name == "manifest.json") {
                    require(manifest == null)
                    manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                } else if (name.startsWith("mp3/") && filePattern.matches(name.removePrefix("mp3/"))) {
                    require(bytes.isNotEmpty() && assets.put(name.removePrefix("mp3/"), bytes) == null)
                }
            }
        }
        val info = requireNotNull(manifest) { "Gói giọng thiếu danh mục." }
        require(info.getInt("speaker") == OfflineTtsModelSpec.speakerId(voice)) { "Gói tải về sai giọng." }
        val rows = info.getJSONArray("clips")
        val index = mutableMapOf<String, String>()
        val clips = mutableMapOf<String, ByteArray>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val id = row.getString("id")
            val name = row.getString("file")
            val key = Mp3ReplyText.key(row.getString("loi_doc"))
            require(idPattern.matches(id) && key.isNotBlank())
            require(clips.put(id, requireNotNull(assets[name])) == null)
            require(index.put(key, id) == null)
        }
        require(listOf("common/sent", "common/confirmed", "common/rejected").all(clips::containsKey)) { "Gói giọng thiếu câu phản hồi bắt buộc." }
        val version = info.optString("version").takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,32}")) }
        return Pack(index, clips, version)
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
