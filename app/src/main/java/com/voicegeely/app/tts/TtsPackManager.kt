package com.voicegeely.app.tts

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.voicegeely.app.security.LicenseConfig
import com.voicegeely.app.security.LicenseManager
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Downloads or side-loads the Kokoro voice pack pinned by this APK and installs it.
 *
 * Worker contract (docs/OFFLINE_MODEL_PACKAGING.md):
 *   GET {worker}/api/model?name=kokoro_vi&sha256=<pinned sha>   (license headers)
 *     → {"ok":true,"downloadUrl":"{worker}/api/model/file?name=kokoro_vi&sha256=…","sha256":"…","sizeBytes":N}
 *   GET downloadUrl (license headers) → the zip bytes.
 * The pack is only installed when its size and SHA-256 match PACK.txt shipped in this APK.
 */
object TtsPackManager {
    sealed class State {
        object Idle : State()
        data class Running(val stage: String, val percent: Int) : State()
        data class Done(val message: String) : State()
        data class Failed(val message: String) : State()
    }

    @Volatile var state: State = State.Idle
        private set

    private val listeners = CopyOnWriteArraySet<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    fun addListener(listener: (State) -> Unit) { listeners += listener }
    fun removeListener(listener: (State) -> Unit) { listeners -= listener }

    private fun publish(next: State) {
        state = next
        main.post { listeners.forEach { it(next) } }
    }

    fun isRunning(): Boolean = running.get()
    fun cancel() = cancelled.set(true)

    fun isInstalled(context: Context): Boolean {
        val pack = OfflineTtsModelInstaller.packInfo(context) ?: return true
        return KokoroPack.isInstalled(OfflineTtsModelInstaller.installedDir(context), pack)
    }

    fun remove(context: Context) {
        KokoroPack.remove(OfflineTtsModelInstaller.installedDir(context))
        publish(State.Idle)
    }

    /** Download from the update Worker, verify and install. [onInstalled] runs on the main thread. */
    fun download(context: Context, onInstalled: () -> Unit) {
        val app = context.applicationContext
        val pack = OfflineTtsModelInstaller.packInfo(app) ?: run { publish(State.Done("Giọng đọc đã có sẵn trong bản cài này.")); return }
        if (!running.compareAndSet(false, true)) return
        cancelled.set(false)
        Thread {
            try {
                publish(State.Running("Đang kết nối máy chủ...", 0))
                val headers = LicenseManager(app).authHeaders()
                if (headers.isEmpty()) return@Thread publish(State.Failed("License chưa sẵn sàng để tải giọng đọc."))
                val url = requestDownloadUrl(pack, headers) ?: return@Thread
                val part = File(File(app.cacheDir, "models").apply { mkdirs() }, pack.file + ".part")
                part.delete()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("User-Agent", "THTVoice-Model/1.0")
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                try {
                    val status = conn.responseCode
                    if (status !in 200..299) return@Thread publish(State.Failed("Tải giọng đọc lỗi HTTP $status."))
                    val ok = copyWithProgress(conn.inputStream, part, pack.sizeBytes, "Đang tải giọng đọc")
                    if (!ok) { part.delete(); return@Thread }
                } finally {
                    conn.disconnect()
                }
                install(app, part, pack, onInstalled)
            } catch (t: Throwable) {
                Log.w(TAG, "voice pack download failed type=${t.javaClass.simpleName}")
                publish(State.Failed("Không tải được giọng đọc: ${t.message.orEmpty()}"))
            } finally {
                running.set(false)
            }
        }.apply { name = "THTVoice-PackDownload"; isDaemon = true }.start()
    }

    /** Install from a zip the user picked (USB / downloads), e.g. before the Worker serves packs. */
    fun installFromUri(context: Context, uri: Uri, onInstalled: () -> Unit) {
        val app = context.applicationContext
        val pack = OfflineTtsModelInstaller.packInfo(app) ?: run { publish(State.Done("Giọng đọc đã có sẵn trong bản cài này.")); return }
        if (!running.compareAndSet(false, true)) return
        cancelled.set(false)
        Thread {
            try {
                val part = File(File(app.cacheDir, "models").apply { mkdirs() }, pack.file + ".part")
                part.delete()
                val input = app.contentResolver.openInputStream(uri) ?: return@Thread publish(State.Failed("Không mở được tệp đã chọn."))
                // Accept both the pack itself and the GitHub artifact zip that wraps it.
                val ok = input.use { copyWithProgress(it, part, pack.sizeBytes, "Đang sao chép tệp giọng đọc", exactSize = false) }
                if (!ok) { part.delete(); return@Thread }
                if (part.length() == pack.sizeBytes) {
                    install(app, part, pack, onInstalled)
                } else {
                    publish(State.Running("Đang mở gói giọng đọc bên trong tệp đã chọn...", 100))
                    val inner = File(part.parentFile, pack.file)
                    val extracted = KokoroPack.extractFromWrapper(part, pack, inner)
                    part.delete()
                    if (!extracted) {
                        inner.delete()
                        publish(State.Failed("Tệp đã chọn không phải gói giọng đọc của bản này (cần ${pack.file})."))
                        return@Thread
                    }
                    install(app, inner, pack, onInstalled)
                }
            } catch (t: Throwable) {
                publish(State.Failed("Không cài được tệp giọng đọc: ${t.message.orEmpty()}"))
            } finally {
                running.set(false)
            }
        }.apply { name = "THTVoice-PackImport"; isDaemon = true }.start()
    }

    private fun install(app: Context, zip: File, pack: KokoroPack.Info, onInstalled: () -> Unit) {
        publish(State.Running("Đang kiểm tra và cài giọng đọc...", 100))
        val result = KokoroPack.installFromZip(zip, pack, OfflineTtsModelInstaller.installedDir(app)) { !cancelled.get() }
        zip.delete()
        when (result) {
            KokoroPack.InstallResult.Installed -> {
                publish(State.Done("Đã cài giọng đọc Ngọc Huyền và Diễm Trinh."))
                main.post(onInstalled)
            }
            is KokoroPack.InstallResult.Failed -> publish(State.Failed(result.reason))
        }
    }

    private fun requestDownloadUrl(pack: KokoroPack.Info, headers: Map<String, String>): String? {
        val endpoint = LicenseConfig.endpoint("api/model") + "?name=kokoro_vi&sha256=${pack.sha256}"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "THTVoice-Model/1.0")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (status == 404) {
                publish(State.Failed("Máy chủ chưa có gói giọng đọc cho bản này (mã ${pack.sha256.take(12)}). Có thể dùng \"Cài từ tệp\"."))
                return null
            }
            if (status !in 200..299 || !json.optBoolean("ok", false)) {
                publish(State.Failed(json.optString("message", "Máy chủ từ chối tải giọng đọc (HTTP $status).")))
                return null
            }
            val url = json.optString("downloadUrl", "")
            val sha = json.optString("sha256", "").lowercase()
            if (sha != pack.sha256 || !isTrustedModelUrl(url)) {
                publish(State.Failed("Thông tin gói giọng đọc từ máy chủ không khớp với bản cài này."))
                return null
            }
            return url
        } finally {
            conn.disconnect()
        }
    }

    private fun copyWithProgress(input: InputStream, out: File, total: Long, stage: String, exactSize: Boolean = true): Boolean {
        val limit = if (exactSize) total else total * 2
        var received = 0L
        var lastPercent = -1
        out.outputStream().use { output ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                if (cancelled.get()) {
                    publish(State.Failed("Đã hủy."))
                    return false
                }
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                received += n
                if (received > limit) {
                    publish(State.Failed("Tệp lớn hơn kích thước mong đợi."))
                    return false
                }
                val percent = ((received * 100) / total).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    publish(State.Running(stage, percent))
                }
            }
        }
        if (exactSize && received != total) {
            publish(State.Failed("Tệp chưa tải đủ ($received/$total byte)."))
            return false
        }
        return true
    }

    /** Only the configured Worker may serve packs (same host, https, fixed path). */
    fun isTrustedModelUrl(value: String): Boolean = try {
        val expected = URL(LicenseConfig.WORKER_URL)
        val actual = URL(value)
        actual.protocol.equals("https", true) && actual.host.equals(expected.host, true) &&
            actual.path == "/api/model/file" && actual.ref == null
    } catch (_: Throwable) {
        false
    }

    private const val TAG = "THTVoicePack"
}
