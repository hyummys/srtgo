# SRTgo Web

SRTgo의 웹 인터페이스. SRT/KTX 열차 예매 자동화를 브라우저에서 사용할 수 있습니다.

## 기술 스택

| 영역 | 기술 |
|------|------|
| 백엔드 | Python 3.12, FastAPI, uvicorn, aiosqlite |
| 인증 | JWT (python-jose HS256), bcrypt |
| DB | SQLite |
| 프론트엔드 | Next.js 14, React 18, TypeScript, Tailwind CSS 3.4 |
| 텔레그램 | python-telegram-bot (관리자 승인 봇) |

## 빠른 시작

### 1. 백엔드 설정

```bash
# 프로젝트 루트에서
cd srtgo_org

# Python 가상환경 생성 + 활성화
python -m venv web/venv
# Windows:
web\venv\Scripts\activate
# Linux/Mac:
source web/venv/bin/activate

# 의존성 설치
pip install -r web/backend/requirements.txt
pip install python-jose[cryptography] bcrypt python-telegram-bot
pip install curl_cffi requests PyCryptodome

# 환경변수 설정
# web/.env 파일 생성:
#   SRTGO_SECRET_KEY=<python -c "import secrets; print(secrets.token_hex(32))">
#   SRTGO_JWT_SECRET=<python -c "import secrets; print(secrets.token_hex(32))">

# 서버 실행
python -m web.backend.run
# → http://localhost:8000
```

### 2. 프론트엔드 설정

```bash
cd web/frontend

# 의존성 설치
npm install

# 환경변수 설정
# web/frontend/.env.local:
#   NEXT_PUBLIC_API_URL=http://localhost:8000

# 개발 서버 실행
npm run dev
# → http://localhost:3000
```

### 3. 첫 사용

1. http://localhost:3000 접속
2. **회원가입** - 첫 번째 가입자가 자동으로 관리자(admin)가 됩니다
3. 이후 가입자는 관리자 승인 필요 (관리자 페이지 또는 텔레그램)

## 프로젝트 구조

```
web/
├── .env                      # 환경변수 (gitignore)
├── data/
│   └── srtgo.db              # SQLite DB (gitignore)
├── backend/
│   ├── config.py             # 환경변수 로드
│   ├── models.py             # Pydantic 요청/응답 모델
│   ├── database.py           # DB 초기화 + CRUD
│   ├── jwt_auth.py           # JWT 인증 + bcrypt
│   ├── telegram_bot.py       # 관리자 텔레그램 승인 봇
│   ├── main.py               # FastAPI 앱 + 미들웨어
│   ├── run.py                # 서버 시작점
│   ├── task_manager.py       # 매크로 백그라운드 태스크
│   ├── rail_bridge.py        # srtgo 코어 연동
│   ├── crypto.py             # 설정값 AES 암호화
│   ├── ws_manager.py         # WebSocket 연결 관리
│   └── routers/
│       ├── auth.py           # 회원가입, 로그인, 내 정보
│       ├── admin.py          # 사용자 관리 (admin)
│       ├── settings.py       # SRT/KTX 계정, 텔레그램 등 설정
│       ├── trains.py         # 열차 검색
│       ├── reservations.py   # 예약 조회/결제/취소
│       ├── macro.py          # 매크로 시작/목록/취소
│       └── ws.py             # WebSocket 실시간 상태
├── frontend/
│   ├── .env.local            # 프론트 환경변수
│   ├── package.json
│   └── src/
│       ├── app/
│       │   ├── page.tsx      # 홈 (로그인 + 대시보드)
│       │   ├── register/     # 회원가입
│       │   ├── admin/        # 사용자 관리
│       │   ├── macro/        # 매크로 실행
│       │   ├── reserve/      # 열차 검색/예매
│       │   ├── reservations/ # 예약 확인
│       │   └── settings/     # 설정
│       ├── components/layout/
│       │   ├── Header.tsx    # 데스크톱 네비게이션
│       │   └── BottomNav.tsx # 모바일 하단 탭
│       └── lib/
│           ├── api.ts        # API 클라이언트
│           ├── types.ts      # TypeScript 타입
│           ├── constants.ts  # 역 목록 등 상수
│           └── ws.ts         # WebSocket 클라이언트
```

## 환경변수

### web/.env (백엔드)

| 변수 | 필수 | 설명 |
|------|------|------|
| `SRTGO_SECRET_KEY` | 필수 | 설정값 암호화 키 (hex 64자) |
| `SRTGO_JWT_SECRET` | 권장 | JWT 서명 키 (미설정 시 SECRET_KEY 사용) |
| `SRTGO_ADMIN_TG_TOKEN` | 선택 | 관리자 텔레그램 봇 토큰 |
| `SRTGO_ADMIN_TG_CHAT_ID` | 선택 | 관리자 텔레그램 채팅 ID |
| `SRTGO_CORS_ORIGINS` | 선택 | CORS 허용 도메인 (기본: localhost:3000) |
| `SRTGO_DB_PATH` | 선택 | DB 경로 (기본: web/data/srtgo.db) |

### web/frontend/.env.local (프론트엔드)

| 변수 | 설명 |
|------|------|
| `NEXT_PUBLIC_API_URL` | 백엔드 API 주소 (기본: http://localhost:8000) |

## API 엔드포인트

### 공개
- `GET /api/health` - 서버 상태
- `POST /api/user/register` - 회원가입
- `POST /api/user/login` - 로그인
- `GET /api/trains/stations` - 역 목록

### 인증 필요 (JWT Bearer)
- `GET /api/user/me` - 내 정보
- `GET/PUT /api/settings/*` - 설정 (SRT계정, KTX계정, 텔레그램 등)
- `POST /api/trains/search` - 열차 검색
- `GET /api/reservations/list` - 예약 목록
- `POST /api/macro/start` - 매크로 시작
- `GET /api/macro/list` - 활성 매크로

### 관리자 전용
- `GET /api/admin/users` - 사용자 목록
- `PUT /api/admin/users/{id}/approve` - 승인
- `PUT /api/admin/users/{id}/reject` - 거절
- `DELETE /api/admin/users/{id}` - 삭제

## 인증 흐름

```
첫 접속 → 로그인 폼
├─ 회원가입 → 첫 가입자: admin (자동 승인)
│            → 이후 가입자: pending (관리자 승인 대기)
├─ 로그인 → JWT 발급 (7일 만료) → 대시보드
└─ 관리자 → /admin 에서 사용자 승인/거절/삭제
```

## 프로덕션 배포

### 권장: VPS (Oracle Cloud Free Tier 등)

PaaS 대신 VPS를 권장하는 이유:
- 백그라운드 매크로 프로세스 24시간 가동 필요
- WebSocket 실시간 통신
- SQLite 파일 영속성
- 텔레그램 봇 long-polling

### 배포 시 필요한 변경
- `web/.env`: 프로덕션 키 생성, 텔레그램 봇 설정
- `web/frontend/.env.local`: `NEXT_PUBLIC_API_URL=https://your-domain.com`
- CORS 설정: `SRTGO_CORS_ORIGINS=https://your-domain.com`
- Nginx 리버스 프록시 (80/443 → 8000)
- systemd 서비스 (자동 시작)
- Let's Encrypt HTTPS (도메인 필요)
