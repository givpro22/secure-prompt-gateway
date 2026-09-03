import client from './client'

/*
 * 마스킹 해제 검토 (D25).
 *
 * 규칙은 명단의 문자열만 본다. 고객과 이름이 같은 직원을 쓰면 그 이름도 [고객명]으로
 * 가려지는데, 문맥을 보지 못하는 규칙으로는 둘을 가를 방법이 없다. 사람이 원문과
 * 마스킹본을 비교해 정한다.
 */

/** POST /messages/{id}/unmask-request — 직원이 사유를 적어 올린다. 자기 건만 */
export async function requestUnmask(messageId, reason) {
  const { data } = await client.post(`/messages/${messageId}/unmask-request`, { reason })
  return data
}

/**
 * GET /messages/{id}/unmask-request — 요청자가 자기 건의 처리 상태를 본다.
 * 목록은 담당자 전용이라 요청자가 결과를 알 길이 이것뿐이다. 원문은 실리지 않는다.
 */
export async function fetchMyUnmaskRequest(messageId) {
  const { data } = await client.get(`/messages/${messageId}/unmask-request`)
  return data
}

/**
 * GET /unmask-requests — 담당자 목록.
 * **원문이 실려 오는 유일한 응답이다.** 요청이 붙은 건에 한해서만 열린다.
 */
export async function fetchUnmaskRequests(params) {
  const { data } = await client.get('/unmask-requests', { params })
  return data
}

/** POST /unmask-requests/{id}/decision — 담당자가 해제(true) 또는 유지(false)를 확정한다 */
export async function decideUnmask(requestId, approve, note) {
  const body = { approve }
  if (note) body.note = note
  const { data } = await client.post(`/unmask-requests/${requestId}/decision`, body)
  return data
}
