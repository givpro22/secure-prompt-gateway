# REST API 명세 — ai-gateway-v1

사내 생성형 AI 게이트웨이의 엔드포인트 7종. 기획서 8장이 원본이고, 확정 계약은
`_workspace/01_api-ai-architect_contract-freeze.md`다. 이 문서와 계약서가 어긋나면 계약서가 맞다.

---

## 공통 규약

| 항목 | 규약 |
|---|---|
| Base path | `/api/v1` |
| 인증 | 없음. 요청 헤더 `X-User-Id`로 현재 사용자 전달 (계정 전환 드롭다운 값) |
| 명명 | JSON은 camelCase, DB는 snake_case |
| 시각 | ISO 8601, UTC (`2026-09-03T05:31:12Z`) |
| 목록 응답 | `{ "items": [], "page": 0, "size": 20, "total": 137 }`. 비페이징 엔드포인트도 같은 봉투를 쓰며 `page=0`, `size=total=items.length` |
| 에러 응답 | `{ "code": "…", "message": "…", "details": null }` |
| null | 응답에서 생략하지 않고 `null`로 싣는다. FE가 "필드 없음"과 "null"을 구분하지 않아도 된다 |
| URL | 리소스 명사 복수형. 구현 기술(`ai`, `mock`)을 URL에 노출하지 않는다 |

`X-User-Id`가 필요한 엔드포인트는 `POST /messages`와 `PATCH …/findings/{findingId}` 둘뿐이다.

## 상태 코드 정책

| 코드 | 사용 상황 |
|---|---|
| 200 | 조회 성공, ALLOW/MASK 판정, PATCH 성공 |
| 202 | REVIEW 판정. AI 비동기 처리 시작. `Location` 헤더에 폴링 URL |
| 400 | 본문 누락·빈 문자열, `X-User-Id` 누락·무효, 쿼리 파라미터 형식 오류 |
| 403 | BLOCK 판정. 정책에 의해 전송이 금지됨 |
| 404 | 존재하지 않는 inspection·finding |
| 409 | 이미 처리된 finding에 ACCEPT/REJECT 재요청 |

### 201을 쓰지 않는 이유

`POST /messages`는 `message` 리소스를 실제로 생성하지만 201을 반환하지 않는다.

클라이언트가 받아야 할 주 정보가 "생성 사실"이 아니라 "판정 결과"이기 때문이다. 201 + `Location`으로
응답하면 판정을 알기 위해 한 번 더 요청해야 하고, 무엇보다 BLOCK(전송 거부)을 201로 표현할 방법이
없다. 판정에 따라 200 / 202 / 403으로 갈리는 설계가 각 상태 코드에 고유한 의미를 부여한다.
(기획서 0.5 D4, 8.2)

### 403이 에러 봉투가 아닌 이유

BLOCK은 요청 처리 실패가 아니라 정상적으로 수행된 판정이다. 화면이 차단 사유(규칙 코드·출처·심각도)를
보여줘야 하므로 `{code, message}`만으로는 부족하다. **403 응답은 200/202와 같은 판정 객체**이며,
에러 봉투는 400 / 404 / 409에만 적용된다.

### 에러 코드

| code | HTTP | 발생 조건 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 요청 본문 누락, `text`가 빈 문자열/공백, `reviewStatus`가 ACCEPTED/REJECTED 외 |
| `MISSING_USER_HEADER` | 400 | `X-User-Id` 헤더 없음 |
| `INVALID_USER` | 400 | `X-User-Id`가 숫자가 아니거나 존재하지 않는 사용자 |
| `INVALID_PARAMETER` | 400 | `deptId` 비숫자·누락, `status` enum 외 값, `page`/`size` 음수 |
| `INSPECTION_NOT_FOUND` | 404 | 해당 id의 inspection 없음 |
| `FINDING_NOT_FOUND` | 404 | finding 없음, 또는 경로의 inspection에 속하지 않음 |
| `FINDING_ALREADY_REVIEWED` | 409 | 이미 ACCEPTED 또는 REJECTED |
| `RULE_FINDING_NOT_REVIEWABLE` | 409 | `source='RULE'` 또는 `review_status='CONFIRMED'`. 규칙 판정은 사람이 번복하지 않는다 |

봉투 변환은 `api/GlobalExceptionHandler`(`@RestControllerAdvice`)가 한다. `X-User-Id` 누락·비숫자,
깨진 JSON 본문, 경로 변수 타입 불일치도 전부 이 표의 코드로 나간다 — Spring 기본 400 본문
(`{"timestamp":…,"status":400,"error":"Bad Request"}`)은 더 이상 나오지 않는다.

**403(BLOCK)은 advice를 거치지 않는다.** 판정은 예외가 아니라 정상 응답이므로 컨트롤러가 판정 객체를
직접 반환한다.

---

## 1. `GET /api/v1/departments`

부서 목록. 헤더 불필요.

**200**

```json
{
  "items": [
    { "deptId": 1, "code": "DEV", "name": "개발팀" },
    { "deptId": 2, "code": "SALES", "name": "영업팀" },
    { "deptId": 3, "code": "HR", "name": "인사팀" },
    { "deptId": 4, "code": "INFOSEC", "name": "정보보안팀" }
  ],
  "page": 0, "size": 4, "total": 4
}
```

INFOSEC이 포함된다. 계정 전환 드롭다운에 보안 담당자가 필요하기 때문이다. 감사 콘솔의 부서 필터에서
INFOSEC을 빼는 것은 FE의 표시 결정이며, API는 마스터 4행을 그대로 반환한다 (기획서 0.5 D2).

## 2. `GET /api/v1/users`

계정 전환용 사용자 목록. 쿼리 `deptId`(선택).

**200**

```json
{
  "items": [
    { "userId": 2, "name": "김OO", "email": "kim@example.com", "role": "EMPLOYEE",
      "department": { "deptId": 2, "code": "SALES", "name": "영업팀" } }
  ],
  "page": 0, "size": 4, "total": 4
}
```

## 3. `GET /api/v1/policies`

부서에 적용되는 정책과 규칙. 쿼리 `deptId` **필수**(누락 시 400).

**200**

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

- `appliedVia` — `GLOBAL`(scope=GLOBAL이라 전사 적용) 또는 `DEPT`(department_policy 매핑). 기획서 7.3
- `ownerDept` — 정책을 **만든** 부서명. 적용 부서와 다르다. 엠바고는 홍보팀이 걸고 개발팀·영업팀이 걸린다 (기획서 0.5 D19). 소유 부서가 없으면 `null`
- `rules[].embargoUntil` — 엠바고 **해제일** `yyyy-MM-dd`. 그 날부터 공개 가능하며 차단 조건은 `today < embargoUntil`. 엠바고 규칙이 아니면 `null` (기획서 0.5 D20)
  매트릭스의 "○ (GLOBAL)" / "○ (매핑)"을 필드로 옮긴 것이다.
- **`pattern`은 응답에 없다.** 탐지 정규식이 클라이언트에 노출되면 우회 입력을 만들 수 있다.
- 비활성(`is_active=false`) 정책·규칙은 포함하지 않는다.

## 4. `POST /api/v1/messages`

프롬프트 제출 → 규칙 판정. 헤더 `X-User-Id` **필수**.

**요청**

```json
{ "text": "이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나" }
```

요청 문자열은 기획서 10.4의 데모 Case A 그대로다. 8.4의 축약 예시 대신 이것을 쓰는 이유는 아래
BLOCK 응답의 `span` 값이 요청 문자열과 실제로 맞아야 하기 때문이다 (`spec-steward` OQ-08).

응답 필드 집합은 네 상태에서 동일하고 값만 달라진다. FE가 상태 코드별로 다른 파서를 쓰지 않는다.

| 필드 | ALLOW(200) | MASK(200) | BLOCK(403) | REVIEW(202) |
|---|---|---|---|---|
| `decision` | `ALLOW` | `MASK` | `BLOCK` | `PENDING` |
| `status` | `ALLOWED` | `MASKED` | `BLOCKED` | `PENDING_REVIEW` |
| `submittedText` | 원문 | 마스킹본 | `null` | 마스킹본 |
| `aiStatus` | `SKIPPED` | `SKIPPED` | `SKIPPED` | `PENDING` |
| `decidedBy` | `RULE` | `RULE` | `RULE` | `null` |
| `pollAfterMs` | `null` | `null` | `null` | `2000` |

**BLOCK (403)**

```json
{
  "messageId": 1041,
  "inspectionId": 2088,
  "decision": "BLOCK",
  "status": "BLOCKED",
  "submittedText": null,
  "policySnapshot": {
    "policies": [
      {
        "policyId": 1,
        "code": "P-PII",
        "version": 3,
        "ruleCodes": [
          "PII-CARD-02",
          "PII-EMAIL-04",
          "PII-PHONE-03",
          "PII-RRN-01"
        ]
      },
      {
        "policyId": 2,
        "code": "P-SEC",
        "version": 7,
        "ruleCodes": [
          "SEC-AWSKEY-01",
          "SEC-DBURL-02",
          "SEC-PRIVIP-03"
        ]
      }
    ]
  },
  "ruleResult": {
    "matches": [
      {
        "code": "SEC-DBURL-02",
        "category": "SECRET",
        "action": "BLOCK",
        "span": [
          18,
          56
        ],
        "matchedKeyword": null,
        "severity": "HIGH",
        "obligation": "INTERNAL",
        "source": "정보보안규정 4.2", "embargoUntil": null },
      {
        "code": "PII-RRN-01",
        "category": "PII",
        "action": "MASK",
        "span": [
          73,
          87
        ],
        "matchedKeyword": null,
        "severity": "HIGH",
        "obligation": "LEGAL",
        "source": "개인정보보호법 제24조", "embargoUntil": null }
    ],
    "appliedRuleCodes": [
      "PII-CARD-02",
      "PII-RRN-01",
      "SEC-AWSKEY-01",
      "SEC-DBURL-02",
      "PII-PHONE-03",
      "SEC-PRIVIP-03",
      "PII-EMAIL-04"
    ]
  },
  "aiStatus": "SKIPPED",
  "decidedBy": "RULE",
  "pollAfterMs": null,
  "createdAt": "2026-09-03T05:31:12Z"
}
```

`matches`가 2건인 이유는 중첩 억제 때문이다 (기획서 0.5 D1). 이 입력에는 규칙 4건이 매칭되고 그중
2건이 억제된다.

| 규칙 | span | 매칭 문자열 | 결과 |
|---|---|---|---|
| `SEC-DBURL-02` | `[18, 56]` | `postgres://admin:p%40ss@10.0.3.21/prod` | finding 생성 |
| `PII-EMAIL-04` | `[37, 51]` | `40ss@10.0.3.21` | 억제 |
| `SEC-PRIVIP-03` | `[42, 51]` | `10.0.3.21` | 억제 |
| `PII-RRN-01` | `[73, 87]` | `900101-1234567` | finding 생성 |

억제되는 것은 사설 IP 하나가 아니라 둘이다 — 이메일 정규식이 DB 접속 문자열의 `…@10.0.3.21` 부분을
이메일로 인식한다. 최종 건수는 그대로 2건이므로 발표 대사("규칙 2건")는 바뀌지 않는다.

`appliedRuleCodes`에는 억제된 규칙도 그대로 남는다 — 적용된 규칙과 매칭된 규칙은 다르다.

BLOCK이면 AI를 호출하지 않는다(`aiStatus: SKIPPED`). 이미 확정된 위반에 비용을 쓸 이유가 없고,
외부로 보낼 텍스트 자체가 없다. 같은 이유로 마스킹도 실행하지 않는다 (기획서 7.5, 0.5 D5).

**MASK (200)**

```json
{
  "messageId": 1042,
  "inspectionId": 2089,
  "decision": "MASK",
  "status": "MASKED",
  "submittedText": "고객 연락처 [전화번호] 로 회신 요청",
  "policySnapshot": {
    "policies": [
      {
        "policyId": 1,
        "code": "P-PII",
        "version": 3,
        "ruleCodes": [
          "PII-CARD-02",
          "PII-EMAIL-04",
          "PII-PHONE-03",
          "PII-RRN-01"
        ]
      },
      {
        "policyId": 2,
        "code": "P-SEC",
        "version": 7,
        "ruleCodes": [
          "SEC-AWSKEY-01",
          "SEC-DBURL-02",
          "SEC-PRIVIP-03"
        ]
      },
      {
        "policyId": 3,
        "code": "P-CONF",
        "version": 2,
        "ruleCodes": [
          "CONF-CLIENT-01"
        ]
      }
    ]
  },
  "ruleResult": {
    "matches": [
      {
        "code": "PII-PHONE-03",
        "category": "PII",
        "action": "MASK",
        "span": [
          7,
          20
        ],
        "matchedKeyword": null,
        "severity": "MEDIUM",
        "obligation": "LEGAL",
        "source": "개인정보보호법", "embargoUntil": null }
    ],
    "appliedRuleCodes": [
      "PII-CARD-02",
      "PII-RRN-01",
      "SEC-AWSKEY-01",
      "SEC-DBURL-02",
      "PII-PHONE-03",
      "SEC-PRIVIP-03",
      "PII-EMAIL-04",
      "CONF-CLIENT-01"
    ]
  },
  "aiStatus": "SKIPPED",
  "decidedBy": "RULE",
  "pollAfterMs": null,
  "createdAt": "2026-09-03T05:31:12Z"
}
```

**REVIEW (202)** — 헤더 `Location: /api/v1/inspections/2090`

```json
{
  "messageId": 1043,
  "inspectionId": 2090,
  "decision": "PENDING",
  "status": "PENDING_REVIEW",
  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
  "ruleResult": {
    "matches": [
      { "code": "CONF-CLIENT-01", "category": "CONFIDENTIAL", "action": "REVIEW",
        "span": [0, 2], "matchedKeyword": "A사", "severity": "MEDIUM",
        "obligation": "INTERNAL", "source": "고객사 NDA 목록 v3", "embargoUntil": null }
    ]
  },
  "aiStatus": "PENDING",
  "pollAfterMs": 2000
}
```

`submittedText`는 **마스킹 적용본**이다 (D7). Case B는 매칭된 MASK 규칙이 없어 원문과 같지만, REVIEW와
MASK가 함께 걸리면 마스킹된 본문이 온다. `null`은 **규칙 BLOCK 전용**이며, 6.2의 "NULL이면 미전송"은
"마스킹본이 생성된 적 없음"의 의미다 (D14 — §5의 표 참조). 8.4의 202 예시에 있는
`"submittedText": null`은 같은 검사 건(2090)의 GET 응답과 어긋나는 기획서 오류다.

감사 담당자가 검토해야 할 바로 그 건의 본문이 비면 SCR-02 상세 패널이 무용지물이 되고, 이 값은
AI에 넘기는 `maskedText`와 같으므로 따로 감출 이유가 없다.

`matches`는 **규칙당 1건**이다 (D9). 이 문자열은 `A사`(offset 0)와 `차세대`(offset 3) 두 키워드에
매칭되지만 CONF-CLIENT-01 하나이므로 항목은 하나이고, `matchedKeyword`에는 규칙당 첫 매칭이
들어간다. 감사 목록의 "규칙 수"도 1이다. 매칭된 키워드 전부는 **키워드당 1건**으로 AI 입력의
`hits[]`에 담긴다.

**202 응답에 없는 것: `aiAssessment`, AI findings, `completedAt`.** FE가 이 시점에 `aiAssessment`를
참조하면 크래시한다. AI 결과는 `GET /inspections/{id}` 폴링으로만 얻는다.

`pollAfterMs`는 서버가 지시하는 폴링 간격이다. FE는 자체 상수 대신 이 값을 쓴다.

**ALLOW (200)**

```json
{
  "messageId": 1044,
  "inspectionId": 2091,
  "decision": "ALLOW",
  "status": "ALLOWED",
  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
  "ruleResult": { "matches": [], "appliedRuleCodes": ["…"] },
  "aiStatus": "SKIPPED",
  "decidedBy": "RULE"
}
```

## 5. `GET /api/v1/inspections/{id}`

판정 상세. 폴링 겸용. 헤더 불필요.

**200 — AI 완료 후**

```json
{
  "inspectionId": 2090,
  "messageId": 1043,
  "phase": "INPUT",
  "user": { "userId": 2, "name": "김OO", "department": "영업팀" },
  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
  "status": "PENDING_REVIEW",
  "policySnapshot": { "policies": [ { "policyId": 1, "code": "P-PII", "version": 4, "ruleCodes": ["…"] },
                                    { "policyId": 2, "code": "P-SEC", "version": 7, "ruleCodes": ["…"] },
                                    { "policyId": 3, "code": "P-CONF", "version": 2, "ruleCodes": ["CONF-CLIENT-01"] } ] },
  "ruleResult": { "matches": [ { "code": "CONF-CLIENT-01", "action": "REVIEW", "…": "…" } ] },
  "aiStatus": "COMPLETED",
  "aiAssessment": {
    "riskCandidates": [
      {
        "code": "CONF-CLIENT-PROJECT",
        "category": "CONFIDENTIAL",
        "rationale": "'A사 차세대 프로젝트 오픈 일정'이라는 서술이 계약 상대방과 미공개 일정을 동시에 특정함",
        "evidence": [
          { "source": "고객사 NDA 목록 v3", "excerpt": "A사 — 비밀유지 2027.03까지, 일정·범위 포함" }
        ]
      }
    ],
    "missingContext": [ "해당 일정이 대외 공개된 정보인지 확인 필요" ],
    "reviewRequired": true
  },
  "findings": [
    { "findingId": 501, "source": "RULE", "code": "CONF-CLIENT-01", "category": "CONFIDENTIAL",
      "spanStart": 0, "spanEnd": 2, "action": "REVIEW",
      "rationale": null, "evidence": null,
      "reviewStatus": "CONFIRMED", "reviewedBy": null, "reviewedAt": null },
    { "findingId": 502, "source": "AI", "code": "CONF-CLIENT-PROJECT", "category": "CONFIDENTIAL",
      "spanStart": null, "spanEnd": null, "action": null,
      "rationale": "'A사 차세대 프로젝트 오픈 일정'이라는 서술이 계약 상대방과 미공개 일정을 동시에 특정함",
      "evidence": [
        { "source": "고객사 NDA 목록 v3", "excerpt": "A사 — 비밀유지 2027.03까지, 일정·범위 포함" }
      ],
      "reviewStatus": "SUGGESTED", "reviewedBy": null, "reviewedAt": null }
  ],
  "finalDecision": "PENDING",
  "decidedBy": null,
  "createdAt": "2026-09-03T05:33:40Z",
  "completedAt": "2026-09-03T05:33:43Z"
}
```

`aiStatus`별 차이 — 폴링 FE의 분기 근거:

| `aiStatus` | `aiAssessment` | `findings[]` | `finalDecision` | `completedAt` |
|---|---|---|---|---|
| `SKIPPED` | `null` | RULE만 | ALLOW/MASK/BLOCK | 판정 시각 |
| `PENDING` | `null` | RULE만 | `PENDING` | `null` |
| `COMPLETED` | 객체 | RULE + AI | `PENDING` (사람 확정 전) | AI 완료 시각 |
| `FAILED` | `null` | RULE만 | `PENDING` | 실패 시각 |

**`FAILED`여도 `status`는 `PENDING_REVIEW`를 유지한다.** `ALLOWED`로 떨어뜨리면 검사되지 않은
프롬프트가 통과 기록으로 남는다. AI가 죽어도 사람 검토로 폴백된다는 것이 이 경로의 요점이다.

`findings[]` 항목:

**두 출처가 같은 필드 집합을 쓰고 값으로만 갈린다.** 한쪽에만 있는 키는 없다 — `null`로 실린다.

| 필드 | RULE finding | AI finding |
|---|---|---|
| `source` | `"RULE"` | `"AI"` |
| `code` | 규칙 코드 | AI 후보 코드 |
| `category` | `PII`/`SECRET`/`CONFIDENTIAL` | `CONFIDENTIAL` |
| `spanStart`/`spanEnd` | 원문 기준 오프셋 | `null` |
| `action` | `MASK`/`BLOCK`/`REVIEW` | `null` |
| `rationale` | `null` | 문자열 |
| `evidence` | `null` | **`[{ "source": "…", "excerpt": "…" }]`** — 객체 배열이며 문자열 배열이 아니다 |
| `reviewStatus` | **`CONFIRMED` 고정** | `SUGGESTED` → `ACCEPTED`/`REJECTED` |
| `reviewedBy` / `reviewedAt` | `null` | 확정 시 값 |

`spanStart`/`spanEnd`는 **원문 기준**이다. 마스킹이 길이를 바꾸므로 이 값으로 `submittedText`를 자르면
하이라이트가 밀린다. 화면 하이라이트는 `submittedText`에서 마스킹 라벨 문자열(`[주민번호]` 등)을
검색해 처리한다 (기획서 0.5 D3).

**원문(`original_text`)은 어떤 상태에서도 응답에 포함되지 않는다.**

### `submittedText`가 `null`인 경우 (기획서 0.5 D7·D14)

`submittedText`는 `message.submitted_text`를 그대로 반환한다. `null`은 **"차단됨"이 아니라
"마스킹본이 생성된 적 없음"**을 뜻하며, `Masker`를 아예 호출하지 않는 **규칙 BLOCK 경로에서만**
발생한다 (D5).

| `status` | `decidedBy` | `submittedText` |
|---|---|---|
| `ALLOWED` | `RULE` | 원문 |
| `MASKED` | `RULE` | 마스킹본 |
| `BLOCKED` | `RULE` | **`null`** — 마스킹 미실행 |
| `BLOCKED` | `HUMAN` | **마스킹본 보존** — 사람이 확정한 BLOCK |
| `ALLOWED` | `HUMAN` | 마스킹본 |
| `PENDING_REVIEW` | `null` | 마스킹본 |

불변식은 `BLOCKED ⇒ NULL`이 아니라 **`decidedBy='RULE' AND status='BLOCKED' ⇒ submittedText is null`**이다.
FE는 본문 표시 조건을 `status === 'BLOCKED'`가 아니라 `submittedText != null`로 쓴다 — 전자로 쓰면
담당자가 방금 확정한 건에서 본문이 있는데도 사라진다.

**404** — `{ "code": "INSPECTION_NOT_FOUND", "message": "…", "details": null }`

## 6. `GET /api/v1/inspections`

감사 목록.

| 쿼리 | 기본값 | 비고 |
|---|---|---|
| `deptId` | 전체 | |
| `status` | 전체 | `ALLOWED`/`MASKED`/`BLOCKED`/`PENDING_REVIEW` |
| `from` / `to` | 없음 | ISO 8601. `createdAt` 기준, `from` 이상 `to` 미만 |
| `page` | `0` | 0부터 |
| `size` | `20` | 최대 100 |

**200**

```json
{
  "items": [
    { "inspectionId": 2090, "createdAt": "…", "department": "영업팀", "userName": "김OO",
      "status": "PENDING_REVIEW", "ruleCount": 1, "aiStatus": "COMPLETED", "decidedBy": null },
    { "inspectionId": 2089, "createdAt": "…", "department": "개발팀", "userName": "이OO",
      "status": "MASKED", "ruleCount": 1, "aiStatus": "SKIPPED", "decidedBy": "RULE" }
  ],
  "page": 0, "size": 20, "total": 137
}
```

`ruleCount`는 `source='RULE'`인 finding 개수이며 중첩 억제 후의 값이다. 정렬은 `createdAt DESC` 고정.

## 7. `PATCH /api/v1/inspections/{id}/findings/{findingId}`

AI 후보 ACCEPT/REJECT. 헤더 `X-User-Id` **필수** — 이 값이 `reviewedBy`로 기록된다.

**요청**

```json
{ "reviewStatus": "ACCEPTED", "comment": "NDA 대상 고객사 일정. 전송 불가" }
```

`reviewStatus`는 `ACCEPTED` 또는 `REJECTED`만 허용한다. `SUGGESTED`·`CONFIRMED`를 포함해 그 밖의 값은
400 `INVALID_REQUEST`다.

`comment`는 선택이며 **수신만 하고 저장하지 않는다.** `inspection_finding`에 `review_comment` 컬럼이
없다. 응답에 에코하지도 않는다 — 저장하지 않는 값을 실으면 FE가 저장된 것으로 읽는다.

**200**

```json
{
  "findingId": 502,
  "reviewStatus": "ACCEPTED",
  "reviewedBy": { "userId": 4, "name": "박OO" },
  "reviewedAt": "2026-09-03T05:40:02Z",
  "inspection": { "inspectionId": 2090, "finalDecision": "BLOCK", "decidedBy": "HUMAN",
                  "status": "BLOCKED",
                  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
                  "completedAt": "2026-09-03T05:40:02Z" }
}
```

응답에 **재산출된 inspection 상태를 함께 싣는다.** FE가 한 번 더 조회하지 않고 목록 행과 상세 패널을
동시에 갱신할 수 있다. **PATCH가 바꾸거나 화면이 다시 그려야 하는 값은 전부 여기에 있다** — 빠진
값은 FE가 추론하거나 낡은 채로 남긴다.

`completedAt`은 재산출에서 **사람의 확정 시각으로 갱신된 값**이다. 이 필드가 없으면 상세 패널의
"완료 시각"이 AI 완료 시각에 머물러 바로 아래 확정자 시각과 어긋나 보인다. 판정이 움직이지 않은
경우(SUGGESTED 잔존)에는 AI 완료 시각 그대로 실린다.

`inspection.submittedText`는 **PATCH 전과 같은 값**이다 (D14 — PATCH는 본문을 건드리지 않는다).
그럼에도 응답에 싣는 이유는, 빼 두면 FE가 `status === 'BLOCKED'`에서 "본문은 null이겠지"를 추론해
화면에서 지우기 때문이다. 값을 실으면 추론할 여지가 없다. 이 응답에서 `null`이 오는 경우는 없다 —
규칙 BLOCK 건은 AI finding이 없어 애초에 PATCH 대상이 아니다.

**최종 판정 재산출 규칙**

| AI finding 상태 | `finalDecision` | `status` | `decidedBy` | `completedAt` |
|---|---|---|---|---|
| ACCEPTED 1건 이상 | `BLOCK` | `BLOCKED` | `HUMAN` | 확정 시각으로 갱신 |
| 전부 REJECTED | `ALLOW` | `ALLOWED` | `HUMAN` | 확정 시각으로 갱신 |
| SUGGESTED가 남음 | `PENDING` | `PENDING_REVIEW` | `null` 유지 | 유지 |

`decidedBy`가 `RULE`에서 `HUMAN`으로 전이하는 것이 책임 경계 설계의 증거다. 규칙이 판정한 건은
`RULE`, 사람이 확정한 건은 `HUMAN`으로 남아 감사 기록에서 구분된다.

ACCEPTED가 REJECTED를 이긴다 — 한 건이라도 위반으로 확정되면 나머지가 기각이어도 전송할 수 없다.
SUGGESTED가 남아 있으면 아무것도 건드리지 않는다. 부분 확정을 중간 판정으로 옮기면 감사 목록에
"차단됨"이 뜬 뒤 남은 후보를 기각하면서 "허용"으로 되돌아가고, 그 사이의 기록이 거짓이 된다.

`aiStatus`는 바꾸지 않는다. 사람의 확정은 AI 검사의 상태가 아니며, FE 폴링은 `aiStatus`로만
끝난다 (기획서 0.5 D12).

**PATCH는 `submittedText`를 수정하지 않는다 (기획서 0.5 D14)**

ACCEPT로 `BLOCKED`가 되어도 **본문을 보존한다.** `submitted_text IS NULL`은 "마스킹본이 생성된 적
없음"을 뜻하며 규칙 BLOCK 경로에서만 발생한다(D5). REVIEW 경로는 마스킹을 실행한 뒤 AI를 호출하므로
본문이 이미 있고, 담당자는 그것을 보고 확정한다. 확정 시점에 지우면 감사 시스템이 방금 판단한 근거를
스스로 파기하는 셈이라 "판단의 근거를 남긴다"는 서비스 핵심 가치(기획서 2.4)에 어긋난다.

**409**

```json
{ "code": "FINDING_ALREADY_REVIEWED", "message": "finding 502 is already ACCEPTED", "details": null }
```

이미 처리된 finding에 재요청하면 멱등 처리로 200을 주지 않고 409를 반환한다. 200을 주면
`reviewedAt`이 덮어써져 증적이 손상된다.

규칙 finding(`source: RULE` 또는 `reviewStatus: CONFIRMED`)에 PATCH가 오면 `RULE_FINDING_NOT_REVIEWABLE`
409다. 규칙 판정은 사람이 번복하지 않는다 (기획서 4장, 이번 범위).

**404**

```json
{ "code": "FINDING_NOT_FOUND", "message": "…", "details": null }
```

없는 inspection은 `INSPECTION_NOT_FOUND`, 없는 finding은 `FINDING_NOT_FOUND`다. **`findingId`가 경로의
`inspectionId`에 속하지 않아도 `FINDING_NOT_FOUND` 404**다 — 다른 코드로 구분하면 남의 검사 건
finding id의 존재 여부를 알려 주는 셈이 된다.

**검사 순서**는 존재(404) → 확정 가능 여부(409) → 값 검증(400)이다. 409 안에서는 D13
(`RULE_FINDING_NOT_REVIEWABLE`)이 재요청(`FINDING_ALREADY_REVIEWED`)보다 앞이다. 규칙 finding은 항상
`CONFIRMED`라 두 조건이 겹치는데, 사유가 "이미 확정됨"이면 규칙 판정도 번복 가능한 것처럼 읽힌다.

---

## 비동기 흐름 (202 + 폴링)

```
FE                      BE                          AiInspector(Mock)
 │ POST /messages        │                              │
 │──────────────────────▶│ 규칙 판정 → REVIEW           │
 │                       │ inspection(ai_status=PENDING)│
 │ 202 + Location        │──@Async inspect()───────────▶│
 │◀──────────────────────│                              │ sleep 2500ms
 │ GET /inspections/{id} │                              │
 │──────────────────────▶│ ai_status=PENDING            │
 │ 200 (PENDING)         │                              │
 │◀──────────────────────│                              │
 │   … 2초 간격 …         │◀───── aiAssessment JSON ─────│
 │                       │ ai_result 저장, finding 생성  │
 │ GET /inspections/{id} │ ai_status=COMPLETED          │
 │──────────────────────▶│                              │
 │ 200 (COMPLETED)       │                              │
 │◀──────────────────────│                              │
```

정규식 판정은 밀리초 단위로 끝나므로 기다릴 이유가 없고, 외부 모델 호출은 지연이 예측 불가능하므로
접수와 결과 조회를 분리한다. 이 분리로 200·202·403이 각각 쓰일 이유를 갖는다.

**응답을 먼저 보내고 비동기를 시작한다.** 트랜잭션 커밋 전에 `@Async` 메서드가 실행되면 새 스레드가
아직 커밋되지 않은 inspection을 조회한다. `AiInspectionRunner.schedule()`이
`TransactionSynchronization.afterCommit`으로 이것을 처리하므로 호출 측은 트랜잭션 안에서 호출해도 된다.

폴링은 `pollAfterMs`(2000ms) 간격, `gateway.polling.max-attempts`(30회)가 상한이다. 상한에 도달하면
FE는 폴링을 멈추고 "검토 대기" 상태로 남긴다.

---

## Postman

컬렉션: `docs/ai-gateway-v1.postman_collection.json`

- 컬렉션명 `ai-gateway-v1`, 환경 변수 `baseUrl`·`userId`
- 폴더 4개 — `departments`(부서·사용자 마스터) / `policies` / `messages` / `inspections`
- Mock Server Example 6개 — `POST /messages`에 ALLOW·MASK·BLOCK·REVIEW,
  `GET /inspections/{id}`에 PENDING·COMPLETED
- 같은 URL에 Example이 여럿인 경우(POST `/messages` 4개, GET `/inspections/{id}` 2개) Mock Server는
  기본적으로 첫 Example을 반환한다. 특정 Example을 받으려면 요청에 `x-mock-response-name` 헤더를
  붙인다. 컬렉션의 각 요청에 비활성 헤더로 준비되어 있다

Example 본문은 기획서 8.4의 JSON 그대로다. 손으로 다시 쓰면 오타가 계약 불일치가 된다.

FE는 `VITE_API_BASE`만 Mock Server URL로 바꾸면 선행 개발을 시작할 수 있고, BE가 준비되면 같은
변수를 실제 주소로 바꾼다. 코드 변경은 없다.
