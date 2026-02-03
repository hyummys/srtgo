from fastapi import APIRouter, Depends, HTTPException

from ..models import PayRequest
from ..database import get_setting, get_all_settings
from ..jwt_auth import get_current_user
from ..rail_bridge import RailBridge

router = APIRouter(tags=["reservations"])


@router.get("/reservations/{rail_type}")
async def list_reservations(rail_type: str, user: dict = Depends(get_current_user)):
    if rail_type not in ("SRT", "KTX"):
        raise HTTPException(status_code=400, detail="Invalid rail_type")

    uid = user["id"]
    user_id = await get_setting(rail_type, "id", user_id=uid)
    password = await get_setting(rail_type, "pass", user_id=uid)

    if not user_id or not password:
        raise HTTPException(status_code=401, detail=f"{rail_type} login required")

    try:
        bridge = RailBridge(rail_type, user_id, password)
        await bridge.login()
        reservations = await bridge.get_reservations()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get reservations: {e}")

    return {"reservations": reservations}


@router.post("/reservations/{rail_type}/pay")
async def pay_reservation(rail_type: str, body: PayRequest, user: dict = Depends(get_current_user)):
    if rail_type not in ("SRT", "KTX"):
        raise HTTPException(status_code=400, detail="Invalid rail_type")

    uid = user["id"]
    user_id = await get_setting(rail_type, "id", user_id=uid)
    password = await get_setting(rail_type, "pass", user_id=uid)
    if not user_id or not password:
        raise HTTPException(status_code=401, detail=f"{rail_type} login required")

    card_settings = await get_all_settings("card", user_id=uid)
    if card_settings.get("ok") != "1":
        raise HTTPException(status_code=400, detail="Card not configured. Go to Settings.")

    try:
        bridge = RailBridge(rail_type, user_id, password)
        await bridge.login()
        result = await bridge.pay(body.reservation_number, card_settings)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Payment failed: {e}")

    return {"status": "ok", "paid": result}


@router.delete("/reservations/{rail_type}/{reservation_number}")
async def cancel_reservation(rail_type: str, reservation_number: str, user: dict = Depends(get_current_user)):
    if rail_type not in ("SRT", "KTX"):
        raise HTTPException(status_code=400, detail="Invalid rail_type")

    uid = user["id"]
    user_id = await get_setting(rail_type, "id", user_id=uid)
    password = await get_setting(rail_type, "pass", user_id=uid)
    if not user_id or not password:
        raise HTTPException(status_code=401, detail=f"{rail_type} login required")

    try:
        bridge = RailBridge(rail_type, user_id, password)
        await bridge.login()
        result = await bridge.cancel(reservation_number)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Cancel failed: {e}")

    return {"status": "ok", "cancelled": result}
