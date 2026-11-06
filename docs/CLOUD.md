# 클라우드 백업 (구글 로그인 + Firestore)

기록을 내 구글 계정에 백업하고, 폰을 바꾸거나 앱을 지운 뒤 로그인해서 되돌립니다.
**실시간 동기화가 아닌 백업 · 복원**입니다. 복원은 이 기기의 기록을 **전부 대체**하며, 복원 직전에 현재 기록을 먼저 클라우드에 백업합니다.

`app/google-services.json` 이 없으면 이 기능만 꺼진 채(설정 화면에 안내 한 줄) 앱이 정상 빌드 · 동작합니다.

## 한 번만 하는 설정

1. [Firebase 콘솔](https://console.firebase.google.com) → 프로젝트 만들기(애널리틱스는 꺼도 됩니다).
2. 프로젝트에 **Android 앱 추가**
   * 패키지 이름: `com.windowhyun.health`
   * **SHA-1 인증서 지문**: 릴리스 키 서명의 SHA-1 (아래 "SHA-1 구하기"). 디버그 빌드도 쓰면 디버그 키 SHA-1 도 추가.
3. **Authentication → 로그인 방법 → Google** 사용 설정(지원 이메일 선택).
4. **Firestore Database → 데이터베이스 만들기**(프로덕션 모드, 위치는 가까운 곳).
   그다음 "규칙" 탭에 `firebase/firestore.rules` 내용을 붙여넣고 게시합니다.
5. 프로젝트 설정에서 **`google-services.json` 내려받기**(3번 로그인 설정을 켠 *뒤에* 받아야 웹 클라이언트 ID 가 들어 있습니다).
6. 그 파일을 `app/google-services.json` 으로 둡니다. 방법 둘 중 하나:
   * 저장소에 그대로 커밋(클라이언트 설정이라 비밀이 아닙니다), 또는
   * GitHub 저장소 Settings → Secrets 에 `GOOGLE_SERVICES_JSON_BASE64` 로 `base64 -w0 google-services.json` 결과를 넣기. CI 가 빌드 때 파일을 만듭니다.

## SHA-1 구하기

릴리스 APK 로 확인합니다(키스토어 비밀번호가 필요 없습니다).

```bash
apksigner verify --print-certs health-0.14.0.apk | grep SHA-1
```

SHA-1 이 안 맞으면 로그인 시 "계정을 가져오지 못했어요" 류의 오류가 납니다.

## 데이터 구조

```
users/{uid}/backups/{백업ID}            정보(시각 · 개수 · 크기 · 조각 수 · SHA-256)
users/{uid}/backups/{백업ID}/chunks/{n} 압축한 백업 파일의 조각(600KB 이하 Blob)
```

* 백업 파일은 기존 "백업 파일 만들기"의 JSON 을 gzip 으로 압축한 것입니다. 문서 하나가 1MiB 를 넘지 못해 조각으로 나눕니다.
* Cloud Storage 는 쓰지 않습니다(유료 Blaze 요금제가 필요해서). 무료 Firestore 한도(1GiB 저장, 일 20,000 쓰기)에 한참 못 미칩니다.
* 백업은 최근 5개만 남깁니다.

## 지우기

설정 → 클라우드 백업 → "클라우드 백업 모두 지우기". 계정 자체의 삭제는 Firebase 콘솔 → Authentication 에서 합니다.
