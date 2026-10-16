# 아이언 프로젝트_이웅희 v4.2

설치 호환성을 위해 예전에 정상 설치되었던 환경에 맞춘 버전입니다.

## 주요 기능
- Garmin Connect → Health Connect를 통한 러닝 자동 읽기
- 러닝 시간/거리/평균 심박 자동 표시
- 주간 목표 기본 160분, 완료/남은 시간 자동 계산
- 누락된 러닝 기록 직접 입력 및 삭제
- 수면시간 Health Connect 자동 연동 또는 직접 입력
- 주간 러닝 시간/거리/평균 심박/횟수 표시
- 초록+파랑 중심의 밝은 UI, 도트 캐릭터는 홈 상단에만 사용

## 설치 호환성 변경
- compileSdk 36
- targetSdk 35
- Android Gradle Plugin 8.7.3
- Gradle 8.11.1
- 별도 package: com.ironproject.woonghee.v42

## GitHub에서 APK 만들기
1. 이 ZIP의 내용 전체를 저장소에 업로드하고 Commit합니다.
2. Actions → Build APK → Run workflow를 실행합니다.
3. 빌드가 초록 체크로 끝나면 Summary 맨 아래 Artifacts에서 `iron-project-v4.2-apk`를 받습니다.
4. 압축을 풀어 `app-debug.apk`를 휴대폰에 설치합니다.
