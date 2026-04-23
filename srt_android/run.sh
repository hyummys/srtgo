#!/bin/bash
set -e

# ============================================================
# run.sh - 릴리즈 APK 빌드 & 디바이스 실행 스크립트
# Android Studio의 Run 버튼과 동일한 동작
# ============================================================

APP_ID="com.srtgo.app"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
MAIN_ACTIVITY="${APP_ID}.MainActivity"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_step() {
    echo -e "${CYAN}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✔ $1${NC}"
}

print_error() {
    echo -e "${RED}✘ $1${NC}"
}

print_warn() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# ----------------------------------------------------------
# 1) ADB 디바이스 확인
# ----------------------------------------------------------
print_step "연결된 디바이스 확인 중..."

DEVICE_COUNT=$(adb devices | grep -c -w "device$" || true)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    print_error "연결된 디바이스가 없습니다."
    echo "   adb devices 명령으로 확인해주세요."
    exit 1
elif [ "$DEVICE_COUNT" -gt 1 ]; then
    print_warn "여러 디바이스가 연결되어 있습니다. 첫 번째 디바이스를 사용합니다."
    DEVICE_SERIAL=$(adb devices | grep -w "device$" | head -n1 | awk '{print $1}')
    ADB="adb -s $DEVICE_SERIAL"
    echo "   디바이스: $DEVICE_SERIAL"
else
    DEVICE_SERIAL=$(adb devices | grep -w "device$" | awk '{print $1}')
    ADB="adb"
    echo "   디바이스: $DEVICE_SERIAL"
fi

print_success "디바이스 준비 완료"

# ----------------------------------------------------------
# 2) 릴리즈 APK 빌드
# ----------------------------------------------------------
print_step "릴리즈 APK 빌드 중... (시간이 소요됩니다)"

BUILD_START=$(date +%s)
./gradlew assembleRelease --quiet
BUILD_END=$(date +%s)
BUILD_TIME=$((BUILD_END - BUILD_START))

if [ ! -f "$APK_PATH" ]; then
    print_error "APK 파일을 찾을 수 없습니다: $APK_PATH"
    echo "   빌드 로그를 확인해주세요: ./gradlew assembleRelease"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | awk '{print $1}')
print_success "빌드 완료 (${BUILD_TIME}초, ${APK_SIZE})"

# ----------------------------------------------------------
# 3) 앱 설치
# ----------------------------------------------------------
print_step "앱 설치 중..."

# -r: 기존 앱 위에 덮어설치, -d: 다운그레이드 허용
$ADB install -r -d "$APK_PATH"
print_success "앱 설치 완료"

# ----------------------------------------------------------
# 4) 앱 실행
# ----------------------------------------------------------
print_step "앱 실행 중..."

$ADB shell am start -n "${APP_ID}/${MAIN_ACTIVITY}" \
    -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER

print_success "앱이 실행되었습니다!"
echo ""
echo -e "${GREEN}═══════════════════════════════════════${NC}"
echo -e "${GREEN}  빌드 & 실행 완료! (총 ${BUILD_TIME}초)${NC}"
echo -e "${GREEN}═══════════════════════════════════════${NC}"
