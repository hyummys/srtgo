from fastapi import APIRouter, Depends, HTTPException

from ..models import (
    StationSettingsRequest,
    TelegramSettingsRequest,
    CardSettingsRequest,
    OptionsSettingsRequest,
    DefaultsSettingsRequest,
)
from ..database import get_setting, set_setting, get_all_settings, delete_setting
from ..jwt_auth import get_current_user
from ..rail_bridge import STATIONS, DEFAULT_STATIONS

router = APIRouter(tags=["settings"])


# --- Stations ---

@router.get("/settings/stations/{rail_type}")
async def get_stations(rail_type: str, user: dict = Depends(get_current_user)):
    if rail_type not in ("SRT", "KTX"):
        raise HTTPException(status_code=400, detail="Invalid rail_type")
    uid = user["id"]
    saved = await get_setting(rail_type, "station", user_id=uid)
    selected = saved.split(",") if saved else DEFAULT_STATIONS.get(rail_type, [])
    return {
        "available": STATIONS.get(rail_type, []),
        "selected": selected,
    }


@router.put("/settings/stations/{rail_type}")
async def save_stations(rail_type: str, body: StationSettingsRequest, user: dict = Depends(get_current_user)):
    if rail_type not in ("SRT", "KTX"):
        raise HTTPException(status_code=400, detail="Invalid rail_type")
    await set_setting(rail_type, "station", ",".join(body.stations), user_id=user["id"])
    return {"status": "ok"}


# --- Telegram ---

@router.get("/settings/telegram")
async def get_telegram(user: dict = Depends(get_current_user)):
    uid = user["id"]
    settings = await get_all_settings("telegram", user_id=uid)
    token = settings.get("token", "")
    return {
        "token": _mask_token(token),
        "chat_id": settings.get("chat_id", ""),
        "enabled": settings.get("ok") == "1",
    }


@router.put("/settings/telegram")
async def save_telegram(body: TelegramSettingsRequest, user: dict = Depends(get_current_user)):
    uid = user["id"]
    await set_setting("telegram", "token", body.token, user_id=uid)
    await set_setting("telegram", "chat_id", body.chat_id, user_id=uid)

    # Test telegram connection
    try:
        import telegram
        bot = telegram.Bot(token=body.token)
        await bot.send_message(chat_id=body.chat_id, text="SRTgo 웹 연결 테스트 성공!")
        await set_setting("telegram", "ok", "1", user_id=uid)
        return {"status": "ok", "message": "Telegram test message sent"}
    except Exception as e:
        await delete_setting("telegram", "ok", user_id=uid)
        raise HTTPException(status_code=400, detail=f"Telegram test failed: {e}")


# --- Card ---

@router.get("/settings/card")
async def get_card(user: dict = Depends(get_current_user)):
    uid = user["id"]
    settings = await get_all_settings("card", user_id=uid)
    number = settings.get("number", "")
    return {
        "number_masked": _mask_card(number),
        "has_birthday": bool(settings.get("birthday")),
        "has_expire": bool(settings.get("expire")),
        "enabled": settings.get("ok") == "1",
    }


@router.put("/settings/card")
async def save_card(body: CardSettingsRequest, user: dict = Depends(get_current_user)):
    uid = user["id"]
    await set_setting("card", "number", body.number, user_id=uid)
    await set_setting("card", "password", body.password, user_id=uid)
    await set_setting("card", "birthday", body.birthday, user_id=uid)
    await set_setting("card", "expire", body.expire, user_id=uid)
    await set_setting("card", "ok", "1", user_id=uid)
    return {"status": "ok"}


# --- Options ---

@router.get("/settings/options")
async def get_options(user: dict = Depends(get_current_user)):
    saved = await get_setting("SRT", "options", user_id=user["id"])
    options = saved.split(",") if saved else []
    return {"options": options}


@router.put("/settings/options")
async def save_options(body: OptionsSettingsRequest, user: dict = Depends(get_current_user)):
    await set_setting("SRT", "options", ",".join(body.options), user_id=user["id"])
    return {"status": "ok"}


# --- Defaults ---

@router.get("/settings/defaults/{rail_type}")
async def get_defaults(rail_type: str, user: dict = Depends(get_current_user)):
    if rail_type not in ("SRT", "KTX"):
        raise HTTPException(status_code=400, detail="Invalid rail_type")
    uid = user["id"]
    settings = await get_all_settings(rail_type, user_id=uid)
    is_srt = rail_type == "SRT"
    return {
        "departure": settings.get("departure", "수서" if is_srt else "서울"),
        "arrival": settings.get("arrival", "동대구"),
        "date": settings.get("date", ""),
        "time": settings.get("time", "120000"),
        "adult": int(settings.get("adult", 1)),
        "child": int(settings.get("child", 0)),
        "senior": int(settings.get("senior", 0)),
        "disability1to3": int(settings.get("disability1to3", 0)),
        "disability4to6": int(settings.get("disability4to6", 0)),
    }


@router.put("/settings/defaults/{rail_type}")
async def save_defaults(rail_type: str, body: DefaultsSettingsRequest, user: dict = Depends(get_current_user)):
    if rail_type not in ("SRT", "KTX"):
        raise HTTPException(status_code=400, detail="Invalid rail_type")
    uid = user["id"]
    data = body.model_dump(exclude_none=True)
    for key, value in data.items():
        await set_setting(rail_type, key, str(value), user_id=uid)
    return {"status": "ok"}


# --- Helpers ---

def _mask_token(token: str) -> str:
    if not token or len(token) < 10:
        return "***"
    return token[:5] + "***" + token[-3:]


def _mask_card(number: str) -> str:
    if not number or len(number) < 8:
        return "***"
    return "****-****-****-" + number[-4:]
