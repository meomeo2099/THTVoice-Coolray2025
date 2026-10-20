// Test harness: a D1-compatible shim on node:sqlite plus a ready env with fresh keys.
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { route } from '../src/index.js';

class Stmt {
  constructor(db, sql, args = []) { this.db = db; this.sql = sql; this.args = args; }
  bind(...args) {
    for (const a of args) if (a === undefined) throw new Error(`undefined bound in: ${this.sql}`);
    return new Stmt(this.db, this.sql, args);
  }
  async first() { return this.db.prepare(this.sql).get(...this.args) ?? null; }
  async all() { return { results: this.db.prepare(this.sql).all(...this.args) }; }
  async run() {
    const r = this.db.prepare(this.sql).run(...this.args);
    return { meta: { changes: Number(r.changes), last_row_id: Number(r.lastInsertRowid) } };
  }
}

export function makeDb() {
  const db = new DatabaseSync(':memory:');
  db.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  return {
    raw: db,
    prepare: (sql) => new Stmt(db, sql),
    batch: async (stmts) => Promise.all(stmts.map((s) => s.run())),
  };
}

export async function makeEnv(extra = {}) {
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  const b64 = (buf) => Buffer.from(buf).toString('base64');
  return {
    DB: makeDb(),
    LICENSE_SIGNING_KEY: b64(await crypto.subtle.exportKey('pkcs8', pair.privateKey)),
    PUBLIC_KEY: b64(await crypto.subtle.exportKey('spki', pair.publicKey)),
    LICENSE_KEY_ID: 'k1',
    VIN_KEY: b64(crypto.getRandomValues(new Uint8Array(32))),
    ADMIN_PASSWORD: 'correct-horse-battery',
    ...extra,
  };
}

export const DEVICE = '0123456789ABCDEF';
export const OTHER = 'FEDCBA9876543210';

export function client(env, clock = { now: 1_800_000_000_000 }) {
  const call = async (method, path, { body, headers = {}, ip = '203.0.113.7' } = {}) => {
    const req = new Request(`https://lic.example.dev${path}`, {
      method,
      headers: { 'content-type': 'application/json', 'cf-connecting-ip': ip, ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const res = await route(req, env, clock.now);
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* html */ }
    return { status: res.status, json, text, headers: res.headers };
  };
  const admin = (path, body, password = env.ADMIN_PASSWORD) =>
    call(body ? 'POST' : 'GET', `/admin/api/${path}`, { body, headers: { authorization: `Bearer ${password}` } });
  return { call, admin, clock };
}
