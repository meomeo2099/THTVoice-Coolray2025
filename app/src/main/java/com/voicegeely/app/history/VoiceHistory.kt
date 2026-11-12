package com.voicegeely.app.history

import android.content.Context
import com.voicegeely.app.car.VehicleTelemetryRegistry
import com.voicegeely.app.voice.CommandExecutionResult
import com.voicegeely.app.voice.CommandHistoryObserver
import com.voicegeely.app.voice.ParsedVoiceCommand
import com.voicegeely.app.voice.VoiceResponseFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Process-wide storage, initialized before the service/activity. SQLite is never opened on main. */
object VoiceHistory : CommandHistoryObserver {
    @Volatile var journal: HistoryJournal? = null
        private set

    @Synchronized fun install(context: Context) {
        if (journal != null) return
        val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(128),
            { work -> Thread(work, "THTVoice-History").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
        val history = HistoryJournal(SqliteHistoryStorage(context), enqueue = { work ->
            runCatching { worker.execute { work() } }.isSuccess
        })
        journal = history
        history.start()
        VehicleTelemetryRegistry.diagnosticSink = { history.diagnostic(it) }
        VehicleTelemetryRegistry.archivedDiagnostics = {
            history.diagnostics().reversed().map {
                val stamp = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date(it.timestampMs))
                "[$stamp] ${it.message}"
            }
        }
    }

    override fun begin(parsed: ParsedVoiceCommand): String = journal?.begin(parsed.normalizedText).orEmpty()

    override fun complete(id: String, parsed: ParsedVoiceCommand, result: CommandExecutionResult) {
        val outcome = when (result) {
            is CommandExecutionResult.Confirmed -> "CONFIRMED"
            is CommandExecutionResult.AcceptedUnverified -> "ACCEPTED_UNVERIFIED"
            is CommandExecutionResult.Rejected -> "REJECTED"
            is CommandExecutionResult.Failed -> "FAILED"
        }
        journal?.complete(id, outcome, VoiceResponseFormatter.result(parsed.command, result))
    }
}
