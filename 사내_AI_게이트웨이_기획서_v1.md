# 사내 생성형 AI 게이트웨이 — 프로젝트 기획서 v1.0

| 항목 | 내용 |
|---|---|
| 과정 | SKALA Full-Stack Engineering / AI 웹 서비스 설계 Mini-project |
| 기간 | 2026.09.02(수) ~ 09.04(금) — 3일 |
| 팀 구성 | 4인 (가이드상 Scope 축소 대상) |
| 문서 목적 | 팀원이 이 문서만으로 Figma·ERD·API 명세·Mock·스캐폴딩 작업을 병렬로 시작할 수 있도록 함 |
| 문서 독자 | 팀원 4인, 교수 검토용 |
| 상태 | 1일차 교수 피드백 반영 + 미결 항목 D1~D16 결정 반영본 (2026.09.02) |

---

## 목차

0. 교수 피드백 반영 및 범위 결정
1. 가이드라인 필수 요소 체크리스트
2. 서비스 개요
3. Actor 및 Use-Case
4. 책임 경계 설계 (Rule / AI / Human)
5. 화면 설계 — Figma 작업 지침
6. 데이터 모델 — ERD 작업 지침
7. 정책 및 규칙 정의
8. REST API 명세
9. AI 확장 지점 상세 — 프롬프트·스키마·Mock
10. 시드 및 Mock 데이터 계획
11. 시스템 아키텍처 및 프로젝트 구조
12. R&R (4인)
13. 3일 일정
14. 리스크 및 대응
15. 발표 및 데모 계획
16. 예상 질의 및 답변
17. 향후 확장 로드맵
부록 A. 용어 정의
부록 B. dbdiagram.io DBML 초안
부록 C. 제출물 체크리스트

---

## 0. 교수 피드백 반영 및 범위 결정

### 0.1 피드백 요약

1일차 교수 피드백을 다섯 가지로 정리하였습니다.

| # | 피드백 | 해석 |
|---|---|---|
| F1 | 어느 회사나 고민하는 것, 회사의 가려운 곳을 긁어주는 주제 | 문제 정의는 타당함. 그대로 유지함 |
| F2 | 보안이 필요한 내용, 비효율적인 프롬프트는 비용을 발생시킴. 회사에서 프롬프트와 답변을 기록해 가이드라인을 만들기도 함 | 기록(로그)이 보안 감사뿐 아니라 프롬프트 자산화에도 쓰임. 기록 자체가 핵심 자산이라는 관점 |
| F3 | 해당 서비스에서 AI API를 이용하는 부분은 어디인가. 적극 활용하려면 로컬 LLM이 중요 | AI 확장 지점을 한 곳으로 명확히 특정해야 함. 외부 API가 아닌 사내 호스팅 전제로 설계할 것 |
| F4 | 지속 활용 가능 여부 고려, RAG도 붙일 수 있음 | 향후 확장 경로를 구체적으로 제시할 것 |
| F5 | 회사 내부에서 고민이 많고 프로젝트도 많음. 볼륨 조절이 필요하며 여러 가지 중 하나, 둘 정도만 다룰 것 | 범위를 명시적으로 잘라야 함 |

### 0.2 범위 결정 — "하나, 둘"

이 서비스가 다룰 수 있는 주제는 여섯 가지입니다. 이 중 두 가지만 구현하고 나머지는 설계 문서와 향후 확장에만 둡니다.

| 주제 | 결정 | 사유 |
|---|---|---|
| ① 입력 프롬프트의 민감정보 검사·마스킹·차단 + 감사 기록 | **구현 (하나)** | 서비스의 본체. 규칙 엔진만으로 완결되며 AI 없이 동작함 |
| ② AI 맥락 판정 확장 지점 (Mock) | **구현 (둘)** | 규칙이 못 잡는 맥락형 기밀을 AI가 후보로 제안하는 자리. 과제의 AI-Ready 요구사항 |
| ③ AI 응답(출력) 검사 | **구현 (셋)** | 설계만 하려던 항목. `phase` 컬럼을 남겨 둔 덕에 입력 파이프라인을 그대로 재사용할 수 있어 3일 안에 들어왔다 (0.6) |
| ④ 프롬프트 효율·비용 관리 (F2) | 향후 확장 | 같은 로그를 재활용하는 두 번째 용도. 구현 시 범위가 두 배가 됨 |
| ⑤ 첨부파일 검사 | **표 파일만 구현** | 문서 파싱·OCR은 여전히 범위 밖. 스프레드시트는 프론트엔드가 텍스트로 뽑아 기존 전송 경로로 보낸다 — 백엔드에 첨부 API를 만들지 않고도 검사 대상이 된다 (0.5 D17, 0.6) |
| ⑥ 정책 편집 UI, 정책 시뮬레이터 | 설계만 | 정책은 시드 데이터로 고정하고 조회 API만 제공함 |

F2의 "기록을 모아 가이드라인을 만든다"는 관점은 서비스 정의에 반영하되 구현하지 않습니다. 감사 로그 테이블이 보안 감사와 프롬프트 자산화 두 용도를 모두 감당할 수 있는 구조라는 점을 설계에서 보여주는 것으로 충분합니다.

### 0.3 구현 범위 최종 확정

| 구분 | 만드는 것 | 만들지 않는 것 |
|---|---|---|
| 화면 | 2개 — 직원 AI 챗, 관리자 감사 콘솔 | 정책 편집, 시뮬레이터, 로그인 |
| 테이블 | Core 9개 (DDL 실행) | Future 4개 (Logical Model에만 표기) |
| API | 7개 | 정책 생성·수정, 첨부 업로드 |
| 규칙 엔진 | REGEX 6종 + KEYWORD 2종, 부서별 매핑, 충돌 해결 | 체크섬 검증, 마스킹 전략 다양화 |
| AI | AiInspector·AnswerClient 인터페이스 + Mock 구현체 + 202 비동기, 제공자 교체(Mock·Gemini·Claude) | 임베딩, 벡터 검색, 사내 모델 호스팅 |
| 검사 범위 | 입력 프롬프트, 받은 답변(phase=OUTPUT), 표 파일에서 뽑은 텍스트 | 문서·이미지 첨부, OCR |

### 0.4 AI 구현 여부

**모델을 만들지 않습니다.** 가이드 2일차 항목에 "AI 서비스는 Mock API로 JSON 반환"이 명시되어 있고, 정량 루브릭 8개 기준에 AI 구현 항목이 없습니다. 요구되는 것은 다음 네 가지이며 모두 백엔드·프론트엔드 작업으로 충족됩니다.

- 프롬프트 설계 문서 (9.2)
- 입출력 JSON 스키마 (9.4)
- 스키마대로 반환하는 Mock 엔드포인트 (9.5)
- Mock 응답을 화면에 바인딩한 시연 (15.2)

**다만 실제 모델 호출도 함께 둡니다.** Mock이 최소 요구이지 상한은 아니고, 가이드 4쪽의 AI-Ready 항목 두 가지가 정확히 그것을 요구하기 때문입니다 — 백엔드가 모델 API를 부르도록 바뀌어도 프론트엔드는 같은 JSON 규격을 쓸 것(Interface First), 키와 모델 파라미터를 코드에서 분리해 코드 변경 없이 교체할 수 있을 것(Config Isolation).

`AnswerClient` 인터페이스 하나에 구현체 셋이 붙고 `ANSWER_PROVIDER` 환경변수가 고릅니다. 화면은 어느 쪽이 답했는지 모릅니다 — 받는 JSON이 같기 때문입니다.

| 값 | 동작 | 쓰는 자리 |
|---|---|---|
| `mock` | 모델을 부르지 않고 답을 지어낸다 | 시연 기본값. 네트워크·키·할당량이 필요 없다 |
| `openai` | OpenAI 호환 엔드포인트 (Gemini·Groq·Ollama) | 실제 호출 시연 |
| `claude` | Anthropic SDK | 〃 |

**어느 쪽이든 그 뒤 검사 경로는 같습니다.** 갈리는 것은 답변 문장이 어디서 왔는가뿐이고 규칙 판정·유출 검사·확정 절차는 하나입니다.

### 0.5 미결 항목 결정

초안(v1.0) 정독 과정에서 문서 내부의 모순·미정 항목 6건을 발견하여 결정하였습니다. 이 항목들은 시드·엔진·화면 세 곳에 동시에 영향을 주므로 구현 착수 전에 확정합니다. 각 결정은 본문 해당 절에 반영되어 있으며, 아래 표는 결정 내역과 근거의 색인입니다.

| ID | 항목 | 결정 | 근거 | 반영 위치 |
|---|---|---|---|---|
| D1 | Case A 규칙 매칭 건수 (10.4는 3건, 8.4·15.2는 2건) | **2건.** 포함 관계 매칭은 finding을 생성하지 않음(중첩 억제) | 발표에서 실제로 읽히는 8.4·15.2가 2건. 같은 문자열을 두 규칙이 이중으로 세면 "규칙 N건"이 실제 위험 개수를 과장함 | 7.4, 7.6, 10.4 |
| D2 | 정보보안팀 부서 코드 미정 | code `INFOSEC`, name 정보보안팀 | 정책 코드 `P-SEC`와의 혼동을 피함. 코드가 없으면 app_user 시드가 FK 제약으로 실패 | 6.2, 7.3, 10.2 |
| D3 | span 재계산 주체 미정 (7.6) | **오프셋 산술을 하지 않음.** FE가 submitted_text에서 mask_label 문자열을 검색해 하이라이트 | 마스킹은 길이를 바꾸므로 누적 델타 계산이 필요하고, 틀리면 하이라이트가 조용히 밀림. 라벨은 대괄호로 시작하는 고유 형태라 오탐이 없음 | 5.3, 7.6 |
| D4 | 201 Created 미사용 (8.2에 정의, 사용처 없음) | **쓰지 않고 근거를 남김** | message 리소스는 생성되지만 클라이언트가 받아야 할 주 정보는 판정 결과임. 201+Location이면 판정을 알기 위해 한 번 더 요청해야 하고 BLOCK을 201로 표현할 방법이 없음 | 8.2, 16 |
| D5 | BLOCK 규칙에 mask_label이 없을 때의 마스킹 | **최종 판정이 BLOCK이 아닐 때만 마스킹 실행** | BLOCK 규칙(SEC-DBURL-02, SEC-AWSKEY-01)은 mask_label이 NULL이라 무조건 실행 시 오류. BLOCK은 submitted_text가 NULL이므로 마스킹 대상 자체가 없음 | 7.4, 7.5, 7.6 |
| D6 | review_status 값이 3개인지 4개인지 (6.2 나열은 3개, 같은 줄과 8.4 예시에 CONFIRMED 등장) | **4개.** SUGGESTED, ACCEPTED, REJECTED, CONFIRMED | CONFIRMED가 빠지면 규칙 finding INSERT가 CHECK 제약에 걸리거나, 화면이 규칙 판정에도 ACCEPT 버튼을 노출하여 "AI만 사람이 확정한다"는 책임 경계(4장)와 어긋남 | 5.6, 6.2, 부록 B |

2차 교차 검증(구현 착수 시점)과 구현·검증 과정에서 10건을 추가로 발견하여 결정하였습니다.

| ID | 항목 | 결정 | 근거 | 반영 위치 |
|---|---|---|---|---|
| D7 | 202 응답의 submittedText — 8.4의 202 예시는 null인데 같은 inspection(2090)의 GET 예시는 본문이 있음 | **BLOCK일 때만 NULL.** PENDING_REVIEW는 마스킹본을 채움. 202 예시가 틀렸음 | 감사 담당자가 검토해야 할 바로 그 건의 본문이 비면 SCR-02 상세 패널에 보여줄 것이 없음. AI에 넘기는 maskedText와 같은 값이라 따로 감출 이유도 없음 | 6.2, 7.5, 8.4 |
| D8 | 5.3 캡션의 "적용 정책 3건" — 7.3상 개발팀은 2건 | **2건** | 개발팀에는 P-PII·P-SEC만 적용됨. 데모 Case C의 "개발팀엔 고객사 정책이 매핑되지 않았다"는 대사와 화면이 충돌함 | 5.3 |
| D9 | KEYWORD 규칙에서 키워드가 여러 개 매칭될 때 finding 개수 | **finding은 규칙당 1건, hits[]는 키워드당 1건** | 8.4의 202 예시(match 1건 + matchedKeyword)와 9.3 조립 예시(hits 2건)가 이미 이 구조. 감사 목록의 "규칙 수"는 규칙 단위여야 5.4 도해와 맞음 | 7.4 |
| D10 | `decision_source`라는 스키마에 없는 필드명이 4장 표와 15.2 발표 대사에 사용됨 | **`decided_by` / JSON `decidedBy`로 통일** | 오기. 발표 대사에 들어 있어 Q&A에서 스키마와 다른 이름을 말하게 됨 | 4, 15.2 |
| D11 | Case A의 실제 정규식 매칭 건수 | **원시 3건 → 억제 2건.** (2026-09-02 D21로 갱신 — 이메일 정규식을 조여 `p%40ss@10.0.3.21` 오탐이 애초에 발생하지 않게 되었다. 그전에는 원시 4건이었고 PII-EMAIL-04가 억제 대상이었다) | 정규식을 실행해 확인함. `40ss@10.0.3.21`이 이메일로 오탐되나 SEC-DBURL-02 구간에 포함되어 억제됨. 단위 테스트 기대값을 이 과정으로 고정 | 10.4 |
| D12 | 폴링 종료 조건 자기모순 — 5.3이 "ai_status가 PENDING이 아니면 중단"과 "확정 후 결과를 폴링으로 반영"을 동시에 기술 | **폴링은 ai_status 기준으로만 종료.** 사람 확정 결과는 화면 재조회로 반영 | 사람의 확정 시점은 예측할 수 없어 폴링으로 따라가면 무한 폴링이 됨. 기획서가 이미 데모에서 새로고침을 지시하고 있었음 | 5.3 |
| D13 | review_status=CONFIRMED인 규칙 finding에 PATCH가 오면 | **409.** 에러 코드로 ACCEPTED/REJECTED 재요청과 구분 | 규칙 판정은 사람이 번복하지 않음(4장, 이번 범위). 200을 주면 책임 경계가 화면에서 무너짐 | 8.4 |
| D16 | `aiStatus`의 화면 표기 — 5.6에 값이 없어 미고정 상태였고 구현은 `검토 중/검토 완료/자동 검토 실패`를 사용 | **`(공란) / 분석 중 / 분석 완료 / 분석 실패`.** SCR-02에만 적용하고 직원 화면(5.3) 안내 문구는 그대로 둔다 | "검토"는 5.6이 이미 사람의 절차에 예약한 단어다. 감사 목록 한 행에 판정("검토 대기")과 AI 상태가 나란히 서는데 둘 다 "검토"면 행위 주체가 구분되지 않아 4장 책임 경계 주장이 화면에서 흐려진다. SKIPPED 공란은 5.4의 명시 지시이며, Case A 행의 빈 칸이 데모 0:44 "AI는 호출되지 않았습니다"를 목록에서 한 번 더 증명한다 | 5.6 |
| D15 | SCR-01 직원 발화 버블이 BLOCK일 때 무엇을 표시하는가 — 5.3 도해는 원문을 그리는데 `submittedText`가 null이고 원문 미표시 원칙과 충돌 | **사용자가 방금 입력한 로컬 텍스트를 표시한다.** BLOCK도 동일하며 판정 카드가 차단 사유를 덧붙인다 | 원문 미표시 원칙(5.4)의 대상은 **감사 콘솔에서 타인의 원문**이다. 챗에서 작성자 본인에게 자기 입력을 돌려주는 것은 유출이 아니다. 5.3이 이미 BLOCK 시 입력창에 원문을 복원하도록 지시하므로 같은 텍스트가 이미 화면에 있고, 버블만 가리는 것은 일관되지 않다. 15.1 오프닝이 그 화면을 가리키므로 버블이 비면 장면이 성립하지 않는다. 이 텍스트는 API가 아니라 FE 로컬 상태에서 온다 | 5.3 |
| D14 | D7의 "BLOCK이면 NULL"이 필요조건인지 필요충분조건인지 | **필요조건.** `submitted_text IS NULL`은 "마스킹본이 생성된 적 없음"을 뜻하고 규칙 BLOCK 경로에서만 발생한다. 사람이 확정한 BLOCK은 본문을 보존하며, PATCH는 `submitted_text`를 지우지 않는다 | 규칙 BLOCK은 D5에 따라 마스킹을 아예 실행하지 않아 본문이 없다. REVIEW 경로는 마스킹을 실행한 뒤 AI를 호출하므로 본문이 이미 있고 담당자는 그것을 보고 확정한다. 확정 시점에 지우면 ①감사 담당자가 방금 판단한 근거가 사라져 D7이 막으려던 상황이 재발하고 ②판단의 근거를 남긴다는 서비스 핵심 가치(2.4)에 어긋나며 ③데모 1:50에서 ACCEPT를 누르는 순간 상세 패널 본문이 사라진다 | 6.2, 7.5, 8.4 |

D1과 D5는 뿌리가 같습니다. Case A 입력의 `postgres://admin:p%40ss@10.0.3.21/prod`에서 SEC-DBURL-02의 패턴(`[^\s]+`)이 공백 전까지 통째로 매칭하므로 사설 IP `10.0.3.21`을 잡는 SEC-PRIVIP-03의 구간이 그 안에 완전히 포함됩니다. 중첩을 억제하면 건수 문제와 라벨 충돌 문제가 함께 해소됩니다.

#### 0.5.1 엠바고 정책 추가에 따른 결정 (2026-09-02)

기존 3종 정책은 **정보가 민감해서** 통제합니다. 엠바고는 **아직 때가 아니라서** 통제하며, 같은 문장이 해제일 다음 날에는 그냥 통과합니다. 이 성질의 차이가 아래 네 결정을 낳았습니다.

| ID | 항목 | 결정 | 근거 | 반영 위치 |
|---|---|---|---|---|
| D17 | 파일(엑셀) 입력 경로 — 0.3이 첨부파일과 첨부 업로드 API를 범위 밖으로 명시함 | **프론트엔드에서 텍스트로 추출해 기존 `POST /messages` 경로로 전송한다.** 백엔드·엔진 무변경 | 시연이 보여줄 장면("엑셀을 넣었더니 차단됨")은 이 방식으로 동일하고, 검사 대상은 여전히 입력 프롬프트라 0.3을 위반하지 않는다. 17장 확장 4번("추출기만 추가, 엔진 무변경")이 그대로 성립한다. 다만 **파일 형식 검증은 방어가 아니라 사용자 안내**다 — 파일이 프론트에서만 열리므로 그렇게 설명해야 한다 | 0.3, 17 |
| D18 | 홍보팀 부서 신설 | **`PR / 홍보팀` 추가.** 사용자 1명(한OO)과 감사 로그 5건을 함께 시드한다 | `department.code`에 CHECK 제약이 없어 INSERT만으로 추가된다. 로그를 함께 넣는 이유는 감사 콘솔 부서 필터에서 홍보팀을 골랐을 때 0건이 나오는 자리가 시연 중에 눌릴 수 있기 때문이다 | 6.2, 7.3, 10.2 |
| D19 | "홍보팀이 만든 정책"의 표현 — `department_policy`는 적용 부서이지 소유 부서가 아님 | **`policy.owner_dept_id` 컬럼을 신설한다** | 엠바고는 홍보팀이 걸고 개발팀·영업팀이 걸린다. 두 방향을 한 매핑으로 표현하면 "누가 정한 규칙인가"에 답할 수 없고, 그 답이 없으면 차단이 납득되지 않는다 | 6.2, 7.1, 8.4 |
| D20 | 엠바고 해제일의 저장 위치와 경계 | **`policy_rule.embargo_until DATE` 신설. 의미는 해제일이며 차단 조건은 `today < embargo_until`이다** (경계일 당일은 이미 풀린 것). 만료된 규칙은 매칭시키지 않되 `appliedRuleCodes`에는 남긴다 | 날짜를 코드나 화면에 하드코딩하면 "정책·규칙·임계값은 DB"라는 11.3 주장이 무너진다. "○○일까지 불가"로 읽으면 하루가 어긋나고 그 하루가 발표 당일일 수 있어 부등호를 문서에 고정한다. 만료 규칙을 `appliedRuleCodes`에 남기는 것은 D1 중첩 억제와 같은 논리다 — 적용된 규칙과 매칭된 규칙은 다르다 | 6.2, 7.2, 7.4, 8.4 |

| D21 | 마스킹 규칙의 오탐 — `\b` 누락, 구분자 혼용 허용, 이메일 TLD 미검증 | **PII 4종 경계 조이기 + 사업자등록번호·계좌번호 2종 추가.** P-PII 버전 3 → 4 | 재현율만 올리면 오탐이 늘고, 오탐이 늘면 사용자가 게이트웨이를 우회한다. 그때 통제율은 0이 된다. 후보 패턴을 Java 엔진으로 양성 15·음성 12 케이스에 돌려 확인했으며 데모 Case A·D의 span은 그대로다 | 7.1, 7.2, 8.4 |

정책 버전을 올린 이유는 감사 무결성입니다. 규칙이 바뀌었는데 버전이 같으면 어제의 판정과 오늘의 판정이 같은 근거를 가리키게 되어 기록 자체가 거짓이 됩니다. 기존 감사 로그 100건의 `policy_snapshot`은 `P-PII:3`으로 남고, 이후 판정만 `P-PII:4`가 됩니다 — 시점 보존이 설계대로 동작하는 자리입니다.

| D23 | 고객 이름 탐지 — 정규식으로 일반화 불가, NER은 0.4가 범위 밖 | **`customer` 테이블 신설(Core 9번째) + `rule_type=ROSTER`.** 전체 일치는 MASK, 이름 부분만 일치는 REVIEW. 시드 명단은 합성이며 실 데이터는 운영 DB에 적재한다 | 탐지 문제가 아니라 데이터 문제다. 고객 명단을 사내 DB에 두는 것은 정상이고 막아야 할 것은 외부 전송이다. 명단을 규칙 pattern에 박으면 조직이 바뀔 때 코드를 고쳐야 하고, 감사 스냅샷에 그 시점 이름이 통째로 남는다 | 6.1, 6.2, 7.2, 10.1 |

| D24 | 확정(ACCEPT/REJECT)을 누가 할 수 있는가 — 0.3이 로그인을 뺐고 계약서 §1-7이 역할 검사를 하지 않기로 했음 | **역할은 검사한다.** SECURITY_ADMIN이 아니면 403 `FORBIDDEN_ROLE`. 인증은 여전히 없다 | "AI는 제안하고 사람이 확정한다"에서 사람이 아무나면 4장의 책임 경계가 성립하지 않는다. 화면에서만 버튼을 가리면 그 경계가 주장에 그치고, Q&A에서 "직원 계정으로 API를 직접 부르면요"에 답할 수 없다. X-User-Id는 누구인지 말할 뿐 그 사람임을 증명하지 않으므로 인증을 만든 것은 아니다 | 8.4, 계약서 §1-7 |

D20의 판정 기준일은 `gateway.embargo.reference-date`로 덮어쓸 수 있습니다. 비워 두면 실제 오늘이며, 발표 전 리허설에서 발표 당일을 흉내 낼 때만 채웁니다 (11.3).

### 0.6 범위를 넘어선 것 (2026-09-03)

3일차에 세 가지가 0.3의 "만들지 않는 것"에서 "만든 것"으로 넘어왔습니다. **범위를 늘리려고 한 것이 아니라 남겨 둔 자리가 실제로 비어 있었기 때문입니다** — 셋 다 기존 파이프라인을 재사용하고 엔진을 고치지 않습니다.

| 항목 | 원래 | 지금 | 왜 들어왔나 |
|---|---|---|---|
| 출력 검사 (UC-08) | 설계만 (0.2 ③) | 구현 | `inspection.phase`를 V1부터 남겨 둔 덕에 입력 파이프라인을 그대로 태우면 끝났다. 새로 쓴 것은 유출 검사 세 겹뿐이다 |
| 실제 LLM 호출 | 범위 밖 (0.3) | 구현 | 가이드 4쪽이 요구하는 Interface First·Config Isolation을 말이 아니라 증거로 보이려면 실제로 갈아 끼워 봐야 한다. Mock이 여전히 기본값이다 (0.4) |
| 표 파일 입력 | 설계만 (0.2 ⑤) | 구현 | D17이 정한 대로 프론트엔드가 텍스트로 뽑아 기존 전송 경로로 보낸다. 백엔드에 첨부 API가 없으므로 검사 대상은 여전히 입력 프롬프트다 |

**여전히 범위 밖인 것**은 그대로입니다 — 정책 편집 UI, 시뮬레이터, 로그인, 문서·이미지 첨부와 OCR, 임베딩·벡터 검색, 사내 모델 호스팅.

출력 검사가 들어오면서 한 가지가 늘었습니다. **한 발화에 검사가 둘**이 됩니다(보낸 프롬프트와 받은 답변). 두 검사는 각자 확정되며 서로의 판정을 덮지 않습니다 — 답변이 막혔다고 해서 이미 나간 프롬프트의 판정이 바뀌면 감사 기록이 거짓이 됩니다.

---

## 1. 가이드라인 필수 요소 체크리스트

가이드 문서의 요구사항을 전부 추출하고, 본 기획서의 대응 위치와 담당을 매핑하였습니다. 3일차 제출 전 이 표로 누락을 점검합니다.

### 1.1 일정별 필수 산출물

| 일차 | 가이드 요구 | 본 문서 위치 | 담당 | 상태 |
|---|---|---|---|---|
| 1 | AI-Ready Web Service 아이디어 선정 | 2장 | 전원 | 완료 |
| 1 | Teaming — R&R 정의 | 12장 | 전원 | 완료 |
| 1 | Actor 중심 Use-Case 정의 | 3장 | A | |
| 1 | AI 확장 지점 정의 | 3.4, 9장 | C | |
| 1 | UI/UX 화면 흐름도(Wireframe) | 5장 | A | |
| 1 | FE/BE/DB 프로젝트 생성, 기본 구조 | 11.4 | B, D | |
| 2 | 데이터 모델링(ERD) | 6장, 부록 B | B | |
| 2 | REST API 명세 (Mock API Endpoint 포함) | 8장 | C | |
| 2 | FE/BE API 연동, DB 연결 | 11장 | B, D | |
| 2 | 핵심 화면 1~2개 극소 구현 | 5장 | D | |
| 2 | AI 서비스 Mock API JSON 반환 | 9.5 | C | |
| 3 | 설계 문서 보완, 발표 자료 | 15장 | A | |
| 3 | Live Demo (기본 UI + Mock 데이터 흐름) | 15.2 | D | |
| 3 | Peer Review 제출 (조별 1개) | — | A | |

### 1.2 역할별 산출물 (가이드 R&R Guide 기준)

| Role | 가이드 산출물 | 담당 | 본 문서 위치 |
|---|---|---|---|
| PM | 최종 발표 슬라이드 | A | 15장 |
| Product/UX Designer | Use-Case, Wireframe | A | 3장, 5장 |
| Data Architect | ERD, Database 구성 | B (Joon) | 6장, 10장 |
| API Architect | API 명세서 & Postman Mock Server, AI-Ready (프롬프트 or JSON 규격) | C | 8장, 9장 |
| Frontend Developer | FE Scaffolding, 메인/핵심 페이지 UI | D | 5장, 11.4 |
| Backend Developer | BE Scaffolding, E2E 일부구현 (DB 반영) | B (Joon) | 8장, 11.4 |
| DevOps & Integration | GitHub Repository 세팅, E2E 테스트 결과 | D | 11.5, 14장 |

### 1.3 정량 루브릭 8개 기준 대응

| 영역 | 세부 기준 | 대응 내용 | 위치 |
|---|---|---|---|
| 기획·아키텍처 (30) | Use-Case 정의 및 Figma 완성도 | Actor 3 · UC 6개, 화면 2개 상태별 명세 | 3, 5 |
| | AI 확장 지점 및 프롬프트/JSON 스키마 타당성 | 확장 지점 1곳 특정, 프롬프트 전문, 스키마, Mock 규칙 | 9 |
| | GitHub 관리 및 R&R 적절성 | 브랜치 전략, 4인 배분과 병목 시점 | 11.5, 12 |
| | FE-BE-DB 구조 다이어그램 명확성 | 4계층 구조도 + AiInspector 교체 지점 | 11.1 |
| 설계·스캐폴딩 (30) | ERD 관계(1:N, N:M) 및 정규화 | N:M 1개, 1:N 6개, JSONB 사용 근거 명시 | 6 |
| | Mock API 완성도 및 RESTful 규격(Method, Path, Status Code) | 7 엔드포인트, 200/202/400/403/404/409 사용 근거 (201 미사용 근거 포함) | 8 |
| | FE/BE 구조 및 DB 연동 | 폴더 구조, application.yml, Cloud PostgreSQL | 11 |
| | Mock API 데이터 바인딩 및 화면 시연 | 데모 케이스 3종, 결정론적 Mock | 10.4, 15.2 |

### 1.4 AI-Ready 4대 원칙 대응

| 원칙 | 가이드 정의 | 본 설계에서의 구현 |
|---|---|---|
| Interface First | BE가 AI API를 호출하도록 바뀌어도 FE의 Mock 규격을 유지 | AiInspector 인터페이스 뒤에서 Mock↔LLM 교체. FE가 보는 엔드포인트와 JSON은 불변 |
| Structured Data | AI가 읽기 쉬운 JSON 규격과 메타데이터를 DB에 사전 반영 | 판정 결과를 inspection.rule_result / ai_result JSONB로 저장. 스키마 변경 없이 필드 확장 가능 |
| Asynchronous Pipeline | 비동기 처리와 Pending/Completed 상태 관리 | 규칙 판정은 동기 200/403, AI 판정 필요 시 202 + 폴링. ai_status 컬럼으로 상태 관리 |
| Security & Config Isolation | API Key, Model Parameter를 코드와 분리 | application.yml의 ai.* 항목을 환경변수로 주입. 정책 임계값은 policy_rule 테이블 |

### 1.5 발표 6개 섹션 필수 내용

| 순서 | 섹션 | 시간 | 필수 포함 | 본 문서 위치 |
|---|---|---|---|---|
| 1 | 서비스 기획 & Use-Case | 3분 | 한 줄 정의, 페르소나, Actor·UC 요약 | 2, 3 |
| 2 | AI-Ready 설계 포인트 | 2분 | 확장 지점, 프롬프트, JSON 스키마 | 4, 9 |
| 3 | 시스템 아키텍처 & 설계 | 4분 | 구조도, ERD, API 명세 요약 | 6, 8, 11 |
| 4 | Scaffolding & 데모 | 4분 | 폴더 구조, FE/BE 연동, Mock 시연 | 11.4, 15.2 |
| 5 | 회고 및 향후 확장 | 2분 | 한계점, AI 결합 로드맵, R&R별 회고 | 17 |
| 6 | Q&A | 5분 | 타 조 질의 대응 | 16 |

---

## 2. 서비스 개요

### 2.1 한 줄 정의

직원이 사내 AI에 입력한 프롬프트를 부서별 정책으로 검사해, 허용·마스킹·차단을 근거와 함께 기록으로 남기는 통제·감사 웹 서비스입니다.

AI가 주어가 아니라 목적어입니다. 이 서비스는 AI 기능을 제공하지 않고, AI 사용을 관리합니다. 발표 전반에서 이 구분을 유지합니다.

### 2.2 문제 정의

사내 AI 도입 후 발생하는 유출은 공격이 아니라 승인된 사용 안에서 생깁니다. 직원이 에러 로그를 통째로 붙여넣으면서 접속 문자열이 함께 나가고, 고객 응대 메모를 정리하다가 주민번호가 섞여 나갑니다. 전면 차단은 개인 계정으로의 우회를 부르고, 전면 허용은 기록 없는 유출을 방치합니다.

세 가지 문제로 정리합니다.

- 무엇이 입력되었는지 확인할 기록이 없음
- 부서마다 허용 범위가 달라야 하는데 단일 기준으로 관리됨
- 패턴으로 잡히는 정보(주민번호)와 맥락으로만 잡히는 정보(고객사 프로젝트 일정)가 섞여 있어 한 가지 방식으로는 대응이 안 됨

### 2.3 페르소나

| 항목 | 내용 |
|---|---|
| 이름 | 김도현 (41) |
| 소속 | 임직원 210명, 개발자 80명 규모 IT 서비스 기업 정보보안팀 과장 |
| 겸직 | 개인정보보호 실무, 보안 교육 |
| 상황 | 3개월 전 사내 AI 챗 도입. 사용량은 늘었으나 입력 내용을 확인할 수단이 없음 |
| 발언 | "막으면 일 못 한다고 하고, 열어두면 사고 나면 제가 책임집니다. 그런데 뭐가 나갔는지를 모릅니다." |
| 원하는 것 | 차단이 아니라 가시성. 사고가 났을 때 소명할 수 있는 기록 |

### 2.4 핵심 가치

- 통제 증거를 자동으로 축적함 — 누가, 언제, 어떤 정책으로, 무엇이 판정되었는지
- 부서별 기준을 차등 적용함 — 개발팀의 코드 공유와 영업팀의 고객사 언급은 다른 정책
- 막지 않고 계속 쓰게 함 — 마스킹 후 통과가 기본, 차단은 확정적 위반에만
- 판단의 근거를 남김 — 규칙 ID, 정책 버전, AI 제안의 rationale이 기록에 붙음

### 2.5 시장 위치

국내에 파수 AI-R DLP, 컴트루 Sphinx AI, 지란지교 PCFILTER 등 AI 전용 DLP 제품이 있고, 삼성SDS SGuard-v1과 카카오 Safeguard by Kanana가 가드레일 모델을 오픈소스로 공개했습니다. 한화솔루션은 사내 LLM 게이트웨이를 직접 구축한 사례를 공개했습니다.

본 프로젝트는 이 제품들과 완성도로 경쟁하지 않습니다. 설계 대상을 다음 계층으로 좁힙니다.

| 기성 제품·오픈소스가 하는 것 | 본 프로젝트가 설계하는 것 |
|---|---|
| 프록시, 가상 키, 모델 라우팅 | 보안 담당자용 감사 화면 |
| 표준 PII 탐지·마스킹 모델 | 부서↔정책 N:M 매핑과 충돌 해결 |
| 토큰 사용량·비용 추적 | 위반을 케이스로 검토·확정하는 워크플로우 |
| 유해 콘텐츠 필터 | 정책 버전이 붙은 판정 스냅샷 |

발표에서는 "이 시장은 형성돼 있고, 저희는 그 위의 정책·감사 계층을 설계했다"로 위치를 설명합니다. 우위를 주장하지 않습니다.

---

## 3. Actor 및 Use-Case

### 3.1 Actor 정의

| Actor | 구분 | 설명 | 화면 |
|---|---|---|---|
| 직원 (Employee) | Primary | 사내 AI 챗에 프롬프트를 입력하는 일반 임직원. 소속 부서에 따라 적용 정책이 다름 | SCR-01 |
| 보안 담당자 (Security Admin) | Primary | 판정 결과를 감사하고, AI 제안을 확정하는 정보보안팀 담당자 | SCR-02 |
| 규칙 엔진 (Rule Engine) | System | 정규식·키워드로 확정적 판정을 내리는 내부 컴포넌트 | — |
| AI 판정기 (AiInspector) | System / Future | 맥락형 기밀 후보를 제안하는 컴포넌트. 현재 Mock | — |
| 정책 관리자 | Future | 정책·규칙을 편집하는 역할. 이번 범위에서는 시드로 대체 | 미구현 |
| 외부 감사인 | Future | 증적 내보내기를 요청하는 역할 | 미구현 |

로그인·권한은 구현하지 않습니다. SCR-01 상단의 계정 전환 드롭다운으로 시드 사용자 4명 중 하나를 선택합니다.

### 3.2 Use-Case 목록

| ID | Use-Case | Actor | 우선순위 | 구현 |
|---|---|---|---|---|
| UC-01 | 프롬프트 제출 및 규칙 판정 | 직원, 규칙 엔진 | P0 | 구현 |
| UC-02 | 마스킹된 프롬프트 확인 및 재제출 | 직원 | P0 | 구현 |
| UC-03 | 맥락형 기밀 의심 시 AI 판정 요청 (비동기) | 규칙 엔진, AiInspector | P0 | 구현 (Mock) |
| UC-04 | 판정 상태 폴링 및 결과 확인 | 직원 | P0 | 구현 |
| UC-05 | 위반 이벤트 목록 조회 및 필터링 | 보안 담당자 | P0 | 구현 |
| UC-06 | AI 제안 검토 및 확정 (ACCEPT/REJECT) | 보안 담당자 | P0 | 구현 |
| UC-07 | 부서별 적용 정책 조회 | 직원, 보안 담당자 | P1 | 구현 (조회만) |
| UC-08 | AI 응답 출력 검사 | 규칙 엔진 | Future | 설계만 |
| UC-09 | 첨부파일 텍스트 추출 및 검사 | 추출기 | Future | 설계만 |
| UC-10 | 정책 편집 및 버전 발행 | 정책 관리자 | Future | 설계만 |

### 3.3 Use-Case 상세

#### UC-01 프롬프트 제출 및 규칙 판정

| 항목 | 내용 |
|---|---|
| 사전 조건 | 사용자가 선택되어 있고, 소속 부서에 활성 정책이 매핑되어 있음 |
| 기본 흐름 | 1. 직원이 프롬프트 입력 후 전송 2. 시스템이 사용자→부서→정책→규칙 순으로 적용 규칙 로드 3. 규칙 엔진이 REGEX·KEYWORD 순으로 검사 4. 매칭 결과를 finding으로 생성 5. 충돌 해결 규칙으로 최종 판정 결정 6. inspection 저장 후 응답 |
| 대안 흐름 A1 | 매칭 없음 → decision ALLOW, 200 응답, 프롬프트 원문 그대로 전송 대상 |
| 대안 흐름 A2 | MASK 규칙만 매칭 → decision MASK, 200 응답, submitted_text에 마스킹 본문 |
| 대안 흐름 A3 | BLOCK 규칙 매칭 → decision BLOCK, 403 응답, 전송 안 함 |
| 대안 흐름 A4 | KEYWORD(REVIEW) 규칙 매칭 → UC-03으로 분기, 202 응답 |
| 사후 조건 | inspection 1건, finding N건이 저장됨. message.status가 결정됨 |
| 예외 | 부서에 매핑된 정책이 0건 → 전사 기본 정책(PII)만 적용. 정책 로드 실패 → 500, 전송 보류 |

#### UC-03 AI 판정 요청 (비동기)

| 항목 | 내용 |
|---|---|
| 트리거 | UC-01에서 REVIEW 액션 규칙이 매칭됨 |
| 기본 흐름 | 1. inspection.ai_status = PENDING으로 저장 2. 202 Accepted + Location 헤더 응답 3. @Async로 AiInspector.inspect() 호출 4. Mock이 지연(2.5초) 후 결정론적 응답 반환 5. ai_result JSONB 저장, 후보를 finding(source=AI, review_status=SUGGESTED)으로 생성 6. ai_status = COMPLETED |
| 입력 | 마스킹 처리된 프롬프트, 부서 코드, 적용 정책 카테고리, KEYWORD 매칭 근거 |
| 출력 | 9.4의 aiAssessment JSON |
| 예외 | Mock 예외 발생 → ai_status = FAILED, message.status = PENDING_REVIEW 유지, 감사 콘솔에서 사람이 판단 |
| 불변 조건 | AI 응답에는 결정 필드가 없음. 모든 후보는 SUGGESTED로만 저장됨 |

#### UC-06 AI 제안 검토 및 확정

| 항목 | 내용 |
|---|---|
| 사전 조건 | inspection.ai_status = COMPLETED, finding.review_status = SUGGESTED인 항목이 존재 |
| 기본 흐름 | 1. 보안 담당자가 SCR-02 목록에서 REVIEW 상태 이벤트 선택 2. 상세 패널에서 규칙 결과와 AI 후보를 좌우 분리 확인 3. 각 AI 후보에 ACCEPT 또는 REJECT 4. 시스템이 review_status, reviewed_by, reviewed_at 기록 5. 모든 후보 처리 완료 시 최종 판정 산출 |
| 최종 판정 규칙 | ACCEPTED 후보가 1건 이상 → message.status = BLOCKED, decided_by = HUMAN / 전부 REJECTED → ALLOWED, decided_by = HUMAN |
| 예외 | 이미 처리된 finding에 재요청 → 409 Conflict |

### 3.4 AI 확장 지점 정의

가이드가 요구하는 "AI 확장 지점"은 한 곳으로 특정합니다.

| 항목 | 내용 |
|---|---|
| 위치 | UC-03. 규칙 엔진의 KEYWORD 규칙이 REVIEW 액션으로 매칭된 직후 |
| 입력 | 마스킹된 프롬프트 + 부서 + 정책 카테고리 + 키워드 매칭 근거 |
| 출력 | 위험 후보 목록(코드, 근거 서술, 참조 출처), 확인 필요 항목 |
| 현재 | MockAiInspector — 케이스별 고정 JSON, 2.5초 지연 |
| 교체 후 | LlmAiInspector — 사내 호스팅 모델(SGuard-v1, Safeguard by Kanana 등) 또는 계약된 외부 API |
| 왜 이 자리인가 | 정규식이 100% 판정하는 것은 AI에 맡기지 않음. 정규식이 구조적으로 못 잡는 맥락(고객사명은 잡히지만 그것이 기밀 프로젝트 논의인지는 못 잡음)만 AI 영역 |
| RAG 연결 지점 | 입력의 "키워드 매칭 근거"가 사내 기밀 사전 조회 결과. 현재는 policy_rule의 KEYWORD 패턴에서 오고, 확장 시 knowledge_source 테이블 검색으로 대체 |

교수 피드백 F3 "AI API를 이용하는 부분은 어디인가"에 대한 답이 이 표입니다.

---

## 4. 책임 경계 설계 (Rule / AI / Human)

체감온도 임계값처럼 정형화 가능한 판단을 LLM에 맡기는 것은 설계 실패입니다. 본 설계는 판단 주체를 셋으로 나누고 각각의 권한을 제한합니다.

| 주체 | 권한 | 할 수 없는 것 | 기록되는 것 |
|---|---|---|---|
| Rule Engine | ALLOW / MASK / BLOCK / REVIEW 결정 | 맥락 해석 | ruleId, span, action, policyVersion |
| AI (AiInspector) | 후보 제안, 근거 서술, 확인 필요 항목 보고 | 결정. 응답 스키마에 결정 필드가 존재하지 않음 | candidates[], rationale, evidence |
| Human (보안 담당자) | AI 후보의 ACCEPT / REJECT, 최종 확정 | 규칙 판정 번복 (이번 범위에서는 미구현) | reviewed_by, reviewed_at, decided_by=HUMAN |

이 경계는 세 곳에서 강제됩니다.

- 시스템 프롬프트의 금지 조항 (9.2)
- JSON 스키마에 decision 필드 부재 (9.4)
- inspection_finding.review_status의 기본값 SUGGESTED (6.2)

---

## 5. 화면 설계 — Figma 작업 지침

### 5.1 화면 목록 및 라우팅

| ID | 화면명 | 라우트 | Actor | 우선순위 |
|---|---|---|---|---|
| SCR-01 | 직원 AI 챗 | `/chat` | 직원 | P0 |
| SCR-02 | 관리자 감사 콘솔 | `/admin/audit` | 보안 담당자 | P0 |
| — | 공통 헤더 | 전체 | 전체 | P0 |

### 5.2 공통 레이아웃

- 상단 헤더 높이 56px, 좌측 서비스명, 우측 계정 전환 드롭다운
- 계정 전환 드롭다운: 시드 사용자 4명 표시 (이름 · 부서). 선택 시 Pinia store의 currentUser 갱신
- 좌측 네비게이션 없음. 헤더의 탭 두 개(챗 / 감사 콘솔)로 이동
- 폰트: 시스템 기본 (Pretendard 또는 Noto Sans KR). 본문 14px, 캡션 12px
- 색상 토큰

| 토큰 | 용도 | 값 |
|---|---|---|
| navy | 주 텍스트, 헤더 배경 | #16202E |
| blue | 강조, 링크, 규칙 판정 라벨 | #2F5D8A |
| red | BLOCK | #C2452D |
| amber | MASK | #B7791F |
| purple | REVIEW / AI 후보 | #5B4B8A |
| green | ALLOW / ACCEPT | #2E7D5B |
| gray | 보조 텍스트 | #6B7280 |
| card | 카드 배경 | #F4F6F9 |

### 5.3 SCR-01 직원 AI 챗

#### 레이아웃

```
┌──────────────────────────────────────────────────────┐
│ 헤더: 사내 AI 챗            [탭: 챗 | 감사]  [계정 ▾] │
├──────────────────────────────────────────────────────┤
│                                                      │
│  대화 영역 (스크롤)                                    │
│  ┌──────────────────────────────────────────┐        │
│  │ [직원] 이 에러 좀 봐줘 DB_URL=…            │        │
│  │ ┌─ 판정 카드 ─────────────────────────┐   │        │
│  │ │ BLOCK  규칙 2건 적용                 │   │        │
│  │ │ SEC-DBURL-02 접속 문자열  BLOCK      │   │        │
│  │ │ PII-RRN-01 주민번호       MASK       │   │        │
│  │ │ 정책 v7 · 개발팀                     │   │        │
│  │ └─────────────────────────────────────┘   │        │
│  └──────────────────────────────────────────┘        │
│                                                      │
├──────────────────────────────────────────────────────┤
│ 입력창 (textarea, 3줄)                    [전송]      │
│ 하단 캡션: 부서: 개발팀 · 적용 정책 2건                  │
└──────────────────────────────────────────────────────┘
```

#### 컴포넌트

| 컴포넌트 | 설명 | 데이터 소스 |
|---|---|---|
| MessageInput | textarea + 전송 버튼. 전송 중 비활성 | — |
| PolicyCaption | 현재 사용자 부서와 적용 정책 수 | GET /policies?deptId= |
| MessageBubble | 직원 발화. 작성자 본인의 입력 원문을 표시하되, 마스킹된 경우 submitted_text로 대체하고 마스킹 구간 하이라이트. BLOCK이면 입력 원문 표시 (0.5 D15) | 로컬 입력값 + POST /messages 응답 |
| VerdictCard | 판정 결과 카드. 상태별 색상 | POST /messages, GET /inspections/{id} |
| PendingIndicator | 스피너 + "보안 검토 중" + 경과 시간 | 폴링 상태 |
| AiCandidateList | AI 후보 목록(읽기 전용). 코드, 근거, 출처 | GET /inspections/{id} |

#### 상태 (Figma에서 5개 프레임으로 작성)

| 상태 | 트리거 | 화면 표시 |
|---|---|---|
| S1 초기 | 진입 | 빈 대화 영역, 입력창 활성, PolicyCaption |
| S2 ALLOW | 200, decision=ALLOW | 발화 그대로 표시, 초록 배지 "전송됨", 규칙 0건 |
| S3 MASK | 200, decision=MASK | 마스킹된 본문 표시(`[주민번호]` 등), 노랑 배지, 적용 규칙 목록, "마스킹 후 전송됨" |
| S4 BLOCK | 403 | 빨강 배지, 적용 규칙 목록과 사규 출처, 입력창에 원문 복원, "수정 후 재전송" 안내 |
| S5 PENDING → 결과 | 202 → 폴링 | 보라 스피너 "보안 검토 중" → COMPLETED 후 "검토 대기 (담당자 확정 필요)" + AI 후보 목록 읽기 전용 |

폴링은 ai_status가 PENDING인 동안만 돕니다. COMPLETED 또는 FAILED가 되면 중단합니다. S5의 최종 결과(담당자 확정 후 BLOCKED/ALLOWED)는 폴링으로 따라가지 않고 화면 재조회로 반영합니다 — 사람의 확정 시점은 예측할 수 없어 무한 폴링이 되기 때문입니다. 데모에서는 감사 콘솔에서 확정한 직후 챗 화면을 새로고침해 보여줍니다 (0.5 D12).

#### 인터랙션 규칙

- 전송 클릭 → 입력창 비활성 → 응답 수신 후 활성
- 202 수신 시 2초 간격 폴링 시작, ai_status가 PENDING이 아니면 중단. 최대 30회
- BLOCK 시 입력창에 원문 복원(재수정 유도). MASK 시 입력창 비움
- 마스킹 하이라이트는 submitted_text에서 mask_label 문자열(`[주민번호]`, `[전화번호]` 등)을 찾아 `<mark>`로 감쌈. finding의 span은 원문 기준이라 마스킹본에 그대로 적용하면 위치가 밀림 (0.5 D3)

### 5.4 SCR-02 관리자 감사 콘솔

#### 레이아웃

```
┌──────────────────────────────────────────────────────────────┐
│ 헤더                                                          │
├──────────────────────────────────────────────────────────────┤
│ 필터 바: [부서 ▾] [상태 ▾] [기간 시작~종료]  [검색]   총 137건  │
├───────────────────────────────┬──────────────────────────────┤
│ 목록 (테이블, 페이지 20)         │ 상세 패널 (선택 시 표시)         │
│ 시각 | 부서 | 사용자 | 판정 | 규칙수 │ ─ 원문(마스킹) ─              │
│ 14:31 영업 김OO  REVIEW  1    │ ─ 규칙 판정 (결정) ─           │
│ 14:08 개발 이OO  MASK    1    │   SEC-DBURL-02 BLOCK 사규     │
│ 13:52 영업 박OO  BLOCK   2    │ ─ AI 제안 (후보) ─             │
│ …                             │   CONF-CLIENT-01              │
│                               │   근거: 'A사 차세대…' 서술     │
│                               │   출처: 고객사 NDA 목록 v3     │
│                               │   [ACCEPT] [REJECT]           │
│                               │ ─ 이력 ─                      │
│                               │   정책 v7 · 판정 RULE · 확정 — │
└───────────────────────────────┴──────────────────────────────┘
```

#### 목록 컬럼

| 컬럼 | 필드 | 표시 |
|---|---|---|
| 시각 | inspection.created_at | MM-DD HH:mm |
| 부서 | department.name | 텍스트 |
| 사용자 | app_user.name | 성 + OO 마스킹 |
| 판정 | message.status | 배지 (색상 토큰) |
| 규칙 수 | finding count (source=RULE) | 숫자 |
| AI 상태 | inspection.ai_status | SKIPPED는 공란, PENDING/COMPLETED/FAILED |
| 확정 | decided_by | RULE / HUMAN / — |

#### 필터

| 필터 | 파라미터 | 옵션 |
|---|---|---|
| 부서 | deptId | 전체, 개발팀, 영업팀, 인사팀 |
| 상태 | status | 전체, ALLOWED, MASKED, BLOCKED, PENDING_REVIEW |
| 기간 | from, to | 기본 최근 7일 |

부서 필터에 정보보안팀(INFOSEC)은 넣지 않습니다. 검토자 역할만 하고 프롬프트를 제출하지 않아 항상 0건이며, 빈 결과만 내는 옵션은 데모에서 오해를 부릅니다 (0.5 D2).

#### 상세 패널 섹션

1. 원문 — submitted_text (마스킹된 본문). 원문(original_text)은 표시하지 않음. 원문 열람은 Future
2. 규칙 판정 (결정) — finding(source=RULE) 목록: ruleId, 카테고리, action, obligation, source
3. AI 제안 (후보) — finding(source=AI) 목록: code, rationale, evidence, review_status. SUGGESTED이면 ACCEPT/REJECT 버튼 노출
4. 이력 — policyVersion, decided_by, reviewed_by, reviewed_at

#### 인터랙션 규칙

- 행 클릭 → 우측 패널 로드 (GET /inspections/{id})
- ACCEPT/REJECT 클릭 → PATCH → 성공 시 패널과 목록 행 즉시 갱신
- 모든 AI 후보 처리 완료 시 상단에 최종 판정 배지 표시 (BLOCKED/ALLOWED, HUMAN)

### 5.5 User Flow (Figma 플로우 프레임)

```
[SCR-01 계정 선택] → [프롬프트 입력] → [전송]
   ├─ 매칭 없음 ──────────────→ [S2 ALLOW]
   ├─ MASK만 ─────────────────→ [S3 MASK]
   ├─ BLOCK 포함 ─────────────→ [S4 BLOCK] → [수정] → [전송]
   └─ REVIEW 키워드 ──→ [S5 PENDING] ─(폴링)─→ [검토 대기]
                                                    │
[SCR-02 목록] → [REVIEW 행 선택] → [상세] → [ACCEPT/REJECT] ─┘
                                            → [최종 판정 표시]
```

### 5.6 용어 통일

화면에 노출되는 용어는 아래로 고정합니다. 개발·발표·문서에서 동일하게 씁니다.

| 내부 값 | 화면 표기 |
|---|---|
| ALLOW / ALLOWED | 허용 |
| MASK / MASKED | 마스킹 |
| BLOCK / BLOCKED | 차단 |
| REVIEW / PENDING_REVIEW | 검토 대기 |
| SUGGESTED | 제안됨 |
| ACCEPTED | 확정(위반) |
| REJECTED | 기각 |
| CONFIRMED | 확정(규칙) — 규칙 판정이라 사람의 검토 대상이 아님 |
| SKIPPED (aiStatus) | (공란) — AI를 호출하지 않음 |
| PENDING (aiStatus) | 분석 중 |
| COMPLETED (aiStatus) | 분석 완료 |
| FAILED (aiStatus) | 분석 실패 |

`aiStatus`는 AI의 상태이므로 **분석**, `message.status`와 `review_status`는 사람의 절차이므로 **검토**로 부릅니다. 감사 목록 한 행에 판정("검토 대기")과 AI 상태가 나란히 서는데 둘 다 "검토"면 누가 무엇을 하는지 화면에서 구분되지 않고, 4장 책임 경계 주장이 흐려집니다. 직원 화면(5.3)의 프로세스 안내 문장("보안 검토 중")은 예외로 기획서 문구를 따릅니다 — 직원에게 필요한 것은 자기 프롬프트가 보안 검토를 받고 있다는 사실이고 AI 개입 여부는 관심사가 아닙니다 (0.5 D16).

---

## 6. 데이터 모델 — ERD 작업 지침

### 6.1 테이블 목록

Core Domain 8개는 DDL을 실행하고, Future Domain 4개는 Logical Model에만 표기합니다.

| # | 테이블 | 구분 | 역할 |
|---|---|---|---|
| 1 | department | Core | 부서 마스터 |
| 2 | app_user | Core | 사용자. 로그인 없이 계정 전환용 |
| 3 | policy | Core | 정책 헤더. 카테고리, 버전, 활성 여부 |
| 4 | policy_rule | Core | 규칙. 패턴, 액션, 출처 |
| 5 | department_policy | Core | 부서↔정책 N:M 매핑 |
| 6 | message | Core | 직원이 제출한 프롬프트 |
| 7 | inspection | Core | 검사 1회의 결과. 정책 스냅샷, AI 상태 |
| 8 | inspection_finding | Core | 검사에서 발견된 항목. 규칙 매칭과 AI 후보 모두 |
| F1 | attachment | Future | 첨부파일 메타. message 1:N |
| F2 | knowledge_source | Future | 기밀 사전·NDA 목록 등 RAG 검색 대상 |
| F3 | policy_audit | Future | 정책 변경 이력 |
| F4 | ai_provider_config | Future | 모델별 파라미터. 현재는 application.yml |

### 6.2 테이블 상세

`department`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| dept_id | BIGSERIAL | PK | |
| code | VARCHAR(20) | UNIQUE, NOT NULL | DEV, SALES, HR, INFOSEC, PR |
| name | VARCHAR(50) | NOT NULL | 개발팀, 영업팀, 인사팀, 정보보안팀, 홍보팀 |

`app_user`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| user_id | BIGSERIAL | PK | |
| dept_id | BIGINT | FK → department | |
| name | VARCHAR(50) | NOT NULL | |
| email | VARCHAR(100) | UNIQUE | |
| role | VARCHAR(20) | NOT NULL | EMPLOYEE, SECURITY_ADMIN |
| created_at | TIMESTAMPTZ | DEFAULT now() | |

`policy`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| policy_id | BIGSERIAL | PK | |
| code | VARCHAR(20) | UNIQUE | P-PII, P-SEC, P-CONF, P-EMBARGO |
| name | VARCHAR(100) | NOT NULL | |
| category | VARCHAR(20) | NOT NULL | PII, SECRET, CONFIDENTIAL, EMBARGO |
| version | INT | NOT NULL, DEFAULT 1 | 규칙 변경 시 증가 |
| is_active | BOOLEAN | DEFAULT true | |
| scope | VARCHAR(20) | NOT NULL | GLOBAL(전사) 또는 DEPT(매핑 필요) |
| owner_dept_id | BIGINT | FK → department | 정책을 **만든** 부서. `department_policy`(적용 부서)와 다르다 (0.5 D19) |
| created_at | TIMESTAMPTZ | | |

`policy_rule`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| rule_id | BIGSERIAL | PK | |
| policy_id | BIGINT | FK → policy | |
| code | VARCHAR(30) | UNIQUE | PII-RRN-01 등. 발표·로그에서 이 값을 씀 |
| rule_type | VARCHAR(20) | NOT NULL | REGEX, KEYWORD |
| pattern | TEXT | NOT NULL | 정규식 또는 쉼표 구분 키워드 |
| action | VARCHAR(20) | NOT NULL | MASK, BLOCK, REVIEW |
| mask_label | VARCHAR(30) | | 마스킹 치환 라벨. 예: [주민번호] |
| severity | VARCHAR(10) | NOT NULL | HIGH, MEDIUM, LOW |
| obligation | VARCHAR(20) | NOT NULL | LEGAL(법령), INTERNAL(사규) |
| source | VARCHAR(100) | | 개인정보보호법 제N조, 정보보안규정 N.N 등 |
| description | VARCHAR(200) | | 화면 표시용 설명 |
| embargo_until | DATE | | 엠바고 **해제일**. 이 날부터 공개 가능하며 차단 조건은 `today < embargo_until`. NULL이면 기한 없음 (0.5 D20) |
| is_active | BOOLEAN | DEFAULT true | |

`department_policy` — N:M

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| dept_id | BIGINT | PK, FK → department | |
| policy_id | BIGINT | PK, FK → policy | |
| applied_at | TIMESTAMPTZ | DEFAULT now() | |

scope=GLOBAL 정책은 매핑 없이 전 부서에 적용됩니다. scope=DEPT 정책만 이 테이블로 매핑합니다.

`message`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| message_id | BIGSERIAL | PK | |
| user_id | BIGINT | FK → app_user | |
| original_text | TEXT | NOT NULL | 원문. 화면 미노출 |
| submitted_text | TEXT | | 마스킹 적용 후 본문. NULL은 **마스킹본이 생성된 적이 없음**을 뜻하며 규칙 BLOCK 경로에서만 발생. PENDING_REVIEW와 사람이 확정한 BLOCK은 본문을 보존 (0.5 D7·D14) |
| status | VARCHAR(20) | NOT NULL | ALLOWED, MASKED, BLOCKED, PENDING_REVIEW |
| created_at | TIMESTAMPTZ | DEFAULT now() | |

`inspection`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| inspection_id | BIGSERIAL | PK | |
| message_id | BIGINT | FK → message | |
| phase | VARCHAR(10) | NOT NULL | INPUT. OUTPUT은 Future |
| policy_snapshot | JSONB | NOT NULL | 적용된 정책 id·version·규칙 코드 목록 |
| rule_result | JSONB | NOT NULL | 규칙 엔진 원본 결과 (9.4 ruleResult) |
| ai_status | VARCHAR(20) | NOT NULL | SKIPPED, PENDING, COMPLETED, FAILED |
| ai_result | JSONB | | AI 원본 응답 (9.4 aiAssessment) |
| final_decision | VARCHAR(20) | | ALLOW, MASK, BLOCK, PENDING |
| decided_by | VARCHAR(10) | | RULE, HUMAN |
| created_at | TIMESTAMPTZ | DEFAULT now() | |
| completed_at | TIMESTAMPTZ | | AI 완료 또는 사람 확정 시각 |

`inspection_finding`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| finding_id | BIGSERIAL | PK | |
| inspection_id | BIGINT | FK → inspection | |
| source | VARCHAR(10) | NOT NULL | RULE, AI |
| rule_id | BIGINT | FK → policy_rule, NULL 허용 | AI 후보는 NULL |
| code | VARCHAR(30) | NOT NULL | 규칙 코드 또는 AI 후보 코드 |
| category | VARCHAR(20) | NOT NULL | |
| span_start | INT | | 원문 기준 시작 오프셋 |
| span_end | INT | | |
| action | VARCHAR(20) | | 규칙 finding만 |
| rationale | TEXT | | AI 후보의 근거 서술 |
| evidence | JSONB | | AI 후보의 참조 출처 배열 |
| review_status | VARCHAR(20) | NOT NULL, DEFAULT 'SUGGESTED', CHECK (4값) | SUGGESTED, ACCEPTED, REJECTED, CONFIRMED. 규칙 finding은 CONFIRMED 고정이며 사람의 검토 대상이 아님 — 화면에 ACCEPT/REJECT 버튼을 노출하지 않음 (0.5 D6) |
| reviewed_by | BIGINT | FK → app_user | |
| reviewed_at | TIMESTAMPTZ | | |

### 6.3 관계 정의

| 관계 | 카디널리티 | 설명 |
|---|---|---|
| department — app_user | 1:N | 사용자는 하나의 부서에 속함 |
| department — policy | N:M (department_policy) | 한 정책이 여러 부서에, 한 부서에 여러 정책 |
| policy — policy_rule | 1:N | 정책 하나에 규칙 여러 개 |
| app_user — message | 1:N | |
| message — inspection | 1:N | phase별 1건. 현재는 INPUT만이라 사실상 1:1이나 OUTPUT 확장을 위해 1:N |
| inspection — inspection_finding | 1:N | |
| policy_rule — inspection_finding | 1:N (nullable) | 규칙 finding만 참조 |
| app_user — inspection_finding | 1:N (reviewed_by) | 확정한 담당자 |

### 6.4 설계 근거

| 결정 | 근거 |
|---|---|
| department_policy를 별도 테이블로 | 부서와 정책이 다대다. policy에 dept_id를 두면 같은 정책을 부서 수만큼 복제해야 함 |
| policy.scope로 GLOBAL/DEPT 구분 | PII처럼 전사 공통인 정책까지 매핑 행을 만들면 부서 추가 시 누락 위험. 전사 정책은 매핑 없이 적용 |
| inspection.policy_snapshot JSONB | 정책이 나중에 바뀌어도 당시 어떤 버전·규칙으로 판정했는지 보존. 원본은 policy·policy_rule에 정규화되어 있고 JSONB는 시점 스냅샷 |
| rule_result / ai_result JSONB | 규칙 엔진과 AI의 원본 응답을 그대로 보관. AI 스키마가 확장돼도 컬럼 추가 없음 (Structured Data 원칙) |
| finding을 RULE과 AI 공용 테이블로 | 화면에서 두 출처를 같은 목록 구조로 다룸. source 컬럼으로 구분 |
| finding.review_status 기본값 SUGGESTED | AI 후보가 확정 없이 효력을 갖지 못하도록 DB 수준에서 강제 |
| message.original_text와 submitted_text 분리 | 원문은 감사 목적 보관, 화면과 외부 전송은 마스킹본만 |
| policy.version 컬럼 + 스냅샷 | 별도 이력 테이블 없이 시점 조회 가능. 이력 테이블(policy_audit)은 Future |

### 6.5 Future Domain 정의

| 테이블 | 주요 컬럼 | 연결 |
|---|---|---|
| attachment | attachment_id, message_id FK, file_name, mime_type, extracted_text, extract_status | message 1:N |
| knowledge_source | source_id, name, type(NDA_LIST, CLIENT_DICT, PROJECT_CODE), content, updated_at | AiInspector 입력 |
| policy_audit | audit_id, policy_id FK, from_version, to_version, changed_by, changed_at, diff JSONB | policy 1:N |
| ai_provider_config | config_id, provider, model, temperature, max_tokens, is_active | 현재 application.yml |

---

## 7. 정책 및 규칙 정의

### 7.1 정책 4종

| 코드 | 이름 | 카테고리 | scope | 소유 부서 | 적용 부서 | 규칙 수 |
|---|---|---|---|---|---|---|
| P-PII | 개인정보 보호 | PII | GLOBAL | 정보보안팀 | 전사 | 4 |
| P-SEC | 자격증명·인프라 정보 보호 | SECRET | GLOBAL | 정보보안팀 | 전사 | 3 |
| P-CONF | 고객사 프로젝트 정보 통제 | CONFIDENTIAL | DEPT | 정보보안팀 | 영업팀, 인사팀 | 1 |
| P-EMBARGO | 보도자료 엠바고 | EMBARGO | DEPT | **홍보팀** | 개발팀, 영업팀 | 2 |

P-CONF가 개발팀에 적용되지 않는 이유는 개발팀이 해당 고객사 프로젝트의 수행 조직이라 업무상 논의가 필요하기 때문입니다. 이 차이가 데모 Case B/C의 근거입니다.

P-EMBARGO는 **소유 부서와 적용 부서가 다른 유일한 정책**입니다 (0.5 D19). 홍보팀이 발표 시점을 통제하고, 그 통제를 받는 쪽은 미발표 제품을 다루는 개발팀·영업팀입니다. 홍보팀 자신에게는 매핑하지 않습니다 — 발표 주체가 자기 엠바고에 걸릴 이유가 없습니다.

카테고리를 `CONFIDENTIAL`에 얹지 않고 `EMBARGO`로 나눈 이유는 통제의 근거가 다르기 때문입니다. 기밀은 정보가 민감해서 막지만 엠바고는 아직 때가 아니라서 막으며, 화면에 "기밀"로 뜨면 그 구분이 사라집니다.

### 7.2 규칙 14종

| 코드 | 정책 | 타입 | 패턴 | 액션 | 마스킹 라벨 | 심각도 | 의무 | 출처 | 해제일 |
|---|---|---|---|---|---|---|---|---|---|
| PII-RRN-01 | P-PII | REGEX | `(?<![0-9])\d{2}(?:0[1-9]\|1[0-2])(?:0[1-9]\|[12][0-9]\|3[01])-?[1-4]\d{6}(?![0-9])` | MASK | [주민번호] | HIGH | LEGAL | 개인정보보호법 제24조 | — |
| PII-CARD-02 | P-PII | REGEX | `(?<![0-9])(?:\d{4}-\d{4}-\d{4}-\d{4}\|\d{16})(?![0-9])` | MASK | [카드번호] | HIGH | LEGAL | 개인정보보호법 | — |
| PII-PHONE-03 | P-PII | REGEX | `(?<![0-9])01[016789](?:-\d{3,4}-\d{4}\|\d{7,8})(?![0-9])` | MASK | [전화번호] | MEDIUM | LEGAL | 개인정보보호법 | — |
| PII-EMAIL-04 | P-PII | REGEX | `[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}` | MASK | [이메일] | LOW | LEGAL | 개인정보보호법 | — |
| PII-BIZNO-05 | P-PII | REGEX | `(?<![0-9])\d{3}-\d{2}-\d{5}(?![0-9])` | MASK | [사업자번호] | MEDIUM | INTERNAL | 정보보안규정 4.4 | — |
| PII-ACCOUNT-06 | P-PII | REGEX | `(?<=계좌[^\n]{0,8}\|입금[^\n]{0,8}\|송금[^\n]{0,8}\|이체[^\n]{0,8})(?<![0-9])\d{2,6}-\d{2,6}-\d{2,8}(?![0-9])` | MASK | [계좌번호] | HIGH | LEGAL | 개인정보보호법 | — |
| PII-CUST-07 | P-PII | **ROSTER** | `name` (customer 테이블 조회) | MASK | [고객명] | HIGH | LEGAL | 개인정보보호법 | — |
| PII-CUST-08 | P-PII | **ROSTER** | `given_name` (customer 테이블 조회) | REVIEW | — | LOW | LEGAL | 개인정보보호법 | — |
| SEC-AWSKEY-01 | P-SEC | REGEX | `AKIA[0-9A-Z]{16}` | BLOCK | — | HIGH | INTERNAL | 정보보안규정 4.2 | — |
| SEC-DBURL-02 | P-SEC | REGEX | `(postgres\|mysql\|jdbc)[\w+]*://[^\s]+` | BLOCK | — | HIGH | INTERNAL | 정보보안규정 4.2 | — |
| SEC-PRIVIP-03 | P-SEC | REGEX | `\b(10\.\d{1,3}\|192\.168\|172\.(1[6-9]\|2\d\|3[01]))\.\d{1,3}\.\d{1,3}\b` | MASK | [내부IP] | MEDIUM | INTERNAL | 정보보안규정 4.3 | — |
| CONF-CLIENT-01 | P-CONF | KEYWORD | `A사,B사,C사,프로젝트 오메가,차세대` | REVIEW | — | MEDIUM | INTERNAL | 고객사 NDA 목록 v3 | — |
| EMB-NOVA-01 | P-EMBARGO | KEYWORD | `노바,NOVA,SKALA NOVA` | BLOCK | — | HIGH | INTERNAL | 홍보팀 엠바고 공지 2026-09-01 | **2026-09-20** |
| EMB-ATLAS-02 | P-EMBARGO | KEYWORD | `아틀라스,ATLAS` | BLOCK | — | HIGH | INTERNAL | 홍보팀 엠바고 공지 2026-06-10 | **2026-09-04** |

**PII 4종은 2026-09-02에 경계를 조였습니다** (0.5.1 D21). `\b` 대신 `(?<![0-9])`·`(?![0-9])`를 쓰는데, 한글 문장에서 `\b`는 한글과 숫자 사이를 경계로 보아 의도가 드러나지 않기 때문입니다. 주민번호는 월·일 범위를, 카드·전화는 구분자 일관성을, 이메일은 최상위 도메인이 알파벳임을 함께 검사합니다.

`PII-ACCOUNT-06`만 **문맥을 요구합니다.** 계좌번호는 은행마다 자릿수가 달라 형식만으로는 버전 번호·주문 번호와 구분되지 않습니다. lookbehind는 마스킹 구간에 포함되지 않으므로 '계좌'라는 단어는 본문에 그대로 남습니다.

**고객 이름은 명단 테이블로 잡습니다** (0.5.1 D23). 정규식으로 일반화하면 김치·박스·한우가 걸리고 외자 이름은 놓칩니다. 그리고 애초에 이것은 탐지 문제가 아니라 **데이터 문제**입니다 — 고객 명단을 사내 DB에 두는 것은 정상이고, 막아야 할 것은 그것이 외부 LLM으로 나가는 순간입니다. 게이트웨이가 명단을 이미 알고 있으므로 나가려는 자리에서 막습니다.

`rule_type=ROSTER`는 `pattern`에 정규식 대신 `customer` 테이블의 조회 컬럼명을 둡니다. `PolicyService`가 판정 직전에 명단을 읽어 정규식으로 펼치므로, 엔진은 여전히 REGEX만 알면 되고 명단이 바뀌어도 규칙 행은 그대로입니다. 조직이 바뀔 때 필요한 작업이 "코드 수정"이 아니라 "명단 적재"가 됩니다.

성+이름 전체가 맞으면 마스킹하고, 성을 뗀 이름만 맞으면 **검토로 보냅니다.** 이름 부분은 일반 명사와 겹칠 수 있어 기계가 확정하지 않습니다. 앞뒤 한글 경계로 '재현 가능'의 재현, '서준이가'의 서준 같은 접사 결합을 거릅니다.

**시드 명단은 합성입니다.** 10.1이 실명을 금지하므로 저장소·발표 자료에 실명을 두지 않고, 실제 데이터는 운영 DB에 적재합니다.

여권번호(`[MSRO]\d{8}`)는 넣지 않았습니다. 문맥 없이는 사내 자산번호와 충돌해 오탐이 이득을 넘어섭니다 (17장 확장 9로 이관).

해제일(`policy_rule.embargo_until`)은 **그 날부터 공개할 수 있다**는 뜻이며 차단 조건은 `today < embargo_until`입니다 (0.5 D20). 나머지 8종은 기한이 없습니다 — 주민번호는 다음 달이 된다고 덜 민감해지지 않습니다.

엠바고 규칙을 BLOCK으로 둔 이유는 해제일까지 예외가 없어 사람이 판단할 여지가 없기 때문입니다. REVIEW로 두면 Case B와 같은 202 폴링 흐름이 되어 시연 장면도 겹칩니다.

**규칙 2종을 넣은 것은 시연을 위해서입니다.** 같은 파일에 두 제품이 들어 있고 하나만 걸립니다 — 부서로 갈리는 Case B/C와 같은 증명을 시간 축에서 한 번 더 합니다.

법령 조문 번호는 국가법령정보센터에서 최종 확인 후 확정합니다. 확인 전까지 발표에서는 "개인정보보호법"까지만 언급합니다.

### 7.3 부서별 적용 매트릭스

| 부서 | P-PII | P-SEC | P-CONF | P-EMBARGO |
|---|---|---|---|---|
| 개발팀 (DEV) | ○ (GLOBAL) | ○ (GLOBAL) | × | ○ (매핑) |
| 영업팀 (SALES) | ○ (GLOBAL) | ○ (GLOBAL) | ○ (매핑) | ○ (매핑) |
| 인사팀 (HR) | ○ (GLOBAL) | ○ (GLOBAL) | ○ (매핑) | × |
| 정보보안팀 (INFOSEC) | ○ (GLOBAL) | ○ (GLOBAL) | × | × |
| 홍보팀 (PR) | ○ (GLOBAL) | ○ (GLOBAL) | × | × (소유 부서) |

정보보안팀은 검토자 역할만 하므로 department_policy 매핑이 없습니다. GLOBAL 정책은 전 부서에 적용되는 성질상 이 부서에도 걸리지만, 프롬프트를 제출하지 않아 실제 판정은 발생하지 않습니다 (0.5 D2).

홍보팀은 P-EMBARGO의 **소유 부서**이며 적용 대상이 아닙니다 (0.5 D19). 인사팀에 P-EMBARGO를 매핑하지 않은 것은 미발표 제품 정보를 다루지 않기 때문입니다 — 매핑을 넓히면 "누가 왜 걸리는가"가 흐려집니다.

### 7.4 판정 절차

1. 사용자 → 부서 조회
2. scope=GLOBAL인 활성 정책 전부 + department_policy로 매핑된 scope=DEPT 정책 로드
3. 정책별 활성 규칙 로드, policy_snapshot에 {policyId, version, ruleCodes[]} 기록
3-1. **엠바고 만료 제외.** `embargo_until`이 있고 기준일이 그 날 이후면 매칭 대상에서 뺀다. `appliedRuleCodes`에는 남긴다 (0.5 D20)
4. REGEX 규칙을 severity 내림차순으로 전부 실행. 매칭마다 finding(source=RULE) 생성
5. KEYWORD 규칙 실행. 매칭 시 finding(source=RULE, action=REVIEW)을 **규칙당 1건** 생성. 한 규칙의 키워드가 여러 개 매칭되면 첫 매칭을 matchedKeyword에 기록하고, 매칭된 키워드 전체는 AiInspector 입력의 hits[]로 넘김 (0.5 D9)
6. 중첩 억제. span 시작 오프셋 순으로 정렬한 뒤, 앞선 매칭의 span에 완전히 포함되는 매칭은 finding을 생성하지 않음 (0.5 D1)
7. 충돌 해결로 최종 판정 결정
8. 최종 판정이 BLOCK이 아닐 때만 마스킹 실행 (0.5 D5)

### 7.5 충돌 해결 규칙

한 프롬프트에 여러 규칙이 매칭될 때 우선순위는 다음과 같습니다.

```
BLOCK > REVIEW > MASK > ALLOW
```

| 매칭 조합 | 최종 판정 | message.status | HTTP | 처리 |
|---|---|---|---|---|
| 없음 | ALLOW | ALLOWED | 200 | 원문 그대로 submitted_text |
| MASK만 | MASK | MASKED | 200 | 마스킹본을 submitted_text |
| BLOCK 포함 (REVIEW 유무 무관) | BLOCK | BLOCKED | 403 | submitted_text NULL, AI 호출 안 함. 마스킹을 실행하지 않아 본문이 생성되지 않음 (0.5 D14) |
| REVIEW 포함, BLOCK 없음 | PENDING | PENDING_REVIEW | 202 | 마스킹본을 submitted_text에 저장 후 AI 호출. MASK가 없으면 원문과 동일한 값 (0.5 D7) |

BLOCK이 있으면 AI를 호출하지 않습니다. 이미 확정된 위반에 AI 비용을 쓸 이유가 없고, 외부로 보낼 텍스트 자체가 없기 때문입니다.

같은 이유로 BLOCK이면 마스킹도 실행하지 않습니다. submitted_text가 NULL이라 마스킹할 대상이 없고, BLOCK 액션 규칙(SEC-DBURL-02, SEC-AWSKEY-01)에는 mask_label이 정의되어 있지 않아 실행하면 오류가 납니다 (0.5 D5).

### 7.6 마스킹 규칙

- 마스킹은 최종 판정이 MASK 또는 PENDING일 때만 실행. BLOCK이면 실행하지 않음 (0.5 D5)
- 치환 단위는 매칭 전체. 부분 보존(뒤 4자리)은 Future
- 치환 문자열은 mask_label. 예: `주민번호 900101-1234567` → `주민번호 [주민번호]`
- 앞선 매칭의 span에 완전히 포함되는 매칭은 finding을 만들지 않음. 같은 문자열을 두 번 세지 않기 위함 (0.5 D1)
- 부분 겹침(포함 아님)에서 두 규칙이 만나면 severity 높은 규칙의 라벨 사용. 동률이면 rule code 사전순
- 치환은 span 뒤에서 앞 방향으로 수행. 앞에서부터 치환하면 길이가 변해 뒤 매칭의 오프셋이 전부 밀림
- span은 원문 기준 오프셋으로 finding에 저장. 화면 하이라이트는 오프셋을 재계산하지 않고 submitted_text에서 mask_label 문자열을 검색해 처리 (0.5 D3)

---

## 8. REST API 명세

### 8.1 공통 규약

| 항목 | 규약 |
|---|---|
| Base path | `/api/v1` |
| 인증 | 없음. 요청 헤더 `X-User-Id`로 현재 사용자 전달 (계정 전환 드롭다운 값) |
| 응답 봉투 | 성공 시 리소스 객체 직접 반환. 목록은 `{ items: [], page, size, total }` |
| 에러 형식 | `{ code: "POLICY_BLOCKED", message: "…", details: {} }` |
| 시각 | ISO 8601, UTC |
| 명명 | JSON은 camelCase, DB는 snake_case. 변환은 Jackson 설정 |
| URL 규칙 | 리소스 명사 복수형. 구현 기술(ai, mock)을 URL에 노출하지 않음 |

### 8.2 상태 코드 정책

| 코드 | 사용 상황 | 근거 |
|---|---|---|
| 200 | 조회 성공, ALLOW/MASK 판정 (요청은 성공했고 결과가 본문에 있음) | |
| 201 | **사용하지 않음.** message 리소스는 생성되지만 판정 결과에 따라 200/202/403으로 갈림 | 클라이언트가 받아야 할 주 정보가 생성 사실이 아니라 판정 결과임. 201+Location이면 판정을 알기 위해 한 번 더 요청해야 하고, BLOCK을 201로 표현할 방법이 없음 (0.5 D4) |
| 202 | REVIEW 판정으로 AI 비동기 처리 시작. Location 헤더에 폴링 URL | Asynchronous Pipeline |
| 403 | BLOCK 판정. 정책에 의해 전송이 금지됨 | 요청 형식은 유효하나 정책상 거부 |
| 404 | 존재하지 않는 inspection·finding | |
| 409 | 이미 처리된 finding에 ACCEPT/REJECT 재요청 | 상태 충돌 |
| 400 | 본문 누락, 빈 문자열 | |

### 8.3 엔드포인트 목록

| # | Method | Path | 설명 | 상태 | Mock |
|---|---|---|---|---|---|
| 1 | GET | `/departments` | 부서 목록 | 200 | |
| 2 | GET | `/users?deptId=` | 사용자 목록 (계정 전환용) | 200 | |
| 3 | GET | `/policies?deptId=` | 부서에 적용되는 정책과 규칙 | 200 | |
| 4 | POST | `/messages` | 프롬프트 제출 → 규칙 판정 | 200 / 202 / 403 / 400 | AI 부분 Mock |
| 5 | GET | `/inspections/{id}` | 판정 상세 (폴링 겸용) | 200 / 404 | |
| 6 | GET | `/inspections?deptId=&status=&from=&to=&page=&size=` | 감사 목록 | 200 | |
| 7 | PATCH | `/inspections/{id}/findings/{findingId}` | AI 후보 ACCEPT/REJECT | 200 / 404 / 409 | |

### 8.4 엔드포인트 상세

#### POST /messages

요청

```json
{
  "text": "이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 담당자 주민번호 900101-1234567"
}
```

헤더: `X-User-Id: 1`

응답 — BLOCK (403)

```json
{
  "messageId": 1041,
  "inspectionId": 2088,
  "decision": "BLOCK",
  "status": "BLOCKED",
  "submittedText": null,
  "policySnapshot": {
    "policies": [
      { "code": "P-PII", "version": 5 },
      { "code": "P-SEC", "version": 7 }
    ]
  },
  "ruleResult": {
    "matches": [
      { "code": "SEC-DBURL-02", "category": "SECRET", "action": "BLOCK",
        "span": [12, 52], "severity": "HIGH", "obligation": "INTERNAL",
        "source": "정보보안규정 4.2" },
      { "code": "PII-RRN-01", "category": "PII", "action": "MASK",
        "span": [62, 76], "severity": "HIGH", "obligation": "LEGAL",
        "source": "개인정보보호법" }
    ],
    "appliedRuleCodes": ["PII-RRN-01","PII-CARD-02","PII-PHONE-03","PII-EMAIL-04",
                         "SEC-AWSKEY-01","SEC-DBURL-02","SEC-PRIVIP-03"]
  },
  "aiStatus": "SKIPPED",
  "decidedBy": "RULE",
  "createdAt": "2026-09-03T05:31:12Z"
}
```

matches가 2건인 이유는 사설 IP `10.0.3.21`이 SEC-DBURL-02의 매칭 구간 안에 완전히 포함되어 SEC-PRIVIP-03의 finding이 억제되었기 때문입니다 (0.5 D1). appliedRuleCodes에는 그대로 남습니다 — 적용된 규칙과 매칭된 규칙은 다릅니다.

응답 — MASK (200)

```json
{
  "messageId": 1042,
  "inspectionId": 2089,
  "decision": "MASK",
  "status": "MASKED",
  "submittedText": "고객 연락처 [전화번호] 로 회신 요청",
  "ruleResult": {
    "matches": [
      { "code": "PII-PHONE-03", "category": "PII", "action": "MASK",
        "span": [7, 20], "severity": "MEDIUM", "obligation": "LEGAL",
        "source": "개인정보보호법" }
    ],
    "appliedRuleCodes": ["…"]
  },
  "aiStatus": "SKIPPED",
  "decidedBy": "RULE"
}
```

응답 — REVIEW (202)

헤더: `Location: /api/v1/inspections/2090`

```json
{
  "messageId": 1043,
  "inspectionId": 2090,
  "decision": "PENDING",
  "status": "PENDING_REVIEW",
  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
  "ruleResult": {
    "matches": [
      { "code": "CONF-CLIENT-01", "category": "CONFIDENTIAL", "action": "REVIEW",
        "span": [0, 2], "matchedKeyword": "A사", "severity": "MEDIUM",
        "obligation": "INTERNAL", "source": "고객사 NDA 목록 v3" }
    ]
  },
  "aiStatus": "PENDING",
  "pollAfterMs": 2000
}
```

응답 — ALLOW (200)

```json
{
  "messageId": 1044,
  "inspectionId": 2091,
  "decision": "ALLOW",
  "status": "ALLOWED",
  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
  "ruleResult": { "matches": [], "appliedRuleCodes": ["…"] },
  "aiStatus": "SKIPPED",
  "decidedBy": "RULE"
}
```

#### GET /inspections/{id}

응답 — AI 완료 후 (200)

```json
{
  "inspectionId": 2090,
  "messageId": 1043,
  "phase": "INPUT",
  "user": { "userId": 2, "name": "김OO", "department": "영업팀" },
  "submittedText": "A사 차세대 프로젝트 오픈 일정이 언제였지?",
  "status": "PENDING_REVIEW",
  "policySnapshot": { "policies": [ { "code": "P-PII", "version": 5 },
                                    { "code": "P-SEC", "version": 7 },
                                    { "code": "P-CONF", "version": 2 } ] },
  "ruleResult": { "matches": [ { "code": "CONF-CLIENT-01", "action": "REVIEW", "…": "…" } ] },
  "aiStatus": "COMPLETED",
  "aiAssessment": {
    "riskCandidates": [
      {
        "code": "CONF-CLIENT-PROJECT",
        "category": "CONFIDENTIAL",
        "rationale": "'A사 차세대 프로젝트 오픈 일정'이라는 서술이 계약 상대방과 미공개 일정을 동시에 특정함",
        "evidence": [
          { "source": "고객사 NDA 목록 v3", "excerpt": "A사 — 비밀유지 2027.03까지, 일정·범위 포함" }
        ]
      }
    ],
    "missingContext": [ "해당 일정이 대외 공개된 정보인지 확인 필요" ],
    "reviewRequired": true
  },
  "findings": [
    { "findingId": 501, "source": "RULE", "code": "CONF-CLIENT-01", "action": "REVIEW",
      "reviewStatus": "CONFIRMED" },
    { "findingId": 502, "source": "AI", "code": "CONF-CLIENT-PROJECT",
      "rationale": "…", "evidence": [ "…" ], "reviewStatus": "SUGGESTED",
      "reviewedBy": null, "reviewedAt": null }
  ],
  "finalDecision": "PENDING",
  "decidedBy": null,
  "createdAt": "2026-09-03T05:33:40Z",
  "completedAt": "2026-09-03T05:33:43Z"
}
```

aiStatus가 PENDING인 동안은 aiAssessment와 AI findings가 비어 있고, 나머지 필드는 동일합니다.

#### GET /inspections

쿼리: `deptId`, `status`, `from`, `to`, `page`(0부터), `size`(기본 20)

```json
{
  "items": [
    { "inspectionId": 2090, "createdAt": "…", "department": "영업팀", "userName": "김OO",
      "status": "PENDING_REVIEW", "ruleCount": 1, "aiStatus": "COMPLETED", "decidedBy": null },
    { "inspectionId": 2089, "createdAt": "…", "department": "개발팀", "userName": "이OO",
      "status": "MASKED", "ruleCount": 1, "aiStatus": "SKIPPED", "decidedBy": "RULE" }
  ],
  "page": 0, "size": 20, "total": 137
}
```

#### PATCH /inspections/{id}/findings/{findingId}

요청

```json
{ "reviewStatus": "ACCEPTED", "comment": "NDA 대상 고객사 일정. 전송 불가" }
```

응답 (200)

```json
{
  "findingId": 502,
  "reviewStatus": "ACCEPTED",
  "reviewedBy": { "userId": 4, "name": "박OO" },
  "reviewedAt": "2026-09-03T05:40:02Z",
  "inspection": { "inspectionId": 2090, "finalDecision": "BLOCK", "decidedBy": "HUMAN",
                  "status": "BLOCKED" }
}
```

이미 ACCEPTED/REJECTED인 finding에 재요청 시 409입니다. review_status가 CONFIRMED인 규칙 finding에 PATCH가 와도 409이며, 에러 코드로 구분합니다 — 규칙 판정은 사람이 번복하지 않기 때문입니다 (4장, 0.5 D13).

```json
{ "code": "FINDING_ALREADY_REVIEWED", "message": "finding 502 is already ACCEPTED" }
```

### 8.5 비동기 흐름 (202 + 폴링)

```
FE                      BE                          AiInspector(Mock)
 │ POST /messages        │                              │
 │──────────────────────▶│ 규칙 판정 → REVIEW           │
 │                       │ inspection(ai_status=PENDING)│
 │ 202 + Location        │──@Async inspect()───────────▶│
 │◀──────────────────────│                              │ sleep 2500ms
 │ GET /inspections/{id} │                              │
 │──────────────────────▶│ ai_status=PENDING            │
 │ 200 (PENDING)         │                              │
 │◀──────────────────────│                              │
 │   … 2초 간격 …         │◀───── aiAssessment JSON ─────│
 │                       │ ai_result 저장, finding 생성  │
 │ GET /inspections/{id} │ ai_status=COMPLETED          │
 │──────────────────────▶│                              │
 │ 200 (COMPLETED)       │                              │
 │◀──────────────────────│                              │
```

동기와 비동기를 나눈 근거는 다음과 같습니다. 정규식 판정은 밀리초 단위로 끝나므로 기다릴 이유가 없고, 외부 모델 호출은 지연이 예측 불가능하므로 접수와 결과 조회를 분리합니다. 이 분리로 200·202·403이 각각 쓰일 이유를 갖습니다.

### 8.6 Postman 작업 지침

- 컬렉션명: `ai-gateway-v1`
- 환경 변수: `baseUrl`, `userId`
- 7개 요청을 폴더 `departments / policies / messages / inspections`로 구성
- Mock Server는 2일차 오전 FE 선행 개발용. POST /messages에 Example 4개(ALLOW/MASK/BLOCK/REVIEW), GET /inspections/{id}에 Example 2개(PENDING/COMPLETED) 등록
- Example 본문은 8.4의 JSON을 그대로 사용

---

## 9. AI 확장 지점 상세 — 프롬프트·스키마·Mock

### 9.1 AiInspector 인터페이스

```java
public interface AiInspector {
    AiAssessment inspect(AiInspectionRequest request);
}

// AiInspectionRequest
//   String maskedText        → 원문이 아닌 마스킹 적용본. 원문은 절대 전달하지 않음
//   String departmentCode    → DEV / SALES / HR
//   List<String> categories  → 적용 정책 카테고리
//   List<KeywordHit> hits    → KEYWORD 규칙 매칭 근거 (keyword, ruleCode, source)
//   String policyVersion     → 스냅샷 식별용
```

구현체

| 클래스 | 프로파일 | 동작 |
|---|---|---|
| MockAiInspector | `mock` (기본) | 9.5의 케이스 매핑 규칙으로 고정 JSON 반환, 지연 적용 |
| LlmAiInspector | `llm` | 9.2 프롬프트 조립 후 설정된 엔드포인트 호출. 이번 범위에서는 클래스 골격과 설정 키만 작성 |

### 9.2 시스템 프롬프트

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

### 9.3 프롬프트 조립 기준

프롬프트는 시스템 프롬프트(고정) + 사용자 메시지(조립)로 구성합니다. 사용자 메시지는 다음 규칙으로 만듭니다.

| 순서 | 구성 요소 | 출처 | 규칙 |
|---|---|---|---|
| 1 | 부서 컨텍스트 | departmentCode | "요청자 부서: 영업팀" 한 줄 |
| 2 | 적용 정책 카테고리 | categories | "적용 정책: CONFIDENTIAL(고객사 프로젝트 정보 통제)" |
| 3 | 참조 근거 | hits[] | 키워드별 `{keyword, source}` 목록. 현재는 policy_rule의 source 값, RAG 확장 시 knowledge_source 검색 결과로 대체 |
| 4 | 검토 대상 텍스트 | maskedText | 마스킹 적용본. `<text>…</text>`로 감싸 경계를 명확히 함 |

제약

- 원문(original_text)은 어떤 경우에도 프롬프트에 넣지 않음. 규칙 엔진의 MASK가 먼저 적용된 텍스트만 전달
- BLOCK 판정이 난 텍스트는 AI에 보내지 않음 (7.5)
- 최대 입력 길이 4,000자. 초과 시 앞부분만 전달하고 missingContext에 "입력 절단" 기록
- temperature 0, 출력 max_tokens 800. 값은 application.yml에서 주입

조립 예시 (Case B)

```
요청자 부서: 영업팀
적용 정책: CONFIDENTIAL(고객사 프로젝트 정보 통제)
참조 근거:
- 키워드 "A사" — 고객사 NDA 목록 v3 (A사 — 비밀유지 2027.03까지, 일정·범위 포함)
- 키워드 "차세대" — 고객사 NDA 목록 v3 (프로젝트명 포함 여부 확인 필요)
검토 대상:
<text>A사 차세대 프로젝트 오픈 일정이 언제였지?</text>
```

### 9.4 입출력 JSON 스키마

입력 (AiInspectionRequest)

```json
{
  "maskedText": "string",
  "departmentCode": "DEV | SALES | HR",
  "categories": ["PII", "SECRET", "CONFIDENTIAL"],
  "hits": [ { "keyword": "string", "ruleCode": "string", "source": "string" } ],
  "policyVersion": "string"
}
```

출력 (AiAssessment)

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

스키마에 decision, action, block, allow 같은 결정 필드가 없습니다. 이것이 책임 경계를 스키마 수준에서 강제하는 장치입니다. confidence는 실제 확률이 아니므로 두지 않습니다.

### 9.5 Mock 구현 규칙

MockAiInspector는 결정론적으로 동작해야 합니다. 데모에서 같은 입력에 같은 출력이 나와야 하기 때문입니다.

| 조건 | 반환 |
|---|---|
| hits에 "A사" 포함 | `mock/ai/case-b-client-project.json` |
| hits에 "B사" 포함 | `mock/ai/case-client-generic.json` |
| hits가 있으나 위 외 | 후보 0건, missingContext 1건 ("참조 근거와 대조할 사내 문서 없음"), reviewRequired true |
| hits 없음 (호출되지 않아야 함) | IllegalStateException — 규칙 엔진 버그 감지용 |

- 지연: `ai.mock.delay-ms` (기본 2500). 데모에서 스피너가 보이도록 의도적으로 둠
- 픽스처 위치: `src/main/resources/mock/ai/*.json`. 9.4 스키마로 JSON Schema Validator 통과해야 함
- 실패 시뮬레이션: `ai.mock.fail-keyword`에 설정된 키워드가 포함되면 RuntimeException → ai_status FAILED 경로 검증용

### 9.6 실제 연동 시 교체 절차

1. `application.yml`에서 `ai.provider=llm`으로 변경 (또는 환경변수 `AI_PROVIDER=llm`)
2. `AI_ENDPOINT`, `AI_API_KEY`, `AI_MODEL` 주입
3. LlmAiInspector가 9.2 + 9.3으로 요청 생성, 응답을 9.4 스키마로 검증 후 반환
4. 검증 실패 시 ai_status FAILED, 사람 검토로 폴백

교수 피드백 F3의 "로컬 LLM"은 `AI_ENDPOINT`가 사내 주소를 가리키는 것으로 대응합니다. 삼성SDS SGuard-v1, 카카오 Safeguard by Kanana가 오픈소스로 공개되어 있어 사내 호스팅 후보가 구체적입니다. 외부 API를 부르지 않으므로 "검사하려고 원문을 밖으로 보내는" 문제도 생기지 않습니다.

---

## 10. 시드 및 Mock 데이터 계획

### 10.1 원칙

- 모든 시드는 `V2__seed.sql` 한 파일. 재실행 가능하도록 TRUNCATE 후 INSERT
- 데모 케이스 3종은 시드가 아니라 데모 중 실제 입력으로 생성. 단, 실패 대비로 동일 케이스의 완료 상태 레코드를 시드에 1건씩 포함
- 이름은 실명 대신 `김OO` 형식. 이메일은 example.com

### 10.2 부서·사용자

| user_id | 이름 | 부서 | role | 용도 |
|---|---|---|---|---|
| 1 | 이OO | 개발팀 | EMPLOYEE | Case A, Case C |
| 2 | 김OO | 영업팀 | EMPLOYEE | Case B |
| 3 | 정OO | 인사팀 | EMPLOYEE | 시드 로그 |
| 4 | 박OO | 정보보안팀 | SECURITY_ADMIN | SCR-02 확정 |
| 5 | 한OO | 홍보팀 | EMPLOYEE | 시드 로그 (감사 콘솔 부서 필터) |

홍보팀은 code `PR`로 추가합니다 (0.5 D18). P-EMBARGO의 소유 부서이지만 적용 대상은 아니므로 department_policy 매핑이 없습니다. 다만 정보보안팀과 달리 **감사 로그 5건(message/inspection 104~108)을 함께 시드합니다** — 부서 필터에서 홍보팀을 골랐을 때 빈 화면이 나오는 자리가 시연 중에 눌릴 수 있기 때문입니다.

정보보안팀은 code `INFOSEC`, name 정보보안팀으로 department에 추가합니다. department_policy 매핑은 없습니다(검토자 역할만). GLOBAL 정책은 성질상 이 부서에도 적용되지만 프롬프트를 제출하지 않아 inspection이 0건이며, 그래서 감사 콘솔 부서 필터에도 넣지 않습니다 (0.5 D2).

### 10.3 정책·규칙

7.1, 7.2의 3정책 8규칙을 그대로 INSERT. department_policy에 (SALES, P-CONF), (HR, P-CONF) 2행.

### 10.4 데모 케이스 3종 — 정확한 입력과 기대 결과

| 케이스 | 계정 | 입력 문자열 (그대로 복사) | 기대 판정 | HTTP | 화면 |
|---|---|---|---|---|---|
| A | 이OO (개발팀) | `이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나` | BLOCK · 규칙 2건 (SEC-DBURL-02, PII-RRN-01) | 403 | S4 |
| B | 김OO (영업팀) | `A사 차세대 프로젝트 오픈 일정이 언제였지?` | REVIEW → AI 후보 1건 → 담당자 ACCEPT → BLOCKED | 202 → 200 | S5 → SCR-02 |
| C | 이OO (개발팀) | `A사 차세대 프로젝트 오픈 일정이 언제였지?` | ALLOW (P-CONF 미적용) | 200 | S2 |

Case A의 정규식 실행 결과는 원시 매칭 4건입니다 — SEC-DBURL-02 `postgres://admin:p%40ss@10.0.3.21/prod` [18,56], PII-EMAIL-04 `40ss@10.0.3.21` [37,51], SEC-PRIVIP-03 `10.0.3.21` [42,51], PII-RRN-01 `900101-1234567` [73,87]. 뒤 두 개와 PII-EMAIL-04는 SEC-DBURL-02 구간에 완전히 포함되어 억제되므로 finding은 2건입니다. 단위 테스트 기대값은 이 4→2 과정을 그대로 고정합니다 (0.5 D11).

PII-EMAIL-04가 접속 문자열의 일부를 이메일로 오탐하는 것은 정규식의 구조적 한계입니다. 중첩 억제가 이 오탐까지 함께 걸러낸다는 점이 해당 설계의 부수 효과이며, 예상 질의 대응에 쓸 수 있습니다.

Case A에서 SEC-PRIVIP-03(사설 IP)은 매칭되지만 finding으로 세지 않습니다. `10.0.3.21`이 SEC-DBURL-02의 매칭 구간 안에 완전히 포함되기 때문입니다(중첩 억제, 0.5 D1). 화면과 발표 대사 모두 "규칙 2건"으로 통일합니다.

Case B와 C는 같은 문장입니다. 부서만 다르고 결과가 갈리는 것이 부서별 N:M 설계의 증명입니다.

보조 케이스 (시간 여유 시)

| 케이스 | 계정 | 입력 | 기대 |
|---|---|---|---|
| D | 정OO (인사팀) | `지원자 연락처 010-1234-5678 로 면접 안내 문자 초안 써줘` | MASK (PII-PHONE-03), 200, S3 |

### 10.5 감사 로그 시드 100건 생성 규칙

SCR-02 목록이 비어 보이지 않도록 과거 7일 분량을 생성합니다.

```sql
-- 분포 → ALLOWED 55 / MASKED 25 / BLOCKED 12 / PENDING_REVIEW 8
-- 부서 → DEV 45 / SALES 35 / HR 20
-- 시각 → now() - (random() * interval '7 days')
-- 규칙 → MASKED는 PII 규칙 중 랜덤 1건, BLOCKED는 SEC 규칙 중 랜덤 1건
-- PENDING_REVIEW 8건 중 5건은 ai_status COMPLETED + AI finding SUGGESTED (검토 대기 데모용)
-- 나머지 3건은 ai_status FAILED (실패 경로 표시용)
```

generate_series로 생성하고, original_text는 템플릿 20개 중 랜덤 선택. 실제 개인정보 형태 문자열은 시드에 넣지 않고 `[주민번호]` 라벨 상태로 저장합니다.

### 10.6 FE 선행 개발용 Mock

2일차 09:30 Interface Freeze 후 11:00까지 BE가 준비되지 않은 구간은 Postman Mock Server(8.6)로 개발합니다. Axios baseURL을 환경변수로 두어 `VITE_API_BASE`만 바꾸면 실제 BE로 전환됩니다.

---

## 11. 시스템 아키텍처 및 프로젝트 구조

### 11.1 구조도

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend — Vue 3 (Vite)                                     │
│  SCR-01 직원 챗   SCR-02 감사 콘솔   Axios · Pinia · Router    │
└──────────────────────────────┬──────────────────────────────┘
                               │ REST /api/v1 (JSON)
┌──────────────────────────────▼──────────────────────────────┐
│  Backend — Spring Boot 3                                     │
│  ┌──────────┐  ┌──────────────┐  ┌─────────────────────────┐ │
│  │Controller│→ │ Service      │→ │ RuleEngine              │ │
│  │          │  │ Inspection   │  │  RegexMatcher           │ │
│  │          │  │ Policy       │  │  KeywordMatcher         │ │
│  │          │  │ Review       │  │  ConflictResolver       │ │
│  └──────────┘  └──────┬───────┘  └─────────────────────────┘ │
│                       │ @Async                               │
│                ┌──────▼───────────────┐                      │
│                │ AiInspector (I/F)    │                      │
│                │ ├ MockAiInspector    │ ← 현재 (profile mock)│
│                │ └ LlmAiInspector     │ ← 교체 후 (profile llm)│
│                └──────┬───────────────┘                      │
└───────────────────────┼─────────────────────────────────────┘
                        │ (교체 후) HTTP
              ┌─────────▼─────────┐     ┌────────────────────┐
              │ 사내 호스팅 LLM     │     │ PostgreSQL (Cloud) │
              │ 또는 계약 API       │     │ Core 8 tables      │
              └───────────────────┘     └────────────────────┘
```

### 11.2 기술 스택

| 계층 | 선택 | 버전 | 비고 |
|---|---|---|---|
| Frontend | Vue 3 + Vite | Vue 3.4+ | Composition API, `<script setup>` |
| 상태 | Pinia | | currentUser, polling 상태 |
| HTTP | Axios | | 인터셉터로 X-User-Id 자동 주입 |
| Backend | Spring Boot | 3.3+ | Java 21 |
| ORM | Spring Data JPA | | 엔티티 8개 = ERD 8 테이블 |
| 마이그레이션 | Flyway | | V1__schema.sql, V2__seed.sql |
| DB | PostgreSQL 16 | | Supabase 또는 Neon |
| 비동기 | @Async + ThreadPoolTaskExecutor | | 큐 미도입 |
| API 문서 | Postman + springdoc-openapi | | Swagger UI는 자동 생성 |

### 11.3 설정 분리 (Security & Config Isolation)

`application.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:mock}

ai:
  provider: ${AI_PROVIDER:mock}          # mock | llm
  endpoint: ${AI_ENDPOINT:}              # llm 프로파일에서만 사용
  api-key: ${AI_API_KEY:}
  model: ${AI_MODEL:}
  temperature: ${AI_TEMPERATURE:0}
  max-tokens: ${AI_MAX_TOKENS:800}
  timeout-ms: ${AI_TIMEOUT_MS:10000}
  mock:
    delay-ms: ${AI_MOCK_DELAY_MS:2500}
    fail-keyword: ${AI_MOCK_FAIL_KEYWORD:__FAIL__}

gateway:
  polling:
    interval-ms: 2000
    max-attempts: 30
```

키·모델·지연은 전부 환경변수. 정책·규칙·임계값은 DB. 코드에는 어느 쪽도 없습니다.

### 11.4 프로젝트 폴더 구조

Backend

```
backend/
├── src/main/java/com/skala/gateway/
│   ├── GatewayApplication.java
│   ├── config/
│   │   ├── AsyncConfig.java
│   │   ├── AiProperties.java
│   │   └── WebConfig.java              # CORS, X-User-Id 리졸버
│   ├── domain/
│   │   ├── department/  Department.java, DepartmentRepository.java
│   │   ├── user/        AppUser.java, AppUserRepository.java
│   │   ├── policy/      Policy.java, PolicyRule.java, DepartmentPolicy.java, *Repository.java
│   │   └── inspection/  Message.java, Inspection.java, InspectionFinding.java, *Repository.java
│   ├── engine/
│   │   ├── RuleEngine.java
│   │   ├── RegexMatcher.java
│   │   ├── KeywordMatcher.java
│   │   ├── ConflictResolver.java
│   │   └── Masker.java
│   ├── ai/
│   │   ├── AiInspector.java
│   │   ├── AiInspectionRequest.java
│   │   ├── AiAssessment.java
│   │   ├── MockAiInspector.java        # @Profile("mock")
│   │   └── LlmAiInspector.java         # @Profile("llm"), 골격만
│   ├── service/
│   │   ├── PolicyService.java
│   │   ├── InspectionService.java
│   │   └── ReviewService.java
│   └── api/
│       ├── DepartmentController.java
│       ├── PolicyController.java
│       ├── MessageController.java
│       ├── InspectionController.java
│       └── dto/
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/V1__schema.sql, V2__seed.sql
│   └── mock/ai/*.json
└── build.gradle
```

Frontend

```
frontend/
├── src/
│   ├── main.js
│   ├── router/index.js                 # /chat, /admin/audit
│   ├── stores/session.js               # currentUser
│   ├── api/
│   │   ├── client.js                   # axios 인스턴스, X-User-Id 인터셉터
│   │   ├── messages.js
│   │   └── inspections.js
│   ├── composables/usePolling.js
│   ├── components/
│   │   ├── AppHeader.vue
│   │   ├── VerdictCard.vue
│   │   ├── StatusBadge.vue
│   │   ├── AiCandidateList.vue
│   │   └── MaskedText.vue
│   └── views/
│       ├── ChatView.vue                # SCR-01
│       └── AuditView.vue               # SCR-02
├── .env.development                    # VITE_API_BASE
└── package.json
```

### 11.5 GitHub 운영

| 항목 | 규칙 |
|---|---|
| 저장소 | 모노레포 1개. `backend/`, `frontend/`, `docs/` |
| 브랜치 | `main` — 항상 데모 가능 상태. `feat/*` — 기능별. 첨부·시뮬레이터 등 컷 후보는 별도 브랜치 |
| 머지 | PR 1인 리뷰. 2일차 17:00 Feature Freeze 이후 main 머지 금지 (버그 픽스 제외) |
| docs/ | 본 기획서, ERD 이미지, API 명세 export, 발표 자료 |
| README | 실행 방법(환경변수 목록 포함), 데모 케이스 3종 입력 문자열 |

---

## 12. R&R (4인)

가이드 7개 Role을 4명이 겸임합니다. 분배 기준은 2일차 오전에 병렬로 갈라져야 하는 축입니다.

| 담당 | 가이드 Role | 산출물 | 병목 시점 |
|---|---|---|---|
| A | PM + Product/UX Designer | Use-Case(3장), Figma 와이어프레임(5장), 시드 데이터 생성 SQL(10.5), 데모 스크립트(15.2), 발표 슬라이드 총괄 | 1일차 오후, 3일차 오전 |
| B (Joon) | Data Architect + Backend Developer | ERD·DDL(6장, 부록 B), JPA 엔티티, 규칙 엔진(7장), Policy·Inspection API, Q&A 방어 | 2일차 오전 |
| C | API Architect + AI-Ready | API 명세서·Postman(8장), 프롬프트·스키마(9장), MockAiInspector, @Async 202 흐름, Review API | 2일차 오전 |
| D | Frontend Developer + DevOps | Vue 스캐폴딩, SCR-01·02(5장), 폴링, GitHub 세팅(11.5), E2E 통합 검증 | 2일차 오후 |

부하 균형을 위한 원칙

- B는 규칙 엔진과 Policy·Inspection 조회에 집중하고, AI 관련 백엔드(MockAiInspector, @Async, PATCH findings)는 C가 구현. JSON 스키마를 설계한 사람이 그것을 반환하는 코드를 짬
- A는 1일차 와이어프레임 완료 후 2일차에 시드 SQL과 데모 스크립트를 담당. 감사 콘솔 화면이 D에게 버거우면 A가 합류
- 발표는 B가 진행하되 5번 섹션 회고는 4인 각자

---

## 13. 3일 일정

### 1일차 09.02 (수) — 목표: 내일 아침 바로 개발 가능한 상태

| 시각 | 전체 | A | B | C | D |
|---|---|---|---|---|---|
| ~13:00 | 주제·범위 확정 (0장), R&R 확정 | | | | GitHub 레포 생성, 브랜치 규칙 |
| 13:00 | | Use-Case 정리 (3장) | ERD 초안 → dbdiagram | API 명세 초안 (8장) | Vue 프로젝트 생성 |
| 14:30 | | Figma 와이어프레임 SCR-01 5상태 | DDL 작성, Supabase 생성 | 프롬프트·스키마 (9장) | Spring 프로젝트 생성, 폴더 구조 |
| 16:00 | ERD ↔ API 필드명 대조 (B·C) | Figma SCR-02 | Flyway V1 적용 확인 | Postman 컬렉션·Mock 예시 | 라우팅·헤더·계정 전환 |
| 17:30 | EOD 점검 | 와이어프레임 링크 공유 | DB 접속 정보 공유 | Interface Freeze 초안 | FE 빈 화면 2개 기동 |

EOD 3종 검증: FE 기동, BE 기동 + DB 연결, Postman Mock 응답 확인.

### 2일차 09.03 (목) — 목표: Golden Path 1회 성공

| 시각 | A | B | C | D |
|---|---|---|---|---|
| 09:30 | 시드 SQL 작성 시작 | Interface Freeze 확정 (필드명 변경 금지) | Interface Freeze 확정 | Mock으로 SCR-01 개발 시작 |
| 10:30 | 부서·사용자·정책·규칙 시드 | JPA 엔티티 8개, Repository | MockAiInspector, 픽스처 3종 | VerdictCard, MaskedText |
| 11:30 | 로그 100건 생성 SQL | RuleEngine (Regex·Keyword·Conflict·Masker) | @Async 설정, 202 흐름 | 폴링 composable |
| 13:00 | 데모 스크립트 작성 | POST /messages, GET /policies | GET /inspections/{id}, PATCH findings | SCR-02 목록·필터 |
| 14:30 | 슬라이드 뼈대 | GET /inspections 목록 | ReviewService 최종 판정 산출 | SCR-02 상세 패널·ACCEPT/REJECT |
| 15:00 | | **Backend API Freeze** | **Backend API Freeze** | 실제 BE로 전환 |
| 15:30 | E2E 시나리오 점검 보조 | 케이스 A·B·C 백엔드 검증 | 실패 경로(FAILED) 검증 | E2E 케이스 A·B·C |
| 17:00 | **Feature Freeze · E2E 성공** | | | |
| 17:00~ | 여유 시 1인만: KEYWORD 근거를 DB 조회로 (RAG의 R) — 별도 브랜치 | | | |

### 3일차 09.04 (금) — 목표: 리허설 2회 후 발표

| 시각 | 내용 | 담당 |
|---|---|---|
| 09:00 | 슬라이드 완성 (섹션별 담당이 자기 파트 작성) | 전원 |
| 11:00 | 데모 백업 영상 녹화 (케이스 A·B·C, 3분) — 예외 없이 실행 | D |
| 13:00 | 1차 리허설 — 15분 타임어택 | 전원 |
| 13:30 | 피드백 반영, 슬라이드 컷 | A |
| 14:00 | 코드 프리즈. 2차 리허설. 예상 질문 정리 (16장) | 전원 |
| 15:00 | Project Pitch & Live Demo | B 발표, D 데모 조작 |
| 발표 후 | Peer Review 작성·제출 (조별 1개) | A |

핵심 의존 관계: 2일차 09:30 Interface Freeze로 C가 계약을 확정해야 D가 Mock으로 선행 개발할 수 있고, 10:30 엔티티가 끝나야 B가 15:00 API Freeze를 지킬 수 있습니다.

---

## 14. 리스크 및 대응

| 리스크 | 확률 | 영향 | 대응 (언제·누가·무엇을) |
|---|---|---|---|
| Interface Freeze 지연으로 FE·BE 상호 대기 | 높음 | 2일차 오후 병렬 개발 불가 | 2일차 09:30을 계약 고정 시각으로 선언. 이후 필드 변경은 3일차로 미룸. C가 관리 |
| 규칙 엔진 정규식 오탐·미탐 | 중간 | 데모 케이스가 기대와 다르게 판정 | 10.4의 입력 문자열을 단위 테스트로 고정. B가 2일차 13:00 전 통과 확인 |
| 202 폴링이 데모에서 즉시 끝나 비동기가 안 보임 | 중간 | AI-Ready 원칙이 화면에 드러나지 않음 | Mock 지연 2.5초 고정. D가 스피너 노출 확인 |
| 현장 데모 실패 (네트워크·환경) | 중간 | 4번 섹션 붕괴 | 3일차 11:00 백업 영상 녹화. D 담당. 예외 없음 |
| 스코프 확대 (정책 편집 UI, 출력 검사 등) | 중간 | 2일차 소진 | 0.3의 상한 고정. 초과 요청은 17장 향후 확장으로 이관 |
| 법령 조문·통계 수치 오류 | 낮음 | 신뢰 손상 | 발표 전 국가법령정보센터 확인. 미확인 수치는 발표에서 제외 |
| 4인 중 1인 이탈 | 낮음 | 해당 축 정지 | A·D는 서로, B·C는 서로 백업. 문서가 곧 인수인계 자료 |

---

## 15. 발표 및 데모 계획

### 15.1 15분 시나리오

| 순서 | 섹션 | 시간 | 담당 | 핵심 메시지 |
|---|---|---|---|---|
| 1 | 서비스 기획 & Use-Case | 3분 | A | 유출은 해킹이 아니라 승인된 사용 안에서 생긴다. 막지 않고 기록한다 |
| 2 | AI-Ready 설계 포인트 | 2분 | C | 규칙이 결정하고 AI는 제안한다. 확장 지점은 한 곳. 스키마에 결정 필드가 없다 |
| 3 | 아키텍처 & 설계 | 4분 | B | 부서↔정책 N:M, JSONB 스냅샷, 200/202/403이 갈리는 이유 |
| 4 | Scaffolding & 데모 | 4분 | D | 케이스 A·B·C. 같은 문장이 부서에 따라 갈린다 |
| 5 | 회고 및 향후 확장 | 2분 | 전원 | 국내 오픈소스 모델을 사내 호스팅으로 끼우는 로드맵. R&R별 30초 회고 |

오프닝 (첫 30초, A)

화면에 프롬프트 한 줄만 띄웁니다.

> "이 에러 좀 봐줘" — 그리고 붙여넣은 스택 트레이스. 그 안에 DB 접속 문자열과 주민번호가 있습니다. 이건 해킹이 아닙니다. 승인된 사용입니다. 그래서 아무 기록도 남지 않습니다.

통계 수치(출처 확인된 것만)는 그 다음 슬라이드에서 근거로 씁니다.

### 15.2 데모 스크립트 (4분)

| 시각 | 조작 | 화면 | 말할 것 |
|---|---|---|---|
| 0:00 | 계정 이OO(개발팀) 선택, Case A 입력, 전송 | S4 BLOCK, 규칙 2건 | "접속 문자열은 사규로 차단, 주민번호는 법령으로 마스킹 대상. 규칙이 결정했고 AI는 호출되지 않았습니다" |
| 0:50 | 계정 김OO(영업팀) 선택, Case B 입력, 전송 | S5 스피너 → 검토 대기 + AI 후보 | "패턴이 없어서 규칙은 검토 요청만 냈고, AI가 근거와 함께 후보를 제안했습니다. 결정은 아직 없습니다" |
| 1:50 | 감사 콘솔 탭, 방금 건 선택, AI 후보 ACCEPT | 최종 BLOCKED, HUMAN | "담당자가 확정했고 decided_by가 HUMAN으로 기록됐습니다" |
| 2:40 | 계정 이OO(개발팀) 선택, Case C(같은 문장) 입력 | S2 ALLOW | "완전히 같은 문장입니다. 개발팀에는 고객사 정책이 매핑되지 않아 통과합니다. 부서↔정책 N:M이 화면으로 증명됩니다" |
| 3:20 | 감사 콘솔 목록, 부서 필터 전환 | 목록 | "모든 판정이 정책 버전과 함께 남습니다" |
| 3:50 | 종료 | | |

### 15.3 슬라이드 구성 (안)

총 14장. 표지 1, 섹션1 3장, 섹션2 2장, 섹션3 4장(구조도·ERD·API·상태코드), 섹션4 2장(폴더 구조·데모 케이스), 섹션5 2장.

---

## 16. 예상 질의 및 답변

| 질문 | 답변 |
|---|---|
| 정규식이면 되는데 왜 AI인가 | 패턴은 규칙, 맥락은 AI. 고객사명은 정규식으로 잡히지만 그 언급이 기밀 프로젝트 논의인지는 못 잡는다. 분리 자체가 핵심 설계 판단 |
| 검사하려면 결국 AI에 원문을 보내는 것 아닌가 | 규칙이 먼저 마스킹·차단한다. BLOCK은 AI에 안 가고, MASK는 마스킹본만 간다. 맥락 판정 모델은 사내 호스팅 전제이며 국내 오픈소스 후보가 있다 |
| LiteLLM 같은 기성 게이트웨이 쓰면 되지 않나 | 프록시·키 관리는 기성품 영역이고 실제 도입이면 거기에 맡긴다. 우리가 설계한 것은 그 위의 정책·감사 계층 |
| AI가 오판하면 | AI 결과는 전부 SUGGESTED로 저장되고 스키마에 결정 필드가 없다. 사람이 확정하지 않으면 효력이 없다 |
| 부서 정책이 겹치면 | BLOCK > REVIEW > MASK > ALLOW. 가장 엄격한 판정이 이긴다 |
| JSONB는 정규화 포기 아닌가 | 원본은 policy·policy_rule에 정규화. JSONB는 판정 시점 스냅샷이고 정책이 바뀌어도 당시 판정 근거가 보존된다 |
| 왜 202가 필요한가 | 규칙 판정은 밀리초, 외부 모델 호출은 지연 예측 불가. 접수와 결과 조회를 분리했다 |
| 출력 검사는 왜 없나 | 같은 파이프라인을 phase=OUTPUT으로 재사용하는 구조까지 설계했고, 4인 범위 축소로 구현은 제외 |
| 로컬 LLM은 어떻게 붙나 | AI_PROVIDER=llm, AI_ENDPOINT를 사내 주소로. AiInspector 인터페이스는 그대로 |
| 프롬프트 효율·비용 관리는 | 같은 감사 로그를 재활용하는 두 번째 용도. 로그에 토큰 수 컬럼을 추가하면 되며 이번엔 범위 밖 |
| POST인데 왜 201이 없나 | message는 생성되지만 클라이언트가 받아야 할 주 정보는 판정 결과다. 201+Location이면 판정을 알기 위해 한 번 더 요청해야 하고 BLOCK을 201로 표현할 방법이 없다. 그래서 판정을 본문에 싣고 200/202/403으로 갈랐다 |
| 사설 IP도 규칙에 있는데 왜 안 잡혔나 | 잡혔지만 DB 접속 문자열 구간 안에 완전히 포함되어 별도 항목으로 세지 않았다. 같은 문자열을 두 번 세면 위험 건수가 과장된다. 적용된 규칙 목록(appliedRuleCodes)에는 남아 있다 |

---

## 17. 향후 확장 로드맵

| 단계 | 항목 | 변경 범위 |
|---|---|---|
| 1 | 맥락 판정 모델 사내 호스팅 (SGuard-v1, Safeguard by Kanana 등) | LlmAiInspector 구현, 환경변수만. 답변 생성 쪽은 이미 `AnswerClient`로 갈아 끼운다 (0.6) |
| 2 | 근거 문서 검색 (RAG) — knowledge_source 테이블 검색으로 hits 대체 | 9.3의 3번 구성 요소 출처 변경 |
| ~~3~~ | ~~출력 검사 — phase=OUTPUT~~ → **3일차에 구현됨 (0.6)**. 남은 것은 유출 검사를 규칙·목업이 아닌 모델로 돌리는 것 | LlmLeakInspector 구현, 환경변수만 |
| 4 | 첨부파일 검사 — 문서·이미지 추출기 | attachment 테이블, 추출기만 추가. 엔진 무변경. **표 파일은 3일차에 구현됨** — 프론트엔드가 뽑아 기존 경로로 보낸다 (0.6) |
| 5 | 프롬프트 자산화 — 로그에서 효율적 프롬프트 추출, 사내 가이드라인 (교수 피드백 F2) | inspection에 token_count, 별도 집계 뷰 |
| 6 | 정책 편집 UI, 버전 발행, 소급 시뮬레이션 | policy_audit 테이블, 편집 화면 |
| 7 | 기성 게이트웨이(LiteLLM 등) 하단 결합 — 프록시·키 관리 위임 | AiInspector가 게이트웨이 엔드포인트를 가리킴 |
| 8 | 파일 검사 심화 — XLSX 파트 전수 탐색(sharedStrings·숨긴 시트·피벗 캐시), 컨테이너 검증(zip bomb·매크로·외부 링크), 저장값/수식/서식 3중 검사 | 추출기 내부. 엔진·스키마 무변경 |
| 9 | 탐지 정확도 — 공통 정규화(NFKC·제로폭 제거·오프셋 매핑), 체크섬 검증(주민번호·Luhn), 한글 이름 NER | RegexMatcher 앞단에 정규화 계층 추가 |
| 10 | 일관 가명화 — `[주민번호]` 라벨 치환 대신 형식 보존 대체값과 역치환 맵 | 7.6 마스킹 전략 교체. `policy_rule.mask_label` 의미 변경 동반 |
| 11 | 모듈러 모놀리스 전환 — policy·inspection·review 모듈 분리, 경계 강제 | 패키지 재편. 서비스 분리 전 단계 |

---

## 부록 A. 용어 정의

| 용어 | 정의 |
|---|---|
| 판정 (Inspection) | 프롬프트 1건에 대한 검사 1회. 규칙 결과와 AI 결과를 포함 |
| 발견 항목 (Finding) | 판정에서 식별된 개별 위험. 규칙 매칭 또는 AI 후보 |
| 규칙 (Rule) | 정규식 또는 키워드 패턴과 액션의 쌍 |
| 정책 (Policy) | 규칙의 묶음. 카테고리·버전·적용 범위를 가짐 |
| 액션 | 규칙 매칭 시 조치. MASK, BLOCK, REVIEW |
| 최종 판정 | 충돌 해결 후 결정. ALLOW, MASK, BLOCK, PENDING |
| 스냅샷 | 판정 시점의 정책·규칙 상태를 JSONB로 고정한 것 |
| 확장 지점 | AiInspector가 호출되는 UC-03 |

## 부록 B. dbdiagram.io DBML 초안

```dbml
Table department {
  dept_id bigserial [pk]
  code varchar(20) [unique, not null, note: 'DEV | SALES | HR | INFOSEC']
  name varchar(50) [not null]
}

Table app_user {
  user_id bigserial [pk]
  dept_id bigint [ref: > department.dept_id]
  name varchar(50) [not null]
  email varchar(100) [unique]
  role varchar(20) [not null, note: 'EMPLOYEE | SECURITY_ADMIN']
  created_at timestamptz
}

Table policy {
  policy_id bigserial [pk]
  code varchar(20) [unique]
  name varchar(100) [not null]
  category varchar(20) [not null, note: 'PII | SECRET | CONFIDENTIAL']
  version int [not null, default: 1]
  is_active boolean [default: true]
  scope varchar(20) [not null, note: 'GLOBAL | DEPT']
  created_at timestamptz
}

Table policy_rule {
  rule_id bigserial [pk]
  policy_id bigint [ref: > policy.policy_id]
  code varchar(30) [unique]
  rule_type varchar(20) [not null, note: 'REGEX | KEYWORD']
  pattern text [not null]
  action varchar(20) [not null, note: 'MASK | BLOCK | REVIEW']
  mask_label varchar(30)
  severity varchar(10) [not null]
  obligation varchar(20) [not null, note: 'LEGAL | INTERNAL']
  source varchar(100)
  description varchar(200)
  is_active boolean [default: true]
}

Table department_policy {
  dept_id bigint [ref: > department.dept_id]
  policy_id bigint [ref: > policy.policy_id]
  applied_at timestamptz
  indexes { (dept_id, policy_id) [pk] }
}

Table message {
  message_id bigserial [pk]
  user_id bigint [ref: > app_user.user_id]
  original_text text [not null]
  submitted_text text
  status varchar(20) [not null, note: 'ALLOWED | MASKED | BLOCKED | PENDING_REVIEW']
  created_at timestamptz
}

Table inspection {
  inspection_id bigserial [pk]
  message_id bigint [ref: > message.message_id]
  phase varchar(10) [not null, note: 'INPUT | OUTPUT(future)']
  policy_snapshot jsonb [not null]
  rule_result jsonb [not null]
  ai_status varchar(20) [not null, note: 'SKIPPED | PENDING | COMPLETED | FAILED']
  ai_result jsonb
  final_decision varchar(20)
  decided_by varchar(10) [note: 'RULE | HUMAN']
  created_at timestamptz
  completed_at timestamptz
}

Table inspection_finding {
  finding_id bigserial [pk]
  inspection_id bigint [ref: > inspection.inspection_id]
  source varchar(10) [not null, note: 'RULE | AI']
  rule_id bigint [ref: > policy_rule.rule_id, null]
  code varchar(30) [not null]
  category varchar(20) [not null]
  span_start int
  span_end int
  action varchar(20)
  rationale text
  evidence jsonb
  review_status varchar(20) [not null, default: 'SUGGESTED', note: 'SUGGESTED | ACCEPTED | REJECTED | CONFIRMED(규칙 finding 고정)']
  reviewed_by bigint [ref: > app_user.user_id, null]
  reviewed_at timestamptz
}

// Future (Logical only)
Table attachment { attachment_id bigserial [pk]  message_id bigint [ref: > message.message_id]  file_name varchar(200)  extracted_text text  extract_status varchar(20) }
Table knowledge_source { source_id bigserial [pk]  name varchar(100)  type varchar(30)  content text  updated_at timestamptz }
Table policy_audit { audit_id bigserial [pk]  policy_id bigint [ref: > policy.policy_id]  from_version int  to_version int  changed_by bigint  changed_at timestamptz  diff jsonb }
```

## 부록 C. 제출물 체크리스트 (3일차 14:00 기준)

- [ ] GitHub 레포 — README에 실행 방법, 환경변수, 데모 케이스 문자열
- [ ] Use-Case 문서 (3장) — docs/
- [ ] Figma 링크 — SCR-01 5상태, SCR-02, User Flow
- [ ] ERD 이미지 — dbdiagram export, docs/
- [ ] DDL·시드 — Flyway V1, V2 적용 확인
- [ ] API 명세 — Postman 컬렉션 export, Swagger UI 캡처
- [ ] AI-Ready — 프롬프트 전문, JSON 스키마, Mock 픽스처 3종
- [ ] FE·BE 스캐폴딩 — 폴더 구조 캡처
- [ ] E2E 테스트 결과 — 케이스 A·B·C 캡처 또는 영상
- [ ] 데모 백업 영상
- [ ] 발표 슬라이드 14장
- [ ] Peer Review 양식 (발표 후)
