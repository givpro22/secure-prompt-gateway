import { defineStore } from 'pinia'

/*
 * 사이드바가 읽는 대화 목록.
 *
 * **저장하지 않는다.** 세션·대화 영속화는 범위 밖이라(기획서 0.3) 새로고침하면 사라진다.
 * 데모 항목에 (demo)를 붙인 이유이기도 하다.
 *
 * 처음에는 열려 있는 대화 하나만 들었다. 사이드바가 발화 목록이 되는 것을 피하려던
 * 것인데, 새 대화나 데모를 열 때마다 앞의 것이 밀려나서 콘솔에 확정하러 갔다 오면
 * 방금 시연한 세션이 사라졌다. 이제 세션을 쌓고 눌러서 오간다 — 줄이 느는 단위는
 * 여전히 발화가 아니라 대화다.
 *
 * 계정마다 따로 든다(`byUser`). 로그인이 없어 계정을 갈아 끼우며 시연하는데(0.3),
 * 한 벌만 두면 영업팀으로 바꾼 순간 개발팀이 하던 말이 그대로 남아 누가 무엇을
 * 물었는지가 섞인다. 부서에 따라 판정이 갈리는 것이 이 데모의 핵심이라 더 그렇다.
 */

/** 판정 심각도. 대화 전체를 대표하는 값은 가장 심한 것이다 */
const SEVERITY = { ALLOW: 0, ALLOWED: 0, MASK: 1, MASKED: 1, PENDING: 2, PENDING_REVIEW: 2, BLOCK: 3, BLOCKED: 3 }

/** 아직 자리가 없는 계정을 읽을 때 돌려주는 빈 칸 */
const EMPTY = Object.freeze({ sessions: [], activeId: null, writing: null, hiddenDemos: [] })

const newSlot = () => ({
  /** 이 계정이 이번에 연 대화들. 최근 것이 앞이다 */
  sessions: [],
  /** 화면에 떠 있는 대화. 없으면 빈 화면(인사말) */
  activeId: null,
  /**
   * 전송하지 않고 남아 있는 입력. 사이드바 "작성 중"에 뜬다.
   * 새로고침하면 사라진다 — 검사 전 원문을 브라우저 저장소에 남기는 것은 이 서비스가
   * 막으려는 것과 같은 종류의 위험이다.
   */
  writing: null,
  /**
   * 목록에서 지운 데모의 키. 데모 카탈로그는 정적이라 지울 대상이 없어서, 화면에서
   * 감추는 것으로 대신한다.
   */
  hiddenDemos: [],
})

let seq = 0
const nextId = () => `s${++seq}`

export const useThreadStore = defineStore('thread', {
  state: () => ({
    /** 계정별 대화 상태. { [userId]: slot } */
    byUser: {},
    /** 지금 보고 있는 계정 */
    userId: 0,
    /** ChatView가 감시하는 전환 신호. 증가하면 활성 세션의 대화를 화면에 올린다 */
    switchAt: 0,
    /** 사이드바에서 고른 문장을 입력창으로 옮기는 통로. 전송은 하지 않는다 */
    pendingDraft: null,
    /**
     * 재생할 데모 대화. ChatView가 이 문장들을 차례로 태워 새 세션을 채운다.
     *
     * 판정 객체를 심지 않고 실제로 태우는 이유는 화면과 감사 기록이 어긋나지 않게
     * 하기 위함이다. 이미 열어 본 데모는 다시 태우지 않고 그 세션으로 돌아간다.
     */
    pendingDemo: null,
  }),
  getters: {
    slot: (state) => state.byUser[state.userId] ?? EMPTY,
    sessions() {
      return this.slot.sessions
    },
    activeId() {
      return this.slot.activeId
    },
    active() {
      return this.slot.sessions.find((s) => s.id === this.slot.activeId) ?? null
    },
    writing() {
      return this.slot.writing
    },
    hiddenDemos() {
      return this.slot.hiddenDemos
    },
  },
  actions: {
    /** 계정을 갈아 끼운다. 처음 보는 계정이면 빈 자리를 만든다 */
    useAccount(userId) {
      if (!this.byUser[userId]) this.byUser[userId] = newSlot()
      this.userId = userId
      this.switchAt += 1
    },

    /** 새 대화를 만들어 활성으로. 반환값이 세션 id다 */
    open({ kind = 'own', demoKey = null, title = '' } = {}) {
      const slot = this.byUser[this.userId]
      if (!slot) return null
      const s = { id: nextId(), title, decision: null, turns: 0, kind, demoKey }
      slot.sessions.unshift(s)
      slot.activeId = s.id
      this.switchAt += 1
      return s.id
    },

    /** 목록에서 눌러 오간다. 이미 있는 대화는 다시 태우지 않는다 */
    activate(id) {
      const slot = this.byUser[this.userId]
      if (!slot || slot.activeId === id) return
      slot.activeId = id
      this.switchAt += 1
    },

    /** 대화의 첫 발화가 제목이 된다. 이후 턴은 심각도와 턴 수만 올린다 */
    addTurn(text, decision) {
      const slot = this.byUser[this.userId]
      if (!slot) return
      if (!slot.activeId) this.open()
      const s = slot.sessions.find((x) => x.id === slot.activeId)
      if (!s) return
      s.turns += 1
      if (!s.title) s.title = text.length > 24 ? `${text.slice(0, 24)}…` : text
      if (s.decision === null || (SEVERITY[decision] ?? 0) > (SEVERITY[s.decision] ?? 0)) {
        s.decision = decision
      }
    },

    /** 담당자 확정 등으로 판정이 바뀌었을 때 */
    raiseDecision(decision) {
      const s = this.active
      if (!s) return
      if (s.decision === null || (SEVERITY[decision] ?? 0) > (SEVERITY[s.decision] ?? 0)) {
        s.decision = decision
      }
    },

    /**
     * 데모를 연다. 이미 열어 본 것이면 그 세션으로 돌아가고, 처음이면 새 세션을
     * 만들어 문장을 태운다 — 누를 때마다 다시 태우면 감사 기록에 같은 건이 쌓인다.
     */
    openDemo(key, prompts, answers = [], title = '') {
      const slot = this.byUser[this.userId]
      if (!slot) return
      const seen = slot.sessions.find((s) => s.demoKey === key)
      if (seen) {
        this.activate(seen.id)
        return
      }
      this.open({ kind: 'demo', demoKey: key, title })
      this.pendingDemo = { key, prompts, answers, at: Date.now() }
    },

    /**
     * 작성 중 항목을 연다. **새 대화로 연다** — 지금 대화 뒤에 붙이면 보내지도 않은
     * 문장이 앞 판정들 아래에 끼어들어 한 흐름처럼 보인다.
     */
    openDraft(key, text) {
      this.open()
      this.pendingDraft = { key, text, at: Date.now() }
    },

    setWriting(text) {
      const slot = this.byUser[this.userId]
      if (!slot) return
      const trimmed = (text ?? '').trim()
      slot.writing = trimmed.length === 0 ? null : { text: trimmed }
    },

    /**
     * 대화를 목록에서 지운다.
     *
     * **감사 기록은 지워지지 않는다.** 여기서 없어지는 것은 내 화면의 목록뿐이고,
     * 서버의 검사 기록은 그대로 남아 관리자 콘솔에서 보인다 — 직원이 채팅을 지워
     * 흔적을 없앨 수 있으면 감사 시스템이 아니다.
     *
     * 검토 대기 건은 못 지운다. 담당자가 아직 확정하지 않은 대화를 작성자가 목록에서
     * 치우면, 확정하라고 알림을 받은 사람과 그 대화를 아는 사람이 어긋난다.
     */
    remove(id) {
      const slot = this.byUser[this.userId]
      if (!slot) return false
      const s = slot.sessions.find((x) => x.id === id)
      if (!s || s.decision === 'PENDING') return false
      slot.sessions = slot.sessions.filter((x) => x.id !== id)
      if (s.demoKey) slot.hiddenDemos.push(s.demoKey)
      if (slot.activeId === id) slot.activeId = null
      this.switchAt += 1
      return true
    },

    /** 아직 열어 보지 않은 데모를 목록에서 감춘다 */
    hideDemo(key) {
      const slot = this.byUser[this.userId]
      if (!slot || slot.hiddenDemos.includes(key)) return
      slot.hiddenDemos.push(key)
    },

    /** 새 대화 — 빈 화면으로. 지난 대화는 목록에 남는다 */
    clear() {
      const slot = this.byUser[this.userId]
      if (!slot) return
      slot.activeId = null
      slot.writing = null
      this.switchAt += 1
    },
  },
})
