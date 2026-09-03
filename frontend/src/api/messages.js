import client from './client'

/*
 * POST /messages — 판정 결과가 상태 코드로 갈린다 (200 ALLOW/MASK · 202 REVIEW · 403 BLOCK).
 * 호출자가 `res.status`를 봐야 하므로 응답 본문이 아니라 응답 객체를 그대로 넘긴다.
 *
 * 403은 client.js의 인터셉터가 정상 경로로 돌려놓는다 (C2).
 */
export function submitMessage(text) {
  return client.post('/messages', { text })
}

/*
 * POST /messages/{id}/response — 출력 검사 (UC-08).
 *
 * 모델이 돌려준 답변을 같은 정책으로 다시 본다. 입력과 같은 파이프라인이라 상태
 * 코드도 같다 — 200 ALLOW·MASK / 202 REVIEW / 403 BLOCK. 403도 인터셉터가 정상
 * 경로로 돌려놓으므로 호출자가 `res.status`를 본다.
 */
export function submitResponse(messageId, text) {
  return client.post(`/messages/${messageId}/response`, { text })
}
