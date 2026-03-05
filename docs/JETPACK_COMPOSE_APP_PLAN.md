# SRTgo Android App 기획서 (Jetpack Compose)

> Python 기반 SRT/KTX 예매 자동화 엔진을 Android 네이티브 앱으로 포팅하는 프로젝트 기획서

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [현재 Python 코드베이스 분석](#2-현재-python-코드베이스-분석)
3. [앱 아키텍처 설계](#3-앱-아키텍처-설계)
4. [모듈 구조](#4-모듈-구조)
5. [Kotlin 데이터 모델](#5-kotlin-데이터-모델)
6. [API 통신 레이어](#6-api-통신-레이어)
7. [화면 설계](#7-화면-설계)
8. [핵심 기술 과제 및 해결 방안](#8-핵심-기술-과제-및-해결-방안)
9. [작업 계획 (Phase별)](#9-작업-계획-phase별)
10. [의존성 및 라이브러리](#10-의존성-및-라이브러리)
11. [리스크 및 대응 방안](#11-리스크-및-대응-방안)

---

## 1. 프로젝트 개요

### 1.1 목표

현재 **Python 백엔드 + Next.js 프론트엔드** 구조의 SRT/KTX 예매 자동화 서비스를 **서버 없이 독립 실행 가능한 Android 네이티브 앱**으로 전환한다.

### 1.2 핵심 결정 사항

| 항목 | 결정 |
|------|------|
| 프레임워크 | Jetpack Compose (Android) |
| 언어 | Kotlin |
| HTTP 클라이언트 | OkHttp (Android 기기에서 직접 요청 → TLS 핑거프린트 위장 불필요) |
| 로컬 DB | Room (SQLite) |
| 아키텍처 | MVVM + Clean Architecture |
| DI | Hilt (Dagger) |
| 비밀번호 저장 | Android Keystore + EncryptedSharedPreferences |
| 암호화 | javax.crypto (AES for KTX 비밀번호) |

### 1.3 서버 없이 가능한 이유

Python 코드가 하는 일은 전부 **HTTP 요청 → 응답 파싱**이다. 현재 `curl_cffi`로 Android 앱을 흉내내고 있는데, 실제 Android 앱에서 요청하면 TLS 핑거프린트가 자연스럽게 Android로 인식되므로 위장이 필요 없다.

### 1.4 포팅 범위

| Python 모듈 | LOC | 포팅 대상 |
|------------|-----|-----------|
| `srtgo/srt.py` | ~1261 | ✅ 전체 (SRT API 클라이언트) |
| `srtgo/ktx.py` | ~948 | ✅ 전체 (KTX API 클라이언트) |
| `srtgo/srtgo.py` | ~907 | ✅ 매크로 루프 로직만 (CLI 제외) |
| `srtgo/netfunnel.py` | 포함 | ✅ 전체 (대기열 시스템) |
| `web/backend/` | 전체 | ❌ 불필요 (FastAPI 서버 로직) |
| `web/frontend/` | 전체 | ❌ 불필요 (Next.js → Compose로 대체) |

---

## 2. 현재 Python 코드베이스 분석

### 2.1 전체 구조

```
srtgo/
├── srt.py          → SRT 클래스 (SRT 열차 API)
├── ktx.py          → Korail 클래스 (KTX 열차 API)
├── srtgo.py        → CLI 오케스트레이션 + 매크로 루프
└── (netfunnel)     → srt.py, ktx.py 내부에 NetFunnelHelper 클래스
```

### 2.2 SRT API 분석

#### 서버 정보
- **Base URL**: `https://app.srail.or.kr:443`
- **TLS 위장**: `curl_cffi` → `impersonate="chrome"` (앱에서는 불필요)
- **User-Agent**: `Mozilla/5.0 (Linux; Android 15; SM-S912N ...) SRT-APP-Android V.2.0.38`
- **Content-Type**: `application/x-www-form-urlencoded` (전체 API 동일)

#### API 엔드포인트

| 기능 | Method | URL | 비고 |
|------|--------|-----|------|
| 메인 | POST | `/main/main.do` | 세션 초기화 |
| 로그인 | POST | `/apb/selectListApb01080_n.do` | 이메일/전화/회원번호 |
| 로그아웃 | POST | `/login/loginOut.do` | |
| 열차 검색 | POST | `/ara/selectListAra10007_n.do` | NetFunnel 키 필수 |
| 예약 | POST | `/arc/selectListArc05013_n.do` | NetFunnel 키 필수 |
| 예약 조회 | POST | `/atc/selectListAtc14016_n.do` | |
| 티켓 상세 | POST | `/ard/selectListArd02019_n.do` | |
| 예약 취소 | POST | `/ard/selectListArd02045_n.do` | |
| 예약대기 설정 | POST | `/ata/selectListAta01135_n.do` | SMS/좌석변경 |
| 결제 | POST | `/ata/selectListAta09036_n.do` | 카드 정보 필요 |
| 환불 정보 | POST | `/atc/getListAtc14087.do` | |
| 환불 | POST | `/atc/selectListAtc02063_n.do` | |

#### 인증 방식
- **세션 기반**: 로그인 시 서버가 Set-Cookie → 이후 요청에 자동 포함
- **비밀번호**: 평문 전송 (HTTPS)
- **로그인 유형**: 회원번호(`1`), 이메일(`2`), 전화번호(`3`)

#### 역 코드 (32개)
```
수서=0551, 동탄=0552, 평택지제=0553, 경주=0508, 곡성=0049,
공주=0514, 광주송정=0036, 구례구=0050, 김천(구미)=0507,
나주=0037, 남원=0048, 대전=0010, 동대구=0015, 마산=0059,
목포=0041, 밀양=0017, 부산=0020, 서대구=0506, 순천=0051,
여수EXPO=0053, 여천=0139, 오송=0297, 울산(통도사)=0509,
익산=0030, 전주=0045, 정읍=0033, 진영=0056, 진주=0063,
창원=0057, 창원중앙=0512, 천안아산=0502, 포항=0515
```

### 2.3 KTX API 분석

#### 서버 정보
- **Base URL**: `https://smart.letskorail.com:443`
- **TLS 위장**: `curl_cffi` → `impersonate="chrome131_android"`
- **User-Agent**: `Dalvik/2.1.0 (Linux; U; Android 14; SM-S912N Build/)`

#### API 엔드포인트

| 기능 | Method | URL | 비고 |
|------|--------|-----|------|
| 로그인 | POST | `/classes/com.korail.mobile.login.Login` | AES 암호화 필요 |
| 로그아웃 | POST | `/classes/com.korail.mobile.common.logout` | |
| 열차 검색 | GET | `/classes/com.korail.mobile.seatMovie.ScheduleView` | NetFunnel 키 필수 |
| 예약 | POST | `/classes/com.korail.mobile.certification.TicketReservation` | NetFunnel 키 필수 |
| 예약 취소 | POST | `/classes/com.korail.mobile.reservationCancel.ReservationCancelChk` | |
| 내 티켓 | POST | `/classes/com.korail.mobile.myTicket.MyTicketList` | |
| 결제 | POST | `/classes/com.korail.mobile.payment.ReservationPayment` | |
| 암호화 키 | POST | `/classes/com.korail.mobile.common.code.do` | 로그인 전 호출 |

#### KTX 비밀번호 암호화 (핵심 차이점)

```
1. POST /classes/com.korail.mobile.common.code.do  {code: "app.login.cphd"}
2. 응답: {idx: N, key: "hex_string"}
3. key에서 idx 위치의 32바이트 추출 → AES 키
4. key에서 idx 위치의 16바이트 추출 → AES IV
5. AES-256-CBC로 비밀번호 암호화
6. Base64 인코딩 → 다시 Base64 인코딩 (이중)
7. 암호화된 비밀번호로 로그인 요청
```

#### 역 코드 (34개)
```
서울=SEL, 용산=YSN, 영등포=YDP, 광명=KWM, 수원=SWN,
천안아산=CNA, 오송=OSN, 대전=DJN, 서대전=SDJ, 김천구미=KCG,
동대구=DDG, 경주=GJU, 포항=POH, 밀양=MYN, 구포=GPU,
부산=PUS, 울산(통도사)=ULS, 마산=MAS, 창원중앙=CWJ,
경산=GSN, 논산=NSN, 익산=IKS, 정읍=JEU, 광주송정=KJS,
목포=MKP, 전주=JNJ, 순천=SCN, 여수EXPO=YSE, 청량리=CRL,
강릉=GNE, 행신=HAS, 정동진=JDJ, 진영=JYG
```

### 2.4 NetFunnel 대기열 시스템 (SRT/KTX 공통)

봇 차단을 위한 대기열 시스템. **열차 검색/예약 전에 반드시 통과해야 함.**

#### 동작 순서

```
1. getTidchkEnter (opcode=5101)
   → GET https://nf.letskorail.com/ts.wseq?sid=service_1&aid=act_10&...
   → 응답: NetFunnel.gControl.result='[code]:[status]:[params]'
   → status=200이면 통과, 201이면 대기

2. chkEnter 루프 (opcode=5002) — 1초 간격 폴링
   → GET https://[반환된IP]/ts.wseq?key=[캐시키]&...
   → nwait=대기자수, status=200이면 통과

3. setComplete (opcode=5004)
   → GET https://[IP]/ts.wseq?key=[캐시키]&...
   → 완료 확인

캐시: SRT=48초, KTX=50초 (같은 키 재사용 가능)
```

#### NetFunnel 헤더 (중요)

```
Host: nf.letskorail.com
X-Requested-With: kr.co.srail.newapp
sec-ch-ua: "Chromium";v="136", "Android WebView";v="136"
sec-ch-ua-mobile: ?1
Sec-Fetch-Site: cross-site
Sec-Fetch-Mode: no-cors
Referer: https://app.srail.or.kr/
```

### 2.5 매크로 자동화 루프

```python
while not cancelled:
    login_if_needed()
    netfunnel_key = netfunnel.run()
    trains = search(params, netfunnel_key)

    for selected_train in trains:
        if seat_available(selected_train):
            reservation = reserve(selected_train)
            if auto_pay:
                pay_with_card(reservation, card_info)
            notify_telegram(reservation)
            return SUCCESS

    sleep(gamma_variate(0.25~1.0))  # 랜덤 딜레이
```

### 2.6 승객 유형 시스템

| 유형 | SRT 코드 | KTX 코드 |
|------|----------|----------|
| 어른/청소년 | "1" | "1" |
| 장애 1~3급 | "2" | "2" |
| 장애 4~6급 | "3" | "3" |
| 경로 | "4" | "4" |
| 어린이 | "5" | "5" |

### 2.7 좌석 유형

| 유형 | 값 | 설명 |
|------|-----|------|
| GENERAL_FIRST | 1 | 일반실 우선 (특실 불가 시 일반) |
| GENERAL_ONLY | 2 | 일반실만 |
| SPECIAL_FIRST | 3 | 특실 우선 (일반 불가 시 특실) |
| SPECIAL_ONLY | 4 | 특실만 |

### 2.8 응답 파싱 패턴

SRT/KTX 모두 동일한 패턴:
```json
{
  "resultMap": [{"strResult": "SUCC|FAIL", "msgTxt": "에러 메시지"}],
  "outDataSets": { "dsOutput1": [...] }
}
```

### 2.9 에러 처리 계층

```
SRT 계열:
  SRTError → SRTLoginError, SRTResponseError, SRTNotLoggedInError, SRTNetFunnelError
  SRTResponseError → SRTDuplicateError

KTX 계열:
  KorailError → NeedToLoginError, NoResultsError, SoldOutError, NetFunnelError
```

---

## 3. 앱 아키텍처 설계

### 3.1 전체 구조 (Clean Architecture + MVVM)

```
┌─────────────────────────────────────────────┐
│                 UI Layer                     │
│  (Jetpack Compose Screens + ViewModels)     │
├─────────────────────────────────────────────┤
│              Domain Layer                    │
│  (Use Cases / Interactors)                  │
├─────────────────────────────────────────────┤
│               Data Layer                     │
│  ┌─────────────┐  ┌──────────────────────┐  │
│  │  Local DB   │  │  Remote API Clients  │  │
│  │  (Room)     │  │  (OkHttp + SRT/KTX)  │  │
│  └─────────────┘  └──────────────────────┘  │
├─────────────────────────────────────────────┤
│              Core Layer                      │
│  (NetFunnel, AES Crypto, Session Mgmt)      │
└─────────────────────────────────────────────┘
```

### 3.2 패키지 구조

```
com.srtgo.app/
├── di/                          # Hilt DI 모듈
│   ├── AppModule.kt
│   ├── NetworkModule.kt
│   └── DatabaseModule.kt
│
├── core/                        # 코어 엔진 (Python 포팅)
│   ├── network/
│   │   ├── NetFunnelHelper.kt       # 대기열 시스템
│   │   ├── SessionManager.kt        # OkHttp 세션/쿠키 관리
│   │   └── UserAgents.kt            # UA 상수
│   ├── crypto/
│   │   ├── KtxPasswordEncryptor.kt  # KTX AES-256-CBC 비밀번호 암호화
│   │   └── SecureStorage.kt         # EncryptedSharedPreferences
│   ├── model/
│   │   ├── RailType.kt              # enum: SRT, KTX
│   │   ├── SeatType.kt              # enum: GENERAL_FIRST, etc.
│   │   ├── PassengerType.kt         # enum: ADULT, CHILD, etc.
│   │   ├── Passenger.kt             # data class
│   │   ├── Train.kt                 # data class (SRT/KTX 통합)
│   │   ├── Reservation.kt           # data class
│   │   ├── Ticket.kt                # data class
│   │   └── Station.kt               # data class + 역 코드 매핑
│   ├── client/
│   │   ├── RailClient.kt            # interface (공통 인터페이스)
│   │   ├── SrtClient.kt             # SRT API 구현
│   │   ├── KtxClient.kt             # KTX API 구현
│   │   └── ResponseParser.kt        # 응답 JSON 파싱
│   └── exception/
│       ├── RailException.kt         # sealed class 계층
│       └── ErrorMessages.kt         # 에러 메시지 상수
│
├── data/                        # 데이터 레이어
│   ├── local/
│   │   ├── AppDatabase.kt           # Room DB
│   │   ├── dao/
│   │   │   ├── SettingsDao.kt
│   │   │   ├── MacroHistoryDao.kt
│   │   │   └── CredentialDao.kt
│   │   └── entity/
│   │       ├── SettingsEntity.kt
│   │       ├── MacroHistoryEntity.kt
│   │       └── CredentialEntity.kt
│   └── repository/
│       ├── TrainRepository.kt        # 열차 검색
│       ├── ReservationRepository.kt  # 예약 관리
│       ├── SettingsRepository.kt     # 설정 관리
│       └── MacroRepository.kt        # 매크로 이력
│
├── domain/                      # 도메인 레이어
│   └── usecase/
│       ├── LoginUseCase.kt
│       ├── SearchTrainsUseCase.kt
│       ├── ReserveTrainUseCase.kt
│       ├── PayReservationUseCase.kt
│       ├── CancelReservationUseCase.kt
│       ├── RunMacroUseCase.kt        # 매크로 루프 핵심
│       └── GetReservationsUseCase.kt
│
├── service/                     # 백그라운드 서비스
│   ├── MacroForegroundService.kt    # Foreground Service (매크로 실행)
│   └── MacroNotificationManager.kt # 실시간 알림 업데이트
│
├── ui/                          # UI 레이어
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── navigation/
│   │   └── AppNavigation.kt
│   ├── common/
│   │   ├── LoadingOverlay.kt
│   │   ├── ErrorDialog.kt
│   │   └── StationPicker.kt
│   ├── screen/
│   │   ├── login/                   # SRT/KTX 로그인 설정
│   │   │   ├── LoginScreen.kt
│   │   │   └── LoginViewModel.kt
│   │   ├── search/                  # 열차 검색
│   │   │   ├── SearchScreen.kt
│   │   │   └── SearchViewModel.kt
│   │   ├── result/                  # 검색 결과 + 매크로 시작
│   │   │   ├── ResultScreen.kt
│   │   │   └── ResultViewModel.kt
│   │   ├── macro/                   # 매크로 실행 모니터
│   │   │   ├── MacroScreen.kt
│   │   │   └── MacroViewModel.kt
│   │   ├── reservations/            # 예약 목록
│   │   │   ├── ReservationsScreen.kt
│   │   │   └── ReservationsViewModel.kt
│   │   ├── settings/                # 설정 (카드, 텔레그램 등)
│   │   │   ├── SettingsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   └── history/                 # 매크로 이력
│   │       ├── HistoryScreen.kt
│   │       └── HistoryViewModel.kt
│   └── MainActivity.kt
│
└── util/
    ├── DateTimeUtils.kt             # YYYYMMDD, HHMMSS 포맷
    ├── TelegramNotifier.kt          # 텔레그램 알림
    └── Extensions.kt
```

---

## 4. 모듈 구조

### 4.1 Gradle 모듈 (선택적 멀티모듈)

단일 앱 모듈로 시작하되, 향후 분리 가능하게 패키지 경계를 명확히 한다.

```
app/                    # 단일 모듈 (Compose UI + 전체 로직)
```

### 4.2 핵심 컴포넌트 상세

#### RailClient 인터페이스 (SRT/KTX 공통)

```kotlin
interface RailClient {
    suspend fun login(id: String, password: String)
    suspend fun logout()
    suspend fun searchTrains(
        departure: String,
        arrival: String,
        date: String,       // YYYYMMDD
        time: String,       // HHMMSS
        passengers: List<Passenger>,
        seatType: SeatType = SeatType.GENERAL_FIRST
    ): List<Train>
    suspend fun reserve(
        train: Train,
        passengers: List<Passenger>,
        seatType: SeatType,
        windowSeat: Boolean? = null
    ): Reservation
    suspend fun getReservations(): List<Reservation>
    suspend fun getTicketInfo(reservation: Reservation): List<Ticket>
    suspend fun cancel(reservation: Reservation)
    suspend fun payWithCard(
        reservation: Reservation,
        cardNumber: String,
        cardPassword: String,
        birthday: String,
        expireDate: String,
        installment: Int = 0
    )
    suspend fun refund(reservation: Reservation)
    fun clearNetFunnel()
}
```

#### MacroForegroundService (핵심)

```kotlin
// Android Foreground Service로 매크로 실행
// - 앱이 백그라운드에 있어도 지속 실행
// - Notification으로 진행 상황 실시간 표시
// - 성공 시 텔레그램 알림 + 푸시 알림

class MacroForegroundService : Service() {
    // 상태 Flow → UI에서 collect
    val macroState: StateFlow<MacroState>

    fun startMacro(config: MacroConfig)
    fun cancelMacro()
}

data class MacroState(
    val status: MacroStatus,    // IDLE, SEARCHING, SUCCESS, FAILED, CANCELLED
    val attempts: Int,
    val elapsed: Duration,
    val lastError: String?,
    val reservation: Reservation?
)
```

---

## 5. Kotlin 데이터 모델

### 5.1 열차 (Train)

```kotlin
data class Train(
    val railType: RailType,
    val trainCode: String,          // "17" (SRT), "100" (KTX) 등
    val trainName: String,          // "SRT", "KTX" 등
    val trainNumber: String,        // "101"
    val depDate: String,            // "20260305"
    val depTime: String,            // "120000"
    val depStationCode: String,     // "0551"
    val depStationName: String,     // "수서"
    val depStationRunOrder: String,
    val depStationConsOrder: String,
    val arrDate: String,
    val arrTime: String,
    val arrStationCode: String,
    val arrStationName: String,
    val arrStationRunOrder: String,
    val arrStationConsOrder: String,
    val generalSeatState: String,   // "예약가능" / "매진"
    val specialSeatState: String,
    val reserveWaitCode: String,    // "-1", "0", "9", "-2"
) {
    val isGeneralAvailable: Boolean
        get() = "예약가능" in generalSeatState
    val isSpecialAvailable: Boolean
        get() = "예약가능" in specialSeatState
    val isStandbyAvailable: Boolean
        get() = reserveWaitCode == "9"

    fun isSeatAvailable(seatType: SeatType): Boolean = when (seatType) {
        SeatType.GENERAL_FIRST -> isGeneralAvailable || isSpecialAvailable
        SeatType.GENERAL_ONLY -> isGeneralAvailable
        SeatType.SPECIAL_FIRST -> isSpecialAvailable || isGeneralAvailable
        SeatType.SPECIAL_ONLY -> isSpecialAvailable
    }
}
```

### 5.2 예약 (Reservation)

```kotlin
data class Reservation(
    val railType: RailType,
    val reservationNumber: String,
    val totalCost: Int,
    val seatCount: Int,
    val trainName: String,
    val trainNumber: String,
    val depDate: String,
    val depTime: String,
    val depStationName: String,
    val arrTime: String,
    val arrStationName: String,
    val paymentDate: String?,
    val paymentTime: String?,
    val isPaid: Boolean,
    val isRunning: Boolean,
    val isWaiting: Boolean,
    val tickets: List<Ticket> = emptyList()
)
```

### 5.3 티켓 (Ticket)

```kotlin
data class Ticket(
    val car: String,                // 호차
    val seat: String,               // 좌석번호 (대기 시 빈 문자열)
    val seatTypeCode: String,       // "1"=일반, "2"=특실
    val seatTypeName: String,
    val passengerType: String,
    val price: Int,
    val originalPrice: Int,
    val discount: Int,
    val isWaiting: Boolean
)
```

### 5.4 승객 (Passenger)

```kotlin
data class Passenger(
    val type: PassengerType,
    val count: Int = 1
)

enum class PassengerType(val code: String, val displayName: String) {
    ADULT("1", "어른/청소년"),
    DISABILITY_1_3("2", "장애 1~3급"),
    DISABILITY_4_6("3", "장애 4~6급"),
    SENIOR("4", "경로"),
    CHILD("5", "어린이")
}
```

### 5.5 Enum 정의

```kotlin
enum class RailType { SRT, KTX }

enum class SeatType(val code: Int, val displayName: String) {
    GENERAL_FIRST(1, "일반실 우선"),
    GENERAL_ONLY(2, "일반실"),
    SPECIAL_FIRST(3, "특실 우선"),
    SPECIAL_ONLY(4, "특실")
}
```

### 5.6 Room Entity

```kotlin
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val railType: String,    // "SRT" or "KTX"
    val userId: String,                   // 암호화 저장
    val password: String                  // 암호화 저장
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val key: String,
    val value: String,
    val encrypted: Boolean = false
)

@Entity(tableName = "macro_history")
data class MacroHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val railType: String,
    val departure: String,
    val arrival: String,
    val date: String,
    val time: String,
    val passengers: String,              // JSON
    val seatType: String,
    val autoPay: Boolean,
    val selectedTrains: String,          // JSON
    val status: String,                  // "success", "failed", "cancelled"
    val attempts: Int,
    val elapsedSeconds: Double,
    val resultJson: String?,             // 예약 결과 JSON
    val createdAt: Long,
    val finishedAt: Long?
)
```

---

## 6. API 통신 레이어

### 6.1 OkHttp 설정

```kotlin
// SRT용 OkHttp 클라이언트
val srtClient = OkHttpClient.Builder()
    .cookieJar(PersistentCookieJar())       // 세션 쿠키 자동 관리
    .addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", SRT_USER_AGENT)
                .header("Accept", "application/json")
                .build()
        )
    }
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

// KTX용 OkHttp 클라이언트
val ktxClient = OkHttpClient.Builder()
    .cookieJar(PersistentCookieJar())
    .addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", KTX_USER_AGENT)
                .build()
        )
    }
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

### 6.2 요청/응답 패턴

```kotlin
// SRT: 모든 API가 form-urlencoded POST
suspend fun srtPost(url: String, params: Map<String, String>): JSONObject {
    val body = params.entries.joinToString("&") {
        "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
    }.toRequestBody("application/x-www-form-urlencoded".toMediaType())

    val request = Request.Builder().url(url).post(body).build()
    return withContext(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        JSONObject(response.body!!.string())
    }
}

// KTX: 검색은 GET, 나머지는 POST
suspend fun ktxGet(url: String, params: Map<String, String>): JSONObject {
    val urlWithParams = HttpUrl.parse(url)!!.newBuilder().apply {
        params.forEach { (k, v) -> addQueryParameter(k, v) }
    }.build()

    val request = Request.Builder().url(urlWithParams).get().build()
    return withContext(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        JSONObject(response.body!!.string())
    }
}
```

### 6.3 NetFunnel Kotlin 구현 개요

```kotlin
class NetFunnelHelper(
    private val httpClient: OkHttpClient,
    private val cacheTtlSeconds: Int = 48
) {
    private var cachedKey: String? = null
    private var cachedAt: Long = 0

    suspend fun run(): String {
        // 캐시 유효하면 재사용
        if (cachedKey != null && System.currentTimeMillis() - cachedAt < cacheTtlSeconds * 1000) {
            return cachedKey!!
        }

        // 1. getTidchkEnter
        val (status, key, nwait, ip) = start()
        if (status == "200") {
            complete(key, ip)
            cachedKey = key
            cachedAt = System.currentTimeMillis()
            return key
        }

        // 2. chkEnter 루프
        var currentStatus = status
        var currentKey = key
        var currentIp = ip
        while (currentStatus != "200") {
            delay(1000)
            val result = check(currentKey, currentIp)
            currentStatus = result.status
            currentKey = result.key
            currentIp = result.ip
        }

        // 3. setComplete
        complete(currentKey, currentIp)
        cachedKey = currentKey
        cachedAt = System.currentTimeMillis()
        return currentKey
    }

    fun clear() {
        cachedKey = null
        cachedAt = 0
    }
}
```

### 6.4 KTX AES 암호화 Kotlin 구현 개요

```kotlin
object KtxPasswordEncryptor {
    suspend fun encrypt(password: String, httpClient: OkHttpClient): String {
        // 1. 서버에서 암호화 키 가져오기
        val codeResponse = httpClient.postForm(
            "https://smart.letskorail.com:443/classes/com.korail.mobile.common.code.do",
            mapOf("code" to "app.login.cphd")
        )
        val keyData = codeResponse.getJSONObject("app.login.cphd")
        val idx = keyData.getInt("idx")
        val hexKey = keyData.getString("key")

        // 2. 키/IV 추출
        val keyBytes = hexKey.substring(idx, idx + 32).toByteArray()  // AES-256 키
        val ivBytes = hexKey.substring(idx, idx + 16).toByteArray()   // IV

        // 3. AES-256-CBC 암호화
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        val encrypted = cipher.doFinal(password.toByteArray())

        // 4. 이중 Base64 인코딩
        val firstBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return Base64.encodeToString(firstBase64.toByteArray(), Base64.NO_WRAP)
    }
}
```

---

## 7. 화면 설계

### 7.1 화면 목록 및 네비게이션

```
┌──────────────────────────────┐
│    Bottom Navigation Bar     │
│  [검색]  [예약]  [이력]  [설정] │
└──────────────────────────────┘

검색 탭:
  SearchScreen → ResultScreen → MacroScreen

예약 탭:
  ReservationsScreen → ReservationDetailScreen

이력 탭:
  HistoryScreen

설정 탭:
  SettingsScreen
    ├── SRT 로그인 설정
    ├── KTX 로그인 설정
    ├── 카드 정보 설정
    ├── 텔레그램 설정
    └── 즐겨찾기 역 설정
```

### 7.2 화면별 상세

#### 1. 검색 화면 (SearchScreen)
- SRT/KTX 탭 전환
- 출발역/도착역 선택 (BottomSheet 역 목록)
- 날짜 선택 (DatePicker)
- 시간 선택 (TimePicker)
- 승객 수 설정 (어른, 어린이, 경로, 장애)
- 좌석 유형 선택
- **[열차 조회]** 버튼

#### 2. 결과 화면 (ResultScreen)
- 열차 목록 (일반석/특실 잔여석 표시)
- 체크박스로 감시할 열차 다중 선택
- 자동결제 토글
- **[예매 시작]** 버튼 → 매크로 시작
- 좌석 있는 열차: 직접 예약 버튼도 제공

#### 3. 매크로 화면 (MacroScreen)
- 실시간 상태: 시도 횟수, 경과 시간
- 에러 로그 스크롤
- 성공 시: 예약 정보 카드 + 축하 애니메이션
- **[취소]** 버튼 → 매크로 중단
- Foreground Service Notification과 연동

#### 4. 예약 목록 (ReservationsScreen)
- SRT/KTX 탭
- 예약별 카드: 열차명, 시간, 좌석, 금액
- 미결제 예약: **[결제]** 버튼
- **[취소]** / **[환불]** 버튼

#### 5. 이력 화면 (HistoryScreen)
- 매크로 실행 이력 (성공/실패/취소)
- 시도 횟수, 소요 시간 표시

#### 6. 설정 화면 (SettingsScreen)
- SRT 계정 (ID/PW 입력 → 로그인 테스트)
- KTX 계정 (ID/PW 입력 → 로그인 테스트)
- 카드 정보 (번호, 비밀번호 앞2자리, 생년월일, 유효기간)
- 텔레그램 (봇 토큰, 채팅 ID → 테스트 메시지)
- 즐겨찾기 역 관리

---

## 8. 핵심 기술 과제 및 해결 방안

### 8.1 TLS 핑거프린트

| 문제 | 해결 |
|------|------|
| Python은 `curl_cffi`로 Chrome TLS 위장 | Android OkHttp는 자체 TLS 스택 사용, 실제 Android 기기이므로 자연스러움 |
| SRT User-Agent에 `SRT-APP-Android` 포함 | 동일한 UA 문자열 사용하면 실제 앱과 구분 불가 |
| KTX User-Agent에 `Dalvik` 포함 | Android 기기에서 Dalvik UA는 자연스러움 |

**결론**: TLS 위장은 해결 완료. 추가 작업 없음.

### 8.2 NetFunnel 응답 파싱

| 문제 | 해결 |
|------|------|
| 응답이 JSON이 아닌 JavaScript 콜백 형태 | Regex로 파싱: `NetFunnel\.gControl\.result='([^']+)'` |
| 반환된 IP로 후속 요청 필요 | IP 추출 후 URL 동적 구성 |
| 캐시 TTL 관리 | `System.currentTimeMillis()` 기반 캐시 |

### 8.3 KTX 비밀번호 암호화

| 문제 | 해결 |
|------|------|
| 서버에서 키를 받아 AES-256-CBC 암호화 | `javax.crypto.Cipher` 사용 (Android 기본 제공) |
| 이중 Base64 인코딩 | `android.util.Base64` 2회 적용 |
| 키/IV 추출 로직 (idx 오프셋) | Python 로직 그대로 포팅 |

### 8.4 백그라운드 매크로 실행

| 문제 | 해결 |
|------|------|
| 앱이 백그라운드에서도 실행 필요 | Android Foreground Service + Notification |
| 실시간 UI 업데이트 | `StateFlow` + Service Binding |
| 배터리 최적화로 앱 죽을 수 있음 | Foreground Service는 시스템이 보호 |
| 여러 매크로 동시 실행 | 코루틴 기반 다중 태스크 관리 |

### 8.5 세션 관리

| 문제 | 해결 |
|------|------|
| SRT/KTX 로그인 세션 유지 | OkHttp `CookieJar` 구현 |
| 세션 만료 시 자동 재로그인 | 에러 감지 → 재로그인 → 재시도 |
| 앱 재시작 시 세션 복원 | 쿠키 영구 저장 또는 재로그인 |

### 8.6 보안

| 항목 | 방법 |
|------|------|
| SRT/KTX 비밀번호 저장 | Android Keystore + EncryptedSharedPreferences |
| 카드 정보 저장 | 동일 (Keystore 기반 암호화) |
| 텔레그램 토큰 | 동일 |
| 네트워크 통신 | HTTPS (기본) |

---

## 9. 작업 계획 (Phase별)

### Phase 1: 프로젝트 초기 설정 (1~2일)

```
1.1 Android 프로젝트 생성 (Compose + Material3)
1.2 Gradle 의존성 설정 (OkHttp, Room, Hilt, Navigation)
1.3 패키지 구조 생성
1.4 테마/색상/폰트 설정
1.5 Bottom Navigation 기본 틀
```

### Phase 2: 코어 엔진 포팅 — 데이터 모델 (2~3일)

```
2.1 RailType, SeatType, PassengerType enum 정의
2.2 Train, Reservation, Ticket, Passenger data class 정의
2.3 Station 매핑 (SRT 32개 + KTX 34개 역 코드)
2.4 RailException sealed class 계층
2.5 UserAgent 상수 정의
2.6 ResponseParser (JSON → data class 변환)
```

### Phase 3: 코어 엔진 포팅 — 네트워크 (5~7일) ⭐ 핵심

```
3.1 SessionManager (OkHttp + CookieJar)
3.2 NetFunnelHelper 구현
    - getTidchkEnter, chkEnter 루프, setComplete
    - 응답 파싱 (Regex)
    - 캐시 관리 (48/50초 TTL)
3.3 KtxPasswordEncryptor (AES-256-CBC + 이중 Base64)
3.4 SrtClient 구현
    - login / logout
    - searchTrains (NetFunnel 연동)
    - reserve (개인예약 + 예약대기)
    - getReservations / getTicketInfo
    - cancel / refund
    - payWithCard
3.5 KtxClient 구현
    - login (암호화 포함) / logout
    - searchTrains (GET 방식, NetFunnel 연동)
    - reserve
    - getReservations / getTickets
    - cancel / refund
    - payWithCard
3.6 RailClient 인터페이스 + 팩토리
```

### Phase 4: 로컬 저장소 (2~3일)

```
4.1 Room Database 설정
4.2 SettingsDao + Entity
4.3 MacroHistoryDao + Entity
4.4 CredentialDao + Entity
4.5 SecureStorage (EncryptedSharedPreferences)
4.6 Repository 구현 (Settings, Macro, Credential)
```

### Phase 5: 도메인 레이어 (2~3일)

```
5.1 LoginUseCase (로그인 테스트)
5.2 SearchTrainsUseCase (검색 → 결과)
5.3 ReserveTrainUseCase (예약)
5.4 PayReservationUseCase (결제)
5.5 CancelReservationUseCase (취소/환불)
5.6 RunMacroUseCase (매크로 루프 핵심 로직)
5.7 GetReservationsUseCase (예약 목록)
```

### Phase 6: 매크로 서비스 (3~4일) ⭐ 핵심

```
6.1 MacroForegroundService 구현
    - Notification 채널 생성
    - 실시간 알림 업데이트 (시도 횟수, 경과 시간)
    - 성공/실패/취소 알림
6.2 MacroEngine (코루틴 기반 루프)
    - 로그인 → NetFunnel → 검색 → 좌석 확인 → 예약 → 결제
    - 에러 복구 (재로그인, NetFunnel 재시도)
    - 감마 분포 랜덤 딜레이
6.3 StateFlow 기반 상태 브로드캐스트
6.4 TelegramNotifier (HTTP 직접 호출)
```

### Phase 7: UI 구현 (5~7일)

```
7.1 설정 화면 (SettingsScreen)
    - SRT/KTX 로그인 폼 + 테스트
    - 카드 정보 입력 + 마스킹
    - 텔레그램 설정 + 테스트
    - 즐겨찾기 역 편집
7.2 검색 화면 (SearchScreen)
    - SRT/KTX 전환
    - 역 선택 BottomSheet
    - 날짜/시간 선택
    - 승객 수 카운터
7.3 결과 화면 (ResultScreen)
    - 열차 목록 + 좌석 상태
    - 다중 선택 체크박스
    - 자동결제 토글
    - 예매 시작 버튼
7.4 매크로 화면 (MacroScreen)
    - 실시간 카운터 애니메이션
    - 에러 로그
    - 성공 카드 + 애니메이션
    - 취소 버튼
7.5 예약 목록 (ReservationsScreen)
    - SRT/KTX 탭
    - 예약 카드 + 결제/취소 액션
7.6 이력 화면 (HistoryScreen)
    - 매크로 이력 LazyColumn
```

### Phase 8: 통합 테스트 및 마무리 (3~4일)

```
8.1 SRT 로그인 → 검색 → 예약 → 취소 E2E 테스트
8.2 KTX 로그인 → 검색 → 예약 → 취소 E2E 테스트
8.3 매크로 실행 → 성공/취소 시나리오 테스트
8.4 NetFunnel 대기열 실제 동작 검증
8.5 백그라운드 서비스 안정성 테스트
8.6 에러 복구 시나리오 테스트
8.7 ProGuard/R8 난독화 설정
8.8 APK/AAB 빌드
```

### 총 예상 작업량

| Phase | 내용 | 예상 기간 |
|-------|------|-----------|
| 1 | 프로젝트 설정 | 1~2일 |
| 2 | 데이터 모델 | 2~3일 |
| 3 | **네트워크 엔진** | **5~7일** |
| 4 | 로컬 저장소 | 2~3일 |
| 5 | 도메인 레이어 | 2~3일 |
| 6 | **매크로 서비스** | **3~4일** |
| 7 | **UI 구현** | **5~7일** |
| 8 | 테스트/마무리 | 3~4일 |
| | **합계** | **약 23~33일** |

---

## 10. 의존성 및 라이브러리

### build.gradle (app)

```groovy
dependencies {
    // Jetpack Compose
    implementation platform("androidx.compose:compose-bom:2024.10.01")
    implementation "androidx.compose.ui:ui"
    implementation "androidx.compose.material3:material3"
    implementation "androidx.compose.ui:ui-tooling-preview"
    implementation "androidx.activity:activity-compose:1.9.3"
    implementation "androidx.navigation:navigation-compose:2.8.4"

    // ViewModel + Lifecycle
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7"
    implementation "androidx.lifecycle:lifecycle-runtime-compose:2.8.7"

    // Hilt (DI)
    implementation "com.google.dagger:hilt-android:2.52"
    kapt "com.google.dagger:hilt-compiler:2.52"
    implementation "androidx.hilt:hilt-navigation-compose:1.2.0"

    // OkHttp (HTTP 클라이언트)
    implementation "com.squareup.okhttp3:okhttp:4.12.0"
    implementation "com.squareup.okhttp3:logging-interceptor:4.12.0"

    // Room (로컬 DB)
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"

    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"

    // Security (암호화 저장)
    implementation "androidx.security:security-crypto:1.1.0-alpha06"

    // JSON 파싱
    implementation "org.json:json:20240303"
    // 또는 kotlinx.serialization
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"

    // DataStore (경량 설정)
    implementation "androidx.datastore:datastore-preferences:1.1.1"
}
```

### Python → Kotlin 라이브러리 매핑

| Python | Kotlin/Android |
|--------|---------------|
| `curl_cffi` / `requests` | `OkHttp` |
| `PyCryptodome` (AES) | `javax.crypto.Cipher` (Android 내장) |
| `keyring` | `EncryptedSharedPreferences` (Keystore) |
| `python-telegram-bot` | `OkHttp` 직접 HTTP 호출 |
| `aiosqlite` | `Room` (SQLite ORM) |
| `asyncio` | `Kotlin Coroutines` |
| `threading` | `Coroutines + Foreground Service` |

---

## 11. 리스크 및 대응 방안

### 11.1 높은 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| SRT/KTX 서버가 Android 앱 요청을 차단 | 앱 전체 불능 | UA 문자열 정확히 맞추기, 실제 공식 앱의 요청 패턴 참고 |
| NetFunnel 응답 형식 변경 | 검색/예약 불가 | 유연한 파싱 로직 + 빠른 업데이트 체계 |
| KTX AES 암호화 키 교환 방식 변경 | KTX 로그인 불가 | 서버 응답 모니터링 + 대체 로직 준비 |

### 11.2 중간 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| 세션 쿠키 만료 타이밍 불일치 | 간헐적 에러 | 자동 재로그인 + 에러 복구 로직 |
| Android 12+ 백그라운드 제한 강화 | 매크로 중단 | Foreground Service + 배터리 최적화 예외 요청 |
| OkHttp TLS 버전이 서버와 호환 안됨 | 연결 불가 | TLS 버전 명시 설정, 필요 시 BoringSSL 커스텀 |

### 11.3 낮은 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| 열차 검색 결과 JSON 필드 추가/변경 | 파싱 에러 | `optString()` 등 안전한 파싱, 알 수 없는 필드 무시 |
| Google Play 심사 거부 | 배포 불가 | 자체 APK 배포 (사이드로딩) |

---

## 부록 A: SRT 로그인 요청 상세

```
POST https://app.srail.or.kr:443/apb/selectListApb01080_n.do

Body (form-urlencoded):
  auto=Y
  check=Y
  page=menu
  deviceKey=-
  customerYn=
  login_referer=https://app.srail.or.kr:443/main/main.do
  srchDvCd=1                    # 1=회원번호, 2=이메일, 3=전화
  srchDvNm={사용자 ID}
  hmpgPwdCphd={비밀번호}

성공 응답:
  {"resultMap":[{"strResult":"SUCC"}],
   "userMap":{"MB_CRD_NO":"...", "CUST_NM":"...", "MBL_PHONE":"..."}}
```

## 부록 B: KTX 로그인 요청 상세

```
# Step 1: 암호화 키 요청
POST https://smart.letskorail.com:443/classes/com.korail.mobile.common.code.do
Body: code=app.login.cphd
응답: {"strResult":"SUCC", "app.login.cphd":{"idx":N, "key":"hex..."}}

# Step 2: 로그인
POST https://smart.letskorail.com:443/classes/com.korail.mobile.login.Login
Body (form-urlencoded):
  Device=AD
  Version=240614001
  Key={encrypted_password}        # AES-256-CBC + double Base64
  txtInputFlg=2                   # 1=회원번호, 2=전화, 4=이메일
  txtMemberNo={전화번호}
  txtPwd={encrypted_password}
  radJobId=1
  inputFlg=T
  SeNumber=
  FindMsg=
  TgtTermId=
  isCorporation=N
  CorpNum=

성공 응답:
  {"strResult":"SUCC", "Key":"...", "strMbCrdNo":"...",
   "strCustNm":"...", "strPhone":"...", "strEmailAdr":"..."}
```

## 부록 C: 열차 검색 요청 상세

### SRT
```
POST https://app.srail.or.kr:443/ara/selectListAra10007_n.do

Body:
  chtnDvCd=1
  dptDt=20260305            # 출발일 YYYYMMDD
  dptTm=120000              # 출발시간 HHMMSS
  dptDt1=20260305
  dptTm1=120000
  dptRsStnCd=0551           # 출발역 코드
  arvRsStnCd=0015           # 도착역 코드
  stlbTrnClsfCd=05          # 전체 열차
  trnGpCd=109
  psgNum=1                  # 승객 수
  seatAttCd=015
  arriveTime=N
  dlayTnumAplFlg=Y
  netfunnelKey={key}        # NetFunnel 키 (필수!)
```

### KTX
```
GET https://smart.letskorail.com:443/classes/com.korail.mobile.seatMovie.ScheduleView

Params:
  Device=AD
  Version=240614001
  Key={로그인 키}
  radJobId=1
  txtGoStart={출발역 한글}
  txtGoEnd={도착역 한글}
  txtGoAbrdDt=20260305
  txtGoHour=120000
  txtSeatAttCd=015
  txtPsgFlg_1=1              # 어른 수
  txtPsgFlg_2=0              # 어린이 수
  txtPsgFlg_8=0              # 경로 수
  selGoTrain=05
  txtMenuId=11
  netfunnelKey={key}
```

---

> **이 기획서는 Python srtgo 코어 엔진의 모든 API 호출, 데이터 모델, 인증 방식, 암호화 로직을 분석한 결과를 바탕으로 작성되었습니다. 실제 구현 시 이 문서를 참조하여 1:1 포팅을 진행합니다.**
