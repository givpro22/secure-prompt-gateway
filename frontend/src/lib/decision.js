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

/** 이 턴을 대표하는 판정. 사이드바 점과 삭제 가능 여부가 쓴다 */
export function entryDecision(entry) {
  return worse(promptDecision(entry), answerDecision(entry))
}

/**
 * 이 턴에서 일어난 **검사들**의 판정. 하나이거나 둘이다.
 *
 * 한 발화에 검사가 둘일 수 있다 — 보낸 프롬프트(phase=INPUT)와 받은 답변(phase=OUTPUT).
 * 집계를 턴당 하나로 세면 둘 중 심한 것만 남아 나머지가 사라진다. 마스킹해서 보냈는데
 * 답변이 검토로 갔으면 "마스킹 0 · 검토 대기 1"이 되어, 방금 화면에서 본 마스킹 3건이
 * 숫자에서 없어진다.
 *
 * 검사 하나가 한 번씩 세어진다. 합이 턴 수보다 클 수 있고, 그것이 사실이다.
 */
export function entryVerdicts(entry) {
  const out = []
  const prompt = promptDecision(entry)
  if (prompt) out.push(prompt)
  const answer = answerDecision(entry)
  if (answer) out.push(answer)
  return out
}

/*
 * 턴 하나의 "지금 상태" 한 줄.
 *
 * 카드에는 프롬프트 판정·답변 판정·해제 요청이 따로 떠 있어서, 여러 개가 겹치면
 * 지금 무엇을 기다리는 중인지 읽는 데 시간이 걸린다. 한 줄로 답한다.
 *
 * 고르는 순서가 곧 규칙이다.
 *   1. **아직 사람이 정하지 않은 것**이 가장 먼저다. 기다리는 중이라는 사실이
 *      이미 끝난 판정보다 중요하다.
 *   2. 그다음은 사람이 확정한 결과. 규칙이 낸 판정보다 나중에 온 사실이다.
 *   3. 마지막이 규칙 판정.
 *
 * tone은 화면의 판정색 토큰과 같다 — green·amber·red·purple.
 */
export function turnStatus(entry) {
  const decision = entry?.verdict?.decision
  const insp = entry?.inspection
  const promptSettled = insp && insp.finalDecision !== 'PENDING'
  const answer = entry?.answer
  const answerSettled = answer?.settled
  const unmask = entry?.unmask

  // ── 1. 기다리는 중 ────────────────────────────────────────────────
  if (decision === 'PENDING' && !promptSettled) {
    return { tone: 'purple', label: '검토 대기', detail: '보안 담당자의 확정을 기다립니다' }
  }
  if (answer && answer.decision === 'PENDING' && !answerSettled) {
    return { tone: 'purple', label: '답변 검토 대기', detail: '받은 답변에 원문 유출이 의심됩니다' }
  }
  if (unmask && unmask.status === 'PENDING') {
    return { tone: 'purple', label: '해제 검토 대기', detail: '마스킹을 풀지 담당자가 확정합니다' }
  }

  // ── 2. 사람이 확정한 것 ───────────────────────────────────────────
  if (promptSettled && insp.status === 'BLOCKED') {
    return { tone: 'red', label: '차단 확정', detail: '담당자가 위반으로 확정했습니다' }
  }
  if (answerSettled && answerSettled.status === 'BLOCKED') {
    return { tone: 'red', label: '답변 차단 확정', detail: '담당자가 원문 유출로 확정했습니다' }
  }
  if (unmask && unmask.status === 'APPROVED') {
    return { tone: 'green', label: '해제 승인', detail: '가리지 않아도 되는 것으로 확정되었습니다' }
  }
  if (unmask && unmask.status === 'REJECTED') {
    return { tone: 'amber', label: '마스킹 유지', detail: '그대로 가려 두는 것으로 확정되었습니다' }
  }
  if (promptSettled) {
    return { tone: 'green', label: '검토 통과', detail: '담당자가 전송을 허용했습니다' }
  }
  if (answerSettled) {
    return { tone: 'green', label: '답변 확인 완료', detail: '담당자가 문제없음으로 확정했습니다' }
  }

  // ── 3. 규칙 판정 ──────────────────────────────────────────────────
  if (decision === 'BLOCK') {
    return { tone: 'red', label: '차단', detail: '전송되지 않았습니다' }
  }
  if (decision === 'MASK') {
    return answer
      ? { tone: 'amber', label: '마스킹 후 전송', detail: '답변까지 검사를 통과했습니다' }
      : { tone: 'amber', label: '마스킹 후 전송', detail: '탐지된 항목을 라벨로 치환했습니다' }
  }
  if (decision === 'ALLOW') {
    return answer
      ? { tone: 'green', label: '검사 통과', detail: '보낸 것과 받은 것 모두 문제없습니다' }
      : { tone: 'green', label: '검사 통과', detail: '걸린 규칙이 없습니다' }
  }
  return null
}
