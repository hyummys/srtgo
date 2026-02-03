"""
Adapter layer that wraps srtgo/srt.py and srtgo/ktx.py for async web use.
Does NOT modify the original modules.
"""
from __future__ import annotations

import sys
import asyncio
from pathlib import Path

# Add project root so `from srtgo.xxx import ...` works
_PROJECT_ROOT = str(Path(__file__).resolve().parent.parent.parent)
if _PROJECT_ROOT not in sys.path:
    sys.path.insert(0, _PROJECT_ROOT)

from srtgo.srt import (
    SRT,
    SRTError,
    SRTLoginError,
    SRTNetFunnelError,
    SRTTrain,
    SRTReservation,
    SRTTicket,
    SeatType,
    Adult as SRTAdult,
    Child as SRTChild,
    Senior as SRTSenior,
    Disability1To3 as SRTDisability1To3,
    Disability4To6 as SRTDisability4To6,
)

from srtgo.ktx import (
    Korail,
    KorailError,
    Train as KTXTrain,
    Reservation as KTXReservation,
    Ticket as KTXTicket,
    Seat as KTXSeat,
    ReserveOption,
    TrainType,
    AdultPassenger,
    ChildPassenger,
    SeniorPassenger,
    Disability1To3Passenger,
    Disability4To6Passenger,
)

from srtgo.srtgo import STATIONS, DEFAULT_STATIONS


# --- Serialization helpers ---

def serialize_srt_train(train: SRTTrain) -> dict:
    dep_h, dep_m = train.dep_time[0:2], train.dep_time[2:4]
    arr_h, arr_m = train.arr_time[0:2], train.arr_time[2:4]
    duration = (int(arr_h) * 60 + int(arr_m)) - (int(dep_h) * 60 + int(dep_m))
    if duration < 0:
        duration += 24 * 60
    return {
        "train_name": train.train_name,
        "train_number": train.train_number,
        "dep_date": train.dep_date,
        "dep_time": train.dep_time,
        "dep_station": train.dep_station_name,
        "arr_time": train.arr_time,
        "arr_station": train.arr_station_name,
        "general_seat_state": train.general_seat_state,
        "special_seat_state": train.special_seat_state,
        "general_available": train.general_seat_available(),
        "special_available": train.special_seat_available(),
        "standby_available": train.reserve_standby_available(),
        "duration_minutes": duration,
        "display": str(train),
    }


def serialize_ktx_train(train: KTXTrain) -> dict:
    dep_h, dep_m = train.dep_time[0:2], train.dep_time[2:4]
    arr_h, arr_m = train.arr_time[0:2], train.arr_time[2:4]
    duration = (int(arr_h) * 60 + int(arr_m)) - (int(dep_h) * 60 + int(dep_m))
    if duration < 0:
        duration += 24 * 60
    return {
        "train_name": train.train_type_name,
        "train_number": train.train_no,
        "dep_date": train.dep_date,
        "dep_time": train.dep_time,
        "dep_station": train.dep_name,
        "arr_time": train.arr_time,
        "arr_station": train.arr_name,
        "general_seat_state": "예약가능" if train.has_general_seat() else "매진",
        "special_seat_state": "예약가능" if train.has_special_seat() else "매진",
        "general_available": train.has_general_seat(),
        "special_available": train.has_special_seat(),
        "standby_available": train.has_waiting_list(),
        "duration_minutes": duration,
        "display": repr(train),
    }


def serialize_srt_reservation(rsv: SRTReservation) -> dict:
    tickets = []
    if rsv.tickets:
        for t in rsv.tickets:
            tickets.append({
                "car": t.car,
                "seat": t.seat,
                "seat_type": t.seat_type,
                "passenger_type": t.passenger_type,
                "price": t.price,
                "original_price": t.original_price,
                "discount": t.discount,
                "is_waiting": t.is_waiting,
            })
    return {
        "reservation_number": rsv.reservation_number,
        "train_name": rsv.train_name,
        "train_number": rsv.train_number,
        "dep_date": rsv.dep_date,
        "dep_time": rsv.dep_time,
        "dep_station": rsv.dep_station_name,
        "arr_time": rsv.arr_time,
        "arr_station": rsv.arr_station_name,
        "total_cost": rsv.total_cost,
        "seat_count": rsv.seat_count,
        "paid": rsv.paid,
        "is_waiting": rsv.is_waiting,
        "payment_date": rsv.payment_date,
        "payment_time": rsv.payment_time,
        "is_ticket": getattr(rsv, "is_ticket", False),
        "tickets": tickets,
        "display": str(rsv),
    }


def serialize_ktx_reservation(rsv: KTXReservation) -> dict:
    return {
        "reservation_number": rsv.rsv_id,
        "train_name": rsv.train_type_name,
        "train_number": rsv.train_no,
        "dep_date": rsv.dep_date,
        "dep_time": rsv.dep_time,
        "dep_station": rsv.dep_name,
        "arr_time": rsv.arr_time,
        "arr_station": rsv.arr_name,
        "total_cost": rsv.price,
        "seat_count": rsv.seat_no_count,
        "paid": False,
        "is_waiting": rsv.is_waiting,
        "payment_date": getattr(rsv, "buy_limit_date", ""),
        "payment_time": getattr(rsv, "buy_limit_time", ""),
        "is_ticket": getattr(rsv, "is_ticket", False),
        "tickets": [],
        "display": repr(rsv),
    }


def serialize_ktx_ticket(ticket: KTXTicket) -> dict:
    return {
        "reservation_number": ticket.pnr_no,
        "train_name": ticket.train_type_name,
        "train_number": ticket.train_no,
        "dep_date": ticket.dep_date,
        "dep_time": ticket.dep_time,
        "dep_station": ticket.dep_name,
        "arr_time": ticket.arr_time,
        "arr_station": ticket.arr_name,
        "total_cost": ticket.price,
        "seat_count": ticket.seat_no_count,
        "paid": True,
        "is_waiting": False,
        "payment_date": "",
        "payment_time": "",
        "is_ticket": True,
        "tickets": [{
            "car": ticket.car_no,
            "seat": ticket.seat_no,
            "seat_type": "",
            "passenger_type": "",
            "price": ticket.price,
            "original_price": 0,
            "discount": 0,
            "is_waiting": False,
        }],
        "display": repr(ticket),
    }


# --- Passenger builders ---

def build_passengers(rail_type: str, passengers: dict) -> list:
    """Build passenger list from dict like {"adult": 1, "child": 0, ...}"""
    result = []
    adult = passengers.get("adult", 1)
    child = passengers.get("child", 0)
    senior = passengers.get("senior", 0)
    d1to3 = passengers.get("disability1to3", 0)
    d4to6 = passengers.get("disability4to6", 0)

    total = adult + child + senior + d1to3 + d4to6
    if total == 0:
        adult = 1

    if rail_type == "SRT":
        if adult > 0:
            result.append(SRTAdult(adult))
        if child > 0:
            result.append(SRTChild(child))
        if senior > 0:
            result.append(SRTSenior(senior))
        if d1to3 > 0:
            result.append(SRTDisability1To3(d1to3))
        if d4to6 > 0:
            result.append(SRTDisability4To6(d4to6))
    else:
        if adult > 0:
            result.append(AdultPassenger(adult))
        if child > 0:
            result.append(ChildPassenger(child))
        if senior > 0:
            result.append(SeniorPassenger(senior))
        if d1to3 > 0:
            result.append(Disability1To3Passenger(d1to3))
        if d4to6 > 0:
            result.append(Disability4To6Passenger(d4to6))

    if not result:
        result = [SRTAdult(1)] if rail_type == "SRT" else [AdultPassenger(1)]

    return result


def get_seat_option(rail_type: str, seat_type: str):
    """Convert seat_type string to SeatType/ReserveOption."""
    if rail_type == "SRT":
        mapping = {
            "GENERAL_FIRST": SeatType.GENERAL_FIRST,
            "GENERAL_ONLY": SeatType.GENERAL_ONLY,
            "SPECIAL_FIRST": SeatType.SPECIAL_FIRST,
            "SPECIAL_ONLY": SeatType.SPECIAL_ONLY,
        }
        return mapping.get(seat_type, SeatType.GENERAL_FIRST)
    else:
        mapping = {
            "GENERAL_FIRST": ReserveOption.GENERAL_FIRST,
            "GENERAL_ONLY": ReserveOption.GENERAL_ONLY,
            "SPECIAL_FIRST": ReserveOption.SPECIAL_FIRST,
            "SPECIAL_ONLY": ReserveOption.SPECIAL_ONLY,
        }
        return mapping.get(seat_type, ReserveOption.GENERAL_FIRST)


def is_seat_available(train, seat_type_str: str, rail_type: str) -> bool:
    """Replicates _is_seat_available from srtgo.py without modifying it."""
    seat_type = get_seat_option(rail_type, seat_type_str)
    if rail_type == "SRT":
        if not train.seat_available():
            return train.reserve_standby_available()
        if seat_type in (SeatType.GENERAL_FIRST, SeatType.SPECIAL_FIRST):
            return train.seat_available()
        if seat_type == SeatType.GENERAL_ONLY:
            return train.general_seat_available()
        return train.special_seat_available()
    else:
        if not train.has_seat():
            return train.has_waiting_list()
        if seat_type in (ReserveOption.GENERAL_FIRST, ReserveOption.SPECIAL_FIRST):
            return train.has_seat()
        if seat_type == ReserveOption.GENERAL_ONLY:
            return train.has_general_seat()
        return train.has_special_seat()


# --- RailBridge class ---

class RailBridge:
    """Wraps SRT/Korail clients for async web use."""

    def __init__(self, rail_type: str, user_id: str, password: str, verbose: bool = False):
        self.rail_type = rail_type
        self._client = None
        self._user_id = user_id
        self._password = password
        self._verbose = verbose

    async def login(self) -> dict:
        def _do():
            if self.rail_type == "SRT":
                client = SRT(self._user_id, self._password, verbose=self._verbose)
                return client, {
                    "name": getattr(client, "membership_name", ""),
                    "membership_number": getattr(client, "membership_number", ""),
                    "phone_number": getattr(client, "phone_number", ""),
                }
            else:
                client = Korail(self._user_id, self._password, verbose=self._verbose)
                if not client.logined:
                    raise KorailError("로그인 실패: 아이디 또는 비밀번호를 확인하세요")
                return client, {
                    "name": getattr(client, "name", ""),
                    "membership_number": getattr(client, "membership_number", ""),
                    "phone_number": getattr(client, "phone_number", ""),
                }
        self._client, info = await asyncio.to_thread(_do)
        return info

    @property
    def client(self):
        return self._client

    async def search(self, dep: str, arr: str, date: str, time: str,
                     passengers: list) -> list[dict]:
        def _do():
            if self.rail_type == "SRT":
                trains = self._client.search_train(
                    dep=dep, arr=arr, date=date, time=time,
                    passengers=passengers, available_only=False,
                )
                return [serialize_srt_train(t) for t in trains]
            else:
                trains = self._client.search_train(
                    dep=dep, arr=arr, date=date, time=time,
                    passengers=passengers,
                    include_no_seats=True,
                    include_waiting_list=True,
                )
                return [serialize_ktx_train(t) for t in trains]
        return await asyncio.to_thread(_do)

    async def search_raw(self, dep: str, arr: str, date: str, time: str,
                         passengers: list):
        """Returns raw train objects (for macro use)."""
        def _do():
            if self.rail_type == "SRT":
                return self._client.search_train(
                    dep=dep, arr=arr, date=date, time=time,
                    passengers=passengers, available_only=False,
                )
            else:
                return self._client.search_train(
                    dep=dep, arr=arr, date=date, time=time,
                    passengers=passengers,
                    include_no_seats=True,
                    include_waiting_list=True,
                )
        return await asyncio.to_thread(_do)

    async def get_reservations(self) -> list[dict]:
        def _do():
            results = []
            if self.rail_type == "SRT":
                reservations = self._client.get_reservations()
                for r in reservations:
                    if hasattr(r, "paid") and r.paid:
                        r.is_ticket = True
                    else:
                        r.is_ticket = False
                    results.append(serialize_srt_reservation(r))
            else:
                reservations = self._client.reservations()
                tickets = self._client.tickets()
                for t in tickets:
                    results.append(serialize_ktx_ticket(t))
                for r in reservations:
                    r.is_ticket = False
                    results.append(serialize_ktx_reservation(r))
            return results
        return await asyncio.to_thread(_do)

    async def cancel(self, reservation_number: str) -> bool:
        def _do():
            if self.rail_type == "SRT":
                reservations = self._client.get_reservations()
                for r in reservations:
                    if r.reservation_number == reservation_number:
                        if hasattr(r, "paid") and r.paid:
                            return self._client.refund(r)
                        return self._client.cancel(r)
                raise ValueError(f"Reservation {reservation_number} not found")
            else:
                reservations = self._client.reservations()
                for r in reservations:
                    if r.rsv_id == reservation_number:
                        return self._client.cancel(r)
                tickets = self._client.tickets()
                for t in tickets:
                    if t.pnr_no == reservation_number:
                        return self._client.refund(t)
                raise ValueError(f"Reservation {reservation_number} not found")
        return await asyncio.to_thread(_do)

    async def pay(self, reservation_number: str, card_info: dict) -> bool:
        def _do():
            birthday = card_info.get("birthday", "")
            card_type = "J" if len(birthday) == 6 else "S"

            if self.rail_type == "SRT":
                reservations = self._client.get_reservations()
                for r in reservations:
                    if r.reservation_number == reservation_number:
                        return self._client.pay_with_card(
                            r,
                            card_info["number"],
                            card_info["password"],
                            birthday,
                            card_info["expire"],
                            0,
                            card_type,
                        )
                raise ValueError(f"Reservation {reservation_number} not found")
            else:
                reservations = self._client.reservations()
                for r in reservations:
                    if r.rsv_id == reservation_number:
                        return self._client.pay_with_card(
                            r,
                            card_info["number"],
                            card_info["password"],
                            birthday,
                            card_info["expire"],
                            0,
                            card_type,
                        )
                raise ValueError(f"Reservation {reservation_number} not found")
        return await asyncio.to_thread(_do)
