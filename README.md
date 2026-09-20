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
| 위치 | Fused Location Provider + Foreground Service *(Phase 2)* |
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
│   ├── util/                     # 포맷터, 날짜 확장, Epley 1RM / 볼륨 계산
│   ├── notification/             # 휴식 타이머 진동 · 알림
│   └── designsystem/theme/       # Material 3 Light / Dark 테마, 타이포그래피
├── data/
│   ├── local/
│   │   ├── HealthDatabase.kt     # @Database (v1)
│   │   ├── Converters.kt         # enum ↔ String
│   │   ├── entity/               # 테이블 정의
│   │   ├── dao/                  # 쿼리
│   │   └── relation/             # @Relation 조회 결과
│   ├── datastore/                # SettingsRepositoryImpl
│   ├── mapper/                   # Entity ↔ Domain 변환
│   ├── repository/               # Repository 구현
│   └── seed/                     # 기본 운동 종목 40여 개
├── domain/
│   ├── model/                    # Exercise, Routine, Workout, Run, AppSettings …
│   ├── repository/               # Repository 인터페이스
│   └── usecase/                  # PersonalRecordCalculator
├── di/                           # Hilt 모듈 (AppModule / DatabaseModule / RepositoryModule)
└── ui/
    ├── navigation/               # Routes, 하단 탭, NavHost
    ├── components/               # 공통 Composable
    ├── home/                     # 홈
    ├── gym/                      # 루틴 목록 · 루틴 편집 · 운동 선택 시트
    ├── session/                  # 운동 진행 · 세트 입력 · 휴식 타이머 · 종료 요약
    ├── history/                  # 기록 목록 · 기록 상세
    ├── running/                  # 러닝 (Phase 2)
    └── settings/                 # 설정
```

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
| Phase 2 | GPS 권한 · Foreground Service · 러닝 기록 · 거리/페이스 · 자동 Lap · 러닝 저장 | ⬜ 예정 |
| Phase 3 | 캘린더 · 헬스/러닝 통계 · 성장 그래프 | ⬜ 예정 |
| Phase 4 | Health Connect · 백업/복원 · UI/UX 개선 | ⬜ 예정 |

각 Phase 가 끝날 때마다 앱은 항상 실행 가능한 상태를 유지합니다.
러닝 관련 테이블은 마이그레이션을 줄이기 위해 스키마 v1 에 미리 포함했습니다.

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

### 손으로 확인해 볼 시나리오

1. 헬스 탭 → **루틴 만들기** → 이름 입력 → 운동 추가 → 순서 변경 → 저장
2. 루틴의 **운동 시작** → 세트 완료 → 휴식 타이머가 자동으로 뜨는지 확인
3. **운동 종료** → 총 볼륨 / 세트 수 / 새 PR 확인 → 메모 저장
4. 같은 루틴을 **다시 시작** → 지난 중량·횟수가 기본값으로 채워져 있는지 확인
5. 앱을 강제 종료한 뒤 다시 실행 → 홈에 **이어하기** 카드가 뜨는지 확인
6. 기록 탭 → 기록 선택 → 세트 수정 · 메모 수정 · 삭제
