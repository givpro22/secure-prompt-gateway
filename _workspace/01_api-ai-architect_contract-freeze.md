# Interface Freeze — 계약 확정본 v1

**주관:** `api-ai-architect`
**확정일:** 2026-09-02
**근거:** `사내_AI_게이트웨이_기획서_v1.md` 8장·9장·4장, `_workspace/00_input/decisions.md`(D1~D6)
**상태:** 확정. 이 시점부터 필드명 변경 요청은 거부하고 `spec-steward`에게 에스컬레이션한다.

이 문서는 SSOT 계층 2단이다. 기획서(1단)와 다르게 정한 지점은 §0에 전부 모아 두었고, 코드(3단)가 이 문서와 어긋나면 코드가 틀린 것이다.

---

## 0. 기획서와 다르게 정한 지점 (전체 목록)

계약이 기획서와 다른 곳은 아래 8건이 전부다. 나머지는 기획서 그대로다.

| # | 지점 | 기획서 | 계약 | 사유 |
|---|---|---|---|---|
| C1 | 목록 응답 봉투 | 8.1 "목록은 `{items, page, size, total}`" | **전 목록 엔드포인트에 동일 적용.** 비페이징(`/departments`, `/users`, `/policies`)은 `page=0`, `size=total=items.length` | 8.1을 문자 그대로 지킨 것. FE의 목록 접근 코드가 `res.data.items` 하나로 통일된다 |
| C2 | 403(BLOCK) 응답 형식 | 8.1 "에러 형식 `{code, message, details}`" / 8.4 403 예시는 판정 객체 | **403은 판정 객체를 반환한다**(8.4 그대로). 에러 봉투는 400/404/409에만 적용 | BLOCK은 처리 실패가 아니라 정상 수행된 판정이다. FE는 차단 사유(규칙 코드·출처)를 S4 화면에 표시해야 하므로 `{code,message}`만으로는 화면을 못 그린다. **FE는 403에서 `code` 필드를 찾지 말 것** |
| C3 | null 직렬화 | `application.yml`이 `default-property-inclusion: non_null` | **`always`로 변경**(application.yml 수정함) | 8.4가 `"submittedText": null`, `"decidedBy": null`, `"reviewedAt": null`을 명시적으로 싣는다. non_null이면 이 필드들이 응답에서 사라져 FE가 "필드 없음"과 "null"을 구분하는 옵셔널 체이닝 방어 코드를 쓰게 된다 (spec-contract "FE가 방어 코드로 덮음 — 가장 위험하다") |
| C4 | `POST /messages` 202의 `submittedText` | 8.4에서 같은 inspection 2090이 202에서는 `null`, GET에서는 텍스트 존재 (기획서 내부 모순) | **202도 마스킹 적용본을 반환한다** (매칭된 MASK 규칙이 없으면 원문과 동일). `null`은 **규칙 BLOCK 전용** | **리더 결정 D7·D14.** 감사 담당자가 검토해야 할 바로 그 건의 본문이 비면 SCR-02 상세 패널이 무용지물이고, AI에 넘기는 `maskedText`와 같은 값이라 따로 감출 이유가 없다. **8.4의 202 예시에 있는 `"submittedText": null`은 기획서 오류다.** 6.2의 "NULL이면 미전송"은 **"마스킹본이 생성된 적 없음"**의 의미이며, 그것이 발생하는 경로는 `Masker`를 아예 호출하지 않는 규칙 BLOCK 하나뿐이다 (D5·D14). 사람이 확정한 BLOCK은 본문을 보존한다 — §1-7 |
| C4-1 | REVIEW 시 `hits[]`와 `matches[]`의 건수 | 8.4 202는 매치 1건, 9.3 조립 예시는 참조 근거 2건 (기획서 내부 모순) | **finding·`matches[]`는 규칙당 1건, `hits[]`는 키워드당 1건, `matchedKeyword`는 규칙당 첫 매칭** | **리더 결정 D9.** Case B 문자열은 `A사`(offset 0)와 `차세대`(offset 3)가 둘 다 CONF-CLIENT-01에 매칭된다. 감사 목록의 "규칙 수"는 1이다. 8.4 202 예시와 9.3 조립 예시가 이미 이 구조다 |
| C4-2 | 8.4 BLOCK 예시의 요청 문자열과 span | 요청 예시는 10.4 Case A의 축약본이고, span `[12,52]`·`[62,76]`은 그 축약본으로도 실제와 맞지 않는다 | **Postman Example의 요청 본문을 10.4 Case A 문자열로 바꾸고 span을 실측값 `[18,56]`·`[73,87]`로 정정한다** | `spec-steward` OQ-08 권고를 채택했다. 8.6은 "Example 본문은 8.4의 JSON을 그대로 사용"이라고 하지만, **요청 문자열과 span이 서로 맞지 않는 Example은 그대로 쓰면 `integration-qa`의 기대값이 틀어진다.** D3에 따라 FE는 span을 쓰지 않으므로 화면 영향은 없다. 실측 근거는 §6-1 |
| C5 | `GET /policies` 응답의 `pattern` | 6.2 `policy_rule.pattern` 존재 | **응답에 포함하지 않는다** | 탐지 정규식이 클라이언트에 노출되면 우회 입력을 만들 수 있다. SCR-01 정책 패널에 필요한 것은 code·description·action·severity·source뿐이다 |
| C6 | `GET /policies`의 `deptId` | 8.3 `?deptId=` (필수 여부 미기재) | **필수.** 누락 시 400 | 부서에 적용되는 정책을 반환하는 엔드포인트다. 생략 시 GLOBAL만 줄지 전체를 줄지가 모호하고, 모호한 기본값은 화면마다 다른 결과를 만든다 |
| C7 | `ai/` 패키지 클래스 수 | 11.4에 5개(`AiInspector`, `AiInspectionRequest`, `AiAssessment`, `MockAiInspector`, `LlmAiInspector`) | **4개 추가** — `KeywordHit`, `AiResultSink`, `AiInspectionRunner`, `PromptAssembler` | `KeywordHit`은 9.1이 이미 요구한 타입. 나머지 3개는 §5 인계 지점과 9.3 조립 기준을 코드로 고정하기 위한 것. 상세 사유는 §5·§6 |
| C8 | `X-User-Id` 누락/무효 | 8.2 상태 코드 표에 없음 | **400** (`MISSING_USER_HEADER` / `INVALID_USER`) | 401은 인증 체계가 있다는 뜻인데 이번 범위에 인증이 없다(0.3). 404는 8.2에서 "존재하지 않는 inspection·finding"으로 용도가 고정돼 있다. 남는 것은 400이다 |

### `data-architect` 산출물 대조 상태

**대조 완료.** `_workspace/01_data-architect_names.md`와 `docs/erd.dbml`을 §2·§3과 대조했다. 결과는 §2 말미의 "대조 결과"에 있다. 요약하면 **8개 테이블 컬럼명·enum 값 전건 일치**이며, 조정한 것은 `policySnapshot`의 항목 shape 1건뿐이다(C9).

| # | 지점 | 계약 v1 초안 | 확정 | 사유 |
|---|---|---|---|---|
| C9 | `policySnapshot.policies[]` 항목 | `{code, version}` (8.4 예시) | **`{policyId, code, version, ruleCodes[]}`** — DB에 저장된 그대로 반환한다 | 기획서 7.4-3이 스냅샷에 `{policyId, version, ruleCodes[]}`를 기록하라고 지시하고 `data-architect`가 그렇게 저장한다. API가 `{code, version}`으로 투영하면 **판정 시점에 어떤 규칙이 활성이었는지를 감사 화면에서 볼 수 없게 된다** — 스냅샷의 존재 이유가 시점 보존이므로 감추지 않는다. FE는 `code`·`version`만 읽으면 되고, 8.4 예시는 축약으로 본다 |

---

## 1. 표 1 — 엔드포인트 계약 (7개)

공통 규약(8.1)은 그대로 따른다. Base path `/api/v1`, JSON camelCase, 시각 ISO 8601 UTC.

### 상태 코드 정책 (8.2 + D4)

| 코드 | 사용 상황 | 응답 형식 |
|---|---|---|
| 200 | 조회 성공, ALLOW/MASK 판정, PATCH 성공 | 리소스 객체 또는 목록 봉투 |
| **201** | **사용하지 않음 (D4)** | — |
| 202 | REVIEW 판정. `Location` 헤더에 폴링 URL | 판정 객체 (`pollAfterMs` 포함) |
| 400 | 본문 누락·빈 문자열, `X-User-Id` 누락·무효, 쿼리 파라미터 형식 오류 | 에러 봉투 |
| 403 | BLOCK 판정 | **판정 객체** (에러 봉투 아님 — C2) |
| 404 | 존재하지 않는 inspection·finding | 에러 봉투 |
| 409 | 이미 처리된 finding에 재요청, CONFIRMED finding에 PATCH | 에러 봉투 |

**201 미사용 사유 (D4, 8.2, 16장):** `message` 리소스는 실제로 생성되지만 클라이언트가 받아야 할 주 정보는 "생성 사실"이 아니라 "판정 결과"다. 201 + `Location`으로 응답하면 판정을 알기 위해 클라이언트가 한 번 더 요청해야 하고, BLOCK(전송 거부)을 201로 표현할 방법이 없다. 판정에 따라 200 / 202 / 403으로 갈리는 설계가 각 상태 코드에 고유한 의미를 부여한다.

### 에러 봉투

```json
{ "code": "FINDING_ALREADY_REVIEWED", "message": "finding 502 is already ACCEPTED", "details": null }
```

| code | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 요청 본문 누락, `text`가 빈 문자열/공백 |
| `MISSING_USER_HEADER` | 400 | `X-User-Id` 헤더 없음 |
| `INVALID_USER` | 400 | `X-User-Id`가 숫자가 아니거나 존재하지 않는 사용자 |
| `INVALID_PARAMETER` | 400 | `deptId` 비숫자, `status` enum 외 값, `page`/`size` 음수 |
| `INSPECTION_NOT_FOUND` | 404 | 해당 id의 inspection 없음 |
| `FINDING_NOT_FOUND` | 404 | finding 없음, 또는 경로의 inspection에 속하지 않음 |
| `FINDING_ALREADY_REVIEWED` | 409 | `reviewStatus`가 이미 ACCEPTED 또는 REJECTED |
| `RULE_FINDING_NOT_REVIEWABLE` | 409 | `reviewStatus`가 CONFIRMED (규칙 finding). 규칙 판정은 사람이 번복하지 않는다(4장) |

### 1-1. `GET /api/v1/departments`

| 항목 | 값 |
|---|---|
| 쿼리 | 없음 |
| `X-User-Id` | 불필요 |
| 200 | `{ "items": [ { "deptId": 1, "code": "DEV", "name": "개발팀" } ], "page": 0, "size": 4, "total": 4 }` |

**INFOSEC은 응답에 포함된다.** 계정 전환 드롭다운에 박OO(정보보안팀, SECURITY_ADMIN)이 필요하기 때문이다. 감사 콘솔 부서 필터에서 INFOSEC을 빼는 것(D2, 10.2)은 **FE의 표시 결정**이며 API는 마스터 4행을 그대로 반환한다. `frontend-dev`는 `AuditView`의 부서 필터에서 `code === 'INFOSEC'`을 제외할 것.

### 1-2. `GET /api/v1/users`

| 항목 | 값 |
|---|---|
| 쿼리 | `deptId` (선택, 생략 시 전체) |
| `X-User-Id` | 불필요 |
| 200 | `{ "items": [ { "userId": 2, "name": "김OO", "email": "…@example.com", "role": "EMPLOYEE", "department": { "deptId": 2, "code": "SALES", "name": "영업팀" } } ], "page": 0, "size": 4, "total": 4 }` |
| 400 | `INVALID_PARAMETER` — `deptId` 비숫자 |

`items[].department`는 중첩 객체다. 평탄화(`deptCode`)하지 않는다 — 헤더의 계정 전환 드롭다운이 "김OO (영업팀)"을 그리는 데 name이 필요하다.

### 1-3. `GET /api/v1/policies`

| 항목 | 값 |
|---|---|
| 쿼리 | `deptId` (**필수** — C6) |
| `X-User-Id` | 불필요 |
| 400 | `INVALID_PARAMETER` — `deptId` 누락 또는 비숫자 |

200 응답:

```json
{
  "items": [
    {
      "policyId": 3, "code": "P-CONF", "name": "고객사 프로젝트 정보 통제",
      "category": "CONFIDENTIAL", "version": 2, "scope": "DEPT", "appliedVia": "DEPT",
      "rules": [
        { "ruleId": 8, "code": "CONF-CLIENT-01", "ruleType": "KEYWORD", "action": "REVIEW",
          "maskLabel": null, "severity": "MEDIUM", "obligation": "INTERNAL",
          "source": "고객사 NDA 목록 v3", "description": "고객사명·프로젝트명 언급 시 검토" }
      ]
    }
  ],
  "page": 0, "size": 3, "total": 3
}
```

- `appliedVia`: `GLOBAL`(scope=GLOBAL이라 전사 적용) 또는 `DEPT`(department_policy 매핑으로 적용). 7.3 매트릭스의 "○ (GLOBAL)" / "○ (매핑)"을 그대로 필드화한 것이다.
- `ownerDept`: 정책을 **만든** 부서명. `appliedVia`가 답하는 "어떻게 적용됐나"와 다른 질문인 "누가 정했나"에 답한다 (0.5 D19). 소유 부서가 없으면 `null`.
- `is_active=false`인 정책·규칙은 응답에 포함하지 않는다.
- `rules[].pattern`은 **없다** (C5).

### 1-4. `POST /api/v1/messages`

| 항목 | 값 |
|---|---|
| 헤더 | `X-User-Id` **필수** |
| 요청 | `{ "text": "…" }` |
| 상태 | 200 (ALLOW/MASK) / 202 (REVIEW) / 403 (BLOCK) / 400 |

**응답 필드 집합은 4개 상태에서 동일하다.** 상태별로 값만 달라진다. FE가 상태 코드에 따라 다른 파서를 쓰지 않아도 된다.

| 필드 | 타입 | ALLOW(200) | MASK(200) | BLOCK(403) | REVIEW(202) |
|---|---|---|---|---|---|
| `messageId` | number | ○ | ○ | ○ | ○ |
| `inspectionId` | number | ○ | ○ | ○ | ○ |
| `decision` | enum | `ALLOW` | `MASK` | `BLOCK` | `PENDING` |
| `status` | enum | `ALLOWED` | `MASKED` | `BLOCKED` | `PENDING_REVIEW` |
| `submittedText` | string \| null | 원문 | 마스킹본 | `null` | **마스킹본** (C4) |
| `policySnapshot` | object | ○ | ○ | ○ | ○ |
| `ruleResult` | object | `matches: []` | ○ | ○ | ○ |
| `aiStatus` | enum | `SKIPPED` | `SKIPPED` | `SKIPPED` | `PENDING` |
| `decidedBy` | enum \| null | `RULE` | `RULE` | `RULE` | `null` |
| `pollAfterMs` | number \| null | `null` | `null` | `null` | `2000` |
| `createdAt` | ISO8601 | ○ | ○ | ○ | ○ |

`submittedText`의 `null`은 이 표에서 BLOCK(403) 한 칸뿐이며, `POST /messages`의 BLOCK은 언제나 **규칙 판정**이다(사람은 이 시점에 개입하지 않는다). 따라서 이 칸은 D14의 "규칙 BLOCK ⇒ NULL"과 같은 말이다. 사람 확정으로 BLOCKED가 된 건은 이 응답이 아니라 `PATCH`(§1-7)와 `GET /inspections/{id}`(§1-5)에서 나타나며 **본문을 보존한다.**

- 202에는 `Location: /api/v1/inspections/{inspectionId}` 헤더가 붙는다.
- **202 응답에 존재하지 않는 것:** `aiAssessment`, AI findings, `completedAt`. FE가 202 시점에 `aiAssessment`를 참조하면 크래시한다. AI 결과는 `GET /inspections/{id}` 폴링으로만 얻는다.
- `pollAfterMs`는 서버가 폴링 간격을 지시하는 값이다. FE는 이 값을 쓰고 자체 상수를 쓰지 않는다. 출처는 `gateway.polling.interval-ms`.
- `policySnapshot`: `{ "policies": [ { "policyId": 1, "code": "P-PII", "version": 4, "ruleCodes": ["PII-RRN-01", "…"] } ] }` — DB에 저장된 스냅샷 그대로다(C9). FE는 `code`·`version`만 읽는다.
- `ruleResult`: `{ "matches": [ … ], "appliedRuleCodes": [ … ] }` — 상세 shape은 §5 인계 2.
- `matches[]`와 `appliedRuleCodes[]`는 다르다. 적용된 규칙(로드된 전부)과 매칭된 규칙(finding이 생성된 것)의 차이이며, D1 중첩 억제로 매칭됐으나 finding이 없는 규칙은 `appliedRuleCodes`에만 남는다.

### 1-5. `GET /api/v1/inspections/{id}`

| 항목 | 값 |
|---|---|
| `X-User-Id` | 불필요 |
| 200 / 404 | 404는 `INSPECTION_NOT_FOUND` |

응답 필드:

```
inspectionId, messageId, phase, user{userId,name,department}, submittedText, status,
policySnapshot, ruleResult, aiStatus, aiAssessment, findings[], finalDecision, decidedBy,
createdAt, completedAt
```

- `user.department`는 **부서명 문자열**이다(`"영업팀"`). 8.4 예시 그대로. 객체가 아니다.
- `submittedText`는 `message.submitted_text`를 그대로 반환한다. `POST /messages` 응답과 **같은 값**이다(C4). `null`인 것은 **`decidedBy='RULE'`인 BLOCKED뿐**이며(마스킹을 실행하지 않았으므로 대상 텍스트 자체가 없다 — D5·D14), `decidedBy='HUMAN'`인 BLOCKED는 **마스킹본을 보존한다.** PENDING_REVIEW·MASKED에도 마스킹 적용본이, ALLOWED에는 원문이 들어 있다.

- **원문(`original_text`)은 어떤 상태에서도 응답에 넣지 않는다** (6.2 "원문. 화면 미노출").

`submittedText`의 상태별 값 (D7·D14) — `decidedBy`까지 봐야 결정된다:

| `status` | `decidedBy` | `submittedText` |
|---|---|---|
| `ALLOWED` | `RULE` | 원문 |
| `MASKED` | `RULE` | 마스킹본 |
| `BLOCKED` | `RULE` | **`null`** (마스킹 미실행) |
| `BLOCKED` | `HUMAN` | **마스킹본 보존** |
| `ALLOWED` | `HUMAN` | 마스킹본 |
| `PENDING_REVIEW` | `null` | 마스킹본 |

**FE·QA 주의:** 분기 조건을 `status === 'BLOCKED'`로 쓰면 사람이 확정한 건에서 본문이 있는데도 없는 것처럼 그린다. 상세 패널은 `submittedText`의 null 여부만 보고 판단할 것. `integration-qa`의 불변식도 `BLOCKED ⇒ NULL`이 아니라 `decidedBy='RULE' AND BLOCKED ⇒ NULL`이다.

`aiStatus`별 차이 — 이 표가 폴링 FE의 분기 근거다:

| `aiStatus` | `aiAssessment` | `findings[]` | `finalDecision` | `completedAt` |
|---|---|---|---|---|
| `SKIPPED` | `null` | RULE만 | ALLOW/MASK/BLOCK | 판정 시각 |
| `PENDING` | `null` | RULE만 | `PENDING` | `null` |
| `COMPLETED` | 객체 | RULE + AI | `PENDING` (사람 확정 전) | AI 완료 시각 |
| `FAILED` | `null` | RULE만 | `PENDING` | 실패 시각 |

`FAILED`일 때 `status`는 **`PENDING_REVIEW`를 유지한다.** `ALLOWED`로 떨어뜨리면 검사되지 않은 프롬프트가 통과 기록으로 남는다 (9.5, UC-03 예외).

`findings[]` 항목:

| 필드 | RULE finding | AI finding |
|---|---|---|
| `findingId` | ○ | ○ |
| `source` | `"RULE"` | `"AI"` |
| `code` | 규칙 코드 (`CONF-CLIENT-01`) | AI 후보 코드 (`CONF-CLIENT-PROJECT`) |
| `category` | ○ | ○ |
| `spanStart` / `spanEnd` | 원문 기준 오프셋 | `null` |
| `action` | `MASK`/`BLOCK`/`REVIEW` | `null` |
| `rationale` | `null` | ○ |
| `evidence` | `null` | `[{source, excerpt}]` |
| `reviewStatus` | **`CONFIRMED` 고정** | `SUGGESTED` → `ACCEPTED`/`REJECTED` |
| `reviewedBy` | `null` | `{userId, name}` 또는 `null` |
| `reviewedAt` | `null` | ISO8601 또는 `null` |

`spanStart`/`spanEnd`는 **원문 기준**이다. FE는 이 값으로 `submittedText`를 자르지 않는다 — 마스킹이 길이를 바꾸므로 오프셋이 밀린다. 하이라이트는 `submittedText`에서 `maskLabel` 문자열을 검색해 처리한다 (D3).

### 1-6. `GET /api/v1/inspections`

| 쿼리 | 기본값 | 비고 |
|---|---|---|
| `deptId` | 없음(전체) | |
| `status` | 없음(전체) | `message.status` 4값 |
| `from` / `to` | 없음 | ISO 8601. `createdAt` 기준, `from` 이상 `to` 미만 |
| `page` | `0` | **0부터** |
| `size` | `20` | 최대 100. 초과 시 100으로 절삭 |

200: `{ "items": [ { "inspectionId", "createdAt", "department", "userName", "status", "ruleCount", "aiStatus", "decidedBy" } ], "page": 0, "size": 20, "total": 137 }`

- `department`는 부서명 문자열, `userName`은 사용자명 문자열이다. 목록 행에 필요한 것이 그것뿐이다.
- `ruleCount`는 `source='RULE'`인 finding 개수다. D1 중첩 억제 후의 값이므로 Case A에서 **2**다.
- 정렬은 `createdAt DESC` 고정. 정렬 파라미터를 두지 않는다.

### 1-7. `PATCH /api/v1/inspections/{id}/findings/{findingId}`

| 항목 | 값 |
|---|---|
| 헤더 | `X-User-Id` **필수** — `reviewed_by`에 기록된다 |
| 요청 | `{ "reviewStatus": "ACCEPTED", "comment": "…" }` |
| 상태 | 200 / 400 / 404 / 409 |

- 요청의 `reviewStatus`는 **`ACCEPTED` 또는 `REJECTED`만** 허용한다. `SUGGESTED`·`CONFIRMED`가 오면 400 `INVALID_REQUEST`.
- `comment`는 선택 필드다. **수신만 하고 저장하지 않으며 응답에 에코하지도 않는다.** `inspection_finding`에 `review_comment` 컬럼이 없다(§2 대조 결과). 에코하면 FE가 저장된 값으로 읽는다. 감사 증적에 코멘트가 필요해지면 `V3__*.sql`로 컬럼을 추가한다.
- 역할 검사(SECURITY_ADMIN 여부)는 **하지 않는다.** 로그인·권한이 범위 밖이다(0.3). `X-User-Id`를 그대로 `reviewed_by`에 기록한다.

200 응답:

```json
{
  "findingId": 502,
  "reviewStatus": "ACCEPTED",
  "reviewedBy": { "userId": 4, "name": "박OO" },
  "reviewedAt": "2026-09-03T05:40:02Z",
  "inspection": {
    "inspectionId": 2090, "finalDecision": "BLOCK", "decidedBy": "HUMAN", "status": "BLOCKED",
    "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
    "completedAt": "2026-09-03T05:40:02Z"
  }
}
```

응답에 **재산출된 inspection 상태를 함께 싣는다.** FE가 한 번 더 조회하지 않고 목록 행과 상세 패널을 동시에 갱신할 수 있다.

**`inspection`이 싣는 값의 기준:** PATCH가 바꾸거나 화면이 다시 그려야 하는 값은 **전부** 싣는다. 빠진 값은 FE가 추론하거나 낡은 채로 남긴다 — 실제로 `submittedText`는 추론으로, `completedAt`은 낡은 값으로 각각 화면 버그가 됐다. 재조회 없이 갱신하게 하는 것이 이 객체의 존재 이유다.

**`inspection.completedAt`은 반드시 싣는다 (QA F6).** 재산출에서 **서버가 실제로 갱신하는** 값이다(위 표). 응답에 없으면 FE가 AI 완료 시각을 그대로 두어 상세 패널에 `완료 07:25:07`과 `확정자 박OO · 07:25:12`가 어긋난 채 나란히 표시된다. 판정이 움직이지 않은 경우(SUGGESTED 잔존)에는 AI 완료 시각 그대로가 실린다.

**`inspection.submittedText`는 반드시 싣는다 (D14).** PATCH가 이 값을 건드리지 않으므로 요청 전과 **같은 값**이며, 그래서 뺐다가 되돌린 필드다. 빼 두면 FE가 `status === 'BLOCKED'`에서 "본문은 null이겠지"를 추론해 화면에서 지운다 — 계약 v1의 폐기된 문구를 근거로 실제로 그렇게 하고 있었다. 값을 실으면 추론할 여지가 없어진다. `null`이 오는 경우는 이 응답에 없다(규칙 BLOCK은 AI finding이 없어 PATCH 대상이 아니다).

**최종 판정 재산출 규칙 (UC-06):**

| 해당 inspection의 AI finding 상태 | `finalDecision` | `status` | `decidedBy` | `completedAt` |
|---|---|---|---|---|
| ACCEPTED 1건 이상 | `BLOCK` | `BLOCKED` | `HUMAN` | 갱신 |
| 전부 REJECTED (SUGGESTED 0건) | `ALLOW` | `ALLOWED` | `HUMAN` | 갱신 |
| SUGGESTED가 아직 남음 | `PENDING` | `PENDING_REVIEW` | `null` 유지 | `null` 유지 |

ACCEPTED가 REJECTED를 이긴다. 한 건이라도 위반으로 확정되면 나머지가 기각이어도 전송할 수 없다.

SUGGESTED가 남아 있으면 **아무것도 건드리지 않는다.** 부분 확정 상태를 중간 판정으로 옮기면 감사 목록에 "차단됨"이 뜬 뒤 남은 후보를 기각하면서 "허용"으로 되돌아가고, 그 사이의 기록이 거짓이 된다.

`completedAt`은 AI 완료 시각에서 **사람의 확정 시각으로 갱신된다.** 감사 화면의 "완료"는 판정이 끝난 시점이고, REVIEW 건에서 그것은 담당자가 누른 순간이다. `aiStatus`는 바꾸지 않는다 — 사람의 확정은 AI 검사의 상태가 아니며, FE 폴링은 `aiStatus`로만 끝난다(D12).

**`PATCH`는 어떤 경우에도 `message.submitted_text`를 수정하지 않는다 (D14).**

`submitted_text IS NULL`은 "차단됨"이 아니라 **"마스킹본이 생성된 적 없음"**을 뜻하며, 그 상황은 `Masker`를 아예 호출하지 않는 **규칙 BLOCK 경로에서만** 발생한다(D5). REVIEW 경로는 마스킹을 실행한 뒤 AI를 호출하므로 본문이 이미 있고, 담당자는 **그 본문을 보고** 확정한다. 확정 시점에 지우면 감사 시스템이 방금 판단한 근거를 스스로 파기하는 셈이라 "판단의 근거를 남긴다"는 서비스 핵심 가치(2.4)에 어긋나고, 데모 1:50에서 ACCEPT를 누르는 순간 상세 패널 본문이 사라진다.

불변식: **`decided_by='RULE' AND status='BLOCKED' ⇒ submitted_text IS NULL`.** `ReviewService`는 `decided_by='HUMAN'`만 만들므로 이 불변식을 깨지 않는다. `integration-qa`는 `BLOCKED ⇒ NULL`이 아니라 이 형태로 검증한다.

**검사 순서가 응답 코드를 정한다.** 존재(404) → 확정 가능 여부(409) → 값 검증(400) 순이다. 존재하지 않는 finding에 400을 주면 클라이언트가 값을 먼저 의심하고, 규칙 finding에 `FINDING_ALREADY_REVIEWED`가 나가면 규칙 판정도 번복 가능한 것처럼 읽힌다 — 그래서 D13 검사가 재요청 검사보다 앞이다(규칙 finding은 항상 `CONFIRMED`라 두 조건이 겹친다).

---

## 2. 표 2 — 필드 매핑 3단 대조

이 표가 경계면 버그를 사전에 막는다. DB는 snake_case, API는 camelCase, 변환은 Jackson이 아니라 **DTO에서 명시적으로** 한다(엔티티를 직렬화하지 않는다).

### department / app_user

| DB 컬럼 | API 필드 | FE 참조 |
|---|---|---|
| `department.dept_id` | `deptId` | `dept.deptId` |
| `department.code` | `code` | `dept.code` |
| `department.name` | `name` | `dept.name` |
| `app_user.user_id` | `userId` | `user.userId` |
| `app_user.name` | `name` | `user.name` |
| `app_user.email` | `email` | `user.email` |
| `app_user.role` | `role` | `user.role` |
| `app_user.dept_id` | `department` (중첩 객체) | `user.department.name` |

### policy / policy_rule

| DB 컬럼 | API 필드 | FE 참조 |
|---|---|---|
| `policy.policy_id` | `policyId` | `policy.policyId` |
| `policy.code` | `code` | `policy.code` |
| `policy.name` | `name` | `policy.name` |
| `policy.category` | `category` | `policy.category` |
| `policy.version` | `version` | `policy.version` |
| `policy.scope` | `scope` | `policy.scope` |
| (파생) | `appliedVia` | `policy.appliedVia` |
| `policy_rule.rule_id` | `ruleId` | `rule.ruleId` |
| `policy_rule.code` | `code` | `rule.code` |
| `policy_rule.rule_type` | `ruleType` | `rule.ruleType` |
| `policy_rule.pattern` | **미노출 (C5)** | — |
| `policy_rule.action` | `action` | `rule.action` |
| `policy_rule.mask_label` | `maskLabel` | `rule.maskLabel` |
| `policy_rule.severity` | `severity` | `rule.severity` |
| `policy_rule.obligation` | `obligation` | `rule.obligation` |
| `policy_rule.embargo_until` | `embargoUntil` | `rule.embargoUntil` (문자열) |
| `policy.owner_dept_id` → `department.name` | `ownerDept` | `policy.ownerDept` |
| `policy_rule.source` | `source` | `rule.source` |
| `policy_rule.description` | `description` | `rule.description` |

### message / inspection

| DB 컬럼 | API 필드 | FE 참조 |
|---|---|---|
| `message.message_id` | `messageId` | `verdict.messageId` |
| `message.original_text` | **미노출** (6.2 화면 미노출) | — |
| `message.submitted_text` | `submittedText` | `verdict.submittedText`, `insp.submittedText` |
| `message.status` | `status` | `verdict.status`, `row.status` |
| `message.created_at` | `createdAt` | `row.createdAt` |
| `inspection.inspection_id` | `inspectionId` | `verdict.inspectionId`, `row.inspectionId` |
| `inspection.phase` | `phase` | `insp.phase` |
| `inspection.policy_snapshot` | `policySnapshot` | `insp.policySnapshot.policies[].code` / `.version` |
| `inspection.rule_result` | `ruleResult` | `insp.ruleResult.matches` |
| `inspection.ai_status` | `aiStatus` | `insp.aiStatus` |
| `inspection.ai_result` | `aiAssessment` | `insp.aiAssessment` |
| `inspection.final_decision` | `finalDecision` | `insp.finalDecision` |
| `inspection.decided_by` | `decidedBy` | `row.decidedBy` |
| `inspection.created_at` | `createdAt` | `insp.createdAt` |
| `inspection.completed_at` | `completedAt` | `insp.completedAt` |
| (파생) | `decision` (POST 응답만) | `verdict.decision` |
| (설정 파생) | `pollAfterMs` (202만) | `verdict.pollAfterMs` |
| (집계) | `ruleCount` (목록만) | `row.ruleCount` |

`ai_result` → `aiAssessment` 이름이 바뀌는 지점에 주의한다. DB 컬럼명을 그대로 쓰면 `aiResult`가 되지만, API 필드명은 9.4 스키마와 8.4 예시가 `aiAssessment`로 고정돼 있다.

### inspection_finding

| DB 컬럼 | API 필드 | FE 참조 |
|---|---|---|
| `finding_id` | `findingId` | `finding.findingId` |
| `source` | `source` | `finding.source` |
| `rule_id` | **미노출** | — |
| `code` | `code` | `finding.code` |
| `category` | `category` | `finding.category` |
| `span_start` | `spanStart` | `finding.spanStart` |
| `span_end` | `spanEnd` | `finding.spanEnd` |
| `action` | `action` | `finding.action` |
| `rationale` | `rationale` | `finding.rationale` |
| `evidence` | `evidence` | `finding.evidence[].source` |
| `review_status` | `reviewStatus` | `finding.reviewStatus` |
| `reviewed_by` | `reviewedBy` (중첩 `{userId, name}`) | `finding.reviewedBy.name` |
| `reviewed_at` | `reviewedAt` | `finding.reviewedAt` |

`reviewed_by`는 DB에서 FK(BIGINT)지만 API에서는 `{userId, name}` 객체다. 화면이 "박OO 확정"을 그려야 하므로 id만으로는 부족하고, 이 한 자리 때문에 FE가 사용자 목록을 별도 조회하게 만들 이유가 없다.

### 대조 결과 — `_workspace/01_data-architect_names.md` · `docs/erd.dbml`

| 항목 | 결과 |
|---|---|
| 8개 테이블 컬럼명 | **전건 일치.** 위 표의 좌측 열과 `erd.dbml`이 그대로 대응한다 |
| enum 값 | **전건 일치** (§3). `review_status`는 CHECK 4값(D6), DEFAULT `'SUGGESTED'`(불변식) 확인 |
| `department.code` | `DEV`, `SALES`, `HR`, `INFOSEC` — D2 반영 확인 |
| `ai_result` → `aiAssessment` | 이름이 바뀌는 유일한 지점. `data-architect`도 같은 결론을 기록했다 |
| `inspection.ai_result` 매핑 타입 | `com.skala.gateway.ai.AiAssessment`를 **그대로** 매핑했다. 저장용 타입을 따로 만들지 않았으므로 AI 스키마와 DB 스키마가 갈릴 여지가 없다 |
| `inspection_finding.evidence` | `List<AiAssessment.Evidence>` — 동일 타입 |
| `inspection_finding.review_comment` | **컬럼 없음.** PATCH의 `comment`는 수신만 하고 저장하지 않는다(§1-7). `data-architect`도 같은 결론을 기록했고, 필요해지면 `V3__*.sql`로 추가한다 |
| `policy_snapshot` 항목 shape | **조정함 → C9.** DB는 `{policyId, code, version, ruleCodes[]}`를 저장하고 API는 그대로 반환한다 |
| `rule_result` shape | §4 인계 2와 일치. `matchedKeyword`를 REGEX에서 JSON `null`로 남기는 것까지 동일(C3) |
| 시드 `version` 값 | `P-PII=4`, `P-SEC=7`, `P-CONF=2` — §4의 `policyVersion` 예시 `P-CONF:2;P-PII:4;P-SEC:7`과 일치 |

`policySnapshot`을 제외하면 계약을 고칠 것이 없었다.

---

## 3. 표 3 — enum 값 목록

값 하나라도 빠지면 FE 분기가 죽는다. 화면 표기는 5.6 용어 표를 따른다.

| 대상 | 값 | 화면 표기 |
|---|---|---|
| `message.status` | `ALLOWED`, `MASKED`, `BLOCKED`, `PENDING_REVIEW` | 허용 / 마스킹 / 차단 / 검토 대기 |
| `decision` (POST 응답) | `ALLOW`, `MASK`, `BLOCK`, `PENDING` | 허용 / 마스킹 / 차단 / 검토 대기 |
| `inspection.ai_status` | `SKIPPED`, `PENDING`, `COMPLETED`, `FAILED` | 미실행 / 분석 중 / 완료 / 실패 |
| `inspection.final_decision` | `ALLOW`, `MASK`, `BLOCK`, `PENDING` | 허용 / 마스킹 / 차단 / 검토 대기 |
| `inspection.decided_by` | `RULE`, `HUMAN`, `null` | 규칙 / 담당자 / — |
| `inspection.phase` | `INPUT` (`OUTPUT`은 Future) | — |
| `finding.source` | `RULE`, `AI` | 규칙 / AI |
| `finding.review_status` | `SUGGESTED`, `ACCEPTED`, `REJECTED`, `CONFIRMED` (**4값 — D6**) | 제안됨 / 확정(위반) / 기각 / 확정 |
| `finding.action` | `MASK`, `BLOCK`, `REVIEW`, `null`(AI) | 마스킹 / 차단 / 검토 / — |
| `policy_rule.action` | `MASK`, `BLOCK`, `REVIEW` | 마스킹 / 차단 / 검토 |
| `policy_rule.rule_type` | `REGEX`, `KEYWORD` | — |
| `policy_rule.severity` | `HIGH`, `MEDIUM`, `LOW` | 높음 / 보통 / 낮음 |
| `policy_rule.obligation` | `LEGAL`, `INTERNAL` | 법령 / 사규 |
| `policy.scope` | `GLOBAL`, `DEPT` | 전사 / 부서 |
| `policy.appliedVia` (파생) | `GLOBAL`, `DEPT` | 전사 적용 / 부서 적용 |
| `policy.category` | `PII`, `SECRET`, `CONFIDENTIAL`, `EMBARGO` | 개인정보 / 자격증명 / 기밀 / 엠바고 |
| `policy.category` | `PII`, `SECRET`, `CONFIDENTIAL` | 개인정보 / 자격증명 / 기밀 |
| `department.code` | `DEV`, `SALES`, `HR`, `INFOSEC` (D2) | 개발팀 / 영업팀 / 인사팀 / 정보보안팀 |
| `app_user.role` | `EMPLOYEE`, `SECURITY_ADMIN` | 직원 / 보안 담당자 |
| `aiAssessment.riskCandidates[].category` | `CONFIDENTIAL` (9.4 스키마가 이 한 값으로 제한) | 기밀 |
| PATCH 요청 `reviewStatus` | `ACCEPTED`, `REJECTED` (입력 허용은 2값뿐) | — |

**`CONFIRMED`에는 ACCEPT/REJECT 버튼을 노출하지 않는다 (D6).** 규칙 finding은 사람의 검토 대상이 아니다(4장). 화면에 버튼이 보이면 "AI 후보만 사람이 확정한다"는 책임 경계 주장과 정면으로 어긋난다.

**`aiAssessment` 스키마에 `decision`·`action`·`block`·`allow`·`confidence`는 없다.** 편의를 위해서라도 추가하지 않는다. 이것이 책임 경계를 스키마 수준에서 강제하는 장치다(4장, 9.4).

---

## 4. 표 4 — 인계 지점 시그니처

에이전트 간 코드가 만나는 자리. 이 시그니처는 계약이다.

| # | 인계 | 넘기는 쪽 | 받는 쪽 | 시그니처 |
|---|---|---|---|---|
| 1 | 정책 로드 | `data-architect` | `rule-engine-dev` | `List<PolicyRule> findActiveByDept(Long deptId)` |
| 2 | 판정 결과 → 응답 | `rule-engine-dev` | `api-ai-architect` | `ruleResult` JSON (8.4 형식, §5-2) |
| 3 | **REVIEW 판정 → AI 비동기 시작** | `rule-engine-dev` | `api-ai-architect` | `AiInspectionRunner.schedule(long inspectionId, AiInspectionRequest request)` |
| 4 | **AI 결과 → 영속화** | `api-ai-architect` | `rule-engine-dev` | `AiResultSink.onCompleted(long inspectionId, AiAssessment assessment)` / `onFailed(long inspectionId, String reason)` |
| 5 | AI 검사 본체 | `api-ai-architect` | (내부) | `AiAssessment AiInspector.inspect(AiInspectionRequest request)` |
| 6 | API 계약 → 화면 | `api-ai-architect` | `frontend-dev` | `docs/ai-gateway-v1.postman_collection.json` Example 6개 |

### 인계 3 — `rule-engine-dev`가 호출할 지점

```java
package com.skala.gateway.ai;

// 호출 예 (InspectionService 내부, 트랜잭션 안에서 호출해도 안전하다)
aiInspectionRunner.schedule(inspection.getId(), new AiInspectionRequest(
        maskedText,          // 마스킹 적용본. 원문 금지
        deptCode,            // "SALES"
        categories,          // ["CONFIDENTIAL"]
        hits,                // List<KeywordHit>
        policyVersion        // "P-CONF:2;P-PII:4;P-SEC:7"
));
```

`schedule()`은 **트랜잭션이 활성이면 커밋 후에 실행을 예약한다.** 활성 트랜잭션이 없으면 즉시 비동기 실행한다. `rule-engine-dev`는 `TransactionSynchronizationManager`를 직접 다룰 필요가 없다.

이 처리가 필요한 이유: 트랜잭션 커밋 전에 `@Async` 메서드가 실행되면 새 스레드가 아직 커밋되지 않은 inspection을 조회해 `EntityNotFoundException`이 난다. `@Async`에서 가장 흔한 함정이며, 각자 조심하기로 하는 대신 계약 지점에서 한 번 막는다.

**`@Async` 메서드를 `rule-engine-dev`가 따로 만들지 않는다.** 만들면 executor가 둘이 되고 스레드 풀 설정이 갈린다.

### 인계 4 — `rule-engine-dev`가 구현할 지점

```java
package com.skala.gateway.ai;

public interface AiResultSink {
    void onCompleted(long inspectionId, AiAssessment assessment);
    void onFailed(long inspectionId, String reason);
}
```

`service` 패키지에서 `@Component`로 구현한다. 구현체가 없으면 AI 결과가 저장되지 않고 ERROR 로그만 남는다(애플리케이션은 정상 기동한다 — 이번 라운드에 구현체가 없어도 부팅이 막히지 않게 하기 위함).

구현체가 할 일:

| 콜백 | 저장할 것 |
|---|---|
| `onCompleted` | `inspection.ai_result` = assessment JSON, `ai_status` = `COMPLETED`, `completed_at` = now(). `riskCandidates[]` 각각을 `inspection_finding`(source=`AI`, review_status=`SUGGESTED`, span_start/end/action = NULL)으로 INSERT |
| `onFailed` | `ai_status` = `FAILED`, `completed_at` = now(). **`message.status`는 `PENDING_REVIEW`를 유지한다.** finding은 만들지 않는다 |

두 콜백 모두 **새 트랜잭션**에서 실행된다(호출 스레드에 트랜잭션이 없다). 구현체에 `@Transactional`을 붙일 것.

### 인계 2 — `ruleResult` shape

`api-ai-architect`가 이 구조를 그대로 `inspection.rule_result`에 저장하고 응답에 싣는다.

```json
{
  "matches": [
    { "code": "SEC-DBURL-02", "category": "SECRET", "action": "BLOCK",
      "span": [18, 56], "matchedKeyword": null, "severity": "HIGH",
      "obligation": "INTERNAL", "source": "정보보안규정 4.2", "embargoUntil": null }
  ],
  "appliedRuleCodes": ["PII-RRN-01", "…"]
}
```

- `span`은 `[start, end)` 2원소 배열, **원문 기준**이다.
- `embargoUntil`은 엠바고 **해제일**(`yyyy-MM-dd` 문자열)이다. 엠바고 규칙이 아니면 `null`. 차단 조건은 `today < embargoUntil`이며 **경계일 당일은 이미 풀린 것**이다 (기획서 0.5 D20). `LocalDate`가 아니라 문자열인 이유는 이 구조가 JSONB로 저장되고 Hibernate가 자체 ObjectMapper로 직렬화하기 때문이다 — JavaTimeModule 등록 여부에 화면 계약이 흔들려서는 안 된다.
- `matchedKeyword`는 KEYWORD 규칙 매칭에만 값이 있고 REGEX는 `null`이다. REGEX 매칭 문자열을 여기에 넣지 않는다 — 주민번호 원문이 `rule_result` JSONB에 그대로 남게 된다.
- **`matches[]`는 규칙당 1건이다** (C4-1). 한 KEYWORD 규칙이 여러 키워드에 매칭돼도 항목은 하나이며, `matchedKeyword`에는 **첫 매칭**(가장 앞선 오프셋)을 싣는다. 매칭된 키워드 전부는 `AiInspectionRequest.hits`로 간다.
- `matches[]`는 D1 중첩 억제 **후**의 목록이다. Case A에서 2건이다.
- `appliedRuleCodes[]`는 로드된 활성 규칙 전체 코드다. 억제된 `SEC-PRIVIP-03`·`PII-EMAIL-04`도 여기엔 남는다.

### `AiInspectionRequest` 구성 규칙 (`rule-engine-dev`용)

| 필드 | 채우는 법 |
|---|---|
| `maskedText` | 마스킹 적용본. **원문 금지.** 최종 판정이 PENDING일 때 D5에 따라 마스킹을 이미 실행했으므로 그 결과를 그대로 넘긴다. 4,000자(`ai.max-input-chars`) 초과분은 `AiInspector` 쪽에서 절단한다 |
| `departmentCode` | 제출자 부서 code (`DEV`/`SALES`/`HR`) |
| `categories` | **매칭된 REVIEW 규칙이 속한 정책의 카테고리** 목록. 실질적으로 `["CONFIDENTIAL"]`. 적용된 정책 전체가 아니다 |
| `hits` | **KEYWORD 규칙(action=REVIEW) 매칭에서만** 생성한다. REGEX 매칭은 넣지 않는다 — PII·SECRET은 AI의 영역이 아니다(9.2 금지 조항). **매칭된 키워드를 전부 담는다**(C4-1): Case B는 `A사`·`차세대` 2건이다. 각 항목은 `KeywordHit(keyword, ruleCode, source)`이며 `source`는 `policy_rule.source` 값(RAG 확장 시 `knowledge_source` 검색 결과로 대체되는 자리) |
| `policyVersion` | `policySnapshot`의 정책을 `code:version` 쌍으로 만들어 **code 사전순 정렬 후 `;`로 연결**. 예: `P-CONF:2;P-PII:4;P-SEC:7`. 정렬을 고정하는 이유는 Mock의 결정론 때문이다 |

**`hits`가 비어 있으면 `MockAiInspector`가 `IllegalStateException`을 던진다.** 규칙 엔진이 REVIEW 판정 없이 AI를 호출했다는 뜻이므로 버그다. 조용히 빈 결과를 반환하면 그 버그가 데모까지 살아남는다 (9.5).

---

## 5. AI 확장 지점 계약

### 5-1. 타입 정의 (`com.skala.gateway.ai`)

```java
public interface AiInspector {
    AiAssessment inspect(AiInspectionRequest request);
}

public record AiInspectionRequest(
        String maskedText,
        String departmentCode,
        List<String> categories,
        List<KeywordHit> hits,
        String policyVersion) {}

public record KeywordHit(String keyword, String ruleCode, String source) {}

public record AiAssessment(
        List<RiskCandidate> riskCandidates,
        List<String> missingContext,
        boolean reviewRequired) {

    public record RiskCandidate(String code, String category, String rationale, List<Evidence> evidence) {}
    public record Evidence(String source, String excerpt) {}
}
```

**`AiInspectionRequest`에 원문 필드가 없다.** `original_text`는 어떤 경로로도 들어가지 않는다. "검사하려고 결국 원문을 밖으로 보내는 것 아닌가"(16장 예상 질의)에 대한 답이 이 필드 구성이고, 필드를 만들지 않는 것으로 코드가 답을 증명한다.

**`AiAssessment`에 `decision`·`action`·`block`·`allow`·`confidence`가 없다.** `confidence`를 두지 않는 이유는 실제 확률이 아닌 값을 확률처럼 보이게 하면 사람의 판단을 왜곡하기 때문이다.

### 5-2. 구현체

| 클래스 | 프로파일 | 상태 |
|---|---|---|
| `MockAiInspector` | `mock` (기본) | 완전 구현 |
| `LlmAiInspector` | `llm` | 클래스 골격 + 프롬프트 조립. 실제 HTTP 호출은 범위 밖(0.4) |

프로파일은 `spring.profiles.active`(기본 `mock`)로 전환한다. `ai.provider` 키는 9.6 교체 절차의 문서상 스위치이며 실제 빈 선택은 `@Profile`이 한다.

### 5-3. `MockAiInspector` 분기 (결정론)

평가 순서가 계약이다. 같은 입력에 항상 같은 출력이 나온다. 랜덤·시각·해시 순서에 의존하는 요소가 없다.

| 순서 | 조건 | 결과 |
|---|---|---|
| 1 | `hits`가 null이거나 비었음 | `IllegalStateException` — 규칙 엔진 버그 감지 |
| 2 | `maskedText`에 `ai.mock.fail-keyword`(기본 `__FAIL__`) 포함 | `RuntimeException` → `ai_status=FAILED` 경로 |
| 3 | (지연) `ai.mock.delay-ms` 기본 **2500ms** | |
| 4 | `hits[].keyword` 중 `A사` 포함 | `mock/ai/case-b-client-project.json` |
| 5 | `hits[].keyword` 중 `B사` 포함 | `mock/ai/case-client-generic.json` |
| 6 | 그 외 | `mock/ai/case-no-reference.json` (후보 0건, missingContext 1건, reviewRequired true) |

- 1번이 2번보다 앞이다. `hits` 비었음은 실패 시뮬레이션보다 우선하는 무결성 검사다. **따라서 FAILED 경로를 데모하려면 입력에 REVIEW 키워드가 함께 있어야 한다.** 검증용 입력: `A사 차세대 프로젝트 일정 __FAIL__` (`integration-qa` 참고).
- 4번이 5번보다 앞이다. `A사`와 `B사`가 동시에 있으면 `A사` 픽스처가 나온다.
- 픽스처는 기동 시 1회 로드해 캐시한다. 파싱 실패는 기동 실패로 드러난다.

**지연 2.5초를 최적화하지 않는다.** 즉시 응답하면 202 비동기 설계가 화면에 드러나지 않아 Asynchronous Pipeline 원칙 증명이 실패한다. 기획서 14장이 이것을 리스크로 명시했다.

### 5-4. 설정 키 (`ai.*`)

전부 환경변수 주입이다. 코드에 키·엔드포인트·모델명·지연값이 없고, 정책·규칙·임계값은 DB에 있다. 어느 쪽도 코드에 없는 것이 Security & Config Isolation 원칙(11.3)의 증거다.

| 키 | 환경변수 | 기본값 | 용도 |
|---|---|---|---|
| `ai.provider` | `AI_PROVIDER` | `mock` | 문서상 스위치 (9.6) |
| `ai.endpoint` | `AI_ENDPOINT` | (빈 값) | `llm` 전용 |
| `ai.api-key` | `AI_API_KEY` | (빈 값) | `llm` 전용 |
| `ai.model` | `AI_MODEL` | (빈 값) | `llm` 전용 |
| `ai.temperature` | `AI_TEMPERATURE` | `0` | 9.3 제약 |
| `ai.max-tokens` | `AI_MAX_TOKENS` | `800` | 9.3 제약 |
| `ai.timeout-ms` | `AI_TIMEOUT_MS` | `10000` | |
| `ai.max-input-chars` | `AI_MAX_INPUT_CHARS` | `4000` | 초과 시 절단 + `missingContext`에 "입력 절단" 기록 (9.3) |
| `ai.mock.delay-ms` | `AI_MOCK_DELAY_MS` | `2500` | **최적화 금지** |
| `ai.mock.fail-keyword` | `AI_MOCK_FAIL_KEYWORD` | `__FAIL__` | 실패 경로 검증 |
| `gateway.polling.interval-ms` | `GATEWAY_POLL_INTERVAL_MS` | `2000` | 응답의 `pollAfterMs` |
| `gateway.polling.max-attempts` | `GATEWAY_POLL_MAX_ATTEMPTS` | `30` | FE 폴링 상한 |
| `gateway.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:4173` | Vite dev/preview |

바인딩은 `@ConfigurationProperties("ai")` → `AiProperties` 한 곳이다. `@Value`를 여기저기 뿌리지 않는다 — 어떤 키가 있는지 한눈에 안 보이게 된다.

---

## 6. 리더 결정(D7·D9·D10·D13)과 `spec-steward` 미결 항목 처리

두 경로에서 온 결정이다. **D7·D9·D10·D13은 리더가 판정한 것으로 D1~D6과 같은 효력**이며 재논의
대상이 아니다. OQ-08·OQ-10·OQ-13은 `spec-steward`의 권고를 `api-ai-architect`가 판단해 채택한 것이다.

| ID | 결정 / 권고 | 처리 | 반영 위치 |
|---|---|---|---|
| **D7** (= OQ-01) | `submittedText`는 BLOCK일 때만 `null`. PENDING_REVIEW는 마스킹본 | **반영 완료** | §0 C4, §1-4 표, §1-5 |
| **D9** (= OQ-03) | finding은 규칙당 1건, `hits[]`는 키워드당 1건 | **반영 완료** | §0 C4-1, §4 인계 2, §4 구성 규칙 |
| **D10** | 필드명은 `decided_by` / JSON `decidedBy`. `decision_source`는 스키마에 없는 오기 | **반영 완료 — 위반 없음.** 계약서·API 명세·Postman·코드 전부 `decidedBy`만 쓴다 | §2, §3 |
| **D13** (= OQ-15) | CONFIRMED 규칙 finding에 PATCH는 409, 에러 코드 구분 | **반영 완료.** 코드명을 리더 제안대로 `RULE_FINDING_NOT_REVIEWABLE`로 확정 | §1 에러 코드 표, §1-7 |
| OQ-08 | 8.4 요청 예시를 10.4 문자열로 교체하고 span을 실측값으로 | 채택 | §0 C4-2, §6-1 |
| OQ-13 | 세 번째 Mock 케이스도 파일화해 3종을 맞춤 | 채택(파일명만 다름) | §6-2 |
| OQ-10 | `departmentCode` enum에 INFOSEC이 없는 이유를 한 줄 남길 것 | 채택. 리더도 **enum은 `DEV`·`SALES`·`HR` 유지 + 주석**으로 확인했다 | §6-3 |

### 6-1. Case A span 실측 (OQ-08)

10.4 Case A 입력에 7.2의 정규식 8종을 실행한 결과다. `spec-steward`의 수치와 대조해 확인했다.

```
이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나
```

| 규칙 | span | 매칭 문자열 | 결과 |
|---|---|---|---|
| `SEC-DBURL-02` | `[18, 56]` | `postgres://admin:p%40ss@10.0.3.21/prod` | finding 생성 (BLOCK) |
| `PII-EMAIL-04` | `[37, 51]` | `40ss@10.0.3.21` | **억제** (DB-URL 구간에 포함) |
| `SEC-PRIVIP-03` | `[42, 51]` | `10.0.3.21` | **억제** (DB-URL 구간에 포함) |
| `PII-RRN-01` | `[73, 87]` | `900101-1234567` | finding 생성 (MASK) |

**매칭 4건 → 억제 2건 → finding 2건.** D1의 "규칙 2건"과 일치한다.

억제되는 규칙이 `SEC-PRIVIP-03` 하나가 아니라 `PII-EMAIL-04`까지 둘이라는 점에 주의한다
(`spec-steward` OQ-05). 이메일 정규식이 DB 접속 문자열의 `…@10.0.3.21` 부분을 이메일로 인식한다.
최종 건수는 그대로 2건이므로 발표 대사는 바뀌지 않는다.

**`spec-steward`가 보고한 `[66, 80]`은 8.4의 축약된 요청 문자열 기준이고, 위 `[73, 87]`은 10.4의
실제 데모 문자열 기준이다.** 둘 다 각자의 문자열에 대해 맞다. Postman Example은 10.4 문자열을
쓰므로 `[73, 87]`이 맞는 값이다.

### 6-2. Mock 픽스처 3종 파일명 (OQ-13)

세 번째 케이스를 인라인 응답이 아니라 파일로 만드는 권고를 채택했다. 파일명은 권고안
`case-no-match.json` 대신 **`case-no-reference.json`**을 쓴다 — 이 케이스의 `missingContext`가
"참조 근거와 대조할 사내 문서 없음"이므로 "매칭이 없다"보다 "참조 근거가 없다"가 내용에 맞다.
매칭 자체는 있었고(그래서 호출됐고) 대조할 문서가 없는 상황이다.

`docs/ai-prompt.md`·`docs/api-spec.md`·계약서가 모두 이 이름을 쓴다.

### 6-3. `departmentCode`에 INFOSEC이 없는 이유 (OQ-10)

`AiInspectionRequest.departmentCode`의 값은 `DEV` / `SALES` / `HR` 셋이다. `INFOSEC`을 넣지 않는다.

정보보안팀은 검토자 역할만 하므로 프롬프트를 제출하지 않고(D2, 10.2), 따라서 AI 검사 경로에
도달하지 않는다. `department.code` 마스터에는 `INFOSEC`이 있지만(§3) AI 입력에서는 나타날 수 없다.
`departmentCode`는 자유 문자열이므로 값이 들어와도 깨지지 않으며, `PromptAssembler`는 매핑되지 않은
코드를 그대로 출력한다.

---

## 7. URL 규칙

리소스 명사 복수형. **구현 기술을 URL에 노출하지 않는다** (8.1).

`/ai/inspect`, `/mock/...`, `/api/v1/messages/inspect-with-ai` 같은 경로를 만들지 않는다. Mock↔LLM 교체가 `@Profile` 전환과 환경변수 주입만으로 끝나고 FE가 보는 URL과 JSON이 불변이어야 Interface First 원칙이 성립한다. 7개 엔드포인트 어디에도 `ai`·`mock`이 없다.

---

## 8. 구현 현황

7개 엔드포인트와 에러 봉투가 전부 구현되었다. 계약서에 남은 미구현 항목은 없다.

| 항목 | 담당 | 상태 |
|---|---|---|
| `api/` 컨트롤러 4종 (조회·제출) | `rule-engine-dev` | 완료 |
| `AiResultSink` 구현체 (`InspectionAiResultSink`) | `rule-engine-dev` | 완료 — §4 인계 4 |
| `InspectionService` | `rule-engine-dev` | 완료 — §4 인계 3 |
| `api/ReviewController` + `service/ReviewService` (PATCH) | `api-ai-architect` | 완료 — §1-7 |
| `api/GlobalExceptionHandler` (`@RestControllerAdvice`) | `api-ai-architect` | 완료 — §1 에러 봉투 |

`GlobalExceptionHandler`가 변환하는 것은 `ApiException`(서비스가 던지는 계약 예외), `MissingRequestHeaderException`(→ `MISSING_USER_HEADER`), `ServletRequestBindingException`(→ `INVALID_USER`), `HttpMessageNotReadableException`(→ `INVALID_REQUEST`), `MethodArgumentTypeMismatchException`(→ `INVALID_PARAMETER`) 다섯이다.

**포괄 `@ExceptionHandler(Exception.class)`를 두지 않았다.** §1 상태 코드 표에 500이 없고, 무엇이든 봉투로 감싸면 `PolicyService`의 "활성 규칙 0건"처럼 드러나야 할 서버 오류가 400대 응답처럼 보인다.

**advice는 기존 컨트롤러 4종의 응답을 삼키지 않는다.** 그쪽은 예외를 던지지 않고 `ResponseEntity`로 직접 반환하므로 경로가 겹치지 않으며, 특히 **403(BLOCK)은 여전히 판정 객체다**(C2). `ReviewApiTest.blockVerdictIsNotAnErrorEnvelope`가 이것을 고정한다.

---

## 개정

### 개정 1 — 리더 결정 D7·D9·D10·D13 반영 (2026-09-02)

**사유:** `spec-steward`의 기획서 교차 검증 결과를 리더가 판정해 4건의 결정으로 확정했다.
D1~D6과 같은 효력이며 재논의 대상이 아니다.

| 결정 | 계약 반영 | 영향 범위 |
|---|---|---|
| **D7** `submittedText`는 BLOCK일 때만 null | §0 C4 — 근거를 `spec-steward` 권고에서 **리더 결정 D7**로 교체하고, 6.2 "NULL이면 미전송"의 해석("전송 차단(BLOCK)")을 명시 | **없음.** 계약 초안이 이미 같은 결론이었다(OQ-01 채택). `docs/api-spec.md`·Postman 202 Example도 이미 마스킹본이다 |
| **D9** finding은 규칙당 1건, `hits[]`는 키워드당 1건 | §0 C4-1, §4 인계 2, §4 구성 규칙 — "키워드당 1건" 표현으로 통일하고 "감사 목록의 규칙 수는 1" 추가 | **없음.** 계약 초안이 이미 같은 결론이었다(OQ-03 채택). `AiInspectionRequest` javadoc에 D9 명시 |
| **D10** 필드명은 `decided_by` / `decidedBy` | §6 표에 위반 없음을 기록 | **없음.** 계약서·API 명세·Postman·코드 전부 `decidedBy`만 쓴다. `decision_source`는 어디에도 없다 |
| **D13** CONFIRMED 규칙 finding에 PATCH는 409 | §1 에러 코드 표, §1-7 — 코드명을 `FINDING_NOT_REVIEWABLE` → **`RULE_FINDING_NOT_REVIEWABLE`**로 변경 | **에러 코드명 1건.** 409 동작 자체는 초안과 동일(OQ-15 채택). 아직 컨트롤러가 없어 코드 영향 없음. `docs/api-spec.md` 2곳 동기화 완료 |

**부수 확인:** `departmentCode` enum은 `DEV`·`SALES`·`HR`을 유지하고 INFOSEC 미포함 사유를 주석으로
남기라는 지시도 §6-3과 `AiInspectionRequest` javadoc에 반영되어 있다.

실질 변경은 **에러 코드명 1건뿐**이다. 나머지 3건은 계약 초안이 이미 같은 결론이었고, 이번 개정은
근거를 `spec-steward` 권고에서 리더 결정으로 승격해 표기한 것이다.

### 개정 2 — 리더 결정 D14 반영: PATCH는 `submitted_text`를 지우지 않는다 (2026-09-02)

**사유:** 계약 v1의 §1-7이 "`BLOCK`으로 전이하면 `message.submitted_text`를 `null`로 되돌린다"고
적었고 §1-5가 "BLOCKED면 `null`"로 단정했다. `data-architect`가 DB 실측으로 이 서술이 D7의
필요조건 해석과 어긋난다고 제기했고, 리더가 **D14**로 판정해 계약과 반대 방향을 확정했다.
D1~D13과 같은 효력이며 재논의 대상이 아니다.

**확정 내용:** `submitted_text IS NULL`은 "차단됨"이 아니라 **"마스킹본이 생성된 적 없음"**이며,
`Masker`를 아예 호출하지 않는 **규칙 BLOCK 경로에서만** 발생한다(D5). 사람이 확정한 BLOCK은
**본문을 보존한다.** 불변식은 `decided_by='RULE' AND status='BLOCKED' ⇒ submitted_text IS NULL`이다.

| 위치 | v1 서술 | 정정 |
|---|---|---|
| §1-7 재산출 규칙 | "`BLOCK`으로 전이하면 `message.submitted_text`를 `null`로 되돌린다" | **삭제.** "PATCH는 어떤 경우에도 `submitted_text`를 수정하지 않는다"로 교체하고 근거 3건(D5로 본문 미생성 / REVIEW 경로는 본문이 이미 있고 담당자가 그것을 보고 확정 / 확정 시 삭제는 2.4 "판단의 근거를 남김"에 위배)을 명시 |
| §1-5 `submittedText` | "BLOCKED면 `null`" | **`decidedBy`까지 포함한 6행 표로 교체.** `null`은 `RULE`+`BLOCKED` 한 조합뿐. FE가 `status === 'BLOCKED'`로 분기하면 안 된다는 주의를 함께 남김 |
| §0 C4 | "`null`은 BLOCK 전용" | "`null`은 **규칙 BLOCK** 전용". 6.2 "NULL이면 미전송"의 해석을 "전송 차단(BLOCK)"에서 **"마스킹본이 생성된 적 없음"**으로 정정 |
| §1-4 상태별 표 | BLOCK(403) 칸이 `null` | **값은 그대로.** `POST /messages`의 BLOCK은 언제나 규칙 판정이라 D14와 같은 말이라는 설명을 표 아래에 추가 |
| §8 | 미구현 목록 | 구현 현황으로 교체(PATCH·advice 완료). advice가 403 판정 객체를 삼키지 않음을 명시 |

**영향 범위**

| 대상 | 영향 |
|---|---|
| 코드 | **없음(신규 구현이 D14대로 태어났다).** `ReviewService`는 `message.submitted_text`를 읽지도 쓰지도 않는다. `rule-engine-dev`가 §1-7을 v1대로 읽었다면 삭제 코드가 들어갔을 자리이므로, 구현 전 정정이 제때 된 경우다 |
| 테스트 | `ReviewApiTest.acceptBlocksAndPreservesSubmittedText`·`rejectAllowsAndPreservesSubmittedText`가 회귀를 고정한다. 실 HTTP로도 확인 — ACCEPT 후 `message.submitted_text`가 DB에 그대로 남아 있다 |
| `docs/api-spec.md` | §5·§7 동기화 완료 |
| `frontend-dev` | 상세 패널의 본문 표시 조건을 `status === 'BLOCKED'`가 아니라 `submittedText != null`로 쓸 것. 데모 1:50에서 ACCEPT 직후 본문이 남아야 한다 |
| `integration-qa` | 불변식을 `BLOCKED ⇒ NULL`이 아니라 `decided_by='RULE' AND BLOCKED ⇒ NULL`로 검증할 것 |
| Postman | 영향 없음. PATCH Example에 `submittedText`가 없다 |

**부수 확정 (D14와 별개로 §1-7에서 미정이던 것)**

- `completedAt`은 사람의 확정 시각으로 **갱신한다.** 감사 화면의 "완료"는 판정이 끝난 시점이다
- `aiStatus`는 **바꾸지 않는다.** 사람의 확정은 AI 검사의 상태가 아니고, FE 폴링은 `aiStatus`로만 끝난다(D12)
- 검사 순서는 **404 → 409 → 400**이며, 409 안에서는 **D13(`RULE_FINDING_NOT_REVIEWABLE`)이 재요청(`FINDING_ALREADY_REVIEWED`)보다 앞**이다. 규칙 finding은 항상 `CONFIRMED`라 두 조건이 겹치는데, 사유가 "이미 확정됨"이면 규칙 판정도 번복 가능한 것처럼 읽힌다

### 개정 3 — PATCH 응답에 `submittedText` 추가 + Postman Example 정정 (2026-09-02)

**사유:** `frontend-dev`가 계약서와 Postman을 실제로 소비하며 결함 5건을 보고했고 리더가 전달했다.
**계약 변경은 1건**(PATCH 응답 필드 추가)이고 나머지는 Example이 계약을 따라오지 못한 것이다.

#### 계약 변경 — `PATCH` 200 응답의 `inspection.submittedText` (필드 추가)

| 항목 | 내용 |
|---|---|
| 변경 | `inspection` 객체에 `submittedText`를 추가한다. 값은 PATCH **전과 같다**(D14로 건드리지 않으므로) |
| 사유 | 필드가 없으니 FE가 폐기된 v1 문구("BLOCK 전이 시 null로 되돌린다")를 근거로 **로컬에서 본문을 지우고 있었다.** 값을 감춰서 생긴 추론이 실제 버그가 됐다. 데모 1:50에서 ACCEPT를 누르는 순간 상세 패널 본문이 사라지면 D14가 화면에서 무력화된다 |
| 영향 | **필드 추가라 하위 호환.** 기존 필드·타입 불변. FE는 이 값을 그대로 쓰고 자체 추론을 삭제한다 |
| 반영 | `ReviewResponse.InspectionState`, §1-7 예시·설명, `docs/api-spec.md` §7, Postman `200 ACCEPT → BLOCKED` |

초안에서 이 필드를 뺀 근거는 "확정이 본문을 바꾼다는 오해를 부른다"였다. 오해를 막으려다 더 나쁜
추론을 부른 경우이므로 판단을 뒤집었다.

#### Example 정정 (계약 변경 아님 — Example이 계약과 어긋나 있었다)

| # | 결함 | 정정 | 계약 위반 지점 |
|---|---|---|---|
| 2 | MASK·REVIEW·ALLOW Example에 `policySnapshot` 없음 | **실측 응답으로 4종을 통째 교체.** `pollAfterMs`·`createdAt`까지 채워져 필드 집합이 진짜로 같아졌다 | §1-4 "4개 상태에서 필드 집합 동일", C3 `always` |
| 3 | COMPLETED Example의 AI finding에 `category` 없음, `evidence`가 문자열 배열 | **findings 13필드를 실측값으로 전부 명시.** `evidence`는 `[{source, excerpt}]` | §1-5 표는 처음부터 옳았다 — **계약은 무변경**, Example만 틀렸다 |
| 4 | PENDING Example의 `x-mock-response-name`이 `COMPLETED (200)` | **결함이 아니었다** — 리더 확인 결과 이 헤더는 `disabled: true`로 준비해 두고 필요할 때 켜는 것이 컬렉션 설계이며 description에 안내되어 있다. 다만 **값이 어떤 Example 이름과도 일치하지 않아**(`COMPLETED (200)` vs 실제 이름 `COMPLETED (200) — AI 완료`) 켜는 순간 선택에 실패했으므로 값만 정정했다. 폴링 전이 시연 방법을 `GET /inspections/{id}`의 request description에 명시 | — |
| 5 | Case B·C Example이 둘 다 `X-User-Id: {{userId}}` | **B=2(영업팀), C=1(개발팀).** MASK=3(인사팀), BLOCK=1, PATCH=4(박OO)도 함께 명시 | 같은 문장이 부서로 갈리는 데모 2:40의 근거가 Example에서 안 보였다 |

**추가로 발견해 함께 고친 것 3건**

- BLOCK Example에 **`pollAfterMs`가 없었다.** 결함 2를 "policySnapshot 추가"로만 처리했다면 남았을
  누락이며, §1-4의 "필드 집합 동일"을 실제로 깨고 있었다. 지금은 POST 4종의 **키 목록과 순서까지**
  같다. `pollAfterMs`는 REVIEW만 `2000`이고 나머지 셋은 `null`이다.
- BLOCK Example의 `policySnapshot.ruleCodes`와 `appliedRuleCodes` 정렬이 실측과 달랐다
  (8.4 표기 순서 → 스냅샷은 정책 안 사전순, `appliedRuleCodes`는 실행 순서). Case A와 같은 부서
  (개발팀, user 1)의 실측 응답에서 가져왔다. `matches`의 span은 §6-1 실측값 `[18,56]`·`[73,87]` 그대로다.
- POST `/messages` Example 4종에 `x-mock-response-name`을 값과 함께 추가했다. 컬렉션 규약대로
  `disabled: true`이며, Mock Server에서 ALLOW/MASK/BLOCK/REVIEW를 골라 내보낼 때 켜서 쓴다.

**계정 변수는 살아 있다.** 하드코딩한 것은 각 Example의 `originalRequest`뿐이고, 라이브 요청
(`item.request`)은 `{{userId}}`를 유지한다 — Postman에서 계정 전환이 그대로 된다.

**Example 본문의 출처:** 전부 기동 중인 서버의 실측 응답이다. id만 문서 전역의 값(2090/1043/501/502)으로
맞췄다. 손으로 다시 쓰면 오타가 계약 불일치가 된다(8.6).

### 개정 4 — `PATCH` 응답에 `completedAt` 추가 (QA F6) + Example 실측 교체 (2026-09-02)

**사유:** `integration-qa` 리포트(통과 96 / 실패 7)의 6건이 이 담당 범위다. **구현 결함은 F6 1건**이고
F1~F5는 Example이 실제 응답과 어긋난 것이다.

#### 계약 변경 — `PATCH` 200의 `inspection.completedAt` (필드 추가)

| 항목 | 내용 |
|---|---|
| 변경 | `inspection` 객체에 `completedAt`을 추가한다 |
| 사유 | §1-7이 "`completedAt` 갱신"을 규정하는데 응답에 없어 **FE가 반영할 수 없었다.** QA가 화면에서 잡았다 — 상세 패널에 `완료 07:25:07`(AI 완료)과 `확정자 박OO · 07:25:12`가 어긋난 채 나란히 표시된다 |
| 영향 | **필드 추가라 하위 호환.** `AuditView.onReview`가 `detail.completedAt`을 갱신하면 된다 |
| 값 | 판정이 움직였으면 확정 시각, SUGGESTED가 남아 안 움직였으면 AI 완료 시각 그대로 |

**개정 3과 같은 구조의 결함이다.** `submittedText`는 **안 바뀌는 값**을 감춰서 FE의 추론을 불렀고,
`completedAt`은 **바뀌는 값**을 감춰서 낡은 값을 남겼다. 그래서 §1-7에 판단 기준을 명문화했다 —
**PATCH가 바꾸거나 화면이 다시 그려야 하는 값은 전부 응답에 싣는다.**

#### Example 정정 (계약 변경 아님)

| # | 결함 | 정정 |
|---|---|---|
| F1 | BLOCK Example의 `matches[]`에 `matchedKeyword` 키 없음 | Case A 실측으로 교체. 두 항목 모두 `"matchedKeyword": null`이며 `matches` 항목의 키 집합이 서로 같아졌다 |
| F2 | `PII-RRN-01.source`가 `"개인정보보호법"` | 실측 **`"개인정보보호법 제24조"`**. 시드 확인 결과 이 규칙만 조문이 붙고 `PII-CARD-02`·`PII-EMAIL-04`·`PII-PHONE-03`은 `"개인정보보호법"`이 맞다 |
| F3 | `GET /policies?deptId=2` Example의 규칙 축약 | 실측 교체 — P-PII **4건** / P-SEC **3건** / P-CONF 1건. `pattern` 미노출(C5)도 재확인 |
| F4 | `GET /users` 요청은 `?deptId=2`인데 Example은 4명 전체 | 실측 교체 — 김OO 1명(`total=1`). Example 이름도 `200 사용자 4건` → `200 영업팀 1명 (deptId=2)` |
| F5 | 404·409 Example의 `message`가 영문 | 실측 한글로 교체. `code`는 원래 일치했다 |

**`docs/api-spec.md` §4의 BLOCK·MASK 예시에도 같은 결함이 있어 함께 고쳤다.** QA는 Postman만 지적했지만
같은 종류의 문서이고 같은 실측 응답이 정답이다. `pollAfterMs`·`policySnapshot`·`matchedKeyword`가
채워지고 `ruleCodes`·`appliedRuleCodes` 정렬이 실측과 맞았다.

**F7(`aiStatus` 화면 표기)은 이 담당이 아니다.** 계약서 §3 표와 `docs/screen-spec.md` 중 무엇이 SSOT인지
`spec-steward`가 확정할 항목이다. 계약서 §3은 그대로 둔다.
