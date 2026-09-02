# 경계면 교차 비교표 — `integration-qa`

**작성일:** 2026-09-02
**방법:** 생산자 코드와 소비자 코드를 **같이 열어** 대조하고, 그 위에 **실제 응답 JSON과 실제 화면 DOM 문자열**을 얹었다. 한쪽만 읽으면 각각 정상으로 보이므로 "실측" 열이 판정 근거다.
**대조 시점 실측 데이터:** Case A = inspection 104/111, Case B = 105/114, Case C = 106/113, Case D = 107/112 (백엔드 `localhost:8080`, 프런트 번들 jsdom 구동)

범례 — ○ 일치 · △ 문서만 불일치(구현 정상) · ✗ 불일치

---

## 1. 필드 명명 3단 대조 (DDL → API → FE)

| DB 컬럼 (snake) | API 필드 (camel) | FE 참조 | 실측 값 | 판정 |
|---|---|---|---|---|
| `message.message_id` | `messageId` | (읽지 않음, 계약상 존재) | `105` | ○ |
| `message.original_text` | **미노출** | — | 응답 전문에 `originalText` 키 **없음** | ○ |
| `message.submitted_text` | `submittedText` | `ChatView.vue:175`, `AuditView.vue:327,330` | `"A사 차세대 프로젝트 오픈 일정이 언제였지?"` / A는 `null` | ○ |
| `message.status` | `status` | `AuditView.vue` 목록 행·배지 | `"BLOCKED"` / `"PENDING_REVIEW"` / `"ALLOWED"` / `"MASKED"` | ○ |
| `inspection.inspection_id` | `inspectionId` | `ChatView.vue:107`, `AuditView.vue:113` | `105` | ○ |
| `inspection.phase` | `phase` | 상세 패널 | `"INPUT"` | ○ |
| `inspection.policy_snapshot` | `policySnapshot` | `VerdictCard`(code·version), `AuditView` 이력 | `{"policies":[{"policyId":1,"code":"P-PII","version":3,"ruleCodes":[…]}, …]}` | ○ (C9대로 4필드 전부) |
| `inspection.rule_result` | `ruleResult` | `VerdictCard`, `AuditView.vue:133-143` | `{"matches":[…],"appliedRuleCodes":[…]}` | ○ |
| `inspection.ai_status` | `aiStatus` | `ChatView.vue:190,205`, `AuditView.vue:363-369` | `"SKIPPED"`/`"PENDING"`/`"COMPLETED"`/`"FAILED"` 4값 전부 실측 | ○ |
| **`inspection.ai_result`** | **`aiAssessment`** | `ChatView.vue:200`, `AuditView.vue:375` | `{"riskCandidates":[…],"missingContext":[…],"reviewRequired":true}` | ○ **이름이 바뀌는 유일한 지점** |
| `inspection.final_decision` | `finalDecision` | 최종 판정 배지 | `"BLOCK"` / `"PENDING"` / `"ALLOW"` | ○ |
| `inspection.decided_by` | `decidedBy` | `AuditView.vue:151`, 목록 행 | `"RULE"` / `"HUMAN"` / `null` | ○ (`decision_source` 사용처 0건 — D10) |
| `inspection.created_at` | `createdAt` | 목록 행 | `"2026-09-02T07:20:35.560351Z"` | ○ UTC `Z` |
| `inspection.completed_at` | `completedAt` | 상세 "완료 시각" | `"2026-09-02T07:25:07.638397Z"` | ○ (단 PATCH 후 화면 갱신 누락 → 리포트 F6) |
| `inspection_finding.finding_id` | `findingId` | `AuditView.vue:160` | `57`(RULE) / `58`(AI) | ○ |
| `inspection_finding.source` | `source` | `AuditView.vue:138,146`, `ChatView.vue:150` | `"RULE"` / `"AI"` | ○ **인덱스가 아니라 `source`로 필터** |
| `inspection_finding.rule_id` | **미노출** | — | 응답에 키 없음 | ○ |
| `inspection_finding.code` | `code` | 규칙 행 / AI 후보 카드 | `"CONF-CLIENT-01"` / `"CONF-CLIENT-PROJECT"` | ○ |
| `inspection_finding.category` | `category` | 카테고리 라벨 | `"CONFIDENTIAL"` → 화면 "기밀" | ○ |
| `inspection_finding.span_start/end` | `spanStart`/`spanEnd` | **쓰지 않음** (D3) | `18`/`56`, AI는 `null`/`null` | ○ FE에 오프셋 산술 0곳 |
| `inspection_finding.action` | `action` | 규칙 행 | `"BLOCK"`/`"MASK"`/`"REVIEW"`, AI는 `null` | ○ |
| `inspection_finding.rationale` | `rationale` | AI 후보 카드 | AI만 값, RULE은 `null` | ○ |
| `inspection_finding.evidence` | `evidence` | AI 후보 출처 | `[{"source":"고객사 NDA 목록 v3","excerpt":"A사 — 비밀유지 2027.03까지, 일정·범위 포함"}]` | ○ **객체 배열**(문자열 배열 아님) |
| `inspection_finding.review_status` | `reviewStatus` | `AiCandidateList.vue:29` 배지·버튼 | `"CONFIRMED"`/`"SUGGESTED"`/`"ACCEPTED"`/`"REJECTED"` 4값 전부 실측 | ○ |
| `inspection_finding.reviewed_by` (BIGINT) | `reviewedBy` (**객체**) | `finding.reviewedBy.name` | `{"userId":4,"name":"박OO"}` | ○ 화면에 "박OO 확정" |
| `inspection_finding.reviewed_at` | `reviewedAt` | 확정 시각 | `"2026-09-02T07:25:12.25396Z"` | ○ |
| (파생) | `decision` (POST만) | `ChatView.vue:79,81,176,182`, `client.js:54` | `"BLOCK"`/`"PENDING"`/`"ALLOW"`/`"MASK"` | ○ |
| (설정 파생) | `pollAfterMs` (202만) | `ChatView.vue:105-112` | `2000` (202) / `null` (그 외) | ○ FE 상수 미사용 |
| (집계) | `ruleCount` (목록만) | 목록 "규칙 수" 컬럼 | A=**2**, B=**1**, C=0, D=1 | ○ D1·D9 |
| `department.dept_id/code/name` | `deptId`/`code`/`name` | `session.js`, 필터 | `{"deptId":4,"code":"INFOSEC","name":"정보보안팀"}` | ○ |
| `app_user.dept_id` | `department` (**중첩 객체**) | `user.department.name` (`session.js:44,47`) | `{"deptId":2,"code":"SALES","name":"영업팀"}` | ○ 평탄화 안 함 |
| `policy_rule.pattern` | **미노출 (C5)** | — | `GET /policies` 응답 전문에 `"pattern"` 문자열 **0회** | ○ |

**`user.department`의 두 얼굴 (계약대로, 혼동 주의)** — `GET /users`의 `items[].department`는 **객체**(`{deptId,code,name}`)이고, `GET /inspections/{id}`의 `user.department`는 **문자열**(`"영업팀"`)이다. 실측으로 둘 다 계약서 §1-2·§1-5대로다. FE도 각각 `user.department.name`(`session.js:47`)과 문자열 직접 출력(`AuditView` 패널 헤더 `"김OO · 영업팀 · 09-02 07:25"`)으로 맞게 쓰고 있다.

---

## 2. 응답 shape (생산자 DTO ↔ 소비자 참조)

| 대상 | 생산자 | 소비자 | 실측 | 판정 |
|---|---|---|---|---|
| 목록 봉투 | `api/dto/PageEnvelope.java:15` | `session.js:58,59,75` · `AuditView.vue:96` | 4개 목록 전부 최상위 키 `["items","page","size","total"]` | ○ |
| 비페이징 봉투 | `PageEnvelope.ofAll` `:18` | 동일 | `/departments` → `page=0,size=4,total=4` · `/policies?deptId=1` → `page=0,size=2,total=2` | ○ C1 |
| 페이징 봉투 | `PageEnvelope.of` `:23` | `AuditView.vue:91`(계약 검사) | `?size=5` → `page=0,size=5,total=110` / `?size=500` → **`size=100`으로 절삭** | ○ |
| POST 응답 필드 집합 | `MessageVerdictResponse.java:35-46` | `ChatView.vue` | **4개 상태 모두 최상위 키 11개 동일** — `messageId, inspectionId, decision, status, submittedText, policySnapshot, ruleResult, aiStatus, decidedBy, pollAfterMs, createdAt` | ○ C3 `always` |
| 202 본문 | 같은 DTO | `ChatView.vue:190` 가드 | **`aiAssessment` 키 없음** · `Location: /api/v1/inspections/105` · `pollAfterMs=2000` | ○ |
| 상세 응답 | `InspectionDetailResponse.java:32-47` | `AuditView.vue` · `ChatView` 폴링 | 최상위 키 15개, `originalText` 없음 | ○ |
| finding 항목 | `FindingDto.java:32-44` | `AiCandidateList.vue` · `AuditView.vue:133-146` | 키 12개, RULE/AI가 같은 shape에 값만 다름 | ○ |
| PATCH 응답 | `ReviewResponse.java:22-27,41-46` | `AuditView.vue:158-190` | `{findingId, reviewStatus, reviewedBy, reviewedAt, inspection:{inspectionId, finalDecision, decidedBy, status, submittedText}}` | ○ / `completedAt` 부재 → F6 |
| 에러 봉투 | `ErrorResponse.java:12` | `lib/contract.js` `errorText` | `{code, message, details}` — `details`는 `null`이어도 키 유지 | ○ |
| **403 판정 객체** | 컨트롤러가 직접 `ResponseEntity` 반환 (advice가 삼키지 않음) | `api/client.js:54` | 403 본문에 **`code` 키 없음**, `decision:"BLOCK"` | ○ **C2** |

---

## 3. 상태 코드 ↔ FE 분기

| 상황 | 계약 | 실측 HTTP | FE 처리 | 판정 |
|---|---|---|---|---|
| ALLOW | 200 | 200 | S2 "허용 · 규칙 0건" | ○ |
| MASK | 200 | 200 | S3 "마스킹" + `<mark>[전화번호]</mark>` | ○ |
| REVIEW | 202 + `Location` | 202 + `Location: /api/v1/inspections/105` | S5-a 스피너 → 폴링 | ○ |
| BLOCK | **403 판정 객체** | 403, `code` 없음 | 인터셉터가 `Promise.resolve` → S4 차단 카드 | ○ |
| 본문 누락·공백 | 400 `INVALID_REQUEST` | 400 `INVALID_REQUEST` | `errorText`로 배너 | ○ |
| `X-User-Id` 없음 | 400 `MISSING_USER_HEADER` | 400 `MISSING_USER_HEADER` | — | ○ (`rule-engine-dev` 노트 §5.2의 "아직 Spring 기본 400" **해소됨**) |
| `X-User-Id` 비숫자 | 400 `INVALID_USER` | 400 `INVALID_USER` | — | ○ |
| `X-User-Id` 미존재 사용자 | 400 `INVALID_USER` | 400 `INVALID_USER` | — | ○ |
| `deptId` 누락 | 400 `INVALID_PARAMETER` | 400 `INVALID_PARAMETER` ("deptId는 필수입니다.") | — | ○ **C6** |
| `deptId` 비숫자 | 400 `INVALID_PARAMETER` | 400 `INVALID_PARAMETER` | — | ○ |
| `status` enum 외 | 400 `INVALID_PARAMETER` | 400 `INVALID_PARAMETER` | — | ○ |
| `page` 음수 | 400 `INVALID_PARAMETER` | 400 `INVALID_PARAMETER` | — | ○ |
| 없는 inspection | 404 `INSPECTION_NOT_FOUND` | 404 `INSPECTION_NOT_FOUND` | — | ○ |
| 없는 finding | 404 `FINDING_NOT_FOUND` | 404 `FINDING_NOT_FOUND` | — | ○ |
| 경로 밖 finding | 404 `FINDING_NOT_FOUND` | 404 (inspection 104에 finding 62 요청) | — | ○ |
| 이미 처리된 finding | 409 `FINDING_ALREADY_REVIEWED` | 409 `FINDING_ALREADY_REVIEWED` | `AuditView.vue:193-197` → "이미 처리된 항목입니다" + 재조회 | ○ |
| 규칙 finding에 PATCH | 409 `RULE_FINDING_NOT_REVIEWABLE` | 409 `RULE_FINDING_NOT_REVIEWABLE` | — | ○ **D13** |
| `reviewStatus:"CONFIRMED"` (SUGGESTED 대상) | 400 `INVALID_REQUEST` | 400 `INVALID_REQUEST` | — | ○ |
| `reviewStatus:"SUGGESTED"` / 오타 값 | 400 `INVALID_REQUEST` | 400 `INVALID_REQUEST` | — | ○ |
| 201 | **미사용 (D4)** | 어떤 경로에서도 201 미관측 | — | ○ |

**검사 순서 404 → 409 → 400도 실측으로 확인됐다.** 이미 `ACCEPTED`인 finding에 `reviewStatus:"CONFIRMED"`를 보내면 400이 아니라 **409 `FINDING_ALREADY_REVIEWED`**가 나온다(계약서 §1-7 말미대로). 값 검증만 따로 보려면 `SUGGESTED` 상태의 finding으로 시험해야 하며, 그때는 400이 나온다.

---

## 4. 비동기 경계 (202 → 폴링 → COMPLETED → 확정)

| 단계 | 생산자 실측 | 소비자 실측 | 판정 |
|---|---|---|---|
| 1. 202 수신 | `HTTP 202`, `Location: /api/v1/inspections/105`, `aiStatus:"PENDING"`, `decidedBy:null`, `pollAfterMs:2000`, `submittedText` **채워짐**(D7), **`aiAssessment` 키 없음** | `ChatView.vue:81` `decision==='PENDING'` → `startPolling` | ○ |
| 2. PENDING 창 | 즉시 `GET` → `aiStatus:"PENDING"`, `aiAssessment:null`, `findings:[RULE 1건]`, `finalDecision:"PENDING"`, `completedAt:null` | 화면에 **"보안 검토 중 · 1초 경과 · 폴링 0회"** 표시, AI 후보 미표시 | ○ 스피너가 실제로 노출된다 |
| 3. COMPLETED | 2회 폴링(2s 간격) 후 **t+4.07s**에 `COMPLETED`. Mock 지연 2.5초가 실제로 걸린다 | `usePolling.js:98` `isDone = aiStatus!=='PENDING'` → 정지. 화면에 AI 후보 + 근거 + 출처 + "제안됨" | ○ **D12** |
| | `findings` 순서 `[RULE CONF-CLIENT-01, AI CONF-CLIENT-PROJECT]` | 화면에서도 규칙 섹션(offset 77) < AI 섹션(offset 133) | ○ |
| | `status`는 여전히 `PENDING_REVIEW`, `finalDecision:"PENDING"`, `decidedBy:null` | 직원 화면에 ACCEPT/REJECT **없음** | ○ 4장 책임 경계 |
| 4. 확정 (PATCH) | `200` → `finalDecision:"BLOCK"`, `decidedBy:"HUMAN"`, `status:"BLOCKED"`, `submittedText` **보존**, `reviewedBy:{4,"박OO"}`. `aiStatus`는 `COMPLETED` 유지 | `AuditView.vue:165-190` 재조회 없이 패널·목록 행 동시 갱신. 배지 "확정(위반)", 최종 판정 "차단", 확정 주체 "담당자" | ○ **D14** |
| 4′. 기각 경로 | `200` → `finalDecision:"ALLOW"`, `decidedBy:"HUMAN"`, `status:"ALLOWED"`, `submittedText` 보존 | — | ○ |
| 실패 경로 | `A사 차세대 프로젝트 일정 __FAIL__` → 202 → **t+0.53s** `aiStatus:"FAILED"`, `status:"PENDING_REVIEW"` **유지**, AI finding 미생성, `completedAt` 채워짐 | `ChatView.vue:205` `aiStatus==='FAILED'` → "자동 검토 실패 — 담당자 확인 중" | ○ |
| 폴링 정리 | — | `usePolling.js:115` `onUnmounted(stop)` + `runId` 토큰(`:83,89,94`)으로 stop 이후 응답의 재예약 차단. 상한 30회(`:8,103`) | ○ |

> Mock 실패는 지연(2.5초)보다 **먼저** 발생한다 — 계약서 §5-3의 평가 순서(1 `hits` 무결성 → 2 fail-keyword → 3 지연)대로다. 그래서 실패 경로는 0.5초 만에 `FAILED`가 된다.

---

## 5. 규칙 판정 경계 (시드 → 엔진 → 응답 → 화면)

| 케이스 | 시드/규칙 | 엔진 실측 | 응답 실측 | 화면 실측 | 판정 |
|---|---|---|---|---|---|
| A (user 1, DEV) | 원시 매칭 4건 | 중첩 억제 2건 | `matches` 2건 `SEC-DBURL-02 [18,56]` · `PII-RRN-01 [73,87]`, `appliedRuleCodes` **7건**(억제된 2개 포함) | "규칙 2건" + 2줄. `SEC-PRIVIP-03`·`PII-EMAIL-04` **미표시** | ○ **D1·D11** |
| B (user 2, SALES) | `CONF-CLIENT-01` KEYWORD | 규칙당 1건 | `matches` 1건, `matchedKeyword:"A사"`, finding(RULE) 1건, 목록 `ruleCount=1` | 규칙 1건 · `'A사'` 표시 | ○ **D9** |
| C (user 1, DEV) | **B와 같은 문장** | 매칭 0 | `matches:[]`, `appliedRuleCodes` 7건(**`CONF-CLIENT-01` 없음**) | "허용 · 규칙 0건" | ○ 부서 N:M 증명 |
| D (user 3, HR) | `PII-PHONE-03` | 마스킹 실행 | `submittedText:"지원자 연락처 [전화번호] 로 …"`, `span [8,21]` | `<mark>[전화번호]</mark>`, 원문 번호 미표시 | ○ **D3·D5** |
| A의 마스킹 | BLOCK ⇒ `Masker` 미호출 | — | `submittedText:null` | SCR-02 "차단되어 전송 본문이 저장되지 않았습니다" | ○ **D5·D14** |

**정책 적용 범위 (D8)** — `GET /policies?deptId=1` → 2건 `[P-PII, P-SEC]` 둘 다 `appliedVia:"GLOBAL"` / `deptId=2` → 3건 `[P-PII, P-SEC, P-CONF]`. 화면 캡션도 "적용 정책 2건 (P-PII, P-SEC)" / "3건 (P-PII, P-SEC, P-CONF)"으로 **API 응답값을 그대로 센다**(`PolicyCaption.vue` ← `session.policies.length`).

---

## 6. 용어 매핑 (enum → 화면)

| enum | 계약서 §3 표기 | 실제 화면 | 판정 |
|---|---|---|---|
| `ALLOWED`/`ALLOW` | 허용 | 허용 | ○ |
| `MASKED`/`MASK` | 마스킹 | 마스킹 | ○ |
| `BLOCKED`/`BLOCK` | 차단 | 차단 | ○ |
| `PENDING_REVIEW`/`PENDING` | 검토 대기 | 검토 대기 | ○ |
| `SUGGESTED` | 제안됨 | 제안됨 | ○ |
| `ACCEPTED` | 확정(위반) | 확정(위반) | ○ |
| `REJECTED` | 기각 | 기각 | ○ |
| `CONFIRMED` | 확정 | **확정(규칙)** | ○ (기획서 5.6 `확정(규칙)`을 채택 — `frontend-dev` 노트 7-6) |
| `RULE`/`HUMAN` | 규칙 / 담당자 | 규칙 / 담당자 | ○ **D10** |
| `LEGAL`/`INTERNAL` | 법령 / 사규 | 법령 / 사규 | ○ |
| `PII`/`SECRET`/`CONFIDENTIAL` | 개인정보 / 자격증명 / 기밀 | 개인정보 / 자격증명 / 기밀 | ○ |
| **`aiStatus` 4값** | 미실행 / 분석 중 / 완료 / 실패 | `''` / 검토 중 / **검토 완료** / 자동 검토 실패 | ✗ 리포트 **F7** |

내부 enum 문자열(`ALLOWED`, `SUGGESTED` 등)이 화면 DOM 텍스트에 노출되는 곳 **0곳**.

---

## 7. Postman 컬렉션 ↔ 실제 응답

**갱신본(`mtime 16:21:15`) 기준 재판정이다.** Example 13종을 실측 응답과 ① 키 경로 재귀 비교 ② 핵심 값 비교로 대조했다.

### 7-1. 키 경로 대조 — 13종 중 12종 일치

| Example | 결과 |
|---|---|
| `200 부서 4건` · `200 사용자 4건` · `200 영업팀(정책)` · `MASK (200)` · `REVIEW (202)` · `ALLOW (200)` · `PENDING (200)` · `COMPLETED (200)` · `404` · `200 목록` · `200 ACCEPT → BLOCKED` · `409` | ○ 일치 (12종) |
| `BLOCK (403) — Case A` | ✗ `ruleResult.matches[].matchedKeyword` 부재 (**F1**) |

Example에만 있고 실제에 없는 필드는 **0건** — 존재하지 않는 필드를 약속하는 Example은 없다.

### 7-2. 값 대조

| 대상 | Example | 실측 | 판정 |
|---|---|---|---|
| **BLOCK Example `policySnapshot[].ruleCodes`** | `P-PII: [PII-CARD-02, PII-EMAIL-04, PII-PHONE-03, PII-RRN-01]` · `P-SEC: [SEC-AWSKEY-01, SEC-DBURL-02, SEC-PRIVIP-03]` | 동일 | ○ **해소** (리더 요청 2) |
| BLOCK Example `appliedRuleCodes` | `[PII-CARD-02, PII-RRN-01, SEC-AWSKEY-01, SEC-DBURL-02, PII-PHONE-03, SEC-PRIVIP-03, PII-EMAIL-04]` | 동일 | ○ 실행 순서 그대로 |
| BLOCK Example `matches[0]` | `matchedKeyword` 없음 | `"matchedKeyword": null` | ✗ **F1** |
| BLOCK Example `matches[1].source` | `"개인정보보호법"` | `"개인정보보호법 제24조"` | ✗ **F2** |
| `GET /policies` 규칙 코드 | `P-PII: 2건` · `P-SEC: 1건` · `P-CONF: 1건` | `P-PII: 4건` · `P-SEC: 3건` · `P-CONF: 1건` | ✗ **F3** — 키 경로는 일치하므로 값 비교로만 드러난다 |
| `GET /users?deptId=2` | userIds `[1,2,3,4]`, `total:4` | userIds `[2]`, `total:1` | ✗ **F4** |
| `404` / `409` `message` | 영문 | 한글 (`code`는 일치) | ✗ **F5** |

### 7-3. `frontend-dev` 노트 §7 항목의 종결 상태

| 항목 | 지적 내용 | 현재 |
|---|---|---|
| 7-1 | Example에 `policySnapshot`·`pollAfterMs`·`createdAt`·`appliedRuleCodes` 누락 | **해소** — POST Example 4종 전부 최상위 키 11개 |
| 7-1 | BLOCK Example의 `matches[].matchedKeyword` 누락 | ✗ **미해소** — **F1** |
| 7-2 | COMPLETED Example의 AI finding `category` 없음, `evidence` 문자열 배열 | **해소** — `category:"CONFIDENTIAL"`, `evidence:[{source,excerpt}]`, 정렬 RULE→AI |
| 7-3 | PENDING Example의 `x-mock-response-name`이 COMPLETED를 가리킴 | **해소** — 4종 모두 자기 이름과 일치 |
| 7-4 | Case B/C Example이 `{{userId}}` | **해소** — BLOCK=1 · MASK=3 · REVIEW=2 · ALLOW=1 |
| 7-5 | PATCH 응답의 `submittedText` | **해소** — Example·실제 응답 모두 포함. 값·화면까지 검증(리포트 §1-2) |
| — | `PII-RRN-01.source` / 정책 규칙 축약 / `users` 필터 / 영문 message | ✗ **F2·F3·F4·F5** |

---

## 8. 라우팅

| 생산자 | 소비자 | 실측 | 판정 |
|---|---|---|---|
| `router/index.js` `/chat` | `AppHeader.vue:20` `<RouterLink to="/chat">` | 탭 이동 동작 | ○ |
| `router/index.js` `/admin/audit` | `AppHeader.vue:21` | 탭 클릭 → SCR-02 목록 20행 렌더 | ○ |
| `/` → `/chat` 리다이렉트 | — | (정적 확인) | ○ |
| URL에 `ai`·`mock` 노출 | — | 7개 경로 어디에도 없음 | ○ 계약서 §7 |
