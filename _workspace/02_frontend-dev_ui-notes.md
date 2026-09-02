# FE 구현 노트 — 상태별 렌더링 결정과 API 응답 매핑

**담당:** `frontend-dev`
**작성일:** 2026-09-02
**근거:** 기획서 5장·11.4, `_workspace/00_input/decisions.md` D1~D15, `_workspace/01_api-ai-architect_contract-freeze.md`, `docs/screen-spec.md`
**상태:** SCR-01 5상태 · SCR-02 전 기능 구현 완료. **실제 백엔드(`localhost:8080`)에 붙여 40/40 검증 통과.** 픽스처 모드로도 같은 40건이 통과한다.

**개정 이력**

| 날짜 | 변경 |
|---|---|
| 2026-09-02 | 최초 작성. 픽스처 서버로 37건 검증 |
| 2026-09-02 (2차) | **D14** — ACCEPT 후 `submittedText`를 로컬에서 null로 만들던 코드 제거. **D15** — S4 발화 버블에 작성자 본인의 입력 표시. 실제 백엔드로 재검증(40/40) |
| 2026-09-02 (3차) | PATCH 응답의 `inspection.submittedText`를 그대로 사용(Wave 3). REJECT 경로 검증 추가. `.env.production` 추가 — 프로덕션 빌드에 `VITE_API_BASE`가 없던 문제. **Wave 3 백엔드로 44/44** |
| 2026-09-02 (4차) | **D16** — `aiStatus` 표기를 "분석 중/분석 완료/분석 실패"로. **QA F6** — PATCH 응답의 `completedAt` 반영. **48/48** |

---

## 1. 생성 파일

```
frontend/
├── index.html                     제목을 "사내 AI 게이트웨이"로 교체
├── vite.config.js                 픽스처 서버 플러그인 조건부 등록
├── package.json                   dev:fixtures 스크립트 추가
├── .env.development               VITE_API_BASE=http://localhost:8080/api/v1  (기존)
├── .env.fixtures                  VITE_API_BASE=/api/v1, VITE_FIXTURES=1      (신규)
├── dev/fixture-server.js          개발 전용 픽스처 서버 (§6)
└── src/
    ├── main.js                    pinia·router 설치, X-User-Id 소스 바인딩
    ├── App.vue                    헤더 + RouterView
    ├── style.css                  색상 토큰 8종 정의 (유일한 정의 지점)
    ├── router/index.js            /chat, /admin/audit
    ├── stores/session.js          currentUser, users, departments, policies
    ├── api/client.js              axios 인스턴스, 403 인터셉터, X-User-Id 주입
    ├── api/catalog.js             departments · users · policies
    ├── api/messages.js            POST /messages
    ├── api/inspections.js         GET 상세·목록, PATCH 확정
    ├── composables/usePolling.js  2초 간격·최대 30회·onUnmounted 정리
    ├── lib/terms.js               enum → 화면 용어 (유일한 매핑 지점)
    ├── lib/contract.js            계약 위반 콘솔 보고, 에러 봉투 문구 추출
    ├── components/
    │   ├── AppHeader.vue          탭 2개 + 계정 전환
    │   ├── PolicyCaption.vue      "부서: 개발팀 · 적용 정책 2건"
    │   ├── MessageInput.vue       textarea 3줄 + 전송
    │   ├── MessageBubble.vue      직원 발화 (마스킹본만)
    │   ├── MaskedText.vue         라벨 문자열 검색 → <mark>
    │   ├── VerdictCard.vue        판정 카드
    │   ├── PendingIndicator.vue   스피너 + 경과 초
    │   ├── AiCandidateList.vue    AI 후보 (읽기 전용 / 확정 가능)
    │   └── StatusBadge.vue        상태 배지
    └── views/
        ├── ChatView.vue           SCR-01
        └── AuditView.vue          SCR-02
```

`api/catalog.js`는 11.4 구조에 없는 파일이다. 11.4는 `api/`에 `client.js`·`messages.js`·`inspections.js` 셋만 적었는데 마스터 조회 3종(`/departments`·`/users`·`/policies`)이 갈 자리가 없다. 셋을 한 파일로 묶었다. `lib/`도 11.4에 없는 디렉터리이며, 용어 매핑과 계약 위반 보고를 한 곳에 모으기 위한 것이다.

스캐폴딩 잔재(`HelloWorld.vue`, `src/assets/*`)는 삭제했다.

---

## 2. SCR-01 상태 5종 — 렌더링 결정

| 상태 | 트리거 | 발화 버블 | 판정 카드 | 입력창 | 추가 |
|---|---|---|---|---|---|
| S1 초기 | 진입 | — | — | 활성 | 안내 1줄 + PolicyCaption |
| S2 허용 | 200 `ALLOW` | `submittedText` 그대로 | 초록 배지 · "규칙 0건" · "전송됨" · 규칙 표 없음 | **비움** | — |
| S3 마스킹 | 200 `MASK` | `submittedText` + 라벨 `<mark>` | 노랑 배지 · 규칙 목록 · "마스킹 후 전송됨" | **비움** | — |
| S4 차단 | 403 `BLOCK` | **작성자 본인의 입력값** (D15) | 빨강 배지 · 규칙 목록 + 의무/출처 · "수정한 뒤 재전송" | **원문 복원** | — |
| S5 검토 대기 | 202 `PENDING` | `submittedText`(마스킹본) | 보라 배지 · 규칙 목록 | **비움** | 스피너 → AI 후보(읽기 전용) → 결과 새로고침 |

### 판단이 필요했던 지점

**S4의 발화 버블에 작성자 본인의 입력을 그린다 (D15).**
BLOCK의 `submittedText`는 `null`이라(C4·D5·D14) 서버에서 그릴 본문이 없다. 리더가 **D15로 확정**했다 — 원문 미표시 원칙(5.4)의 대상은 **감사 콘솔에서 보는 타인의 원문**이며, 챗 화면에서 작성자에게 방금 자기가 친 텍스트를 돌려주는 것은 유출이 아니다. 5.3이 이미 차단 시 입력창에 원문을 복원하라고 지시하므로 같은 텍스트가 이미 화면에 있고, 버블만 가리면 일관되지 않는다. 15.1 오프닝이 "붙여넣은 스택 트레이스 안에 접속 문자열과 주민번호가 있습니다"라며 화면을 가리키므로 버블이 비면 그 장면이 성립하지 않는다.

이 텍스트는 **API가 아니라 FE 로컬 상태**(`entry.inputText`)에서 온다. `submittedText`는 판정 결과 표시에만 쓴다. `ChatView.vue`가 `entry.verdict.submittedText ?? entry.inputText`로 넘기므로, 차단이 아닌 상태에서는 서버가 돌려준 마스킹 적용본이 그대로 그려진다.

**감사 콘솔은 그대로다.** SCR-02 §1 원문 섹션은 `submittedText`만 그리고, 규칙 BLOCK 건은 `null`이므로 "차단되어 전송 본문이 저장되지 않았습니다"를 표시한다. 타인의 원문은 어느 경로로도 나오지 않는다.

**S5의 최종 결과는 "결과 새로고침" 버튼으로 반영한다.**
D12가 "폴링으로 따라가지 않고 화면 재조회로 반영"이라고 확정했고, 기획서 5.3은 "새로고침"이라고 적었다. 브라우저 F5로 처리하면 대화 상태가 메모리에서 사라져 데모에서 방금 만든 검토 건이 화면에서 없어진다. 판정 카드 아래 **결과 새로고침 버튼**(= `GET /inspections/{id}` 재조회)을 두었다. D12 문구 그대로이면서 데모 흐름이 끊기지 않는다.

**폴링은 화면당 하나만 돈다.**
검토 대기 중에 또 전송하면 이전 건의 폴링이 멈춘다. 멈춘 건에는 "새 전송으로 자동 확인이 중단되었습니다. 아래 버튼으로 결과를 확인하세요."를 표시하고, 결과 새로고침으로 복구할 수 있게 했다. 폴러를 건별로 늘리면 타이머 정리 책임이 흩어진다.

**계정을 바꾸면 정책 캡션만 다시 불러오고 대화는 남긴다.**
데모가 Case A(개발팀) → Case B(영업팀) → Case C(개발팀)로 계정을 오가며, 앞선 판정 카드가 화면에 남아 있어야 "같은 문장, 다른 결과"를 나란히 보여줄 수 있다.

---

## 3. API 응답 → 컴포넌트 매핑

### `POST /messages` (200 / 202 / 403 — 필드 집합 동일)

| 응답 필드 | 쓰는 곳 | 비고 |
|---|---|---|
| `decision` | `VerdictCard` 배지, ChatView 상태 분기 | 화면에는 한글로만 노출 |
| `status` | 사용하지 않음 (챗은 `decision` 기준) | SCR-02 목록에서 사용 |
| `submittedText` | `MessageBubble` → `MaskedText` | BLOCK이면 `null` |
| `ruleResult.matches[]` | `VerdictCard` 규칙 행. 헤더 "규칙 N건" = **이 배열 길이** | `appliedRuleCodes` 길이가 아니다 |
| `ruleResult.matches[].{code,category,action,obligation,source,matchedKeyword}` | 규칙 행 1줄 | `span`은 쓰지 않는다 (D3) |
| `policySnapshot.policies[].{code,version}` | 카드 하단 "적용 정책 P-PII v3 …" | `ruleCodes`·`policyId`는 읽지 않는다 |
| `aiStatus` | 폴링 시작 여부 | |
| `pollAfterMs` | 폴링 간격 | **FE 상수 대신 이 값을 쓴다** |
| `inspectionId` | 폴링·재조회 대상 | |
| `decidedBy`, `createdAt`, `messageId` | 읽지 않음 | 계약상 존재 |

### `GET /inspections/{id}`

| 응답 필드 | 쓰는 곳 |
|---|---|
| `aiStatus` | 폴링 종료 판정 · SCR-02 AI 섹션 분기 |
| `aiAssessment.missingContext[]` | `AiCandidateList` "확인이 필요한 맥락" |
| `aiAssessment.riskCandidates[]` | 직접 쓰지 않는다 — 화면은 `findings[]`(source=AI)를 그린다 |
| `findings[]` where `source='RULE'` | SCR-02 §2 규칙 판정. `ruleResult.matches[]`와 **code로 조인**해 의무·출처를 붙인다 |
| `findings[]` where `source='AI'` | SCR-02 §3 AI 제안, SCR-01 S5-b 읽기 전용 |
| `findings[].{reviewStatus,reviewedBy,reviewedAt}` | 배지·확정자 표기 |
| `submittedText` | SCR-02 §1 원문 섹션 |
| `policySnapshot.policies[]` | SCR-02 §4 이력 |
| `finalDecision`, `decidedBy`, `status` | 최종 판정 배지 |
| `user.{name,department}` | 패널 헤더. `department`는 **문자열**이다 |
| `spanStart`/`spanEnd` | **쓰지 않는다** (원문 기준이라 마스킹본에서 밀린다) |

`aiAssessment`는 `aiStatus === 'COMPLETED'` 가드 안에서만 접근한다. 202 응답에는 없고, `PENDING`/`FAILED` 상태의 상세 응답에서도 `null`이다.

### `GET /inspections` (목록 봉투)

`{ items, page, size, total }`를 구조 그대로 받는다. `items[]`의 7개 필드가 목록 7컬럼과 1:1이다. `total`이 "총 N건"이다.

### `PATCH .../findings/{findingId}`

응답의 `reviewStatus`·`reviewedBy`·`reviewedAt`으로 해당 finding을, `inspection.{finalDecision,status,decidedBy}`로 패널 헤더와 목록 행을 **재조회 없이** 갱신한다. 409는 "이미 처리된 항목입니다"를 띄우고 해당 건만 재조회한다.

**`submittedText`와 `completedAt`은 서버가 준 값을 그대로 쓴다.** Wave 3부터 PATCH 응답의 `inspection`에 둘 다 실린다(키 5종: `inspectionId`·`finalDecision`·`decidedBy`·`status`·`submittedText`·`completedAt`). 없으면 `expectField`가 계약 위반을 남긴다 — 추론하지 않는다.

**`completedAt` (QA F6)** — 서버가 확정 시각으로 `completed_at`을 갱신한다. 반영하지 않으면 이력 섹션에 "완료 07:25:07"과 "확정자 박OO 07:25:12"가 서로 어긋난 시각으로 나란히 표시된다. 실 HTTP로 확정 전후 값이 바뀌고 `reviewedAt`과 같은 초인 것을 확인했다.

**본문 표시 조건은 `submittedText`의 존재 여부다. `status`가 아니다.** `submittedText`가 null인 것은 "마스킹본이 생성된 적이 없다"는 뜻이고 **규칙 BLOCK 경로에서만** 발생한다. 사람이 확정한 BLOCK은 본문이 남으므로 `status === 'BLOCKED'`로는 두 경우를 구분할 수 없고, 그 조건으로 쓰면 ACCEPT 직후 상태가 BLOCKED로 바뀌는 순간 본문이 사라진다 — D14가 화면에서 무력화되는 경로다. 패널은 처음부터 `v-if="detail.submittedText === null"`로 걸었고, `src/` 어디에도 `status`로 본문을 거는 코드는 없다.

---

## 4. 지켜야 했던 규칙과 구현 위치

| 규칙 | 구현 위치 |
|---|---|
| **403은 에러가 아니다** (C2) | `api/client.js` 응답 인터셉터. `status===403 && data.decision==='BLOCK'`이면 `Promise.resolve`. `code` 필드는 찾지 않는다 |
| `X-User-Id` 자동 주입 | `api/client.js` 요청 인터셉터. store를 직접 import하면 순환 참조라 `main.js`가 `bindUserIdSource`로 연결 |
| **오프셋 산술 금지** (D3) | `MaskedText.vue`. 라벨 5종을 정규식 대안으로 검색해 `<mark>`. escape 후 치환하므로 `v-html`이 안전하다 |
| **CONFIRMED에 버튼 미노출** (D6) | `AuditView.vue` §2 규칙 목록은 버튼 마크업 자체가 없다. `AiCandidateList`는 `source='AI'`만 받고 `SUGGESTED`에만 버튼을 그린다 |
| **캡션 건수 하드코딩 금지** (D8) | `PolicyCaption.vue` — `session.policies.length` |
| **폴링 종료·상한·정리** (D12) | `composables/usePolling.js` — `isDone`은 `aiStatus !== 'PENDING'`, 상한 30, `onUnmounted(stop)`, `runId` 토큰으로 stop 후 도착한 응답이 재예약하지 못하게 막음 |
| 목록은 봉투다 (C1) | `api/*.js`가 봉투를 그대로 반환하고 호출자가 `.items`를 꺼낸다 |
| 부서 필터에 INFOSEC 제외 (D2) | `stores/session.js`의 `filterDepartments` getter |
| 타인의 원문 미노출 (D15가 범위를 확정) | SCR-02 §1은 `submittedText`만 그린다. `originalText`는 어떤 응답에도 없고 어디서도 참조하지 않는다. 챗의 S4 버블은 **작성자 본인의 로컬 입력값**이라 이 규칙의 대상이 아니다 |
| 확정이 본문을 지우지 않음 (D14) | `AuditView.vue`의 `onReview`. `submittedText`는 응답에 실려 올 때만 갱신한다 |
| 색상 토큰 8종 한 곳 | `src/style.css` `:root`. 컴포넌트는 `var(--red)` 형태로만 쓴다 |
| 용어 한 곳 | `src/lib/terms.js`. 내부 enum이 화면에 나오지 않는지 검증에서 확인했다 |
| **D16 — AI는 "분석", 사람은 "검토"** | `AI_STATUS_TERMS` 4값이 "분석 중·분석 완료·분석 실패". 아래 참조 |

### D16 — "분석"과 "검토"를 가르는 기준

`aiStatus`는 **AI의 상태**이므로 "분석", `message.status`·`reviewStatus`는 **사람의 절차**이므로 "검토"다. 감사 목록 한 행에 판정 "검토 대기"와 AI 상태가 나란히 서는데 후자까지 "검토 중"이면 한 행에 "검토"가 두 번 나와 행위 주체가 구분되지 않는다. 4장 책임 경계가 이 프로젝트의 핵심 주장이라 화면에서 흐려지면 안 된다.

**적용 범위는 값 라벨이다. 안내 문장은 기획서 문구를 따른다.**

| 자리 | 문구 | D16 적용 |
|---|---|---|
| `terms.js` `AI_STATUS_TERMS` | 분석 중 / 분석 완료 / 분석 실패 | **적용** |
| `AuditView` AI 제안 섹션 본문 3종 | "AI 분석을 실행하지 않았습니다" / "AI 분석이 진행 중입니다" / "분석 실패 — 담당자 판단이 필요합니다" | **적용** |
| `PendingIndicator` "보안 검토 중" | 5.3이 고정한 프로세스 안내 | **미적용 (고치지 말 것)** |
| `ChatView` "자동 검토 실패 — 담당자 확인 중", 지연 안내 note | 직원 화면 안내 문장 | **미적용 (고치지 말 것)** |

직원에게 필요한 것은 자기 프롬프트가 보안 검토를 받고 있다는 사실이지 AI 개입 여부가 아니다. 나중에 "불일치"로 보고 고치지 않도록 **해당 파일 4곳에 주석을 남겼다.**

`AuditView`의 본문 3종은 리더가 지정한 범위(`terms.js` 4값 + `AuditView` FAILED 문장) 밖이지만 함께 고쳤다. 내가 쓴 문장이고 기획서에서 온 것이 아니며, 바로 위 배지가 "분석 중"인데 본문이 "AI 검토가 진행 중입니다"이면 같은 섹션 안에서 어긋나 D16이 막으려는 혼선이 그대로 남기 때문이다.

---

## 5. 방어 코드를 쓴 자리와 쓰지 않은 자리

옵셔널 체이닝을 **쓴 곳은 세 자리뿐**이다. 전부 계약상 실제로 없을 수 있는 값이다.

- `err.response?.status` — 네트워크 실패에는 응답이 없다
- `detail.aiAssessment?.missingContext` — `PENDING`/`FAILED`에서 `null`
- `finding.match` (규칙 finding ↔ `ruleResult.matches` 조인 실패 시) — 조인 실패는 표시만 줄어든다

**나머지는 덮지 않았다.** `policySnapshot`이 없거나 `ruleResult.matches`가 배열이 아니거나 상태 코드와 `decision`이 어긋나면 `lib/contract.js`의 `expectField`가 콘솔에 `[계약 위반] …`을 남긴다. 화면을 빈 값으로 조용히 그리지 않는다. 데모 전에 콘솔을 한 번 보면 경계면 문제가 바로 드러난다.

---

## 6. 검증

**실제 백엔드와 픽스처 양쪽에서 같은 40건이 통과한다.** 아래 6-1이 백엔드가 없던 동안 쓴 픽스처, 6-2가 실제 백엔드 결과다.

### 6-1. 픽스처 서버 (백엔드가 없을 때의 대체 경로)

`api-ai-architect`의 Postman Mock Server URL을 받지 못했고 착수 시점에 BE 컨트롤러도 없어서, **Vite dev 서버에 붙는 개발 전용 픽스처 서버**(`frontend/dev/fixture-server.js`)를 만들어 7개 엔드포인트를 계약서 §1 shape 그대로 응답하게 했다.

```bash
cd frontend && npm run dev:fixtures     # http://localhost:5173, /api/v1을 자체 응답
```

- `vite.config.js`가 `VITE_FIXTURES=1`일 때만 플러그인을 등록한다. `apply: 'serve'`라 **프로덕션 번들에 들어가지 않는다.**
- 애플리케이션 코드는 이 파일을 import하지 않는다. 앱에는 목 분기가 한 줄도 없다.
- 판정 로직은 기획서 7.2 규칙 8종 정규식 + D1 중첩 억제 + D5 마스킹 분기 + D9 규칙당 1건을 그대로 흉내 낸다. AI는 `ai.mock.delay-ms` 2500ms 지연 후 Case B 픽스처를 돌려준다.
- 응답 shape은 **Postman Example이 아니라 계약서 §1 표**를 기준으로 만들었다. Example이 축약본이기 때문이다 (§7-1).

실제 BE 검증을 마친 뒤에도 이 경로는 남겨 둔다. BE가 내려가 있거나 DB 없이 화면만 볼 때 `npm run dev:fixtures` 한 줄로 데모 4종이 그대로 돈다. 픽스처의 판정 결과가 실제 BE와 어긋나면 그것 자체가 신호이므로, 두 모드에서 같은 40건을 돌려 일치를 확인했다.

### 6-2. 실제 백엔드 — 최종 검증 (Wave 3)

`.env.development`가 `http://localhost:8080/api/v1`을 가리키므로 `npm run dev`로 전환한다. **코드 변경 없이 `VITE_API_BASE` 한 줄로 갈아탔다.**

**컨트롤러 7종(PATCH 포함) 전 경로를 실제 BE로 검증했다.** 픽스처로 남은 항목은 없다.

> **검증 환경 주의.** 검증은 `SERVER_PORT=8081`(3차)·`8082`(4차)로 **소스에서 새로 기동한 인스턴스**에서 했다. 3차 때 `:8080`에 떠 있던 프로세스가 Wave 3 이전 빌드라 PATCH 응답에 `submittedText`가 없었기 때문이다(소스에는 있었다). 팀이 공유하는 `:8080`과 `:5173`은 건드리지 않았고 검증 후 내 인스턴스만 종료했다. **실행 중인 서버가 최신 소스인지 확인하지 않으면 같은 혼선이 반복된다.**
>
> **DB는 검증 후 시드 상태로 복원했다** — `message`/`inspection`의 `id > 103`, 그에 딸린 finding을 삭제하고 시퀀스를 103/103/54로 되돌렸다. 복원 후 행 수 103/103/54, 최대 id 103/103/54를 확인했다.

**API 레벨 (curl)**

| 확인 | 실제 BE 결과 |
|---|---|
| `GET /policies?deptId=1` / `=2` | 2건 (P-PII·P-SEC) / 3건 (+P-CONF) — D8 |
| `GET /policies` (deptId 누락) | 400 `INVALID_PARAMETER` — C6 |
| Case A (이OO/개발팀) | **403** · `BLOCK` · `submittedText=null` · matches 2건 · span **`[18,56]`·`[73,87]`** — 계약서 §6-1 실측값과 일치 |
| Case A 응답 필드 | `policySnapshot`·`pollAfterMs:null`·`createdAt`·`matchedKeyword:null` **전부 존재** — §1-4 필드 집합과 C3(`always`)을 그대로 지킨다 |
| Case D (정OO/인사팀) | 200 · `MASK` · `"지원자 연락처 [전화번호] 로 …"` |
| Case B (김OO/영업팀) | **202** · `Location: /api/v1/inspections/132` · `pollAfterMs=2000` · `matchedKeyword="A사"` (D9) · `appliedRuleCodes` 8건 |
| Case C (이OO/개발팀, Case B와 동일 문장) | 200 · `ALLOW` · matches 0건 |
| 폴링 | `aiStatus` `PENDING`→`COMPLETED`, AI finding `SUGGESTED` 생성 |
| AI finding 필드 | `category:"CONFIDENTIAL"`, `evidence:[{source,excerpt}]` **존재** — §1-5 표대로다 (§7-2 해소) |
| PATCH ACCEPT | 200 · `finalDecision=BLOCK`·`decidedBy=HUMAN`·`status=BLOCKED` |
| PATCH REJECT | 200 · `finalDecision=ALLOW`·`decidedBy=HUMAN`·`status=ALLOWED` |
| **PATCH 응답의 `inspection.submittedText`** | **양쪽 다 실려 오고 보존됨** (`"A사 차세대 프로젝트 …"`) — **D14 준수 확인.** 응답 키: `inspectionId`·`finalDecision`·`decidedBy`·`status`·`submittedText` |
| PATCH 재요청 | 409 `FINDING_ALREADY_REVIEWED` |
| 규칙 finding에 PATCH | 409 `RULE_FINDING_NOT_REVIEWABLE` — D13 |
| 없는 inspection | 404 `INSPECTION_NOT_FOUND` |
| `X-User-Id` 누락 | 400 `MISSING_USER_HEADER` 봉투 (Spring 기본 400이 아니다) — C8 |
| 403 본문 | 판정 객체 유지. `code` 필드 **없음** — C2 그대로. 인터셉터 수정 불필요 |

### 6-3. 화면 레벨 (jsdom에 실제 앱을 마운트해 조작) — **48/48 통과**

`npm run build` 산출물과 같은 클라이언트 번들을 jsdom에 마운트하고 **실제 백엔드에 HTTP로 붙여** 사람이 하듯 조작했다. 검증 스크립트는 저장소에 남기지 않았다(세션 스크래치패드).

실행 중 콘솔에 `[계약 위반]`이 한 건도 찍히지 않았다 — 응답 shape이 계약서 §1과 전부 일치한다는 뜻이다.

| # | 확인 | 결과 |
|---|---|---|
| S1 | 빈 대화 안내 + 입력창 활성 | 통과 |
| S1 | 캡션 "부서: 개발팀 · 적용 정책 2건" (API 응답값) | 통과 |
| S4 | 403이 통신 오류가 아니라 차단 판정으로 렌더 | 통과 |
| S4 | 규칙 2건 + "정보보안규정 4.2" + "사규" | 통과 |
| S4 | 억제된 `SEC-PRIVIP-03`·`PII-EMAIL-04` 미표시 (D1) | 통과 |
| S4 | **버블에 작성자 본인의 입력 표시 (D15)** — 접속 문자열·주민번호가 버블에 보인다 | 통과 |
| S4 | 입력창에 원문 복원 | 통과 |
| S3 | `<mark>[전화번호]</mark>` 하이라이트, 원문 번호 미표시 | 통과 |
| S3 | 입력창 비움 · 캡션 "인사팀 3건" | 통과 |
| S2 | "규칙 0건 · 전송됨" | 통과 |
| S5-a | 스피너 "보안 검토 중" + 경과 초 | 통과 |
| S5-b | 폴링이 COMPLETED를 받아 AI 후보 표시 | 통과 |
| S5-b | 직원 화면에 ACCEPT/REJECT **없음** | 통과 |
| S5-b | COMPLETED 후 폴링 정지, 추가 요청 없음 (D12) | 통과 |
| SCR-02 | 목록 봉투 렌더 + "총 N건" | 통과 |
| SCR-02 | 부서 필터에 정보보안팀 없음 (D2) | 통과 |
| SCR-02 | 행 클릭 → 4개 섹션 패널 로드 | 통과 |
| SCR-02 | 규칙/AI 섹션에 "결정"/"후보" 꼬리표 분리 | 통과 |
| SCR-02 | 규칙 finding에 버튼 없음, 배지 "확정(규칙)" (D6) | 통과 |
| SCR-02 | SUGGESTED AI 후보에만 ACCEPT/REJECT | 통과 |
| SCR-02 | ACCEPT → 패널·목록 행 즉시 갱신(재조회 없이) | 통과 |
| SCR-02 | ACCEPT 후 버튼 사라짐, "확정(위반)" 배지 | 통과 |
| SCR-02 | **ACCEPT 후에도 원문 섹션 본문 보존 (D14)** | 통과 |
| **F6** | **ACCEPT 후 완료 시각 갱신 · 확정자 시각과 같은 초** | 통과 |
| **D16** | 목록 AI 상태가 "분석 …" 표기 | 통과 |
| **D16** | 목록에 "검토 완료"/"검토 중" 없음 (판정 "검토 대기"와 구분) | 통과 |
| SCR-02 | **REJECT 버튼 노출 → 기각 배지 + "최종 판정 허용 · 담당자"** | 통과 |
| SCR-02 | **REJECT 후에도 본문 보존** (PATCH 응답값 사용) | 통과 |
| SCR-02 | REJECT 후 목록 행 즉시 갱신 (허용 · 담당자) | 통과 |
| SCR-02 | 상태 필터 `BLOCKED` 동작 | 통과 |
| SCR-02 | **규칙 BLOCK 건은 "차단되어 전송 본문이 저장되지 않았습니다"** (`submittedText=null`) — D14의 반대편 | 통과 |
| SCR-02 | 규칙 BLOCK 건에도 ACCEPT/REJECT 없음 | 통과 |
| D12 | 확정 직후 챗 화면은 아직 검토 대기 (폴링이 안 따라감) | 통과 |
| D12 | 결과 새로고침 → "최종 판정 차단 · 확정 주체 담당자" | 통과 |
| 용어 | 8개 enum → 허용/마스킹/차단/검토 대기/제안됨/확정(위반)/기각/확정(규칙), 내부 enum 문자열 미노출 | 통과 |
| 라우팅 | `/chat`, `/admin/audit`, `/` → `/chat` | 통과 |

### 6-4. 빌드

`npm run build` 통과 (110 모듈, 경고 없음).

브라우저 육안 확인은 리더가 별도로 처리한다. `npm run dev` 후 `http://localhost:5173/chat`을 열면 위 항목이 그대로 보인다(백엔드가 떠 있어야 한다). 백엔드가 내려가 있으면 `npm run dev:fixtures`.

---

## 7. 계약·명세와 어긋나 보고가 필요한 지점

전부 **화면을 방어 코드로 덮지 않고** 기록만 했다.

**실제 백엔드로 검증한 결과, 구현은 계약서 §1대로 응답한다.** 아래 7-1~7-4는 **Postman 컬렉션(문서)의 문제이고 구현의 문제가 아니다.** `integration-qa`가 Example 기대값으로 회귀 테스트를 짜면 그때 어긋난다.

| # | 대상 | 실제 BE 확인 결과 |
|---|---|---|
| 7-1 | Example의 필드 누락 | **구현은 정상** — `policySnapshot`·`pollAfterMs`·`createdAt`·`matchedKeyword` 전부 응답에 있다 |
| 7-2 | AI finding의 `category`·`evidence` | **구현은 정상** — `category:"CONFIDENTIAL"`, `evidence:[{source,excerpt}]` |
| 7-3 | PENDING Example의 mock 헤더 | Mock Server 전용 문제. 실제 BE에는 영향 없음 |
| 7-4 | Case B/C Example의 `{{userId}}` | Example 문서 문제 |
| 7-5 | PATCH 응답의 `submittedText` | **Wave 3에서 실린다. FE 반영 완료** — ACCEPT·REJECT 양쪽 본문 보존 확인 |
| 7-6 | `CONFIRMED` 용어 | `spec-steward` 확인 대기 |
| 7-7 | S4 발화 버블 | **D15로 결정됨 — 반영 완료** |

### 7-1. Postman Example이 계약서 §1-4보다 필드가 적다 → `api-ai-architect`

**구현이 아니라 Example 문서의 문제다** (실제 BE는 전 필드를 싣는다).

계약서 §1-4는 "**응답 필드 집합은 4개 상태에서 동일하다**"고 확정했고 C3이 `default-property-inclusion: always`를 정했다. 그런데 Example은 그렇지 않다.

| Example | 빠진 필드 |
|---|---|
| MASK (200) | `policySnapshot`, `pollAfterMs`, `createdAt` |
| ALLOW (200) — Case C | `policySnapshot`, `pollAfterMs`, `createdAt` |
| REVIEW (202) — Case B | `policySnapshot`, `decidedBy`, `createdAt`, `ruleResult.appliedRuleCodes` |
| BLOCK (403) — Case A | `pollAfterMs`, `matches[].matchedKeyword` (C3 `always`면 REGEX도 `null`로 실려야 한다) |

`policySnapshot`이 없으면 판정 카드 하단의 "적용 정책 P-PII v3" 줄이 사라진다. 그 줄이 "판정 시점의 정책 버전을 스냅샷으로 남긴다"는 주장을 화면에서 보여주는 유일한 자리라 데모 손실이 크다. **Example을 계약서 §1-4 표에 맞춰 채워 주기를 요청한다.** (FE는 계약서 표를 기준으로 구현했고 픽스처도 그렇게 만들었다.)

### 7-2. `GET /inspections/{id}` COMPLETED Example의 AI finding에 `category`가 없다 → `api-ai-architect`

계약서 §1-5 findings 표는 `category`를 RULE·AI 모두 ○로 적었는데, Example의 finding 502에는 `code`·`rationale`·`evidence`·`reviewStatus`만 있다. 같은 Example의 `evidence`도 `[ "…" ]`(문자열 배열)로 축약돼 있다.

**실제 BE는 계약서 표대로 응답한다** — `category:"CONFIDENTIAL"`, `evidence:[{source,excerpt}]`. Example만 채우면 된다.

### 7-3. Postman "PENDING (200)" Example이 COMPLETED를 가리킨다 → `api-ai-architect`

`GET /inspections/{id}`의 **PENDING Example** 요청 헤더가 `x-mock-response-name: COMPLETED (200)`이다. Mock Server에 붙으면 PENDING을 요청해도 COMPLETED가 돌아와, **폴링의 PENDING→COMPLETED 전이를 Mock으로는 시연할 수 없다.** 헤더 값을 `PENDING (200) — AI 진행 중`으로 고쳐야 한다.

### 7-4. Case B와 Case C Example이 계정을 구분하지 않는다 → `api-ai-architect`

두 Example 모두 요청 문자열이 같고 헤더가 `X-User-Id: {{userId}}`다. 10.4는 Case B가 김OO(2), Case C가 이OO(1)이라고 확정했는데 Example만 보면 왜 결과가 갈리는지 드러나지 않는다. 이 대비가 부서별 N:M 설계의 증명이므로 Example에 실제 값(2 / 1)을 박아 두기를 요청한다.

### 7-0. 프로덕션 빌드에 `VITE_API_BASE`가 없었다 → **수정 완료**

`.env.development`(8080)와 `.env.fixtures`(`/api/v1`)만 있고 `.env.production`이 없어서, `npm run build` 산출물의 `baseURL`이 `undefined`였다. 그 상태로 `npm run preview`를 돌리면 axios가 상대 경로로 요청해 정적 서버가 `index.html`을 돌려주고, **"JSON 파싱 실패" 같은 엉뚱한 에러**로 나타난다. README가 `npm run preview`를 안내하므로 실제로 밟을 수 있는 경로였다.

`.env.production`을 추가했다(계약서 §5-4의 CORS 기본값이 이미 `http://localhost:4173`을 허용하므로 대상은 로컬 BE다). 실제 배포 시에는 빌드 시점에 `VITE_API_BASE`를 주입해 덮어쓴다. 함께 `api/client.js`에 **값이 비면 기동 시 계약 위반을 남기는** 가드를 넣어, 다음에 같은 일이 나면 콘솔 첫 줄에서 원인이 드러나게 했다.

### 7-5. PATCH 응답의 `submittedText` → **D14로 정리됨. FE 위반 수정 완료**

**이전 구현이 틀렸다.** 계약서 §1-7의 "BLOCK으로 전이하면 `message.submitted_text`를 `null`로 되돌린다"를 근거로 ACCEPT 직후 패널의 원문 섹션을 비웠는데, **그 문구는 D14로 폐기됐다.**

D14 — `submitted_text IS NULL`은 "마스킹본이 생성된 적이 없다"를 뜻하고 **규칙 BLOCK 경로에서만** 발생한다. 사람이 확정한 BLOCK은 본문을 **보존**하며, PATCH는 어떤 경우에도 이 값을 수정하지 않는다.

기존 코드대로면 데모 1:50에 ACCEPT를 누르는 순간 상세 패널 본문이 사라진다 — 감사 담당자가 방금 판단한 근거가 화면에서 없어지는데, 그게 정확히 D14가 막으려던 상황이다. **해당 분기를 제거했다.**

**Wave 3에서 PATCH 응답의 `inspection`에 `submittedText`가 실린다.** 이제 그 값을 그대로 쓰고, 없으면 `expectField`가 계약 위반을 남긴다. ACCEPT·REJECT 양쪽에서 본문이 보존되는 것을 실 HTTP로 확인했다.

`status === 'BLOCKED'`로 본문 표시를 걸면 안 된다는 `api-ai-architect`의 지적은 **현재 코드에 해당하지 않는다.** 패널은 처음부터 `v-if="detail.submittedText === null"`이고, `src/` 어디에도 `status`로 본문을 거는 코드가 없다.

**픽스처 서버도 같이 고쳤다** — `recompute()`가 사람 확정 BLOCK에서 `submittedText`를 지우지 않는다.

### 7-6. `CONFIRMED`의 화면 표기 — `docs/screen-spec.md` §1.2가 기획서 5.6과 어긋난다 → `spec-steward`

screen-spec §1.2는 CONFIRMED에 대해 "**5.6에 대응 용어가 없으므로** 라벨을 만들지 않고 배지를 그리지 않음"이라고 적었다. 그런데 **기획서 5.6에는 `CONFIRMED → 확정(규칙)`이 있다.** 전제가 사실과 다르다.

기획서(SSOT 1단)와 팀 리더 지시의 용어 목록을 따라 **`blue` 토큰의 "확정(규칙)" 배지를 그렸다.** 색을 blue로 둔 것은 5.2의 "blue = 규칙 판정 라벨"에 맞춘 것이며, AI 후보의 purple/red/gray와 시각적으로 갈린다. **D6의 실질(규칙 finding에 ACCEPT/REJECT 미노출)은 그대로 지켰다.** screen-spec §1.2 문구를 정정할지 확인이 필요하다.

### 7-7. S4 발화 버블 → **D15로 결정됨. 반영 완료**

리더가 **D15로 확정**했다 — 원문 미표시 원칙(5.4)의 대상은 감사 콘솔에서 보는 타인의 원문이고, 챗 화면에서 작성자 본인에게 자기 입력을 돌려주는 것은 유출이 아니다. **입력 원문을 버블에 표시한다.** 15.1 오프닝이 화면을 가리키며 "붙여넣은 스택 트레이스 안에 접속 문자열과 주민번호가 있습니다"라고 하므로 버블이 비면 그 장면이 성립하지 않는다.

FE 로컬 상태에서 오는 값이라 API 변경은 없다. 구현 상세는 §2의 "판단이 필요했던 지점" 참조.

### 7-8. 기간 필터의 `to`는 미만이다 (참고)

계약서 §1-6이 `from` 이상 `to` 미만으로 정했다. 화면의 "기간 종료"는 그날을 포함하는 것이 자연스러워, 질의 시 **하루를 더해** 보낸다(`AuditView.vue`의 `exclusiveEndIso`). 시각 표기와 필터 모두 UTC 기준으로 통일했다 — `MM-DD HH:mm`은 ISO 문자열에서 직접 잘라 쓰므로 브라우저 시간대에 따라 흔들리지 않는다. 시드 로그의 시각 표기가 로컬 시간과 다르게 보이면 이 결정 때문이다.

---

## 8. 미완료 / 다음 라운드

| 항목 | 비고 |
|---|---|
| ~~실제 BE 연동~~ | **완료.** 컨트롤러 7종(PATCH 포함) 전 경로를 실제 BE로 검증했다 (§6-2, §6-3) |
| 브라우저 육안 확인 | 리더가 처리 |
| Postman Mock Server 연동 | URL 미수령. 픽스처 서버로 대체했고, 실제 BE 검증을 마쳤으므로 더 필요하지 않다 |
| ~~PATCH 응답의 `submittedText`~~ | **완료.** Wave 3에서 실리고 FE가 그 값을 쓴다 (§7-5) |
| `:8080` 실행 인스턴스 최신화 | 최종 검증 시점의 `:8080` 프로세스가 Wave 3 이전 빌드였다. 데모 전에 재기동해 소스와 맞춰야 한다 (§6-2 주의) |
| `integration-qa` E2E | 준비 완료. 조작 가능 상태다 |
| 감사 목록 정렬·검색 | 계약에 정렬 파라미터가 없다(§1-6, `createdAt DESC` 고정). 5.4 도해의 "검색" 버튼은 만들지 않았다 — 필터 변경 시 즉시 조회한다 |
