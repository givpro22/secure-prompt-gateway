---
name: ai-mock-contract
description: "사내 AI 게이트웨이의 API 계약과 AI 확장 지점을 구현하는 스킬. REST 엔드포인트 7종·Postman 컬렉션/Mock Server·AiInspector 인터페이스·시스템 프롬프트·JSON 스키마·MockAiInspector 픽스처·@Async 202 폴링·PATCH 검토 확정을 다룬다. API 명세·Postman·AI Mock·비동기 흐름·프롬프트·JSON 스키마·검토 확정 작업에 반드시 사용. 'API 응답 바꿔', 'Mock 추가', '폴링 안 돼', '202 흐름', '프롬프트 수정' 같은 후속 요청에도 사용."
---

# AI Mock & API Contract — 계약과 AI 확장 지점

기획서 8장(API 명세)·9장(AI 확장 지점)이 원본이다. 이 프로젝트에서 AI-Ready 원칙 4개 중 3개(Interface First, Structured Data, Asynchronous Pipeline)가 이 스킬의 범위에서 증명된다.

## 순서

**계약 확정을 다른 무엇보다 먼저 끝낸다.** 기획서 13장의 핵심 의존 관계가 여기다 — 계약이 늦으면 FE 개발이 통째로 막힌다.

1. 계약 확정 (`spec-contract` 스킬의 Interface Freeze 절차)
2. Postman 컬렉션 + Mock Server → FE 선행 개발 해금
3. `AiInspector` 인터페이스 + 픽스처
4. `@Async` 202 흐름
5. `PATCH findings` 검토 확정
6. `GET /inspections/{id}` 폴링 응답

## AiInspector 설계

```java
public interface AiInspector {
    AiAssessment inspect(AiInspectionRequest request);
}
```

인터페이스가 하나뿐인 것이 요점이다. Mock↔LLM 교체가 `@Profile` 전환과 환경변수 주입만으로 끝나고, FE가 보는 엔드포인트와 JSON은 불변이다. 이것이 Interface First 원칙의 증거다.

| 구현체 | 프로파일 | 상태 |
|---|---|---|
| `MockAiInspector` | `mock` (기본) | 완전 구현 |
| `LlmAiInspector` | `llm` | 클래스 골격 + 설정 키만. 실제 호출은 범위 밖 |

`LlmAiInspector`를 골격만 만드는 것은 미완성이 아니라 범위 결정이다(0.4). 교체 절차(9.6)를 문서로 남기는 것으로 충분하다.

### 입력 — 원문을 넣지 않는다

```
AiInspectionRequest(
    String maskedText,        // 규칙 엔진의 MASK가 이미 적용된 텍스트
    String departmentCode,    // DEV | SALES | HR
    List<String> categories,  // 적용 정책 카테고리
    List<KeywordHit> hits,    // {keyword, ruleCode, source}
    String policyVersion
)
```

`original_text`는 어떤 경로로도 들어가지 않는다. "검사하려고 결국 원문을 밖으로 보내는 것 아닌가"가 예상 질의 2번(16장)이고, 이 필드 구성이 답이다. `AiInspectionRequest`에 원문 필드를 만들지 않는 것으로 코드가 답을 증명한다.

`hits`는 9.3 프롬프트 조립의 "참조 근거"다. 현재는 `policy_rule.source` 값에서 오고, RAG 확장 시 `knowledge_source` 검색 결과로 대체된다. 이 자리가 F4 피드백("RAG도 붙일 수 있음")에 대한 답이다.

### 출력 스키마 — 결정 필드가 없다

`AiAssessment(List<RiskCandidate> riskCandidates, List<String> missingContext, boolean reviewRequired)`

`decision`·`action`·`block`·`allow`가 **없다.** 편의를 위해서라도 추가하지 않는다. 이것이 책임 경계(4장)를 스키마 수준에서 강제하는 장치이고, 예상 질의 "AI가 오판하면"에 대한 답이다. `confidence`도 두지 않는다 — 실제 확률이 아닌 값을 확률처럼 보이게 하면 사람의 판단을 왜곡한다.

스키마 전문은 기획서 9.4. `docs/ai-prompt.md`에 프롬프트 전문(9.2)·조립 기준(9.3)·스키마(9.4)를 함께 싣는다. 이 문서가 제출물이다(부록 C).

## MockAiInspector

### 결정론

같은 입력에 항상 같은 출력. 데모가 이 성질에 의존한다. 랜덤·시각·해시 순서에 의존하는 요소를 넣지 않는다.

| 조건 | 반환 |
|---|---|
| `hits`에 "A사" 포함 | `mock/ai/case-b-client-project.json` |
| `hits`에 "B사" 포함 | `mock/ai/case-client-generic.json` |
| `hits` 있으나 위 외 | 후보 0건, `missingContext` 1건("참조 근거와 대조할 사내 문서 없음"), `reviewRequired` true |
| `hits` 없음 | `IllegalStateException` |

마지막 항목이 중요하다. `hits`가 비었는데 호출됐다는 것은 규칙 엔진이 REVIEW 판정 없이 AI를 불렀다는 뜻이므로 버그다. 조용히 빈 결과를 반환하면 그 버그가 데모까지 살아남는다.

### 지연 2.5초는 의도된 것이다

`ai.mock.delay-ms` 기본 2500. **최적화하지 않는다.** 즉시 응답하면 202 비동기 설계가 화면에 드러나지 않아 Asynchronous Pipeline 원칙 증명이 실패한다. 기획서 14장이 이것을 리스크로 명시했다("202 폴링이 데모에서 즉시 끝나 비동기가 안 보임").

### 실패 시뮬레이션

`ai.mock.fail-keyword`(기본 `__FAIL__`)가 텍스트에 포함되면 `RuntimeException`을 던진다. `ai_status=FAILED` 경로가 실제로 동작해야 "AI가 죽어도 사람 검토로 폴백된다"는 주장이 증명된다. 이 경로를 반드시 테스트한다.

FAILED 시 `message.status`는 `PENDING_REVIEW`를 **유지한다**(UC-03 예외). ALLOWED로 떨어뜨리면 검사되지 않은 프롬프트가 통과 기록으로 남는다.

### 픽스처

`backend/src/main/resources/mock/ai/*.json` 3종. 각 파일은 9.4 스키마로 JSON Schema Validator를 통과해야 한다. 통과하지 않는 픽스처는 실제 LLM 교체 시 그대로 깨진다.

## @Async 202 흐름

```
POST /messages → 규칙 판정 REVIEW
  → inspection 저장 (ai_status=PENDING)
  → 202 + Location: /api/v1/inspections/{id}  ← 즉시 응답
  → @Async inspect() → 2.5s → ai_result 저장, AI finding 생성(SUGGESTED), ai_status=COMPLETED
```

**응답을 먼저 보내고 비동기를 시작한다.** 트랜잭션 커밋 전에 `@Async` 메서드가 실행되면 새 스레드가 아직 없는 inspection을 조회한다. `TransactionSynchronizationManager.afterCommit` 또는 서비스 경계 분리로 커밋 후 실행을 보장한다. 이것이 `@Async`에서 가장 흔한 함정이다.

`AsyncConfig`에 `ThreadPoolTaskExecutor`를 명시한다. 기본 `SimpleAsyncTaskExecutor`는 요청마다 스레드를 만든다.

202 응답 본문에는 `aiAssessment`와 AI findings가 **없다**. `pollAfterMs: 2000`을 실어 폴링 간격을 서버가 지시한다. FE가 202 시점에 `aiAssessment`를 참조하면 크래시하므로, 계약서에 "202 응답에 존재하지 않는 필드"를 명시한다.

## PATCH /inspections/{id}/findings/{findingId}

```json
{ "reviewStatus": "ACCEPTED", "comment": "..." }
```

응답에 갱신된 finding과 **재산출된 inspection 상태**를 함께 싣는다(8.4). FE가 한 번 더 조회하지 않아도 목록과 패널을 갱신할 수 있다.

최종 판정 규칙(UC-06): ACCEPTED 후보가 1건 이상이면 `BLOCKED`, 전부 REJECTED면 `ALLOWED`. 어느 쪽이든 `decided_by=HUMAN`. 이 값의 전이가 책임 경계 설계의 증거이며 데모 1:50의 대사다.

이미 ACCEPTED/REJECTED인 finding에 재요청하면 **409**를 반환한다. 멱등 처리로 200을 주면 `reviewed_at`이 덮어써져 증적이 손상된다.

`review_status`가 `CONFIRMED`인 규칙 finding에 PATCH가 오면 이것도 409다. 규칙 판정은 사람이 번복하지 않는다(4장, 이번 범위).

## Postman

- 컬렉션명 `ai-gateway-v1`, 환경 변수 `baseUrl`·`userId`
- 폴더 4개: `departments` / `policies` / `messages` / `inspections`
- Mock Server Example 6개 — `POST /messages`에 ALLOW·MASK·BLOCK·REVIEW, `GET /inspections/{id}`에 PENDING·COMPLETED
- Example 본문은 기획서 8.4의 JSON을 **그대로** 쓴다. 손으로 다시 쓰면 오타가 계약 불일치가 된다
- export를 `docs/ai-gateway-v1.postman_collection.json`에 커밋한다. 제출물이다

Mock Server URL을 준비하는 즉시 `frontend-dev`에게 통보한다. 그 통보가 FE 개발의 출발 신호다.

## 설정 분리

`application.yml`의 `ai.*`는 전부 환경변수 주입이다(11.3). 코드에 키·엔드포인트·모델명·지연값이 없고, 정책·규칙·임계값은 DB에 있다. 어느 쪽도 코드에 없는 것이 Security & Config Isolation 원칙의 증거다.

`@ConfigurationProperties("ai")`로 `AiProperties`에 바인딩한다. `@Value`를 여기저기 뿌리면 어떤 키가 있는지 한눈에 안 보인다.

## URL 규칙

리소스 명사 복수형. **구현 기술을 URL에 노출하지 않는다**(8.1). `/ai/inspect`, `/mock/...` 같은 경로를 만들지 않는다. Mock↔LLM 교체 시 FE가 보는 URL이 불변이어야 Interface First가 성립한다.
