"""
Background macro task lifecycle manager.
Each macro runs in its own thread (SRT/Korail clients are synchronous).
"""
from __future__ import annotations

import threading
import asyncio
import time
import uuid
import json
from random import gammavariate
from datetime import datetime

from .rail_bridge import (
    RailBridge,
    SRT,
    Korail,
    is_seat_available,
    get_seat_option,
    build_passengers,
    serialize_srt_reservation,
    serialize_ktx_reservation,
    SRTError,
    SRTNetFunnelError,
    SRTLoginError,
    KorailError,
)

RESERVE_INTERVAL_SHAPE = 4
RESERVE_INTERVAL_SCALE = 0.25
RESERVE_INTERVAL_MIN = 0.25


class MacroTask:
    def __init__(
        self,
        task_id: str,
        rail_type: str,
        search_params: dict,
        train_indices: list[int],
        passengers: list,
        seat_type_str: str,
        auto_pay: bool,
        card_info: dict | None,
        on_event,
        loop: asyncio.AbstractEventLoop,
        user_id: str = "",
        password: str = "",
        app_user_id: int = 0,
    ):
        self.task_id = task_id
        self.rail_type = rail_type
        self.app_user_id = app_user_id  # Web app user ID for ownership
        self._client = None  # Created in _run() on the background thread
        self._search_params = search_params
        self._train_indices = train_indices
        self._passengers = passengers
        self._seat_type_str = seat_type_str
        self._auto_pay = auto_pay
        self._card_info = card_info
        self._on_event = on_event
        self._loop = loop
        self._user_id = user_id
        self._password = password
        self._cancel_event = threading.Event()
        self._thread: threading.Thread | None = None
        self.status = "pending"
        self.attempts = 0
        self.start_time: float | None = None
        self.result_text = ""

        # Search params for display
        self.departure = search_params.get("dep", "")
        self.arrival = search_params.get("arr", "")
        self.date = search_params.get("date", "")
        self.time_str = search_params.get("time", "")

    def start(self):
        self._thread = threading.Thread(target=self._run, daemon=True)
        self.status = "running"
        self.start_time = time.time()
        self._thread.start()

    def cancel(self):
        self._cancel_event.set()
        self.status = "cancelled"

    def _emit(self, data: dict):
        asyncio.run_coroutine_threadsafe(
            self._on_event(self.task_id, data), self._loop
        )

    def _sleep(self):
        interval = gammavariate(RESERVE_INTERVAL_SHAPE, RESERVE_INTERVAL_SCALE) + RESERVE_INTERVAL_MIN
        # Check cancel every 0.1s during sleep
        slept = 0.0
        while slept < interval and not self._cancel_event.is_set():
            step = min(0.1, interval - slept)
            time.sleep(step)
            slept += step

    def _relogin(self):
        try:
            if self.rail_type == "SRT":
                self._client = SRT(self._user_id, self._password)
            else:
                client = Korail(self._user_id, self._password)
                if not client.logined:
                    raise KorailError("Re-login failed: credentials invalid")
                self._client = client
            self._emit({"type": "relogin", "message": "Re-login successful"})
        except Exception as e:
            self._emit({"type": "error", "message": f"Re-login failed: {e}", "error_type": "login"})

    def _run(self):
        # Login on this thread (same thread that will search/reserve)
        try:
            self._relogin()
            if self._client is None:
                self.status = "failed"
                self._emit({"type": "error", "message": "Login failed", "error_type": "login"})
                return
        except Exception as e:
            self.status = "failed"
            self._emit({"type": "error", "message": f"Login failed: {e}", "error_type": "login"})
            return

        seat_option = get_seat_option(self.rail_type, self._seat_type_str)

        while not self._cancel_event.is_set():
            self.attempts += 1
            elapsed = time.time() - self.start_time

            self._emit({
                "type": "tick",
                "attempts": self.attempts,
                "elapsed": round(elapsed, 1),
                "status": "searching",
            })

            try:
                # Search trains
                if self.rail_type == "SRT":
                    trains = self._client.search_train(
                        dep=self._search_params["dep"],
                        arr=self._search_params["arr"],
                        date=self._search_params["date"],
                        time=self._search_params["time"],
                        passengers=self._passengers,
                        available_only=False,
                    )
                else:
                    trains = self._client.search_train(
                        dep=self._search_params["dep"],
                        arr=self._search_params["arr"],
                        date=self._search_params["date"],
                        time=self._search_params["time"],
                        passengers=self._passengers,
                        include_no_seats=True,
                        include_waiting_list=True,
                    )

                # Check each selected train
                for idx in self._train_indices:
                    if self._cancel_event.is_set():
                        break
                    if idx >= len(trains):
                        continue
                    if is_seat_available(trains[idx], self._seat_type_str, self.rail_type):
                        # Reserve
                        reservation = self._client.reserve(
                            trains[idx], self._passengers, seat_option
                        )

                        # Auto-pay if enabled
                        if self._auto_pay and self._card_info:
                            try:
                                birthday = self._card_info.get("birthday", "")
                                card_type = "J" if len(birthday) == 6 else "S"
                                self._client.pay_with_card(
                                    reservation,
                                    self._card_info["number"],
                                    self._card_info["password"],
                                    birthday,
                                    self._card_info["expire"],
                                    0,
                                    card_type,
                                )
                            except Exception as pay_err:
                                self._emit({
                                    "type": "error",
                                    "message": f"Auto-pay failed: {pay_err}",
                                    "error_type": "payment",
                                })

                        # Serialize result
                        if self.rail_type == "SRT":
                            rsv_data = serialize_srt_reservation(reservation)
                        else:
                            rsv_data = serialize_ktx_reservation(reservation)

                        self.status = "success"
                        self.result_text = json.dumps(rsv_data, ensure_ascii=False)
                        elapsed = time.time() - self.start_time

                        self._emit({
                            "type": "success",
                            "reservation": rsv_data,
                            "attempts": self.attempts,
                            "elapsed": round(elapsed, 1),
                        })

                        # Send telegram notification
                        self._send_telegram(rsv_data)
                        return

                # No seat available, sleep and retry
                self._sleep()

            except (SRTNetFunnelError,) as ex:
                self._emit({
                    "type": "error",
                    "message": f"NetFunnel: {ex}",
                    "error_type": "netfunnel",
                })
                if hasattr(self._client, 'clear'):
                    self._client.clear()
                self._sleep()

            except (SRTLoginError,) as ex:
                self._emit({
                    "type": "error",
                    "message": f"Login error: {ex}",
                    "error_type": "login",
                })
                self._relogin()
                self._sleep()

            except SRTError as ex:
                msg = str(ex)
                if "로그인" in msg or "정상적인 경로" in msg:
                    self._relogin()
                elif "잔여석없음" in msg or "사용자가 많아" in msg or "마감" in msg:
                    pass  # Expected, keep retrying
                else:
                    self._emit({
                        "type": "error",
                        "message": msg,
                        "error_type": "srt",
                    })
                self._sleep()

            except KorailError as ex:
                msg = str(ex)
                if "로그인" in msg or "Login" in msg:
                    self._relogin()
                elif any(kw in msg for kw in ("매진", "잔여석", "마감")):
                    pass  # Expected
                else:
                    self._emit({
                        "type": "error",
                        "message": msg,
                        "error_type": "korail",
                    })
                self._sleep()

            except (ConnectionError, json.JSONDecodeError) as ex:
                self._emit({
                    "type": "error",
                    "message": f"Connection error: {ex}",
                    "error_type": "connection",
                })
                self._relogin()
                self._sleep()

            except Exception as ex:
                self._emit({
                    "type": "error",
                    "message": f"Unexpected: {ex}",
                    "error_type": "unknown",
                })
                self._sleep()

        # Cancelled
        self.status = "cancelled"
        self._emit({"type": "cancelled"})

    def _send_telegram(self, rsv_data: dict):
        try:
            async def _do():
                from ..database import get_all_settings
                tg = await get_all_settings("telegram", user_id=self.app_user_id)
                if tg.get("ok") != "1":
                    return
                import telegram
                bot = telegram.Bot(token=tg["token"])
                msg = (
                    f"SRTgo 예약 성공!\n"
                    f"{rsv_data.get('train_name', '')} {rsv_data.get('train_number', '')}\n"
                    f"{rsv_data.get('dep_station', '')} -> {rsv_data.get('arr_station', '')}\n"
                    f"{rsv_data.get('dep_date', '')} {rsv_data.get('dep_time', '')}\n"
                    f"비용: {rsv_data.get('total_cost', '')}원"
                )
                await bot.send_message(chat_id=tg["chat_id"], text=msg)

            asyncio.run_coroutine_threadsafe(_do(), self._loop)
        except Exception:
            pass  # Non-critical


class TaskManager:
    def __init__(self):
        self._tasks: dict[str, MacroTask] = {}

    def create_task(self, **kwargs) -> MacroTask:
        task_id = str(uuid.uuid4())
        task = MacroTask(task_id=task_id, **kwargs)
        self._tasks[task_id] = task
        return task

    def start_task(self, task_id: str):
        if task_id in self._tasks:
            self._tasks[task_id].start()

    def cancel_task(self, task_id: str):
        if task_id in self._tasks:
            self._tasks[task_id].cancel()

    def get_task(self, task_id: str) -> MacroTask | None:
        return self._tasks.get(task_id)

    def get_status(self, task_id: str) -> dict | None:
        task = self._tasks.get(task_id)
        if not task:
            return None
        elapsed = (time.time() - task.start_time) if task.start_time else 0
        return {
            "task_id": task.task_id,
            "rail_type": task.rail_type,
            "departure": task.departure,
            "arrival": task.arrival,
            "date": task.date,
            "time": task.time_str,
            "status": task.status,
            "attempts": task.attempts,
            "elapsed": round(elapsed, 1),
            "app_user_id": task.app_user_id,
        }

    def list_active(self, app_user_id: int | None = None) -> list[dict]:
        results = []
        for task_id, task in self._tasks.items():
            if task.status in ("pending", "running"):
                if app_user_id is not None and task.app_user_id != app_user_id:
                    continue
                results.append(self.get_status(task_id))
        return results

    def list_all(self) -> list[dict]:
        return [self.get_status(tid) for tid in self._tasks]

    def cleanup_finished(self):
        finished = [
            tid for tid, task in self._tasks.items()
            if task.status in ("success", "failed", "cancelled")
        ]
        for tid in finished:
            del self._tasks[tid]
