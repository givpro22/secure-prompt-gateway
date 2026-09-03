---
name: db-schema-seed
description: "사내 AI 게이트웨이의 PostgreSQL 스키마·Flyway 마이그레이션·JPA 엔티티 8종·시드 데이터(부서/사용자/정책 4종/규칙 14종/감사 로그 105건)를 구현하는 스킬. ERD·DDL·엔티티·시드·마이그레이션·JSONB 매핑·dbdiagram DBML 작업 시 반드시 사용. '테이블 만들어', '엔티티 짜줘', '시드 데이터', '스키마 수정', 'ERD 다시', '마이그레이션 추가' 같은 후속 요청에도 사용."
---

# DB Schema & Seed — 데이터 계층 구현

기획서 6장(테이블 상세)·부록 B(DBML)·10장(시드)이 원본이다. 이 스킬은 **절차와 함정**만 담는다. 컬럼 정의는 기획서를 직접 읽는다.

## 구현 순서

순서를 지키는 이유는 뒤 단계가 앞 단계의 산출물에 의존하기 때문이다. 엔티티를 먼저 짜면 DDL과 어긋난다.

1. **DDL** `V1__schema.sql` — Core 8 테이블만. Future 4개는 생성하지 않는다
2. **DBML** `docs/erd.dbml` — 부록 B를 기준으로, V1과 일치하는지 대조. dbdiagram.io에 붙여 이미지 export
3. **엔티티** — DDL을 보고 작성. 8개 = 8 테이블
4. **시드** `V2__seed.sql` — 마스터(부서·사용자·정책·규칙·매핑) → 감사 로그 100건 순
5. **검증** — Flyway 적용 후 각 테이블 count와 기대값 대조

## 테이블별 함정

| 테이블 | 함정 |
|---|---|
| `department` | 정보보안팀 코드가 기획서에 없다. `spec-contract`의 미결 항목 Q2 결정을 따른다 (권고: `INFOSEC`) |
| `policy` | `scope`가 GLOBAL이면 `department_policy` 행을 만들지 않는다. 만들면 부서 추가 시 누락이 생기는 구조로 되돌아간다 (6.4) |
| `department_policy` | PK는 복합키 `(dept_id, policy_id)`. `@IdClass` 또는 `@EmbeddedId`가 필요하다. 서러게이트 PK를 추가하면 중복 매핑을 막지 못한다 |
| `policy_rule` | `pattern`이 TEXT다. 정규식 백슬래시가 SQL 리터럴에서 이스케이프되는 것에 주의. PostgreSQL은 `E''` 문자열에서만 백슬래시를 이스케이프하므로 일반 `''` 리터럴을 쓴다 |
| `message` | `submitted_text`는 NULL 허용. BLOCK 판정이면 NULL이다 |
| `inspection` | JSONB 3개. `policy_snapshot`·`rule_result`는 NOT NULL, `ai_result`만 NULL 허용 |
| `inspection_finding` | `review_status` DEFAULT `'SUGGESTED'` + CHECK 제약. 값은 4개 — `spec-contract` 미결 항목 Q6 참조 |

## JSONB 매핑

Spring Boot 3.3 + Hibernate 6에서는 별도 라이브러리 없이 매핑된다.

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private PolicySnapshot policySnapshot;
```

문자열(`String`)로 받으면 안 된다. 저장은 되지만 `->>` 연산자로 조회할 때 타입이 어긋나고, "AI 스키마가 확장돼도 컬럼 추가가 없다"(Structured Data 원칙)는 주장이 코드로 증명되지 않는다.

DTO는 `record`로 정의한다. `PolicySnapshot(List<PolicyRef> policies)`, `RuleResult(List<Match> matches, List<String> appliedRuleCodes)`.

## CHECK 제약으로 강제할 것

enum 값을 애플리케이션에만 두면 DB에 잘못된 값이 들어간다. 아래는 DB에서 막는다.

```sql
CONSTRAINT chk_finding_review_status CHECK (review_status IN ('SUGGESTED','ACCEPTED','REJECTED','CONFIRMED')),
CONSTRAINT chk_inspection_ai_status  CHECK (ai_status IN ('SKIPPED','PENDING','COMPLETED','FAILED')),
CONSTRAINT chk_message_status        CHECK (status IN ('ALLOWED','MASKED','BLOCKED','PENDING_REVIEW')),
CONSTRAINT chk_policy_scope          CHECK (scope IN ('GLOBAL','DEPT')),
CONSTRAINT chk_rule_action           CHECK (action IN ('MASK','BLOCK','REVIEW'))
```

`review_status`의 DEFAULT `'SUGGESTED'`는 특히 중요하다. AI 후보가 사람 확정 없이 효력을 갖지 못하도록 **DB 수준에서** 보증하는 장치이며, 이것이 4장 책임 경계 설계의 세 강제 지점 중 하나다.

## 인덱스

감사 콘솔 목록이 `deptId`·`status`·기간으로 필터링하고 `created_at` 역순 정렬한다(5.4). 100건에서는 없어도 되지만, 설계 의도를 보이기 위해 만든다.

```sql
CREATE INDEX idx_inspection_created_at ON inspection (created_at DESC);
CREATE INDEX idx_message_user_status   ON message (user_id, status);
CREATE INDEX idx_finding_inspection    ON inspection_finding (inspection_id);
```

## 시드 작성

### 재실행 가능성

```sql
TRUNCATE inspection_finding, inspection, message,
         department_policy, policy_rule, policy, app_user, department
  RESTART IDENTITY CASCADE;
```

FK 역순으로 나열한다. `RESTART IDENTITY`가 없으면 재실행 때마다 ID가 밀려 데모 URL(`/inspections/2090`)이 달라진다.

### 마스터 데이터

기획서 7.1(정책 4종)·7.2(규칙 14종)·7.3(매핑)·10.2(사용자 5명)를 그대로 INSERT한다. V2가 정책 3종·규칙 8종까지, V3가 P-EMBARGO와 엠바고 규칙 2종·홍보팀을 더한다. `department_policy`는 `(SALES, P-CONF)`, `(HR, P-CONF)` 2행뿐이다. GLOBAL 정책은 매핑하지 않는다.

### 감사 로그 100건 (10.5)

분포를 지킨다: 상태 ALLOWED 55 / MASKED 25 / BLOCKED 12 / PENDING_REVIEW 8, 부서 DEV 45 / SALES 35 / HR 20.

PENDING_REVIEW 8건은 다시 갈린다 — 5건은 `ai_status=COMPLETED` + AI finding `SUGGESTED`(검토 대기 데모용), 3건은 `ai_status=FAILED`(실패 경로 표시용). 이 8건이 없으면 감사 콘솔에서 ACCEPT/REJECT를 눌러볼 대상이 없다.

**시드에 실제 개인정보 형태 문자열을 넣지 않는다.** `original_text`도 이미 `[주민번호]` 라벨이 적용된 상태로 저장한다. 시드는 감사 화면을 채우는 것이 목적이고, 판정 로직 증명은 데모 케이스가 실시간으로 한다.

`generate_series(1, 100)`와 템플릿 20개 배열에서 `(random()*20)::int` 인덱싱으로 생성한다. 시각은 `now() - (random() * interval '7 days')`.

### 데모 백업 레코드

기획서 10.1이 요구한다 — 데모 케이스 A·B·C와 같은 결과를 가진 완료 상태 레코드를 1건씩 추가로 넣는다. 현장에서 실시간 입력이 실패해도 감사 콘솔에서 같은 판정을 보여줄 수 있다. 100건과 별개로 3건이다.

## 검증

Flyway 적용 후 아래를 확인하고 결과를 `_workspace/02_data-architect_schema-notes.md`에 기록한다.

| 확인 | 기대 |
|---|---|
| `SELECT count(*) FROM department` | 4 (DEV, SALES, HR, INFOSEC) |
| `SELECT count(*) FROM policy` | 3 |
| `SELECT count(*) FROM policy_rule` | 8 |
| `SELECT count(*) FROM department_policy` | 2 |
| `SELECT count(*) FROM app_user` | 4 |
| `SELECT status, count(*) FROM message GROUP BY status` | 55/25/12/8 (+백업 3건) |
| `SELECT ai_status, count(*) FROM inspection GROUP BY ai_status` | PENDING_REVIEW 8건이 COMPLETED 5 / FAILED 3 |
| 정규식 왕복 | `SELECT pattern FROM policy_rule WHERE code='SEC-PRIVIP-03'` 결과가 기획서 7.2와 문자 단위로 일치 |

마지막 항목이 중요하다. SQL 리터럴에서 백슬래시가 하나 유실되면 정규식이 조용히 다르게 동작하고, `rule-engine-dev`는 자기 코드를 의심하게 된다.

## 마이그레이션 수정 규칙

이미 적용된 `V1`을 고치면 Flyway checksum이 깨진다. 상황별로 다르다.

- **아직 아무 DB에도 적용 안 됨** → `V1`을 직접 고치고 노트에 기록
- **로컬에만 적용됨** → `flyway clean` 후 재적용. 팀원에게 통보
- **공유 DB에 적용됨** → `V3__*.sql`로 변경분 추가. `V1` 불변
