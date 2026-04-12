import { execFileSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

function esc(value) {
  if (value === null || value === undefined) return 'NULL';
  if (typeof value === 'number') return String(value);
  if (typeof value === 'boolean') return value ? '1' : '0';
  return `'${String(value).replace(/'/g, "''")}'`;
}

export class SqliteStorage {
  constructor(dbPath = '.data/dauren.sqlite') {
    this.dbPath = dbPath;
    mkdirSync(dirname(dbPath), { recursive: true });
    this.initSchema();
  }

  run(sql) {
    execFileSync('sqlite3', [this.dbPath, sql], { encoding: 'utf-8' });
  }

  all(sql) {
    const out = execFileSync('sqlite3', ['-json', this.dbPath, sql], { encoding: 'utf-8' });
    return out.trim() ? JSON.parse(out) : [];
  }

  get(sql) {
    return this.all(sql)[0] ?? null;
  }

  initSchema() {
    this.run(`
      PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        whatsapp_name TEXT,
        created_at TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS profiles (
        user_id TEXT PRIMARY KEY,
        surname TEXT,
        role TEXT,
        FOREIGN KEY(user_id) REFERENCES users(id)
      );
      CREATE TABLE IF NOT EXISTS access_levels (
        user_id TEXT PRIMARY KEY,
        level INTEGER NOT NULL DEFAULT 1,
        updated_at TEXT NOT NULL,
        FOREIGN KEY(user_id) REFERENCES users(id)
      );
      CREATE TABLE IF NOT EXISTS group_members (
        chat_id TEXT NOT NULL,
        user_id TEXT NOT NULL,
        PRIMARY KEY(chat_id, user_id)
      );
      CREATE TABLE IF NOT EXISTS tasks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        chat_id TEXT NOT NULL,
        assignee_id TEXT NOT NULL,
        created_by TEXT NOT NULL,
        text TEXT NOT NULL,
        type TEXT NOT NULL,
        due_date TEXT,
        weekday TEXT,
        remind_time TEXT,
        done_at TEXT,
        deleted_at TEXT,
        created_at TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS task_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        task_id INTEGER NOT NULL,
        event_type TEXT NOT NULL,
        actor_id TEXT,
        payload TEXT,
        event_at TEXT NOT NULL,
        FOREIGN KEY(task_id) REFERENCES tasks(id)
      );
      CREATE TABLE IF NOT EXISTS audit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        actor_id TEXT,
        action TEXT NOT NULL,
        object_type TEXT,
        object_id TEXT,
        details TEXT,
        at TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS conversation_contexts (
        scope_type TEXT NOT NULL,
        scope_id TEXT NOT NULL,
        context_json TEXT,
        greeted INTEGER NOT NULL DEFAULT 0,
        updated_at TEXT NOT NULL,
        PRIMARY KEY(scope_type, scope_id)
      );
      CREATE TABLE IF NOT EXISTS rate_limits (
        key TEXT PRIMARY KEY,
        window_start INTEGER,
        count INTEGER,
        cooldown_until INTEGER
      );
    `);
  }

  ensureUser(userId, whatsappName = null) {
    const now = new Date().toISOString();
    this.run(`INSERT OR IGNORE INTO users(id, whatsapp_name, created_at) VALUES(${esc(userId)}, ${esc(whatsappName)}, ${esc(now)});`);
    if (whatsappName) {
      this.run(`UPDATE users SET whatsapp_name=${esc(whatsappName)} WHERE id=${esc(userId)};`);
    }
    this.run(`INSERT OR IGNORE INTO profiles(user_id) VALUES(${esc(userId)});`);
    this.run(`INSERT OR IGNORE INTO access_levels(user_id, level, updated_at) VALUES(${esc(userId)}, 1, ${esc(now)});`);
    return this.getUser(userId);
  }

  rememberGroupMember(chatId, userId) {
    this.run(`INSERT OR IGNORE INTO group_members(chat_id, user_id) VALUES(${esc(chatId)}, ${esc(userId)});`);
  }

  listGroupMembers(chatId) {
    return this.all(`
      SELECT u.id, u.whatsapp_name, p.surname, p.role, a.level as access_level
      FROM group_members gm
      JOIN users u ON u.id = gm.user_id
      LEFT JOIN profiles p ON p.user_id = u.id
      LEFT JOIN access_levels a ON a.user_id = u.id
      WHERE gm.chat_id = ${esc(chatId)}
      ORDER BY COALESCE(p.surname, u.whatsapp_name, u.id) COLLATE NOCASE;
    `);
  }

  getUser(userId) {
    return this.get(`
      SELECT u.id, u.whatsapp_name, p.surname, p.role, COALESCE(a.level,1) as access_level
      FROM users u
      LEFT JOIN profiles p ON p.user_id = u.id
      LEFT JOIN access_levels a ON a.user_id = u.id
      WHERE u.id = ${esc(userId)};
    `);
  }

  setProfile(userId, { surname, role }) {
    if (surname !== undefined) this.run(`UPDATE profiles SET surname=${esc(surname)} WHERE user_id=${esc(userId)};`);
    if (role !== undefined) this.run(`UPDATE profiles SET role=${esc(role)} WHERE user_id=${esc(userId)};`);
    return this.getUser(userId);
  }

  getAccessLevel(userId) {
    const row = this.get(`SELECT level FROM access_levels WHERE user_id=${esc(userId)};`);
    return row ? Number(row.level) : 1;
  }

  setAccessLevel(userId, level) {
    const now = new Date().toISOString();
    this.run(`INSERT INTO access_levels(user_id, level, updated_at) VALUES(${esc(userId)}, ${esc(level)}, ${esc(now)})
      ON CONFLICT(user_id) DO UPDATE SET level=excluded.level, updated_at=excluded.updated_at;`);
  }

  createTask(task) {
    const now = new Date().toISOString();
    this.run(`
      INSERT INTO tasks(chat_id, assignee_id, created_by, text, type, due_date, weekday, remind_time, created_at)
      VALUES(${esc(task.chatId)}, ${esc(task.assigneeId)}, ${esc(task.createdBy)}, ${esc(task.text)}, ${esc(task.type)},
             ${esc(task.dueDate)}, ${esc(task.weekday)}, ${esc(task.remindTime)}, ${esc(now)});
    `);
    return this.get('SELECT * FROM tasks ORDER BY id DESC LIMIT 1;');
  }

  listActiveTasks(chatId) {
    return this.all(`SELECT * FROM tasks WHERE chat_id=${esc(chatId)} AND deleted_at IS NULL AND done_at IS NULL ORDER BY id ASC;`);
  }

  getTask(taskId) {
    return this.get(`SELECT * FROM tasks WHERE id=${esc(taskId)};`);
  }

  markTaskDone(taskId, doneAt = new Date().toISOString()) {
    this.run(`UPDATE tasks SET done_at=${esc(doneAt)} WHERE id=${esc(taskId)};`);
  }

  deleteTask(taskId, deletedAt = new Date().toISOString()) {
    this.run(`UPDATE tasks SET deleted_at=${esc(deletedAt)} WHERE id=${esc(taskId)};`);
  }

  logTaskEvent(taskId, eventType, actorId = null, payload = null) {
    const now = new Date().toISOString();
    this.run(`INSERT INTO task_events(task_id, event_type, actor_id, payload, event_at) VALUES(${esc(taskId)}, ${esc(eventType)}, ${esc(actorId)}, ${esc(payload ? JSON.stringify(payload) : null)}, ${esc(now)});`);
  }

  hasTaskEvent(taskId, eventType, periodKey) {
    const row = this.get(`SELECT id FROM task_events WHERE task_id=${esc(taskId)} AND event_type=${esc(eventType)} AND payload LIKE ${esc(`%"periodKey":"${periodKey}"%`)} LIMIT 1;`);
    return Boolean(row);
  }

  getDueTasks(dateStr, timeStr, weekday) {
    return this.all(`
      SELECT * FROM tasks
      WHERE deleted_at IS NULL
        AND done_at IS NULL
        AND (
          (type='dated' AND due_date=${esc(dateStr)} AND remind_time <= ${esc(timeStr)})
          OR
          (type='weekly' AND weekday=${esc(weekday)} AND remind_time <= ${esc(timeStr)})
        );
    `);
  }

  writeAudit({ actorId = null, action, objectType = null, objectId = null, details = null }) {
    const at = new Date().toISOString();
    this.run(`INSERT INTO audit_logs(actor_id, action, object_type, object_id, details, at)
              VALUES(${esc(actorId)}, ${esc(action)}, ${esc(objectType)}, ${esc(objectId)}, ${esc(details ? JSON.stringify(details) : null)}, ${esc(at)});`);
  }

  getContext(scopeType, scopeId) {
    return this.get(`SELECT * FROM conversation_contexts WHERE scope_type=${esc(scopeType)} AND scope_id=${esc(scopeId)};`);
  }

  saveContext(scopeType, scopeId, contextJson, greeted) {
    const now = new Date().toISOString();
    this.run(`INSERT INTO conversation_contexts(scope_type, scope_id, context_json, greeted, updated_at)
      VALUES(${esc(scopeType)}, ${esc(scopeId)}, ${esc(JSON.stringify(contextJson || []))}, ${esc(greeted ? 1 : 0)}, ${esc(now)})
      ON CONFLICT(scope_type, scope_id)
      DO UPDATE SET context_json=excluded.context_json, greeted=excluded.greeted, updated_at=excluded.updated_at;`);
  }

  clearContext(scopeType, scopeId) {
    const now = new Date().toISOString();
    this.run(`INSERT INTO conversation_contexts(scope_type, scope_id, context_json, greeted, updated_at)
      VALUES(${esc(scopeType)}, ${esc(scopeId)}, ${esc('[]')}, 1, ${esc(now)})
      ON CONFLICT(scope_type, scope_id)
      DO UPDATE SET context_json='[]', updated_at=excluded.updated_at;`);
  }

  getRateLimit(key) {
    return this.get(`SELECT * FROM rate_limits WHERE key=${esc(key)};`);
  }

  saveRateLimit(key, windowStart, count, cooldownUntil = 0) {
    this.run(`INSERT INTO rate_limits(key, window_start, count, cooldown_until)
      VALUES(${esc(key)}, ${esc(windowStart)}, ${esc(count)}, ${esc(cooldownUntil)})
      ON CONFLICT(key) DO UPDATE SET window_start=excluded.window_start, count=excluded.count, cooldown_until=excluded.cooldown_until;`);
  }
}
