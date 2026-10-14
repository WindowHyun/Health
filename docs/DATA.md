# 헬스 데이터 파일 (종목 · 루틴 템플릿)

기본 운동 종목과 루틴 템플릿은 코드가 아니라 JSON 파일에 있습니다.
Kotlin 을 건드리지 않고 파일만 고치면 됩니다. 고친 뒤에는 앱을 다시 빌드해야 반영됩니다
(파일은 APK 에 함께 들어갑니다).

| 파일 | 내용 |
| --- | --- |
| `app/src/main/resources/health/exercises.json` | 기본 운동 종목 |
| `app/src/main/resources/health/routine_templates.json` | 루틴 템플릿 |

형식과 규칙의 기준은 코드의 설명(KDoc)입니다: `ExerciseCatalog.kt`, `RoutineTemplateCatalog.kt`.

---

## 종목 추가

`exercises.json` 의 `exercises` 배열에 한 줄을 더합니다.

```json
{ "name": "벤치프레스", "category": "BARBELL", "bodyPart": "CHEST" },
{ "name": "플랭크", "category": "BODYWEIGHT", "bodyPart": "CORE", "trackingType": "TIME" }
```

| 키 | 값 |
| --- | --- |
| `name` | 종목 이름. 파일 안에서 겹치면 안 됩니다. |
| `category` | `BARBELL` `DUMBBELL` `MACHINE` `CABLE` `BODYWEIGHT` `KETTLEBELL` `CARDIO` `OTHER` |
| `bodyPart` | `CHEST` `BACK` `SHOULDER` `ARM` `LEG` `CORE` `FULL_BODY` `CARDIO` |
| `trackingType` (선택) | `WEIGHT_REPS`(기본, 중량×횟수) `REPS_ONLY`(횟수만) `TIME`(시간만) |
| `defaultRestSeconds` (선택) | 이 종목의 기본 휴식(5~600초). 없으면 앱 설정의 휴식을 씁니다. |

앱을 업데이트하면 이미 쓰던 기기에도 **빠진 종목만** 더해집니다. 있는 종목은 건드리지 않습니다.

> **이미 있는 종목의 이름을 바꾸거나 지우지 마세요.** 기록과 루틴 템플릿이 이름으로 종목을 가리킵니다.
> 이름을 바꾸면 새 종목이 하나 더 생기고 예전 이름은 그대로 남습니다.

---

## 루틴 템플릿 추가

`routine_templates.json` 의 `templates` 배열에 하나를 더합니다.

```json
{
  "id": "my_split",
  "category": "SPLIT",
  "title": "내 분할",
  "description": "목록에 보이는 설명. 횟수 목표나 대체 운동을 적어 두세요.",
  "days": [
    {
      "name": "하체 (화)",
      "scheduledDays": ["TUESDAY"],
      "exercises": [
        { "name": "스쿼트", "sets": 4, "rest": 120 },
        { "name": "케이블 푸시다운", "sets": 3, "rest": 60, "superset": "A" },
        { "name": "바벨 컬", "sets": 3, "rest": 75, "superset": "A" }
      ]
    }
  ]
}
```

| 키 | 설명 |
| --- | --- |
| `id` | 템플릿 식별자. 겹치면 안 됩니다. |
| `category` | `BEGINNER` `SPLIT` `STRENGTH` `EQUIPMENT` `FOCUS` (목록 갈래, 이 순서로 보입니다) |
| `days[].name` | 만들어지는 루틴 이름. **모든 템플릿을 통틀어** 겹치면 안 됩니다. |
| `days[].scheduledDays` (선택) | `MONDAY` … `SUNDAY`. 있으면 홈의 "오늘의 루틴"에 나옵니다. |
| `exercises[].name` | **종목 파일(`exercises.json`)에 있는 이름과 정확히 같아야 합니다.** |
| `exercises[].sets` | 기본 세트 수(1~20) |
| `exercises[].rest` (선택) | 휴식(5~600초). 없으면 종목의 기본 휴식, 그것도 없으면 앱 설정의 휴식을 따릅니다. 가능하면 모든 종목에 적어 두세요. |
| `exercises[].superset` (선택) | 같은 글자끼리 슈퍼셋으로 묶입니다. 묶음 종목은 **이어서** 적고, 두 종목 이상이어야 합니다. 묶음은 마지막 종목을 마친 뒤에만 쉽니다. |

앱의 루틴에는 세트 수만 들어갑니다. 횟수 목표 같은 안내는 `description` 에 적습니다.

---

## 틀리면

형식이 틀린 파일은 **앱이 시작할 때 오류**가 되고, 단위 테스트에서 먼저 걸립니다.
오류 메시지에 파일 · 템플릿 id · 날 이름 · 종목 이름이 들어 있어 어디가 틀렸는지 바로 알 수 있습니다.

* 모르는 키(오타 포함)와 잘못된 값(없는 `category` 등)은 조용히 넘기지 않고 거절합니다.
* 템플릿의 종목 이름이 종목 파일에 없으면 테스트가 실패합니다.

파일을 고친 뒤에는 반드시 단위 테스트를 돌리세요.

```
./gradlew :app:testDebugUnitTest
```
