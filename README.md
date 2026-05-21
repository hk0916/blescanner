# TraceTag Scanner

Skaichips TraceTag BLE 태그의 신호 송출 상태를 테스트하기 위한 안드로이드 BLE 스캐너 앱입니다.

## 주요 기능

- **실시간 BLE 스캔** — 주변 BLE 기기를 저지연(LOW_LATENCY) 모드로 스캔
- **MAC 필터 (2가지 방식)**
  - **Prefix**: MAC 접두사로 필터링 (예: `AC:23:3F` → 해당 접두사 태그만 표시)
  - **전체 MAC**: 정확한 MAC 일치 (콤마/줄바꿈으로 여러 개 동시 입력 가능)
- **RSSI 신호세기 표시** — dBm 수치 + 색상(초록/노랑/빨강) + 4단계 신호 막대
- **갱신 정보** — 마지막 감지 시각, 누적 감지 횟수
- **RSSI 내림차순 정렬** — 가장 가까운(강한) 태그가 맨 위로

## 설치 방법

### 방법 A — GitHub Actions로 APK 받기 (PC 환경 불필요) ⭐ 추천

1. 이 프로젝트를 GitHub 새 저장소에 올립니다.
   ```bash
   git init
   git add .
   git commit -m "TraceTag Scanner"
   git branch -M main
   git remote add origin https://github.com/<본인계정>/<저장소명>.git
   git push -u origin main
   ```
2. push되면 **Actions** 탭에서 `Build APK` 워크플로우가 자동 실행됩니다.
   (수동 실행은 Actions 탭 → Build APK → Run workflow)
3. 빌드 완료(약 3~5분) 후, 해당 실행 페이지 하단 **Artifacts** 에서
   `tracetag-scanner-debug-apk` 를 다운로드 → 압축 해제하면 `.apk` 파일이 나옵니다.
4. APK를 폰으로 옮겨 설치합니다. (설정에서 "출처를 알 수 없는 앱 설치" 허용 필요)

### 방법 B — Android Studio로 직접 빌드

1. Android Studio에서 이 폴더를 엽니다. (Gradle sync 자동 실행)
2. 폰을 USB로 연결하고 **개발자 옵션 → USB 디버깅** 을 켭니다.
3. 상단의 ▶ Run 버튼을 누르면 폰에 바로 설치/실행됩니다.
4. APK 파일만 필요하면 메뉴 **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
   결과물: `app/build/outputs/apk/debug/app-debug.apk`

## 사용법

1. 앱 실행 → 블루투스 권한 허용 / 블루투스 켜기 안내에 따라 진행
2. 필터 모드 선택:
   - **전체**: 모든 BLE 기기 표시 (필터 없음)
   - **Prefix**: TraceTag MAC 접두사 입력 (예: `AC:23:3F`)
   - **전체 MAC**: 특정 태그의 전체 MAC 입력
3. 하단 **스캔 시작** 버튼 클릭
4. 우리 태그가 잡히면 RSSI 값과 신호 막대가 실시간으로 갱신됨

## 참고 사항

- **minSdk 24 (Android 7.0) / targetSdk 34 (Android 14)**
- Android 12 이상은 `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` 권한 사용
  (`neverForLocation` 플래그로 위치 권한 회피)
- Android 11 이하는 BLE 스캔에 위치 권한(`ACCESS_FINE_LOCATION`)이 필요 — OS 정책
- **MAC 주소 주의**: 태그가 랜덤 주소(RPA)를 사용하면 MAC이 주기적으로 바뀌어
  prefix 필터가 동작하지 않을 수 있습니다. TraceTag가 퍼블릭(고정) MAC을 쓰는지
  확인이 필요하며, 랜덤 주소라면 Service UUID / Manufacturer Data 기반 필터로
  전환하는 것이 좋습니다. (현재는 RSSI 확인 목적이라 MAC 필터만 구현)

## 기술 스택

- Kotlin + Jetpack Compose (Material 3)
- `BluetoothLeScanner` (소프트웨어 필터링 방식)
- Kotlin Coroutines / StateFlow
- AndroidX Lifecycle (ViewModel + collectAsStateWithLifecycle)
