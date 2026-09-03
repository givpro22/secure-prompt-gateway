import { defineStore } from 'pinia'

/*
 * 사이드바가 읽는 지금 대화.
 *
 * **저장하지 않는다.** 세션·대화 영속화는 범위 밖이라(기획서 0.3) 새로고침하면 사라진다.
 * 시안의 "오늘 / 어제" 이력을 만들 수 없는 이유이고, 데모 항목에 (demo)를 붙인 이유다.
 *
 * 항목이 하나인 것은 의도다. 턴마다 줄이 늘면 사이드바가 대화 목록이 아니라 발화
 * 목록이 되어, 옆의 (demo) 대화들과 단위가 어긋난다. 지금 열려 있는 대화 하나만 둔다.
 *
 * 계정마다 따로 든다(`byUser`). 로그인이 없어 계정을 갈아 끼우며 시연하는데(0.3),
 * 한 벌만 두면 영업팀으로 바꾼 순간 개발팀이 하던 말이 그대로 남아 누가 무엇을
 * 물었는지가 섞인다. 부서에 따라 판정이 갈리는 것이 이 데모의 핵심이라 더 그렇다.
 */

/** 판정 심각도. 대화 전체를 대표하는 값은 가장 심한 것이다 */
const SEVERITY = { ALLOW: 0, ALLOWED: 0, MASK: 1, MASKED: 1, PENDING: 2, PENDING_REVIEW: 2, BLOCK: 3, BLOCKED: 3 }

/** 아직 자리가 없는 계정을 읽을 때 돌려주는 빈 칸 */
const EMPTY = Object.freeze({ current: null, writing: null, viewing: 'own' })

const newSlot = () => ({
  /** 지금 열려 있는 대화 하나. { title, decision, turns } */
  current: null,
  /**
   * 전송하지 않고 남아 있는 입력. 사이드바 "작성 중"에 뜬다.
   * 새로고침하면 사라진다 — 검사 전 원문을 브라우저 저장소에 남기는 것은 이 서비스가
   * 막으려는 것과 같은 종류의 위험이다.
   */
  writing: null,
  /** 화면에 뜬 것. 'own'이면 내가 입력한 대화, 아니면 열어 본 데모의 키 */
  viewing: 'own',
})

export const useThreadStore = defineStore('thread', {
  state: () => ({
    /** 계정별 대화 상태. { [userId]: slot } */
    byUser: {},
    /** 지금 보고 있는 계정 */
    userId: 0,
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
    /** 내 대화로 돌아가라는 신호. 증가하면 ChatView가 치워 둔 대화를 되돌린다 */
    resumeAt: 0,
  }),
  getters: {
    slot: (state) => state.byUser[state.userId] ?? EMPTY,
    current() {
      return this.slot.current
    },
    writing() {
      return this.slot.writing
    },
    viewing() {
      return this.slot.viewing
    },
  },
  actions: {
    /** 계정을 갈아 끼운다. 처음 보는 계정이면 빈 자리를 만든다 */
    useAccount(userId) {
      if (!this.byUser[userId]) this.byUser[userId] = newSlot()
      this.userId = userId
    },
    /** 대화의 첫 발화가 제목이 된다. 이후 턴은 심각도와 턴 수만 올린다 */
    addTurn(text, decision) {
      const slot = this.byUser[this.userId]
      if (!slot) return
      const title = text.length > 24 ? `${text.slice(0, 24)}…` : text
      if (!slot.current) {
        slot.current = { title, decision, turns: 1 }
        return
      }
      slot.current.turns += 1
      if ((SEVERITY[decision] ?? 0) > (SEVERITY[slot.current.decision] ?? 0)) {
        slot.current.decision = decision
      }
    },
    /** 담당자 확정 등으로 판정이 바뀌었을 때 */
    raiseDecision(decision) {
      const cur = this.byUser[this.userId]?.current
      if (!cur) return
      if ((SEVERITY[decision] ?? 0) > (SEVERITY[cur.decision] ?? 0)) cur.decision = decision
    },
    setViewing(value) {
      const slot = this.byUser[this.userId]
      if (slot) slot.viewing = value
    },
    /** 내 대화로 돌아온다. 데모를 보다가 입력해도 이 경로를 탄다 */
    resumeOwn() {
      this.resumeAt += 1
    },
    requestDraft(text) {
      this.pendingDraft = { text, at: Date.now() }
    },
    openDemo(key, prompts, answers = []) {
      this.setViewing(key)
      this.pendingDemo = { key, prompts, answers, at: Date.now() }
    },
    setWriting(text) {
      const slot = this.byUser[this.userId]
      if (!slot) return
      const trimmed = (text ?? '').trim()
      slot.writing = trimmed.length === 0 ? null : { text: trimmed }
    },
    clear() {
      const slot = this.byUser[this.userId]
      if (slot) {
        slot.current = null
        slot.writing = null
        slot.viewing = 'own'
      }
      this.clearedAt += 1
    },
  },
})
