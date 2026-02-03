from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from starlette.responses import JSONResponse

from .config import CORS_ORIGINS
from .database import init_db
from .task_manager import TaskManager
from .ws_manager import WSManager
from .routers import auth, settings, trains, reservations, macro, ws, admin

task_manager = TaskManager()
ws_manager = WSManager()

# Paths that skip JWT auth
_PUBLIC_PATHS = {"/docs", "/openapi.json", "/redoc", "/api/health", "/api/user/register", "/api/user/login", "/api/trains/stations"}

_bot_task: asyncio.Task | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _bot_task
    await init_db()

    # Start admin Telegram bot in background
    from .telegram_bot import start_polling_bot
    _bot_task = asyncio.create_task(start_polling_bot())

    yield

    # Cleanup
    if _bot_task and not _bot_task.done():
        _bot_task.cancel()
        try:
            await _bot_task
        except asyncio.CancelledError:
            pass

    for task_info in task_manager.list_active():
        task_manager.cancel_task(task_info["task_id"])


app = FastAPI(
    title="SRTgo Web API",
    description="Web interface for SRTgo train reservation helper",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# JWT auth middleware
@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    # Skip auth for CORS preflight, docs, public endpoints, and websocket
    if request.method == "OPTIONS":
        return await call_next(request)
    path = request.url.path
    if path in _PUBLIC_PATHS or path.startswith("/ws/"):
        return await call_next(request)

    # Check JWT bearer token
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        return JSONResponse(
            status_code=401,
            content={"detail": "Missing Authorization header", "code": "TOKEN_REQUIRED"},
        )

    from .jwt_auth import decode_token
    from jose import JWTError
    token = auth_header[7:]
    try:
        decode_token(token)
    except JWTError:
        return JSONResponse(
            status_code=401,
            content={"detail": "Invalid or expired token", "code": "TOKEN_INVALID"},
        )

    return await call_next(request)


# Initialize macro/ws routers with shared state
macro.init(task_manager, ws_manager)
ws.init(task_manager, ws_manager)

# Include routers
app.include_router(auth.router, prefix="/api")
app.include_router(admin.router, prefix="/api")
app.include_router(settings.router, prefix="/api")
app.include_router(trains.router, prefix="/api")
app.include_router(reservations.router, prefix="/api")
app.include_router(macro.router, prefix="/api")
app.include_router(ws.router)


@app.get("/api/health")
async def health():
    return {"status": "ok"}
