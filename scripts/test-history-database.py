"""Exercise the literal production SQLite schema/queries across a real close/reopen."""
from pathlib import Path
import re
import sqlite3
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SQL = dict(re.findall(r'const val (\w+) = """(.*?)"""', (ROOT / 'app/src/main/java/com/voicegeely/app/history/HistorySql.kt').read_text(encoding='utf-8')))


class HistoryDatabaseTest(unittest.TestCase):
    def test_restart_outcomes_repeated_commands_and_retention(self):
        (ROOT / 'build').mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / 'build') as directory:
            path = Path(directory) / 'history.db'
            with sqlite3.connect(path) as db:
                for name in ['COMMANDS', 'DIAGNOSTICS', 'COMMAND_INDEX', 'DIAGNOSTIC_INDEX']:
                    db.execute(SQL[name])
                db.execute(SQL['SAVE_COMMAND'], ('a', 'same command', 'waiting', 'PENDING', 1))
                db.execute(SQL['SAVE_COMMAND'], ('b', 'same command', 'not verified', 'ACCEPTED_UNVERIFIED', 2))
                db.execute(SQL['SAVE_COMMAND'], ('c', 'other command', 'waiting', 'PENDING', 3))
                db.execute(SQL['COMPLETE_COMMAND'], ('CONFIRMED', 'verified', 'c'))
                db.execute(SQL['SAVE_DIAGNOSTIC'], ('d', 'ECARX rejected', 4))
            # Connection context commits; explicit close ensures the next connection has no memory cache.
            db.close()
            with sqlite3.connect(path) as db:
                db.execute(SQL['INTERRUPT'])
                rows = db.execute(SQL['RECENT_COMMANDS']).fetchall()
                self.assertEqual(['CONFIRMED', 'ACCEPTED_UNVERIFIED', 'INTERRUPTED'], [r[3] for r in rows])
                self.assertEqual(2, sum(r[1] == 'same command' for r in rows))
                self.assertEqual('ECARX rejected', db.execute(SQL['RECENT_DIAGNOSTICS']).fetchone()[1])
                for i in range(2100):
                    db.execute(SQL['SAVE_COMMAND'], (str(i), 'command', 'rejected', 'REJECTED', 10 + i))
                    db.execute(SQL['SAVE_DIAGNOSTIC'], (str(i), 'API', 10 + i))
                db.execute(SQL['TRIM_COMMANDS'])
                db.execute(SQL['TRIM_DIAGNOSTICS'])
                self.assertEqual(1000, db.execute('SELECT COUNT(*) FROM command_history').fetchone()[0])
                self.assertEqual(2000, db.execute('SELECT COUNT(*) FROM api_diagnostics').fetchone()[0])
                self.assertEqual('2099', db.execute(SQL['RECENT_COMMANDS']).fetchone()[0])
                self.assertEqual(16, len(db.execute(SQL['RECENT_DIAGNOSTICS']).fetchall()))
                self.assertEqual(6, len(db.execute(SQL['RECENT_COMMANDS']).fetchall()))
                self.assertEqual(1000, len(db.execute(SQL['BROWSE_COMMANDS']).fetchall()))
                self.assertEqual(2000, len(db.execute(SQL['BROWSE_DIAGNOSTICS']).fetchall()))
                # Browsing retained rows never reruns the startup PENDING -> INTERRUPTED update.
                db.execute(SQL['SAVE_COMMAND'], ('live', 'in progress', 'waiting', 'PENDING', 9999))
                self.assertEqual('PENDING', db.execute(SQL['BROWSE_COMMANDS']).fetchone()[3])
                self.assertEqual('PENDING', db.execute(SQL['RECENT_COMMANDS']).fetchone()[3])
            db.close()


if __name__ == '__main__':
    unittest.main()
