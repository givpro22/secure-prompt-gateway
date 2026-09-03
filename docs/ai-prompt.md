# AI 확장 지점 — 시스템 프롬프트·조립 기준·입출력 스키마

기획서 9.2 / 9.3 / 9.4의 구현 확정본. 제출물(부록 C)이다.

가이드가 요구하는 "AI 확장 지점"은 한 곳으로 특정된다 — **UC-03, 규칙 엔진의 KEYWORD 규칙이 REVIEW
액션으로 매칭된 직후**다. 정규식이 100% 판정하는 것은 AI에 맡기지 않는다. 정규식이 구조적으로 못 잡는
맥락, 즉 "고객사명은 잡히지만 그것이 기밀 프로젝트 논의인지는 못 잡는" 부분만 AI의 영역이다.

| 항목 | 내용 |
|---|---|
| 인터페이스 | `AiInspector.inspect(AiInspectionRequest) → AiAssessment` |
| 현재 구현 | `MockAiInspector` (`@Profile("mock")`) — 케이스별 고정 JSON, 2.5초 지연 |
| 교체 후 | `LlmAiInspector` (`@Profile("llm")`) — 사내 호스팅 모델 또는 계약된 외부 API |
| RAG 연결 지점 | 입력 `hits[]`의 `source`. 현재는 `policy_rule.source`, 확장 시 `knowledge_source` 검색 결과 |

---

## 1. 시스템 프롬프트 (9.2)

고정 텍스트다. 실행 사본은 `PromptAssembler.SYSTEM_PROMPT`에 있다.

```
당신은 사내 정보보안팀의 프롬프트 검토 보조 시스템이다.

[역할]
- 입력된 텍스트에서 규칙 엔진이 잡지 못한 맥락형 기밀 노출 후보를 찾는다.
- 각 후보에 대해 어떤 서술에서 도출했는지 rationale을 쓴다.
- 제공된 참조 근거(evidence) 중 관련 있는 것을 후보에 연결한다.
- 판단에 필요한데 입력에 없는 정보는 missingContext에 기록한다.

[금지]
- 허용, 마스킹, 차단 여부를 판단하지 않는다.
- 입력에 없는 사실을 확정적으로 생성하지 않는다.
- 근거가 불충분하면 후보를 만들지 말고 missingContext에 남긴다.
- 개인정보로 보이는 문자열이 있어도 그것은 규칙 엔진의 영역이므로 후보로 만들지 않는다.

[출력]
- 아래 JSON 스키마만 반환한다. 설명 문장, 마크다운, 코드 펜스를 붙이지 않는다.
- riskCandidates가 없으면 빈 배열을 반환한다.
```

`[금지]`의 첫 줄이 책임 경계(기획서 4장)를 프롬프트 수준에서 강제하는 조항이다. 프롬프트만으로는
모델이 지시를 어길 수 있으므로 같은 경계를 **응답 스키마에 결정 필드를 두지 않는 것**(§3)과
**`review_status` 기본값 `SUGGESTED`**(DB)로 이중·삼중 강제한다. 셋 중 스키마와 DB는 모델이 어길 수
없는 종류의 강제다.

`[금지]`의 마지막 줄이 중요하다. 주민번호·카드번호는 정규식이 100% 잡으므로 AI가 중복해서 후보를
만들면 같은 위험이 두 번 세어진다. 그래서 응답 스키마의 `category`도 `CONFIDENTIAL` 한 값으로
제한되어 있다(§3).

---

## 2. 프롬프트 조립 기준 (9.3)

프롬프트는 시스템 프롬프트(고정) + 사용자 메시지(조립)로 구성된다. 사용자 메시지는 아래 4단을 이
순서로 만든다. 구현은 `PromptAssembler.assembleUserMessage()`이며 순수 함수라 같은 입력에 같은
프롬프트가 나온다.

| 순서 | 구성 요소 | 출처 | 규칙 |
|---|---|---|---|
| 1 | 부서 컨텍스트 | `departmentCode` | "요청자 부서: 영업팀" 한 줄 |
| 2 | 적용 정책 카테고리 | `categories` | "적용 정책: CONFIDENTIAL(고객사 프로젝트 정보 통제)" |
| 3 | 참조 근거 | `hits[]` | 키워드별 `{keyword, source}` 목록 |
| 4 | 검토 대상 텍스트 | `maskedText` | `<text>…</text>`로 감싸 경계를 명확히 함 |

### 제약

- **원문(`original_text`)은 어떤 경우에도 프롬프트에 넣지 않는다.** 규칙 엔진의 MASK가 먼저 적용된
  텍스트만 전달한다.
- **BLOCK 판정이 난 텍스트는 AI에 보내지 않는다** (기획서 7.5). 이미 확정된 위반에 비용을 쓸 이유가
  없고, 보낼 텍스트 자체가 없다.
- 최대 입력 길이 `ai.max-input-chars`(기본 4,000자). 초과 시 앞부분만 전달하고 `missingContext`에
  "입력 절단"을 기록한다.
- `temperature` 0, `max_tokens` 800. 값은 `application.yml`에서 주입한다. 코드에 상수로 두지 않는다.

`temperature` 0인 이유는 같은 프롬프트에 같은 후보가 나와야 감사 기록이 재현 가능하기 때문이다.
검토자가 "왜 이 후보가 나왔나"를 물었을 때 다시 돌려 확인할 수 있어야 한다.

### 조립 예시 (Case B)

```
요청자 부서: 영업팀
적용 정책: CONFIDENTIAL(고객사 프로젝트 정보 통제)
참조 근거:
- 키워드 "A사" — 고객사 NDA 목록 v3
- 키워드 "차세대" — 고객사 NDA 목록 v3
검토 대상:
<text>A사 차세대 프로젝트 오픈 일정이 언제였지?</text>
```

3단 "참조 근거"가 RAG 확장 지점이다. 지금은 `policy_rule`의 KEYWORD 패턴 매칭에서 오지만, 확장 시
`knowledge_source` 테이블 검색 결과가 같은 자리에 들어간다. 입력 스키마도 프롬프트 형식도 바뀌지
않는다 — 이 자리가 교수 피드백 F4("RAG도 붙일 수 있음")에 대한 답이다.

---

## 3. 입출력 JSON 스키마 (9.4)

### 입력 — `AiInspectionRequest`

```json
{
  "maskedText": "string",
  "departmentCode": "DEV | SALES | HR",
  "categories": ["PII", "SECRET", "CONFIDENTIAL"],
  "hits": [ { "keyword": "string", "ruleCode": "string", "source": "string" } ],
  "policyVersion": "string"
}
```

**원문 필드가 없다.** `original_text`를 담을 자리가 스키마에 존재하지 않는다. "검사하려고 결국 원문을
밖으로 보내는 것 아닌가"(기획서 16장 예상 질의 2번)에 대한 답이 이 필드 구성이고, 필드를 만들지 않는
것으로 코드가 답을 증명한다.

필드별 구성 규칙:

| 필드 | 채우는 법 |
|---|---|
| `maskedText` | 마스킹 적용본. 최종 판정이 PENDING일 때 마스킹이 이미 실행된 결과 |
| `departmentCode` | 제출자 부서 코드 |
| `categories` | **매칭된 REVIEW 규칙이 속한 정책**의 카테고리. 적용 정책 전체가 아니다 |
| `hits` | **KEYWORD 규칙(action=REVIEW) 매칭에서만** 생성. REGEX 매칭은 넣지 않는다 — PII·SECRET은 AI의 영역이 아니다. **매칭된 키워드를 전부 담는다** |
| `policyVersion` | `code:version` 쌍을 code 사전순으로 `;` 연결. 예: `P-CONF:2;P-PII:4;P-SEC:7` |

정렬을 고정하는 이유는 Mock의 결정론 때문이다. 같은 입력에 같은 문자열이 나와야 한다.

**finding은 규칙당 1건, `hits[]`는 키워드당 1건이다** (D9). Case B는 finding 1건에 `hits` 2건이며,
이것이 §2의 조립 예시(참조 근거 2건)와 8.4의 202 예시(매치 1건)가 동시에 성립하는 구성이다.
감사 목록의 "규칙 수"는 1이다.

`departmentCode`에 `INFOSEC`이 없는 이유: 정보보안팀은 검토자 역할만 하므로 프롬프트를 제출하지 않고
(기획서 0.5 D2), 따라서 AI 검사 경로에 도달하지 않는다. `department` 마스터에는 존재하지만 이 필드에는
나타날 수 없다. 실제로 도달할 수 없는 값을 enum에 넣으면 FE가 불필요한 분기를 만든다.

### 출력 — `AiAssessment` (JSON Schema draft 2020-12)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["riskCandidates", "missingContext", "reviewRequired"],
  "additionalProperties": false,
  "properties": {
    "riskCandidates": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["code", "category", "rationale"],
        "properties": {
          "code": { "type": "string", "pattern": "^[A-Z]+-[A-Z-]+$" },
          "category": { "type": "string", "enum": ["CONFIDENTIAL"] },
          "rationale": { "type": "string", "minLength": 10 },
          "evidence": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["source"],
              "properties": {
                "source": { "type": "string" },
                "excerpt": { "type": "string" }
              }
            }
          }
        }
      }
    },
    "missingContext": { "type": "array", "items": { "type": "string" } },
    "reviewRequired": { "type": "boolean" }
  }
}
```

**스키마에 `decision`, `action`, `block`, `allow`가 없다.** 이것이 책임 경계를 스키마 수준에서 강제하는
장치다. 모델이 프롬프트 지시를 어기고 "이건 차단해야 합니다"라고 말하려 해도 그 값을 실을 자리가
응답 형식에 없고, 역직렬화 단계에서 걸러진다. "AI가 오판하면 어떻게 되나"에 대한 답이 여기다 —
AI의 오판은 후보 하나가 늘거나 주는 것이지 판정이 뒤집히는 것이 아니다.

`confidence`도 두지 않는다. 실제 확률이 아닌 값을 확률처럼 보이게 하면 사람의 판단을 왜곡한다.
검토자가 "0.92니까 맞겠지"라고 생각하는 순간 사람 확정 단계가 형식적인 것이 된다.

`category`가 `CONFIDENTIAL` 한 값으로 제한된 것도 같은 맥락이다. PII·SECRET은 규칙 엔진이 100%
판정하므로 AI가 그 영역에서 후보를 만들 수 없어야 한다.

`additionalProperties: false`이므로 실제 LLM이 필드를 덧붙이면 역직렬화가 실패하고
`ai_status=FAILED` → 사람 검토 폴백으로 떨어진다. 스키마 위반이 조용히 통과하지 않는다.

---

## 4. Mock 구현 (9.5)

`MockAiInspector`는 **결정론적**이다. 같은 입력에 항상 같은 출력이 나온다. 데모가 이 성질에
의존하므로 랜덤·시각·해시 순서에 의존하는 요소를 넣지 않았다. 평가 순서 자체가 계약이다.

| 순서 | 조건 | 결과 |
|---|---|---|
| 1 | `hits`가 비었음 | `IllegalStateException` |
| 2 | `maskedText`에 `ai.mock.fail-keyword` 포함 | `RuntimeException` → `ai_status=FAILED` |
| 3 | (지연) `ai.mock.delay-ms` 기본 **2500ms** | |
| 4 | `hits`에 `A사` | `mock/ai/case-b-client-project.json` |
| 5 | `hits`에 `B사` | `mock/ai/case-client-generic.json` |
| 6 | 그 외 | `mock/ai/case-no-reference.json` |

**1번이 왜 예외인가.** `hits`가 비었는데 `AiInspector`가 호출됐다는 것은 규칙 엔진이 REVIEW 판정 없이
AI를 불렀다는 뜻이므로 버그다. 조용히 빈 결과를 반환하면 그 버그가 데모까지 살아남는다.

1번이 2번보다 앞이므로 **FAILED 경로를 시연하려면 입력에 REVIEW 키워드가 함께 있어야 한다.**
검증용 입력: `A사 차세대 프로젝트 일정 __FAIL__`

**지연 2.5초는 의도된 것이다.** 최적화하지 않는다. 즉시 응답하면 202 비동기 설계가 화면에 드러나지
않아 Asynchronous Pipeline 원칙 증명이 실패한다. 기획서 14장이 이것을 리스크로 명시했다
("202 폴링이 데모에서 즉시 끝나 비동기가 안 보임").

### 실패 시 동작

`ai_status=FAILED`가 되어도 **`message.status`는 `PENDING_REVIEW`를 유지한다.** `ALLOWED`로
떨어뜨리면 검사되지 않은 프롬프트가 통과 기록으로 남는다. AI가 죽어도 사람 검토로 폴백된다는 것이
이 경로의 요점이며, 그래서 이 경로를 실제로 실행해 볼 수 있게 설정 키로 만들어 두었다.

### 픽스처

`backend/src/main/resources/mock/ai/` 3종. 전부 §3 스키마를 만족한다(`additionalProperties: false`,
`code` 패턴, `category` enum, `rationale` minLength 10, 결정 필드 부재 검증 완료).

| 파일 | 후보 | 용도 |
|---|---|---|
| `case-b-client-project.json` | `CONF-CLIENT-PROJECT` 1건 | 데모 Case B |
| `case-client-generic.json` | `CONF-CLIENT-MENTION` 1건 | 고객사 언급이 있으나 맥락이 부족한 경우 |
| `case-no-reference.json` | 0건 | 대조할 사내 문서가 없는 경우 |

`case-no-reference.json`이 후보 0건인데 `reviewRequired: true`인 것이 프롬프트 `[금지]` 3번째 줄의
구현이다 — 근거가 불충분하면 후보를 만들지 않고 `missingContext`에 남긴다.

---

## 5. 실제 연동 시 교체 절차 (9.6)

1. `application.yml`에서 `ai.provider=llm`으로 변경 (또는 환경변수 `AI_PROVIDER=llm`),
   활성 프로파일을 `llm`으로 전환
2. `AI_ENDPOINT`, `AI_API_KEY`, `AI_MODEL` 주입
3. `LlmAiInspector`가 §1 + §2로 요청을 만들고(이미 구현되어 있다), 응답을 §3 스키마로 검증 후 반환
4. 검증 실패 시 `ai_status=FAILED`, 사람 검토로 폴백

**FE 코드와 API URL은 바뀌지 않는다.** `/ai/`, `/mock/` 같은 경로를 만들지 않았기 때문이다. 교체가
`@Profile` 전환과 환경변수 주입으로 끝나는 것이 Interface First 원칙의 증거다.

교수 피드백 F3의 "로컬 LLM"은 `AI_ENDPOINT`가 사내 주소를 가리키는 것으로 대응한다. 삼성SDS
SGuard-v1, 카카오 Safeguard by Kanana가 오픈소스로 공개되어 있어 사내 호스팅 후보가 구체적이다.
외부 API를 부르지 않으므로 "검사하려고 원문을 밖으로 보내는" 문제도 생기지 않는다. 어느 쪽이든
`LlmAiInspector`가 받는 것은 `maskedText`뿐이고 원문에 접근할 경로가 없다.

---

## 6. 설정 키

키·모델·지연은 전부 환경변수, 정책·규칙·임계값은 전부 DB. 코드에는 어느 쪽도 없다 (기획서 11.3).
바인딩은 `@ConfigurationProperties("ai")` → `AiProperties` 한 곳이다.

| 키 | 환경변수 | 기본값 |
|---|---|---|
| `ai.provider` | `AI_PROVIDER` | `mock` |
| `ai.endpoint` | `AI_ENDPOINT` | (빈 값) |
| `ai.api-key` | `AI_API_KEY` | (빈 값) |
| `ai.model` | `AI_MODEL` | (빈 값) |
| `ai.temperature` | `AI_TEMPERATURE` | `0` |
| `ai.max-tokens` | `AI_MAX_TOKENS` | `800` |
| `ai.timeout-ms` | `AI_TIMEOUT_MS` | `10000` |
| `ai.max-input-chars` | `AI_MAX_INPUT_CHARS` | `4000` |
| `ai.mock.delay-ms` | `AI_MOCK_DELAY_MS` | `2500` |
| `ai.mock.fail-keyword` | `AI_MOCK_FAIL_KEYWORD` | `__FAIL__` |

API 키가 저장소에 없고 규칙 패턴이 코드에 없는 것이 Security & Config Isolation 원칙의 증거다.
