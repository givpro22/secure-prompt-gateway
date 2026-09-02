# 컬럼명·enum 값 목록 — `data-architect` 산출물

**작성일:** 2026-09-02
**상태:** 확정. `V1__schema.sql` + `V2__seed.sql`이 로컬 DB에 Flyway로 적용되어 검증된 값이다
**대상 독자:** `api-ai-architect`(계약 확정), `rule-engine-dev`(정책 로드), `frontend-dev`(필드 참조), `integration-qa`(경계면 대조)

원본은 코드다 — `backend/src/main/resources/db/migration/V1__schema.sql`.
이 문서와 DDL이 어긋나면 DDL이 정답이다.

---

## 0. 명명 규칙

| 계층 | 규칙 | 예 |
|---|---|---|
| DB 컬럼 | snake_case | `review_status` |
| JPA 엔티티 필드 | camelCase + `@Column(name=…)` | `reviewStatus` |
| API JSON | camelCase (Jackson) | `reviewStatus` |

**한 곳만 다르다.** `inspection.ai_result` → 엔티티 필드 `aiResult` → **API 필드 `aiAssessment`**.
기획서 9.4 스키마와 8.4 예시가 `aiAssessment`로 고정돼 있어서다 (계약서 §2에 동일 기록).

---

## 1. 테이블별 컬럼 ↔ Java 필드

패키지: `com.skala.gateway.domain`

### `department` → `Department`

| DB 컬럼 | Java 필드 | 타입 | 제약 |
|---|---|---|---|
| `dept_id` | `deptId` | `Long` | PK, BIGSERIAL |
| `code` | `code` | `String` | UNIQUE, NOT NULL, varchar(20) |
| `name` | `name` | `String` | NOT NULL, varchar(50) |

### `app_user` → `AppUser`

| DB 컬럼 | Java 필드 | 타입 | 제약 |
|---|---|---|---|
| `user_id` | `userId` | `Long` | PK |
| `dept_id` | `department` | `Department` (`@ManyToOne`) | FK, **NOT NULL** |
| `name` | `name` | `String` | NOT NULL, varchar(50) |
| `email` | `email` | `String` | UNIQUE, varchar(100) |
| `role` | `role` | `UserRole` | NOT NULL, CHECK |
| `created_at` | `createdAt` | `OffsetDateTime` | NOT NULL, DEFAULT now() |

`dept_id`는 FK 객체로 매핑돼 있다. id만 필요하면 `user.getDepartment().getDeptId()`.

### `policy` → `Policy`

| DB 컬럼 | Java 필드 | 타입 | 제약 |
|---|---|---|---|
| `policy_id` | `policyId` | `Long` | PK |
| `code` | `code` | `String` | UNIQUE, NOT NULL, varchar(20) |
| `name` | `name` | `String` | NOT NULL, varchar(100) |
| `category` | `category` | `PolicyCategory` | NOT NULL, CHECK |
| `version` | `version` | `Integer` | NOT NULL, DEFAULT 1 |
| `is_active` | `isActive` | `Boolean` | **NOT NULL**, DEFAULT true |
| `scope` | `scope` | `PolicyScope` | NOT NULL, CHECK |
| `created_at` | `createdAt` | `OffsetDateTime` | NOT NULL, DEFAULT now() |

`appliedVia`(계약서 §3의 파생 필드)는 컬럼이 아니다. `scope`로 그대로 파생된다 — GLOBAL이면 전사 적용, DEPT면 매핑 적용.

**시드 version 값:** `P-PII=3`, `P-SEC=7`, `P-CONF=2`. 기획서 8.4 `policySnapshot` 예시와 계약서 §4 `policyVersion` 예시(`P-CONF:2;P-PII:3;P-SEC:7`)를 그대로 쓴 것이다.

### `policy_rule` → `PolicyRule`

| DB 컬럼 | Java 필드 | 타입 | 제약 |
|---|---|---|---|
| `rule_id` | `ruleId` | `Long` | PK |
| `policy_id` | `policy` | `Policy` (`@ManyToOne`) | FK, NOT NULL |
| `code` | `code` | `String` | UNIQUE, NOT NULL, varchar(30) |
| `rule_type` | `ruleType` | `RuleType` | NOT NULL, CHECK |
| `pattern` | `pattern` | `String` | NOT NULL, text. **API 미노출 (C5)** |
| `action` | `action` | `RuleAction` | NOT NULL, CHECK |
| `mask_label` | `maskLabel` | `String` | varchar(30). CHECK: `action='MASK'`면 필수 |
| `severity` | `severity` | `Severity` | NOT NULL, CHECK |
| `obligation` | `obligation` | `Obligation` | NOT NULL, CHECK |
| `source` | `source` | `String` | varchar(100) |
| `description` | `description` | `String` | varchar(200) |
| `is_active` | `isActive` | `Boolean` | **NOT NULL**, DEFAULT true |

### `department_policy` → `DepartmentPolicy`

복합 PK다. `@EmbeddedId DepartmentPolicyId(deptId, policyId)` + `@MapsId` 연관.

| DB 컬럼 | Java 필드 | 타입 |
|---|---|---|
| `dept_id` | `id.deptId` / `department` | `Long` / `Department` |
| `policy_id` | `id.policyId` / `policy` | `Long` / `Policy` |
| `applied_at` | `appliedAt` | `OffsetDateTime` |

### `message` → `Message`

| DB 컬럼 | Java 필드 | 타입 | 제약 |
|---|---|---|---|
| `message_id` | `messageId` | `Long` | PK |
| `user_id` | `user` | `AppUser` (`@ManyToOne`) | FK, NOT NULL |
| `original_text` | `originalText` | `String` | NOT NULL, text. **API 미노출** |
| `submitted_text` | `submittedText` | `String` | NULL 허용. **NULL = 마스킹본이 만들어진 적 없음** (0.5 D7) |
| `status` | `status` | `MessageStatus` | NOT NULL, CHECK |
| `created_at` | `createdAt` | `OffsetDateTime` | NOT NULL, DEFAULT now() |

### `inspection` → `Inspection`

| DB 컬럼 | Java 필드 | 타입 | 제약 |
|---|---|---|---|
| `inspection_id` | `inspectionId` | `Long` | PK |
| `message_id` | `message` | `Message` (`@ManyToOne`) | FK, NOT NULL |
| `phase` | `phase` | `InspectionPhase` | NOT NULL, DEFAULT 'INPUT', CHECK |
| `policy_snapshot` | `policySnapshot` | **`PolicySnapshot`** (JSONB) | NOT NULL |
| `rule_result` | `ruleResult` | **`RuleResult`** (JSONB) | NOT NULL |
| `ai_status` | `aiStatus` | `AiStatus` | NOT NULL, CHECK |
| `ai_result` | `aiResult` → API `aiAssessment` | **`AiAssessment`** (JSONB) | NULL 허용 |
| `final_decision` | `finalDecision` | `FinalDecision` | NULL 허용, CHECK |
| `decided_by` | `decidedBy` | `DecidedBy` | NULL 허용, CHECK |
| `created_at` | `createdAt` | `OffsetDateTime` | NOT NULL, DEFAULT now() |
| `completed_at` | `completedAt` | `OffsetDateTime` | NULL 허용 |

### `inspection_finding` → `InspectionFinding`

| DB 컬럼 | Java 필드 | 타입 | 제약 |
|---|---|---|---|
| `finding_id` | `findingId` | `Long` | PK |
| `inspection_id` | `inspection` | `Inspection` (`@ManyToOne`) | FK, NOT NULL |
| `source` | `source` | `FindingSource` | NOT NULL, CHECK |
| `rule_id` | `rule` | `PolicyRule` (`@ManyToOne`) | FK, NULL 허용. **API 미노출** |
| `code` | `code` | `String` | NOT NULL, varchar(30) |
| `category` | `category` | `PolicyCategory` | NOT NULL, CHECK |
| `span_start` | `spanStart` | `Integer` | NULL 허용 (AI 후보는 NULL) |
| `span_end` | `spanEnd` | `Integer` | NULL 허용 |
| `action` | `action` | `RuleAction` | NULL 허용 (AI 후보는 NULL), CHECK |
| `rationale` | `rationale` | `String` | text. 규칙 finding은 NULL |
| `evidence` | `evidence` | **`List<AiAssessment.Evidence>`** (JSONB) | NULL 허용 |
| `review_status` | `reviewStatus` | `ReviewStatus` | **NOT NULL, DEFAULT 'SUGGESTED', CHECK 4값** |
| `reviewed_by` | `reviewedBy` | `AppUser` (`@ManyToOne`) | FK, NULL 허용 |
| `reviewed_at` | `reviewedAt` | `OffsetDateTime` | NULL 허용 |

### `message.submitted_text`의 상태별 값 (0.5 D7·D14) — 시드 실측

| 상태 | `decided_by` | 값 | 건수 |
|---|---|---|---|
| ALLOWED | RULE | 원문 | 56 |
| MASKED | RULE | 마스킹본 | 25 |
| PENDING_REVIEW | (null) | **마스킹본** — D7. 비면 감사 콘솔 상세 패널이 빈다 | 8 |
| BLOCKED | RULE | **NULL** — D5로 마스킹을 실행하지 않아 본문이 없다 | 13 |
| BLOCKED | HUMAN | 마스킹본 — REVIEW 경로라 본문이 이미 만들어져 있었다 | 1 |

**`submitted_text IS NULL`의 의미는 "차단됨"이 아니라 "마스킹본이 생성된 적 없음"이며,
규칙 BLOCK 경로에서만 발생한다 (0.5 D14, 기획서 6.2).** 차단 사실은
`status`·`final_decision`이 기록한다.

**계약서 C4와의 관계:** DB에는 위대로 저장하고, `POST /messages`의 202 응답에서만
`submittedText`를 `null`로 내리는 것은 그대로 유효하다. `GET /inspections/{id}`는
저장된 값을 그대로 반환한다.

**PATCH는 `submitted_text`를 지우지 않는다 (D14 확정).** 사람이 ACCEPT해 BLOCK으로 확정해도
본문을 보존한다. 지우면 담당자가 방금 무엇을 보고 판단했는지가 사라져 "판단의 근거를 남긴다"는
서비스 핵심 가치(2.4)에 어긋나고, 데모 1:50에서 ACCEPT를 누르는 순간 상세 패널 본문이 사라진다.

**`integration-qa` 검증 불변식:** `BLOCKED ⇒ NULL`이 아니라
**`decided_by='RULE' AND status='BLOCKED' ⇒ submitted_text IS NULL`**이다.

**`review_comment` 컬럼은 없다.** 기획서 6.2에 없고 8.4 응답 예시에도 없다. PATCH의 `comment`는 수신만 하고 저장하지 않는다 (계약서 §2가 이미 같은 결론). 감사 증적에 코멘트가 필요하면 `V3__*.sql`로 추가한다.

---

## 2. enum 컬럼별 허용값 — CHECK 제약 전문

아래는 DB에 실제로 걸려 있는 값이다. 애플리케이션 enum과 1:1이며, 여기 없는 값은 INSERT가 거부된다.

| DB 컬럼 | Java enum (`com.skala.gateway.domain.enums`) | 허용값 (전체) |
|---|---|---|
| `app_user.role` | `UserRole` | `EMPLOYEE`, `SECURITY_ADMIN` |
| `policy.category` | `PolicyCategory` | `PII`, `SECRET`, `CONFIDENTIAL` |
| `policy.scope` | `PolicyScope` | `GLOBAL`, `DEPT` |
| `policy_rule.rule_type` | `RuleType` | `REGEX`, `KEYWORD` |
| `policy_rule.action` | `RuleAction` | `MASK`, `BLOCK`, `REVIEW` |
| `policy_rule.severity` | `Severity` | `HIGH`, `MEDIUM`, `LOW` |
| `policy_rule.obligation` | `Obligation` | `LEGAL`, `INTERNAL` |
| `message.status` | `MessageStatus` | `ALLOWED`, `MASKED`, `BLOCKED`, `PENDING_REVIEW` |
| `inspection.phase` | `InspectionPhase` | `INPUT`, `OUTPUT` |
| `inspection.ai_status` | `AiStatus` | `SKIPPED`, `PENDING`, `COMPLETED`, `FAILED` |
| `inspection.final_decision` | `FinalDecision` | `ALLOW`, `MASK`, `BLOCK`, `PENDING` (+ NULL) |
| `inspection.decided_by` | `DecidedBy` | `RULE`, `HUMAN` (+ NULL) |
| `inspection_finding.source` | `FindingSource` | `RULE`, `AI` |
| `inspection_finding.action` | `RuleAction` | `MASK`, `BLOCK`, `REVIEW` (+ NULL) |
| `inspection_finding.review_status` | `ReviewStatus` | `SUGGESTED`, `ACCEPTED`, `REJECTED`, `CONFIRMED` **(4값 — D6)** |

`department.code`는 enum이 아니라 마스터 데이터다. 시드 4행: `DEV`, `SALES`, `HR`, `INFOSEC` (0.5 D2).

**`Severity`의 상수 선언 순서가 곧 우선순위다.** `HIGH, MEDIUM, LOW` 순이라
`Comparator.comparing(PolicyRule::getSeverity)`가 기획서 7.4-4의 "severity 내림차순"을 그대로 준다.

### DB 수준에서 강제되는 것 (검증 완료)

| 제약 | 내용 | 확인 방법 |
|---|---|---|
| `chk_finding_review_status` | 4값 외 거부 | `review_status='APPROVED'` INSERT → 거부됨 |
| `review_status` DEFAULT | 미지정 INSERT가 `SUGGESTED`로 저장 | 실측 확인 |
| `chk_finding_ai_no_rule` | `source='AI'`면 `rule_id`는 NULL | rule_id 지정 INSERT → 거부됨 |
| `chk_rule_mask_label` | `action='MASK'`면 `mask_label` 필수 | 라벨 없는 MASK 규칙 → 거부됨 |

세 번째·네 번째는 기획서에 명시된 제약은 아니지만 각각 6.2("AI 후보는 NULL")와
7.5("BLOCK 규칙에는 mask_label이 정의되어 있지 않아 실행하면 오류")를 DB로 옮긴 것이다.

---

## 3. JSONB 3개 컬럼의 shape

문자열이 아니라 타입 있는 객체로 매핑돼 있다 (`@JdbcTypeCode(SqlTypes.JSON)`).
Hibernate ↔ Java 왕복이 실측 확인되었다.

### `inspection.policy_snapshot` → `com.skala.gateway.domain.jsonb.PolicySnapshot`

```json
{ "policies": [ { "policyId": 1, "code": "P-PII", "version": 3,
                  "ruleCodes": ["PII-CARD-02","PII-EMAIL-04","PII-PHONE-03","PII-RRN-01"] } ] }
```

```java
record PolicySnapshot(List<PolicyRef> policies) {
    record PolicyRef(Long policyId, String code, Integer version, List<String> ruleCodes) {}
}
```

**계약서 §1-4의 예시(`{code, version}`)보다 필드가 많다.** 기획서 7.4-3이 스냅샷에
`{policyId, version, ruleCodes[]}`를 기록하라고 해서다. API가 `{code, version}`만 노출하고
싶으면 응답 DTO에서 투영하면 되고, DB에는 전량 남긴다 — 스냅샷의 목적이 시점 보존이다.

### `inspection.rule_result` → `com.skala.gateway.domain.jsonb.RuleResult`

```json
{ "matches": [ { "code": "SEC-DBURL-02", "category": "SECRET", "action": "BLOCK",
                 "span": [18, 26], "matchedKeyword": null, "severity": "HIGH",
                 "obligation": "INTERNAL", "source": "정보보안규정 4.2" } ],
  "appliedRuleCodes": ["PII-CARD-02", "…"] }
```

```java
record RuleResult(List<RuleMatch> matches, List<String> appliedRuleCodes) {
    record RuleMatch(String code, PolicyCategory category, RuleAction action,
                     List<Integer> span, String matchedKeyword,
                     Severity severity, Obligation obligation, String source) {}
}
```

- `span`은 `[start, end)` 2원소, **원문 기준**이다 (D3).
- `matchedKeyword`는 REGEX 매칭에서 **JSON `null`로 남긴다** (키를 없애지 않는다 — 계약서 C3).
- 시드도 이 규칙을 지켜 REGEX 매칭 문자열을 JSONB에 남기지 않는다.

### `inspection.ai_result` → `com.skala.gateway.ai.AiAssessment`

`api-ai-architect`가 정의한 타입을 **그대로** 매핑했다. 저장용 타입을 따로 만들면
AI 스키마와 DB 스키마가 조용히 갈린다. `inspection_finding.evidence`는
`List<AiAssessment.Evidence>`다.

---

## 4. 인계 시그니처 (계약서 §4 인계 1) — 구현 완료

```java
// com.skala.gateway.domain.repository.PolicyRuleRepository
List<PolicyRule> findActiveById(…)  // ✗
List<PolicyRule> findActiveByDept(Long deptId);   // ✓ 계약서와 동일
```

반환 순서가 곧 기획서 7.4의 실행 순서다 — REGEX를 severity 내림차순으로 먼저, 그 다음
KEYWORD, 동률은 rule code 사전순.

실측 (`deptId=1`, 개발팀):
`PII-CARD-02/HIGH, PII-RRN-01/HIGH, SEC-AWSKEY-01/HIGH, SEC-DBURL-02/HIGH, PII-PHONE-03/MEDIUM, SEC-PRIVIP-03/MEDIUM, PII-EMAIL-04/LOW`

실측 (`deptId=2`, 영업팀): 위 7건 + `CONF-CLIENT-01`(KEYWORD, 마지막)

반환된 목록의 `code` 전체가 `ruleResult.appliedRuleCodes`가 된다.

### 그 밖에 제공되는 조회

| 메서드 | 용도 |
|---|---|
| `PolicyRepository.findActiveByDept(Long)` | `policySnapshot` 조립 (7.4-2) |
| `InspectionRepository.findDetailById(Long)` | 상세. message·user·department fetch join |
| `InspectionRepository.findAll(Specification, Pageable)` + `InspectionSpecs.of(deptId, status, from, to)` | 감사 목록. null 필터는 조건 자체를 만들지 않는다 |
| `InspectionSpecs.DEFAULT_SORT` | `createdAt DESC` 고정 |
| `InspectionFindingRepository.findByInspectionInspectionIdOrderBySourceAscFindingIdAsc(Long)` | 상세의 `findings[]` |
| `InspectionFindingRepository.countRuleFindings(Collection<Long>)` | 목록의 `ruleCount`. 페이지 단위 일괄 집계 |

`InspectionSpecs`를 쓴 이유는 §5 노트에 있다 — `(:param is null or …)` JPQL이
PostgreSQL에서 실제로 죽는다.
