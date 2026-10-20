package com.voicegeely.app.voice

import android.content.Context

/**
 * Process-local application context for read-only Android capability probes.
 * Stores only applicationContext, never an Activity/Service instance.
 */
object AndroidRuntimeContext {
    @Volatile private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun getOrNull(): Context? = appContext
}
