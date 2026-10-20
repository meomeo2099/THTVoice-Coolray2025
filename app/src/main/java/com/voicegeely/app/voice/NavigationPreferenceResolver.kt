package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultNavigationApp
import com.voicegeely.app.settings.UserVoicePreferences

/** Resolves AUTO navigation commands from the user's saved default map app. */
class NavigationPreferenceResolver(
    private val defaultProvider: () -> DefaultNavigationApp
) {
    constructor(preferences: UserVoicePreferences) : this({ preferences.defaultNavigationApp })

    fun providerOrder(requested: NavigationProvider): List<NavigationProvider> {
        if (requested != NavigationProvider.AUTO) return listOf(requested)

        val preferred = when (defaultProvider()) {
            DefaultNavigationApp.AUTO -> null
            DefaultNavigationApp.GOOGLE_MAPS -> NavigationProvider.GOOGLE_MAPS
            DefaultNavigationApp.VIETMAP -> NavigationProvider.VIETMAP
            DefaultNavigationApp.WAZE -> NavigationProvider.WAZE
        }

        val automaticFallback = listOf(
            NavigationProvider.GOOGLE_MAPS,
            NavigationProvider.WAZE,
            NavigationProvider.VIETMAP
        )
        return if (preferred == null) automaticFallback
        else listOf(preferred) + automaticFallback.filterNot { it == preferred }
    }
}
