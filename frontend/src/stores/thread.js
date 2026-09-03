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
    /** 사이드바에서 고른 문장을 입력창으로 옮기는 통로. 전송은 하지 않는다 */
    pendingDraft: null,
    /**
     * 열어볼 데모 대화. ChatView가 지금 대화를 비우고 이 문장들을 차례로 태운다.
     *
     * 판정 객체를 심지 않고 실제로 태우는 이유는 화면과 감사 기록이 어긋나지 않게
     * 하기 위함이다. 대신 **누를 때마다 이어붙지 않고 갈아끼운다** — 대화는 서로
     * 다른 세션이고, 이어붙이면 앞 대화의 판정이 뒤 대화의 맥락처럼 보인다.
     */
    pendingDemo: null,
    /**
     * 전송하지 않고 남아 있는 입력. 사이드바 "작성 중"에 뜬다.
     * 새로고침하면 사라진다 — 영속화는 범위 밖이고(0.3), 검사 전 원문을 브라우저
     * 저장소에 남기는 것은 이 서비스가 막으려는 것과 같은 종류의 위험이다.
     */
    writing: null,
  }),
  actions: {
    push(item) {
      this.items.unshift(item)
    },
    updateDecision(key, decision) {
      const found = this.items.find((i) => i.key === key)
      if (found) found.decision = decision
    },
    requestDraft(text) {
      this.pendingDraft = { text, at: Date.now() }
    },
    openDemo(prompts) {
      this.pendingDemo = { prompts, at: Date.now() }
    },
    setWriting(text) {
      const trimmed = (text ?? '').trim()
      this.writing = trimmed.length === 0 ? null : { text: trimmed }
    },
    clear() {
      this.items = []
      this.writing = null
      this.clearedAt += 1
    },
  },
})
