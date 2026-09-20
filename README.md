# Health

헬스와 러닝 기록을 한 앱에서 관리하는 **개인용** Android 앱입니다.
로그인 · 회원가입 · 친구 · 랭킹 · 결제 같은 기능은 없고, 서버도 쓰지 않습니다.
모든 데이터는 기기 안의 Room DB 에만 저장되는 **오프라인 우선** 구조입니다.

가장 중요한 UX 원칙은 **운동 중 스마트폰 조작 횟수를 최소화**하는 것입니다.

---

## 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| 언어 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 구조 | MVVM (UI / ViewModel / Repository / Database) |
| DB | Room 2.6.1 (KSP) |
| 설정 | DataStore (Preferences) |
| DI | Hilt 2.52 |
| 위치 | Fused Location Provider + Foreground Service |
| 건강 데이터 | Health Connect *(Phase 4)* |
| minSdk | **26** (Health Connect 클라이언트 요구사항) |
| targetSdk / compileSdk | 35 |

---

## 아키텍처

```
UI (Compose)  →  ViewModel  →  Repository(interface)  →  RepositoryImpl  →  DAO  →  Room
                                       ↑                                          DataStore
                                  domain/model
```

* **UI 는 domain 모델만 안다.** Room 엔티티는 `data` 패키지 밖으로 나가지 않습니다.
* **ViewModel 은 Repository 인터페이스에만 의존**하므로 테스트에서 쉽게 바꿔 끼울 수 있습니다.
* 화면은 **DB Flow 를 구독**하고, 모든 변경은 즉시 DB 에 기록됩니다.
  덕분에 운동 중 앱이 죽어도 진행 상황이 남습니다.
* 계산식(예상 1RM, 볼륨)은 **Kotlin 한 곳에만** 둡니다. SQL 로 중복 구현하지 않습니다.

### 폴더 구조

```
app/src/main/java/com/windowhyun/health/
├── HealthApplication.kt          # Hilt 엔트리 포인트, 알림 채널 생성
├── MainActivity.kt               # 단일 Activity + Compose
├── core/
│   ├── model/                    # BodyPart, ExerciseCategory, WeightUnit, PersonalRecord …
│   ├── util/                     # 포맷터, 날짜 확장, Epley 1RM · 볼륨 · Haversine · 페이스
│   ├── notification/             # 휴식 타이머 진동 · 알림
│   └── designsystem/theme/       # Material 3 Light / Dark 테마, 타이포그래피
├── data/
│   ├── local/
│   │   ├── HealthDatabase.kt     # @Database (v1)
│   │   ├── Converters.kt         # enum ↔ String
│   │   ├── entity/               # 테이블 정의
│   │   ├── dao/                  # 쿼리
│   │   └── relation/             # @Relation 조회 결과
│   ├── location/                 # FusedLocationTracker
│   ├── tracking/                 # RunMetricsAccumulator, RunTracker
│   ├── datastore/                # SettingsRepositoryImpl
│   ├── mapper/                   # Entity ↔ Domain 변환
│   ├── repository/               # Repository 구현
│   └── seed/                     # 기본 운동 종목 41개 + 시드 콜백
├── domain/
│   ├── model/                    # Exercise, Routine, Workout, Run, RunTrackingState …
│   ├── repository/               # Repository / LocationTracker 인터페이스
│   └── usecase/                  # PersonalRecordCalculator
├── service/                      # RunTrackingService(Foreground), RunServiceController
├── di/                           # Hilt 모듈 (App / Database / Repository / Location)
└── ui/
    ├── navigation/               # Routes, 하단 탭, NavHost
    ├── components/               # 공통 Composable
    ├── home/                     # 홈
    ├── gym/                      # 루틴 목록 · 루틴 편집 · 운동 선택 시트
    ├── session/                  # 운동 진행 · 세트 입력 · 휴식 타이머 · 종료 요약
    ├── history/                  # 기록 목록 · 기록 상세
    ├── running/                  # 러닝 시작 · 진행 · 결과 · 경로 그리기
    └── settings/                 # 설정
```

### 러닝 기록 구조

```
RunTrackingService (Foreground)
      │  GPS 샘플          1초 타이머
      ▼                        ▼
   RunTracker ──► RunMetricsAccumulator   (거리 · 페이스 · Lap 계산)
      │  StateFlow                │
      ▼                           ▼
  ViewModel / UI            RunRepository ──► Room (5초마다 증분 저장)
```

* 화면은 서비스에 **바인딩하지 않고** `RunTracker.state` 만 구독한다.
  화면이 꺼지거나 회전해도 기록에 영향이 없다.
* 계산기는 `android.location.Location` 대신 자체 `LocationSample` 을 받아서
  **거리 · 페이스 · Lap 로직 전체를 JVM 단위 테스트로 검증**한다.
* GPS 노이즈 대응: 정확도 30m 초과 · 3m 미만 이동 · 12m/s 초과 속도는 버린다.
* 일시정지 구간은 거리에 넣지 않고 경로도 끊어서 그린다.
* 경로는 지도 SDK 없이 Compose Canvas 로 그린다(API 키·네트워크 불필요).

---

## 데이터 모델

```
exercise ──┬── routine_exercise ──── routine
           │
           ├── workout_exercise ──── workout ──── (routineId, SET NULL)
           │        └── workout_set
           └── personal_record ───── workout

run ──┬── run_lap
      └── run_location
```

| 테이블 | 설명 | 주요 삭제 규칙 |
| --- | --- | --- |
| `exercise` | 운동 종목 사전 (기본 제공 + 사용자 추가) | — |
| `routine` | 루틴. 요일은 비트마스크로 저장 | — |
| `routine_exercise` | 루틴 안의 운동 + 순서 + 기본 세트 + 휴식시간 | routine CASCADE |
| `workout` | 운동 세션. `endTime = null` 이면 진행 중 | routine **SET NULL** (루틴을 지워도 기록은 남음) |
| `workout_exercise` | 세션 안의 운동 | workout CASCADE |
| `workout_set` | 세트(중량 kg, 반복, 완료 여부) | workout_exercise CASCADE |
| `personal_record` | 그 세션에서 **새로 세운** PR | workout CASCADE |
| `run` / `run_lap` / `run_location` | 러닝 (Phase 2 에서 사용) | run CASCADE |

저장 단위는 항상 **kg / meter** 이고, kg↔lb · km↔mile 변환은 화면에서만 합니다.

---

## 개발 단계

| Phase | 내용 | 상태 |
| --- | --- | --- |
| **Phase 1** | Room DB · 운동 종목 · 루틴 · 운동 진행 · 세트 기록 · 휴식 타이머 · 운동 저장 · PR · 과거 기록 | ✅ 완료 |
| **Phase 2** | GPS 권한 · Foreground Service · 러닝 기록 · 거리/페이스 · 자동 Lap · 경로 · 러닝 저장 | ✅ 완료 |
| Phase 3 | 캘린더 · 헬스/러닝 통계 · 성장 그래프 · 러닝 기록 상세 | ⬜ 예정 |
| Phase 4 | Health Connect · 백업/복원 · UI/UX 개선 | ⬜ 예정 |

각 Phase 가 끝날 때마다 앱은 항상 실행 가능한 상태를 유지합니다.
러닝 관련 테이블은 마이그레이션을 줄이기 위해 스키마 v1 에 미리 포함했습니다.

> Phase 2 기준으로 저장된 러닝은 **홈 화면의 "최근 러닝 기록"** 에서 볼 수 있습니다.
> 기록 탭의 캘린더와 러닝 상세 화면은 Phase 3 에서 붙습니다.

---

## 실행 방법

1. Android Studio(Ladybug 이상)로 프로젝트를 엽니다.
2. `local.properties` 의 `sdk.dir` 이 로컬 Android SDK 경로를 가리키는지 확인합니다.
3. Android 8.0(API 26) 이상 기기 또는 에뮬레이터를 연결합니다.
4. `Run ▶` 또는 터미널에서:

```bash
./gradlew :app:installDebug
```

APK 만 만들려면:

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

### 릴리즈 APK

기기에 설치해서 쓰려면 서명된 릴리즈 APK 가 필요합니다.
키스토어 준비부터 서명 검증까지의 전체 과정과 실제 빌드 기록은
**[docs/RELEASE.md](docs/RELEASE.md)** 에 정리해 두었습니다.

```bash
./gradlew clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

버전별 변경 내용은 [CHANGELOG.md](CHANGELOG.md) 를 참고하세요.

---

## 테스트 방법

```bash
./gradlew :app:testDebugUnitTest      # 단위 테스트 (Robolectric 포함, 기기 불필요)
./gradlew :app:lintDebug              # 정적 분석
```

테스트 리포트: `app/build/reports/tests/testDebugUnitTest/index.html`

| 테스트 | 확인하는 것 |
| --- | --- |
| `OneRepMaxTest` | Epley 1RM, 세트 볼륨 계산 |
| `FormattersTest` | 시간 · 중량 · 거리 · 페이스 표기, 단위 왕복 변환 |
| `PersonalRecordCalculatorTest` | 최고 중량 / 최고 볼륨 / 예상 1RM PR 판정 |
| `DayMaskTest` | 루틴 요일 비트마스크 변환 |
| `WorkoutRepositoryTest` | 인메모리 Room 으로 "시작 → 기록 → 종료" 전체 흐름 |
| `RoutineRepositoryTest` | 루틴 순서 유지, 요일별 조회, 수정 시 정렬 보존 |
| `ExerciseSeedTest` | 첫 실행 시 기본 종목 시드 |
| `GeoUtilsTest` | Haversine 거리, 페이스, 칼로리 추정 |
| `RunMetricsAccumulatorTest` | 거리 누적, GPS 노이즈 제거, 자동 Lap, 일시정지, 최고 페이스 |
| `RunRepositoryTest` | 러닝 증분 저장과 개인 기록 판정 |

### 손으로 확인해 볼 시나리오

1. 헬스 탭 → **루틴 만들기** → 이름 입력 → 운동 추가 → 순서 변경 → 저장
2. 루틴의 **운동 시작** → 세트 완료 → 휴식 타이머가 자동으로 뜨는지 확인
3. **운동 종료** → 총 볼륨 / 세트 수 / 새 PR 확인 → 메모 저장
4. 같은 루틴을 **다시 시작** → 지난 중량·횟수가 기본값으로 채워져 있는지 확인
5. 앱을 강제 종료한 뒤 다시 실행 → 홈에 **이어하기** 카드가 뜨는지 확인
6. 기록 탭 → 기록 선택 → 세트 수정 · 메모 수정 · 삭제

러닝(Phase 2, **실제 기기 권장** — 에뮬레이터는 위치를 모의 주입해야 합니다):

7. 러닝 탭 → 위치 권한 허용 → GPS 준비됨 확인
8. 모드 선택(자유 / 목표 거리 / 목표 시간) → **러닝 시작**
9. 걷거나 달리면서 거리·현재 페이스가 갱신되는지 확인
10. **화면을 끄고** 몇 분 뒤 다시 켜기 → 시간과 거리가 계속 쌓였는지 확인
11. 일시정지 → 자리를 옮긴 뒤 계속 → 옮긴 거리가 더해지지 않고 경로가 끊겼는지 확인
12. 1km 를 넘기면 Lap 이 자동으로 생기는지 확인
13. 종료 → 결과 화면에서 경로·Lap·칼로리·개인기록 확인 → 메모 후 저장
14. 목표 거리/시간 모드로 시작해 목표에 도달하면 자동으로 종료되는지 확인

> 에뮬레이터에서는 Extended Controls → Location 에서 GPX/KML 경로를 재생하면
> 실제 이동과 비슷하게 테스트할 수 있습니다.
