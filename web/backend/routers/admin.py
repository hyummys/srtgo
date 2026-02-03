from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from ..database import list_users, update_user_status, delete_user, get_user_by_id
from ..jwt_auth import require_admin

router = APIRouter(tags=["admin"])


@router.get("/admin/users")
async def admin_list_users(user: dict = Depends(require_admin)):
    users = await list_users()
    return {"users": users}


@router.put("/admin/users/{target_id}/approve")
async def admin_approve_user(target_id: int, user: dict = Depends(require_admin)):
    target = await get_user_by_id(target_id)
    if not target:
        raise HTTPException(status_code=404, detail="User not found")
    await update_user_status(target_id, "approved")
    return {"status": "ok", "message": f"User {target['username']} approved"}


@router.put("/admin/users/{target_id}/reject")
async def admin_reject_user(target_id: int, user: dict = Depends(require_admin)):
    target = await get_user_by_id(target_id)
    if not target:
        raise HTTPException(status_code=404, detail="User not found")
    await update_user_status(target_id, "rejected")
    return {"status": "ok", "message": f"User {target['username']} rejected"}


@router.delete("/admin/users/{target_id}")
async def admin_delete_user(target_id: int, user: dict = Depends(require_admin)):
    if target_id == user["id"]:
        raise HTTPException(status_code=400, detail="Cannot delete yourself")
    target = await get_user_by_id(target_id)
    if not target:
        raise HTTPException(status_code=404, detail="User not found")
    await delete_user(target_id)
    return {"status": "ok", "message": f"User {target['username']} deleted"}
