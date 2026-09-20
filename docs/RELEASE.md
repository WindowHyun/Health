# 릴리즈 빌드 가이드

개인용 앱이라 Play 스토어에 올리지 않고, **서명된 APK 를 직접 기기에 설치**해서 씁니다.
이 문서는 릴리즈 APK 를 만드는 전체 과정과, 실제로 만든 빌드의 기록을 담고 있습니다.

---

## 1. 키스토어 준비 (최초 1회)

릴리즈 APK 는 반드시 서명되어야 기기에 설치됩니다.
키스토어는 한 번 만들어 두고 **계속 같은 것을 써야** 합니다.
키가 바뀌면 기존에 설치된 앱 위에 업데이트할 수 없고, 지우고 새로 깔아야 합니다.

```bash
keytool -genkeypair -v \
  -keystore health-release.jks \
  -alias health \
  -keyalg RSA -keysize 4096 \
  -validity 10950 \
  -dname "CN=<이름>, OU=Personal, O=Health, L=Seoul, C=KR"
```

* `-validity 10950` 은 30년입니다. 개인 앱이므로 넉넉하게 잡습니다.
* 비밀번호를 물어보면 키스토어 비밀번호와 키 비밀번호를 정합니다(같게 두어도 됩니다).

만든 키스토어 정보를 프로젝트 루트의 `keystore.properties` 에 적습니다.

```properties
storeFile=health-release.jks
storePassword=<키스토어 비밀번호>
keyAlias=health
keyPassword=<키 비밀번호>
```

> ⚠️ `health-release.jks` 와 `keystore.properties` 는 `.gitignore` 에 있습니다.
> **절대 저장소에 올리지 마세요.** 이 두 개를 잃어버리면 같은 앱으로 업데이트할 수 없습니다.
> 클라우드 드라이브나 비밀번호 관리자에 따로 백업해 두세요.

### CI 처럼 파일을 둘 수 없는 환경

`keystore.properties` 대신 환경 변수로도 넘길 수 있습니다.

| 환경 변수 | 대응하는 항목 |
| --- | --- |
| `HEALTH_STORE_FILE` | 키스토어 파일 경로(프로젝트 루트 기준) |
| `HEALTH_STORE_PASSWORD` | 키스토어 비밀번호 |
| `HEALTH_KEY_ALIAS` | 키 별칭 |
| `HEALTH_KEY_PASSWORD` | 키 비밀번호 |

둘 다 없으면 **빌드는 성공하되 서명되지 않은 APK** 가 나옵니다.
저장소를 그냥 클론한 사람도 빌드는 할 수 있게 하기 위한 동작입니다.
서명되지 않은 APK 는 기기에 설치되지 않습니다.

---

## 2. 서명 설정이 동작하는 방식

`app/build.gradle.kts` 에서 다음 순서로 값을 찾습니다.

```
keystore.properties  →  환경 변수  →  (없으면) 서명하지 않음
```

서명 방식은 **APK Signature Scheme v2 / v3** 만 사용합니다.
v1(JAR 서명)은 Android 7.0 미만에서만 필요한데 이 앱의 minSdk 는 26(Android 8.0)이라 끄는 편이 빌드도 빠르고 안전합니다.

---

## 3. 빌드

```bash
# 전체 검증 후 릴리즈 APK 생성
./gradlew clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

결과물:

```
app/build/outputs/apk/release/app-release.apk
```

APK 만 빠르게 만들려면:

```bash
./gradlew :app:assembleRelease
```

---

## 4. 검증

빌드가 성공했다고 끝이 아닙니다. 설치 전에 세 가지를 확인합니다.

### 4-1. 서명 확인

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

`Verifies` 와 `v2/v3 scheme ... true` 가 나와야 합니다.

### 4-2. 패키지 정보 확인

```bash
$ANDROID_HOME/build-tools/35.0.0/aapt2 dump badging \
  app/build/outputs/apk/release/app-release.apk | head -5
```

`versionCode`, `versionName`, 권한 목록이 의도한 대로인지 봅니다.

### 4-3. R8 이 필요한 클래스를 지우지 않았는지 확인

릴리즈 빌드는 R8 로 코드를 줄이기 때문에, 리플렉션으로 찾는 클래스가 사라지면
**빌드는 성공해도 실행할 때 죽습니다.** 특히 Room 의 생성 클래스가 위험합니다.

```bash
unzip -p app/build/outputs/apk/release/app-release.apk classes.dex > /tmp/c.dex
$ANDROID_HOME/build-tools/35.0.0/dexdump /tmp/c.dex \
  | grep -oE "Lcom/windowhyun/health/[A-Za-z_/]+;" | sort -u
```

최소한 다음 네 개는 남아 있어야 합니다.

* `HealthApplication`
* `MainActivity`
* `RunTrackingService`
* `HealthDatabase_Impl` ← Room 이 만들어 주는 클래스

---

## 5. 설치

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

기기에 파일을 직접 옮겨 설치할 때는 "출처를 알 수 없는 앱 설치" 를 허용해야 합니다.

> 디버그 빌드는 `applicationId` 뒤에 `.debug` 가 붙으므로
> 디버그와 릴리즈를 한 기기에 같이 설치해 둘 수 있습니다.

---

## 6. 버전 올리기

`app/build.gradle.kts` 의 `defaultConfig` 에서 올립니다.

```kotlin
versionCode = 2       // 설치/업데이트 판단에 쓰는 정수. 반드시 증가.
versionName = "0.2.0" // 사람이 읽는 버전.
```

`versionCode` 가 이전보다 작거나 같으면 기기가 업데이트를 거부합니다.

---

## 7. 빌드 기록

### v0.2.0 (versionCode 2) — Phase 2 까지

| 항목 | 값 |
| --- | --- |
| 빌드 일시 | 2026-09-20 08:35 ~ 08:43 UTC |
| 커밋 | `4c2dbec` (Phase 2: GPS 러닝 기록 기능) |
| 브랜치 | `claude/android-workout-tracker-gc164p` |
| JDK | OpenJDK 21.0.10 |
| Gradle | 8.11.1 |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 (KSP 2.0.21-1.0.28) |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |

**실행한 명령**

```bash
./gradlew clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

**결과**

| 단계 | 결과 |
| --- | --- |
| `clean` | 성공 |
| `testDebugUnitTest` | **62개 전부 통과** (실패 0) |
| `lintRelease` | **오류 0** (경고 40건은 모두 "의존성 최신 버전 있음" 안내) |
| `minifyReleaseWithR8` | 성공 |
| `shrinkReleaseRes` | 성공 |
| `assembleRelease` | **BUILD SUCCESSFUL in 2m 48s** (96 tasks) |

**산출물**

| 항목 | 값 |
| --- | --- |
| 파일 | `app/build/outputs/apk/release/app-release.apk` |
| 크기 | 1,838,490 bytes (약 1.8 MB) |
| SHA-256 | `4d89b13054674626a09b83e8948734a256c2aef097a962980371d63086ef4a68` |

> 같은 소스로 다시 빌드해도 SHA-256 은 달라집니다.
> APK 는 ZIP 이고 엔트리마다 빌드 시각이 들어가며 서명 블록도 매번 새로 만들어지기 때문입니다.
> **크기가 같고 `apksigner verify` 가 통과하면 정상**입니다.
> 위 해시는 이번에 전달한 파일을 확인하는 용도입니다.

**서명 검증 (`apksigner verify`)**

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
Signer #1 certificate DN: CN=WindowHyun, OU=Personal, O=Health, L=Seoul, C=KR
Signer #1 certificate SHA-256 digest: 2d1048151df37b97dcf82cf6c24a584ee79db0a002794956eda2c1c6d78a67df
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 4096
```

**패키지 정보 (`aapt2 dump badging`)**

```
package: name='com.windowhyun.health' versionCode='2' versionName='0.2.0'
targetSdkVersion:'35'
uses-permission: android.permission.VIBRATE
uses-permission: android.permission.POST_NOTIFICATIONS
uses-permission: android.permission.ACCESS_FINE_LOCATION
uses-permission: android.permission.ACCESS_COARSE_LOCATION
uses-permission: android.permission.FOREGROUND_SERVICE
uses-permission: android.permission.FOREGROUND_SERVICE_LOCATION
uses-feature-not-required: android.hardware.location.gps
```

GPS 는 `required="false"` 라서 GPS 없는 기기에도 설치됩니다(헬스 기능만 사용).

**R8 생존 확인 (`dexdump`)**

```
Lcom/windowhyun/health/HealthApplication;
Lcom/windowhyun/health/MainActivity;
Lcom/windowhyun/health/data/local/HealthDatabase;
Lcom/windowhyun/health/data/local/HealthDatabase_Impl;
Lcom/windowhyun/health/service/RunTrackingService;
```

Room 의 생성 클래스까지 모두 남아 있는 것을 확인했습니다.

**키스토어 없이 빌드되는지 확인**

`keystore.properties` 와 `.jks` 를 잠시 치우고 다시 빌드해서, 저장소를 그냥 클론한
사람도 빌드할 수 있는지 확인했습니다.

| 조건 | 결과물 | 설치 가능 |
| --- | --- | --- |
| 키스토어 있음 | `app-release.apk` | O |
| 키스토어 없음 | `app-release-unsigned.apk` (빌드는 성공) | X |

**확인하지 못한 것**

빌드 환경에 실제 기기도 에뮬레이터도 없어서 **APK 를 설치해 실행해 보지는 못했습니다.**
정적으로 확인 가능한 범위(서명 · 패키지 정보 · R8 생존 · 단위 테스트)까지만 검증했습니다.
기기에 설치한 뒤에는 README 의 "손으로 확인해 볼 시나리오" 를 한 번 훑어 주세요.
특히 **릴리즈 빌드에서 처음 앱을 켰을 때 기본 운동 종목이 보이는지**(Room + R8 조합)를
가장 먼저 확인하면 좋습니다.
