# API·AI 확장 지점 구현 노트 — `api-ai-architect`

**작성일:** 2026-09-02
**상태:** 담당 범위 구현 완료. 계약서 개정 4까지 반영. 단위/통합 테스트 37건 통과 (기존 28 + 신규 9)
**계약 원본:** `_workspace/01_api-ai-architect_contract-freeze.md` — 이 노트와 어긋나면 **계약서가 맞다**

계약·필드명·상태 코드는 전부 계약서에 있다. 이 문서는 **판단이 필요했던 지점**과 **미완료 항목**이다.

---

## 1. 산출물

| 파일 | 내용 |
|---|---|
| `api/ReviewController.java` | `PATCH /inspections/{id}/findings/{findingId}` |
| `service/ReviewService.java` | 사람 확정 + 최종 판정 재산출 (UC-06) |
| `api/GlobalExceptionHandler.java` | `@RestControllerAdvice` 에러 봉투 |
| `api/ApiException.java` | 계약서 §1 에러 코드 8종을 상태 코드와 묶은 예외 |
| `api/dto/ReviewRequest.java` · `ReviewResponse.java` | PATCH 요청·응답 |
| `src/test/java/.../api/ReviewApiTest.java` | 신규 9건 |
| `docs/api-spec.md` · `docs/ai-gateway-v1.postman_collection.json` | 명세·컬렉션 |

이전 라운드 산출물(`ai/**`, `config/{AsyncConfig,AiProperties}`, `mock/ai/*.json`, `docs/ai-prompt.md`)은 계약서 §5에 있다.

---

## 2. 판단이 필요했던 지점

### 2.1 PATCH 응답에 무엇을 싣는가 — 두 번 틀리고 규칙을 세웠다

`inspection` 객체에서 필드를 뺐다가 두 번 되돌렸다. **둘 다 화면 버그로 나타났다.**

| 필드 | 뺀 이유 | 실제로 일어난 일 |
|---|---|---|
| `submittedText` | PATCH가 바꾸지 않는 값이라 "확정이 본문을 바꾼다"는 오해를 부를까 봐 | FE가 폐기된 v1 문구를 근거로 `status === 'BLOCKED'`에서 본문을 **로컬에서 지웠다** (`frontend-dev` 보고) |
| `completedAt` | 응답 크기만 늘린다고 봤다 | 서버가 확정 시각으로 갱신하는데 FE가 낡은 값을 유지해 상세 패널에 `완료 07:25:07`과 `확정자 박OO · 07:25:12`가 **어긋난 채 나란히** 표시됐다 (QA F6) |

**규칙: PATCH가 바꾸거나 화면이 다시 그려야 하는 값은 전부 응답에 싣는다.** 빠진 값은 FE가 추론하거나 낡은 채로 남긴다. 재조회 없이 갱신하게 하는 것이 이 객체의 존재 이유다(§1-7). 두 사례의 성격이 정반대인 것이 요점이다 — 하나는 **안 바뀌는 값**을 감춰서 추론을 불렀고, 하나는 **바뀌는 값**을 감춰서 낡은 값을 남겼다. "안 바뀌니까 뺀다"도 "안 쓸 것 같으니 뺀다"도 근거가 못 된다.

### 2.2 검사 순서가 응답 코드를 정한다 — 404 → 409 → 400

존재하지 않는 finding에 400을 주면 클라이언트가 값을 먼저 의심한다. **409 안에서는 D13이 재요청보다 앞이다** — 규칙 finding은 항상 `CONFIRMED`라 두 조건이 겹치는데, 사유가 `FINDING_ALREADY_REVIEWED`로 나가면 규칙 판정도 번복 가능한 것처럼 읽힌다. 책임 경계(4장)가 에러 코드에서 드러나는 자리다.

### 2.3 `completedAt`은 사람의 확정 시각으로 덮어쓴다

§1-7이 "갱신"만 정하고 AI 완료 시각 보존 여부는 미정이었다. **덮어쓴다** — 감사 화면의 "완료"는 판정이 끝난 시점이고, REVIEW 건에서 그것은 담당자가 누른 순간이다. AI 완료 시각이 필요하면 `ai_result` 저장 시각으로 따로 남길 문제다. `aiStatus`는 바꾸지 않는다 — 사람의 확정은 AI 검사의 상태가 아니고 FE 폴링이 이 값으로만 끝난다(D12).

### 2.4 advice에 포괄 핸들러를 두지 않았다

`@ExceptionHandler(Exception.class)`가 없다. 계약서 §1 상태 코드 표에 500이 없고, 무엇이든 봉투로 감싸면 `PolicyService`의 "활성 규칙 0건"처럼 **드러나야 할 서버 오류가 400대 응답처럼 보인다**. 변환 대상은 `ApiException`·`MissingRequestHeaderException`·`ServletRequestBindingException`·`HttpMessageNotReadableException`·`MethodArgumentTypeMismatchException` 다섯뿐이다.

**403(BLOCK)은 advice를 거치지 않는다.** 기존 컨트롤러 4종은 예외를 던지지 않고 `ResponseEntity`로 직접 반환하므로 경로가 겹치지 않는다. `ReviewApiTest.blockVerdictIsNotAnErrorEnvelope`가 이 회귀를 고정한다 — advice를 넓히려는 다음 사람이 여기서 걸린다.

### 2.5 `reviewStatus`를 enum이 아니라 String으로 받는다

enum으로 받으면 `"SUGGESTED"`는 Jackson을 통과해 서비스에서 걸리고 `"FOO"`는 Jackson이 먼저 터져 **두 경우의 400 메시지가 갈린다.** 문자열로 받아 한 자리에서 판정하면 어떤 값이 와도 같은 `INVALID_REQUEST` 봉투가 나간다.

### 2.6 Example 본문은 손으로 쓰지 않는다

`frontend-dev` 5건 + QA 5건, 총 10건의 Postman 결함이 전부 **손으로 쓴 Example이 실제 응답과 어긋난 것**이었다(`policySnapshot` 누락, `matchedKeyword` 키 누락, `source` 오기, 규칙 축약, 영문 에러 메시지). 지금은 전부 기동 중인 서버의 실측 응답에서 가져오고 id만 문서 전역값으로 맞춘다. 검증 스크립트가 Example ↔ 실측을 대조한다.

---

## 3. 미완료 / 남은 위험

| 항목 | 담당 | 내용 |
|---|---|---|
| `review_comment` 저장 | — | 컬럼이 없어 PATCH의 `comment`는 **수신만 하고 버린다**(§1-7, `data-architect` 노트 4.6). 감사 증적에 코멘트가 필요하면 `V3__*.sql`로 컬럼 추가 |
| 역할 검사 | — | `X-User-Id`를 그대로 `reviewed_by`에 쓴다. **SECURITY_ADMIN 여부를 확인하지 않는다** — 로그인·권한이 범위 밖이다(0.3). 직원 계정으로도 확정이 된다 |
| 동시 확정 | — | 같은 finding에 PATCH가 동시에 오면 둘 다 200이 될 수 있다. 낙관적 잠금(`@Version`)이 없다. 단일 사용자 데모라 실현되지 않는다 |
| `LlmAiInspector` | — | 골격만. 실제 HTTP 호출은 범위 밖(0.4). 교체 절차는 `docs/ai-prompt.md` |
| `findings[]` 정렬 | `data-architect`(완료) | `rule-engine-dev` 노트 §4가 지적한 AI-우선 정렬은 `findByInspectionIdRuleFirst`로 해결됨 |
| `submission-checklist.md` #18 | `spec-steward` | 구현 노트를 "3종"으로 적고 있다. 이 파일이 생겨 **4종**이다 |

---

## 4. 검증 방법

```bash
cd backend && ./gradlew test           # 37건
```

Postman Example ↔ 실측 대조는 서버를 띄우고 아래를 대조한다. **`POST /messages`는 행을 만든다** — 확인 후 `message_id > 103`을 지우고 시퀀스를 되돌린다.

```bash
curl -s 'localhost:8080/api/v1/policies?deptId=2'    # F3
curl -s 'localhost:8080/api/v1/users?deptId=2'       # F4
curl -s  localhost:8080/api/v1/inspections/999999    # F5 (404)
curl -s -X PATCH localhost:8080/api/v1/inspections/102/findings/54 \
     -H 'X-User-Id: 4' -H 'Content-Type: application/json' \
     -d '{"reviewStatus":"REJECTED"}'                # F5 (409) — 실패 요청이라 DB에 쓰지 않는다
```

**`./gradlew test`는 롤백되지만 시퀀스는 소모된다.** 시드 판정은 행 수와 시퀀스를 함께 본다(QA 확인).
