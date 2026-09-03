---
name: rule-engine-impl
description: "사내 AI 게이트웨이의 규칙 판정 엔진을 구현하는 스킬. REGEX 6종·KEYWORD 2종 매칭, 중첩 span 억제, 충돌 해결(BLOCK>REVIEW>MASK>ALLOW), 마스킹, policy_snapshot 기록, POST /messages와 정책·감사 조회 API를 다룬다. 판정 로직·정규식 매칭·마스킹·충돌 해결·오탐/미탐·데모 케이스 판정 관련 작업에 반드시 사용. '판정이 이상해', '규칙 안 잡혀', '마스킹 위치', '엔진 수정' 같은 후속 요청에도 사용."
---

# Rule Engine — 판정 엔진 구현

기획서 7장(정책·규칙·충돌 해결)·8.4(응답 형식)가 원본이다. 규칙 14종의 패턴은 `policy_rule` 테이블에서 읽는다. 엠바고 규칙 2종은 `embargo_until`이 있고, 해제일이 지난 규칙은 매칭 대상에서 빠지되 `appliedRuleCodes`에는 남는다 (0.5 D20). **Java 상수로 하드코딩하지 않는다** — 하면 정책을 DB에 둔 이유가 사라지고 "정책·규칙·임계값은 DB"라는 Config Isolation 주장이 무너진다.

## 판정 파이프라인

이 순서가 곧 설계다. 순서를 바꾸면 결과가 달라진다.

```
1. 사용자 → 부서 조회
2. 정책 로드    GLOBAL 활성 전부 + department_policy 매핑된 DEPT 활성
3. 스냅샷 기록  policy_snapshot = {policyId, version, ruleCodes[]}
4. REGEX 실행   severity 내림차순, 전부 실행 (조기 종료 없음)
5. KEYWORD 실행 매칭 시 action=REVIEW finding
6. 중첩 억제    포함 관계 매칭 제거          ← Q1 결정
7. 충돌 해결    BLOCK > REVIEW > MASK > ALLOW
8. 마스킹       판정이 BLOCK이 아닐 때만 실행 ← Q5 결정
9. 저장·응답    200 / 202 / 403
```

### 4단계 — 조기 종료하지 않는 이유

BLOCK을 만나도 나머지 규칙을 전부 실행한다. 감사 기록의 목적이 "무엇이 걸렸는지 전부 남기는 것"이기 때문이다. 최종 판정만 필요하면 조기 종료가 빠르지만, 그러면 `finding` 테이블에 일부만 남아 사후 소명이 불가능해진다. 페르소나가 원하는 것은 차단이 아니라 가시성이다(2.3).

### 6단계 — 중첩 억제

매칭을 span 시작 오프셋 순으로 정렬하고, 앞선 매칭의 span에 **완전히 포함되는** 매칭은 finding을 만들지 않는다.

Case A의 `postgres://admin:p%40ss@10.0.3.21/prod`에서 SEC-DBURL-02가 전체를 먹고 SEC-PRIVIP-03(`10.0.3.21`)이 그 안에 들어간다. 억제하지 않으면 같은 문자열이 두 번 세어져 화면의 "규칙 N건"이 실제 위험 개수를 과장한다. 배경과 근거는 `spec-contract` 스킬의 `references/open-questions.md` Q1을 읽는다.

부분 겹침(포함 아님)은 억제하지 않는다. 7.6의 severity 규칙을 적용한다.

### 7단계 — 충돌 해결

| 매칭 조합 | 판정 | message.status | HTTP | submitted_text |
|---|---|---|---|---|
| 없음 | ALLOW | ALLOWED | 200 | 원문 그대로 |
| MASK만 | MASK | MASKED | 200 | 마스킹본 |
| BLOCK 포함 | BLOCK | BLOCKED | 403 | NULL |
| REVIEW 포함, BLOCK 없음 | PENDING | PENDING_REVIEW | 202 | 마스킹본 (MASK 동반 시) |

**BLOCK이면 AI를 호출하지 않는다.** 이 분기를 `ConflictResolver` 결과에서 명시적으로 만든다. 이유는 두 가지다 — 이미 확정된 위반에 모델 비용을 쓸 이유가 없고, 애초에 밖으로 보낼 텍스트가 없다(`submitted_text`가 NULL이다).

### 8단계 — 마스킹

판정이 BLOCK이면 마스킹을 **실행하지 않는다.** BLOCK 규칙에는 `mask_label`이 없어서(NULL) 무조건 실행하면 NPE가 난다. Case A가 데모 첫 케이스다.

치환 규칙(7.6):
- 치환 단위는 매칭 전체. 뒤 4자리 보존 같은 부분 마스킹은 하지 않는다
- 치환 문자열은 `mask_label` (`[주민번호]`, `[전화번호]`, `[카드번호]`, `[이메일]`, `[내부IP]`)
- 겹치는 구간은 severity 높은 규칙의 라벨. 동률이면 rule code 사전순
- **뒤에서 앞으로 치환한다.** 앞에서부터 치환하면 길이가 변해 뒤 매칭의 오프셋이 전부 밀린다

```java
matches.stream()
    .sorted(Comparator.comparingInt(Match::spanStart).reversed())
    .forEach(m -> sb.replace(m.spanStart(), m.spanEnd(), m.maskLabel()));
```

`span`은 **원문 기준**으로 finding에 저장한다. 마스킹본 기준 좌표는 저장하지 않는다. 화면 하이라이트는 FE가 라벨 문자열을 검색해서 처리한다(Q3 결정).

## 정규식 처리

### 패턴 컴파일 캐싱

`Pattern.compile()`을 요청마다 호출하면 낭비다. `rule_id → Pattern` 맵으로 캐싱하되, 정책이 바뀔 수 있으므로 `rule_id + updated_at`이 아니라 애플리케이션 기동 시 1회 로드 + 정책 조회 시 갱신 정도로 충분하다. 3일 범위에서 캐시 무효화 전략을 정교하게 만들 필요는 없다.

### 정규식이 기대와 다를 때

**패턴을 임의로 고치지 않는다.** 기획서 7.2의 패턴은 발표 자료에 그대로 실린다. 기대와 다르게 매칭되면 다음 순서로 원인을 좁힌다.

1. **시드 왕복 확인** — `SELECT pattern FROM policy_rule WHERE code='...'` 결과가 기획서 7.2와 문자 단위로 같은가. SQL 리터럴에서 백슬래시가 유실되는 것이 가장 흔한 원인이다
2. **Java 문자열 이스케이프** — DB에서 읽으면 이 문제는 없다. 하드코딩했다면 그것이 원인이다
3. **패턴 자체의 한계** — 이때만 `_workspace/02_rule-engine-dev_engine-notes.md`에 기대 vs 실제를 기록하고 `spec-steward`에게 판단을 요청한다

### KEYWORD 매칭

`pattern`이 쉼표 구분 문자열이다(`A사,B사,C사,프로젝트 오메가,차세대`). split 후 각 키워드를 `indexOf`로 찾는다. 매칭된 키워드는 `hits[]`에 `{keyword, ruleCode, source}`로 모아 `AiInspectionRequest`에 넘긴다 — 이것이 9.3 프롬프트 조립의 "참조 근거"이자 RAG 확장 지점이다.

대소문자·공백 정규화는 하지 않는다. 한글 키워드라 의미가 없고, 데모 문자열이 정확히 일치한다.

## API 구현

### POST /messages

인계 지점이 명확하다. **규칙 판정과 저장까지가 이 스킬의 범위**이고, REVIEW 판정 이후 `@Async` AiInspector 호출부터는 `ai-mock-contract` 스킬(`api-ai-architect`)의 범위다. 인계 시그니처는 `_workspace/01_api-ai-architect_contract-freeze.md` 표 4에 고정한다.

응답 본문은 기획서 8.4의 4가지 예시(ALLOW/MASK/BLOCK/REVIEW)를 그대로 따른다. 403도 판정 결과이므로 **에러 봉투가 아니라 판정 본문**을 반환한다. `{ code, message, details }` 형식은 400·404·409에만 쓴다.

### GET /policies?deptId=

부서에 적용되는 정책과 규칙을 반환한다. SCR-01 하단 캡션("부서: 개발팀 · 적용 정책 3건")이 이걸 쓴다. GLOBAL + 매핑된 DEPT를 합쳐 반환한다 — 부서 필터만 걸면 GLOBAL이 누락된다.

### GET /inspections (감사 목록)

`{ items, page, size, total }` 봉투로 반환한다. 배열을 직접 반환하면 FE가 `.items`를 꺼내지 않아 `filter is not a function`이 난다. 이것이 경계면 버그의 전형이다.

`ruleCount`는 `source='RULE'`인 finding 개수다. AI finding을 세면 안 된다(5.4 목록 컬럼 정의).

## 데모 케이스 고정 테스트

기획서 10.4의 입력 문자열을 **한 글자도 바꾸지 않고** 단위 테스트에 박는다. 이 테스트가 통과하지 않으면 데모가 실패한다.

| 케이스 | 계정 | 기대 판정 | 기대 규칙 | HTTP |
|---|---|---|---|---|
| A | 이OO (개발팀) | BLOCK | SEC-DBURL-02, PII-RRN-01 (2건, Q1) | 403 |
| B | 김OO (영업팀) | PENDING | CONF-CLIENT-01 | 202 |
| C | 이OO (개발팀) | ALLOW | 0건 | 200 |
| D | 정OO (인사팀) | MASK | PII-PHONE-03 | 200 |

B와 C는 **같은 문장**이다(`A사 차세대 프로젝트 오픈 일정이 언제였지?`). 부서만 다르고 결과가 갈리는 것이 부서↔정책 N:M 설계의 증명이며, 데모 2:40의 핵심 장면이다. 둘 다 통과해야 의미가 있다.

C가 ALLOW인 이유는 P-CONF가 `scope=DEPT`이고 DEV에 매핑되지 않아 규칙 자체가 로드되지 않기 때문이다. 개발팀이 해당 고객사 프로젝트의 수행 조직이라 업무상 논의가 필요하다는 것이 정책 근거다(7.1).

## 실패 시 원인 계층

데모 케이스가 기대와 다를 때 아래 순서로 좁힌다. 계층을 특정하지 않으면 담당자끼리 서로 미룬다.

1. **시드** — 규칙 14종이 다 들어갔나, 패턴이 온전한가, `department_policy` 4행이 있나
2. **정책 로드** — GLOBAL이 포함됐나, DEPT 매핑이 걸렸나
3. **매칭** — 정규식이 원문에서 실제로 매칭되나 (단독 테스트)
4. **중첩 억제** — 억제되면 안 될 매칭이 억제됐나
5. **충돌 해결** — 우선순위가 맞나
6. **마스킹** — 뒤에서 앞으로 치환했나
7. **응답 매핑** — DTO 필드명이 계약과 같나
