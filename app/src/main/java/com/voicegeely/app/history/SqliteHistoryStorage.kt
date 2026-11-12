package com.voicegeely.app.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** All calls owned by the single background history worker. */
internal class SqliteHistoryStorage(context: Context) : SQLiteOpenHelper(context.applicationContext, "tht_voice_history.db", null, 1), HistoryStorage {
    override fun onCreate(db: SQLiteDatabase) {
        listOf(HistorySql.COMMANDS, HistorySql.DIAGNOSTICS, HistorySql.COMMAND_INDEX, HistorySql.DIAGNOSTIC_INDEX).forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Fail closed rather than silently erase a diagnostic database on an unknown migration.
        throw IllegalStateException("Unsupported history schema $oldVersion -> $newVersion")
    }

    override fun load(): HistorySnapshot {
        val db = writableDatabase
        db.execSQL(HistorySql.INTERRUPT)
        val commands = mutableListOf<HistoryCommand>()
        db.rawQuery(HistorySql.RECENT_COMMANDS, null).use { c ->
            while (c.moveToNext()) commands += HistoryCommand(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))
        }
        val diagnostics = mutableListOf<HistoryDiagnostic>()
        db.rawQuery(HistorySql.RECENT_DIAGNOSTICS, null).use { c ->
            while (c.moveToNext()) diagnostics += HistoryDiagnostic(c.getString(0), c.getString(1), c.getLong(2))
        }
        return HistorySnapshot(commands, diagnostics)
    }

    override fun saveCommand(command: HistoryCommand) = transaction { db ->
        db.execSQL(HistorySql.SAVE_COMMAND, arrayOf(command.id, command.command, command.result, command.outcome, command.timestampMs))
        db.execSQL(HistorySql.TRIM_COMMANDS)
    }

    override fun saveDiagnostic(diagnostic: HistoryDiagnostic) = transaction { db ->
        db.execSQL(HistorySql.SAVE_DIAGNOSTIC, arrayOf(diagnostic.id, diagnostic.message, diagnostic.timestampMs))
        db.execSQL(HistorySql.TRIM_DIAGNOSTICS)
    }

    override fun completeCommand(id: String, outcome: String, result: String) {
        writableDatabase.execSQL(HistorySql.COMPLETE_COMMAND, arrayOf(outcome, result, id))
    }

    private fun transaction(write: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try { write(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
}
