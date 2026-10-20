package com.voicegeely.app.tts

import java.io.File
import java.security.MessageDigest

/**
 * [REF-03 / S03] Disk cache of replies synthesized by THT Voice's own Kokoro voice.
 *
 * - Key = exact model input text + voice + speaker + speed + voice-pack stamp + [FORMAT]; a new
 *   voice pack, another voice or a frontend change gives a different key (never stale audio).
 * - Holds only what THT Voice already generated for that exact sentence, so a result sentence is
 *   cached per outcome: "Đã bật…" (Confirmed) and "Đã gửi lệnh… chưa xác nhận" are different keys,
 *   and nothing is ever played before the executor result (the caller speaks after the result).
 * - Only short sentences are stored ([MAX_TEXT_CHARS]); total size capped ([maxBytes]) with
 *   least-recently-used eviction. Any read/write error = cache miss, never a crash.
 * File: 4-byte little-endian sample rate + PCM16 mono.
 */
class TtsPhraseCache(private val dir: File, private val maxBytes: Long = MAX_BYTES) {

    data class Clip(val pcm16: ByteArray, val sampleRate: Int) {
        val frames: Int get() = pcm16.size / 2
    }

    fun key(modelText: String, voice: String, speakerId: Int, speed: Float, packStamp: String): String {
        val material = listOf(FORMAT, packStamp, voice, speakerId.toString(), speed.toString(), modelText).joinToString("\u0001")
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun cacheable(text: String): Boolean = text.isNotBlank() && text.length <= MAX_TEXT_CHARS

    @Synchronized
    fun get(key: String): Clip? = runCatching {
        val file = File(dir, "$key.pcm")
        if (!file.isFile) return null
        val bytes = file.readBytes()
        if (bytes.size < 6) { file.delete(); return null }
        val rate = (bytes[0].toInt() and 0xff) or ((bytes[1].toInt() and 0xff) shl 8) or
            ((bytes[2].toInt() and 0xff) shl 16) or ((bytes[3].toInt() and 0xff) shl 24)
        if (rate !in 8_000..96_000) { file.delete(); return null }
        file.setLastModified(System.currentTimeMillis())
        Clip(bytes.copyOfRange(4, bytes.size), rate)
    }.getOrNull()

    @Synchronized
    fun put(key: String, clip: Clip) {
        runCatching {
            if (clip.pcm16.isEmpty() || clip.pcm16.size + 4 > maxBytes) return
            dir.mkdirs()
            val tmp = File(dir, "$key.tmp")
            tmp.outputStream().use { out ->
                val r = clip.sampleRate
                out.write(byteArrayOf((r and 0xff).toByte(), ((r ushr 8) and 0xff).toByte(), ((r ushr 16) and 0xff).toByte(), ((r ushr 24) and 0xff).toByte()))
                out.write(clip.pcm16)
            }
            val target = File(dir, "$key.pcm")
            if (!tmp.renameTo(target)) { tmp.delete(); return }
            trim()
        }
    }

    @Synchronized
    fun clear() { runCatching { dir.listFiles()?.forEach { it.delete() } } }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".pcm") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
    }

    companion object {
        /** v2: tail-trimmed audio (TtsTailTrim); old untrimmed clips are never played. */
        const val FORMAT = "pcm16-v2"
        const val MAX_TEXT_CHARS = 120
        const val MAX_BYTES = 24L * 1024 * 1024

        fun toPcm16(samples: FloatArray): ByteArray {
            val out = ByteArray(samples.size * 2)
            samples.forEachIndexed { index, value ->
                val sample = (value.coerceIn(-1f, 1f) * 32767f).toInt()
                out[index * 2] = (sample and 0xff).toByte()
                out[index * 2 + 1] = ((sample ushr 8) and 0xff).toByte()
            }
            return out
        }
    }
}
