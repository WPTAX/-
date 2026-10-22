# 아이언 프로젝트 v4.4 — v4.3 소스 기반 수정본

이 프로젝트는 바탕화면에서 찾은 v4.3 소스와 기존 APK를 기준으로 수정했습니다.
현재 상태는 **로컬 JavaScript 테스트 통과 / Android 컴파일 및 기기 설치 미검증**입니다.
새 APK가 생성되었다거나 삼성폰 설치 문제가 해결되었다고 아직 확정할 수 없습니다.

## 기능

- Garmin Connect → Health Connect로 공유된 러닝·거리·심박·수면 읽기
- 앱 최초 화면 로드 및 앱으로 돌아올 때 자동 동기화, 수동 새로고침
- 앱이 닫힌 동안 백그라운드 동기화는 하지 않음
- 러닝/수면 권한을 독립 처리; 거리/심박이 없어도 러닝 시간 표시
- 과거 기록 권한이 있으면 최근 180일, 없으면 최근 29일 조회
- 페이지가 1,000개 기록을 넘으면 다음 페이지까지 조회
- 성공한 조회 구간만 자동 캐시 교체, 읽기 실패·미허용 항목은 기존값 유지
- 수기 러닝 입력/삭제, 수기 수면 입력/삭제, 기기 내부 저장
- 기본 주간 목표 160분, 사용자 목표 30~1000분, 완료·남은 시간은 분:초
- 월요일 시작 주간 통계, 요일별 그래프, 최근 8주 추이, 최근 수면
- 수기 기록·목표 백업/복원. 자동 기록은 백업에 포함하되 복원 시 다시 동기화
- 초록·파랑 디자인, 상단의 작은 도트 캐릭터, Android 시스템바·키보드 여백 처리

자동 러닝 시간은 Health Connect 세션의 시작~종료 시간입니다. Garmin의 일시정지가
포함된 경우 Garmin 화면의 실제 운동시간과 다를 수 있습니다. 자동 수면도 깨어 있는
시간을 포함한 세션 시간으로 표시합니다. 수기 수면은 같은 날짜 자동값을 대신하며,
수기 삭제 시 자동값이 복원됩니다. Garmin이 공유하지 않은 항목은 앱에서 만들 수 없습니다.

자동 기록 간 중복은 같은 원본 ID 또는 서로 다른 출처의 시작시각 1분 이내·시간 1분
이내 기록을 기준으로 Garmin을 우선합니다. 수기 러닝은 자동으로 제거하지 않습니다.
비슷한 자동 기록이 있으면 추가 전에 확인합니다. 사용자가 중복 수기를 삭제할 수 있습니다.

## 기존 APK 조사 결과

- 파일 크기: 10,882,761 bytes
- SHA-256: `37a7cd69c9f03174903cc5b8774305dcef27638e9ec71b2dc81b3235153b7b14`
- 실제 버전: 4.2-compatible / versionCode 42 (v4.3 소스 폴더와 동일한 화면 파일)
- 패키지: `com.ironproject.woonghee.v42`
- minSdk 28 / targetSdk 35 / debuggable true / testOnly 미설정
- ABI 제한 없음: APK에 native .so 라이브러리 없음
- ZIP CRC 검사 통과
- Node crypto로 APK v2 RSA 서명, 인증서-공개키 일치, 전체 콘텐츠 다이제스트 검증 통과
- 서명 지문 SHA-256: `EA:6F:70:E7:50:C3:7B:C7:84:4E:53:B6:85:35:E7:13:20:F8:AA:AF:C8:2A:4E:57:09:91:9D:98:33:B8:4E:9A`

이 결과는 Android의 apksigner 또는 실제 설치 시험을 대체하지 않습니다. 기존 APK에서
손상·서명 누락·testOnly 제한·CPU 제한은 발견하지 못했습니다. 삼성폰의 실제 설치
실패 코드는 아직 확보하지 못했으므로 원인은 미확정입니다.

확정한 별도 결함: 기존 화면 스크립트의 `s++x.minutes` 문법 오류로 전체 스크립트가
실행되지 않습니다. 이 오류는 설치 실패 원인이 아니라 **설치 후 화면 동작 결함**입니다.
기존 GitHub Actions는 매번 새 디버그 키를 만들 수 있어 업데이트 서명 충돌 가능성이
있지만, 해당 삼성폰에서 발생한 원인이라고 확인한 것은 아닙니다.

## GitHub Actions

프로젝트 루트의 숨김 `.github` 폴더까지 저장소에 포함하세요. Gradle Wrapper는 원본에
없으며, workflow가 Gradle 8.11.1을 설치합니다. JDK 17, AGP 8.10.1, compileSdk 36,
targetSdk 35, Build Tools 35.0.0, Health Connect 1.1.0을 고정 사용합니다.
대상 실제 기기는 Z Fold7 / SM-F966N / Android 16입니다. 세로 화면 고정을 제거하여
접기/펼치기·가로 화면에서도 반응형 레이아웃을 사용합니다. 실제 폴드 전환 검증은 남아 있습니다.

### 1. Validate Android project

push/PR/수동 실행 시 JavaScript 검증, debug/release 컴파일, lint, 계측 테스트 컴파일을
수행합니다. 별도 Android API 28/35/36 에뮬레이터 작업에서 수기 저장·재실행 지속성을
확인합니다. 이 workflow는 임시 디버그 서명 APK나 unsigned APK를 설치용으로 배포하지 않습니다.

### 2. 고정 서명키 설정 (최초 한 번)

이미 키가 있다면 **같은 키를 재사용**하세요. APK의 공개 인증서에서 기존 개인키를 복구할 수는 없습니다.
JDK 17이 설치된 PC에서 프로젝트 루트에서 다음을 실행하면 새 개인용 키를 만듭니다.

```powershell
./scripts/create-signing.ps1
```

저장소 Settings → Secrets and variables → Actions → New repository secret에 다음을 등록합니다.

| Secret | 값 |
|---|---|
| IRON_KEYSTORE_BASE64 | `.private/IRON_KEYSTORE_BASE64.txt` 내용 |
| IRON_STORE_PASSWORD | 생성할 때 입력한 암호 |
| IRON_KEY_ALIAS | `iron` (기존 키면 그 키의 alias) |
| IRON_KEY_PASSWORD | 생성할 때 입력한 암호 |

개인키·암호·BASE64 파일을 소스에 커밋하지 마세요. 다음 버전도 같은 키를 사용합니다.
기존 설치본과 서명이 다르면 업데이트할 수 없습니다. 앱 데이터 삭제/제거에 앞서 수기
기록 백업을 확보하고, 실제 오류 코드를 확인하세요. 패키지 ID를 계속 바꾸는 방식은 사용하지 않았습니다.

### 3. Build installable APK

Actions → Build installable APK → Run workflow. 고정 서명 release APK를 만든 뒤:

1. apksigner 서명 검증
2. zipalign 정렬 검사
3. 패키지·버전·minSdk·targetSdk·testOnly·debuggable·Activity·ABI 검사
4. Android 16 (API 36) 에뮬레이터에서 같은 서명 APK 신규 설치·실행·업데이트·재실행
5. 위 단계가 모두 성공하면 `iron-project-4.4-installable` artifact 업로드

artifact ZIP을 풀면 `iron-project-4.4.apk`, SHA256SUMS.txt, 서명/manifest 검사 결과,
에뮬레이터 화면과 런타임 오류 로그가 들어 있습니다. 키 파일은 artifact에 포함되지 않습니다.
이 workflow는 준비되었으나 현재 작업 환경에서 실행하지 못했습니다.

## 삼성폰 설치 실패 확인

휴대폰의 설정 → 휴대전화 정보에서 모델명, 소프트웨어 정보에서 Android/One UI 버전을
확인합니다. USB 디버깅을 연결한 PC의 Android SDK platform-tools에서:

```powershell
./scripts/install-diagnose.ps1 -Apk ./iron-project-4.4.apk
```

스크립트는 데이터 삭제 없이 `adb install -r`을 시도하여 `INSTALL_FAILED_*` 상세 코드를
보여줍니다. 제거 명령이나 테스트 제한을 우회하는 `-t` 옵션은 사용하지 않습니다.
기기에서 실행한 결과가 있어야 기존 문제 재현 및 해결을 판정할 수 있습니다.

## 로컬 검증

```powershell
$env:TZ='Asia/Seoul'
node --check app/src/main/assets/app.js
node --check app/src/main/assets/model.js
node --test tests/*.test.cjs
```

25개 JavaScript 검증 통과: 160분 목표/초 단위 잔여시간, 월요일·연도 경계, 한국 자정,
가중 평균심박, 수기 기록 보존, 교차 출처 중복, 조회 구간 교체, 수면 중복/수기 우선,
입력검증, 메모 escape, 전체 화면 코드 초기화, 저장·재시작·백업 복원.
이 검증은 DOM 대역과 계산 로직을 사용하며 실제 Android WebView 렌더링 시험과 구별됩니다.

## 참고한 공식 문서

- [Health Connect 시작 및 권한 화면](https://developer.android.com/health-and-fitness/health-connect/get-started)
- [페이지 조회·과거 기록 권한](https://developer.android.com/health-and-fitness/health-connect/read-data)
- [AGP 8.10 / API 36 지원](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
- [apksigner](https://developer.android.com/tools/apksigner)
- [Garmin → Health Connect](https://support.garmin.com/en-IN/?faq=JToBEy0jfe6pIygark2Ui5)
