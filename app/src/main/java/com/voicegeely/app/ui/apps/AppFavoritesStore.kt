package com.voicegeely.app.ui.apps

import android.content.Context

class AppFavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()

    fun toggle(packageName: String): Boolean {
        val items = all().toMutableSet()
        val favorite = if (packageName in items) {
            items.remove(packageName)
            false
        } else {
            items.add(packageName)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, items).apply()
        return favorite
    }

    companion object {
        private const val PREFS_NAME = "tht_voice_app_launcher"
        private const val KEY_FAVORITES = "favorites"
    }
}
