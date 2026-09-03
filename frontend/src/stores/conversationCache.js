/*
 * 세션별 대화 본문 캐시.
 *
 * 키는 세션 id다. ChatView 안에 두면 화면을 벗어나는 순간(관리자 콘솔로 갔다 오는
 * 것만으로) 사라져서, 사이드바 목록에는 세션이 있는데 눌러도 빈 화면이 떴다.
 * 모듈에 두면 화면이 다시 만들어져도 살아 있다.
 *
 * Pinia 스토어에 넣지 않은 이유는 판정 응답 덩어리라 반응형으로 감쌀 이유가 없고,
 * 새로고침에 살아남을 필요도 없기 때문이다(영속화는 범위 밖, 0.3).
 */
export const conversationCache = new Map()

/**
 * 캐시에 있는 모든 대화에서 이 검사 건의 턴을 찾는다.
 *
 * 담당자 확정 결과는 종(알림)이 물어 오는데, 그 결과를 받아 적어야 할 대화는 지금
 * 화면에 떠 있는 것이 아닐 수 있다 — 콘솔에 갔다 오는 사이에 다른 세션을 보고 있을
 * 수 있고, 계정을 바꿔 있을 수도 있다. 그래서 화면이 아니라 캐시를 뒤진다.
 *
 * 화면에 떠 있는 대화의 배열은 캐시에 든 것과 같은 객체다(ChatView의 persist). 여기서
 * 고친 것이 곧바로 화면에 반영되는 이유다.
 *
 * @returns {{ sessionId: string, entry: object } | null}
 */
export function findByInspection(inspectionId) {
  if (inspectionId == null) return null
  for (const [sessionId, entries] of conversationCache) {
    for (const entry of entries ?? []) {
      if (entry?.verdict?.inspectionId === inspectionId) return { sessionId, entry }
    }
  }
  return null
}

/** 같은 이유로 messageId로도 찾는다 — 마스킹 해제 확정이 이 길로 온다 */
export function findByMessage(messageId) {
  if (messageId == null) return null
  for (const [sessionId, entries] of conversationCache) {
    for (const entry of entries ?? []) {
      if (entry?.verdict?.messageId === messageId) return { sessionId, entry }
    }
  }
  return null
}
