// Wrong-code lockout. Counts consecutive failures per key; a success, an admin unlock or a
// one-time unlock code resets it, and the count also resets after 24 h without failures.
//   device:  5 → 15 min, 10 → 1 h, 15+ → 24 h
//   IP:      the same tiers ×4 (many cars can share a mobile-carrier IP)
// VIP codes do NOT bypass this: the lock is checked before the code is looked up.

export const MINUTE = 60 * 1000;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

export const TIERS = [
  { fails: 15, lockMs: DAY },
  { fails: 10, lockMs: HOUR },
  { fails: 5, lockMs: 15 * MINUTE },
];

export function lockDurationFor(failCount, scale = 1) {
  for (const tier of TIERS) if (failCount >= tier.fails * scale) return tier.lockMs;
  return 0;
}

export function scaleFor(key) {
  return key.startsWith('ip:') ? 4 : 1;
}

/** Remaining lock in ms for the first locked key, or 0. */
export async function lockedFor(db, keys, now) {
  let longest = 0;
  for (const key of keys) {
    const row = await db.prepare('SELECT locked_until FROM lockouts WHERE key = ?').bind(key).first();
    if (row && row.locked_until > now) longest = Math.max(longest, row.locked_until - now);
  }
  return longest;
}

/** Records one failure for every key; returns the longest lock and the fewest tries left. */
export async function recordFailure(db, keys, now) {
  let lockedMs = 0;
  let failsBeforeLock = Infinity;
  for (const key of keys) {
    const row = await db.prepare('SELECT fail_count, last_fail_at FROM lockouts WHERE key = ?').bind(key).first();
    const previous = row && now - row.last_fail_at < DAY ? row.fail_count : 0;
    const count = previous + 1;
    const scale = scaleFor(key);
    const lockMs = lockDurationFor(count, scale);
    await db.prepare(
      'INSERT INTO lockouts (key, fail_count, locked_until, last_fail_at) VALUES (?, ?, ?, ?) ' +
      'ON CONFLICT(key) DO UPDATE SET fail_count = excluded.fail_count, locked_until = excluded.locked_until, last_fail_at = excluded.last_fail_at'
    ).bind(key, count, lockMs ? now + lockMs : 0, now).run();
    lockedMs = Math.max(lockedMs, lockMs);
    const nextTier = [...TIERS].reverse().find((t) => t.fails * scale > count);
    failsBeforeLock = Math.min(failsBeforeLock, lockMs ? 0 : (nextTier ? nextTier.fails * scale - count : 0));
  }
  return { lockedMs, failsBeforeLock: lockedMs ? 0 : (Number.isFinite(failsBeforeLock) ? failsBeforeLock : 0) };
}

export async function clearKeys(db, keys) {
  for (const key of keys) await db.prepare('DELETE FROM lockouts WHERE key = ?').bind(key).run();
}

export function describeWait(ms) {
  const minutes = Math.max(1, Math.ceil(ms / MINUTE));
  if (minutes < 60) return `${minutes} phút`;
  const hours = Math.ceil(minutes / 60);
  return `${hours} giờ`;
}
