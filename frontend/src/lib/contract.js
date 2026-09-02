/*
 * 계약 위반을 조용히 넘기지 않기 위한 장치.
 *
 * 응답 필드가 계약서(`_workspace/01_api-ai-architect_contract-freeze.md`)와 다를 때
 * 옵셔널 체이닝으로 덮으면 화면이 빈 값으로 그려지고 경계면 버그가 데모까지 살아남는다.
 * 여기서 한 번 크게 떠들고 `_workspace/02_frontend-dev_ui-notes.md`에 기록한 뒤
 * `api-ai-architect`에게 보고한다.
 */
const reported = new Set()

export function contractViolation(where, message, payload) {
  const key = `${where}::${message}`
  if (reported.has(key)) return
  reported.add(key)
  console.error(`[계약 위반] ${where} — ${message}`, payload ?? '')
}

/** 조건이 거짓이면 콘솔에 계약 위반을 남기고 false를 반환한다. */
export function expectField(cond, where, message, payload) {
  if (cond) return true
  contractViolation(where, message, payload)
  return false
}

/** 에러 봉투(400/404/409)에서 사람이 읽을 문구를 뽑는다. 403 판정 객체는 여기로 오지 않는다. */
export function errorText(err, fallback = '요청을 처리하지 못했습니다.') {
  const data = err?.response?.data
  if (data && typeof data === 'object' && data.code) {
    return `${data.message ?? fallback} (${data.code})`
  }
  if (err?.response) return `${fallback} (HTTP ${err.response.status})`
  if (err?.message) return `${fallback} (${err.message})`
  return fallback
}
