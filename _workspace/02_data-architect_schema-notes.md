# 스키마 설계 노트 — `data-architect`

**작성일:** 2026-09-02
**환경:** PostgreSQL 16.15 (Docker `gateway-pg`, `jdbc:postgresql://localhost:55432/gateway`)
**적용 상태:** Flyway V1·V2가 빈 스키마에서 성공 적용됨. `ddl-auto: validate` 통과, 앱 정상 기동

컬럼명·enum 값 목록은 `_workspace/01_data-architect_names.md`에 있다. 이 문서는 **왜 그렇게 했는지**와 **검증 결과**다.

---

## 1. 산출물

| 파일 | 내용 |
|---|---|
| `backend/src/main/resources/db/migration/V1__schema.sql` | Core 8 테이블 DDL, CHECK 제약, 인덱스 6개 |
| `backend/src/main/resources/db/migration/V2__seed.sql` | 부서 4 · 사용자 4 · 정책 3 · 규칙 8 · 매핑 2 · 감사 로그 100 · 데모 백업 3 |
| `backend/src/main/java/com/skala/gateway/domain/*.java` | 엔티티 8 + 복합 PK 클래스 1 |
| `backend/src/main/java/com/skala/gateway/domain/enums/*.java` | enum 14 |
| `backend/src/main/java/com/skala/gateway/domain/jsonb/*.java` | `PolicySnapshot`, `RuleResult` |
| `backend/src/main/java/com/skala/gateway/domain/repository/*.java` | Repository 8 + `InspectionSpecs` |
| `docs/erd.dbml` | dbdiagram.io 입력용. Core 8 + Future 4(Logical) |

Future 4 테이블(`attachment`, `knowledge_source`, `policy_audit`, `ai_provider_config`)은
**DDL을 만들지 않았다.** `docs/erd.dbml`에만 Logical로 표기했다 (기획서 6.1, 6.5).
부록 B의 DBML 초안에는 `ai_provider_config`가 빠져 있는데, 6.1·6.5 표에는 있어서 넣었다.

---

## 2. 검증 결과 — 기대 vs 실제

`db-schema-seed` 스킬의 "검증" 표를 전부 실행했다. **전 항목 일치.**

| 확인 | 기대 | 실제 |
|---|---|---|
| `count(department)` | 4 (DEV, SALES, HR, INFOSEC) | **4** ✓ |
| `count(app_user)` | 4 | **4** ✓ |
| `count(policy)` | 3 | **3** ✓ |
| `count(policy_rule)` | 8 | **8** ✓ |
| `count(department_policy)` | 2 | **2** — (SALES, P-CONF), (HR, P-CONF) ✓ |
| `message.status` 분포 (감사 로그 100건) | 55 / 25 / 12 / 8 | **ALLOWED 55, MASKED 25, BLOCKED 12, PENDING_REVIEW 8** ✓ |
| 부서 분포 (감사 로그 100건) | DEV 45 / SALES 35 / HR 20 | **45 / 35 / 20** ✓ |
| 백업 3건 포함 총계 | 103 | **ALLOWED 56, MASKED 25, BLOCKED 14, PENDING_REVIEW 8 = 103** ✓ |
| PENDING_REVIEW 8건의 `ai_status` | COMPLETED 5 / FAILED 3 | **5 / 3** ✓ |
| 정규식 왕복 (`SEC-PRIVIP-03` 외 7건) | 기획서 7.2와 문자 단위 일치 | **8건 전부 일치** ✓ |
| `submitted_text` (0.5 D7) | PENDING_REVIEW 8건 본문 있음, 규칙 BLOCK만 NULL | **ALLOWED 56·MASKED 25·PENDING_REVIEW 8 본문 있음, 규칙 BLOCK 13건만 NULL** ✓ |

### 정규식 왕복 검증 — 실제로 한 것

기획서 7.2 표에서 패턴 열을 파싱해(마크다운 표 이스케이프 `\|` → `|` 복원) DB 값과
문자열 비교했다. 8건 전부 바이트 단위로 일치한다. 예:

```
SEC-PRIVIP-03  \b(10\.\d{1,3}|192\.168|172\.(1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b
```

백슬래시가 살아남는 이유는 PostgreSQL의 `standard_conforming_strings`가 기본 `on`이라
일반 문자열 리터럴(`'…'`)에서 `\`가 보존되기 때문이다. **`E'…'`를 쓰면 `\d`가 `d`로 유실된다.**
V2에 `E''` 리터럴은 한 개도 없다.

### DB 제약이 실제로 막는지 (트랜잭션 롤백으로 실측)

| 시도 | 결과 |
|---|---|
| `review_status` 미지정 INSERT | `SUGGESTED`로 저장됨 ✓ (D6 기본값이 DB에서 강제됨) |
| `review_status = 'APPROVED'` INSERT | `chk_finding_review_status` 위반으로 거부 ✓ |
| `source='AI'` + `rule_id=1` INSERT | `chk_finding_ai_no_rule` 위반으로 거부 ✓ |
| `action='MASK'` + `mask_label` 없는 규칙 INSERT | `chk_rule_mask_label` 위반으로 거부 ✓ |

### JSONB 왕복 (Hibernate ↔ PostgreSQL)

임시 `CommandLineRunner`로 실제 조회해 확인한 뒤 삭제했다.

- `policy_snapshot` → `PolicySnapshot[policies=[PolicyRef[policyId=1, code=P-PII, version=3, ruleCodes=[…]]]]`
- `rule_result` → `RuleResult[matches=[RuleMatch[…, span=[18, 26], matchedKeyword=null, …]], appliedRuleCodes=[…]]`
- `ai_result` → `AiAssessment[riskCandidates=[RiskCandidate[…, evidence=[Evidence[…]]]], missingContext=[…], reviewRequired=true]`
- `inspection_finding.evidence` → `[Evidence[source=고객사 NDA 목록 v3, excerpt=…]]`

문자열이 아니라 타입 있는 객체로 왕복한다. 이것이 "AI 스키마가 확장돼도 컬럼 추가가 없다"는
Structured Data 원칙을 코드가 증명하는 자리다.

### Flyway 적용 절차

작업 중에는 `psql`로 직접 적용해 문법을 확인했고, 그 다음 `DROP SCHEMA public CASCADE`로
비운 뒤 `./gradlew bootRun`으로 Flyway가 처음부터 적용하는 것을 확인했다.

```
Migrating schema "public" to version "1 - schema"
Migrating schema "public" to version "2 - seed"
Successfully applied 2 migrations to schema "public", now at version v2
Started GatewayApplication in 2.275 seconds
```

`flyway_schema_history` 2행 전부 `success=t`. `ddl-auto: validate`가 통과했으므로
**엔티티 8개와 DDL이 컬럼 단위로 일치한다** — 이게 사실상 자동 대조다. bootRun은 확인 후 종료했다.

---

## 3. 설계 결정과 근거

### 3.1 CHECK 제약을 기획서보다 넓게 걸었다

기획서 6.2가 명시한 CHECK는 `review_status` 하나다. 나머지 enum 컬럼도 전부 걸었다.
enum을 애플리케이션에만 두면 엔티티를 우회한 INSERT(시드 SQL, 수동 패치, 다른 에이전트의
JDBC 코드) 한 번에 잘못된 값이 들어가고, 감사 데이터에서 그건 조용한 오염이다.

기획서에 없지만 추가한 제약 2개:

| 제약 | 근거 | 위험 |
|---|---|---|
| `chk_rule_mask_label` — `action='MASK'`면 `mask_label` 필수 | 7.5 "BLOCK 액션 규칙에는 mask_label이 정의되어 있지 않아 실행하면 오류" | 한 방향만 강제했다. BLOCK/REVIEW 규칙에 라벨을 다는 것은 막지 않는다 |
| `chk_finding_ai_no_rule` — `source='AI'`면 `rule_id` NULL | 6.2 "AI 후보는 NULL" | 역방향(`RULE`이면 `rule_id` 필수)은 **걸지 않았다.** 규칙 엔진이 rule_id 없이 finding을 만드는 경로를 막고 싶지 않아서다 |

**`source='RULE'`이면 `review_status='CONFIRMED'`는 CHECK로 걸지 않았다.**
의미상 참이지만(6.2, D6), 규칙 엔진이 DEFAULT에 기대어 INSERT한 뒤 UPDATE하는 구현을
쓰면 INSERT가 즉시 죽는다. 3일 스프린트에서 병렬 작업 중인 에이전트를 막을 위험이
얻는 것보다 크다. **엔티티의 `InspectionFinding.ofRule(...)` 팩토리가 `CONFIRMED`를
박아서 만든다** — 그 경로를 쓰면 규약이 지켜진다.

### 3.2 NOT NULL을 기획서보다 조인다

기획서 표에 제약이 안 적힌 컬럼 중 아래를 NOT NULL로 했다. 전부 시드·API 경로에서
항상 값이 있는 것들이다.

`app_user.dept_id`, `policy.is_active`, `policy_rule.is_active`, 모든 FK(`policy_rule.policy_id`,
`message.user_id`, `inspection.message_id`, `inspection_finding.inspection_id`),
모든 `created_at`/`applied_at`.

nullable로 두면 `is_active IS NOT FALSE` 같은 3값 논리가 정책 로드 쿼리에 번지고,
그 쿼리가 규칙 엔진의 입구다.

### 3.3 인덱스 6개

| 인덱스 | 근거 |
|---|---|
| `idx_inspection_created_at (created_at DESC)` | 감사 콘솔 목록의 고정 정렬 (5.4) |
| `idx_inspection_message (message_id)` | 목록·상세가 항상 message로 조인한다 |
| `idx_message_user_status (user_id, status)` | 부서 필터가 user를 거치고, status 필터가 뒤따른다 |
| `idx_finding_inspection (inspection_id)` | 상세의 `findings[]`, 목록의 `ruleCount` |
| `idx_policy_rule_policy (policy_id)` | 정책 로드 조인 |
| `idx_app_user_dept (dept_id)` | 부서 필터 조인 |

103행 규모에서는 전부 seq scan이 더 빠르다. 설계 의도를 남기려고 만들었고,
그 사실을 여기 적어둔다 — 발표에서 "인덱스 있습니다"가 아니라 "이 조회 패턴에
이 인덱스입니다"로 말하기 위함이다.

PostgreSQL은 UNIQUE와 PK에 자동으로 인덱스를 만들므로 `code` 계열은 따로 만들지 않았다.

### 3.4 시드의 ID를 명시 지정했다

마스터·감사 로그·데모 백업 전부 PK를 SQL에 박고, 파일 끝에서 `setval`로 시퀀스를 맞춘다.

`RESTART IDENTITY`만 믿으면 INSERT 순서 변화에 ID가 밀린다. 데모 URL(`/inspections/{id}`)과
`rule_id` 참조(감사 로그 finding이 규칙 1~8을 가리킨다)가 그것에 걸려 있다.
`setval`을 빼먹으면 첫 API 호출이 duplicate key로 죽는다 — V2 §8이 그 자리다.

배정: 마스터 1~N / 감사 로그 message·inspection 1~100 / 데모 백업 101~103.
`message_id = inspection_id`라 디버깅 때 눈으로 따라가기 쉽다.

### 3.5 감사 로그 100건의 분포를 만든 방법

상태는 연속 블록(1~55 ALLOWED, 56~80 MASKED, 81~92 BLOCKED, 93~100 PENDING_REVIEW)이다.
부서를 단순 modulo로 배정하면 상태와 부서가 상관돼 특정 상태에 특정 부서가 몰린다.
`(i*37) % 92` 순열의 순위로 잘라 흩었다.

`PENDING_REVIEW` 8건은 **SALES/HR로 고정**했다. P-CONF가 개발팀에 적용되지 않으므로
(7.3) DEV 행에 `CONF-CLIENT-01` REVIEW가 걸리면 매트릭스와 모순되는 데이터가 된다.
`ai_status`(COMPLETED 5 / FAILED 3)는 부서와 상관되지 않게 배정했다.

`BLOCKED` 12건의 규칙은 `SEC-AWSKEY-01`/`SEC-DBURL-02` 2종에서만 고른다.
10.5는 "BLOCKED는 SEC 규칙 중 랜덤 1건"이라고 했지만 `SEC-PRIVIP-03`은 action이 MASK라
BLOCKED 판정의 근거가 될 수 없다.

`created_at`은 10.5 그대로 `now() - (random() * interval '7 days')`다. 랜덤이라
`message`와 `inspection`이 같은 값을 써야 해서, 파생값을 헬퍼 테이블에 한 번 계산해
고정한 뒤 각 테이블에 INSERT한다. 두 문장에서 각각 `random()`을 부르면 시각이 어긋난다.
헬퍼 테이블 2개는 마이그레이션 끝에서 DROP한다.

### 3.6 시드에 개인정보 형태 문자열이 없다

감사 로그 100건의 `original_text`는 처음부터 라벨이 적용된 상태다
(`… (참고 정보: [전화번호])`). 20개 템플릿은 전부 평범한 업무 프롬프트다.

`matchedKeyword`도 KEYWORD 매칭에만 값을 넣는다. REGEX 매칭 문자열을 넣으면
주민번호 원문이 `rule_result` JSONB에 그대로 남는다 (계약서 §4가 같은 규칙을 명시).

---

## 4. 판단이 필요했던 지점 — 기획서와 어긋나거나 모호했던 것

### 4.1 데모 백업 Case A의 `original_text`를 라벨 적용본으로 저장했다

10.4가 Case A의 입력 문자열을 그대로 명시하는데(`… postgres://admin:p%40ss@10.0.3.21/prod …
주민번호 900101-1234567 …`), 10.1·10.5와 에이전트 정의는 "시드에 실제 개인정보 형태
문자열을 넣지 않는다"고 한다. 정면으로 부딪힌다.

**라벨 적용본을 저장했다:**
`이 에러 좀 봐줘. DB_URL=[DB접속정보] 로 붙었는데 담당자 주민번호 [주민번호] 기준으로 조회하면 타임아웃 나`

근거: `original_text`는 화면 미노출(6.2)이고 Case A의 `submitted_text`는 NULL이다.
감사 콘솔이 보여주는 것 — 상태 `BLOCKED`, 규칙 2건, 코드 `SEC-DBURL-02`/`PII-RRN-01`,
`decidedBy=RULE` — 은 실시간 입력과 완전히 같다. 백업 레코드의 목적(현장 실패 시 같은
판정을 보여주기)은 그대로 달성된다.

**부작용:** `rule_result`의 `span` 값이 8.4 예시(`[12,52]`, `[62,76]`)와 다르다.
저장된 텍스트에서 실제로 계산한 `[18,26]`, `[43,49]`다. 8.4의 값은 원문 기준이므로
실시간 입력 결과와는 다를 수 있다 — 백업 레코드의 span을 테스트 기대값으로 쓰지 말 것.

### 4.2 `CONF-CLIENT-01`이 Case B 문장에 **2번** 매칭된다 → 0.5 D9로 결정됨

**D9: finding은 규칙당 1건, `hits[]`는 키워드당 1건.** 시드가 이미 그렇게 들어가 있어
수정할 것이 없다 (백업 레코드 102의 RULE finding 1건, `matches` 1건). 아래는 발견 경위다.

`A사 차세대 프로젝트 오픈 일정이 언제였지?`에 키워드 `A사`와 `차세대`가 **둘 다** 있다.
서로 포함 관계가 아니라 D1 중첩 억제로 걸러지지 않는다. 규칙 엔진을 곧이곧대로 짜면
matches 2건 · RULE finding 2건이 나온다.

**시드 백업 레코드는 1건(`A사`, span [0,2])으로 넣었다.** 기획서 8.4의 202 응답 예시가
`A사` 하나만 싣고, 계약서도 그 예시를 인용하기 때문이다.

**D9가 규칙당 1건으로 확정했다.** `hits[]`(AI 입력)만 키워드당 1건이므로
`AiInspectionRequest.hits`는 `A사`·`차세대` 2건, `inspection_finding`과
`ruleResult.matches`는 1건이다. 감사 목록의 `ruleCount`가 규칙 단위여야 5.4 도해와 맞는다.

### 4.3 Case A에서 억제되는 매칭은 `SEC-PRIVIP-03` 하나가 아니다 → 0.5 D11로 결정됨

**D11: 원시 4건 → 억제 2건. `PII-EMAIL-04`도 억제 대상.** 아래 실측이 그 근거로 채택되어
기획서 10.4에 반영되었다. 단위 테스트 기대값을 이 4건 → 2건 과정으로 고정한다.

10.4는 `SEC-PRIVIP-03`만 억제 대상으로 언급한다. 실제로 8개 패턴을 Case A 입력에
돌려보면 **`PII-EMAIL-04`도 매칭된다** — `40ss@10.0.3.21` (`p%40ss@10.0.3.21`의 일부).

```
SEC-DBURL-02    [18, 56]  postgres://admin:p%40ss@10.0.3.21/prod
PII-RRN-01      [73, 87]  900101-1234567
SEC-PRIVIP-03   [42, 51]  10.0.3.21          ← [18,56]에 포함 → 억제
PII-EMAIL-04    [37, 51]  40ss@10.0.3.21     ← [18,56]에 포함 → 억제
```

D1 중첩 억제를 제대로 구현하면 **둘 다 억제되어 결과는 2건**이다.
"억제되는 건 사설 IP 하나"라고 알고 짜면 이메일 매칭이 살아남아 3건이 되고,
발표 대사와 QA 기대값("2건")이 깨진다.

### 4.4 `policy.version`을 1이 아니라 3 / 7 / 2로 넣었다

기획서 7.1·10.3에 version 값이 없고 DDL DEFAULT는 1이다. 하지만 8.4의
`policySnapshot` 예시가 `P-PII:3`, `P-SEC:7`, `P-CONF:2`이고, 계약서 §4의
`policyVersion` 예시 문자열 `P-CONF:2;P-PII:3;P-SEC:7`이 그 값들로 조립된다.
Mock의 결정론적 픽스처가 이 문자열에 걸려 있어서 문서의 예시와 맞췄다.

### 4.5 `ai_status=FAILED`의 `completed_at`을 채웠다

6.2는 `completed_at`을 "AI 완료 또는 사람 확정 시각"이라고만 한다. FAILED는 완료가
아니므로 NULL이 자연스럽지만, 계약서 §1-5의 `aiStatus`별 표가 FAILED의 `completedAt`을
**"실패 시각"**으로 못 박았다. NULL이면 FE가 PENDING과 FAILED를 구분하지 못한다.
계약을 따라 채웠다 (`created_at + 10초`).

### 4.6 `submitted_text`의 상태별 규약 — 0.5 D7 반영

D7: **`submitted_text`는 최종 판정이 BLOCK일 때만 NULL이다. PENDING_REVIEW 행도 마스킹본을 채운다.**

| 상태 | 값 | 근거 |
|---|---|---|
| ALLOWED | 원문 | 7.5 |
| MASKED | 마스킹본 | 7.5 |
| BLOCKED (규칙 판정) | **NULL** | 7.5. D5로 마스킹 자체를 실행하지 않아 만들어진 본문이 없다 |
| BLOCKED (사람 확정) | 마스킹본 | 아래 참조 |
| PENDING_REVIEW | 마스킹본 | **D7.** 이 8건이 감사 콘솔에서 ACCEPT/REJECT를 눌러볼 유일한 대상이라 비면 상세 패널에 보여줄 게 없다 |

감사 로그 100건은 처음부터 D7을 만족하고 있었다 — 생성 로직이
`CASE WHEN status='BLOCKED' THEN NULL ELSE original_text END`이라 PENDING_REVIEW 8건에
마스킹본이 들어간다. 실측: ALLOWED 55 / MASKED 25 / PENDING_REVIEW 8 전부 본문 있음,
BLOCKED 12만 NULL. **수정한 것은 데모 백업 Case B 한 행이다.**

#### Case B(message 102, 사람이 ACCEPT해 BLOCKED가 된 건)를 NULL에서 마스킹본으로 바꿨다

**→ 0.5 D14로 확정됨. 기획서 6.2·7.5·8.4에 반영되어 있다.** 아래는 그 결정의 근거다.

D7의 문구는 "BLOCK일 때만 NULL"이다. 이걸 **"NULL이 허용되는 조건이 BLOCK"**(필요조건)으로
읽었지 **"BLOCK이면 반드시 NULL"**(필요충분)로 읽지 않았다. 근거 셋:

1. **D7의 취지 그대로다.** 리더가 든 이유가 "감사 담당자가 검토해야 할 바로 그 건의 본문이
   비면 SCR-02 상세 패널에 보여줄 게 없다"였다. Case B는 실시간 입력 실패 시 감사 콘솔에서
   대신 보여줄 백업 레코드다. NULL이면 D7이 막으려던 상황이 그대로 생긴다.
2. **D5의 메커니즘상 본문이 실제로 존재한다.** 규칙 BLOCK은 마스킹을 아예 실행하지 않아
   본문이 없다. REVIEW 경로는 마스킹을 실행한 뒤 AI를 호출하므로 본문이 이미 만들어져 있고,
   담당자는 그 본문을 보고 ACCEPT한 것이다. 확정 순간에 그걸 지우면 판단 근거가 사라진다.
3. **6.2의 정의와 맞는다.** "NULL이면 미전송"이지 "NULL이면 차단"이 아니다. 차단 사실은
   `status`·`final_decision`이 기록한다.

즉 `submitted_text IS NULL`의 의미는 **"마스킹본이 만들어진 적이 없다"**이며,
그건 규칙 BLOCK 경로에서만 발생한다.

D14가 여기에 근거 하나를 더 얹었다 — **확정 시점에 본문을 지우는 것은 감사 시스템이
증적을 파기하는 것이다.** "판단의 근거를 남김"(2.4)이 이 서비스의 핵심 가치인데,
담당자가 방금 그 텍스트를 보고 내린 판단의 대상을 삭제하면 사후 소명이 불가능해진다.

**검증 불변식 (integration-qa용):** `BLOCKED ⇒ NULL`이 아니다.
`decided_by='RULE' AND status='BLOCKED' ⇒ submitted_text IS NULL` (13건).
사람 확정 BLOCK 1건은 본문을 갖는다.

**PATCH 구현 (api-ai-architect용):** ACCEPT 시 `submitted_text`를 건드리지 않는다.

### 4.7 `department_policy` 조회에서 INFOSEC은 GLOBAL 정책만 받는다

7.3이 "정보보안팀도 GLOBAL 정책은 성질상 적용된다"고 해서, `findActiveByDept(4)`는
P-PII·P-SEC 7개 규칙을 반환한다. 매핑 테이블에는 여전히 행이 없다 (D2).
실제로는 프롬프트를 제출하지 않아 판정이 일어나지 않는다.

---

## 5. 구현 중 막힌 것과 해결

### `(:param is null or …)` JPQL이 PostgreSQL에서 죽는다

감사 목록의 선택 필터를 아래처럼 짰다가 실패했다.

```
and (:from is null or i.createdAt >= :from)
```

```
ERROR: could not determine data type of parameter $5
```

`OffsetDateTime` null을 바인딩하면 드라이버가 타입 OID를 못 보내고, PostgreSQL이
`$5 IS NULL`의 타입을 결정하지 못한다. `deptId`(bigint)·`status`(varchar)는 통과하는데
타임스탬프만 죽어서 더 헷갈린다.

**해결:** `JpaSpecificationExecutor` + `InspectionSpecs.of(deptId, status, from, to)`.
null인 필터는 Specification을 아예 만들지 않으므로 null 파라미터가 SQL에 나가지 않는다.
목록에는 `@EntityGraph(attributePaths = {"message", "message.user", "message.user.department"})`를
붙였다 — 없으면 20행 목록에서 부서·사용자 조회가 행마다 나간다.

`api-ai-architect`가 컨트롤러에서 쓸 형태:

```java
Page<Inspection> page = inspectionRepository.findAll(
        InspectionSpecs.of(deptId, status, from, to),
        PageRequest.of(page, size, InspectionSpecs.DEFAULT_SORT));
```

---

## 6. 미완료 / 다른 에이전트가 이어야 할 것

| 항목 | 담당 | 내용 |
|---|---|---|
| `docs/erd.dbml` → 이미지 | `spec-steward` 또는 나 | dbdiagram.io에 붙여넣어 PNG export. 부록 C 제출물 |
| 4.3 Case A 억제 구현 | `rule-engine-dev`, `integration-qa` | D11이 "원시 4건 → 억제 2건"으로 확정. `PII-EMAIL-04`도 억제돼야 2건이 된다 |
| 4.6 PATCH 시 `submitted_text` | `api-ai-architect` | D14 확정 — ACCEPT가 `submitted_text`를 지우지 않는다 |
| 4.6 검증 불변식 | `integration-qa` | `BLOCKED ⇒ NULL`이 아니라 `decided_by='RULE' AND BLOCKED ⇒ NULL` |

4.2(KEYWORD 다중 매칭)와 4.6(D7 해석)은 각각 **D9**, **D14**로 결정되어 닫혔다.
시드는 두 결정 모두 이미 만족한다.
| `review_comment` 컬럼 | `api-ai-architect` 요청 시 | 기획서 6.2에 없어 만들지 않았다. 필요하면 `V3__*.sql` |
| 법령 조문 번호 | `spec-steward` | 7.2가 "국가법령정보센터에서 최종 확인 후 확정"이라고 유보했다. 시드는 `개인정보보호법 제24조`(RRN)와 `개인정보보호법`(나머지)로 넣었다 |

---

## 7. 재작업 시 규칙

`V1`은 이미 로컬 DB에 적용됐다. 상황별로 다르다 (`db-schema-seed` 스킬).

- **로컬에만 적용된 지금** → `V1`을 고치고 `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` 후 재적용. 팀원에게 통보
- **공유 DB에 적용된 뒤** → `V3__*.sql`로 변경분 추가. `V1` 불변

시드만 다시 넣고 싶으면 V2를 그대로 실행하면 된다 — `TRUNCATE … RESTART IDENTITY CASCADE`로
시작하므로 재실행 가능하다.

```
docker exec -i gateway-pg psql -U gateway -d gateway -v ON_ERROR_STOP=1 \
  < backend/src/main/resources/db/migration/V2__seed.sql
```
