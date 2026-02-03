from __future__ import annotations

from datetime import datetime, timedelta

import bcrypt
from jose import JWTError, jwt
from fastapi import Depends, HTTPException, Request

from .config import JWT_SECRET, JWT_EXPIRY_DAYS

ALGORITHM = "HS256"


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain: str, hashed: str) -> bool:
    return bcrypt.checkpw(plain.encode("utf-8"), hashed.encode("utf-8"))


def create_access_token(user_id: int, username: str, role: str) -> str:
    payload = {
        "sub": str(user_id),
        "username": username,
        "role": role,
        "exp": datetime.utcnow() + timedelta(days=JWT_EXPIRY_DAYS),
        "iat": datetime.utcnow(),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=ALGORITHM)


def decode_token(token: str) -> dict:
    return jwt.decode(token, JWT_SECRET, algorithms=[ALGORITHM])


async def get_current_user(request: Request) -> dict:
    """FastAPI dependency that extracts and validates JWT from Authorization header."""
    from .database import get_user_by_id  # lazy import to avoid circular

    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing token", headers={"code": "TOKEN_REQUIRED"})

    token = auth_header[7:]
    try:
        payload = decode_token(token)
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token", headers={"code": "TOKEN_INVALID"})

    user = await get_user_by_id(int(payload["sub"]))
    if not user:
        raise HTTPException(status_code=401, detail="User not found", headers={"code": "TOKEN_INVALID"})
    if user["status"] != "approved":
        raise HTTPException(status_code=403, detail="Account not approved")

    request.state.user = user
    return user


async def require_admin(user: dict = Depends(get_current_user)) -> dict:
    """FastAPI dependency that requires admin role."""
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Admin access required")
    return user
