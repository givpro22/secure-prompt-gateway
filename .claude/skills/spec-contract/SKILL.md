---
name: spec-contract
description: "사내 AI 게이트웨이 기획서를 단일 진실 원천으로 삼아 필드명·enum 값·상태 코드·용어의 계약 정합성을 유지하는 공유 스킬. Interface Freeze 절차, 계약 위반 감지, 기획서 미결 항목 처리를 다룬다. 게이트웨이 프로젝트에서 API 필드명·DB 컬럼명·enum 값·상태 코드·화면 용어를 정하거나 바꾸거나 대조할 때, '필드명이 뭐였지', '이 값이 계약에 맞나', 'Interface Freeze', '계약 확정', '기획서랑 다른데' 같은 상황에서 반드시 이 스킬을 사용할 것. 계약 변경·재확정·정합성 재점검 요청에도 사용."
---

# Spec Contract — 계약 정합성 유지

이 프로젝트의 최대 리스크는 기능 부족이 아니라 **경계면 불일치**다. FE·BE·DB가 각각 정상인데 연결에서 어긋나면 데모가 죽는다. 기획서 14장이 "Interface Freeze 지연"을 확률 높음·영향 큼으로 분류한 이유다.

## 단일 진실 원천 (SSOT)

`사내_AI_게이트웨이_기획서_v1.md`가 SSOT다. 계층은 다음과 같다.

| 우선순위 | 문서 | 관할 |
|---|---|---|
| 1 | 기획서 | 도메인 결정 — 정책·규칙·판정 로직·범위 |
| 2 | `_workspace/01_api-ai-architect_contract-freeze.md` | 계약 확정본 — 실제 필드명·shape. 기획서 예시와 다를 수 있으나 **다르다는 사실이 기록되어야 한다** |
| 3 | 코드 | 구현. 1·2와 어긋나면 코드가 틀린 것이다 |

**기획서 내용을 스킬·문서에 복사하지 않는다.** 섹션 번호로 인용한다. 복사본은 기획서가 갱신되는 순간 어긋나고, 어느 쪽이 맞는지 아무도 모르게 된다.

## 불변식 — 협상 대상이 아닌 것

아래는 편의를 위해서라도 바꾸지 않는다. 각각이 이 프로젝트의 설계 주장을 증명하는 장치이기 때문이다.

| 불변식 | 근거 | 깨지면 잃는 것 |
|---|---|---|
| AI 응답 스키마에 `decision`/`action`/`block`/`allow` 필드 없음 | 9.4 | "AI는 제안만 한다"는 책임 경계 주장 전체 |
| `inspection_finding.review_status` DB 기본값 `SUGGESTED` | 6.2, 4장 | AI 후보가 사람 확정 없이 효력을 갖게 됨 |
| BLOCK 판정 시 AI 미호출 | 7.5 | 이미 확정된 위반에 비용 지출, 외부 전송 텍스트 존재 |
| AI 입력은 `maskedText`, 원문 절대 미전달 | 9.3 | "검사하려고 원문을 밖으로 보낸다"는 반론에 무방비 |
| Mock 지연 2.5초 유지 | 9.5, 14장 | 202 비동기 설계가 화면에 안 드러남 |
| 충돌 우선순위 `BLOCK > REVIEW > MASK > ALLOW` | 7.5 | 부서별 정책 중첩 시 판정 불일치 |
| URL에 `ai`/`mock` 미노출 | 8.1 | Mock↔LLM 교체 시 FE 변경 필요 → Interface First 원칙 붕괴 |
| 키·모델·지연은 환경변수, 정책·규칙은 DB | 11.3 | Security & Config Isolation 원칙 |

## Interface Freeze 절차

`api-ai-architect`가 주관한다. 기획서 13장 기준 2일차 09:30이 고정 시각이다.

1. **입력 수집** — `data-architect`의 컬럼명·enum 목록, `rule-engine-dev`의 `ruleResult` shape을 먼저 받는다. 계약을 혼자 쓰면 반드시 DB와 어긋난다
2. **계약서 작성** — `_workspace/01_api-ai-architect_contract-freeze.md`에 아래 4개 표를 채운다
3. **전원 통보** — SendMessage로 확정을 알린다. 이 시점부터 FE는 Mock으로 선행 개발할 수 있다
4. **변경 차단** — 이후 필드명 변경 요청은 거부하고 `spec-steward`에게 에스컬레이션한다

### 계약서 필수 4표

**표 1. 엔드포인트 계약** — 7개 전부. Method / Path / 요청 shape / 상태 코드별 응답 shape

**표 2. 필드 매핑** — 3단 대조. 이 표가 경계면 버그를 사전에 막는다

| DB 컬럼 | API 필드 | FE 참조 |
|---|---|---|
| `ai_status` | `aiStatus` | `inspection.aiStatus` |
| `submitted_text` | `submittedText` | `msg.submittedText` |
| `decided_by` | `decidedBy` | `row.decidedBy` |
| `review_status` | `reviewStatus` | `finding.reviewStatus` |

**표 3. enum 값 목록** — 값 하나라도 빠지면 FE 분기가 죽는다

| 대상 | 값 |
|---|---|
| `message.status` | ALLOWED, MASKED, BLOCKED, PENDING_REVIEW |
| `inspection.ai_status` | SKIPPED, PENDING, COMPLETED, FAILED |
| `inspection.final_decision` | ALLOW, MASK, BLOCK, PENDING |
| `inspection.decided_by` | RULE, HUMAN, (null) |
| `finding.source` | RULE, AI |
| `finding.review_status` | SUGGESTED, ACCEPTED, REJECTED, CONFIRMED |
| `policy_rule.action` | MASK, BLOCK, REVIEW |
| `policy.scope` | GLOBAL, DEPT |

**표 4. 인계 지점 시그니처** — 에이전트 간 코드가 만나는 자리

| 인계 | 넘기는 쪽 | 받는 쪽 | 시그니처 |
|---|---|---|---|
| 규칙 판정 → AI 호출 | `rule-engine-dev` | `api-ai-architect` | `AiInspectionRequest(maskedText, departmentCode, categories, hits, policyVersion)` |
| 정책 로드 | `data-architect` | `rule-engine-dev` | `List<PolicyRule> findActiveByDept(Long deptId)` |
| 판정 결과 → 응답 | `rule-engine-dev` | `api-ai-architect` | `ruleResult` JSON (8.4 형식) |

## 계약 위반 감지

계약 위반을 발견했을 때 대응은 위반 유형에 따라 다르다.

| 유형 | 예시 | 대응 |
|---|---|---|
| 코드가 계약과 다름 | API가 `ai_status`로 반환 | 코드를 고친다. 계약이 맞다 |
| 계약이 기획서와 다름 | 계약에 `pollAfterMs` 없음 | 의도적이면 계약서에 사유를 명기, 실수면 계약을 고친다 |
| 기획서 내부 모순 | 아래 "미결 항목" 참조 | 임의 해석 금지. `spec-steward`에게 결정 요청 |
| FE가 방어 코드로 덮음 | 옵셔널 체이닝으로 조용히 통과 | **가장 위험하다.** 버그가 데모까지 살아남는다. 방어 코드를 걷어내고 계약대로 고친다 |

## 결정된 항목 (D1~D6)

기획서 v1.0의 모순·미정 항목 6건은 **2026-09-02에 전부 결정되어 기획서 본문에 반영되었다.** 기획서 0.5절이 색인이고, 결정 기록은 `_workspace/00_input/decisions.md`, 근거 전문은 `references/open-questions.md`다.

**새로 결정할 것은 없다.** 아래는 구현 시 지켜야 할 결정 내용이다.

| ID | 결정 | 구현에서 지킬 것 |
|---|---|---|
| D1 | Case A 규칙 **2건** (중첩 억제) | 앞선 매칭의 span에 완전히 포함되는 매칭은 finding 미생성 |
| D2 | 정보보안팀 code **`INFOSEC`** | department 시드 4행, department_policy 매핑 없음, 부서 필터 미노출 |
| D3 | span 재계산 **안 함** | FE가 `submitted_text`에서 mask_label 문자열 검색으로 하이라이트 |
| D4 | 201 **미사용** | 계약서 상태 코드 표에 미사용 사유 명기 |
| D5 | 마스킹은 **BLOCK 아닐 때만** | `Masker` 호출을 최종 판정 분기 안으로. 치환은 뒤에서 앞으로 |
| D6 | `review_status` **4값** | CHECK 제약·DTO enum에 `CONFIRMED` 포함. CONFIRMED에는 ACCEPT/REJECT 버튼 미노출 |

결정을 뒤집자는 제안이 나오면 `references/open-questions.md`의 "결정 안 하면" 항목으로 비용을 먼저 확인한다. 뒤집으려면 기획서 본문 수정까지 함께 해야 한다.

## 용어 고정

화면 노출 용어는 기획서 5.6 표를 따른다. 내부 enum을 화면에 그대로 노출하지 않는다.

`ALLOW/ALLOWED`→허용 · `MASK/MASKED`→마스킹 · `BLOCK/BLOCKED`→차단 · `REVIEW/PENDING_REVIEW`→검토 대기 · `SUGGESTED`→제안됨 · `ACCEPTED`→확정(위반) · `REJECTED`→기각

문서·발표·코드 주석에서도 같은 대응을 쓴다. 같은 것을 다르게 부르면 Q&A에서 팀이 서로 다른 말을 하게 된다.

## 범위 방어

기획서 0.3이 만들지 않을 것을 명시했다. 아래 요청이 오면 17장 향후 확장으로 이관한다.

출력(응답) 검사 · 첨부파일 검사 · 정책 편집 UI · 정책 시뮬레이터 · 로그인/권한 · 실제 LLM 호출 · 임베딩/벡터 검색 · 토큰 비용 집계

"금방 되는데요"가 이 프로젝트에서 가장 위험한 문장이다. 3일이고 4인이다.
