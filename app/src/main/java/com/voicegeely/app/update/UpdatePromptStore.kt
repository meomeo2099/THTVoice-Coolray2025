package com.voicegeely.app.update

import android.content.Context

/** Remembers the last automatic check and a required (forced) version found by it. */
class UpdatePromptStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tht_update_prompt", Context.MODE_PRIVATE)

    var lastAutoCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    var requiredVersionCode: Long
        get() = prefs.getLong(KEY_REQUIRED_CODE, 0L)
        set(value) = prefs.edit().putLong(KEY_REQUIRED_CODE, value).apply()

    var requiredVersionName: String
        get() = prefs.getString(KEY_REQUIRED_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_REQUIRED_NAME, value).apply()

    private companion object {
        const val KEY_LAST_CHECK = "last_auto_check_at"
        const val KEY_REQUIRED_CODE = "required_version_code"
        const val KEY_REQUIRED_NAME = "required_version_name"
    }
}
