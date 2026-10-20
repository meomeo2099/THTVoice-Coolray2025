package com.voicegeely.app.voice

/**
 * When the speech models may start loading (staged startup, 02/10/2026). Right after the head
 * unit boots, the launcher, maps and car services start too; loading ~400 MB of models at that
 * moment can stall the screen. So the load waits until the unit has been up for
 * [SETTLED_UPTIME_MS], at most [MAX_DELAY_MS]; later starts (app opened by hand) load at once.
 */
object StartupPolicy {
    const val SETTLED_UPTIME_MS = 45_000L
    const val MAX_DELAY_MS = 20_000L

    fun loadDelayMs(uptimeMs: Long): Long =
        (SETTLED_UPTIME_MS - uptimeMs).coerceIn(0L, MAX_DELAY_MS)
}
