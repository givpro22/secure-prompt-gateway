# 통합 정합성 검증 리포트 — `integration-qa`

**작성일:** 2026-09-02
**대상:** `backend/` · `frontend/` 전체, `docs/`, DB(`jdbc:postgresql://localhost:55432/gateway`)
**판정 기준:** `_workspace/00_input/decisions.md` D1~D15 · `_workspace/01_api-ai-architect_contract-freeze.md`(개정 2까지) · `docs/demo-script.md`
**검증 방식:** 정적 교차 대조(생산자·소비자 코드 동시 읽기) + 실제 서버 E2E(HTTP) + 실제 빌드 산출물의 화면 구동 검증(jsdom)

---

## 요약

| 구분 | 건수 |
|---|---|
| **통과** | **96** |
| **실패** | **7** (전부 데모 비차단) |
| **미검증** | **8** |
| **데모 차단 이슈** | **0** — 단, 아래 E2는 데모 당일 재발 시 차단 요인이 된다 |

실패 7건 중 **6건이 문서(Postman 컬렉션) 결함이고 구현 결함은 1건**(F6, 화면의 "완료 시각" 미갱신)이다.
**데모 케이스 A·B·C·D는 API와 화면 양쪽에서 전부 기대대로 동작한다.**

가장 주의할 것은 실패 항목이 아니라 **환경 이슈 E2**다 — 검증 도중 내가 만들지 않은 행이 DB에 두 번 나타났다. 데모 직전 시드 복원을 해도 다시 오염될 수 있다.

---

## 1. 실패

> **재판정 기록 (2026-09-02).** 리더가 `docs/ai-gateway-v1.postman_collection.json`이 검증 도중(파일 mtime `16:21:15`) 갱신되었음을 알려 왔다. 최초 판독이 갱신 직후 몇 초 이내였으므로 **파일을 다시 읽고 Example 13종 전부를 실측 응답과 프로그램으로 재대조**했다(키 경로 재귀 비교 + 핵심 값 비교). 아래 F1~F5는 **갱신본 기준 재판정 결과**다. 갱신으로 **해소된 항목**과 재판정 상세는 §1-1에 적었다.

| # | 경계면 | 증상 | 근거 (파일:라인) | 원인 계층 | 담당 | 데모 차단 |
|---|---|---|---|---|---|---|
| F1 | Postman Example ↔ 실제 응답 | `BLOCK (403) — Case A` Example의 `ruleResult.matches[]` 두 항목에 **`matchedKeyword` 키가 없다.** 실제 응답은 둘 다 `"matchedKeyword": null` | `docs/ai-gateway-v1.postman_collection.json` — `POST /messages` › Example `BLOCK (403) — Case A` | 응답 매핑(문서) | `api-ai-architect` | 아니오 |
| F2 | Postman Example ↔ 시드 | 같은 Example의 `PII-RRN-01.source`가 `"개인정보보호법"`. 실제 시드·응답은 **`"개인정보보호법 제24조"`** | 같은 파일, 같은 Example / 실측: `GET /api/v1/messages` 403 본문 | 시드 ↔ 문서 | `api-ai-architect` | 아니오 |
| F3 | Postman Example ↔ 실제 응답 | `GET /policies?deptId=2` Example의 규칙 목록이 축약본이다. **P-PII 2건**(실제 4건 — `PII-CARD-02`·`PII-EMAIL-04` 누락), **P-SEC 1건**(실제 3건 — `SEC-AWSKEY-01`·`SEC-PRIVIP-03` 누락) | 같은 파일 › `GET /policies?deptId=` › Example `200 영업팀` | 응답 매핑(문서) | `api-ai-architect` | 아니오 |
| F4 | Postman 요청 ↔ Example | `GET /users` 요청 URL이 `?deptId=2`인데 Example 본문은 **4명 전체**(`total:4`)다. **실측 `?deptId=2`는 1명**(`userId 2`, `total:1`)이므로 Example이 엔드포인트 동작과 어긋난다 | 같은 파일 › `GET /users` (요청 URL·Example URL 둘 다 `?deptId=2`) | 문서 | `api-ai-architect` | 아니오 |
| F5 | Postman Example ↔ 실제 응답 | 404·409 Example의 `message`가 영문(`"inspection 999999 not found"`, `"finding 502 is already ACCEPTED"`). 실제 서버는 한글(`"존재하지 않는 inspection입니다: 999999"`, `"finding 58는 이미 ACCEPTED 상태입니다."`). `code`는 일치 | 같은 파일 › `GET /inspections/{id}` Example `404`, `PATCH …` Example `409` | 문서 | `api-ai-architect` | 아니오 |
| **F6** | **PATCH 응답 ↔ SCR-02 상세 패널** | **ACCEPT 후 상세 패널의 "완료 시각"이 갱신되지 않는다.** 서버는 `completed_at`을 사람 확정 시각으로 갱신하지만(계약서 §1-7) PATCH 응답에 `completedAt`이 없어 FE가 반영할 수 없다. 화면에서 `완료 시각 07:25:07`(AI 완료)과 바로 아래 `확정자 박OO · 07:25:12`가 어긋나 보인다 | 생산자 `backend/src/main/java/com/skala/gateway/api/dto/ReviewResponse.java:41-46` (`InspectionState`에 `completedAt` 없음) / 소비자 `frontend/src/views/AuditView.vue:155-190` (`onReview`가 `finalDecision`·`status`·`decidedBy`·`submittedText`만 갱신) | 응답 매핑 | `api-ai-architect` (+`frontend-dev`) | 아니오 |
| F7 | 용어 매핑 ↔ 계약서 §3 | `aiStatus` 화면 표기가 계약서 §3 표와 다르다. §3은 `미실행 / 분석 중 / 완료 / 실패`, 구현은 `'' / 검토 중 / 검토 완료 / 자동 검토 실패` | `frontend/src/lib/terms.js:26-31` | FE 렌더링 | `spec-steward` 판정 후 `frontend-dev` | 아니오 |

### 수정 방법

**F1~F5 (Postman 컬렉션)** — `docs/ai-gateway-v1.postman_collection.json`을 실제 응답으로 교체한다. 아래 명령의 출력을 그대로 Example 본문에 넣으면 된다.

```bash
curl -s -X POST localhost:8080/api/v1/messages -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
  -d '{"text":"이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나"}'   # F1·F2
curl -s 'localhost:8080/api/v1/policies?deptId=2'                                                    # F3
curl -s 'localhost:8080/api/v1/users?deptId=2'                                                       # F4
curl -s localhost:8080/api/v1/inspections/999999                                                     # F5
```

`GET /users` Example(F4)은 요청 URL에서 `?deptId=2`를 빼거나, Example 본문을 `curl -s 'localhost:8080/api/v1/users?deptId=2'`(1명)로 바꾼다. 둘 중 어느 쪽이든 요청과 본문이 같은 이야기를 하면 된다.

**F6** — 둘 중 하나. ① `ReviewResponse.InspectionState`에 `OffsetDateTime completedAt`을 추가하고(`ReviewResponse.java:41-46`, `InspectionState.of`에서 `ApiTimes.utc(inspection.getCompletedAt())`) `AuditView.vue`의 `onReview`에 `detail.value.completedAt = result.inspection.completedAt` 한 줄을 더한다. ② 서버를 건드리지 않는다면 FE가 확정 직후 해당 건만 `fetchInspection`으로 재조회한다. **①을 권한다** — 계약서 §1-7이 "FE가 한 번 더 조회하지 않고 갱신할 수 있게" PATCH 응답에 재산출 상태를 싣기로 한 취지에 맞고, `submittedText`를 되살린 것(개정 2)과 같은 이유다.

**F7** — 계약서 §3 표와 `docs/screen-spec.md` 중 무엇이 SSOT인지 `spec-steward`가 한 줄로 확정한 뒤 `terms.js:26-31`을 맞춘다. 데모 대사 용어(허용/마스킹/차단/검토 대기/제안됨/확정(위반)/기각)에는 영향이 없다.

---

## 1-1. Postman 컬렉션 재판정 (갱신본 기준)

**방법.** Example 13종 각각에 대해 ① **키 경로 재귀 비교**(배열은 원소 경로의 합집합으로 접어 비교) ② **핵심 값 비교**를 실측 응답과 프로그램으로 수행했다. 키 경로 비교만으로는 "배열 원소 개수·값" 차이를 못 잡으므로 값 비교를 함께 돌렸다 — 실제로 F3이 그 경우다(키 경로는 일치, 원소 개수가 다름).

### 키 경로 대조 — 13종 중 12종 일치

| Example | 결과 |
|---|---|
| `200 부서 4건` · `200 사용자 4건` · `200 영업팀(정책)` | 일치 |
| `MASK (200)` · `REVIEW (202) — Case B` · `ALLOW (200) — Case C` | 일치 |
| `PENDING (200)` · `COMPLETED (200)` · `404` | 일치 |
| `200 목록` · `200 ACCEPT → BLOCKED` · `409` | 일치 |
| **`BLOCK (403) — Case A`** | **불일치 1건** — Example에 없는 실제 필드 `ruleResult.matches[].matchedKeyword` (**F1**) |

Example에만 있고 실제에 없는 필드는 **0건**이다 — 즉 Example이 존재하지 않는 필드를 약속하는 경우는 없다.

### 갱신으로 해소된 항목

| 항목 | 갱신 전 | 갱신본 실측 |
|---|---|---|
| **`ruleCodes` 정렬 (리더 요청 2)** | BLOCK Example이 실측과 달랐음 | **해소·일치.** `P-PII: [PII-CARD-02, PII-EMAIL-04, PII-PHONE-03, PII-RRN-01]`, `P-SEC: [SEC-AWSKEY-01, SEC-DBURL-02, SEC-PRIVIP-03]` — 정책 안 사전순으로 실측과 **완전 일치** |
| `appliedRuleCodes` | — | **일치.** `[PII-CARD-02, PII-RRN-01, SEC-AWSKEY-01, SEC-DBURL-02, PII-PHONE-03, SEC-PRIVIP-03, PII-EMAIL-04]` — 실행 순서 그대로이며 `ruleCodes`와 정렬이 다른 것이 의도대로다(`rule-engine-dev` 노트 3.6) |
| `policySnapshot`·`pollAfterMs`·`createdAt` | 4개 Example에서 누락 | **해소.** POST 응답 Example 4종 전부 최상위 키 11개 동일 — §1-4 "필드 집합 동일"이 샘플에서도 성립 |
| COMPLETED Example의 `category`·`evidence` | `category` 없음, `evidence`가 문자열 배열 | **해소.** `category:"CONFIDENTIAL"`, `evidence:[{source,excerpt}]`, findings 13필드 명시, 정렬도 RULE→AI |
| `x-mock-response-name` | PENDING Example이 COMPLETED를 가리킴 | **해소.** 4종 모두 자기 이름과 일치 |
| Case B/C 계정 | `{{userId}}` | **해소.** BLOCK=1 · MASK=3 · REVIEW=2 · ALLOW=1 |
| PATCH 응답의 `submittedText` | 없음 | **해소.** Example·실제 응답 모두 포함 (아래 §1-2) |

### 갱신본에서도 남은 항목

**F1** `BLOCK` Example `matches[]` 2건 모두 `matchedKeyword` 키 없음 — 실측은 둘 다 `"matchedKeyword": null`. `MASK` Example에는 이미 있어 같은 컬렉션 안에서 표현이 갈린다.
**F2** 같은 Example `PII-RRN-01.source` = `"개인정보보호법"` / 실측 `"개인정보보호법 제24조"`.
**F3** `GET /policies?deptId=2` Example 규칙 축약 — `P-PII` 2건(실측 4건), `P-SEC` 1건(실측 3건). 키 경로는 일치하므로 **값 비교로만 드러난다**.
**F4** `GET /users` — 위 §1 표.
**F5** 404/409 Example `message` 영문 / 실측 한글. `code`는 일치하고 FE는 `code`만 쓰므로 영향은 문서에 한정된다.

> **계약서를 판정 기준으로 유지한다는 리더 판단에 동의한다.** 재판정에서도 **계약서 §1-5와 실제 구현이 일치하고 Example만 어긋난** 구조였다. 계약 변경은 PATCH 응답의 `submittedText` 추가 1건뿐이며 그것은 D14의 귀결이다.

## 1-2. PATCH 응답의 `inspection.submittedText` (리더 요청 1) — **통과**

D14가 화면까지 지켜지는지의 관문이므로 API·DB·화면 3층에서 확인했다.

| 확인 | 결과 |
|---|---|
| 응답에 `submittedText` **키가 존재**하는가 | 존재 (`'submittedText' in inspection` = true) |
| **실제 값**이 실려 오는가 (`null`이 아닌가) | `"A사 차세대 프로젝트 오픈 일정이 언제였지?"` |
| ACCEPT **전** `GET /inspections/{id}` | `"A사 차세대 프로젝트 오픈 일정이 언제였지?"` |
| **PATCH 응답** | 동일 |
| ACCEPT **후** `GET /inspections/{id}` | 동일 |
| 세 값 동일 여부 | **동일** |
| 동시 반환된 재산출 상태 | `finalDecision:"BLOCK"` · `status:"BLOCKED"` · `decidedBy:"HUMAN"` |
| **화면** — ACCEPT 직후 SCR-02 상세 패널 본문 | **잔존** (§5 B-7). `AuditView.vue:176-186`이 `'submittedText' in result.inspection` 분기로 서버 값을 그대로 쓰고 추론하지 않는다 |
| DB | `message.submitted_text` 변경 없음. 불변식 재검사 0 위반 (§8) |

**데모 1:50 장면(ACCEPT 직후 본문 보존)이 API·화면 양쪽에서 성립한다.**

---

## 2. 환경 이슈 — 데모 운영에 직접 영향

| # | 내용 | 조치 |
|---|---|---|
| **E1** | **인수 시점 DB가 시드 상태가 아니었다.** 리더 브리핑은 "시드 상태로 복원되어 있음(103/103/54)"이었으나 실제는 **message 110 / inspection 110 / finding 65**, 시퀀스 111/111/67이었다. `message_id` 104~111에 이전 회차 검증 데이터(Case A·B·D 재현분, ACCEPT/REJECT 완료건 포함)가 남아 있었다 | 검증 착수 시 내가 시드 상태로 되돌렸고, 종료 시 다시 되돌렸다 |
| **E2** | **저장소·환경이 독점 상태가 아니었다.** ① `07:17:33Z` 내가 만들지 않은 `PENDING_REVIEW`(user 2) 1건 — 이후 소멸 ② `07:26:09Z` 내가 만들지 않은 `MASKED` Case D(user 3) 1건 — 잔존 ③ 내가 종료시킨 백엔드와 별개의 프로세스가 8080을 점유한 정황 ④ **1차 정리 완료 후 비워 둔 8080·5173이 다시 점유됨**(새 PID). ⑤ `docs/ai-gateway-v1.postman_collection.json`이 검증 도중 갱신됨. **리더 확인 결과 ③~⑤는 `api-ai-architect`의 컬렉션 보정 작업이었다.** ①②는 그 작업의 부수 효과일 가능성이 높다. 7분 유휴 관찰(`07:28`~`07:35`)에서는 추가 쓰기가 없었다 | **대체로 해명됨.** 다만 브리핑의 "다른 에이전트는 전부 유휴, 저장소 독점"은 실제와 달랐다. **QA 실행 구간과 다른 에이전트의 작업이 겹치면 판정이 흔들린다** — 이번에는 컬렉션 재판정으로 복구했다. 리허설 직전에 §11 확인 명령을 한 번 더 실행할 것 |
| **E3** | `./gradlew test`(37건)는 전부 `@Transactional`이라 **행은 남기지 않지만 시퀀스는 소모한다.** 실측으로 `message_id` 104~119 16개가 소모되었다 | 시드 상태 판정은 **행 수 + 시퀀스** 둘 다 봐야 한다. 반대로 **시퀀스 불연속 자체는 오염 증거가 아니다** |

---

## 3. 미검증

아직 만들어지지 않았거나 이번 회차에서 수행할 수 없었던 것이다. **실패가 아니다.**

| 항목 | 사유 |
|---|---|
| 실제 브라우저(Chrome) 육안 확인 | 브라우저 선택에 사용자 확인이 필요한데 서브 에이전트라 요청할 수 없다. **대체 수행:** `vite build --mode development` 산출물을 jsdom에 마운트해 **실제 백엔드에 HTTP로 붙여** 45항목을 구동 검증했다(§5). 픽셀·색상·레이아웃만 미검증이며 DOM·문구·상호작용은 검증됨 |
| `_workspace/02_api-ai-architect_*_notes.md` | **파일이 없다.** 구현 노트는 `data-architect`·`rule-engine-dev`·`frontend-dev` 3종뿐이다(`docs/submission-checklist.md` #18도 "3종"으로 적고 있다). 해당 담당자의 미완료 항목 목록을 대조할 수 없었다 |
| Postman Mock Server 실동작 | Mock Server URL 미수령. 컬렉션 파일 자체는 검증했다 |
| `LlmAiInspector` 실제 LLM 호출 | 범위 밖 (기획서 0.4). 클래스 골격만 존재하는 것이 설계대로다 |
| 부분 겹침 마스킹 | 데모 데이터에 사례가 없다. `MaskerTest` 4건이 동작을 고정하고 있으나 E2E 경로로는 재현 불가 |
| ERD PNG · Swagger 캡처 · 폴더 구조 캡처 · 백업 영상 | 사람 작업 산출물. `spec-steward`의 추적 대상 |
| git 커밋 | **0건.** 저장소 전체가 untracked다(`docs/submission-checklist.md` #1과 동일 관측). 제출물 #1의 남은 작업 |
| `GET /inspections` 기간 필터(`from`/`to`) 경계 | 파라미터 파싱과 400 처리는 검증했으나, `to` 미만 경계의 하루 단위 동작은 시드 시각 분포상 확정적으로 재현하지 못했다 |

---

## 4. 통과 — 고위험 경계면 9종 + 이번 회차 추가분

`demo-verification` 스킬의 고위험 경계면 표 순서다. 근거는 **생산자·소비자 양쪽**을 적었다.

| # | 경계면 | 결과 | 근거 |
|---|---|---|---|
| 1 | 202 → 폴링 → COMPLETED | **통과** | 생산자: 202 본문 최상위 키 11개에 **`aiAssessment` 키 자체가 없음**(실측). 소비자: `ChatView.vue:190` `v-if="entry.aiStatus === 'COMPLETED'"` 가드 안에서만 `entry.inspection.aiAssessment` 접근(`:200`), `AuditView.vue:363-375`도 `SKIPPED`/`PENDING`/`FAILED`를 먼저 걸러낸 뒤에만 접근 |
| 2 | 403 BLOCK | **통과** | 생산자: 403 본문에 `code` 필드 없음, `decision:"BLOCK"`인 판정 객체(실측). 소비자: `api/client.js:54` `status===403 && data?.decision==='BLOCK'` → `Promise.resolve`. 화면에 "통신 오류"가 아니라 S4 차단 카드가 그려짐(§5 A-2) |
| 3 | 목록 봉투 | **통과** | 생산자: `PageEnvelope.java:15`, 4개 목록 전부 `{items,page,size,total}`(실측). 소비자: `stores/session.js:58,59,75` · `AuditView.vue:96`이 `.items`를 꺼냄. `AuditView.vue:91`이 `Array.isArray(envelope.items)`를 계약 검사로 확인 |
| 4 | snake ↔ camel | **통과** | `ai_status`→`aiStatus` · `submitted_text`→`submittedText` · `decided_by`→`decidedBy` · `review_status`→`reviewStatus` · `ai_result`→**`aiAssessment`**(유일한 개명 지점) 전부 실측 확인. 엔티티를 직렬화하지 않고 DTO에서 명시 변환(§6 매트릭스) |
| 5 | span 좌표계 | **통과** | 생산자: `spanStart/spanEnd`는 원문 기준(A: `[18,56]`·`[73,87]`). 소비자: FE에 오프셋 산술이 없다. `MaskedText.vue`가 `terms.js:53`의 라벨 5종을 문자열 검색해 `<mark>` — 화면에서 `<mark>[전화번호]</mark>` 확인(§5 D-4) |
| 6 | `decidedBy` 전이 | **통과** | PATCH ACCEPT 후 `decidedBy: "HUMAN"`, 화면 "확정 주체 담당자"(§5 B-8·B-10). 쓰기 지점은 `ReviewService.java:149` 한 곳뿐 |
| 7 | `CONFIRMED` finding | **통과** | 생산자: 규칙 finding `reviewStatus:"CONFIRMED"` 고정. 소비자: `AiCandidateList.vue:29`가 `reviewStatus==='SUGGESTED'`에만 버튼. 화면 버튼 목록이 `이전 \| 다음 \| ACCEPT (위반 확정) \| REJECT (기각)` 뿐이고 규칙 섹션엔 없음(§5 B-6) |
| 8 | `ruleCount` | **통과** | Case A `ruleCount=2`, **Case B `ruleCount=1`**(AI finding 미포함) 실측. 집계는 `InspectionFindingRepository.countRuleFindings`가 `source=RULE`만 센다(`:54-61`) |
| 9 | 엔드포인트 ↔ FE 호출 | **통과 (7/7)** | OpenAPI 경로 7개 = FE 호출 7개. §7 표에 1:1 대응과 실행 확인 지점을 적었다 |

### 이번 회차 추가 지시 항목

| 항목 | 결과 | 근거 |
|---|---|---|
| **D14 불변식** (`decided_by='RULE' AND BLOCKED ⇒ NULL`) | **통과** | DB 전수 검사 10종 전부 0건(§8). 특히 `RULE+BLOCKED & text NOT NULL`=0, **`HUMAN+BLOCKED & text IS NULL`=0**. PATCH 응답이 `submittedText`를 실어 보냄(계약서 §1-7 요청사항 **반영 완료**). ACCEPT 후 SCR-02 상세 패널에 본문 잔존 확인(§5 B-7) |
| **findings 정렬** (RULE 먼저) | **통과** | `InspectionFindingRepository.java:27-36`이 `case f.source when RULE then 0 else 1 end`로 명시 정렬. 실측 `[RULE CONF-CLIENT-01, AI CONF-CLIENT-PROJECT]`. `@Deprecated` 위임 메서드(`:42-46`)는 **본문이 `return findByInspectionIdRuleFirst(inspectionId);` 한 줄**이라 신규 메서드와 결과가 같음이 코드로 보장된다. 화면에서도 규칙 섹션이 AI 섹션보다 위(§5 B-5) |
| **D9 구분** (`hits` 2 vs `matches` 1) | **통과** | Case B `ruleResult.matches` 1건 · `matchedKeyword:"A사"` · finding(RULE) 1건 · 감사 목록 `ruleCount=1`. `hits` 2건(`A사`·`차세대`)은 `AiInspectionRequest`로만 가고 응답·화면에 나오지 않는다(설계대로) |
| **D11 억제 2건** | **통과** | 원시 4건 → finding 2건. **화면에 `SEC-PRIVIP-03`·`PII-EMAIL-04`가 나오지 않음**을 둘 다 확인(§5 A-6·A-7). 두 코드는 `appliedRuleCodes[]`에만 남는다 |
| **D15 발화 버블** | **통과** | S4 버블에 입력 원문(`900101-1234567` 포함) 표시(§5 A-3). SCR-02에서는 규칙 BLOCK 건이 "차단되어 전송 본문이 저장되지 않았습니다"이고 **주민번호·접속 문자열이 어느 경로로도 나오지 않음**(§5 SCR02-4·5) |
| **`domain` → `ai.AiAssessment` 구조적 결합** | **기록** | §9 참조. 결함은 아니나 변경 시 파급 범위가 큰 지점이다 |
| Postman ↔ 실제 응답 | **부분 통과** | **갱신본 재판정**(§1-1). Example 13종 키 경로 대조 **12종 일치·1종 불일치**(F1). `ruleCodes` 정렬·`appliedRuleCodes`·`policySnapshot`·`pollAfterMs`·`createdAt`·`category`·`evidence`·mock 헤더·계정 값은 **전부 해소**. **남은 것은 F1~F5 5건** |
| **PATCH `inspection.submittedText`** (리더 요청 1) | **통과** | §1-2. 키 존재·실제 값·ACCEPT 전/응답/후 3값 동일·화면 잔존까지 확인 |
| **Example `ruleCodes` 정렬** (리더 요청 2) | **통과** | §1-1. BLOCK Example의 `P-PII`·`P-SEC` `ruleCodes`가 실측과 완전 일치 |

---

## 5. 화면 구동 검증 — 45항목 전건 통과

`vite build --mode development` 산출물(= 배포 번들과 같은 코드)을 jsdom에 마운트하고 **실제 백엔드(`localhost:8080`)에 HTTP로 붙여** 사람이 하듯 조작했다. 콘솔에 `[계약 위반]` 로그·JS 에러 **0건**.

| # | 확인 | 결과 |
|---|---|---|
| — | 앱 부팅 · 계정 드롭다운 4명 | 통과 |
| A-1 | D8 캡션 "부서: 개발팀 · 적용 정책 2건 (P-PII, P-SEC)" — API 응답값 | 통과 |
| A-2 | S4 차단 렌더 (통신 오류 아님) | 통과 |
| A-3 | **D15** 발화 버블에 입력 원문 표시 | 통과 |
| A-4 | 판정 카드 "규칙 2건" | 통과 |
| A-5 | `SEC-DBURL-02` · `PII-RRN-01` 2줄 + 의무(사규/법령) + 출처 | 통과 |
| A-6 | **D11** 억제된 `SEC-PRIVIP-03` 미표시 | 통과 |
| A-7 | **D11** 억제된 `PII-EMAIL-04` 미표시 | 통과 |
| A-8 | 차단 시 입력창에 원문 복원 | 통과 |
| A-9 | 정책 스냅샷 "P-PII v3 / P-SEC v7" 표시 | 통과 |
| D-1~5 | S3 마스킹 · `[전화번호]` 라벨 · 원문 번호 미표시 · **D3** `<mark>` 하이라이트 · 입력창 비움 | 통과 |
| C-1~2 | S2 허용 · "규칙 0건" | 통과 |
| B-1 | 영업팀 캡션 "적용 정책 3건" | 통과 |
| B-2 | **stage2** 스피너 "보안 검토 중" + 경과 초 노출 | 통과 |
| B-3 | **stage2** 시점 AI 후보 미표시 | 통과 |
| B-4 | **stage3** COMPLETED 후 AI 후보(`CONF-CLIENT-PROJECT`) 표시 + 근거·출처 "고객사 NDA 목록 v3" | 통과 |
| B-5 | 배지 "제안됨" · 직원 화면에 ACCEPT/REJECT **없음** | 통과 |
| B-6 | SCR-02 상세 4개 섹션 · 규칙 섹션이 AI 섹션보다 위 · 규칙 배지 "확정(규칙)" · AI 후보에만 버튼 | 통과 |
| B-7 | **stage4** ACCEPT → **D14 본문 보존** · 배지 "확정(위반)" · 최종 판정 "차단" · 확정 주체 "담당자" · 확정자 박OO · 버튼 사라짐 | 통과 |
| SCR02-1~3 | 목록 봉투 렌더(20행) · **D2 부서 필터 = 전체/개발팀/영업팀/인사팀**(정보보안팀 없음) · 상태 필터 5종 | 통과 |
| SCR02-4 | 규칙 BLOCK 건 "차단되어 전송 본문이 저장되지 않았습니다" | 통과 |
| SCR02-5 | 규칙 BLOCK 건에 **타인의 원문 미노출** (주민번호·`postgres://` 없음) | 통과 |
| SCR02-6 | 규칙 BLOCK 건에 확정/기각 버튼 없음 | 통과 |

> 부서 필터 검증에서 최초 1건이 FAIL로 나왔으나 **검증 스크립트의 셀렉터 오류**였다(계정 전환 드롭다운을 부서 필터로 잘못 집었다). 실제 부서 필터 옵션은 `["전체","개발팀","영업팀","인사팀"]`으로 D2를 지킨다.

---

## 6. 상태 전이 완전성

쓰기 지점을 **전수 grep**해 정의 ↔ 코드를 양방향으로 대조했다. **죽은 전이 0건 · 무단 전이 0건.**

| 대상 | 정의된 전이 | 코드 위치 | 결과 |
|---|---|---|---|
| `ai_status` | 생성 시 `SKIPPED`/`PENDING` | `InspectionService.java:110` | 통과 |
| | `PENDING → COMPLETED` | `InspectionAiResultSink.java:59` | 통과 (실측 t+2.5s) |
| | `PENDING → FAILED` | `InspectionAiResultSink.java:85` | 통과 (실측) |
| | `SKIPPED` 종단 | 다른 쓰기 지점 없음 | 통과 |
| `message.status` | 생성 시 `ALLOWED`/`MASKED`/`BLOCKED`/`PENDING_REVIEW` | `InspectionService.java:103,199-202` | 통과 |
| | `PENDING_REVIEW → BLOCKED (HUMAN)` | `ReviewService.java:153-155` | **통과** (실측 + 화면) |
| | `PENDING_REVIEW → ALLOWED (HUMAN)` | 같은 곳 | **통과** (REJECT 실측: `ALLOW`/`ALLOWED`/`HUMAN`) |
| | 규칙 확정 3값 종단 | `setStatus` 호출부가 `ReviewService.java:153` **한 곳뿐** | 통과 — 규칙 판정이 뒤집힐 경로가 없다 |
| `review_status` | 규칙 finding `CONFIRMED` 고정 | `InspectionFinding.java:112` | 통과 (DB 전수 0건 위반) |
| | AI finding `SUGGESTED` 생성 | `InspectionFinding.java:88,127` + DB `DEFAULT 'SUGGESTED'` | 통과 |
| | `SUGGESTED → ACCEPTED` / `→ REJECTED` | `ReviewService.java:102` → `InspectionFinding.review(...)` | 통과 (둘 다 실측) |
| | `CONFIRMED`에 PATCH → 409 | `ReviewService.java:94-97` | 통과 (`RULE_FINDING_NOT_REVIEWABLE`, D13) |
| `decided_by` | `null → RULE` (생성 시) | `InspectionService.java:113` | 통과 |
| | `null → HUMAN` | `ReviewService.java:149` — **유일한 쓰기 지점** | 통과 |

**`ai_status=FAILED`일 때 `message.status`가 `PENDING_REVIEW`를 유지**하는지 별도 확인 — 통과. 입력 `A사 차세대 프로젝트 일정 __FAIL__` → 202 → `aiStatus=FAILED`, `status=PENDING_REVIEW`, `finalDecision=PENDING`, `decidedBy=null`, `completedAt` 채워짐, AI finding 미생성. `onFailed`는 `message.status`를 건드리지 않는다(`InspectionAiResultSink.java:78-88`).

**부수 확인 — 리더 브리핑의 전제 1건 정정.** 브리핑은 "`__FAIL__` 단독은 `hits` 무결성 검사가 먼저 걸려 `IllegalStateException`이 난다"였으나, 실측은 **200 `ALLOW`** 다. `__FAIL__` 단독은 어떤 규칙에도 매칭되지 않아 REVIEW 판정이 나지 않고, 따라서 **AI가 아예 호출되지 않는다.** `MockAiInspector`의 1번 분기(`hits` 비었음 → `IllegalStateException`)는 규칙 엔진이 REVIEW 없이 AI를 부르는 버그를 잡는 장치이고, 정상 경로에서는 도달하지 않는 것이 맞다. 실패 경로 데모에는 브리핑대로 `A사 차세대 프로젝트 일정 __FAIL__`을 쓴다.

**잠재 분기 1건 (결함 아님).** `ReviewService.recomputeFinalDecision`(`:132-157`)은 AI finding이 0건이면 `anyAccepted=false, anyPending=false`가 되어 `ALLOW`/`HUMAN`으로 떨어진다. 실제로는 확정 대상 자체가 AI finding이라 도달할 수 없다. 향후 이 메서드를 다른 곳에서 부르게 되면 규칙 판정을 `ALLOW`로 뒤집을 수 있으니 호출부를 늘리지 않는 편이 좋다.

---

## 7. 엔드포인트 ↔ FE 호출 1:1

OpenAPI(`/v3/api-docs`)가 노출하는 경로 7개와 FE 호출이 1:1로 대응하고, **7개 모두 이번 구동 검증에서 실제로 실행됐다.**

| # | 엔드포인트 | FE 호출 | 실행 확인 |
|---|---|---|---|
| 1 | `GET /api/v1/departments` | `api/catalog.js:8` → `stores/session.js:57` | 계정 드롭다운·부서 필터 렌더 |
| 2 | `GET /api/v1/users` | `api/catalog.js:13` → `stores/session.js:57` | 계정 4명 표시 |
| 3 | `GET /api/v1/policies` | `api/catalog.js:20` → `stores/session.js:74` | 캡션 "적용 정책 2건/3건" (계정 전환마다 재조회) |
| 4 | `POST /api/v1/messages` | `api/messages.js:9` → `ChatView.vue:59` | A·B·C·D 4건 |
| 5 | `GET /api/v1/inspections/{id}` | `api/inspections.js:4` → `ChatView.vue:107`(폴링)·`:140`(결과 새로고침), `AuditView.vue:113`(상세) | 폴링 2회 + 상세 패널 2건 |
| 6 | `GET /api/v1/inspections` | `api/inspections.js:13` → `AuditView.vue:89` | 감사 목록 20행 |
| 7 | `PATCH /api/v1/inspections/{id}/findings/{findingId}` | `api/inspections.js:22` → `AuditView.vue:158` | ACCEPT 1건(화면) + REJECT·409·400·404(API) |

`Swagger UI`(`/swagger-ui/index.html`) 200 · `/v3/api-docs` 200.

---

## 8. DB 불변식 전수 검사

검증 종료 시점의 전체 테이블 대상. **10종 전부 0건 위반.**

| # | 불변식 | 위반 |
|---|---|---|
| 1 | `decided_by='RULE' AND BLOCKED ⇒ submitted_text IS NULL` (**D14**) | 0 |
| 2 | `decided_by='HUMAN' AND BLOCKED ⇒ submitted_text IS NOT NULL` (**D14 반대편**) | 0 |
| 3 | `status<>'BLOCKED' ⇒ submitted_text IS NOT NULL` (D7) | 0 |
| 4 | `source='RULE' ⇒ review_status='CONFIRMED'` (D6) | 0 |
| 5 | `source='AI' ⇒ review_status<>'CONFIRMED'` | 0 |
| 6 | `source='AI' ⇒ span·action IS NULL` | 0 |
| 7 | `ai_status='FAILED' ⇒ status='PENDING_REVIEW'` | 0 |
| 8 | `decided_by IS NULL ⇒ status='PENDING_REVIEW'` | 0 |
| 9 | `ai_status='SKIPPED' ⇒ AI finding 없음` | 0 |
| 10 | `source='RULE' ⇒ reviewed_by IS NULL` | 0 |

---

## 9. 구조적 결합 기록 — `domain` → `ai.AiAssessment`

계약서 §2 "대조 결과"가 "저장용 타입을 따로 만들지 않았으므로 AI 스키마와 DB 스키마가 갈릴 여지가 없다"고 적은 지점의 반대편을 기록한다.

```
backend/src/main/java/com/skala/gateway/domain/Inspection.java:3          import com.skala.gateway.ai.AiAssessment;
backend/src/main/java/com/skala/gateway/domain/InspectionFinding.java:3   import com.skala.gateway.ai.AiAssessment;
```

- `inspection.ai_result`(JSONB) ↔ `AiAssessment`, `inspection_finding.evidence`(JSONB) ↔ `List<AiAssessment.Evidence>`가 `@JdbcTypeCode(SqlTypes.JSON)`으로 **직접** 매핑돼 있다. 중간 저장용 타입이 없다.
- **결과:** `AiAssessment`의 레코드 컴포넌트를 바꾸면 **이미 저장된 JSONB 행의 역직렬화 결과가 바뀐다.** 필드 **이름 변경**이 가장 위험하다 — Spring Boot 기본 설정은 `FAIL_ON_UNKNOWN_PROPERTIES=false`라 예외 없이 **조용히 `null`이 되고**, 감사 화면에서 근거·출처가 사라진 채로 정상처럼 보인다.
- 도메인 패키지가 AI 패키지에 의존하는 방향도 통상적 계층 방향과 반대다(`domain` ← `ai`가 아니라 `domain` → `ai`).
- **권고:** 이 결합은 이번 범위에서 바꿀 것이 아니다(계약서가 의도한 선택이다). 다만 `AiAssessment`를 고칠 때는 **DB 마이그레이션이 함께 필요한 변경**으로 취급하고, 필드 이름은 9.4 스키마와 함께만 바꾼다. `LlmAiInspector`를 실제 구현하며 응답 스키마를 손대는 순간이 이 위험의 발현 지점이다.

---

## 10. 제출물 체크리스트 — 검증 가능 항목

| 항목 | 결과 |
|---|---|
| README에 실행 방법·환경변수 목록·데모 케이스 문자열 | **통과** — 실행 3단계, 환경변수 표(백엔드·프런트), 데모 케이스 4종 문자열(A의 `900101-1234567`, B/C의 `A사 차세대`, D의 `010-1234-5678`) 모두 존재 |
| Flyway V1·V2 적용 | **통과** — `flyway_schema_history` 2건, 기동 로그 "Successfully validated 2 migrations", 스키마 8테이블 |
| Postman 컬렉션 export 존재 | **통과** (내용 결함은 F1~F5) |
| Swagger UI 접근 | **통과** — `/swagger-ui/index.html` 200, 7개 경로 노출 |
| 프롬프트 전문·JSON 스키마·Mock 픽스처 3종 | **통과** — `docs/ai-prompt.md`(스키마 §3), `mock/ai/` 3종 |
| 픽스처가 9.4 스키마 검증 통과 | **통과** — 3종 전부. `additionalProperties:false`(최상위 키 정확히 3개), `code` 패턴 `^[A-Z]+-[A-Z-]+$`, `category` enum `CONFIDENTIAL`, `rationale` 10자 이상, `evidence` 항목 `{source,excerpt}`, **금지 필드(`decision`·`action`·`block`·`allow`·`confidence`) 0건** |
| FE·BE 폴더 구조가 11.4와 일치 | **통과 (의도된 추가 있음)** — BE는 계약서 C7대로 `ai/` 9종. FE는 `api/catalog.js`·`lib/` 2개가 11.4에 없으나 `frontend-dev`가 사유를 노트 §1에 기록했다 |
| E2E 케이스 A·B·C·D 결과 기록 | **통과** — `docs/e2e-result.md` 신규 작성 |
| 백엔드 테스트 | **통과** — `./gradlew test --rerun-tasks` **37건 전건 통과**(실패 0·오류 0). 클래스별: `RuleEngineDemoCaseTest` 6 · `DemoCaseApiTest` 6 · `MasterDataApiTest` 10 · `MaskerTest` 4 · `ReviewApiTest` 9 · `InspectionAiResultSinkTest` 2 |

---

## 11. 정리 상태

| 항목 | 상태 |
|---|---|
| **1차 정리 (16:37)** | **완료했다.** DB를 시드 상태로 되돌리고(103/103/54, 시퀀스 103/103/54, 상태 분포 `ALLOWED 56 / MASKED 25 / BLOCKED 14 / PENDING_REVIEW 8`) 백엔드·프런트엔드를 모두 종료했다 |
| **2차 정리 (재판정 후)** | **의도적으로 보류했다 — 아래 사유 참조** |
| 내 흔적 | **제거 완료.** 재판정에서 만든 4행(`message_id 104~107`)과 그 inspection·finding을 삭제했다. **시퀀스는 건드리지 않았다** |
| 백엔드(8080) · 프런트엔드(5173) | **가동 중 — 내가 띄운 것이 아니다.** 1차 정리로 비워 둔 두 포트를 다른 프로세스가 다시 점유했다 |
| 저장소 코드 변경 | **없음.** 오타 수정도 필요하지 않았다. 검증 스크립트·빌드 산출물은 전부 세션 스크래치패드에 두었다 |

### 2차 정리를 보류한 사유

재판정 직후 확인한 결과 **다른 에이전트가 같은 DB·서버에서 지금도 작업 중이다.** 90초 관찰 동안 관측한 것이다.

```
07:41:03  max=143 cnt=127
07:41:18  max=141 cnt=125   ← 142·143 삭제됨 (타 에이전트의 자체 정리)
07:42:49  max=144 cnt=126   ← 144 생성
(직후)    max=145            ← 계속 증가
```

`message_id > 103` 일괄 삭제와 시퀀스 리셋을 지금 실행하면 **다른 에이전트의 진행 중인 검증 데이터를 지운다.** 특히 **시퀀스를 103으로 되돌리면 이미 존재하는 108~145와 PK가 충돌해 그쪽 INSERT가 실패한다.** 서버 종료도 그쪽 실행을 끊는다. 그래서 내 흔적만 지우고 멈췄다.

### 최종 정리 — 마지막에 작업하는 쪽이 실행할 것

동시 작업자가 끝난 것을 확인한 뒤 실행한다. **반드시 서버를 먼저 내리고 DB를 정리한다** (반대 순서면 정리 중에 새 행이 들어온다).

```bash
# 1) 서버 종료
pkill -f "com.skala.gateway.GatewayApplication"; pkill -f "gradle-wrapper.jar bootRun"; pkill -f "node_modules/.bin/vite"
lsof -nP -iTCP:8080 -sTCP:LISTEN; lsof -nP -iTCP:5173 -sTCP:LISTEN   # 둘 다 비어야 한다

# 2) DB 시드 복원
docker exec gateway-pg psql -U gateway -d gateway -c "
BEGIN;
DELETE FROM inspection_finding WHERE finding_id > 54;
DELETE FROM inspection       WHERE inspection_id > 103;
DELETE FROM message          WHERE message_id > 103;
SELECT setval('message_message_id_seq', 103, true);
SELECT setval('inspection_inspection_id_seq', 103, true);
SELECT setval('inspection_finding_finding_id_seq', 54, true);
COMMIT;"

# 3) 확인 — 103 | 103 | 54 이고 상태 분포가 56/25/14/8이어야 한다
docker exec gateway-pg psql -U gateway -d gateway -c "
select (select count(*) from message) m, (select count(*) from inspection) i, (select count(*) from inspection_finding) f;
select status, count(*) from message group by status order by status;"
```

**데모 리허설 직전에도 3)을 한 번 더 실행할 것.**
