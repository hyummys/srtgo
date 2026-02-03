import os
import secrets
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent.parent  # srtgo_org/
WEB_DIR = BASE_DIR / "web"
DATA_DIR = WEB_DIR / "data"
DATA_DIR.mkdir(parents=True, exist_ok=True)

# Load .env file from web/ directory
_env_file = WEB_DIR / ".env"
if _env_file.exists():
    with open(_env_file) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, value = line.partition("=")
                key, value = key.strip(), value.strip()
                if key and key not in os.environ:
                    os.environ[key] = value

DB_PATH = os.environ.get("SRTGO_DB_PATH", str(DATA_DIR / "srtgo.db"))

# Encryption key for credentials. Set in production via env var.
# If not set, generates a random key (credentials won't survive restart).
SECRET_KEY = os.environ.get("SRTGO_SECRET_KEY", "")
if not SECRET_KEY:
    SECRET_KEY = secrets.token_hex(32)
    print(f"[WARNING] No SRTGO_SECRET_KEY set. Generated temporary key: {SECRET_KEY}")
    print("[WARNING] Set SRTGO_SECRET_KEY env var to persist encrypted credentials across restarts.")

# JWT authentication
JWT_SECRET = os.environ.get("SRTGO_JWT_SECRET", "")
if not JWT_SECRET:
    JWT_SECRET = SECRET_KEY  # Fallback to encryption key
    print("[WARNING] No SRTGO_JWT_SECRET set. Using SECRET_KEY as JWT secret.")
    print("[WARNING] Set SRTGO_JWT_SECRET in web/.env for production.")
JWT_EXPIRY_DAYS = 7

# Admin Telegram bot for user approval
ADMIN_TG_TOKEN = os.environ.get("SRTGO_ADMIN_TG_TOKEN", "")
ADMIN_TG_CHAT_ID = os.environ.get("SRTGO_ADMIN_TG_CHAT_ID", "")
if not ADMIN_TG_TOKEN or not ADMIN_TG_CHAT_ID:
    print("[INFO] Admin Telegram bot not configured. Set SRTGO_ADMIN_TG_TOKEN and SRTGO_ADMIN_TG_CHAT_ID in web/.env.")

CORS_ORIGINS = os.environ.get(
    "SRTGO_CORS_ORIGINS",
    "http://localhost:3000,http://127.0.0.1:3000"
).split(",")
