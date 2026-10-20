package com.voicegeely.app.update

import android.content.Context
import com.voicegeely.app.security.LicenseManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Downloads only through the trusted Worker URL into app-private cache/updates. */
class UpdateDownloader(
    context: Context,
    private val licenseManager: LicenseManager = LicenseManager(context)
) {
    private val appContext = context.applicationContext

    fun download(
        metadata: UpdateMetadata,
        onProgress: (Int) -> Unit = {},
        shouldContinue: () -> Boolean = { true }
    ): UpdateDownloadResult {
        val url = metadata.downloadUrl
            ?: return UpdateDownloadResult(false, detail = "Metadata chưa có downloadUrl.")
        if (!UpdateApiClient.isTrustedWorkerUrl(url)) {
            return UpdateDownloadResult(false, detail = "downloadUrl không thuộc Worker cập nhật tin cậy.")
        }
        val expectedSha = UpdatePackageVerifier.normalizeSha256(metadata.sha256)
            ?: return UpdateDownloadResult(false, detail = "SHA-256 metadata không hợp lệ.")
        val expectedSize = metadata.sizeBytes.coerceAtLeast(0L)
        if (expectedSize > MAX_APK_BYTES) {
            return UpdateDownloadResult(false, detail = "APK metadata vượt giới hạn an toàn ${MAX_APK_BYTES / 1024 / 1024} MB.")
        }
        val headers = licenseManager.authHeaders()
        if (headers.isEmpty()) return UpdateDownloadResult(false, detail = "License chưa sẵn sàng để tải cập nhật.")

        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val finalFile = File(dir, "THTVoice-${metadata.versionCode}.apk")
        val partFile = File(dir, "THTVoice-${metadata.versionCode}.apk.part")
        partFile.delete()

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 30000
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.android.package-archive")
                setRequestProperty("User-Agent", "THTVoice-Update/1.0")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val status = conn.responseCode
            if (status !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(180)
                return UpdateDownloadResult(false, detail = "Tải APK HTTP $status${if (detail.isNotBlank()) ": $detail" else ""}")
            }

            val advertised = conn.contentLengthLong
            if (advertised > MAX_APK_BYTES) {
                return UpdateDownloadResult(false, detail = "APK vượt giới hạn an toàn ${MAX_APK_BYTES / 1024 / 1024} MB.")
            }
            if (expectedSize > 0L && advertised > 0L && advertised != expectedSize) {
                return UpdateDownloadResult(false, detail = "Kích thước HTTP ($advertised) không khớp metadata ($expectedSize).")
            }

            val progressTotal = when {
                expectedSize > 0L -> expectedSize
                advertised > 0L -> advertised
                else -> 0L
            }
            var received = 0L
            var lastProgress = -1
            if (progressTotal > 0L) {
                lastProgress = 0
                onProgress(0)
            }

            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var chunksSinceCheck = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        received += count
                        if (received > MAX_APK_BYTES) {
                            throw IllegalStateException("APK vượt giới hạn an toàn")
                        }
                        if (expectedSize > 0L && received > expectedSize) {
                            throw IllegalStateException("APK vượt kích thước metadata")
                        }
                        output.write(buffer, 0, count)
                        chunksSinceCheck++
                        if (chunksSinceCheck >= CONTINUE_CHECK_EVERY_CHUNKS) {
                            chunksSinceCheck = 0
                            if (!shouldContinue()) throw DownloadStoppedException()
                        }

                        if (progressTotal > 0L) {
                            val progress = ((received * 100L) / progressTotal).toInt().coerceIn(0, 99)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            if (received <= 0L) {
                partFile.delete()
                return UpdateDownloadResult(false, detail = "APK tải về rỗng.")
            }
            if (expectedSize > 0L && received != expectedSize) {
                partFile.delete()
                return UpdateDownloadResult(
                    false,
                    detail = "Kích thước APK ($received) không khớp metadata ($expectedSize); file đã bị xóa.",
                    bytes = received
                )
            }

            val actualSha = UpdatePackageVerifier.sha256(partFile)
            if (actualSha != expectedSha) {
                partFile.delete()
                return UpdateDownloadResult(
                    false,
                    detail = "SHA-256 sau khi tải không khớp; file đã bị xóa.",
                    bytes = received,
                    actualSha256 = actualSha
                )
            }

            finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.delete()
                return UpdateDownloadResult(false, detail = "Không hoàn tất file APK atomically.")
            }

            onProgress(100)
            UpdateDownloadResult(
                ok = true,
                file = finalFile,
                bytes = received,
                actualSha256 = actualSha,
                detail = "Đã tải và xác minh kích thước + SHA-256 APK."
            )
        } catch (_: DownloadStoppedException) {
            partFile.delete()
            UpdateDownloadResult(false, detail = OtaParkedPolicy.DOWNLOAD_STOPPED_MESSAGE)
        } catch (t: Throwable) {
            partFile.delete()
            UpdateDownloadResult(false, detail = "Không tải được APK: ${t.message.orEmpty()}")
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        // Leaves room for ASR/TTS models while preventing an unbounded response from filling storage.
        const val MAX_APK_BYTES = 512L * 1024L * 1024L
        /** 16 × 64 KiB = re-check the parked state about every 1 MiB. */
        const val CONTINUE_CHECK_EVERY_CHUNKS = 16
    }
}

data class UpdateDownloadResult(
    val ok: Boolean,
    val file: File? = null,
    val bytes: Long = 0L,
    val actualSha256: String? = null,
    val detail: String
)

/** Thrown inside the copy loop when the vehicle leaves the parked state. */
private class DownloadStoppedException : RuntimeException()
