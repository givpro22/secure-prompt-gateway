---
name: vue-screens
description: "사내 AI 게이트웨이의 Vue 3 화면을 구현하는 스킬. SCR-01 직원 챗 5상태(초기/허용/마스킹/차단/검토대기), SCR-02 관리자 감사 콘솔(목록·필터·상세·ACCEPT/REJECT), 202 폴링 composable, Pinia 계정 전환, Axios 인터셉터, 색상 토큰을 다룬다. 화면·컴포넌트·폴링·상태 렌더링·마스킹 하이라이트·감사 콘솔 작업에 반드시 사용. '화면 고쳐', '스피너 안 돌아', '차단이 에러로 보여', '필터 추가' 같은 후속 요청에도 사용."
---

# Vue Screens — 화면 구현

기획서 5장(화면 설계)·11.4(폴더 구조)가 원본이다. 화면은 2개뿐이지만 SCR-01은 상태가 5개고, 그 5개가 이 프로젝트의 판정 로직을 시각적으로 증명한다. ALLOW만 되는 화면은 아무것도 증명하지 못한다.

## 선행 개발

**BE를 기다리지 않는다.** Interface Freeze 직후 `api-ai-architect`가 Postman Mock Server URL을 통보하면 즉시 시작한다.

```
VITE_API_BASE=https://<postman-mock-id>.mock.pstmn.io/api/v1   # 개발 초기
VITE_API_BASE=http://localhost:8080/api/v1                      # BE 준비 후
```

전환이 환경변수 한 줄인 것이 요점이다. `client.js`의 `baseURL`을 `import.meta.env.VITE_API_BASE`로 두고 어디에도 URL을 하드코딩하지 않는다.

## Axios 인터셉터 — 403은 에러가 아니다

가장 먼저 만들고 가장 자주 틀리는 부분이다. Axios는 4xx를 reject하므로, 그대로 두면 **차단 판정이 "통신 오류"로 보인다.**

```js
client.interceptors.response.use(
  res => res,
  err => {
    // 403은 정책 판정 결과다. 판정 본문을 정상 경로로 넘긴다
    if (err.response?.status === 403 && err.response.data?.decision === 'BLOCK') {
      return Promise.resolve(err.response)
    }
    return Promise.reject(err)
  }
)
```

`decision === 'BLOCK'`을 확인하는 이유는, 판정과 무관한 403(있다면)을 삼키지 않기 위해서다. 요청 인터셉터에서는 Pinia의 `currentUser.userId`를 `X-User-Id` 헤더로 자동 주입한다.

## SCR-01 상태 5종

각 상태는 별개 프레임이다. 어떤 응답이 어떤 상태로 가는지가 계약이다.

| 상태 | 트리거 | 표시 | 입력창 |
|---|---|---|---|
| S1 초기 | 진입 | 빈 대화, PolicyCaption | 활성 |
| S2 ALLOW | 200 · `decision=ALLOW` | 발화 그대로, 초록 "전송됨", 규칙 0건 | 비움 |
| S3 MASK | 200 · `decision=MASK` | `submittedText` + 마스킹 하이라이트, 노랑 배지, 규칙 목록 | 비움 |
| S4 BLOCK | 403 · `decision=BLOCK` | 빨강 배지, 규칙 목록 + 사규 출처, "수정 후 재전송" | **원문 복원** |
| S5 PENDING | 202 | 보라 스피너 "보안 검토 중" + 경과 시간 → COMPLETED 후 "검토 대기" + AI 후보(읽기 전용) | 비움 |

**S4에서 입력창에 원문을 복원한다.** 차단의 목적은 수정 후 재전송을 유도하는 것이므로 입력을 날리면 사용자가 처음부터 다시 써야 한다. S3는 이미 마스킹되어 전송됐으므로 비운다.

S5의 AI 후보 목록은 **읽기 전용**이다. 직원 화면에 ACCEPT/REJECT 버튼을 두면 책임 경계가 무너진다. 확정은 SCR-02에서 보안 담당자만 한다.

## 폴링

```js
// composables/usePolling.js
// 2초 간격, 최대 30회 (기획서 5.3 / 11.3 gateway.polling)
```

세 가지를 반드시 지킨다.

1. **종료 조건** — `aiStatus !== 'PENDING'`이면 즉시 중단
2. **상한** — 30회 초과 시 중단하고 "검토가 지연되고 있습니다" 표시. 무한 폴링은 데모 중 네트워크 탭을 채운다
3. **정리** — `onUnmounted`에서 타이머를 clear. 안 하면 감사 콘솔로 탭을 옮긴 뒤에도 요청이 계속 나간다

**202 시점에 `aiAssessment`를 참조하지 않는다.** 202 응답에는 없는 필드다. `aiStatus === 'COMPLETED'`를 확인한 뒤에만 접근한다. 이것이 이 프로젝트에서 가장 잡기 쉬운 런타임 크래시다.

S5의 최종 결과(담당자 확정 후 BLOCKED/ALLOWED)도 폴링으로 반영된다. 데모에서는 감사 콘솔에서 확정한 직후 챗 화면을 새로고침해 보여준다(5.3).

## 마스킹 하이라이트

오프셋 산술을 하지 않는다. `submittedText`에서 `[주민번호]`·`[전화번호]`·`[카드번호]`·`[이메일]`·`[내부IP]` 라벨 문자열을 찾아 `<mark>`로 감싼다.

finding의 `span`은 **원문 기준**이라 마스킹본에 그대로 쓰면 하이라이트가 밀린다. 마스킹은 길이를 바꾸기 때문이다(`900101-1234567` 14자 → `[주민번호]` 6자). 배경은 `spec-contract` 스킬 `references/open-questions.md` Q3.

`v-html`을 쓰므로 라벨 치환 전에 텍스트를 escape한다. 사용자 입력이 그대로 렌더링되는 자리다.

## SCR-02 감사 콘솔

목록 + 우측 상세 패널. 행 클릭 → `GET /inspections/{id}` → 패널 로드.

### 목록 응답은 봉투다

```js
const { items, page, size, total } = res.data   // 배열이 아니다
```

`res.data.filter(...)`를 호출하면 `filter is not a function`이 난다. 페이지네이션 응답을 배열로 오해하는 것이 경계면 버그의 전형이다.

### 상세 패널 4섹션

1. **원문** — `submittedText`(마스킹본)만. `originalText`는 API 응답에 없고, 있어도 렌더링하지 않는다(5.4). 원문 열람은 Future
2. **규칙 판정 (결정)** — `source='RULE'` finding. `reviewStatus`가 `CONFIRMED`이므로 **ACCEPT/REJECT 버튼을 노출하지 않는다**
3. **AI 제안 (후보)** — `source='AI'` finding. `SUGGESTED`일 때만 버튼 노출
4. **이력** — `policySnapshot`의 버전, `decidedBy`, `reviewedBy`, `reviewedAt`

2와 3을 좌우로 나누는 것이 "규칙은 결정하고 AI는 제안한다"는 주장의 시각적 증명이다. 두 목록을 한데 섞으면 발표의 핵심 메시지가 화면에서 사라진다.

### ACCEPT/REJECT

`PATCH` 성공 응답에 갱신된 inspection 상태가 함께 온다. 재조회 없이 패널과 목록 행을 즉시 갱신한다. 모든 AI 후보 처리가 끝나면 상단에 최종 판정 배지(BLOCKED/ALLOWED · HUMAN)를 표시한다.

409가 오면 "이미 처리된 항목입니다"를 표시하고 해당 행만 재조회한다. 다른 탭에서 먼저 처리된 경우다.

## 색상 토큰

기획서 5.2의 8개 토큰을 CSS 변수로 **한 곳에** 정의한다. 컴포넌트마다 hex를 직접 쓰면 판정 상태별 색이 어긋나고, 어긋난 색은 데모에서 바로 보인다.

```css
:root {
  --navy:#16202E; --blue:#2F5D8A; --red:#C2452D; --amber:#B7791F;
  --purple:#5B4B8A; --green:#2E7D5B; --gray:#6B7280; --card:#F4F6F9;
}
```

상태 → 색 매핑: ALLOW→green, MASK→amber, BLOCK→red, REVIEW/AI 후보→purple, 규칙 판정 라벨→blue.

## 용어

내부 enum을 화면에 노출하지 않는다(5.6). `StatusBadge.vue`에서 한 번만 매핑한다.

`ALLOWED`→허용 · `MASKED`→마스킹 · `BLOCKED`→차단 · `PENDING_REVIEW`→검토 대기 · `SUGGESTED`→제안됨 · `ACCEPTED`→확정(위반) · `REJECTED`→기각

## 방어 코드를 쓰지 않는다

API 응답 필드가 계약과 다를 때 옵셔널 체이닝으로 조용히 넘기지 않는다. 화면은 빈 값으로 그려지고, 경계면 버그는 데모까지 살아남는다. `api-ai-architect`에게 즉시 보고하고 계약대로 고치게 한다.

`?.`는 계약상 **실제로 없을 수 있는 필드**에만 쓴다 — 202 시점의 `aiAssessment`, 미확정 상태의 `decidedBy`, 규칙 finding의 `rationale`.
