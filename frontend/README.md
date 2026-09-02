# frontend — 사내 AI 게이트웨이 화면

Vue 3 + Vite. 화면 2개(SCR-01 직원 챗 `/chat`, SCR-02 관리자 감사 콘솔 `/admin/audit`)입니다.

실행 방법·환경변수·데모 케이스는 **저장소 루트의 [`README.md`](../README.md)** 를 보세요.
구현 결정과 API 응답 매핑표는 [`_workspace/02_frontend-dev_ui-notes.md`](../_workspace/02_frontend-dev_ui-notes.md)에 있습니다.

```bash
npm install
npm run dev            # 백엔드(localhost:8080)에 붙는다
npm run dev:fixtures   # 백엔드 없이 화면만 — dev 서버가 /api/v1을 직접 응답
npm run build
npm run preview
```

## 손대기 전에 알아야 할 것

- **색상은 `src/style.css`의 토큰 8종에서만 온다.** 컴포넌트에 hex를 직접 쓰면 판정 상태별 색이 어긋납니다.
- **화면 용어 매핑은 `src/lib/terms.js` 한 곳이다.** 내부 enum(`ALLOW`/`MASK`/`BLOCK`)을 화면에 그대로 노출하지 않습니다.
- **403은 에러가 아니다.** `src/api/client.js`의 인터셉터가 차단 판정을 정상 경로로 넘깁니다. 이걸 지우면 차단이 "통신 오류"로 보입니다.
- **응답이 계약과 다르면 옵셔널 체이닝으로 덮지 말 것.** `src/lib/contract.js`의 `expectField`로 콘솔에 남기고 보고합니다.
- `dev/fixture-server.js`는 개발 전용입니다. 애플리케이션 코드가 import하지 않고 프로덕션 번들에도 들어가지 않습니다.
