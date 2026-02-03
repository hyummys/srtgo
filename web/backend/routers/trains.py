from fastapi import APIRouter, Depends, HTTPException

from ..models import SearchRequest
from ..database import get_setting, set_setting
from ..jwt_auth import get_current_user
from ..rail_bridge import RailBridge, STATIONS, build_passengers

router = APIRouter(tags=["trains"])


@router.get("/trains/stations")
async def get_stations():
    return STATIONS


@router.post("/trains/search")
async def search_trains(body: SearchRequest, user: dict = Depends(get_current_user)):
    uid = user["id"]
    user_id = await get_setting(body.rail_type, "id", user_id=uid)
    password = await get_setting(body.rail_type, "pass", user_id=uid)

    if not user_id or not password:
        raise HTTPException(
            status_code=401,
            detail=f"{body.rail_type} login required. Go to Settings to configure."
        )

    # Validate stations
    available = STATIONS.get(body.rail_type, [])
    if body.departure not in available:
        raise HTTPException(status_code=400, detail=f"Invalid departure station: {body.departure}")
    if body.arrival not in available:
        raise HTTPException(status_code=400, detail=f"Invalid arrival station: {body.arrival}")
    if body.departure == body.arrival:
        raise HTTPException(status_code=400, detail="Departure and arrival stations must differ")

    passengers = build_passengers(body.rail_type, body.passengers)

    try:
        bridge = RailBridge(body.rail_type, user_id, password)
        await bridge.login()
        trains = await bridge.search(
            dep=body.departure,
            arr=body.arrival,
            date=body.date,
            time=body.time,
            passengers=passengers,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {e}")

    # Save last search params as defaults
    await set_setting(body.rail_type, "departure", body.departure, user_id=uid)
    await set_setting(body.rail_type, "arrival", body.arrival, user_id=uid)
    await set_setting(body.rail_type, "date", body.date, user_id=uid)
    await set_setting(body.rail_type, "time", body.time, user_id=uid)

    return {"trains": trains}
