package com.voicegeely.app.kws

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Trained voices ("Dạy từ gọi"): up to [MAX] people, each with the keyword lines learned for one
 * wake profile, kept locally (never uploaded). Lines are added to the spotter on top of the
 * built-in file; "Quên" removes them.
 */
class WakeVoices(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tht_wake_voices", Context.MODE_PRIVATE)

    data class Voice(val id: String, val name: String, val profile: WakePhrase, val lines: List<String>,
                     val createdMs: Long, val music: Boolean)

    fun all(): List<Voice> = runCatching {
        val raw = prefs.getString(KEY, "[]").orEmpty()
        require(raw.length <= 65_536)
        val arr = JSONArray(raw)
        (0 until minOf(arr.length(), MAX)).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val profile = runCatching { WakePhrase.valueOf(o.getString("profile")) }.getOrNull() ?: return@mapNotNull null
            val lines = o.getJSONArray("lines").let { a -> (0 until minOf(a.length(), WakeEnrollment.MAX_LINES)).map { a.getString(it).take(512) } }
            Voice(o.getString("id"), o.optString("name"), profile, lines, o.optLong("created"), o.optBoolean("music"))
        }
    }.getOrDefault(emptyList())

    fun forProfile(profile: WakePhrase): List<Voice> = all().filter { it.profile == profile }

    /** Extra keyword lines for [profile], for the spotter stream. */
    fun lines(profile: WakePhrase): List<KeywordLines.Line> =
        KeywordLines.forProfile(forProfile(profile).flatMap { v -> v.lines.mapNotNull { KeywordLines.parse(it) } }, profile)

    fun save(voice: Voice): Boolean = synchronized(lock) {
        val list = all().filterNot { it.id == voice.id }.toMutableList()
        if (list.size >= MAX) return@synchronized false
        val lines = KeywordLines.forProfile(voice.lines.mapNotNull { KeywordLines.parse(it) }, voice.profile)
        if (lines.isEmpty()) return@synchronized false
        list += voice.copy(name = voice.name.take(48), lines = lines.take(WakeEnrollment.MAX_LINES).map { it.format() })
        write(list)
        true
    }

    fun forget(id: String) = synchronized(lock) { write(all().filterNot { it.id == id }) }
    fun forgetAll() = synchronized(lock) { write(emptyList()) }

    private fun write(list: List<Voice>) {
        val arr = JSONArray()
        list.forEach { v ->
            arr.put(JSONObject().put("id", v.id).put("name", v.name).put("profile", v.profile.name)
                .put("lines", JSONArray(v.lines)).put("created", v.createdMs).put("music", v.music))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private val lock = Any()
        const val MAX = 4
        private const val KEY = "voices"
    }
}
