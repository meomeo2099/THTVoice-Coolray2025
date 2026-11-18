package com.voicegeely.app.history

/** Shared with scripts/test-history-database.py: exercise the exact production SQL. */
internal object HistorySql {
    const val COMMANDS = """CREATE TABLE command_history (id TEXT PRIMARY KEY NOT NULL, command TEXT NOT NULL, result TEXT NOT NULL, outcome TEXT NOT NULL, timestamp_ms INTEGER NOT NULL)"""
    const val DIAGNOSTICS = """CREATE TABLE api_diagnostics (id TEXT PRIMARY KEY NOT NULL, message TEXT NOT NULL, timestamp_ms INTEGER NOT NULL)"""
    const val COMMAND_INDEX = """CREATE INDEX command_history_time ON command_history(timestamp_ms DESC)"""
    const val DIAGNOSTIC_INDEX = """CREATE INDEX api_diagnostics_time ON api_diagnostics(timestamp_ms DESC)"""
    const val INTERRUPT = """UPDATE command_history SET outcome='INTERRUPTED', result='Phiên trước đã dừng; chưa xác nhận kết quả.' WHERE outcome='PENDING'"""
    const val SAVE_COMMAND = """INSERT OR REPLACE INTO command_history(id,command,result,outcome,timestamp_ms) VALUES(?,?,?,?,?)"""
    const val COMPLETE_COMMAND = """UPDATE command_history SET outcome=?,result=? WHERE id=?"""
    const val SAVE_DIAGNOSTIC = """INSERT INTO api_diagnostics(id,message,timestamp_ms) VALUES(?,?,?)"""
    const val RECENT_COMMANDS = """SELECT id,command,result,outcome,timestamp_ms FROM command_history ORDER BY timestamp_ms DESC,rowid DESC LIMIT 6"""
    const val RECENT_DIAGNOSTICS = """SELECT id,message,timestamp_ms FROM api_diagnostics ORDER BY timestamp_ms DESC,rowid DESC LIMIT 16"""
    const val BROWSE_COMMANDS = """SELECT id,command,result,outcome,timestamp_ms FROM command_history ORDER BY timestamp_ms DESC,rowid DESC LIMIT 1000"""
    const val BROWSE_DIAGNOSTICS = """SELECT id,message,timestamp_ms FROM api_diagnostics ORDER BY timestamp_ms DESC,rowid DESC LIMIT 2000"""
    const val TRIM_COMMANDS = """DELETE FROM command_history WHERE id NOT IN (SELECT id FROM command_history ORDER BY timestamp_ms DESC,rowid DESC LIMIT 1000)"""
    const val TRIM_DIAGNOSTICS = """DELETE FROM api_diagnostics WHERE id NOT IN (SELECT id FROM api_diagnostics ORDER BY timestamp_ms DESC,rowid DESC LIMIT 2000)"""
}
