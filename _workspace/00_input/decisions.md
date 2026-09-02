# 미결 항목 결정 기록

**결정일:** 2026-09-02
**대상:** `사내_AI_게이트웨이_기획서_v1.md` v1.0 정독 중 발견한 문서 내부 모순·미정 항목 6건
**상태:** 전건 결정 완료, 기획서 본문 반영 완료 (0.5절이 색인)

이 파일은 `gateway-orchestrator` Phase 1의 산출물이다. 구현 팀원 전원이 착수 전에 읽는다.

---

## 결정 요약

| ID | 결정 | 기획서 반영 위치 |
|---|---|---|
| D1 | Case A 규칙 매칭 **2건**. 포함 관계 매칭은 finding 미생성(중첩 억제) | 0.5, 7.4-6, 7.6, 8.4, 10.4 |
| D2 | 정보보안팀 code **`INFOSEC`**. department_policy 매핑 없음, 부서 필터 미노출 | 0.5, 6.2, 7.3, 10.2, 부록 B |
| D3 | span 재계산 **하지 않음**. FE가 mask_label 문자열 검색으로 하이라이트 | 0.5, 5.3, 7.6 |
| D4 | 201 Created **미사용**, 근거를 문서와 Q&A에 명시 | 0.5, 8.2, 16 |
| D5 | 마스킹은 최종 판정이 **BLOCK이 아닐 때만** 실행 | 0.5, 7.4-8, 7.5, 7.6 |
| D6 | `review_status` **4값** (SUGGESTED/ACCEPTED/REJECTED/CONFIRMED) | 0.5, 6.2, 부록 B |

---

## 구현 영향

각 결정이 어느 팀원의 코드를 바꾸는지. 착수 전에 자기 항목을 확인한다.

| 팀원 | 반영할 것 |
|---|---|
| `data-architect` | D2 — department 시드에 INFOSEC 1행 추가 (app_user 4번의 FK). D6 — `review_status` CHECK 제약을 4값으로 |
| `rule-engine-dev` | D1 — `ConflictResolver`에 중첩 억제 단계. D5 — `Masker` 호출을 최종 판정 분기 안으로. 치환은 뒤에서 앞으로 |
| `api-ai-architect` | D4 — 계약서 상태 코드 표에 201 미사용과 사유 명기. D6 — 응답 DTO의 `reviewStatus` enum 4값 |
| `frontend-dev` | D3 — 오프셋 산술 금지, 라벨 검색 방식. D6 — `CONFIRMED`에는 ACCEPT/REJECT 버튼 미노출. D2 — 부서 필터 3개 유지 |
| `integration-qa` | D1 — Case A 기대 2건. D5 — BLOCK 경로에서 Masker 미호출 확인. D6 — 규칙 finding에 버튼이 없는지 확인 |
| `spec-steward` | 발표 대사를 "규칙 2건"으로 통일. 16장 Q&A 2건 추가분 숙지 |

---

## 결정 근거 (요약)

전문은 `.claude/skills/spec-contract/references/open-questions.md`.

**D1** — 기획서 세 곳이 갈렸다. 10.4는 3건, 8.4 응답 예시와 15.2 데모 스크립트는 2건. 발표에서 실제로 읽히는 문서가 2건이고, 같은 문자열을 두 규칙이 이중으로 세면 감사 화면의 "규칙 N건"이 실제 위험 개수를 과장한다. Case A는 BLOCK이라 `submitted_text`가 NULL이므로 억제된 IP가 마스킹되지 않아도 유출 위험이 없다.

**D2** — 10.2가 정보보안팀 추가를 지시했으나 7.3 매트릭스에 없고 코드가 정의되지 않았다. 코드가 없으면 app_user 시드가 FK 제약으로 실패한다. `SEC`는 정책 코드 `P-SEC`와 혼동되므로 쓰지 않는다.

**D3** — 7.6이 "재계산"만 하고 주체를 정하지 않았다. 마스킹은 길이를 바꾸므로(`900101-1234567` 14자 → `[주민번호]` 6자) 다중 매칭에서 누적 델타 계산이 필요하고, 틀리면 하이라이트가 조용히 밀린다. 라벨은 대괄호로 시작하는 고유 형태라 검색이 틀릴 여지가 없고 코드가 세 줄이다. DB 저장은 감사 목적상 원문 기준을 유지한다.

**D4** — 8.2에 201이 정의만 되고 8.3 상태 칸에는 없다. 누락이 아니라 설계 판단이며, 루브릭이 상태 코드 근거를 평가하므로 이유를 말할 수 있으면 가점 요인이다.

**D5** — 7.6의 "severity 높은 규칙의 라벨 사용"이 BLOCK 규칙에는 `mask_label`이 없다는 사실과 충돌한다. D1의 중첩 억제로 라벨 충돌 자체가 사라지지만, 근본적으로 BLOCK이면 `submitted_text`가 NULL이라 마스킹 대상이 없다. 무조건 실행하면 Case A에서 NPE가 나고 그것이 데모 첫 케이스다.

**D6** — 6.2 나열은 3값인데 같은 줄과 8.4 응답 예시에 CONFIRMED가 등장한다. 빠지면 규칙 finding INSERT가 CHECK 제약에 걸리거나, 화면이 규칙 판정에도 ACCEPT 버튼을 노출해 "AI만 사람이 확정한다"는 책임 경계(4장)와 정면으로 어긋난다.

---

# 2차 결정 (D7~D13) — 구현 착수 시점

`spec-steward`가 기획서 전체를 교차 검증하며 발견한 모순 15건 중, 구현에 영향을 주는 7건을 리더가 판정했다. D1~D6과 같은 효력이며 기획서 0.5절에 반영되었다.

| ID | 결정 | 기획서 반영 위치 |
|---|---|---|
| D7 | `submitted_text`는 **BLOCK일 때만 NULL**. PENDING_REVIEW도 마스킹본을 채움 | 0.5, 6.2, 7.5, 8.4 |
| D8 | 5.3 캡션 "적용 정책 **2건**" (개발팀은 P-PII·P-SEC) | 0.5, 5.3 |
| D9 | finding은 **규칙당 1건**, `hits[]`는 **키워드당 1건** | 0.5, 7.4 |
| D10 | 필드명 `decided_by` / `decidedBy`로 통일 (`decision_source` 오기 제거) | 0.5, 4, 15.2 |
| D11 | Case A 원시 매칭 **4건 → 억제 2건** (PII-EMAIL-04 포함) | 0.5, 10.4 |
| D12 | 폴링은 `ai_status` 기준으로만 종료. 사람 확정은 재조회로 반영 | 0.5, 5.3 |
| D13 | `CONFIRMED` 규칙 finding에 PATCH 시 **409** | 0.5, 8.4 |

## 구현 영향

| 팀원 | 반영할 것 |
|---|---|
| `data-architect` | D7 — 시드의 PENDING_REVIEW 8건에 `submitted_text` 채우기. BLOCKED 12건만 NULL |
| `rule-engine-dev` | D7 — PENDING 경로에서도 마스킹 실행 후 저장. D9 — KEYWORD finding은 규칙당 1건, `matchedKeyword`에 첫 매칭. D11 — 단위 테스트 기대값을 원시 4건→finding 2건으로 고정 |
| `api-ai-architect` | D7 — 202 응답 shape에 `submittedText` 포함. D9 — `hits[]`는 키워드 전체. D10 — `decidedBy`. D13 — CONFIRMED PATCH는 409, 에러 코드 구분 |
| `frontend-dev` | D8 — 캡션 정책 건수는 API 응답값 사용. D12 — 폴링은 `aiStatus`로만 종료, `onUnmounted` 정리. 확정 반영은 재조회 |
| `integration-qa` | D11 — Case A 억제 2건(PII-EMAIL-04·SEC-PRIVIP-03) 확인. D12 — 폴링이 COMPLETED 후 멈추는지. D13 — 규칙 finding PATCH가 409인지 |
| `spec-steward` | D10 — 발표 대사에서 `decided_by` 사용 |

## D11 검증 기록

리더가 기획서 7.2의 정규식 7종을 Case A 입력에 직접 실행한 결과다.

```
[ 18, 56] SEC-DBURL-02   BLOCK  'postgres://admin:p%40ss@10.0.3.21/prod'
[ 37, 51] PII-EMAIL-04   MASK   '40ss@10.0.3.21'      ← 억제 (⊂ DBURL)
[ 42, 51] SEC-PRIVIP-03  MASK   '10.0.3.21'           ← 억제 (⊂ DBURL)
[ 73, 87] PII-RRN-01     MASK   '900101-1234567'
→ finding 2건, 최종 판정 BLOCK
```

PII-EMAIL-04의 오탐은 정규식의 구조적 한계이며, 중첩 억제가 이것까지 걸러낸다는 점이 D1 설계의 부수 효과다.

## 미판정 (낮은 우선순위)

`_workspace/01_spec-steward_open-questions.md`에 남은 8건은 구현을 막지 않는다. 발표 자료 작성 시 `spec-steward`가 처리한다 — 8.4 예시의 span 값·문자열 불일치, "UC 6개" 표기, 5.4 도해의 부서-이름 불일치, 동률 매칭 정렬 기준, 픽스처 개수 표기 등.

---

# D14 — `submitted_text IS NULL`의 의미 (2026-09-02, 구현 중)

`data-architect`가 D7을 "BLOCK이면 NULL"의 **필요조건**으로 읽고 확인을 요청했다. 리더가 DB 실측 후 그 해석을 채택했다.

**결정:** `submitted_text IS NULL` ⇔ **마스킹본이 생성된 적이 없다.** 규칙 BLOCK 경로에서만 발생한다.

| 경로 | status | decided_by | submitted_text |
|---|---|---|---|
| 규칙 BLOCK | BLOCKED | RULE | **NULL** (마스킹 미실행) |
| REVIEW → 사람 ACCEPT | BLOCKED | HUMAN | **마스킹본 보존** |
| REVIEW → 사람 REJECT | ALLOWED | HUMAN | 마스킹본 |
| PENDING_REVIEW | PENDING_REVIEW | (null) | 마스킹본 |
| MASK / ALLOW | MASKED / ALLOWED | RULE | 마스킹본 / 원문 |

**근거:** 규칙 BLOCK은 D5에 따라 `Masker`를 아예 호출하지 않아 본문이 존재하지 않는다. REVIEW 경로는 마스킹을 실행한 뒤 AI를 호출하므로 본문이 이미 있고, 담당자는 그것을 보고 확정한다. 확정 시점에 지우면 감사 담당자가 방금 판단한 근거가 사라져 D7이 막으려던 상황이 재발하고, "판단의 근거를 남긴다"는 서비스 핵심 가치(2.4)에 정면으로 어긋나며, 데모 1:50에서 ACCEPT를 누르는 순간 상세 패널 본문이 사라진다.

**불변식:** `decided_by='RULE' AND status='BLOCKED' ⇒ submitted_text IS NULL`

**실측 (DB):** BLOCKED/RULE 13건 전부 NULL · BLOCKED/HUMAN 1건(message 102, Case B 백업) 본문 보존 · PENDING_REVIEW 8건 전부 본문 · ALLOWED 56 / MASKED 25 전부 본문.

## 구현 영향

| 팀원 | 반영할 것 |
|---|---|
| `api-ai-architect` | **PATCH는 `submitted_text`를 지우지 않는다.** ACCEPT로 BLOCKED가 되어도 본문을 보존한다 |
| `rule-engine-dev` | 변경 없음. 규칙 BLOCK에서 마스킹 미실행 → NULL은 그대로 |
| `integration-qa` | 불변식을 `BLOCKED ⇒ NULL`이 아니라 `decided_by='RULE' AND BLOCKED ⇒ NULL`로 검증한다. Case B ACCEPT 후 상세 패널에 본문이 남는지 확인 |

---

# D15 — SCR-01 직원 발화 버블의 표시 내용 (2026-09-02, 구현 중)

`frontend-dev`가 S4(BLOCK)에서 발화 버블을 "전송이 차단되어 전송 본문이 기록되지 않았습니다"로 그렸다. 기획서 5.3 도해는 원문을 그리는데 `submittedText`가 null이고 원문 미표시 원칙과 충돌한다고 판단한 결과다.

**결정:** 직원 발화 버블은 **사용자가 방금 입력한 로컬 텍스트**를 표시한다. BLOCK일 때도 마찬가지이며, 판정 카드가 차단 사유를 덧붙인다.

**근거:** 원문 미표시 원칙(5.4)의 대상은 **감사 콘솔에서 타인의 원문**이다. 챗 화면에서 작성자 본인에게 방금 자기가 친 텍스트를 돌려주는 것은 유출이 아니다. 5.3 인터랙션 규칙이 이미 BLOCK 시 입력창에 원문을 복원하도록 지시하므로 같은 텍스트가 이미 화면에 있고, 버블만 가리는 것은 일관되지 않다.

이 텍스트는 API 응답이 아니라 FE 로컬 상태에서 온다. `submittedText`는 판정 결과 표시에만 쓴다. API 변경은 없다.

**데모 영향:** 15.1 오프닝이 "붙여넣은 스택 트레이스 안에 접속 문자열과 주민번호가 있습니다"로 화면을 가리킨다. 버블이 비면 그 장면이 성립하지 않는다.

## 구현 영향

| 팀원 | 반영할 것 |
|---|---|
| `frontend-dev` | S4 버블에 입력 원문 표시. **ACCEPT 후 `submittedText`를 로컬에서 null로 만들지 않는다** (D14) |
| `api-ai-architect` | PATCH 응답에 `submittedText`를 포함해 FE가 추론하지 않게 한다 |

---

# D16 — `aiStatus` 화면 표기 (2026-09-02, QA 후)

QA F7이 "구현이 계약서 §3과 다르다"로 올렸으나, 기획서 5.6에 `aiStatus` 값이 애초에 없어 **미고정 용어**였다. `spec-steward`가 판정하고 리더가 승격했다.

| 내부 값 | 표기 |
|---|---|
| SKIPPED | **(공란)** |
| PENDING | **분석 중** |
| COMPLETED | **분석 완료** |
| FAILED | **분석 실패** |

**원칙: `aiStatus`는 AI의 상태이므로 "분석", `message.status`·`review_status`는 사람의 절차이므로 "검토".**

**근거.** "검토"는 5.6이 이미 사람에게 예약한 단어다(`PENDING_REVIEW`→검토 대기, `REVIEW`→검토). 감사 목록 한 행에 판정 "검토 대기"와 AI 상태가 나란히 서는데 후자까지 "검토 중"이면 한 행에 "검토"가 두 번 나와 행위 주체가 구분되지 않고, 4장 책임 경계가 화면에서 흐려진다. SKIPPED 공란은 5.4의 명시 지시이며 SSOT 계층상 계약서의 "미실행"보다 우선한다 — Case A 행의 빈 칸이 데모 0:44 "AI는 호출되지 않았습니다"를 목록에서 한 번 더 증명한다.

**적용 범위는 SCR-02뿐이다.** 직원 화면(SCR-01)의 "보안 검토 중"·"자동 검토 실패 — 담당자 확인 중"은 기획서 5.3이 고정한 프로세스 안내 문장이라 그대로 둔다. 기준은 **값 라벨은 행위 주체를 구분하고, 안내 문장은 기획서 문구를 따른다**.

## 구현 영향

| 팀원 | 반영할 것 |
|---|---|
| `frontend-dev` | `src/lib/terms.js:26-31`의 `AI_STATUS_TERMS` 4값, `src/views/AuditView.vue:370` 문장. **`PendingIndicator.vue`와 `ChatView.vue:208`은 변경하지 않는다** |
| `api-ai-architect` | 계약서 §3의 화면 표기 열 갱신 |

## 재발 방지

CONFIRMED 건(D6 잔여)과 원인이 같다 — 5.6에 없는 값을 용어표 승격 없이 `screen-spec.md`에서 확정했다. **5.6에 없는 값을 화면에 쓰려면 먼저 용어표 승격을 요청한다.**
