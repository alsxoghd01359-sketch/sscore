# sscore

라이브스코어 앱: `android/`(Kotlin 앱), `backend/`(Spring Boot + Kotlin), `db/`(SQL).

## Agent skills

### Issue tracker

이슈는 로컬 마크다운으로 관리하며 `.scratch/<기능>/` 아래 파일에 저장한다. See `docs/agents/issue-tracker.md`.

### Triage labels

기본 5개 라벨(`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`)을 사용한다. See `docs/agents/triage-labels.md`.

### Domain docs

single-context (루트 `GLOSSARY.md` + `docs/adr/`). See `docs/agents/domain.md`.

## Workflow

- 작업을 완료하고 검증한 뒤에는 바로 커밋하고 `origin`(GitHub)으로 푸시한다.
- 비밀값(API 키, DB 비밀번호 등)은 커밋하지 않는다.
- 코드는 기능별 패키지/폴더로 묶는다(인증, 회원, 팔로우, 알림, 경기, 리그, 팀, 선수, 검색, 관리자, 공통). 티켓에서 건드리는 기능의 코드는 그 작업 안에서 해당 기능 폴더로 옮겨 정리하고, 이동만 하는 큰 일괄 작업은 만들지 않는다.
