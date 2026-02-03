from __future__ import annotations

import asyncio
import json

from fastapi import APIRouter, Depends, HTTPException

from ..models import MacroStartRequest
from ..database import get_setting, get_all_settings, save_macro, update_macro
from ..jwt_auth import get_current_user
from ..rail_bridge import RailBridge, build_passengers

router = APIRouter(tags=["macro"])

# These will be set by main.py
task_manager = None
ws_manager = None


def init(tm, wm):
    global task_manager, ws_manager
    task_manager = tm
    ws_manager = wm


@router.post("/macro/start")
async def start_macro(body: MacroStartRequest, user: dict = Depends(get_current_user)):
    uid = user["id"]
    rail_user_id = await get_setting(body.rail_type, "id", user_id=uid)
    password = await get_setting(body.rail_type, "pass", user_id=uid)

    if not rail_user_id or not password:
        raise HTTPException(status_code=401, detail=f"{body.rail_type} login required")

    # Build passengers
    passengers = build_passengers(body.rail_type, body.passengers)

    # Load card info if auto-pay
    card_info = None
    if body.auto_pay:
        card_settings = await get_all_settings("card", user_id=uid)
        if card_settings.get("ok") != "1":
            raise HTTPException(status_code=400, detail="Card not configured for auto-pay")
        card_info = card_settings

    # Verify credentials work before starting macro
    try:
        bridge = RailBridge(body.rail_type, rail_user_id, password)
        await bridge.login()
        await bridge.search(
            dep=body.departure,
            arr=body.arrival,
            date=body.date,
            time=body.time,
            passengers=passengers,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Verification failed: {e}")

    # Get the event loop for cross-thread communication
    loop = asyncio.get_running_loop()

    # Create event callback
    async def on_event(task_id: str, data: dict):
        await ws_manager.broadcast(task_id, data)
        # Update DB on terminal events
        if data.get("type") in ("success", "failed", "cancelled"):
            from datetime import datetime
            await update_macro(
                task_id,
                status=data["type"],
                attempts=data.get("attempts", 0),
                elapsed_seconds=data.get("elapsed", 0),
                result_text=json.dumps(data.get("reservation", {}), ensure_ascii=False) if data.get("reservation") else "",
                finished_at=datetime.now().isoformat(),
            )

    # Create macro task
    search_params = {
        "dep": body.departure,
        "arr": body.arrival,
        "date": body.date,
        "time": body.time,
    }

    task = task_manager.create_task(
        rail_type=body.rail_type,
        search_params=search_params,
        train_indices=body.train_indices,
        passengers=passengers,
        seat_type_str=body.seat_type,
        auto_pay=body.auto_pay,
        card_info=card_info,
        on_event=on_event,
        loop=loop,
        user_id=rail_user_id,
        password=password,
        app_user_id=uid,
    )

    # Save to DB
    await save_macro({
        "id": task.task_id,
        "user_id": uid,
        "rail_type": body.rail_type,
        "departure": body.departure,
        "arrival": body.arrival,
        "date": body.date,
        "time": body.time,
        "passengers_json": json.dumps(body.passengers, ensure_ascii=False),
        "seat_type": body.seat_type,
        "auto_pay": 1 if body.auto_pay else 0,
        "selected_trains_json": json.dumps(body.train_indices),
        "status": "running",
    })

    # Start background thread
    task_manager.start_task(task.task_id)

    return {"task_id": task.task_id, "status": "running"}


@router.get("/macro/active")
async def list_active_macros(user: dict = Depends(get_current_user)):
    uid = user["id"]
    if user["role"] == "admin":
        return {"tasks": task_manager.list_active()}
    return {"tasks": task_manager.list_active(app_user_id=uid)}


@router.get("/macro/{task_id}")
async def get_macro_status(task_id: str, user: dict = Depends(get_current_user)):
    status = task_manager.get_status(task_id)
    if not status:
        raise HTTPException(status_code=404, detail="Macro task not found")
    # Ownership check (admin can see all)
    if user["role"] != "admin" and status.get("app_user_id") != user["id"]:
        raise HTTPException(status_code=404, detail="Macro task not found")
    return status


@router.delete("/macro/{task_id}")
async def cancel_macro(task_id: str, user: dict = Depends(get_current_user)):
    task = task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Macro task not found")
    # Ownership check
    if user["role"] != "admin" and getattr(task, "app_user_id", 0) != user["id"]:
        raise HTTPException(status_code=404, detail="Macro task not found")
    task_manager.cancel_task(task_id)
    await update_macro(task_id, status="cancelled")
    return {"status": "cancelled", "task_id": task_id}
