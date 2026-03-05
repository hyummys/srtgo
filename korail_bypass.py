"""
Korail MACRO 우회 래퍼 + KTX 전용 CLI

srtgo/ktx.py의 Korail 클래스를 상속하여
- Base URL: smart.letskorail.com → www.korail.com/ebizweb
- 로그인: AES(code.do) → RSA(pblk 공개키)
나머지 기능(열차조회, 예매, 취소, 결제 등)은 그대로 동작.

실행: python korail_bypass.py
"""

import asyncio
import json
import re
import time
from datetime import datetime, timedelta
from json.decoder import JSONDecodeError
from random import gammavariate

from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_v1_5

try:
    from curl_cffi.requests.exceptions import ConnectionError as CurlConnectionError
except ImportError:
    from requests.exceptions import ConnectionError as CurlConnectionError

import srtgo.ktx as ktx
from srtgo.ktx import (
    Korail,
    KorailError,
    ReserveOption,
    TrainType,
    AdultPassenger,
    ChildPassenger,
    SeniorPassenger,
    Disability1To3Passenger,
    Disability4To6Passenger,
)

# ── KorailBypass 클래스 ─────────────────────────────────────────────

_ORIG_MOBILE = ktx.KORAIL_MOBILE
KORAIL_MOBILE_NEW = "https://www.korail.com/ebizweb/classes/com.korail.mobile"
PBLK_URL = "https://www.korail.com/ebizweb/tour/mypage/pwd_action/pblk"

_PATCHED_ENDPOINTS = {
    k: v.replace(_ORIG_MOBILE, KORAIL_MOBILE_NEW)
    for k, v in ktx.API_ENDPOINTS.items()
}

MAX_LOGIN_RETRIES = 5


class KorailBypass(Korail):
    """MACRO 우회 Korail 클라이언트. Korail과 동일한 인터페이스."""

    def __init__(self, korail_id, korail_pw, auto_login=True, verbose=False):
        self._patch_endpoints()
        super().__init__(korail_id, korail_pw, auto_login=False, verbose=verbose)
        self._session.headers["Host"] = "www.korail.com"
        if auto_login:
            self.login(korail_id, korail_pw)

    @staticmethod
    def _patch_endpoints():
        ktx.API_ENDPOINTS.update(_PATCHED_ENDPOINTS)

    def _get_rsa_key(self):
        r = self._session.get(PBLK_URL)
        j = json.loads(r.text)
        if j.get("strResult") != "SUCC" or not j.get("publicKeyModulus"):
            raise KorailError("RSA 공개키 획득 실패")
        modulus = int(j["publicKeyModulus"], 16)
        exponent = int(j["publicKeyExponent"], 16)
        return RSA.construct((modulus, exponent)), j["keyname"]

    def _enc_password_rsa(self, password):
        rsa_key, keyname = self._get_rsa_key()
        cipher = PKCS1_v1_5.new(rsa_key)
        return keyname, cipher.encrypt(password.encode("utf-8")).hex()

    def login(self, korail_id=None, korail_pw=None):
        if korail_id:
            self.korail_id = korail_id
        if korail_pw:
            self.korail_pw = korail_pw

        txt_input_flg = (
            "5" if re.match(r"[^@]+@[^@]+\.[^@]+", self.korail_id)
            else "4" if re.match(r"(\d{3})-(\d{3,4})-(\d{4})", self.korail_id)
            else "2"
        )

        for attempt in range(MAX_LOGIN_RETRIES):
            keyname, enc_pw = self._enc_password_rsa(self.korail_pw)
            data = {
                "Device": self._device,
                "Version": self._version,
                "Key": self._key,
                "txtMemberNo": self.korail_id,
                "txtPwd": enc_pw,
                "txtInputFlg": txt_input_flg,
                "idx": keyname,
            }
            r = self._session.post(ktx.API_ENDPOINTS["login"], data=data)
            self._log(r.text)
            j = json.loads(r.text)

            if j.get("strResult") == "SUCC" and j.get("strMbCrdNo"):
                self.membership_number = j["strMbCrdNo"]
                self.name = j["strCustNm"]
                self.email = j.get("strEmailAdr", "")
                self.phone_number = j.get("strCpNo", "")
                print(f"로그인 성공: {self.name} (멤버십번호: {self.membership_number}, 전화번호: {self.phone_number})")
                self.logined = True
                return True

            h_msg_cd = j.get("h_msg_cd", "")
            if "MACRO" in h_msg_cd:
                raise KorailError(j.get("h_msg_txt", "MACRO ERROR"), h_msg_cd)
            if h_msg_cd == "WRC000390":
                raise KorailError(j.get("h_msg_txt", "계정 잠김"), h_msg_cd)
            self._log(f"로그인 시도 {attempt + 1}/{MAX_LOGIN_RETRIES} 실패: {h_msg_cd}")

        self.logined = False
        raise KorailError("로그인 실패 (RSA 키 로테이션 재시도 초과)")


# ── KTX CLI ─────────────────────────────────────────────────────────

if __name__ == "__main__":
    import click
    import inquirer
    import keyring
    import telegram
    from termcolor import colored

    RAIL_TYPE = "KTX"

    STATIONS = [
        "서울", "용산", "영등포", "광명", "수원", "천안아산", "오송", "대전",
        "서대전", "김천구미", "동대구", "경주", "포항", "밀양", "구포", "부산",
        "울산(통도사)", "마산", "창원중앙", "경산", "논산", "익산", "정읍",
        "광주송정", "목포", "전주", "순천", "여수EXPO", "청량리", "강릉", "행신", "정동진", "진영",
    ]
    DEFAULT_STATIONS = ["서울", "대전", "동대구", "부산"]
    WAITING_BAR = ["|", "/", "-", "\\"]
    RESERVE_INTERVAL_SHAPE = 4
    RESERVE_INTERVAL_SCALE = 0.25
    RESERVE_INTERVAL_MIN = 0.25

    PASSENGER_CLASSES = {
        "adult": AdultPassenger,
        "child": ChildPassenger,
        "senior": SeniorPassenger,
        "disability1to3": Disability1To3Passenger,
        "disability4to6": Disability4To6Passenger,
    }
    PASSENGER_LABELS = {
        AdultPassenger: "어른/청소년",
        ChildPassenger: "어린이",
        SeniorPassenger: "경로우대",
        Disability1To3Passenger: "1~3급 장애인",
        Disability4To6Passenger: "4~6급 장애인",
    }

    # ── helpers ──

    def _sleep():
        time.sleep(gammavariate(RESERVE_INTERVAL_SHAPE, RESERVE_INTERVAL_SCALE) + RESERVE_INTERVAL_MIN)

    def get_telegram():
        token = keyring.get_password("telegram", "token")
        chat_id = keyring.get_password("telegram", "chat_id")
        async def tgprintf(text):
            if token and chat_id:
                bot = telegram.Bot(token=token)
                async with bot:
                    await bot.send_message(chat_id=chat_id, text=text)
        return tgprintf

    def get_station_choices():
        saved = keyring.get_password(RAIL_TYPE, "station")
        if saved:
            return [s.strip() for s in saved.split(",")]
        return DEFAULT_STATIONS

    def get_options():
        opts = keyring.get_password(RAIL_TYPE, "options") or ""
        return opts.split(",") if opts else []

    def _handle_error(ex, msg=None):
        msg = msg or f"\nException: {ex}, Type: {type(ex)}, Message: {ex.msg if hasattr(ex, 'msg') else str(ex)}"
        print(msg)
        tgprintf = get_telegram()
        asyncio.run(tgprintf(msg))
        return inquirer.confirm(message="계속할까요", default=True)

    def _is_seat_available(train, seat_type):
        if not train.has_seat():
            return train.has_waiting_list()
        if seat_type in [ReserveOption.GENERAL_FIRST, ReserveOption.SPECIAL_FIRST]:
            return train.has_seat()
        if seat_type == ReserveOption.GENERAL_ONLY:
            return train.has_general_seat()
        return train.has_special_seat()

    def do_login(debug=False):
        uid = keyring.get_password(RAIL_TYPE, "id")
        pw = keyring.get_password(RAIL_TYPE, "pass")
        if not uid or not pw:
            set_login(debug)
            uid = keyring.get_password(RAIL_TYPE, "id")
            pw = keyring.get_password(RAIL_TYPE, "pass")
        return KorailBypass(uid, pw, verbose=debug)

    # ── menu actions ──

    def set_login(debug=False):
        creds = {
            "id": keyring.get_password(RAIL_TYPE, "id") or "",
            "pass": keyring.get_password(RAIL_TYPE, "pass") or "",
        }
        info = inquirer.prompt([
            inquirer.Text("id", message="KTX 계정 아이디 (멤버십 번호, 이메일, 전화번호)", default=creds["id"]),
            inquirer.Password("pass", message="KTX 계정 패스워드", default=creds["pass"]),
        ])
        if not info:
            return False
        try:
            KorailBypass(info["id"], info["pass"], verbose=debug)
            keyring.set_password(RAIL_TYPE, "id", info["id"])
            keyring.set_password(RAIL_TYPE, "pass", info["pass"])
            keyring.set_password(RAIL_TYPE, "ok", "1")
            return True
        except KorailError as err:
            print(err)
            return False

    def set_station():
        saved = get_station_choices()
        result = inquirer.prompt([
            inquirer.Checkbox(
                "stations",
                message="역 선택 (Space: 선택, Enter: 완료, Ctrl-A: 전체선택, Ctrl-C: 취소)",
                choices=STATIONS, default=saved,
            )
        ])
        if not result or not result["stations"]:
            print("선택된 역이 없습니다.")
            return
        keyring.set_password(RAIL_TYPE, "station", ",".join(result["stations"]))
        print(f"선택된 역: {','.join(result['stations'])}")

    def edit_station():
        saved = keyring.get_password(RAIL_TYPE, "station") or ""
        result = inquirer.prompt([
            inquirer.Text("stations", message="역 수정 (예: 서울,대전,동대구)", default=saved)
        ])
        if not result or not result["stations"]:
            print("선택된 역이 없습니다.")
            return
        selected = [s.strip() for s in result["stations"].split(",")]
        hangul = re.compile("[가-힣]+")
        for s in selected:
            if not hangul.search(s):
                print(f"'{s}'는 잘못된 입력입니다. 기본 역으로 설정합니다.")
                selected = DEFAULT_STATIONS
                break
        keyring.set_password(RAIL_TYPE, "station", ",".join(selected))
        print(f"선택된 역: {','.join(selected)}")

    def set_telegram_config():
        token = keyring.get_password("telegram", "token") or ""
        chat_id = keyring.get_password("telegram", "chat_id") or ""
        info = inquirer.prompt([
            inquirer.Text("token", message="텔레그램 token", default=token),
            inquirer.Text("chat_id", message="텔레그램 chat_id", default=chat_id),
        ])
        if not info:
            return
        try:
            keyring.set_password("telegram", "ok", "1")
            keyring.set_password("telegram", "token", info["token"])
            keyring.set_password("telegram", "chat_id", info["chat_id"])
            tgprintf = get_telegram()
            asyncio.run(tgprintf("[KTX] 텔레그램 설정 완료"))
        except Exception as err:
            print(err)
            keyring.delete_password("telegram", "ok")

    def set_card():
        card = {k: keyring.get_password("card", k) or "" for k in ("number", "password", "birthday", "expire")}
        info = inquirer.prompt([
            inquirer.Password("number", message="신용카드 번호 (하이픈 제외)", default=card["number"]),
            inquirer.Password("password", message="카드 비밀번호 앞 2자리", default=card["password"]),
            inquirer.Password("birthday", message="생년월일 (YYMMDD) / 사업자등록번호", default=card["birthday"]),
            inquirer.Password("expire", message="카드 유효기간 (YYMM)", default=card["expire"]),
        ])
        if info:
            for k, v in info.items():
                keyring.set_password("card", k, v)
            keyring.set_password("card", "ok", "1")

    def set_options():
        current = get_options()
        result = inquirer.prompt([
            inquirer.Checkbox(
                "options", message="예매 옵션 선택 (Space: 선택, Enter: 완료)",
                choices=[("어린이", "child"), ("경로우대", "senior"), ("중증장애인", "disability1to3"),
                         ("경증장애인", "disability4to6"), ("KTX만", "ktx")],
                default=current,
            )
        ])
        if result is not None:
            keyring.set_password(RAIL_TYPE, "options", ",".join(result.get("options", [])))

    def pay_card(rail, reservation):
        if keyring.get_password("card", "ok"):
            birthday = keyring.get_password("card", "birthday")
            return rail.pay_with_card(
                reservation,
                keyring.get_password("card", "number"),
                keyring.get_password("card", "password"),
                birthday,
                keyring.get_password("card", "expire"),
                0, "J" if len(birthday) == 6 else "S",
            )
        return False

    # ── 예매 시작 ──

    def reserve(debug=False):
        rail = do_login(debug)
        now = datetime.now() + timedelta(minutes=10)
        today = now.strftime("%Y%m%d")
        this_time = now.strftime("%H%M%S")
        options = get_options()

        defaults = {
            "departure": keyring.get_password(RAIL_TYPE, "departure") or "서울",
            "arrival": keyring.get_password(RAIL_TYPE, "arrival") or "동대구",
            "date": keyring.get_password(RAIL_TYPE, "date") or today,
            "time": keyring.get_password(RAIL_TYPE, "time") or "120000",
            "adult": int(keyring.get_password(RAIL_TYPE, "adult") or 1),
            "child": int(keyring.get_password(RAIL_TYPE, "child") or 0),
            "senior": int(keyring.get_password(RAIL_TYPE, "senior") or 0),
            "disability1to3": int(keyring.get_password(RAIL_TYPE, "disability1to3") or 0),
            "disability4to6": int(keyring.get_password(RAIL_TYPE, "disability4to6") or 0),
        }
        if defaults["departure"] == defaults["arrival"]:
            defaults["arrival"] = "동대구" if defaults["departure"] == "서울" else "서울"

        station_choices = get_station_choices()
        max_days = 31 if now.hour >= 7 else 30
        date_choices = [((now + timedelta(days=i)).strftime("%Y/%m/%d %a"), (now + timedelta(days=i)).strftime("%Y%m%d")) for i in range(max_days + 1)]
        time_choices = [(f"{h:02d}시", f"{h:02d}0000") for h in range(24)]

        q = [
            inquirer.List("departure", message="출발역", choices=station_choices, default=defaults["departure"]),
            inquirer.List("arrival", message="도착역", choices=station_choices, default=defaults["arrival"]),
            inquirer.List("date", message="출발 날짜", choices=date_choices, default=defaults["date"]),
            inquirer.List("time", message="출발 시각", choices=time_choices, default=defaults["time"]),
            inquirer.List("adult", message="성인 승객수", choices=range(10), default=defaults["adult"]),
        ]
        passenger_types = {"child": "어린이", "senior": "경로우대", "disability1to3": "1~3급 장애인", "disability4to6": "4~6급 장애인"}
        for key, label in passenger_types.items():
            if key in options:
                q.append(inquirer.List(key, message=f"{label} 승객수", choices=range(10), default=defaults[key]))

        info = inquirer.prompt(q)
        if not info:
            print(colored("취소되었습니다", "red"))
            return
        if info["departure"] == info["arrival"]:
            print(colored("출발역과 도착역이 같습니다", "red"))
            return

        for k, v in info.items():
            keyring.set_password(RAIL_TYPE, k, str(v))

        if info["date"] == today and int(info["time"]) < int(this_time):
            info["time"] = this_time

        passengers = []
        total = 0
        for key, cls in PASSENGER_CLASSES.items():
            if key in info and info[key] > 0:
                passengers.append(cls(info[key]))
                total += info[key]
        if not passengers:
            print(colored("승객수는 0이 될 수 없습니다", "red"))
            return
        if total >= 10:
            print(colored("승객수는 10명을 초과할 수 없습니다", "red"))
            return

        print(" ".join(f"{PASSENGER_LABELS[type(p)]} {p.count}명" for p in passengers))

        search_params = {
            "dep": info["departure"], "arr": info["arrival"],
            "date": info["date"], "time": info["time"],
            "passengers": [AdultPassenger(total)],
            "include_no_seats": True,
            **({} if "ktx" not in options else {"train_type": TrainType.KTX}),
        }

        trains = rail.search_train(**search_params)
        if not trains:
            print(colored("예약 가능한 열차가 없습니다", "red"))
            return

        def deco(t):
            return str(t).replace("가능", colored("가능", "green"))

        choice = inquirer.prompt([
            inquirer.Checkbox("trains", message="예약할 열차 선택 (Space: 선택, Enter: 완료)",
                              choices=[(deco(t), i) for i, t in enumerate(trains)])
        ])
        if not choice or not choice["trains"]:
            print(colored("선택한 열차가 없습니다", "red"))
            return

        opts = inquirer.prompt([
            inquirer.List("type", message="좌석 유형", choices=[
                ("일반실 우선", ReserveOption.GENERAL_FIRST),
                ("일반실만", ReserveOption.GENERAL_ONLY),
                ("특실 우선", ReserveOption.SPECIAL_FIRST),
                ("특실만", ReserveOption.SPECIAL_ONLY),
            ]),
            inquirer.Confirm("pay", message="예매 시 카드 결제", default=False),
        ])
        if opts is None:
            return

        def _reserve(train):
            rsv = rail.reserve(train, passengers=passengers, option=opts["type"])
            msg = f"{rsv}"
            if hasattr(rsv, "tickets") and rsv.tickets:
                msg += "\n" + "\n".join(map(str, rsv.tickets))
            print(colored(f"\n\n🎫 🎉 예매 성공!!! 🎉 🎫\n{msg}\n", "red", "on_green"))
            if opts["pay"] and not rsv.is_waiting and pay_card(rail, rsv):
                print(colored("\n\n💳 ✨ 결제 성공!!! ✨ 💳\n\n", "green", "on_red"))
                msg += "\n결제 완료"
            asyncio.run(get_telegram()(msg))

        i_try = 0
        start_time = time.time()
        while True:
            try:
                i_try += 1
                elapsed = int(time.time() - start_time)
                h, m, s = elapsed // 3600, elapsed % 3600 // 60, elapsed % 60
                print(f"\r예매 대기 중... {WAITING_BAR[i_try & 3]} {i_try:4d} ({h:02d}:{m:02d}:{s:02d}) ", end="", flush=True)

                trains = rail.search_train(**search_params)
                for i in choice["trains"]:
                    if _is_seat_available(trains[i], opts["type"]):
                        _reserve(trains[i])
                        return
                _sleep()

            except KorailError as ex:
                msg = ex.msg
                if "Need to Login" in msg:
                    rail = do_login(debug)
                    if not rail.logined and not _handle_error(ex):
                        return
                elif not any(err in msg for err in ("Sold out", "잔여석없음", "예약대기자한도수초과")):
                    if not _handle_error(ex):
                        return
                _sleep()
            except JSONDecodeError:
                _sleep()
                rail = do_login(debug)
            except CurlConnectionError as ex:
                if not _handle_error(ex, "연결이 끊겼습니다"):
                    return
                rail = do_login(debug)
            except Exception as ex:
                if not _handle_error(ex):
                    return
                rail = do_login(debug)

    # ── 예매 확인/결제/취소 ──

    def check_reservation(debug=False):
        rail = do_login(debug)
        while True:
            reservations = rail.reservations()
            tickets = rail.tickets()

            all_rsv = []
            for t in tickets:
                t.is_ticket = True
                all_rsv.append(t)
            for r in reservations:
                r.is_ticket = hasattr(r, "paid") and r.paid
                all_rsv.append(r)

            if not all_rsv:
                print(colored("예약 내역이 없습니다", "red"))
                return

            items = [(str(r), i) for i, r in enumerate(all_rsv)]
            items += [("텔레그램으로 예매 정보 전송", -2), ("돌아가기", -1)]
            choice = inquirer.list_input(message="예약 선택 (Enter: 결정)", choices=items)

            if choice in (None, -1):
                return
            if choice == -2:
                out = ["[ 예매 내역 ]"] + [f"🚅{r}" for r in all_rsv]
                asyncio.run(get_telegram()("\n".join(out)))
                return

            rsv = all_rsv[choice]
            if not rsv.is_ticket and not rsv.is_waiting:
                answer = inquirer.list_input(
                    message=f"결제 대기 승차권: {rsv}",
                    choices=[("결제하기", 1), ("취소하기", 2)],
                )
                if answer == 1:
                    if pay_card(rail, rsv):
                        print(colored("\n💳 ✨ 결제 성공!!! ✨ 💳\n", "green", "on_red"))
                elif answer == 2:
                    rail.cancel(rsv)
                return

            if inquirer.confirm(message=colored("정말 취소하시겠습니까", "red")):
                try:
                    if rsv.is_ticket:
                        rail.refund(rsv)
                    else:
                        rail.cancel(rsv)
                except Exception as err:
                    print(err)
                return

    # ── 메인 메뉴 ──

    @click.command()
    @click.option("--debug", is_flag=True, help="Debug mode")
    def main(debug=False):
        MENU = [
            ("예매 시작", 1),
            ("예매 확인/결제/취소", 2),
            ("로그인 설정", 3),
            ("텔레그램 설정", 4),
            ("카드 설정", 5),
            ("역 설정", 6),
            ("역 직접 수정", 7),
            ("예매 옵션 설정", 8),
            ("나가기", -1),
        ]

        while True:
            choice = inquirer.list_input(
                message="KTX 메뉴 선택 (↕:이동, Enter: 선택)", choices=MENU
            )
            if choice == -1:
                break

            actions = {
                1: lambda: reserve(debug),
                2: lambda: check_reservation(debug),
                3: lambda: set_login(debug),
                4: set_telegram_config,
                5: set_card,
                6: set_station,
                7: edit_station,
                8: set_options,
            }
            action = actions.get(choice)
            if action:
                action()

    main()
