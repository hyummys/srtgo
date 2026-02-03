from __future__ import annotations

import aiosqlite
from datetime import datetime

from .config import DB_PATH, SECRET_KEY
from .crypto import encrypt, decrypt

# Fields that must be encrypted at rest
ENCRYPTED_FIELDS = {
    ("SRT", "id"), ("SRT", "pass"),
    ("KTX", "id"), ("KTX", "pass"),
    ("card", "number"), ("card", "password"), ("card", "birthday"), ("card", "expire"),
    ("telegram", "token"),
}


def _should_encrypt(category: str, key: str) -> bool:
    return (category, key) in ENCRYPTED_FIELDS


async def get_db() -> aiosqlite.Connection:
    db = await aiosqlite.connect(DB_PATH)
    db.row_factory = aiosqlite.Row
    return db


async def init_db():
    async with aiosqlite.connect(DB_PATH) as db:
        # Users table
        await db.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                nickname TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'user',
                status TEXT NOT NULL DEFAULT 'pending',
                created_at TEXT DEFAULT (datetime('now')),
                updated_at TEXT DEFAULT (datetime('now'))
            )
        """)

        # Check if settings table needs migration (add user_id)
        cursor = await db.execute("PRAGMA table_info(settings)")
        columns = [row[1] for row in await cursor.fetchall()]

        if "user_id" not in columns:
            if "category" in columns:
                # Migrate existing settings table
                await db.execute("""
                    CREATE TABLE settings_new (
                        user_id INTEGER NOT NULL DEFAULT 0,
                        category TEXT NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT NOT NULL,
                        encrypted INTEGER DEFAULT 0,
                        updated_at TEXT DEFAULT (datetime('now')),
                        UNIQUE(user_id, category, key)
                    )
                """)
                await db.execute(
                    "INSERT INTO settings_new (user_id, category, key, value, encrypted, updated_at) "
                    "SELECT 0, category, key, value, encrypted, updated_at FROM settings"
                )
                await db.execute("DROP TABLE settings")
                await db.execute("ALTER TABLE settings_new RENAME TO settings")
            else:
                # Fresh install
                await db.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        user_id INTEGER NOT NULL DEFAULT 0,
                        category TEXT NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT NOT NULL,
                        encrypted INTEGER DEFAULT 0,
                        updated_at TEXT DEFAULT (datetime('now')),
                        UNIQUE(user_id, category, key)
                    )
                """)

        # Check if macro_history needs user_id
        cursor = await db.execute("PRAGMA table_info(macro_history)")
        macro_columns = [row[1] for row in await cursor.fetchall()]

        if not macro_columns:
            # Fresh install
            await db.execute("""
                CREATE TABLE IF NOT EXISTS macro_history (
                    id TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL DEFAULT 0,
                    rail_type TEXT NOT NULL,
                    departure TEXT NOT NULL,
                    arrival TEXT NOT NULL,
                    date TEXT NOT NULL,
                    time TEXT NOT NULL,
                    passengers_json TEXT NOT NULL,
                    seat_type TEXT NOT NULL,
                    auto_pay INTEGER DEFAULT 0,
                    selected_trains_json TEXT NOT NULL,
                    status TEXT DEFAULT 'pending',
                    attempts INTEGER DEFAULT 0,
                    elapsed_seconds REAL DEFAULT 0,
                    result_text TEXT,
                    created_at TEXT DEFAULT (datetime('now')),
                    finished_at TEXT
                )
            """)
        elif "user_id" not in macro_columns:
            await db.execute("ALTER TABLE macro_history ADD COLUMN user_id INTEGER NOT NULL DEFAULT 0")

        await db.commit()


# ──────────────────────────────────────────────
# User CRUD
# ──────────────────────────────────────────────

async def create_user(username: str, password_hash: str, nickname: str, role: str = "user", status: str = "pending") -> int:
    async with aiosqlite.connect(DB_PATH) as db:
        cursor = await db.execute(
            "INSERT INTO users (username, password_hash, nickname, role, status) VALUES (?, ?, ?, ?, ?)",
            (username, password_hash, nickname, role, status),
        )
        await db.commit()
        return cursor.lastrowid


async def get_user_by_username(username: str) -> dict | None:
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        cursor = await db.execute("SELECT * FROM users WHERE username = ?", (username,))
        row = await cursor.fetchone()
        return dict(row) if row else None


async def get_user_by_id(user_id: int) -> dict | None:
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        cursor = await db.execute("SELECT * FROM users WHERE id = ?", (user_id,))
        row = await cursor.fetchone()
        return dict(row) if row else None


async def list_users() -> list[dict]:
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        cursor = await db.execute(
            "SELECT id, username, nickname, role, status, created_at, updated_at FROM users ORDER BY created_at DESC"
        )
        rows = await cursor.fetchall()
        return [dict(row) for row in rows]


async def update_user_status(user_id: int, status: str):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            "UPDATE users SET status = ?, updated_at = datetime('now') WHERE id = ?",
            (status, user_id),
        )
        await db.commit()


async def delete_user(user_id: int):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("DELETE FROM settings WHERE user_id = ?", (user_id,))
        await db.execute("DELETE FROM macro_history WHERE user_id = ?", (user_id,))
        await db.execute("DELETE FROM users WHERE id = ?", (user_id,))
        await db.commit()


async def count_users() -> int:
    async with aiosqlite.connect(DB_PATH) as db:
        cursor = await db.execute("SELECT COUNT(*) FROM users")
        row = await cursor.fetchone()
        return row[0]


async def reassign_legacy_data(user_id: int):
    """Reassign user_id=0 legacy data to the first admin user."""
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("UPDATE settings SET user_id = ? WHERE user_id = 0", (user_id,))
        await db.execute("UPDATE macro_history SET user_id = ? WHERE user_id = 0", (user_id,))
        await db.commit()


# ──────────────────────────────────────────────
# Settings (per-user)
# ──────────────────────────────────────────────

async def get_setting(category: str, key: str, user_id: int = 0) -> str | None:
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        cursor = await db.execute(
            "SELECT value, encrypted FROM settings WHERE user_id = ? AND category = ? AND key = ?",
            (user_id, category, key),
        )
        row = await cursor.fetchone()
        if row is None:
            return None
        value = row["value"]
        if row["encrypted"]:
            value = decrypt(value, SECRET_KEY)
        return value


async def set_setting(category: str, key: str, value: str, user_id: int = 0):
    should_enc = _should_encrypt(category, key)
    stored_value = encrypt(value, SECRET_KEY) if should_enc else value
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            """INSERT INTO settings (user_id, category, key, value, encrypted, updated_at)
               VALUES (?, ?, ?, ?, ?, datetime('now'))
               ON CONFLICT(user_id, category, key)
               DO UPDATE SET value = excluded.value,
                             encrypted = excluded.encrypted,
                             updated_at = excluded.updated_at""",
            (user_id, category, key, stored_value, 1 if should_enc else 0),
        )
        await db.commit()


async def delete_setting(category: str, key: str, user_id: int = 0):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            "DELETE FROM settings WHERE user_id = ? AND category = ? AND key = ?",
            (user_id, category, key),
        )
        await db.commit()


async def get_all_settings(category: str, user_id: int = 0) -> dict[str, str]:
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        cursor = await db.execute(
            "SELECT key, value, encrypted FROM settings WHERE user_id = ? AND category = ?",
            (user_id, category),
        )
        rows = await cursor.fetchall()
        result = {}
        for row in rows:
            value = row["value"]
            if row["encrypted"]:
                value = decrypt(value, SECRET_KEY)
            result[row["key"]] = value
        return result


# ──────────────────────────────────────────────
# Macro history (per-user)
# ──────────────────────────────────────────────

async def save_macro(data: dict):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            """INSERT INTO macro_history
               (id, user_id, rail_type, departure, arrival, date, time,
                passengers_json, seat_type, auto_pay, selected_trains_json,
                status, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))""",
            (
                data["id"], data.get("user_id", 0),
                data["rail_type"], data["departure"], data["arrival"],
                data["date"], data["time"], data["passengers_json"],
                data["seat_type"], data.get("auto_pay", 0),
                data["selected_trains_json"], data.get("status", "running"),
            ),
        )
        await db.commit()


async def update_macro(macro_id: str, **fields):
    if not fields:
        return
    set_clause = ", ".join(f"{k} = ?" for k in fields)
    values = list(fields.values()) + [macro_id]
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            f"UPDATE macro_history SET {set_clause} WHERE id = ?",
            values,
        )
        await db.commit()


async def get_macro(macro_id: str) -> dict | None:
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        cursor = await db.execute(
            "SELECT * FROM macro_history WHERE id = ?", (macro_id,)
        )
        row = await cursor.fetchone()
        return dict(row) if row else None


async def list_macros(status: str | None = None, limit: int = 50, user_id: int | None = None) -> list[dict]:
    async with aiosqlite.connect(DB_PATH) as db:
        db.row_factory = aiosqlite.Row
        conditions = []
        params = []
        if status:
            conditions.append("status = ?")
            params.append(status)
        if user_id is not None:
            conditions.append("user_id = ?")
            params.append(user_id)
        where = (" WHERE " + " AND ".join(conditions)) if conditions else ""
        params.append(limit)
        cursor = await db.execute(
            f"SELECT * FROM macro_history{where} ORDER BY created_at DESC LIMIT ?",
            params,
        )
        rows = await cursor.fetchall()
        return [dict(row) for row in rows]
