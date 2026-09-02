# 판정 엔진 구현 노트 — `rule-engine-dev`

**작성일:** 2026-09-02
**상태:** 엔진·서비스·컨트롤러 구현 완료. 단위/통합 테스트 28건 통과. 실제 HTTP로 데모 A·B·C·D 확인 후 검증 데이터 정리 완료
**환경:** Spring Boot 3.5.3 / Java 21 / PostgreSQL 16.15 (`jdbc:postgresql://localhost:55432/gateway`)

컬럼명·enum은 `01_data-architect_names.md`, 계약은 `01_api-ai-architect_contract-freeze.md`가 원본이다.
이 문서는 **판정에서 판단이 필요했던 지점**과 **검증 결과**다.

---

## 1. 산출물

| 파일 | 내용 |
|---|---|
| `engine/RuleEngine.java` | 파이프라인 5단계 (매칭 → 중첩 억제 → 충돌 해결 → 마스킹 → `ruleResult` 조립) |
| `engine/RegexMatcher.java` | REGEX 실행. `rule_id → Pattern` 캐시. 패턴은 DB에서만 읽는다 |
| `engine/KeywordMatcher.java` | KEYWORD 실행. 규칙당 1건 + 매칭 키워드 전체 보존 (D9) |
| `engine/ConflictResolver.java` | 중첩 억제(D1) + 충돌 해결(7.5) |
| `engine/Masker.java` | 뒤→앞 치환, 부분 겹침 병합 |
| `engine/RuleHit.java` · `EngineVerdict.java` | 엔진 내부·경계 타입 |
| `service/PolicyService.java` | 정책 로드·스냅샷·`policyVersion`, `GET /policies` |
| `service/InspectionService.java` | 저장·응답·AI 인계, 감사 목록·상세 |
| `service/InspectionAiResultSink.java` | **계약서 §4 인계 4 구현체** |
| `api/{DepartmentController,PolicyController,MessageController,InspectionController}.java` | 엔드포인트 6종 |
| `api/QueryParams.java` | 쿼리 파라미터 파싱 헬퍼 (§5 참조) |
| `api/dto/*.java` | 요청·응답 DTO 12종 |
| `src/test/java/**` | 테스트 5클래스 28건 |

**만들지 않은 것:** `ReviewController`, `ReviewService`, `@RestControllerAdvice`, `PATCH` 엔드포인트.
계약서 §8대로 `api-ai-architect`의 다음 라운드 몫이다.

---

## 2. 데모 케이스 검증 — 기대 vs 실제

### 단위·통합 테스트 (`./gradlew test`) — **28건 전부 통과**

| 클래스 | 건수 | 내용 |
|---|---|---|
| `RuleEngineDemoCaseTest` | 6 | A·B·C·D 엔진 판정, 조기 종료 없음, 평문 ALLOW |
| `DemoCaseApiTest` | 6 | A·B·C·D의 HTTP 상태·응답 본문, 400 2종 |
| `MasterDataApiTest` | 10 | 조회 4종 + 봉투·에러 |
| `MaskerTest` | 4 | 뒤→앞 치환, 겹침 병합 |
| `InspectionAiResultSinkTest` | 2 | AI 결과 저장, FAILED 폴백 |

`DemoCaseApiTest`·`RuleEngineDemoCaseTest`는 `@Transactional`이라 **롤백된다.** 테스트가 감사 콘솔에
행을 남기지 않고, `afterCommit`이 실행되지 않아 Mock의 2.5초 지연도 타지 않는다.

### 실제 HTTP (`bootRun` + curl) — **4종 전부 기대와 일치**

| 케이스 | 계정 | 기대 | 실제 |
|---|---|---|---|
| A | 1 (개발팀) | BLOCK · 403 · finding 2건 · `submittedText` null | ✓ 403, `SEC-DBURL-02 [18,56]` · `PII-RRN-01 [73,87]`, `submittedText: null`, `aiStatus=SKIPPED`, `decidedBy=RULE`, 목록 `ruleCount=2` |
| B | 2 (영업팀) | PENDING · 202 · finding 1건 · `submittedText` 채워짐 · hits 2건 | ✓ 202 + `Location: /api/v1/inspections/117`, `CONF-CLIENT-01 [0,2]` `matchedKeyword="A사"`, `pollAfterMs=2000`, 2.54초 뒤 `aiStatus=COMPLETED` |
| C | 1 (개발팀) | ALLOW · 200 · finding 0건 | ✓ 200, `matches: []`, `appliedRuleCodes`에 `CONF-CLIENT-01` 없음 |
| D | 3 (인사팀) | MASK · 200 · `[전화번호]` | ✓ 200, `submittedText: "지원자 연락처 [전화번호] 로 면접 안내 문자 초안 써줘"`, `PII-PHONE-03 [8,21]` |

**Case A 원시 매칭 4건 → finding 2건 (D11)이 실측으로 재현된다.**
`RuleEngineDemoCaseTest.caseA_suppressesTwoNestedMatches`가 원시 4건을 span까지 고정하고 있어,
누가 억제 로직을 "사설 IP 하나만"으로 바꾸면 그 자리에서 깨진다.

### 추가 확인

- **AI 실패 폴백:** `A사 차세대 프로젝트 일정 __FAIL__` → 202 → `aiStatus=FAILED`, `completedAt` 채워짐,
  `status`는 `PENDING_REVIEW` 유지, AI finding 미생성. 규칙 finding만 남는다
- **원문 미노출:** `GET /inspections/{id}` 응답에 `originalText` 키 자체가 없다
- **정규식 미노출:** `GET /policies` 응답 전문에 `pattern` 문자열이 없다 (C5)

### 검증 데이터 정리

`bootRun` 확인으로 생긴 message/inspection 116~120(5건)과 finding 6건을 삭제하고 시퀀스를
`message=103` / `inspection=103` / `finding=54`로 되돌렸다. **현재 DB는 `data-architect`의 시드 상태 그대로다** —
103행, `ALLOWED 56 / MASKED 25 / BLOCKED 14 / PENDING_REVIEW 8`, 규칙 BLOCK 13건만 `submitted_text` NULL.

---

## 3. 판단이 필요했던 지점

### 3.1 중첩 억제의 정렬 기준 — 같은 자리에서 시작하면 **긴 매칭이 앞선다**

D1은 "span 시작 오프셋 순 정렬 후 앞선 매칭에 포함되면 억제"까지만 정한다. 시작 오프셋이 같을 때
누가 "앞선 매칭"인지는 미정이었다.

**`spanStart` 오름차순 → `spanEnd` 내림차순 → severity → rule code 사전순으로 고정했다.**
짧은 쪽을 먼저 남기면 그것을 감싸는 넓은 매칭이 뒤늦게 살아남아 같은 구간이 두 번 세어진다.
severity·code 순서는 7.6의 라벨 충돌 기준과 같은 것을 썼다 (`spec-steward` 미판정 항목 "동률 매칭 정렬 기준"에 해당).

Case A는 시작 오프셋이 전부 달라 이 규칙이 결과를 바꾸지 않는다. 앞으로 규칙이 늘 때를 위한 고정이다.

### 3.2 부분 겹침 마스킹은 **구간을 합쳐서** 치환한다

7.6은 "부분 겹침이면 severity 높은 규칙의 라벨"만 정하고 치환 방법을 정하지 않았다.
겹치는 두 구간을 각각 치환하면 뒤→앞 순서로도 문자열이 깨진다(앞 치환이 뒤 구간 경계를 침범).

**겹치는 구간을 하나로 병합하고 승자의 라벨 하나만 넣는다.** 데모 4종에는 부분 겹침이 없고
`MaskerTest`가 이 동작을 고정한다.

### 3.3 REGEX는 매칭마다 finding, KEYWORD는 규칙당 1건

7.4-4는 "매칭마다", 7.4-5와 D9는 "규칙당 1건"이다. 문구 그대로 다르게 구현했다.
같은 주민번호가 두 번 나오면 finding 2건이고, `A사`·`차세대`가 둘 다 걸려도 finding은 1건이다.
`data-architect` 노트 4.2가 제기한 "Case B의 `ruleCount`는 1인가 2인가"는 **1**로 확정된다.

### 3.4 응답 `createdAt`은 `inspection.created_at`이다

계약서 §2가 `message.created_at`과 `inspection.created_at`을 둘 다 `createdAt`으로 매핑한다.
같은 요청에서 밀리초 차이지만 값이 다르다. **전부 `inspection.created_at`으로 통일했다** —
감사 목록의 정렬 기준(`InspectionSpecs.DEFAULT_SORT`)이 `inspection.createdAt`이라, 목록 행의
`createdAt`이 다른 컬럼이면 정렬과 표시가 어긋난다.

### 3.5 시각은 경계에서 UTC로 고정한다

DB가 `timestamptz`라 서버 로컬 오프셋(`+09:00`)으로 읽힌다. 8.1이 "ISO 8601, UTC"이므로
DTO에서 `withOffsetSameInstant(UTC)`로 변환한다 (`api/dto/ApiTimes`). 응답은 `…Z`로 나간다.

### 3.6 `policySnapshot.ruleCodes[]`는 정책 안에서 코드 사전순

7.4-3은 순서를 정하지 않았다. `data-architect`의 시드 예시가 사전순이라 맞췄다.
`appliedRuleCodes[]`는 **실행 순서 그대로**(REGEX severity 내림차순 → KEYWORD)다 — 두 배열의
정렬이 다른 것은 의도한 것이고, 앞은 스냅샷이라 안정적 표시가 목적, 뒤는 실행 순서 기록이 목적이다.

### 3.7 활성 규칙이 0건이면 500으로 막는다

`PolicyService.loadForDecision`이 `IllegalStateException`을 던진다. GLOBAL 정책은 매핑 없이 전 부서에
적용되므로(7.3) 규칙 0건은 시드가 비었다는 뜻이다. 빈 정책으로 통과시키면 검사 없이 지나간 프롬프트가
`ALLOWED`로 기록되어 감사 기록 자체가 거짓이 된다 (UC-01 예외).

부서에 매핑된 **DEPT 정책이 0건인 것은 정상**이며 예외를 던지지 않는다. 그것이 Case C다.

---

## 4. 계약과 어긋난다고 판단한 지점 — 1건

### `findings[]`의 정렬이 문서 예시와 반대다 (AI가 먼저 온다)

`InspectionFindingRepository.findByInspectionInspectionIdOrderBySourceAscFindingIdAsc`의 Javadoc은
"RULE이 먼저 오고 그 안에서는 생성 순"이라고 적혀 있지만, `source`가 `@Enumerated(STRING)`이라
**SQL이 문자열로 정렬해 `'AI' < 'RULE'`이 된다.** 실측:

```
findings: [ {source: "AI",   code: "CONF-CLIENT-PROJECT"},
            {source: "RULE", code: "CONF-CLIENT-01"} ]
```

기획서 8.4 예시와 계약서 §1-5는 RULE(501) → AI(502) 순으로 적혀 있다.

**고치지 않았다.** 리포지토리는 `data-architect`의 파일이고, D3에 따라 FE는 `source`로 갈라 렌더링하므로
화면 영향이 없다고 판단했다. 다만 **FE가 `findings[0]`을 규칙 finding으로 가정하면 깨진다.**
`frontend-dev`는 인덱스가 아니라 `source`로 필터할 것. 순서를 문서대로 맞추려면
`data-architect`가 정렬을 `case when source='RULE' then 0 else 1 end`로 바꾸면 된다.

---

## 5. `api-ai-architect`가 다음 라운드에 알아야 할 것

### 5.1 인계 지점은 계약서대로 동작한다

- **인계 3** — `InspectionService.scheduleAiInspection`이 `aiInspectionRunner.schedule(...)`을
  트랜잭션 안에서 호출한다. `afterCommit` 처리는 `AiInspectionRunner`가 하므로 규칙 엔진 쪽에
  `TransactionSynchronizationManager`도 `@Async`도 없다. 실측으로 202 응답이 즉시 나가고
  2.5초 뒤 `COMPLETED`가 된다
- **인계 4** — `service/InspectionAiResultSink`가 `@Component` + `@Transactional`로 구현되어 있다.
  `onCompleted`는 `ai_result`·`ai_status=COMPLETED`·`completed_at`을 쓰고 `riskCandidates[]`를
  `source=AI` / `review_status=SUGGESTED` / span·action NULL로 INSERT한다.
  `onFailed`는 `ai_status=FAILED`·`completed_at`만 쓰고 `message.status`는 건드리지 않는다
- **`AiResultSink`는 최종 판정을 바꾸지 않는다.** `final_decision=PENDING`, `decided_by=null`,
  `message.status=PENDING_REVIEW`가 그대로 남는다. 판정을 옮기는 것은 PATCH(사람 확정)뿐이다 —
  `ReviewService`가 §1-7의 재산출 규칙을 그 위에 얹으면 된다

### 5.2 에러 봉투가 아직 컨트롤러 안에 있다

`@RestControllerAdvice`가 없어서 각 컨트롤러가 `ResponseEntity`로 직접 `ErrorResponse`를 반환한다.
현재 봉투가 나가는 것은 `INVALID_REQUEST`·`INVALID_USER`·`INVALID_PARAMETER`·`INSPECTION_NOT_FOUND`다.

**아직 봉투가 아닌 것 (Spring 기본 400 본문이 나간다):**

```
$ curl -X POST /api/v1/messages   # X-User-Id 없음
{"timestamp":"...","status":400,"error":"Bad Request","path":"/api/v1/messages"}
```

`WebConfig`의 인자 리졸버가 던지는 `MissingRequestHeaderException`(→ `MISSING_USER_HEADER`)과
`ServletRequestBindingException`(→ `INVALID_USER`)을 advice에서 변환하면 된다.
advice가 생기면 `api/QueryParams`를 쓰는 컨트롤러의 수동 파싱도 예외 변환으로 옮길 수 있다 —
지금 구조는 advice와 충돌하지 않는다(예외를 던지지 않고 직접 반환하므로).

`api/QueryParams.java`는 담당 범위 목록에 없던 파일이지만 컨트롤러 3개가 공유하는
package-private 헬퍼라 `api/`에 두었다. 다른 에이전트의 파일과 이름이 겹치지 않는다.

### 5.3 PATCH 구현 시 확인할 것

- `submitted_text`를 BLOCK 전이 때 NULL로 되돌리는 규칙(§1-7)은 `data-architect` 노트 4.6의
  "사람 확정 BLOCK은 본문 유지" 결론과 **반대 방향**이다. 리더 확인이 아직 열려 있는 항목이므로
  구현 전에 한 번 맞출 것
- `InspectionFinding.review(...)`가 `reviewStatus`를 바꾸는 유일한 경로다. 엔티티가 이미 제공한다
- `ruleCount` 집계는 `InspectionFindingRepository.countRuleFindings`를 쓴다. AI finding을 세면
  Case B가 2가 되어 목록과 상세가 어긋난다

### 5.4 `frontend-dev`가 쓸 실제 응답

§2의 실측값이 그대로 응답 본문이다. 특히:

- 목록 4종 전부 `{items, page, size, total}` 봉투다. 비페이징도 `page=0`, `size=total=items.length`
- 403은 판정 객체다. `code` 필드가 **없다**
- 202에는 `aiAssessment` 키 자체가 없다. `Location` 헤더로 폴링 URL이 온다
- `pollAfterMs=2000`은 서버가 준다. FE 상수를 쓰지 않는다
- `findings[]`는 §4대로 AI가 먼저 올 수 있다. `source`로 필터할 것

---

## 6. 미완료 / 남은 위험

| 항목 | 담당 | 내용 |
|---|---|---|
| `@RestControllerAdvice` | `api-ai-architect` | §5.2. `X-User-Id` 누락이 아직 기본 400 본문 |
| `PATCH` + `ReviewService` | `api-ai-architect` | §5.3 |
| `findings[]` 정렬 | `data-architect`(선택) | §4. 문서 예시와 반대. FE가 `source`로 필터하면 무해 |
| 사람 확정 BLOCK의 `submitted_text` | 리더 | `data-architect` 노트 4.6이 아직 열려 있다 |
| 부분 겹침 마스킹 | — | 구현·테스트는 있으나 데모 데이터에 사례가 없다 |
| 동시 요청 하의 `Pattern` 캐시 | — | `ConcurrentHashMap.compute`로 갱신한다. 정책을 런타임에 고쳐도 다음 요청에 반영된다 |
