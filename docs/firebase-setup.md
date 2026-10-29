# Firebase(푸시 알림) 설정

푸시 알림은 Firebase Cloud Messaging(FCM, 무료)으로 보낸다. 필요한 파일은 두 개다.

| 파일 | 위치 | 용도 | git |
| --- | --- | --- | --- |
| `google-services.json` | `android/app/` | 앱이 Firebase 프로젝트에 연결되는 설정 (API 키 포함) | **커밋하지 않는다** (`.gitignore`에 등록됨). 새 PC에서는 Firebase 콘솔에서 다시 내려받는다 |
| 서비스 계정 키 | `backend/firebase-service-account.json` | 서버가 FCM으로 푸시를 보낼 때 쓰는 **비밀 키** | **커밋 금지** (`.gitignore`에 등록됨) |

## 1. 프로젝트 만들기
1. <https://console.firebase.google.com> 에서 프로젝트를 만든다. (Google Analytics는 꺼도 된다.)

## 2. 앱 등록 → `google-services.json`
1. 프로젝트 개요에서 안드로이드 아이콘(앱 추가)을 누른다.
2. Android 패키지 이름에 `com.example.livescore` 를 입력하고 앱을 등록한다. (SHA-1은 비워도 된다.)
3. `google-services.json` 을 내려받아 `android/app/` 에 넣는다.

## 3. 서버 키
1. 프로젝트 설정(톱니바퀴) → 서비스 계정 → **새 비공개 키 생성**.
2. 내려받은 JSON을 `backend/firebase-service-account.json` 으로 이름을 바꿔 넣는다.
   - 다른 위치/이름을 쓰려면 환경변수 `FIREBASE_CREDENTIALS_FILE` 에 경로를 적는다 (`backend/.env`).
3. 이 파일의 내용은 채팅·이슈·커밋 어디에도 붙여 넣지 않는다.

키 파일이 없으면 서버는 푸시를 보내지 않고 로그만 남긴다(개발용). 키가 있으면 시작할 때 `FCM으로 푸시를 보냅니다.` 로그가 나온다.

## 4. 동작 확인
1. 서버를 켜고 앱을 실행해 로그인한다. 알림 권한을 허용한다 (Android 13 이상에서 묻는다).
2. 앱이 기기 토큰을 서버에 등록한다 (`PUT /api/members/me/push-token`).
3. ADMIN 계정 토큰으로 테스트 푸시를 보낸다.

```
POST /api/admin/push/test?memberId=<회원 번호>
Authorization: Bearer <ADMIN 액세스 토큰>
```

응답의 `sentDevices` 가 푸시를 보낸 기기 수다. 폰에 "SScore 테스트 알림"이 뜨면 설정 완료다.

## 참고
- 로그아웃하면 그 기기의 로그인 세션이 지워지면서 토큰도 함께 지워진다. 탈퇴하거나 비밀번호를 바꿔 다른 기기가 로그아웃되어도 마찬가지다.
- 같은 폰에서 계정을 바꿔 로그인하면 토큰이 새 계정으로 옮겨진다.
- 앱을 지우는 등으로 FCM이 거절한 토큰은 서버가 자동으로 정리한다.
- 서버가 실제 폰에 닿으려면 폰이 PC 서버에 접속할 수 있어야 한다 (`android/server.properties` 의 `server.url`).
