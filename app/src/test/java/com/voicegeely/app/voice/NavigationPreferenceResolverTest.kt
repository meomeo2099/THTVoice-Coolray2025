package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultNavigationApp
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPreferenceResolverTest {
    @Test fun savedVietmapDefaultLeadsAutoNavigation() {
        val resolver = NavigationPreferenceResolver { DefaultNavigationApp.VIETMAP }
        assertEquals(
            listOf(NavigationProvider.VIETMAP, NavigationProvider.GOOGLE_MAPS, NavigationProvider.WAZE),
            resolver.providerOrder(NavigationProvider.AUTO)
        )
    }

    @Test fun automaticModeKeepsStableFallbackOrder() {
        val resolver = NavigationPreferenceResolver { DefaultNavigationApp.AUTO }
        assertEquals(
            listOf(NavigationProvider.GOOGLE_MAPS, NavigationProvider.WAZE, NavigationProvider.VIETMAP),
            resolver.providerOrder(NavigationProvider.AUTO)
        )
    }

    @Test fun explicitlySpokenProviderOverridesSavedDefault() {
        val resolver = NavigationPreferenceResolver { DefaultNavigationApp.GOOGLE_MAPS }
        assertEquals(
            listOf(NavigationProvider.WAZE),
            resolver.providerOrder(NavigationProvider.WAZE)
        )
    }
}
