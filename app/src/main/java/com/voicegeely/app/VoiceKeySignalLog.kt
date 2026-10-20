package com.voicegeely.app

/**
 * Last voice-key signals seen by the app (in memory only, cleared on restart), shown in
 * Settings → "Kiểm tra nút voice" so the owner can copy them without a computer/ADB.
 * Used to learn which raw values / broadcast extras mean a short press or a long hold.
 */
object VoiceKeySignalLog {
    const val MAX = 40

    data class Entry(val atMs: Long, val source: String, val detail: String)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(atMs: Long, source: String, detail: String) {
        entries.addLast(Entry(atMs, source, detail.take(300)))
        while (entries.size > MAX) entries.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    /** Lines like "+0 ms  vhal raw=1" (time relative to the first entry). */
    fun format(list: List<Entry> = snapshot()): String {
        if (list.isEmpty()) return "Chưa ghi nhận tín hiệu nút voice nào từ khi mở app. Bấm nút voice trên vô lăng vài lần (bấm nhanh và giữ lâu), rồi bấm Kiểm tra lại."
        val t0 = list.first().atMs
        return list.joinToString("\n") { "+${it.atMs - t0} ms  ${it.source}  ${it.detail}" }
    }
}
