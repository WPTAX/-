# 아이언 프로젝트 v4 — Garmin/Health Connect

## 핵심 변경
- Garmin Connect → Health Connect 경유 자동 러닝 불러오기
- 삼성헬스에 고정되지 않고 Health Connect의 러닝 세션을 모두 읽음
- 동일 운동이 여러 앱에서 겹치면 앱 화면에서 중복을 최대한 제거
- 주간 목표시간(기본 160분) / 완료시간 / 남은시간 / 달성률
- 누락 러닝 수기 입력 및 삭제
- Health Connect 수면 자동 읽기 + 수면 수기 입력(수기가 우선)
- 주간 러닝 바 차트, 최근 기록, 평균 심박/거리/횟수
- 초록 + 파랑 UI, 도트 캐릭터는 헤더에만 사용

## Garmin 설정
Garmin Connect 앱에서 Health Connect 공유를 켠 뒤, 아이언 프로젝트에서 Health Connect 권한을 허용합니다.
앱은 Garmin API에 직접 로그인하지 않고 Health Connect를 통해 데이터를 읽습니다.

## APK 빌드
GitHub에 이 프로젝트 전체를 업로드한 뒤 Actions > Build APK > Run workflow.
빌드 산출물: `아이언-프로젝트-이웅희-APK` artifact.

## 참고
현재 채팅 실행환경에는 Android SDK/Gradle이 설치되어 있지 않아 APK 자체를 로컬 빌드하지 못하고, GitHub Actions 빌드 설정을 포함한 소스 프로젝트를 제공합니다.


## v4.1 install fix
- applicationId changed to `com.ironproject.woonghee.v4` to avoid conflicts with prior debug-signing installs.
