package com.voicegeely.app.tts

/**
 * Coolray 2025 (car logs 18–19/11/2026): the head unit's MP4 reader stops an M4A reply after 3 AAC
 * frames (MediaPlayer reports "complete" after ~130 ms; MediaExtractor + MediaCodec fail too), while
 * MP3 played fine. The AAC frames inside are normal, so they are re-wrapped as an ADTS stream: the
 * platform then uses its plain AAC reader instead of the MP4 one. No re-encoding, same sound.
 *
 * Pure Kotlin (unit tested). Returns null for anything that is not a single-track AAC-LC/HE M4A.
 */
object Mp4ToAdts {
    private const val MAX_FRAMES = 20_000

    fun isMp4(data: ByteArray): Boolean =
        data.size >= 12 && data[4] == 'f'.code.toByte() && data[5] == 't'.code.toByte() &&
            data[6] == 'y'.code.toByte() && data[7] == 'p'.code.toByte()

    fun convert(data: ByteArray): ByteArray? = runCatching { convertOrThrow(data) }.getOrNull()

    private class Box(val type: String, val start: Int, val end: Int) {
        val body get() = start + 8
    }

    private fun u32(d: ByteArray, i: Int): Long =
        ((d[i].toLong() and 0xFF) shl 24) or ((d[i + 1].toLong() and 0xFF) shl 16) or
            ((d[i + 2].toLong() and 0xFF) shl 8) or (d[i + 3].toLong() and 0xFF)

    private fun u64(d: ByteArray, i: Int): Long = (u32(d, i) shl 32) or u32(d, i + 4)

    private fun children(d: ByteArray, from: Int, to: Int): List<Box> {
        val out = mutableListOf<Box>()
        var i = from
        while (i + 8 <= to) {
            var size = u32(d, i)
            val type = String(d, i + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) { size = u64(d, i + 8); header = 16 }
            if (size == 0L) size = (to - i).toLong()
            require(size >= header && i + size <= to)
            out += Box(type, i, (i + size).toInt()).also { require(header == 8) }
            i += size.toInt()
        }
        return out
    }

    private fun find(d: ByteArray, parent: Box, vararg path: String): Box? {
        var cur = parent
        for (name in path) cur = children(d, cur.body, cur.end).firstOrNull { it.type == name } ?: return null
        return cur
    }

    private fun convertOrThrow(d: ByteArray): ByteArray? {
        if (!isMp4(d)) return null
        val top = children(d, 0, d.size)
        val moov = top.firstOrNull { it.type == "moov" } ?: return null
        val traks = children(d, moov.body, moov.end).filter { it.type == "trak" }
        for (trak in traks) {
            val stbl = find(d, trak, "mdia", "minf", "stbl") ?: continue
            val boxes = children(d, stbl.body, stbl.end).associateBy { it.type }
            val stsd = boxes["stsd"] ?: continue
            // stsd: version/flags(4) + entry count(4), then the mp4a sample entry.
            val entry = children(d, stsd.body + 8, stsd.end).firstOrNull { it.type == "mp4a" } ?: continue
            // mp4a: 6 reserved + 2 index + 8 reserved + 2 ch + 2 bits + 4 + 4 rate = 28 bytes, then esds.
            val esds = children(d, entry.body + 28, entry.end).firstOrNull { it.type == "esds" } ?: continue
            val asc = decoderSpecificInfo(d, esds.body + 4, esds.end) ?: return null
            val objectType = (asc[0].toInt() and 0xFF) shr 3
            val freqIndex = ((asc[0].toInt() and 0x07) shl 1) or ((asc[1].toInt() and 0xFF) shr 7)
            val channels = ((asc[1].toInt() and 0xFF) shr 3) and 0x0F
            // ADTS profile carries AAC Main/LC/SSR/LTP only; HE-AAC is signalled implicitly as LC.
            val profile = when (objectType) { 1, 2, 3, 4 -> objectType; 5, 29 -> 2; else -> return null }
            if (freqIndex > 12 || channels !in 1..2) return null

            val sizes = sampleSizes(d, boxes["stsz"] ?: return null)
            val offsets = sampleOffsets(d, boxes, sizes.size) ?: return null
            val out = java.io.ByteArrayOutputStream(d.size + sizes.size * 7)
            for (k in sizes.indices) {
                val size = sizes[k]
                val off = offsets[k]
                require(off >= 0 && off + size <= d.size && size in 1..(8191 - 7))
                val len = size + 7
                out.write(0xFF)
                out.write(0xF1) // MPEG-4, layer 0, no CRC
                out.write(((profile - 1) shl 6) or (freqIndex shl 2) or (channels shr 2))
                out.write(((channels and 3) shl 6) or (len shr 11))
                out.write((len shr 3) and 0xFF)
                out.write(((len and 7) shl 5) or 0x1F)
                out.write(0xFC)
                out.write(d, off, size)
            }
            return out.toByteArray()
        }
        return null
    }

    /** DecoderSpecificInfo (tag 5) inside the ES descriptor. */
    private fun decoderSpecificInfo(d: ByteArray, from: Int, to: Int): ByteArray? {
        var i = from
        fun readLen(): Int {
            var len = 0
            repeat(4) {
                val b = d[i++].toInt() and 0xFF
                len = (len shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) return len
            }
            return len
        }
        while (i < to) {
            val tag = d[i++].toInt() and 0xFF
            val len = readLen()
            when (tag) {
                0x03 -> { // ES_Descriptor: ES_ID(2) + flags(1) [+ optional fields]
                    val flags = d[i + 2].toInt() and 0xFF
                    i += 3
                    if (flags and 0x80 != 0) i += 2
                    if (flags and 0x40 != 0) i += 1 + (d[i].toInt() and 0xFF)
                    if (flags and 0x20 != 0) i += 2
                }
                0x04 -> i += 13 // DecoderConfigDescriptor fixed part, then its sub-descriptors
                0x05 -> return if (len >= 2 && i + len <= to) d.copyOfRange(i, i + len) else null
                else -> i += len
            }
        }
        return null
    }

    private fun sampleSizes(d: ByteArray, stsz: Box): IntArray {
        val fixed = u32(d, stsz.body + 4).toInt()
        val count = u32(d, stsz.body + 8).toInt()
        require(count in 1..MAX_FRAMES)
        return IntArray(count) { if (fixed != 0) fixed else u32(d, stsz.body + 12 + it * 4).toInt() }
    }

    private fun sampleOffsets(d: ByteArray, boxes: Map<String, Box>, samples: Int): LongArrayCompat? {
        val stsc = boxes["stsc"] ?: return null
        val chunkOffsets: LongArray = boxes["stco"]?.let { b ->
            val n = u32(d, b.body + 4).toInt(); require(n in 1..MAX_FRAMES)
            LongArray(n) { u32(d, b.body + 8 + it * 4) }
        } ?: boxes["co64"]?.let { b ->
            val n = u32(d, b.body + 4).toInt(); require(n in 1..MAX_FRAMES)
            LongArray(n) { u64(d, b.body + 8 + it * 8) }
        } ?: return null
        val sizes = sampleSizes(d, boxes.getValue("stsz"))
        val entries = u32(d, stsc.body + 4).toInt()
        require(entries in 1..MAX_FRAMES)
        val firstChunk = IntArray(entries) { u32(d, stsc.body + 8 + it * 12).toInt() }
        val perChunk = IntArray(entries) { u32(d, stsc.body + 12 + it * 12).toInt() }
        val out = IntArray(samples)
        var sample = 0
        for (chunk in chunkOffsets.indices) {
            val chunkNumber = chunk + 1
            var e = entries - 1
            while (e > 0 && firstChunk[e] > chunkNumber) e--
            var pos = chunkOffsets[chunk]
            repeat(perChunk[e]) {
                if (sample >= samples) return LongArrayCompat(out)
                require(pos <= Int.MAX_VALUE)
                out[sample] = pos.toInt()
                pos += sizes[sample]
                sample++
            }
        }
        return if (sample == samples) LongArrayCompat(out) else null
    }

    /** Sample file offsets (Int: replies are a few hundred KB at most). */
    private class LongArrayCompat(val values: IntArray) {
        operator fun get(i: Int) = values[i]
    }
}
