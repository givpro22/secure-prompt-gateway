/*
 * 판정 하나를 여러 곳에서 세는 바람에 화면이 서로 다른 말을 했다. 사이드바 점은
 * "차단"인데 위 집계는 "허용 1"이고, 답변 배지는 아직 "검토 대기"인 식이었다.
 * 계산을 여기 하나로 모은다.
 *
 * 한 턴에는 판정이 둘이다 — 보낸 프롬프트와 받은 답변. 그리고 각각 담당자 확정으로
 * 뒤집힐 수 있다. 턴을 대표하는 값은 그 넷 중 가장 심한 것이다.
 */

/** 판정 심각도. 대화를 대표하는 값은 가장 심한 것이다 */
export const SEVERITY = {
  ALLOW: 0, ALLOWED: 0,
  MASK: 1, MASKED: 1,
  PENDING: 2, PENDING_REVIEW: 2,
  BLOCK: 3, BLOCKED: 3,
}

/** 검사 기록의 message.status를 판정 값으로 되돌린다 */
export const FROM_STATUS = { ALLOWED: 'ALLOW', MASKED: 'MASK', BLOCKED: 'BLOCK', PENDING_REVIEW: 'PENDING' }

/** 둘 중 더 심한 쪽 */
export function worse(a, b) {
  if (a == null) return b
  if (b == null) return a
  return (SEVERITY[b] ?? 0) > (SEVERITY[a] ?? 0) ? b : a
}

/** 담당자가 확정했으면 그 결과, 아니면 보낼 때의 판정 */
function promptDecision(entry) {
  const insp = entry?.inspection
  if (insp && insp.finalDecision !== 'PENDING') {
    return FROM_STATUS[insp.status] ?? entry.verdict?.decision
  }
  return entry?.verdict?.decision
}

/** 받은 답변의 판정. 담당자가 확정했으면 그 결과. 답변이 없으면 null */
function answerDecision(entry) {
  const a = entry?.answer
  if (!a) return null
  const settled = a.settled?.status
  return settled ? (FROM_STATUS[settled] ?? a.decision) : a.decision
}

/** 이 턴을 대표하는 판정 */
export function entryDecision(entry) {
  return worse(promptDecision(entry), answerDecision(entry))
}
