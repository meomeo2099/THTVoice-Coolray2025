package com.voicegeely.app.security

import android.content.Context
import org.json.JSONObject

/** Last notice from the server and the id the customer already dismissed ("Đã xem"). */
class CustomerNoticeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("customer_notice", Context.MODE_PRIVATE)

    /** A license reply without "notice" clears it (the admin turned it off or it expired). */
    fun saveFromReply(reply: JSONObject) {
        val n = reply.optJSONObject("notice")
        val notice = n?.let { CustomerNoticePolicy.of(it.optString("id"), it.optString("text"), it.optLong("until", 0L)) }
        val edit = prefs.edit()
        if (notice == null) {
            edit.remove(KEY_ID).remove(KEY_TEXT).remove(KEY_UNTIL)
        } else {
            edit.putString(KEY_ID, notice.id).putString(KEY_TEXT, notice.text).putLong(KEY_UNTIL, notice.until)
        }
        edit.apply()
    }

    fun load(): CustomerNotice? =
        CustomerNoticePolicy.of(prefs.getString(KEY_ID, null), prefs.getString(KEY_TEXT, null), prefs.getLong(KEY_UNTIL, 0L))

    val seenId: String? get() = prefs.getString(KEY_SEEN, null)

    fun markSeen(id: String) {
        prefs.edit().putString(KEY_SEEN, id).apply()
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_TEXT = "text"
        const val KEY_UNTIL = "until"
        const val KEY_SEEN = "seen_id"
    }
}
