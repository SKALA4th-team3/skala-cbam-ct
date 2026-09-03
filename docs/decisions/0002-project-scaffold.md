# ADR-0002: 초기 구조 — backend/frontend 분리 · Gradle · JavaScript

- 상태: 채택
- 날짜: 2026-09-03
- 관련 기능: 전체

## 배경

한 저장소에 BE·FE가 같이 들어간다. **5명이 3일 동안 병렬로 붙는다.** 디렉터리·빌드 도구·언어를 먼저 정하지 않으면 각자 다른 자리에 만들고, 첫 merge에서 하루를 쓴다.

세 가지를 한 번에 정한다. 셋 다 **되돌리려면 전원이 다시 clone 해야 하는** 선택이다.

## 검토한 선택지

### 1. 디렉터리 — `backend/` + `frontend/` (고른 것)

- 장점 — Spring Boot·Vite 문서와 튜토리얼이 쓰는 이름이다. 처음 보는 사람이 안 헷갈린다
- 장점 — 루트에는 문서와 설정만 남는다
- **`be/` + `fe/` 를 고르지 않은 이유** — 브랜치 도메인과 글자까지 같아지는 건 좋지만, **`be` 는 영어 단어라** `grep -r be/` 와 에디터 자동완성에서 잡음이 심하다. 브랜치명은 짧아야 하는 자리고 디렉터리는 읽히는 자리다 — 제약이 다르다. 대신 [`GIT_CONVENTION.md`](../GIT_CONVENTION.md) 에 `be ↔ backend`, `fe ↔ frontend` 매핑을 박아 뒀다
- **루트를 Spring Boot 로 두지 않은 이유** — 루트가 `build.gradle`·`src/`·문서로 지저분해지고, FE가 곁방 취급이 된다

### 2. 빌드 도구 — Gradle (고른 것)

- 장점 — `start.spring.io` 기본값. 설정 파일이 짧아 의존성 추가가 한 줄이다
- **Maven 을 고르지 않은 이유** — XML 이라 같은 내용이 길어진다. 3일 동안 5명이 의존성을 자주 건드릴 텐데 줄이 길수록 충돌 면적이 넓다

### 3. 프론트 언어 — JavaScript (고른 것)

- 장점 — 3일이다. 타입 오류 잡는 데 시간을 안 쓴다
- 장점 — 명세의 상태값(`협력유지중`·`검토 대기`·`미등록 부품`)은 **어차피 문자열**이라 TS 이득이 작다
- **TypeScript 를 고르지 않은 이유** — BE 응답 타입을 명시하면 API 계약 오해가 준다는 이득은 있지만, **API 명세가 아직 없다.** 타입을 먼저 쓰면 그게 곧 명세가 되는데, 그건 팀이 정할 일이지 FE 혼자 정할 일이 아니다

## 결정

- `backend/` — Spring Boot **4.1.1** · Java **21** (설치된 JDK 와 일치, LTS) · Gradle
  의존성: `web` `data-jpa` `validation` `lombok` `mail` `h2`
- `frontend/` — Vue **3** · Vite · JavaScript · Vue Router · Pinia · ESLint · Prettier
- 비밀값은 `.env`, `application.properties` 가 `${VAR:기본값}` 으로 참조한다

## 결과

- 쉬워진다 — `./gradlew bootRun` 과 `npm run dev` 로 각자 바로 뜬다
- **감수한다 — H2 는 부팅용이다.** `spring.datasource.url` 기본값이 H2 인메모리라 **껐다 켜면 데이터가 날아간다.** 실 DB 는 아직 안 정했다. 정할 때 ADR 을 따로 쓴다
- **감수한다 — `ddl-auto` 를 안 넣었다.** 스키마 모양은 별도 결정이다 ([`README`](README.md) 의 「언제 쓰나」 참고)
- create-vue 가 만든 `oxlint@~1.74.0` 과 `eslint-plugin-oxlint@1.73.0` 이 peer 충돌을 일으켜 **둘 다 `~1.81.0` 으로 올렸다.** `--legacy-peer-deps` 로 덮지 않았다
- **감수한다 — `.env` 의 키를 주석 처리해 두었다.** `${VAR:기본값}` 은 변수가 **빈 문자열로 존재하면 기본값을 쓰지 않는다.** `.env.example` 을 그대로 복사해 `source` 하면 `DB_URL=""` 이 되어 부팅이 깨진다. 그래서 쓰는 것만 주석을 벗기게 했다

## 다시 볼 조건

- 실 DB 를 정할 때 — H2 기본값과 `ddl-auto` 를 그때 함께 정한다
- BE·FE 를 따로 배포해야 할 때 — 지금 구조는 한 저장소 두 폴더다
