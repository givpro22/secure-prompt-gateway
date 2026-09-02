# 사내 생성형 AI 게이트웨이

사내 구성원이 생성형 AI에 보내는 프롬프트를 **전송 전에** 검사해 개인정보·자격증명·기밀 정보 유출을 막는 게이트웨이입니다.

핵심 설계는 책임 경계입니다. **규칙 엔진은 결정하고(허용/마스킹/차단), AI는 제안만 합니다.** AI가 제시한 후보는 보안 담당자가 감사 콘솔에서 확정하기 전까지 어떤 판정도 되지 않습니다. 이 경계는 화면에서도, 스키마에서도 강제됩니다 — `aiAssessment`에는 `decision`·`block`·`allow`·`confidence` 필드가 아예 없습니다.

## 구성

모노레포 1개입니다.

| 디렉터리 | 내용 |
|---|---|
| `backend/` | Spring Boot 3.5.3 · Java 21 · PostgreSQL · Flyway |
| `frontend/` | Vue 3 · Vite · Pinia · vue-router · axios |
| `docs/` | API 명세, Postman 컬렉션, ERD, 화면 명세, 데모 스크립트 |
| `_workspace/` | 팀 산출물 (계약 확정본, 스키마 노트, UI 노트) |

화면은 2개입니다.

- **SCR-01 직원 AI 챗** `/chat` — 프롬프트 제출과 판정 결과 (허용 / 마스킹 / 차단 / 검토 대기)
- **SCR-02 관리자 감사 콘솔** `/admin/audit` — 판정 이력 조회와 AI 후보 확정

## 요구 환경

| 항목 | 버전 |
|---|---|
| Java | 21 (Temurin OpenJDK 21.0.11 검증) |
| Node | 26.5.1 / npm 12.0.2 |
| PostgreSQL | 16 (Docker) |
| Docker | 29.6.2 |

Gradle은 설치할 필요가 없습니다. `backend/gradlew` 래퍼를 씁니다.

## 실행

### 1. 데이터베이스

```bash
docker run -d --name gateway-pg \
  -e POSTGRES_DB=gateway -e POSTGRES_USER=gateway -e POSTGRES_PASSWORD=gateway \
  -p 55432:5432 postgres:16-alpine
```

스키마와 시드는 Flyway가 애플리케이션 기동 시 적용합니다 (`backend/src/main/resources/db/migration/`).

### 2. 백엔드

```bash
cd backend
./gradlew bootRun
```

`http://localhost:8080`에서 기동합니다. API base path는 `/api/v1`이고, Swagger UI는 `http://localhost:8080/swagger-ui/index.html`입니다. AI 검사는 기본값 `mock` 프로파일에서 `MockAiInspector`가 담당하며, 실제 LLM 호출로 바꾸려면 `SPRING_PROFILES_ACTIVE=llm`으로 전환합니다 — 코드 변경은 없습니다.

첫 `./gradlew` 실행은 의존성 내려받기로 수 분이 걸릴 수 있습니다.

### 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

`npm run build`로 프로덕션 번들을, `npm run preview`로 그 번들을 확인합니다.

**백엔드 없이 화면만 보려면:**

```bash
cd frontend
npm run dev:fixtures
```

Vite dev 서버가 `/api/v1`을 직접 응답하는 개발 전용 픽스처 서버(`frontend/dev/fixture-server.js`)를 켭니다. 판정 규칙 8종·중첩 억제·2.5초 AI 지연까지 실제 계약대로 흉내 내므로 아래 데모 케이스 4종이 그대로 동작합니다. 이 서버는 프로덕션 번들에 포함되지 않습니다.

## 환경변수

코드에는 키·엔드포인트·모델명·임계값이 없습니다. 정책과 규칙은 DB에, 나머지는 환경변수에 있습니다.

### 백엔드

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:55432/gateway` | JDBC 접속 문자열 |
| `DB_USER` | `gateway` | DB 사용자 |
| `DB_PASSWORD` | `gateway` | DB 비밀번호 |
| `SPRING_PROFILES_ACTIVE` | `mock` | AI 구현체 선택 (`mock` / `llm`) |
| `SERVER_PORT` | `8080` | 백엔드 포트. 바꾸면 `VITE_API_BASE`와 `CORS_ALLOWED_ORIGINS`도 함께 조정 |
| `AI_PROVIDER` | `mock` | 문서상 스위치. 실제 빈 선택은 프로파일이 한다 |
| `AI_ENDPOINT` | (빈 값) | `llm` 프로파일 전용 |
| `AI_API_KEY` | (빈 값) | `llm` 프로파일 전용 |
| `AI_MODEL` | (빈 값) | `llm` 프로파일 전용 |
| `AI_TEMPERATURE` | `0` | 판정 재현성을 위해 0 고정 |
| `AI_MAX_TOKENS` | `800` | |
| `AI_TIMEOUT_MS` | `10000` | |
| `AI_MAX_INPUT_CHARS` | `4000` | 초과분은 절단하고 `missingContext`에 기록 |
| `AI_MOCK_DELAY_MS` | `2500` | Mock 응답 지연. **줄이지 마세요** — 202 비동기 설계가 화면에 드러나는 자리입니다 |
| `AI_MOCK_FAIL_KEYWORD` | `__FAIL__` | 실패 경로 검증용 |
| `GATEWAY_POLL_INTERVAL_MS` | `2000` | 202 응답의 `pollAfterMs`로 나갑니다 |
| `GATEWAY_POLL_MAX_ATTEMPTS` | `30` | 프론트엔드 폴링 상한 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:4173` | Vite dev / preview |

클라우드 DB로 옮길 때 바꾸는 것은 `DB_URL`·`DB_USER`·`DB_PASSWORD` 셋뿐이고 코드 변경은 없습니다.

### 프론트엔드

Vite 규칙상 `VITE_` 접두사가 붙은 값만 클라이언트에 노출됩니다.

| 환경변수 | 파일 | 값 | 용도 |
|---|---|---|---|
| `VITE_API_BASE` | `.env.development` | `http://localhost:8080/api/v1` | `npm run dev`가 쓰는 값. 코드에 URL을 하드코딩하지 않으므로 이 한 줄로 Postman Mock ↔ 로컬 BE ↔ 배포 환경을 전환합니다 |
| `VITE_API_BASE` | `.env.production` | `http://localhost:8080/api/v1` | `npm run build` / `npm run preview`가 쓰는 값. 실제 배포 시에는 빌드 시점에 주입해 덮어씁니다 |
| `VITE_API_BASE` | `.env.fixtures` | `/api/v1` | 픽스처 모드 (`npm run dev:fixtures`) |
| `VITE_FIXTURES` | `.env.fixtures` | `1` | 픽스처 서버 활성화. 이 값이 `1`일 때만 플러그인이 등록됩니다 |

## 데모 케이스

계정 전환은 화면 우측 상단 드롭다운에서 합니다. **계정에 따라 결과가 갈리는 것이 부서별 정책 적용의 증명입니다.**

| 케이스 | 계정 | 입력 문자열 | 기대 결과 |
|---|---|---|---|
| **A** | 이OO · 개발팀 | `이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나` | **차단** (403) · 규칙 2건 (`SEC-DBURL-02`, `PII-RRN-01`) · 입력창에 원문 복원 |
| **B** | 김OO · 영업팀 | `A사 차세대 프로젝트 오픈 일정이 언제였지?` | **검토 대기** (202) → 2.5초 스피너 → AI 후보 1건 → 감사 콘솔에서 ACCEPT → **차단** |
| **C** | 이OO · 개발팀 | `A사 차세대 프로젝트 오픈 일정이 언제였지?` | **허용** (200) · 규칙 0건 |
| **D** | 정OO · 인사팀 | `지원자 연락처 010-1234-5678 로 면접 안내 문자 초안 써줘` | **마스킹** (200) · `[전화번호]`로 치환 후 전송 |

**B와 C는 완전히 같은 문장입니다.** 영업팀에는 고객사 정책(`P-CONF`)이 매핑돼 있고 개발팀에는 없어서 결과가 갈립니다.

Case A에서 정규식은 4건 매칭되지만 화면에 표시되는 규칙은 2건입니다. 사설 IP(`SEC-PRIVIP-03`)와 이메일(`PII-EMAIL-04`)이 DB 접속 문자열 구간에 완전히 포함돼 중첩 억제되기 때문입니다. 같은 문자열을 두 규칙이 이중으로 세면 감사 화면의 위험 건수가 부풀려집니다.

Case B의 전체 흐름:

1. 김OO 계정으로 Case B 문자열 전송 → 보라 스피너 "보안 검토 중"
2. 2.5초 후 AI 후보 `CONF-CLIENT-PROJECT`가 **읽기 전용**으로 표시 (직원 화면에는 ACCEPT/REJECT가 없습니다)
3. 감사 콘솔로 이동 → 최상단 행 클릭 → AI 제안 섹션에서 **ACCEPT**
4. 챗 화면으로 돌아와 **결과 새로고침** → "최종 판정 차단 · 확정 주체 담당자"

AI 검사 실패 경로를 보려면 입력에 `__FAIL__`을 함께 넣습니다 (예: `A사 차세대 프로젝트 일정 __FAIL__`). 검토 대기 상태는 유지되고 "자동 검토 실패 — 담당자 확인 중"이 표시됩니다.

## 문서

| 문서 | 내용 |
|---|---|
| `사내_AI_게이트웨이_기획서_v1.md` | 원본 기획서. 0.5절이 결정 사항 색인 |
| `_workspace/01_api-ai-architect_contract-freeze.md` | **API 계약 확정본.** 코드가 이 문서와 어긋나면 코드가 틀린 것 |
| `docs/api-spec.md` · `docs/ai-gateway-v1.postman_collection.json` | API 명세와 실행 가능한 컬렉션 |
| `docs/screen-spec.md` | 화면 명세 (프레임 8개, 컴포넌트별 데이터 경로) |
| `docs/erd.dbml` | 8개 테이블 ERD |
| `docs/demo-script.md` | 발표 진행 대본 |
| `_workspace/02_frontend-dev_ui-notes.md` | 화면 구현 결정과 API 응답 매핑표 |

## 브랜치 운영

| 항목 | 규칙 |
|---|---|
| `main` | 항상 데모 가능한 상태를 유지합니다 |
| `feat/*` | 기능별 브랜치. PR 1인 리뷰 후 머지 |
| Feature Freeze | 2일차 17:00 이후 `main` 머지 금지 (버그 픽스 제외) |
