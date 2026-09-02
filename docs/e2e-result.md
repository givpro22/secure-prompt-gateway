# E2E 테스트 결과 — 데모 케이스 A · B · C · D

| 항목 | 내용 |
|---|---|
| 출처 | `사내_AI_게이트웨이_기획서_v1.md` 10.4 (데모 케이스), 부록 C #9 (제출물) |
| 실행일 | 2026-09-02 |
| 실행자 | `integration-qa` |
| 기대 판정 기준 | `_workspace/00_input/decisions.md` D1~D15 · `_workspace/01_api-ai-architect_contract-freeze.md`(개정 2) · `docs/demo-script.md` |
| 결과 | **A · B · C · D 전건 통과.** API 레벨·화면 레벨 양쪽에서 기대와 일치 |

## 실행 환경

| 항목 | 값 |
|---|---|
| 백엔드 | Spring Boot 3.5.3 / Java 21, `http://localhost:8080`, 프로파일 `mock` |
| 프런트엔드 | Vue 3 + Vite, `VITE_API_BASE=http://localhost:8080/api/v1` |
| DB | PostgreSQL 16.15 (`jdbc:postgresql://localhost:55432/gateway`), Flyway V1·V2 적용 |
| AI | `MockAiInspector`, `ai.mock.delay-ms=2500`, `ai.mock.fail-keyword=__FAIL__` |
| 폴링 | `gateway.polling.interval-ms=2000` (응답의 `pollAfterMs`), 상한 30회 |

**실행 방식 2종.** ① **API 레벨** — 실제 서버에 HTTP 직접 호출(inspection 104~110). ② **화면 레벨** — `vite build` 산출물(배포 번들과 동일한 코드)을 DOM 환경에 마운트해 **같은 백엔드에 붙여** 사람이 하듯 조작(inspection 111~114). 두 경로의 판정이 일치한다.

**입력 문자열은 기획서 10.4 원본을 한 글자도 바꾸지 않았다.**

---

## 요약

| 케이스 | 계정 | 기대 판정 | 기대 규칙 | 기대 HTTP | 실제 판정 | 실제 규칙 | 실제 HTTP | 화면 | 결과 |
|---|---|---|---|---|---|---|---|---|---|
| **A** | 이OO · 개발팀 (id 1) | BLOCK | `SEC-DBURL-02`, `PII-RRN-01` (2건) | 403 | BLOCK | 동일 2건 | **403** | S4 차단 | **통과** |
| **B** | 김OO · 영업팀 (id 2) | REVIEW → AI 후보 1건 → ACCEPT → BLOCKED | `CONF-CLIENT-01` (1건) | 202 → 200 | 동일 | 동일 | **202 → 200** | S5-a → S5-b → SCR-02 | **통과** |
| **C** | 이OO · 개발팀 (id 1) | ALLOW | 0건 | 200 | ALLOW | 0건 | **200** | S2 허용 | **통과** |
| **D** | 정OO · 인사팀 (id 3) | MASK | `PII-PHONE-03` (1건) | 200 | MASK | 동일 1건 | **200** | S3 마스킹 | **통과** |
| **F** (실패 경로) | 김OO · 영업팀 (id 2) | `ai_status=FAILED`, `status`는 `PENDING_REVIEW` 유지 | `CONF-CLIENT-01` | 202 | 동일 | 동일 | **202** | 자동 검토 실패 안내 | **통과** |

---

## Case A — 규칙이 결정한다 (BLOCK · 403)

**계정** 이OO · 개발팀 (`X-User-Id: 1`)

**입력** (10.4 원본)
```
이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나
```

**HTTP** `403` — 에러 봉투가 아니라 **판정 객체**다(계약서 C2). 본문에 `code` 필드가 없다.

**응답 (발췌)**
```json
{
  "messageId": 104, "inspectionId": 104,
  "decision": "BLOCK", "status": "BLOCKED",
  "submittedText": null,
  "policySnapshot": { "policies": [
    { "policyId": 1, "code": "P-PII", "version": 3, "ruleCodes": ["PII-CARD-02","PII-EMAIL-04","PII-PHONE-03","PII-RRN-01"] },
    { "policyId": 2, "code": "P-SEC", "version": 7, "ruleCodes": ["SEC-AWSKEY-01","SEC-DBURL-02","SEC-PRIVIP-03"] } ] },
  "ruleResult": {
    "matches": [
      { "code": "SEC-DBURL-02", "category": "SECRET", "action": "BLOCK",
        "span": [18, 56], "matchedKeyword": null, "severity": "HIGH",
        "obligation": "INTERNAL", "source": "정보보안규정 4.2" },
      { "code": "PII-RRN-01", "category": "PII", "action": "MASK",
        "span": [73, 87], "matchedKeyword": null, "severity": "HIGH",
        "obligation": "LEGAL", "source": "개인정보보호법 제24조" }
    ],
    "appliedRuleCodes": ["PII-CARD-02","PII-RRN-01","SEC-AWSKEY-01","SEC-DBURL-02","PII-PHONE-03","SEC-PRIVIP-03","PII-EMAIL-04"]
  },
  "aiStatus": "SKIPPED", "decidedBy": "RULE", "pollAfterMs": null
}
```

| 확인 항목 | 기대 | 실제 | 결과 |
|---|---|---|---|
| HTTP | 403 | 403 | 통과 |
| 판정 / 상태 | `BLOCK` / `BLOCKED` | 동일 | 통과 |
| finding 건수 (**D1·D11**) | **2건** (원시 4건 → 중첩 억제 2건) | 2건 | 통과 |
| span 실측값 | `[18,56]` · `[73,87]` | 동일 | 통과 |
| 억제된 규칙 (**D11**) | `SEC-PRIVIP-03`·`PII-EMAIL-04`가 `matches`에 없고 `appliedRuleCodes`에는 있음 | 동일 | 통과 |
| `submittedText` (**D5·D14**) | `null` — 규칙 BLOCK은 `Masker` 미호출 | `null` | 통과 |
| `aiStatus` | `SKIPPED` — AI 미호출 | `SKIPPED` | 통과 |
| `decidedBy` | `RULE` | `RULE` | 통과 |
| 감사 목록 `ruleCount` | 2 | 2 | 통과 |

**화면 (S4 차단)**

| 확인 항목 | 결과 |
|---|---|
| 차단 판정으로 렌더 (통신 오류 아님) | 통과 |
| **D15** 발화 버블에 방금 입력한 원문 표시 (접속 문자열·주민번호 포함) | 통과 |
| 판정 카드 헤더 "규칙 2건" | 통과 |
| `SEC-DBURL-02 · 자격증명 · 차단 · 사규 · 정보보안규정 4.2` | 통과 |
| `PII-RRN-01 · 개인정보 · 마스킹 · 법령 · 개인정보보호법 제24조` | 통과 |
| **D11** 억제된 `SEC-PRIVIP-03` 미표시 | 통과 |
| **D11** 억제된 `PII-EMAIL-04` 미표시 | 통과 |
| 입력창에 원문 복원 | 통과 |
| 정책 스냅샷 "P-PII v3 / P-SEC v7" | 통과 |
| **D8** 캡션 "부서: 개발팀 · 적용 정책 2건 (P-PII, P-SEC)" | 통과 |

---

## Case B — AI는 제안만 한다 (REVIEW · 202 → ACCEPT → BLOCKED)

**계정** 김OO · 영업팀 (`X-User-Id: 2`)

**입력** (10.4 원본)
```
A사 차세대 프로젝트 오픈 일정이 언제였지?
```

**4단계 전부 확인했다.** 중간 단계가 빠지면 비동기 설계가 증명되지 않는다.

### 단계 1 — 202 수신

**HTTP** `202`, **`Location: /api/v1/inspections/105`**

```json
{
  "messageId": 105, "inspectionId": 105,
  "decision": "PENDING", "status": "PENDING_REVIEW",
  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
  "ruleResult": { "matches": [
    { "code": "CONF-CLIENT-01", "category": "CONFIDENTIAL", "action": "REVIEW",
      "span": [0, 2], "matchedKeyword": "A사", "severity": "MEDIUM",
      "obligation": "INTERNAL", "source": "고객사 NDA 목록 v3" } ],
    "appliedRuleCodes": [ … 8건 … ] },
  "aiStatus": "PENDING", "decidedBy": null, "pollAfterMs": 2000
}
```

| 확인 항목 | 기대 | 실제 | 결과 |
|---|---|---|---|
| HTTP + `Location` | 202 + 폴링 URL | 동일 | 통과 |
| `submittedText` (**D7**) | 마스킹본이 채워짐 (`null` 아님) | 채워짐 | 통과 |
| **`aiAssessment` 부재** | 202 본문에 키 자체가 없어야 함 | **최상위 키 11개, `aiAssessment` 없음** | 통과 |
| finding (**D9**) | `matches` 1건 · `matchedKeyword` = 첫 매칭 `"A사"` | 동일 | 통과 |
| `hits` vs `matches` (**D9**) | `hits`는 2건(`A사`·`차세대`)이지만 응답·화면에는 나오지 않음. 감사 목록 `ruleCount`는 **1** | `ruleCount=1` | 통과 |
| `pollAfterMs` | 서버가 지시 (2000) | 2000 | 통과 |

### 단계 2 — 스피너 노출 (PENDING 창)

202 직후 상세를 조회하면 `aiStatus:"PENDING"`, `aiAssessment:null`, `findings`에 RULE 1건만, `finalDecision:"PENDING"`, `completedAt:null`.

| 확인 항목 | 결과 |
|---|---|
| 화면에 **"보안 검토 중 · N초 경과 · 폴링 N회"** 스피너 노출 | 통과 |
| 이 시점에 AI 후보 미표시 | 통과 |
| Mock 지연 2.5초가 실제로 걸림 (최적화되지 않음) | 통과 |

### 단계 3 — COMPLETED

폴링 **2회**(2초 간격) 후 **t+4.07초**에 `aiStatus:"COMPLETED"`.

```json
"aiAssessment": {
  "riskCandidates": [ { "code": "CONF-CLIENT-PROJECT", "category": "CONFIDENTIAL",
    "rationale": "'A사 차세대 프로젝트 오픈 일정'이라는 서술이 계약 상대방과 미공개 일정을 동시에 특정함",
    "evidence": [ { "source": "고객사 NDA 목록 v3", "excerpt": "A사 — 비밀유지 2027.03까지, 일정·범위 포함" } ] } ],
  "missingContext": ["해당 일정이 대외 공개된 정보인지 확인 필요"],
  "reviewRequired": true
}
"findings": [ {source:"RULE", code:"CONF-CLIENT-01", reviewStatus:"CONFIRMED"},
              {source:"AI",   code:"CONF-CLIENT-PROJECT", reviewStatus:"SUGGESTED"} ]
```

| 확인 항목 | 기대 | 실제 | 결과 |
|---|---|---|---|
| `findings` 정렬 | **RULE이 먼저, AI가 나중** | 동일 (API·화면 양쪽) | 통과 |
| AI finding | `spanStart/End`·`action` 모두 `null`, `rationale`·`evidence` 존재 | 동일 | 통과 |
| `reviewStatus` | AI = `SUGGESTED`, RULE = `CONFIRMED` | 동일 | 통과 |
| `status` / `finalDecision` / `decidedBy` | `PENDING_REVIEW` / `PENDING` / `null` — **AI는 판정을 옮기지 않는다** | 동일 | 통과 |
| `aiAssessment` 스키마 | `decision`·`action`·`block`·`allow`·`confidence` **부재** | 부재 | 통과 |
| 폴링 종료 (**D12**) | `aiStatus !== 'PENDING'`이면 정지, 추가 요청 없음 | 정지 | 통과 |
| 화면 — AI 후보 카드 + 근거 + 출처 "고객사 NDA 목록 v3" + 배지 "제안됨" | | 표시 | 통과 |
| 화면 — **직원 화면에 ACCEPT/REJECT 없음** | | 없음 | 통과 |

### 단계 4 — SCR-02에서 ACCEPT → BLOCKED / HUMAN

`PATCH /api/v1/inspections/105/findings/58` (`X-User-Id: 4` 박OO) → **HTTP 200**

```json
{
  "findingId": 58, "reviewStatus": "ACCEPTED",
  "reviewedBy": { "userId": 4, "name": "박OO" },
  "reviewedAt": "2026-09-02T07:20:39.649738Z",
  "inspection": { "inspectionId": 105, "finalDecision": "BLOCK", "decidedBy": "HUMAN",
                  "status": "BLOCKED",
                  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?" }
}
```

| 확인 항목 | 기대 | 실제 | 결과 |
|---|---|---|---|
| `finalDecision` / `status` / `decidedBy` | `BLOCK` / `BLOCKED` / **`HUMAN`** | 동일 | 통과 |
| **`submittedText` 보존 (D14)** | 사람 확정 BLOCK은 본문을 지우지 않는다 | **보존됨.** ACCEPT **전** `GET` / **PATCH 응답** / ACCEPT **후** `GET` 세 값이 모두 `"A사 차세대 프로젝트 오픈 일정이 언제였지?"`로 동일하고, 키(`'submittedText' in inspection`)도 존재한다. DB `message.submitted_text`도 변경 없음 | 통과 |
| `aiStatus` | `COMPLETED` 유지 (사람 확정은 AI 검사 상태가 아니다) | 유지 | 통과 |
| `completedAt` | 사람 확정 시각으로 갱신 | 갱신됨 | 통과 |
| 화면 — 배지 "확정(위반)", 최종 판정 "차단", 확정 주체 "담당자", 확정자 박OO | | 표시 | 통과 |
| 화면 — **ACCEPT 후에도 상세 패널 본문 잔존 (D14)** | | 잔존 | 통과 |
| 화면 — 확정 후 버튼 사라짐 | | 사라짐 | 통과 |

**기각 경로도 확인** — 다른 건에서 `REJECTED` → `finalDecision:"ALLOW"`, `status:"ALLOWED"`, `decidedBy:"HUMAN"`, `submittedText` 보존.

**에러 경로**

| 요청 | 기대 | 실제 |
|---|---|---|
| 같은 finding에 재요청 | 409 `FINDING_ALREADY_REVIEWED` | 동일 |
| 규칙 finding에 PATCH (**D13**) | 409 `RULE_FINDING_NOT_REVIEWABLE` | 동일 |
| `reviewStatus:"CONFIRMED"` (SUGGESTED 대상) | 400 `INVALID_REQUEST` | 동일 |
| 없는 inspection / 없는 finding | 404 `INSPECTION_NOT_FOUND` / `FINDING_NOT_FOUND` | 동일 |

---

## Case C — 같은 문장, 다른 결과 (ALLOW · 200)

**계정** 이OO · 개발팀 (`X-User-Id: 1`) — **입력은 Case B와 완전히 같은 문장이다.**

```
A사 차세대 프로젝트 오픈 일정이 언제였지?
```

**HTTP** `200`

```json
{ "messageId": 106, "inspectionId": 106,
  "decision": "ALLOW", "status": "ALLOWED",
  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
  "ruleResult": { "matches": [],
    "appliedRuleCodes": ["PII-CARD-02","PII-RRN-01","SEC-AWSKEY-01","SEC-DBURL-02","PII-PHONE-03","SEC-PRIVIP-03","PII-EMAIL-04"] },
  "aiStatus": "SKIPPED", "decidedBy": "RULE" }
```

| 확인 항목 | 기대 | 실제 | 결과 |
|---|---|---|---|
| HTTP / 판정 | 200 / `ALLOW` | 동일 | 통과 |
| finding | 0건 | `matches: []` | 통과 |
| `appliedRuleCodes` | **`CONF-CLIENT-01`이 없다** — 개발팀에 P-CONF가 매핑되지 않음 | 7건, `CONF-CLIENT-01` 없음 | 통과 |
| `submittedText` | 원문 그대로 | 동일 | 통과 |
| 화면 | "허용 · 규칙 0건 · 전송됨" | 동일 | 통과 |

**부서별 N:M 설계의 증명** — 같은 문자열이 영업팀에서는 202 REVIEW, 개발팀에서는 200 ALLOW다. `GET /policies?deptId=1` → 2건(P-PII, P-SEC), `?deptId=2` → 3건(+P-CONF)이 그 근거이며, 화면 캡션도 "적용 정책 2건" / "3건"으로 갈린다.

---

## Case D — 마스킹 (MASK · 200)

**계정** 정OO · 인사팀 (`X-User-Id: 3`)

```
지원자 연락처 010-1234-5678 로 면접 안내 문자 초안 써줘
```

**HTTP** `200`

```json
{ "messageId": 107, "inspectionId": 107,
  "decision": "MASK", "status": "MASKED",
  "submittedText": "지원자 연락처 [전화번호] 로 면접 안내 문자 초안 써줘",
  "ruleResult": { "matches": [
    { "code": "PII-PHONE-03", "category": "PII", "action": "MASK",
      "span": [8, 21], "matchedKeyword": null, "severity": "MEDIUM",
      "obligation": "LEGAL", "source": "개인정보보호법" } ] },
  "aiStatus": "SKIPPED", "decidedBy": "RULE" }
```

| 확인 항목 | 기대 | 실제 | 결과 |
|---|---|---|---|
| HTTP / 판정 | 200 / `MASK` | 동일 | 통과 |
| `submittedText` | 원문 번호가 `[전화번호]`로 치환 | 동일 | 통과 |
| 화면 — `<mark>[전화번호]</mark>` 하이라이트 (**D3** 라벨 문자열 검색, 오프셋 산술 없음) | | 동일 | 통과 |
| 화면 — 원문 번호 `010-1234-5678` 미표시 | | 미표시 | 통과 |
| 화면 — 마스킹 후 입력창 비움 | | 비움 | 통과 |

---

## 실패 경로 — `ai_status = FAILED`

**입력** `A사 차세대 프로젝트 일정 __FAIL__` (`X-User-Id: 2`)

REVIEW 키워드(`A사`)를 함께 넣어야 AI가 호출된다. `MockAiInspector`의 평가 순서가 `hits` 무결성 검사 → fail-keyword → 지연이기 때문이다(계약서 §5-3).

**HTTP** `202` → **t+0.53초**에 `aiStatus:"FAILED"`

| 확인 항목 | 기대 | 실제 | 결과 |
|---|---|---|---|
| `aiStatus` | `FAILED` | `FAILED` | 통과 |
| **`message.status`** | **`PENDING_REVIEW` 유지** — `ALLOWED`로 떨어지면 검사되지 않은 프롬프트가 통과 기록으로 남는다 | `PENDING_REVIEW` | 통과 |
| `finalDecision` / `decidedBy` | `PENDING` / `null` | 동일 | 통과 |
| `aiAssessment` | `null` | `null` | 통과 |
| AI finding | 생성하지 않음 (규칙 finding만) | RULE 1건뿐 | 통과 |
| `completedAt` | 실패 시각으로 채움 | 채워짐 | 통과 |
| 화면 | "자동 검토 실패 — 담당자 확인 중" | 표시 | 통과 |

**참고** — `__FAIL__`만 단독으로 보내면 어떤 규칙에도 매칭되지 않아 REVIEW 판정이 나지 않고, **AI가 아예 호출되지 않아 200 `ALLOW`**가 된다. 실패 경로를 재현하려면 반드시 REVIEW 키워드를 함께 넣는다.

---

## 부수 검증

| 항목 | 결과 |
|---|---|
| `GET /departments` · `/users` · `/policies` · `/inspections` 목록 봉투 `{items,page,size,total}` | 4종 전부 통과 |
| `GET /policies` `deptId` 누락 → 400 `INVALID_PARAMETER` | 통과 |
| `GET /policies` 응답에 `pattern` 미노출 | 통과 — 응답 전문에 `"pattern"` 0회 |
| `GET /inspections/{id}` 응답에 `originalText` 미노출 | 통과 — 키 자체가 없음 |
| `X-User-Id` 누락 → 400 `MISSING_USER_HEADER` / 비숫자·미존재 → 400 `INVALID_USER` | 통과 |
| 빈 `text` → 400 `INVALID_REQUEST` | 통과 |
| `?size=500` → `size=100`으로 절삭 | 통과 |
| 감사 목록 정렬 `createdAt DESC` 고정 | 통과 |
| 부서·상태 필터 | 통과 (`deptId=2` / `status=BLOCKED`) |
| **D2** 감사 콘솔 부서 필터에 정보보안팀 없음 (전체/개발팀/영업팀/인사팀) | 통과 |
| **D6** 규칙 finding에 ACCEPT/REJECT 버튼 없음, 배지 "확정(규칙)" | 통과 |
| **D15** SCR-02에 타인의 원문 미노출 — 규칙 BLOCK 건은 "차단되어 전송 본문이 저장되지 않았습니다" | 통과 |
| Swagger UI (`/swagger-ui/index.html`) · OpenAPI 7개 경로 | 통과 |
| 백엔드 테스트 `./gradlew test --rerun-tasks` | **37건 전건 통과** (실패 0 · 오류 0) |
| DB 불변식 10종 (D5·D6·D7·D14 포함) | **전건 0 위반** |
| Postman 컬렉션 Example 13종 ↔ 실측 응답 (키 경로 + 값 대조) | 키 경로 **12종 일치 · 1종 불일치**. 남은 문서 결함 5건은 리포트 §1 참조 |

---

## 판정

**데모 케이스 A · B · C · D 전건 통과.** 실패 경로까지 포함해 기획서 10.4·15.2의 기대 결과와 어긋나는 항목이 없다.

발표 대사에 직접 걸려 있는 세 지점을 특히 확인했다.

1. **0:20 "규칙 두 건이 걸렸습니다"** — 원시 4건 → 억제 2건, 화면에도 2줄만 나온다.
2. **2:18 "차단으로 바뀌었는데 검토한 본문은 그대로 남아 있습니다"** — ACCEPT 후 `submittedText`가 보존되고 상세 패널에 그대로 있다.
3. **2:46 "완전히 같은 문장입니다. 그런데 통과했습니다"** — Case B(202)와 Case C(200)가 같은 문자열로 갈린다.

미해소 항목은 `_workspace/03_integration-qa_report.md` §1(실패 7건, 전부 데모 비차단)과 §2(환경 이슈)에 있다.
