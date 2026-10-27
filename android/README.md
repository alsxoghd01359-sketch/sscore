# LiveScore (SScore 안드로이드 앱)

Android Studio에서 이 폴더(`android/`)를 열어 실행한다. (저장소 루트가 아니다)

## 서버 주소

앱이 붙는 서버 주소는 빌드 종류별로 `BuildConfig.BASE_URL`에서 온다.

| 빌드 | 기본값 | 바꾸는 방법 |
| --- | --- | --- |
| debug | `http://10.0.2.2:8080/` (에뮬레이터에서 PC로 가는 주소) | `local.properties`에 `server.url=http://<PC의 IP>:8080` |
| release | `https://api.sscore.example/` (자리표시자) | `local.properties`에 `server.url.release=https://...` |

- 실기기로 테스트할 때는 `local.properties`에 `server.url=http://192.168.0.2:8080`처럼 PC의 IP를 적는다. 기기와 PC가 같은 Wi-Fi여야 하고, Windows 방화벽이 8080 포트를 막지 않아야 한다.
- `local.properties`는 git에 올라가지 않는다.
- 평문(HTTP) 통신은 debug 빌드에서만 허용된다. release는 HTTPS가 필요하다.
