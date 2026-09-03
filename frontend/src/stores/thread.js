import { defineStore } from 'pinia'

/*
 * 사이드바가 읽는 대화 요약.
 *
 * **저장하지 않는다.** 세션·대화 영속화는 범위 밖이라(기획서 0.3) 새로고침하면 사라진다.
 * 시안의 "오늘 / 어제" 이력은 그래서 만들 수 없다 — 없는 데이터를 그린 목록은 시연 중에
 * 눌리고, 눌러도 아무것도 안 열린다.
 *
 * 대신 이번 세션에 실제로 보낸 것만 담는다. 화면에 이미 있는 것이라 클릭하면 그 자리로 간다.
 */
export const useThreadStore = defineStore('thread', {
  state: () => ({
    items: [],
    /** ChatView가 감시하는 초기화 신호. 증가하면 대화를 비운다 */
    clearedAt: 0,
    /**
     * 사이드바에서 고른 문장을 입력창으로 옮기는 통로. ChatView가 감시한다.
     * `send`가 true면 그대로 전송까지 한다 — 완료된 대화를 여는 것처럼 보이게 하되
     * 판정은 실제로 규칙 엔진이 낸다. 가짜 판정 객체를 화면에 심지 않는다.
     */
    pendingDraft: null,
  }),
  actions: {
    push(item) {
      this.items.unshift(item)
    },
    updateDecision(key, decision) {
      const found = this.items.find((i) => i.key === key)
      if (found) found.decision = decision
    },
    requestDraft(text, { send = false } = {}) {
      this.pendingDraft = { text, send, at: Date.now() }
    },
    clear() {
      this.items = []
      this.clearedAt += 1
    },
  },
})
