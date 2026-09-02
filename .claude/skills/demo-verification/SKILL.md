---
name: demo-verification
description: "사내 AI 게이트웨이의 통합 정합성 검증과 데모 E2E 검증을 수행하는 스킬. FE-BE-DB 경계면 교차 비교, 데모 케이스 A·B·C·D 판정 확인, 상태 전이 완전성 추적, 제출물 체크리스트 확인을 다룬다. 검증·QA·E2E·통합 테스트·데모 리허설·'데모 케이스 확인', '연동 확인', '판정이 화면과 다름', '제출물 점검' 요청에 반드시 사용. 재검증·부분 재실행 요청에도 사용."
---

# Demo Verification — 통합 정합성 및 E2E 검증

각 모듈이 정상인데 연결에서 어긋나는 것이 이 프로젝트의 주된 실패 방식이다. 이 스킬은 **존재 확인이 아니라 연결 확인**을 한다.

## 실행 시점

전체 완성 후 한 번이 아니라 **각 모듈 완성 직후 점진적으로** 실행한다. 나중에 몰아서 하면 버그가 누적되고 초기 불일치가 후속 모듈로 전파된다.

| 완성 신호 | 즉시 검증할 것 |
|---|---|
| 시드 적용 완료 | 테이블 count, 정규식 왕복 |
| `POST /messages` 완성 | 데모 케이스 A·B·C·D 백엔드 판정 |
| API 응답 DTO 확정 | DB 컬럼 ↔ DTO 필드 ↔ 계약서 3단 대조 |
| FE 화면 하나 완성 | 그 화면이 참조하는 필드 ↔ 실제 응답 |
| @Async 202 완성 | 202 본문에 없는 필드를 FE가 참조하는지 |
| 전체 연동 | 데모 케이스 E2E |

## 양쪽 동시 읽기

경계면 검증은 반드시 양쪽 코드를 **같이 열어** 비교한다. 한쪽만 보면 각각 정상으로 보인다.

| 검증 대상 | 왼쪽 (생산자) | 오른쪽 (소비자) |
|---|---|---|
| 응답 shape | `api/dto/*.java` | `frontend/src/api/*.js` + 컴포넌트 참조 필드 |
| 필드 명명 | DDL 컬럼 (snake_case) | 엔티티 → DTO (camelCase) → FE 참조 |
| 목록 봉투 | `{items,page,size,total}` | FE가 `.items`를 꺼내는가 |
| 비동기 | 202 응답 본문 | FE가 202 시점에 `aiAssessment`를 보는가 |
| 상태 전이 | `status` / `ai_status` 업데이트 코드 전부 | FE의 `status === '...'` 분기 값 |
| 라우팅 | `router/index.js` | 헤더 탭 · `router.push` 값 |
| 규칙 | `policy_rule` 시드 | 데모 케이스 실제 판정 |

빌드 성공은 정상 동작이 아니다. 실제 응답 JSON을 확인한다.

## 이 프로젝트의 고위험 경계면

과거 유사 프로젝트에서 실제로 터진 패턴을 이 도메인에 대응시킨 것이다. 우선 검증한다.

| # | 경계면 | 증상 | 확인 방법 |
|---|---|---|---|
| 1 | 202 → 폴링 → COMPLETED | 202 직후 화면 크래시 | 202 응답 JSON에 `aiAssessment`가 없음을 확인하고, FE에서 해당 필드 참조부가 `aiStatus==='COMPLETED'` 가드 안에 있는지 확인 |
| 2 | 403 BLOCK | 차단이 "통신 오류"로 표시 | Axios 인터셉터가 403 + `decision==='BLOCK'`을 정상 경로로 넘기는지 |
| 3 | 목록 봉투 | `filter is not a function` | `GET /inspections` 응답이 배열인지 봉투인지, FE가 `.items`를 꺼내는지 |
| 4 | snake↔camel | 값이 전부 undefined | `ai_status`→`aiStatus`, `submitted_text`→`submittedText`, `decided_by`→`decidedBy`, `review_status`→`reviewStatus` |
| 5 | span 좌표계 | 하이라이트가 밀림 | FE가 오프셋 산술을 하는지(하면 안 됨), 라벨 검색 방식인지 |
| 6 | `decidedBy` 전이 | 확정해도 `RULE`로 남음 | PATCH 후 `decidedBy`가 `HUMAN`으로 바뀌는지 |
| 7 | `CONFIRMED` finding | 규칙 판정에 ACCEPT 버튼이 뜸 | SCR-02가 `reviewStatus==='SUGGESTED'`일 때만 버튼을 노출하는지 |
| 8 | `ruleCount` | 규칙 수가 과다 표시 | `source='RULE'`만 세는지, AI finding이 섞이지 않는지 |
| 9 | 엔드포인트 ↔ 호출 | 만들었는데 안 씀 | 7개 엔드포인트 각각에 대응 FE 호출이 존재하고 실제 실행되는지 |

## 상태 전이 완전성

정의된 전이 중 코드에서 실행되지 않는 것(죽은 전이)과, 코드에 있으나 정의에 없는 것(무단 전이)을 양방향으로 찾는다.

| 대상 | 전이 |
|---|---|
| `ai_status` | SKIPPED(종단) · PENDING→COMPLETED · PENDING→FAILED |
| `message.status` | ALLOWED / MASKED / BLOCKED(종단) · PENDING_REVIEW→BLOCKED(HUMAN) · PENDING_REVIEW→ALLOWED(HUMAN) |
| `review_status` | CONFIRMED(규칙, 종단) · SUGGESTED→ACCEPTED · SUGGESTED→REJECTED |
| `decided_by` | null→RULE · null→HUMAN |

**특히 확인할 것:** `PENDING_REVIEW → BLOCKED` 전이가 `ReviewService`에 실제로 구현되어 있는가. 중간 상태에서 최종 상태로 가는 전이의 누락이 가장 흔하며, 증상은 "확정을 눌러도 화면이 검토 대기에 머무름"이다. 데모 1:50 장면이 여기서 죽는다.

`ai_status=FAILED`일 때 `message.status`가 `PENDING_REVIEW`를 유지하는지도 확인한다. ALLOWED로 떨어지면 검사되지 않은 프롬프트가 통과 기록으로 남는다.

## 데모 케이스 E2E

기획서 10.4의 입력 문자열을 **한 글자도 바꾸지 않고** 실행한다.

| 케이스 | 계정 | 기대 판정 | 규칙 | HTTP | 화면 |
|---|---|---|---|---|---|
| A | 이OO (개발팀) | BLOCK | SEC-DBURL-02, PII-RRN-01 (2건) | 403 | S4 |
| B | 김OO (영업팀) | REVIEW→AI 후보 1건→ACCEPT→BLOCKED | CONF-CLIENT-01 | 202→200 | S5→SCR-02 |
| C | 이OO (개발팀) | ALLOW | 0건 | 200 | S2 |
| D | 정OO (인사팀) | MASK | PII-PHONE-03 | 200 | S3 |

A의 규칙 건수가 2건인 근거(중첩 span 억제)는 `spec-contract` 스킬 `references/open-questions.md` Q1을 읽는다. 화면 표시와 발표 대사가 여기에 걸려 있다.

**B와 C는 같은 문장이다.** 부서만 다르고 결과가 갈리는 것이 이 프로젝트의 핵심 증명이며 데모 2:40의 장면이다. 하나만 통과하면 증명이 성립하지 않는다.

B는 4단계 전부 확인한다: 202 수신 → 스피너 2.5초 노출 → COMPLETED 후 AI 후보 표시 → SCR-02에서 ACCEPT → `BLOCKED`/`HUMAN` 반영. 중간 단계가 빠지면 비동기 설계가 증명되지 않는다.

추가로 실패 경로를 확인한다: `__FAIL__`을 포함한 입력 → `ai_status=FAILED`, `message.status`는 `PENDING_REVIEW` 유지.

## 리포트 형식

`_workspace/03_integration-qa_report.md`에 쓴다. **통과 / 실패 / 미검증을 반드시 구분한다.** 아직 구현되지 않은 것을 실패로 세면 리포트가 쓸모없어진다.

```markdown
## 요약
통과 N · 실패 N · 미검증 N · 데모 차단 이슈 N

## 실패
| # | 경계면 | 증상 | 근거 | 원인 계층 | 담당 | 데모 차단 |
|---|---|---|---|---|---|---|
| 1 | ... | ... | `파일:라인` | 응답 매핑 | api-ai-architect | 예 |

## 미검증
| 항목 | 사유 |

## 통과
| 항목 | 근거 |
```

발견은 **파일:라인 + 수정 방법**으로 보고한다. "불일치가 있습니다"만으로는 아무도 못 고친다.

경계면 이슈는 **양쪽 에이전트 모두에게** 알린다. 한쪽만 고치면 반대 방향으로 어긋난다.

## 원인 계층 특정

데모 케이스가 기대와 다를 때 계층을 좁혀서 보고한다. 특정하지 않으면 담당자끼리 서로 미룬다.

시드 → 정책 로드 → 정규식 매칭 → 중첩 억제 → 충돌 해결 → 마스킹 → 응답 매핑 → FE 렌더링

각 계층의 확인 방법은 `rule-engine-impl` 스킬의 "실패 시 원인 계층"을 읽는다.

## 고치지 않고 보고한다

담당 에이전트가 고치는 것이 원칙이다. 한 줄 수준의 명백한 오타만 고치고 통보한다. QA가 직접 고치면 담당자가 같은 실수를 반복하고, 수정이 다른 곳을 깨뜨렸을 때 아무도 모른다.

## 제출물 체크리스트

기획서 부록 C 중 **검증 가능한 항목**만 확인한다. Figma 링크나 슬라이드 같은 사람 산출물은 `spec-steward`의 추적 대상이다.

- [ ] README에 실행 방법·환경변수 목록·데모 케이스 문자열
- [ ] Flyway V1·V2 적용 확인 (테이블 count 대조)
- [ ] Postman 컬렉션 export가 저장소에 있음
- [ ] Swagger UI 접근 가능
- [ ] 프롬프트 전문·JSON 스키마·Mock 픽스처 3종 존재
- [ ] 픽스처가 9.4 스키마 검증 통과
- [ ] FE·BE 폴더 구조가 기획서 11.4와 일치
- [ ] E2E 케이스 A·B·C·D 결과 기록 (`docs/e2e-result.md`)
