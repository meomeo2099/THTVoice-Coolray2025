package com.voicegeely.app.history

import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

data class HistoryCommand(val id: String, val command: String, val result: String, val outcome: String, val timestampMs: Long)
data class HistoryDiagnostic(val id: String, val message: String, val timestampMs: Long)
data class HistorySnapshot(val commands: List<HistoryCommand>, val diagnostics: List<HistoryDiagnostic>)

interface HistoryStorage {
    fun load(): HistorySnapshot
    fun saveCommand(command: HistoryCommand)
    fun completeCommand(id: String, outcome: String, result: String)
    fun saveDiagnostic(diagnostic: HistoryDiagnostic)
}

/** Display/audit history only. No vehicle state, command replay, or actuator reads. */
class HistoryJournal(
    private val storage: HistoryStorage,
    private val enqueue: (() -> Unit) -> Boolean,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private var recentCommands = emptyList<HistoryCommand>()
    private var recentDiagnostics = emptyList<HistoryDiagnostic>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    @Volatile var storageStatus = "LOADING"
        private set

    fun start() = submit {
        val loaded = storage.load()
        synchronized(this) {
            // Live entries win when startup I/O overlaps an execution.
            recentCommands = (recentCommands + loaded.commands).distinctBy { it.id }.sortedByDescending { it.timestampMs }.take(6)
            recentDiagnostics = (recentDiagnostics + loaded.diagnostics).distinctBy { it.id }.sortedByDescending { it.timestampMs }.take(16)
        }
        if (storageStatus == "LOADING") storageStatus = "READY"
        notifyChanged()
    }

    @Synchronized fun commands(): List<HistoryCommand> = recentCommands.toList()
    @Synchronized fun diagnostics(): List<HistoryDiagnostic> = recentDiagnostics.toList()
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    @Synchronized fun begin(command: String): String {
        val row = HistoryCommand(UUID.randomUUID().toString(), clean(command, 240), "Đang xử lý...", "PENDING", now())
        recentCommands = (listOf(row) + recentCommands).take(6)
        submit { storage.saveCommand(row) }
        notifyChanged()
        return row.id
    }

    @Synchronized fun complete(id: String, outcome: String, result: String) {
        val safeResult = clean(result, 600)
        recentCommands = recentCommands.map { if (it.id == id) it.copy(outcome = outcome, result = safeResult) else it }
        // Update by UUID even when the row has left the six-item display cache.
        submit { storage.completeCommand(id, outcome, safeResult) }
        notifyChanged()
    }

    @Synchronized fun diagnostic(message: String) {
        val row = HistoryDiagnostic(UUID.randomUUID().toString(), clean(message, 600), now())
        recentDiagnostics = (listOf(row) + recentDiagnostics).take(16)
        submit { storage.saveDiagnostic(row) }
        // Diagnostics are rendered on demand, not on each high-frequency API event.
    }

    private fun submit(work: () -> Unit) {
        val accepted = runCatching { enqueue {
            try { work() } catch (e: Exception) { storageStatus = "ERROR:${e.javaClass.simpleName}" }
        } }.getOrDefault(false)
        if (!accepted) storageStatus = "QUEUE_FULL"
    }

    private fun notifyChanged() { listeners.forEach { runCatching { it() } } }

    private fun clean(value: String, limit: Int): String = value.replace(Regex("[\\r\\n]+"), " ")
        .replace(Regex("(?i)\\b[A-HJ-NPR-Z0-9]{17}\\b"), "[VIN]").take(limit)
}
