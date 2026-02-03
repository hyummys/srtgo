# CLAUDE.md - SRTgo Web Project Guide

> 이 문서는 모든 프로젝트 가이드의 단일 진실 소스(Single Source of Truth)입니다.
> Claude Code 세션 재개 시 자동으로 참고됩니다.

---

## 프로젝트 개요

SRT/KTX 열차 예매 자동화 서비스의 웹 인터페이스.
- **코어 엔진**: `srtgo/` — 절대 수정 금지 (upstream 패키지)
- **웹 앱**: `web/` — 모든 개발은 여기서

## 핵심 규칙

1. **`srtgo/` 폴더 절대 수정 금지** — upstream 예매 엔진
2. 모든 변경은 `web/` (backend 또는 frontend) 내에서만
3. DB 경로: `web/data/srtgo.db` (SQLite)
4. 백엔드 실행: `python -m web.backend.run`

## 현재 상태

### 완료된 작업
- 고정 API 토큰 → JWT 기반 다중 사용자 인증 전환 완료 (21단계)
- 회원가입/로그인/관리자 승인 시스템
- 사용자별 설정 격리 (settings, macro_history에 user_id)
- 관리자 텔레그램 승인 봇
- API 테스트 15개 항목 전체 통과
- 프론트엔드 전체 페이지 구현 완료

### 해결된 버그
1. **passlib + bcrypt 비호환**: passlib 제거 → 직접 `bcrypt` 모듈 사용
2. **JWT sub 문자열 필수**: `create_access_token`에서 `str(user_id)`, `get_current_user`에서 `int(payload["sub"])`

### 남은 할일: 배포 (Cloudflare Tunnel)
- Cloudflare 계정 가입 → cloudflared 설치 → 터널 연결
- 자세한 방법은 아래 "Cloudflare Tunnel로 외부 공개" 섹션 참고

---

## 프로젝트 구조

```
srtgo_org/
├── srtgo/                        # 코어 엔진 (수정 금지)
├── CLAUDE.md                     # 이 파일 (프로젝트 가이드 통합)
├── web/
│   ├── .env                      # 환경변수 (gitignore)
│   ├── .gitignore
│   ├── data/
│   │   └── srtgo.db              # SQLite DB (gitignore)
│   ├── backend/
│   │   ├── __init__.py
│   │   ├── config.py             # 환경변수, DB 경로, JWT/TG 설정
│   │   ├── models.py             # Pydantic 모델
│   │   ├── database.py           # SQLite CRUD (users, settings, macro_history)
│   │   ├── jwt_auth.py           # JWT 생성/검증, bcrypt, FastAPI Depends
│   │   ├── crypto.py             # 설정값 암호화 (Fernet)
│   │   ├── main.py               # FastAPI 앱, 미들웨어, 라우터 등록
│   │   ├── run.py                # uvicorn 시작점
│   │   ├── task_manager.py       # 매크로 백그라운드 태스크
│   │   ├── rail_bridge.py        # srtgo 코어 연동
│   │   ├── ws_manager.py         # WebSocket 관리
│   │   ├── telegram_bot.py       # 관리자 승인 텔레그램 봇
│   │   └── routers/
│   │       ├── auth.py           # /api/user/register, login, me + /api/auth/*
│   │       ├── admin.py          # /api/admin/users CRUD
│   │       ├── settings.py       # /api/settings/* (사용자별)
│   │       ├── trains.py         # /api/trains/search, stations
│   │       ├── reservations.py   # /api/reservations/*
│   │       ├── macro.py          # /api/macro/start, list, cancel
│   │       └── ws.py             # WebSocket /ws/{task_id}
│   ├── requirements.txt          # Python 의존성 (전체)
│   ├── frontend/
│   │   ├── .env.local            # NEXT_PUBLIC_API_URL (gitignore)
│   │   ├── package.json          # next 14, react 18, tailwindcss 3.4
│   │   ├── next.config.js
│   │   ├── tsconfig.json
│   │   └── src/
│   │       ├── app/
│   │       │   ├── layout.tsx
│   │       │   ├── page.tsx          # 홈 (로그인/대시보드)
│   │       │   ├── register/page.tsx # 회원가입
│   │       │   ├── admin/page.tsx    # 사용자 관리 (admin only)
│   │       │   ├── macro/page.tsx    # 매크로 모니터
│   │       │   ├── reserve/page.tsx  # 열차 검색/예매
│   │       │   ├── reservations/page.tsx # 예약 확인
│   │       │   └── settings/page.tsx # 설정
│   │       ├── components/layout/
│   │       │   ├── Header.tsx        # 데스크톱 네비게이션
│   │       │   └── BottomNav.tsx     # 모바일 하단 네비게이션
│   │       ├── lib/
│   │       │   ├── api.ts            # API 클라이언트, JWT 관리
│   │       │   ├── types.ts          # TypeScript 타입
│   │       │   ├── constants.ts      # 상수
│   │       │   └── ws.ts            # WebSocket 클라이언트
│   │       └── styles/globals.css
│   └── venv/                     # Python 가상환경 (gitignore)
```

## 기술 스택

| 영역 | 기술 |
|------|------|
| 백엔드 | Python 3.12, FastAPI, uvicorn, aiosqlite |
| 인증 | python-jose (JWT HS256, 7일 만료), bcrypt (passlib 아님) |
| DB | SQLite (web/data/srtgo.db), 자동 마이그레이션 |
| 암호화 | cryptography (Fernet) — SRT/KTX 자격증명, 카드정보, TG 토큰 |
| 프론트엔드 | Next.js 14, React 18, TypeScript, Tailwind CSS 3.4 |
| 텔레그램 | python-telegram-bot |
| 코어 | srtgo 패키지 (SRT/KTX API), curl_cffi |

## 인증 시스템

- JWT 기반 다중 사용자 (HS256, 7일 만료)
- 첫 가입자 = 자동 admin (auto-approved)
- 이후 가입자 = admin 승인 필요 (관리자 페이지 또는 텔레그램)
- 사용자별 설정 격리 (user_id on settings/macro_history)
- 비밀번호: 직접 `bcrypt` 모듈 사용 (passlib 비호환으로 제거)

---

## 환경변수

### web/.env
```env
SRTGO_SECRET_KEY=<hex-64>           # 설정값 암호화 키 (필수)
SRTGO_JWT_SECRET=<hex-64>           # JWT 서명 키 (미설정 시 SECRET_KEY 사용)
SRTGO_ADMIN_TG_TOKEN=<봇 토큰>      # 관리자 텔레그램 봇 (선택)
SRTGO_ADMIN_TG_CHAT_ID=<채팅 ID>    # 관리자 텔레그램 채팅 (선택)
SRTGO_CORS_ORIGINS=http://localhost:3000  # CORS 허용 도메인
SRTGO_DB_PATH=<경로>                # DB 경로 (기본: web/data/srtgo.db)
```

### web/frontend/.env.local
```env
NEXT_PUBLIC_API_URL=http://localhost:8000
```

> **키 생성:** `python -c "import secrets; print(secrets.token_hex(32))"`

---

## API 엔드포인트

### 공개 (인증 불필요)
| Method | Path | 설명 |
|--------|------|------|
| GET | /api/health | 서버 상태 |
| POST | /api/user/register | 회원가입 (첫 가입자=admin) |
| POST | /api/user/login | 로그인 → JWT |
| GET | /api/trains/stations | 역 목록 |

### JWT 인증 필요
| Method | Path | 설명 |
|--------|------|------|
| GET | /api/user/me | 내 정보 |
| GET/PUT | /api/settings/* | 사용자별 설정 |
| POST | /api/trains/search | 열차 검색 |
| GET/POST | /api/reservations/* | 예약 조회/결제/취소 |
| POST | /api/macro/start | 매크로 시작 |
| GET | /api/macro/list | 활성 매크로 목록 |
| DELETE | /api/macro/{task_id} | 매크로 취소 |

### Admin 전용
| Method | Path | 설명 |
|--------|------|------|
| GET | /api/admin/users | 전체 사용자 목록 |
| PUT | /api/admin/users/{id}/approve | 사용자 승인 |
| PUT | /api/admin/users/{id}/reject | 사용자 거절 |
| DELETE | /api/admin/users/{id} | 사용자 삭제 |

### WebSocket
- `/ws/macro/{task_id}` — 매크로 실시간 진행 (인증 없음, UUID 기반)

---

## DB 스키마

### users
```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    nickname TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'user',       -- 'admin' | 'user'
    status TEXT NOT NULL DEFAULT 'pending',  -- 'pending' | 'approved' | 'rejected'
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);
```

### settings
```sql
CREATE TABLE settings (
    user_id INTEGER NOT NULL DEFAULT 0,
    category TEXT NOT NULL,
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    encrypted INTEGER DEFAULT 0,
    updated_at TEXT DEFAULT (datetime('now')),
    UNIQUE(user_id, category, key)
);
```

### macro_history
```sql
CREATE TABLE macro_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL DEFAULT 0,
    rail_type TEXT, departure TEXT, arrival TEXT,
    date TEXT, time TEXT, passengers TEXT,
    seat_type TEXT, auto_pay INTEGER,
    selected_trains TEXT, status TEXT,
    attempts INTEGER, elapsed_seconds REAL,
    result_text TEXT,
    created_at TEXT, finished_at TEXT
);
```

> 스키마 자동 생성/마이그레이션: `database.py init_db()`

---

## 주의사항

- `passlib` 사용 금지 — 직접 `bcrypt` 모듈 사용
- JWT `sub`는 문자열: `str(user_id)` → 토큰, `int(payload["sub"])` → 읽기
- WebSocket `/ws/{task_id}`는 인증 없음 (task_id가 UUID라 추측 불가)
- SQLite 마이그레이션은 `database.py init_db()`에서 자동 실행

## 의존성

모든 Python 의존성: `web/requirements.txt`
srtgo 코어 엔진: 프로젝트 루트에서 `pip install -e .`로 설치

---

## 새 PC 세팅 가이드

Claude Code에게 **"이 프로젝트 세팅해줘"** 라고 하면 아래 절차를 수행합니다.

### 1. 사전 설치 (수동)
1. **Python 3.12+**: https://www.python.org/downloads/ (Add to PATH 체크)
2. **Node.js 20+**: https://nodejs.org/
3. **Git**: https://git-scm.com/

### 2. 프로젝트 클론 + 백엔드 세팅
```bash
git clone https://github.com/hyummys/srtgo.git
cd srtgo

# Python 가상환경
python -m venv web/venv

# Windows
web\venv\Scripts\activate
# Linux/Mac
source web/venv/bin/activate

# srtgo 코어 설치 (프로젝트 루트에서)
pip install -e .

# 백엔드 의존성
pip install -r web/requirements.txt
```

### 3. 프론트엔드 세팅
```bash
cd web/frontend
npm install
cd ../..
```

### 4. 환경변수 설정

`web/.env` 생성:
```env
SRTGO_SECRET_KEY=<python -c "import secrets; print(secrets.token_hex(32))" 로 생성>
SRTGO_JWT_SECRET=<위와 같은 방식으로 별도 생성>
SRTGO_ADMIN_TG_TOKEN=<텔레그램 봇 토큰 - 없으면 줄 삭제>
SRTGO_ADMIN_TG_CHAT_ID=<텔레그램 채팅 ID - 없으면 줄 삭제>
SRTGO_CORS_ORIGINS=http://localhost:3000
```

`web/frontend/.env.local` 생성:
```env
NEXT_PUBLIC_API_URL=http://localhost:8000
```

### 5. 서버 실행
```bash
# 터미널 1: 백엔드 (프로젝트 루트에서)
# Windows
web\venv\Scripts\python.exe -m web.backend.run
# Linux/Mac
web/venv/bin/python -m web.backend.run

# 터미널 2: 프론트엔드
cd web/frontend && npm run dev
```

백엔드: http://localhost:8000 / 프론트엔드: http://localhost:3000

### 6. 첫 실행 후
- 첫 번째 회원가입 사용자가 자동으로 **admin**
- 이후 가입자는 admin 승인 필요 (관리자 페이지 또는 텔레그램)

---

## Cloudflare Tunnel로 외부 공개 (선택)

PC에서 돌리는 서버를 외부에서 접속 가능하게 만드는 방법. 무료, 신용카드 불필요.

### 1. Cloudflare 계정 + 도메인
1. https://dash.cloudflare.com 가입 (무료)
2. 도메인 있으면 Cloudflare DNS로 연결
3. 도메인 없으면 `*.trycloudflare.com` 임시 URL 사용 가능

### 2. cloudflared 설치
- **Windows**: https://github.com/cloudflare/cloudflared/releases → `cloudflared-windows-amd64.msi`
- **Linux**: `curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o /usr/local/bin/cloudflared && chmod +x /usr/local/bin/cloudflared`
- **Mac**: `brew install cloudflared`

### 3-A. 빠른 테스트 (임시 URL, 계정 불필요)
```bash
cloudflared tunnel --url http://localhost:8000
```
출력되는 `https://xxxxx.trycloudflare.com` URL로 접속.

### 3-B. 커스텀 도메인 (영구 설정)
```bash
# 최초 1회
cloudflared tunnel login
cloudflared tunnel create srtgo

# ~/.cloudflared/config.yml 작성:
tunnel: <터널 ID>
credentials-file: ~/.cloudflared/<터널 ID>.json

ingress:
  - hostname: api.yourdomain.com
    service: http://localhost:8000
  - hostname: yourdomain.com
    service: http://localhost:3000
  - service: http_status:404

# DNS 등록 + 실행
cloudflared tunnel route dns srtgo api.yourdomain.com
cloudflared tunnel route dns srtgo yourdomain.com
cloudflared tunnel run srtgo
```

### 4. Tunnel 사용 시 환경변수 변경
```env
# web/.env
SRTGO_CORS_ORIGINS=http://localhost:3000,https://yourdomain.com

# web/frontend/.env.local
NEXT_PUBLIC_API_URL=https://api.yourdomain.com
```

프론트엔드 재빌드: `cd web/frontend && npm run build && npm start`
