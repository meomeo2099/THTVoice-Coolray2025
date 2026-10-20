package com.voicegeely.app.security

/**
 * What the app tells the license server about the VIN on each check, so the admin page can show
 * how reliably a car's VIN is read before VIN-locked licenses are switched on:
 * "ok" with the VIN, or "none:<reason>" when it could not be read.
 */
object VinReportPolicy {
    fun report(result: VinReadResult): Pair<String?, String> = when (result) {
        is VinReadResult.Available -> result.vin to "ok"
        is VinReadResult.Unavailable -> null to "none:${result.reason.name}"
    }
}
