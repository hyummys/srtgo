from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from ..models import LoginRequest, UserRegisterRequest, UserLoginRequest
from ..database import (
    get_setting, set_setting, get_all_settings,
    create_user, get_user_by_username, count_users, reassign_legacy_data,
)
from ..jwt_auth import (
    hash_password, verify_password, create_access_token, get_current_user,
)
from ..rail_bridge import RailBridge

router = APIRouter(tags=["auth"])


# ──────────────────────────────────────────────
# User registration / login
# ──────────────────────────────────────────────

@router.post("/user/register")
async def register(body: UserRegisterRequest):
    existing = await get_user_by_username(body.username)
    if existing:
        raise HTTPException(status_code=409, detail="Username already exists")

    is_first = (await count_users()) == 0
    role = "admin" if is_first else "user"
    status = "approved" if is_first else "pending"

    pw_hash = hash_password(body.password)
    user_id = await create_user(body.username, pw_hash, body.nickname, role, status)

    # First user: reassign legacy data (user_id=0) to this admin
    if is_first:
        await reassign_legacy_data(user_id)

    # Non-first user: send Telegram approval request
    if not is_first:
        from ..telegram_bot import send_approval_request
        await send_approval_request(user_id, body.username, body.nickname)

    if is_first:
        token = create_access_token(user_id, body.username, role)
        return {
            "status": "approved",
            "message": "Admin account created",
            "token": token,
            "user": {"id": user_id, "username": body.username, "nickname": body.nickname, "role": role},
        }

    return {"status": "pending", "message": "Registration complete. Waiting for admin approval."}


@router.post("/user/login")
async def login_user(body: UserLoginRequest):
    user = await get_user_by_username(body.username)
    if not user:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    if not verify_password(body.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="Invalid credentials")

    if user["status"] == "pending":
        raise HTTPException(status_code=403, detail="Account pending approval")
    if user["status"] == "rejected":
        raise HTTPException(status_code=403, detail="Account has been rejected")

    token = create_access_token(user["id"], user["username"], user["role"])
    return {
        "token": token,
        "user": {
            "id": user["id"],
            "username": user["username"],
            "nickname": user["nickname"],
            "role": user["role"],
        },
    }


@router.get("/user/me")
async def get_me(user: dict = Depends(get_current_user)):
    return {
        "id": user["id"],
        "username": user["username"],
        "nickname": user["nickname"],
        "role": user["role"],
        "status": user["status"],
        "created_at": user["created_at"],
    }


# ──────────────────────────────────────────────
# Rail login (SRT/KTX credential save) — per user
# ──────────────────────────────────────────────

@router.post("/auth/login")
async def rail_login(body: LoginRequest, user: dict = Depends(get_current_user)):
    try:
        bridge = RailBridge(body.rail_type, body.id, body.password)
        info = await bridge.login()
    except Exception as e:
        raise HTTPException(status_code=401, detail=f"Login failed: {e}")

    uid = user["id"]
    await set_setting(body.rail_type, "id", body.id, user_id=uid)
    await set_setting(body.rail_type, "pass", body.password, user_id=uid)
    await set_setting(body.rail_type, "ok", "1", user_id=uid)

    return {"status": "ok", "user": info}


@router.get("/auth/status")
async def auth_status(user: dict = Depends(get_current_user)):
    uid = user["id"]
    result = {}
    for rail_type in ("SRT", "KTX"):
        ok = await get_setting(rail_type, "ok", user_id=uid)
        rail_id = await get_setting(rail_type, "id", user_id=uid)
        result[rail_type] = {
            "logged_in": ok == "1" and rail_id is not None,
            "id": rail_id,
        }
    return result


def _mask_id(user_id: str) -> str:
    if not user_id:
        return ""
    if len(user_id) <= 4:
        return user_id[0] + "***"
    return user_id[:2] + "*" * (len(user_id) - 4) + user_id[-2:]
