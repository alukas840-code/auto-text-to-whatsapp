CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    whatsapp_name TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS profiles (
    user_id TEXT PRIMARY KEY,
    surname TEXT,
    role TEXT
);

CREATE TABLE IF NOT EXISTS access_levels (
    user_id TEXT PRIMARY KEY,
    level INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL
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
    event_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_contexts (
    scope_type TEXT NOT NULL,
    scope_id TEXT NOT NULL,
    context_json TEXT,
    greeted INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL,
    PRIMARY KEY(scope_type, scope_id)
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

CREATE TABLE IF NOT EXISTS rate_limits (
    key TEXT PRIMARY KEY,
    window_start INTEGER,
    count INTEGER,
    cooldown_until INTEGER
);
