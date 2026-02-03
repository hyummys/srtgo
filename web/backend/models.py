from __future__ import annotations

from pydantic import BaseModel, Field
from typing import Optional, Literal


class LoginRequest(BaseModel):
    rail_type: Literal["SRT", "KTX"]
    id: str = Field(min_length=1)
    password: str = Field(min_length=1)


class SearchRequest(BaseModel):
    rail_type: Literal["SRT", "KTX"]
    departure: str
    arrival: str
    date: str = Field(pattern=r"^\d{8}$")
    time: str = Field(pattern=r"^\d{6}$")
    passengers: dict = Field(default_factory=lambda: {"adult": 1})


class MacroStartRequest(BaseModel):
    rail_type: Literal["SRT", "KTX"]
    departure: str
    arrival: str
    date: str = Field(pattern=r"^\d{8}$")
    time: str = Field(pattern=r"^\d{6}$")
    passengers: dict = Field(default_factory=lambda: {"adult": 1})
    train_indices: list[int] = Field(min_length=1)
    seat_type: Literal["GENERAL_FIRST", "GENERAL_ONLY", "SPECIAL_FIRST", "SPECIAL_ONLY"] = "GENERAL_FIRST"
    auto_pay: bool = False


class StationSettingsRequest(BaseModel):
    stations: list[str]


class TelegramSettingsRequest(BaseModel):
    token: str
    chat_id: str


class CardSettingsRequest(BaseModel):
    number: str
    password: str
    birthday: str
    expire: str


class OptionsSettingsRequest(BaseModel):
    options: list[str] = []


class DefaultsSettingsRequest(BaseModel):
    departure: Optional[str] = None
    arrival: Optional[str] = None
    date: Optional[str] = None
    time: Optional[str] = None
    adult: int = 1
    child: int = 0
    senior: int = 0
    disability1to3: int = 0
    disability4to6: int = 0


class PayRequest(BaseModel):
    reservation_number: str


# --- User Auth Models ---

class UserRegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=30)
    password: str = Field(min_length=6)
    nickname: str = Field(min_length=1, max_length=30)


class UserLoginRequest(BaseModel):
    username: str = Field(min_length=1)
    password: str = Field(min_length=1)


class UserResponse(BaseModel):
    id: int
    username: str
    nickname: str
    role: str
    status: str
    created_at: str
