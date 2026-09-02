# 제출물 체크리스트 — 부록 C 추적표

| 항목 | 내용 |
|---|---|
| 출처 | `사내_AI_게이트웨이_기획서_v1.md` 부록 C (3일차 14:00 기준), 1.1 (일정별 필수 산출물), 12장 (R&R) |
| 기준 시각 | 3일차 2026-09-04 (금) 14:00 — 코드 프리즈 |
| 담당 | spec-steward (부록 C 누락 추적) |
| 최종 점검 | **2026-09-02 (2차) — 저장소·DB 실측** |

상태 값은 **완료 / 진행 / 미착수** 세 가지입니다. "진행"은 산출물이 일부만 존재하거나 검증이 남은 상태입니다. 상태는 추측하지 않고 저장소와 DB를 확인해서 적습니다.

## 0. 실측 결과 (2026-09-02 2차 점검)

| 확인 항목 | 결과 |
|---|---|
| Flyway 적용 | `flyway_schema_history` **2건** (V1 schema, V2 seed) |
| 시드 데이터 | department **4**(INFOSEC 포함, D2) · policy_rule **8** · message 118 · inspection 118 · inspection_finding 75 |
| 백엔드 소스 | 84개 파일 — 엔티티 8종, enum 14종, 리포지터리 9종, 엔진 7종, AI 9종, 서비스 4종, 컨트롤러 5종 + DTO 15종 |
| API 엔드포인트 | **7/7 코드 존재** — `/departments` `/users` `/policies` `POST /messages` `GET /inspections` `GET /inspections/{id}` `PATCH /inspections/{id}/findings/{findingId}` |
| Mock 픽스처 | **3종** — `case-b-client-project.json` · `case-client-generic.json` · `case-no-reference.json` |
| 테스트 | 8파일 — `RuleEngineDemoCaseTest` · `MaskerTest` · `DemoCaseApiTest` · `ReviewApiTest` · `MasterDataApiTest` · `InspectionAiResultSinkTest` · `DemoCases` |
| 프런트엔드 | 뷰 2종(ChatView·AuditView), 컴포넌트 9종, 폴링 composable, 라우터·스토어·API 클라이언트 |
| 문서 | `docs/` 8개 파일, `_workspace/` 노트 5개 + 계약 확정본 |
| **git 커밋** | **0건.** 모든 파일이 여전히 untracked |
| 이미지·영상 | 저장소 전체에 `.png/.jpg/.mp4` **0개** |

의도적 미구현은 `LlmAiInspector`의 `UnsupportedOperationException` 하나뿐이며, 기획서 9.1이 "클래스 골격과 설정 키만"으로 지정한 것입니다.

## 1. 부록 C 추적표

| # | 제출물 | 담당 (역할 / 에이전트) | 산출물 경로 | 상태 | 비고 |
|---|---|---|---|---|---|
| 1 | GitHub 레포 — README에 실행 방법, 환경변수, 데모 케이스 문자열 | D / `frontend-dev` (DevOps 겸임, 11.5) | `README.md` | **진행** | README는 실행·환경변수·데모 케이스까지 완비되어 있고 Case A 문자열이 10.4와 일치함. **커밋이 0건이라 GitHub에 올라간 것이 아직 없음** — 이 항목의 남은 작업은 문서가 아니라 커밋·푸시 |
| 2 | Use-Case 문서 (3장) | A / `spec-steward` | `docs/use-cases.md` | **완료** | UC-02·04·05·07 신규 상세, UC-01·03·06 보강, D7~D15 반영 |
| 3 | Figma 링크 — SCR-01 5상태, SCR-02, User Flow | A (사람 작업) | Figma 파일 링크 → README | **미착수** | 지침은 `docs/screen-spec.md`. 프레임 8개·Color Style 8개 목록이 5절에 있음 |
| 4 | ERD 이미지 — dbdiagram export | B / `data-architect` | `docs/erd.dbml` → `docs/erd.png` | **진행** | DBML은 있고 **이미지 export만 남음**. dbdiagram.io에 붙여넣고 PNG 저장 |
| 5 | DDL·시드 — Flyway V1, V2 적용 확인 | B / `data-architect` | `backend/src/main/resources/db/migration/V1__schema.sql`, `V2__seed.sql` | **완료** | DB 실측으로 적용 확인. 부서 4·규칙 8·감사 로그 적재 완료 |
| 6 | API 명세 — Postman 컬렉션 export, Swagger UI 캡처 | C / `api-ai-architect` | `docs/api-spec.md`, `docs/ai-gateway-v1.postman_collection.json` | **진행** | 명세와 컬렉션은 완료. **Swagger UI 캡처만 남음** (springdoc 기동 후 화면 저장) |
| 7 | AI-Ready — 프롬프트 전문, JSON 스키마, Mock 픽스처 3종 | C / `api-ai-architect` | `docs/ai-prompt.md`, `backend/src/main/resources/mock/ai/*.json` | **완료** | 픽스처 3종 확인. 초판에서 지적한 "3종 vs 파일 2개" 불일치는 `case-no-reference.json` 추가로 해소됨 |
| 8 | FE·BE 스캐폴딩 — 폴더 구조 캡처 | B·D / `data-architect`·`frontend-dev` | `docs/structure-be.png`, `docs/structure-fe.png` | **진행** | 11.4 폴더 구조가 양쪽 모두 완성됨. **캡처만 남음** |
| 9 | E2E 테스트 결과 — 케이스 A·B·C 캡처 또는 영상 | D / `integration-qa` | `docs/e2e-result.md` | **완료** | A·B·C·D 전건 통과를 API·화면 양쪽에서 기록. 부록 C 문구는 "캡처 또는 영상"이지만 실측 리포트가 더 강한 증거이고, 화면 증거는 #10 백업 영상이 겸함. 미해소 7건(전부 데모 비차단)은 `_workspace/03_integration-qa_report.md` |
| 10 | 데모 백업 영상 | D / `frontend-dev` | `docs/demo-backup.mp4` | **미착수** | 3일차 11:00 녹화, 예외 없음 (13장). 조작 순서는 `docs/demo-script.md` 3절 |
| 11 | 발표 슬라이드 14장 | A 총괄 + 섹션별 담당 (사람 작업) | 슬라이드 파일 → README 링크 | **미착수** | 구성안은 15.3 |
| 12 | Peer Review 양식 (발표 후) | A (사람 작업) | 제출 양식 | **미착수** | 조별 1개 |

## 2. 부록 C에 없지만 필요한 산출물

| # | 산출물 | 담당 | 경로 | 상태 |
|---|---|---|---|---|
| 13 | 화면 명세 (5장 정리) | `spec-steward` | `docs/screen-spec.md` | **완료** |
| 14 | 데모 스크립트 (15.2 확장) | `spec-steward` | `docs/demo-script.md` | **완료** |
| 15 | 계약 확정본 (Interface Freeze) | `api-ai-architect` | `_workspace/01_api-ai-architect_contract-freeze.md` | **완료** |
| 16 | 미결 항목 보고 | `spec-steward` | `_workspace/01_spec-steward_open-questions.md` | **완료** |
| 17 | 규칙 엔진 단위 테스트 (10.4 문자열 고정) | `rule-engine-dev` | `backend/src/test/.../RuleEngineDemoCaseTest.java`, `DemoCases.java` | **완료** |
| 18 | 구현 노트 3종 | `data-architect`·`rule-engine-dev`·`frontend-dev` | `_workspace/02_*.md` | **완료** |
| 19 | QA 리포트·경계면 매트릭스 | `integration-qa` | `_workspace/03_integration-qa_report.md`, `_workspace/03_integration-qa_boundary-matrix.md` | **완료** |

## 3. 남은 것 요약

부록 C 12개 중 **완료 4 · 진행 4 · 미착수 4**입니다. 초판 점검(완료 2 · 진행 3 · 미착수 7) 대비 구현·검증 축은 끝났고, **남은 것은 대부분 "만드는 일"이 아니라 "찍고 올리는 일"** 입니다.

| 성격 | 항목 | 소요 |
|---|---|---|
| 커밋·푸시 | #1 — 커밋 0건 상태. 저장소에 아무것도 올라가 있지 않음 | 즉시 |
| 화면 캡처·export | #4 ERD PNG · #6 Swagger 캡처 · #8 폴더 구조 캡처 | 각 5~10분 |
| 사람 작업 | #3 Figma · #10 백업 영상 · #11 슬라이드 · #12 Peer Review | 3일차 |

**가장 시급한 것은 #1입니다.** 커밋이 0건이면 루브릭의 "GitHub 관리 및 R&R 적절성"(1.3)을 평가할 근거가 없고, 3일간의 작업 이력이 남지 않습니다. 나머지 산출물이 아무리 완성돼도 이 항목 하나로 제출물 전체가 로컬에만 존재하는 상태가 됩니다.

그다음은 #4·#6·#8 세 건의 캡처입니다. 만들 것이 남아서가 아니라 화면을 저장하지 않아서 열려 있는 항목이고, 셋을 합쳐 30분이면 닫힙니다.

## 4. 최종 점검 절차 (3일차 13:30, A)

| # | 확인 | 통과 기준 |
|---|---|---|
| 1 | 위 12개 항목의 상태가 전부 완료 | 미착수 0건 |
| 2 | README의 데모 문자열이 10.4와 한 글자도 다르지 않음 | `docs/demo-script.md` 1절과 대조 |
| 3 | 슬라이드·화면·문서의 용어가 5.6 표와 일치 | 내부 enum 값이 화면·슬라이드에 노출되지 않음. CONFIRMED는 "확정(규칙)". AI 상태는 공란 / 분석 중 / 분석 완료 / 분석 실패이고 "검토"는 사람의 절차에만 씀 (D16 제안) |
| 4 | 슬라이드에 법령 조문 번호·미확인 통계가 없음 | 14장 리스크 대응 |
| 5 | Case A 관련 모든 표기가 "규칙 2건" | 화면·슬라이드·대사·README (D1, D11) |
| 6 | 백업 영상 재생 확인 | 발표 노트북에서 직접 재생 |
| 7 | main 브랜치가 데모 가능 상태로 푸시되어 있음 | 11.5 브랜치 운영 |

## 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-09-02 | 최초 작성 — 부록 C 12항목에 담당·경로·상태 부여, 중간 산출물 5건 추가 | 부록 C가 체크박스만 있어 추적 불가 |
| 2026-09-02 | **2차 점검 — 전 항목 상태 갱신.** DDL·시드·AI-Ready 완료 전환, ERD·API·스캐폴딩·E2E를 진행으로, 실측 결과 절 신설, 커밋 0건을 최우선 항목으로 승격 | 구현이 하루 만에 대부분 완료되어 초판 상태표가 전부 무효화됨 |
| 2026-09-02 | 3차 — #9 E2E 완료 전환(`docs/e2e-result.md`), QA 산출물 #19 추가, 최종 점검 3번에 `aiStatus` 표기 기준 추가 | QA 실행 결과가 산출물로 제출됨 |
