package com.voicegeely.app.tts

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * Decodes one compressed reply clip (MP3 or M4A/AAC) to mono 16-bit PCM in memory, so a whole
 * reply can be played as one buffer. Null when the clip cannot be decoded (caller falls back).
 */
internal object ClipDecoder {
    data class Pcm(val samples: ShortArray, val sampleRate: Int)

    private class Bytes(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0 || position >= data.size) return -1
            val n = minOf(size, data.size - position.toInt())
            System.arraycopy(data, position.toInt(), buffer, offset, n)
            return n
        }
        override fun getSize(): Long = data.size.toLong()
        override fun close() = Unit
    }

    fun decode(input: ByteArray, cancelled: () -> Boolean = { false }): Pcm? {
        // Coolray 2025: the MP4 reader fails on M4A replies; read the same AAC frames as ADTS instead.
        val data = if (Mp4ToAdts.isMp4(input)) Mp4ToAdts.convert(input) ?: input else input
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(Bytes(data))
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            if (rate !in 8000..96000 || channels !in 1..2) return null
            val out = java.io.ByteArrayOutputStream()
            val deadline = android.os.SystemClock.elapsedRealtime() + 4000
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idle = 0
            while (!outputDone && idle < 200) {
                if (cancelled() || Thread.currentThread().isInterrupted || android.os.SystemClock.elapsedRealtime() >= deadline) return null
                if (!inputDone) {
                    val inIndex = c.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = c.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            c.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0); extractor.advance()
                        }
                    }
                }
                val outIndex = c.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex >= 0 -> {
                        idle = 0
                        val buf = c.getOutputBuffer(outIndex)!!
                        if (info.size > 0) {
                            if (info.size > 2 * 1024 * 1024 - out.size()) return null
                            val chunk = ByteArray(info.size)
                            buf.position(info.offset); buf.get(chunk, 0, info.size)
                            out.write(chunk)
                        }
                        c.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Coolray 2025 (MediaTek, 18/11/2026): the AAC decoder reports its output format
                        // after the first buffer. Like THTVoice, take the new format and keep decoding;
                        // only a real change of rate/channels after audio was written is refused.
                        if (c.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) && c.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) != 2) return null
                        val newRate = c.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val newChannels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (newRate !in 8000..96000 || newChannels !in 1..2) return null
                        rate = newRate
                        channels = newChannels
                    }
                    else -> idle++
                }
            }
            // No end-of-stream flag after a long idle: keep what was decoded (THTVoice), never an empty clip.
            if (!outputDone && out.size() == 0) return null
            val bytes = out.toByteArray()
            val whole = bytes.size - bytes.size % (2 * channels)
            if (whole <= 0) return null
            val shorts = ShortArray(whole / 2)
            java.nio.ByteBuffer.wrap(bytes, 0, whole).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            val mono = if (channels <= 1) shorts else ShortArray(shorts.size / channels) { i ->
                var s = 0; for (ch in 0 until channels) s += shorts[i * channels + ch]; (s / channels).toShort()
            }
            if (mono.isEmpty() || rate <= 0) null else Pcm(mono, rate)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; runCatching { extractor.release() }
        }
    }
}
