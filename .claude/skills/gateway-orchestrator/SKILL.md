---
name: gateway-orchestrator
description: "사내 AI 게이트웨이 프로젝트의 에이전트 팀을 조율하는 오케스트레이터. 기획서를 실제 산출물(DDL·엔티티·규칙 엔진·API·Mock AI·Vue 화면·문서)로 구현할 때 사용. 트리거 — '게이트웨이 구현해줘', '기획서대로 만들어줘', 'ERD/API/화면 만들어', '2일차 작업 시작', '데모 준비', 'E2E 검증'. 후속 작업 — '다시 실행', '재실행', '업데이트', '수정', '보완', '규칙 엔진만 다시', '화면만 다시', 'API만 다시', '이전 결과 기반으로', '결과 개선', '검증 다시', 'QA 다시 돌려' 요청에도 반드시 이 스킬을 사용. 기획서 자체에 대한 단순 질문은 직접 응답 가능."
---

# Gateway Orchestrator — 사내 AI 게이트웨이 구현 팀 조율

`사내_AI_게이트웨이_기획서_v1.md`를 입력으로 받아, 6인 에이전트 팀이 기획서 부록 C의 제출물을 생산하도록 조율한다.

## 실행 모드: 에이전트 팀

팀 모드를 쓰는 이유는 이 프로젝트의 실패 방식이 **경계면 불일치**이기 때문이다. 서브 에이전트로 각자 만들어 나중에 합치면, 각각 정상인데 연결에서 어긋난 것을 늦게 발견한다. 팀원이 SendMessage로 "이 필드가 실제로 안 온다"를 즉시 보내야 그 자리에서 고쳐진다.

## 에이전트 구성

| 팀원 | 기획서 R&R | 역할 | 스킬 | 주요 출력 |
|---|---|---|---|---|
| `spec-steward` | A | 기획서 SSOT 관리, Use-Case·화면 명세·데모 스크립트·발표 | `spec-contract` | `docs/use-cases.md`, `docs/screen-spec.md`, `docs/demo-script.md` |
| `data-architect` | B (Data) | DDL·Flyway·JPA 엔티티 8종·시드 | `db-schema-seed`, `spec-contract` | `V1__schema.sql`, `V2__seed.sql`, `domain/**`, `docs/erd.dbml` |
| `rule-engine-dev` | B (Backend) | 규칙 엔진·POST /messages·정책/감사 조회 | `rule-engine-impl`, `spec-contract` | `engine/**`, `service/**`, `api/{Message,Policy,Department}Controller` |
| `api-ai-architect` | C | 계약 확정·Postman·AiInspector·@Async 202·PATCH 확정 | `ai-mock-contract`, `spec-contract` | `docs/api-spec.md`, `ai/**`, `InspectionController`, 계약서 |
| `frontend-dev` | D | Vue 스캐폴딩·SCR-01 5상태·SCR-02·폴링·저장소 | `vue-screens`, `spec-contract` | `frontend/src/**` |
| `integration-qa` | — | 경계면 교차 검증·데모 E2E·제출물 확인 | `demo-verification`, `spec-contract` | `_workspace/03_integration-qa_report.md`, `docs/e2e-result.md` |

`integration-qa`는 기획서 R&R에 없는 추가 인원이다. D가 E2E를 겸하도록 되어 있으나, 만든 사람이 자기 산출물을 검증하면 경계면 버그를 놓친다. 팀 내부에 두어 각 모듈 완성 직후 즉시 검증하게 한다.

모든 팀원은 `model: "opus"`로 생성한다.

## 워크플로우

### Phase 0: 컨텍스트 확인

1. `_workspace/` 존재 여부를 확인한다
2. 실행 모드를 결정한다:
   - **미존재** → 초기 실행. Phase 1로
   - **존재 + 부분 수정 요청** ("규칙 엔진만 다시", "화면 수정") → **부분 재실행**. 해당 팀원만 팀에 넣고, 이전 산출물 경로를 프롬프트에 포함해 읽고 개선하게 한다
   - **존재 + 새 입력/전면 재실행** → 기존 `_workspace/`를 `_workspace_{YYYYMMDD_HHMMSS}/`로 이동한 뒤 Phase 1
3. 부분 재실행이면 `_workspace/01_api-ai-architect_contract-freeze.md`를 반드시 읽어 팀원 프롬프트에 계약을 싣는다. 계약을 모르는 팀원이 필드명을 새로 지으면 이미 만든 화면이 깨진다

### Phase 1: 준비

1. `사내_AI_게이트웨이_기획서_v1.md`를 읽고 요청 범위를 판정한다 — 전체 구현인가, 특정 계층인가
2. `_workspace/` 생성
3. **미결 항목을 먼저 결정한다.** `spec-contract` 스킬의 `references/open-questions.md` Q1~Q6를 읽고, 권고안대로 진행할지 사용자에게 확인한다. 이 결정이 시드·엔진·화면에 동시에 영향을 주므로 나중에 바꾸면 세 곳을 고쳐야 한다
4. 결정 결과를 `_workspace/00_input/decisions.md`에 기록한다

### Phase 2: 팀 구성

```
TeamCreate(
  team_name: "gateway-team",
  members: [
    { name: "spec-steward",     agent_type: "spec-steward",     model: "opus", prompt: "..." },
    { name: "data-architect",   agent_type: "data-architect",   model: "opus", prompt: "..." },
    { name: "rule-engine-dev",  agent_type: "rule-engine-dev",  model: "opus", prompt: "..." },
    { name: "api-ai-architect", agent_type: "api-ai-architect", model: "opus", prompt: "..." },
    { name: "frontend-dev",     agent_type: "frontend-dev",     model: "opus", prompt: "..." },
    { name: "integration-qa",   agent_type: "integration-qa",   model: "opus", prompt: "..." }
  ]
)
```

각 팀원 프롬프트에 반드시 포함할 것: 기획서 절대 경로, 담당 스킬 이름, `_workspace/00_input/decisions.md` 경로, 출력 경로, "계약 확정 전에는 필드명을 확정하지 말 것".

작업 등록 — 팀원당 4~6개:

```
TaskCreate(tasks: [
  // 계약 (선행)
  { title: "미결 항목 결정 반영",        assignee: "spec-steward" },
  { title: "컬럼명·enum 목록 산출",       assignee: "data-architect" },
  { title: "ruleResult shape 초안",       assignee: "rule-engine-dev" },
  { title: "Interface Freeze 계약서",     assignee: "api-ai-architect",
    depends_on: ["컬럼명·enum 목록 산출", "ruleResult shape 초안", "미결 항목 결정 반영"] },
  { title: "Postman 컬렉션·Mock Server",  assignee: "api-ai-architect", depends_on: ["Interface Freeze 계약서"] },

  // 구현 (병렬)
  { title: "V1 DDL + V2 시드",            assignee: "data-architect",   depends_on: ["Interface Freeze 계약서"] },
  { title: "JPA 엔티티 8종",              assignee: "data-architect",   depends_on: ["V1 DDL + V2 시드"] },
  { title: "규칙 엔진 5종 구현",           assignee: "rule-engine-dev",  depends_on: ["Interface Freeze 계약서"] },
  { title: "POST /messages + 조회 API",   assignee: "rule-engine-dev",  depends_on: ["JPA 엔티티 8종", "규칙 엔진 5종 구현"] },
  { title: "AiInspector + 픽스처 3종",     assignee: "api-ai-architect", depends_on: ["Interface Freeze 계약서"] },
  { title: "@Async 202 + PATCH 확정",     assignee: "api-ai-architect", depends_on: ["POST /messages + 조회 API"] },
  { title: "Vue 스캐폴딩 + SCR-01 5상태", assignee: "frontend-dev",     depends_on: ["Postman 컬렉션·Mock Server"] },
  { title: "SCR-02 감사 콘솔",            assignee: "frontend-dev",     depends_on: ["Postman 컬렉션·Mock Server"] },
  { title: "Use-Case·화면 명세 문서",      assignee: "spec-steward" },
  { title: "데모 스크립트·제출물 추적",     assignee: "spec-steward" },

  // 검증 (점진)
  { title: "시드·정규식 왕복 검증",        assignee: "integration-qa", depends_on: ["V1 DDL + V2 시드"] },
  { title: "데모 케이스 백엔드 판정 검증",  assignee: "integration-qa", depends_on: ["POST /messages + 조회 API"] },
  { title: "경계면 3단 대조",             assignee: "integration-qa", depends_on: ["@Async 202 + PATCH 확정"] },
  { title: "데모 E2E A·B·C·D",           assignee: "integration-qa",
    depends_on: ["SCR-02 감사 콘솔", "@Async 202 + PATCH 확정"] }
])
```

### Phase 3: 계약 확정 (Interface Freeze)

**이 Phase가 끝나기 전에는 다른 구현을 시작하지 않는다.** 기획서 13장의 핵심 의존 관계이며, 계약이 늦으면 FE가 통째로 막히고 필드명이 나중에 바뀌면 이미 만든 것을 다시 만든다.

순서가 중요하다. `data-architect`(컬럼명)와 `rule-engine-dev`(판정 결과 shape)가 **먼저** 자기 이름을 정해 `api-ai-architect`에게 보낸다. 계약을 혼자 쓰면 DB와 어긋난다.

산출물: `_workspace/01_api-ai-architect_contract-freeze.md` — `spec-contract` 스킬의 "계약서 필수 4표"를 채운다.

확정 즉시 `api-ai-architect`가 전원에게 브로드캐스트한다. Postman Mock Server URL도 함께 보낸다. 그 통보가 FE 개발의 출발 신호다.

### Phase 4: 병렬 구현 + 점진 검증

팀원들이 공유 작업 목록에서 작업을 요청해 독립적으로 수행한다.

**통신 규칙:**

| 발신 | 수신 | 내용 | 시점 |
|---|---|---|---|
| `data-architect` | `rule-engine-dev` | `PolicyRule` 엔티티 shape, 정책 로드 쿼리 시그니처 | 엔티티 완성 즉시 |
| `rule-engine-dev` | `api-ai-architect` | `AiInspectionRequest`의 `hits[]` 구조, 마스킹 적용본 | REVIEW 분기 구현 시 |
| `rule-engine-dev` | `frontend-dev` | 200/202/403 실제 응답 본문 예시 | 각 판정 경로 완성 즉시 |
| `api-ai-architect` | `frontend-dev` | Mock Server URL, 계약 개정 사항 | 즉시 |
| `frontend-dev` | `api-ai-architect` | "이 필드가 실제로 오지 않는다" | 발견 즉시 |
| 각 팀원 | `integration-qa` | 모듈 완성 통보 | 완성 즉시 |
| `integration-qa` | 경계면 **양쪽** | 불일치 지적 (파일:라인 + 수정 방법) | 발견 즉시 |
| 누구든 | `spec-steward` | 기획서 모호·모순 질의 | 발견 즉시 |

**`integration-qa`는 마지막에 한 번이 아니라 각 모듈 완성 직후 실행한다.** 몰아서 하면 버그가 누적되고 초기 불일치가 후속 모듈로 전파된다.

**산출물 경로:**

| 팀원 | 코드 | 노트 |
|---|---|---|
| `spec-steward` | `docs/{use-cases,screen-spec,demo-script,submission-checklist}.md` | `_workspace/01_spec-steward_open-questions.md` |
| `data-architect` | `backend/src/main/resources/db/migration/`, `backend/.../domain/`, `docs/erd.dbml` | `_workspace/02_data-architect_schema-notes.md` |
| `rule-engine-dev` | `backend/.../engine/`, `service/`, `api/` | `_workspace/02_rule-engine-dev_engine-notes.md` |
| `api-ai-architect` | `backend/.../ai/`, `config/`, `docs/api-spec.md`, `docs/ai-prompt.md` | `_workspace/01_api-ai-architect_contract-freeze.md` |
| `frontend-dev` | `frontend/src/` | `_workspace/02_frontend-dev_ui-notes.md` |
| `integration-qa` | `docs/e2e-result.md` | `_workspace/03_integration-qa_{report,boundary-matrix}.md` |

**리더 모니터링:** 팀원이 유휴가 되면 알림을 받는다. 막힌 팀원에게는 SendMessage로 지시하거나 작업을 재할당한다. 진행률은 TaskGet으로 확인한다.

리더가 특히 주시할 것 — 계약 확정 지연, `frontend-dev`가 BE를 기다리며 놀고 있는 상태(Mock으로 진행시킨다), `integration-qa`가 미검증만 쌓는 상태(검증 가능한 모듈이 나왔는지 확인한다).

### Phase 5: 통합 검증

1. 모든 구현 작업 완료를 TaskGet으로 확인한다
2. `integration-qa`에게 전체 검증을 지시한다 — 경계면 9종, 상태 전이 완전성, 데모 케이스 E2E
3. `_workspace/03_integration-qa_report.md`를 읽는다
4. **데모 차단 이슈**(케이스 A·B·C 실패)가 있으면 해당 담당자에게 재작업을 지시하고 재검증한다. 최대 2회 반복하고, 그래도 실패하면 미해결로 보고한다
5. `spec-steward`에게 제출물 체크리스트 최종 확인을 지시한다

### Phase 6: 정리

1. 팀원들에게 종료를 알린다
2. `TeamDelete`
3. `_workspace/`는 **보존한다**. 중간 산출물은 사후 검증·감사 추적용이며, 부분 재실행의 입력이 된다
4. 사용자에게 보고한다 — 생성된 산출물 목록, QA 통과/실패/미검증 건수, 데모 차단 이슈, 남은 작업

## 데이터 흐름

```
기획서 (SSOT)
   │
   ├─→ [spec-steward] ──── 미결 항목 결정 ──┐
   ├─→ [data-architect] ── 컬럼명·enum ────┤
   └─→ [rule-engine-dev] ─ ruleResult ─────┤
                                            ↓
                              [api-ai-architect]
                                            ↓
                    01_contract-freeze.md  +  Postman Mock URL
                                            │
        ┌───────────┬───────────────┬───────┴────────┐
        ↓           ↓               ↓                ↓
   [data-arch]  [rule-engine]  [api-ai-arch]   [frontend-dev]
    DDL·시드     엔진·API       AI Mock·202      SCR-01·02
    ·엔티티                     ·PATCH
        └───────────┴───────────────┴────────────────┘
                            ↓  (각 모듈 완성 즉시)
                     [integration-qa] ──경계면 불일치──→ 양쪽 담당자
                            ↓
                   03_report.md · docs/e2e-result.md
```

## 에러 핸들링

| 상황 | 전략 |
|---|---|
| 계약 확정이 지연됨 | 가장 위험하다. `data-architect`·`rule-engine-dev`의 입력이 늦은 것이 원인이므로 그쪽을 먼저 푼다. FE는 기획서 8.4 예시 JSON으로 임시 진행시킨다 |
| 팀원 1명 실패/중지 | SendMessage로 상태 확인 → 1회 재시작. 재실패 시 백업 관계로 재할당 (`spec-steward`↔`frontend-dev`, `data-architect`↔`rule-engine-dev`, 기획서 14장) |
| 팀원 과반 실패 | 사용자에게 알리고 진행 여부를 확인한다 |
| DB 접속 실패 | 로컬 Docker PostgreSQL 16으로 폴백. 접속 정보 차이를 노트에 기록 |
| 데모 케이스 판정 불일치 | `integration-qa`가 원인 계층(시드/로드/매칭/억제/충돌/마스킹/매핑/렌더링)을 특정 → 해당 담당자에게 지시 → 재검증. 2회까지 |
| 계약 변경 요청 | 혼자 결정하지 않는다. 영향 범위(FE 코드·Postman Example·테스트)를 산정해 `spec-steward`와 리더가 승인. 계약서에 `## 개정` 추가 |
| 기획서 모순 발견 | 임의 해석 금지. `spec-steward`가 결정 요청으로 승격, 리더가 사용자에게 확인 |
| 범위 확장 요청 | 기획서 0.3 상한으로 거부하고 17장 향후 확장으로 이관. 3일·4인이다 |
| 팀원 간 데이터 충돌 | 삭제하지 않고 출처를 병기한 뒤 계약서 기준으로 판정 |
| 타임아웃 | 수집된 부분 결과로 진행. 리포트에 미완료 영역을 명시 |

## 테스트 시나리오

### 정상 흐름

1. 사용자가 "기획서대로 구현해줘" 요청
2. Phase 0 — `_workspace/` 없음 → 초기 실행
3. Phase 1 — 미결 항목 Q1~Q6를 권고안대로 결정, `decisions.md` 기록
4. Phase 2 — 6명 팀 구성, 19개 작업 등록
5. Phase 3 — `data-architect`·`rule-engine-dev` 입력 → `api-ai-architect`가 계약 확정 → 전원 통보 + Mock URL
6. Phase 4 — 4개 계층 병렬 구현, `integration-qa`가 모듈별 즉시 검증. 경계면 이슈 발견 시 양쪽에 통보
7. Phase 5 — E2E A·B·C·D 전부 통과, 리포트 생성
8. Phase 6 — 팀 정리, `_workspace/` 보존
9. 예상 결과: `backend/`·`frontend/`·`docs/` 산출물 + `docs/e2e-result.md`에 케이스 4종 통과 기록

### 에러 흐름 — 경계면 불일치

1. Phase 4에서 `frontend-dev`가 SCR-02 목록을 배열로 처리 (`res.data.filter`)
2. `integration-qa`가 "경계면 3단 대조" 작업에서 발견 — API는 `{items,page,size,total}` 봉투 반환
3. QA가 **양쪽**에 통보: `frontend-dev`에게 "`res.data.items`를 꺼낼 것 (`frontend/src/api/inspections.js:14`)", `api-ai-architect`에게 "봉투 형식이 계약서 표1과 일치함을 확인, 변경 불필요"
4. `frontend-dev`가 수정 후 QA에게 완료 통보
5. QA가 재검증 → 통과. 리포트의 실패 항목이 해소로 이동
6. Phase 5로 진행

### 에러 흐름 — 데모 케이스 실패

1. Phase 5에서 Case A가 규칙 3건으로 판정됨 (기대 2건)
2. `integration-qa`가 원인 계층을 "중첩 억제 미구현"으로 특정
3. `rule-engine-dev`에게 지시 — `ConflictResolver`에 포함 관계 매칭 억제 추가, 근거는 `open-questions.md` Q1
4. 수정 후 재검증 → 2건. Case B·C·D 회귀 없음 확인
5. 2회 재시도로도 실패하면 미해결로 사용자에게 보고하고, `spec-steward`에게 발표 대사를 실제 동작에 맞춰 수정하도록 지시한다
