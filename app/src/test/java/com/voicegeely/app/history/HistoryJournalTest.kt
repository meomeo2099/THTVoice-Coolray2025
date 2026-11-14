package com.voicegeely.app.history

import org.junit.Assert.*
import org.junit.Test

class HistoryJournalTest {
    @Test fun browseRetainsMoreThanHomeWithoutChangingHomeContracts() {
        val journal = HistoryJournal(Memory(), { it(); true })
        repeat(40) { val id = journal.begin("Lệnh $it"); journal.complete(id, "FAILED", "Lỗi"); journal.diagnostic("API $it") }
        var loaded: HistorySnapshot? = null
        journal.browse { loaded = it }
        assertEquals(40, loaded!!.commands.size)
        assertEquals(40, loaded!!.diagnostics.size)
        assertEquals(6, journal.commands().size)
        assertEquals(16, journal.diagnostics().size)
        assertEquals(40, HistoryReport.rows(loaded!!, HistoryFilter.PROBLEMS, "").size)
    }
    @Test fun completionSurvivesDisplayCacheEviction() {
        val db = Memory()
        val journal = HistoryJournal(db, { it(); true })
        journal.start()
        val first = journal.begin("Lệnh chậm")
        repeat(8) { val id = journal.begin("Lệnh $it"); journal.complete(id, "REJECTED", "Từ chối") }
        journal.complete(first, "ACCEPTED_UNVERIFIED", "Chưa xác nhận")
        assertEquals("ACCEPTED_UNVERIFIED", db.commands[first]?.outcome)
        assertEquals(6, journal.commands().size)
    }
    private class Memory : HistoryStorage {
        val commands = linkedMapOf<String, HistoryCommand>()
        val diagnostics = mutableListOf<HistoryDiagnostic>()
        override fun load(): HistorySnapshot {
            commands.replaceAll { _, v -> if (v.outcome == "PENDING") v.copy(outcome = "INTERRUPTED", result = "Phiên trước đã dừng; chưa xác nhận kết quả.") else v }
            return HistorySnapshot(commands.values.toList(), diagnostics.toList())
        }
        override fun saveCommand(command: HistoryCommand) { commands[command.id] = command }
        override fun completeCommand(id: String, outcome: String, result: String) {
            commands[id]?.let { commands[id] = it.copy(outcome = outcome, result = result) }
        }
        override fun saveDiagnostic(diagnostic: HistoryDiagnostic) { diagnostics += diagnostic }
    }
    @Test fun repeatedCommandsHaveIndependentOutcomes() {
        val db = Memory()
        val journal = HistoryJournal(db, { it(); true })
        journal.start()
        val a = journal.begin("Hạ kính lái")
        journal.complete(a, "ACCEPTED_UNVERIFIED", "Chưa xác nhận xe thực hiện.")
        val b = journal.begin("Hạ kính lái")
        journal.complete(b, "REJECTED", "API từ chối")
        assertNotEquals(a, b)
        assertEquals(2, db.commands.size)
        assertEquals(setOf("ACCEPTED_UNVERIFIED", "REJECTED"), db.commands.values.map { it.outcome }.toSet())
    }
    @Test fun startupMergeKeepsCommandsArrivingWhileDiskLoads() {
        val db = Memory()
        db.commands["old"] = HistoryCommand("old", "Lệnh cũ", "Đang xử lý...", "PENDING", 1)
        val queue = mutableListOf<() -> Unit>()
        val journal = HistoryJournal(db, { queue += it; true }, { 20L })
        journal.start()
        val id = journal.begin("Lệnh mới")
        journal.complete(id, "CONFIRMED", "Đã xác minh")
        while (queue.isNotEmpty()) queue.removeAt(0)()
        assertEquals("CONFIRMED", journal.commands().first { it.id == id }.outcome)
        assertEquals("INTERRUPTED", journal.commands().first { it.id == "old" }.outcome)
        val restarted = HistoryJournal(db, { it(); true })
        restarted.start()
        assertEquals(2, restarted.commands().size)
    }
    @Test fun unavailableQueueKeepsRamUsableAndReportsFailure() {
        val journal = HistoryJournal(Memory(), { false })
        journal.start()
        val id = journal.begin("Bật điều hòa")
        journal.complete(id, "FAILED", "API lỗi")
        assertEquals("FAILED", journal.commands().single().outcome)
        assertEquals("QUEUE_FULL", journal.storageStatus)
    }
    @Test fun diskFailureDoesNotEscapeAndOnlyBoundedCachesRemain() {
        val db = object : HistoryStorage {
            override fun load(): HistorySnapshot = error("disk")
            override fun saveCommand(command: HistoryCommand) { error("disk") }
            override fun completeCommand(id: String, outcome: String, result: String) { error("disk") }
            override fun saveDiagnostic(diagnostic: HistoryDiagnostic) { error("disk") }
        }
        val journal = HistoryJournal(db, { it(); true })
        journal.start()
        repeat(40) { journal.begin("Lệnh $it"); journal.diagnostic("ECARX lỗi $it") }
        assertEquals(6, journal.commands().size)
        assertEquals(16, journal.diagnostics().size)
        assertEquals("ERROR:IllegalStateException", journal.storageStatus)
    }
}
