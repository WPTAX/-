# 아이언 프로젝트_이웅희 v2

## 포함 기능
- Samsung Health → Health Connect에서 달리기 세션 자동 읽기
- 러닝 시간 / 거리 / 평균 심박 자동 표시
- 이번 주 총 러닝 시간 자동 합산 (160분 목표)
- 수면시간 수기 입력
- 주간 / 월간 러닝 시간·거리·심박·수면 그래프
- 최근 러닝 기록
- 아침발기 / 강직도 / 성욕 입력 항목 없음

## 최초 사용
1. Samsung Health 최신 버전 확인
2. Samsung Health > 설정 > Health Connect 에서 데이터 공유 허용
3. 앱 실행 > `연결 / 권한 허용`
4. 운동, 거리, 심박, 과거 데이터 권한 허용
5. `기록 새로고침`

Galaxy Watch 기록은 Samsung Health가 휴대폰으로 동기화한 뒤 Health Connect에 들어옵니다.

## APK 만들기 (GitHub Actions)
1. 새 GitHub 저장소 생성
2. 이 ZIP 압축을 풀어 파일 전체를 저장소 루트에 업로드
3. GitHub `Actions` 탭 → `Build APK` → `Run workflow`
4. 완료된 작업 하단 `Artifacts`에서 `아이언-프로젝트-이웅희-APK` 다운로드
5. 압축을 풀고 `app-debug.apk`를 갤럭시에 설치

## 참고
- 이 앱은 Samsung Health의 Android 패키지(com.sec.android.app.shealth)가 Health Connect에 기록한 러닝/트레드밀 러닝만 읽도록 필터링합니다.
- Health Connect가 허용하는 범위에서 최대 최근 6개월을 표시하며, 과거 데이터 권한이 없으면 최근 30일 범위로 자동 재시도합니다.
- 백그라운드 건강 데이터 읽기는 사용하지 않습니다. 앱을 열 때 자동 동기화하며 `기록 새로고침`으로 즉시 갱신할 수 있습니다.
- 수면 기록은 앱 내부 localStorage에 저장되므로 앱 데이터 삭제 시 사라질 수 있습니다.
