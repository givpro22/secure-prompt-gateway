import { defineStore } from 'pinia'

/*
 * 계정별 알림함.
 *
 * **저장하지 않는다.** 새로고침하면 사라진다 — 검사 전후의 본문 조각을 브라우저
 * 저장소에 남기는 것은 이 서비스가 막으려는 것과 같은 종류의 위험이다 (0.3).
 *
 * 계정마다 따로 드는 이유는 대화 상태와 같다. 로그인이 없어 계정을 갈아 끼우며
 * 시연하는데 한 벌만 두면 영업팀 알림이 개발팀 화면에 그대로 남는다.
 *
 * 담당자에게 가는 해제 요청 알림만 성질이 다르다. 다른 계정이 만든 일이라 화면
 * 안에서 건네주는 대신 **서버에 물어서 만든다** (`GET /unmask-requests`). 계정을 옮겨
 * 다니며 시연하는 자리에서 화면끼리 몰래 주고받으면, 정말 도는 것인지 흉내인지
 * 구분할 수 없다.
 */

const MAX_ITEMS = 40

let seq = 0

export const useNotificationStore = defineStore('notifications', {
  state: () => ({
    /** { [userId]: { items: [], seenIds: [], watching: [] } } */
    byUser: {},
    userId: 0,
  }),
  getters: {
    items: (state) => state.byUser[state.userId]?.items ?? [],
    unread() {
      return this.items.filter((n) => !n.read).length
    },
  },
  actions: {
    useAccount(userId) {
      if (!this.byUser[userId]) this.byUser[userId] = { items: [], seenIds: [], watching: [] }
      this.userId = userId
    },

    /**
     * 결과를 기다리는 해제 요청. 목록 API는 담당자 전용이라 요청자는 자기 건을
     * 하나씩 물어야 한다. 확정이 나면 지운다.
     */
    watch(userId, messageId, title) {
      if (!this.byUser[userId]) this.byUser[userId] = { items: [], seenIds: [], watching: [] }
      const box = this.byUser[userId]
      if (!box.watching.some((w) => w.messageId === messageId)) {
        box.watching.push({ messageId, title })
      }
    },

    unwatch(userId, messageId) {
      const box = this.byUser[userId]
      if (box) box.watching = box.watching.filter((w) => w.messageId !== messageId)
    },

    push(userId, item) {
      if (!this.byUser[userId]) this.byUser[userId] = { items: [], seenIds: [], watching: [] }
      const box = this.byUser[userId]
      box.items.unshift({ id: `n${++seq}`, at: new Date().toISOString(), read: false, ...item })
      if (box.items.length > MAX_ITEMS) box.items.length = MAX_ITEMS
    },

    /**
     * 서버에서 온 것을 한 번만 알린다. 폴링이 같은 행을 계속 물어오므로
     * 키를 기억해 두지 않으면 6초마다 같은 알림이 쌓인다.
     */
    pushOnce(userId, key, item) {
      if (!this.byUser[userId]) this.byUser[userId] = { items: [], seenIds: [], watching: [] }
      const box = this.byUser[userId]
      if (box.seenIds.includes(key)) return false
      box.seenIds.push(key)
      this.push(userId, item)
      return true
    },

    /**
     * 알림 하나를 지운다.
     *
     * 아직 열려 있는 건(`pending`)은 못 지운다. 확정을 기다리는 알림을 치우면 해야 할
     * 일이 목록에서만 없어지고 실제로는 남는다 — 대화 목록의 검토 대기와 같은 이유다.
     * 지워지는 것은 내 알림함뿐이고 서버의 검사·요청 기록은 그대로다.
     */
    remove(id) {
      const box = this.byUser[this.userId]
      if (!box) return false
      const n = box.items.find((x) => x.id === id)
      if (!n || n.pending) return false
      box.items = box.items.filter((x) => x.id !== id)
      return true
    },

    /**
     * 서버에서 아직 열려 있는 것만 남기고 나머지를 닫힌 상태로 바꾼다.
     * 담당자가 확정하면 목록에서 빠지므로, 폴링이 그 사실을 알림에 옮긴다.
     */
    settle(userId, aliveKeys) {
      const box = this.byUser[userId]
      if (!box) return
      for (const n of box.items) {
        if (n.pending && n.watchKey && !aliveKeys.includes(n.watchKey)) n.pending = false
      }
    },

    markAllRead() {
      const box = this.byUser[this.userId]
      if (box) box.items.forEach((n) => (n.read = true))
    },

    /** 다 지우기 — 열려 있는 건은 남는다 */
    clear() {
      const box = this.byUser[this.userId]
      if (box) box.items = box.items.filter((n) => n.pending)
    },
  },
})

/** 판정 하나를 알림 한 줄로 옮긴다. 본문이 아니라 무엇이 걸렸는지만 남긴다 */
export function notificationFromVerdict(verdict) {
  const matches = verdict.ruleResult?.matches ?? []
  const codes = [...new Set(matches.map((m) => m.code))]

  if (verdict.decision === 'BLOCK') {
    return {
      tone: 'block',
      kind: '차단',
      title: '전송이 차단되었습니다',
      body: codes.length > 0 ? `${codes.join(', ')}에 걸렸습니다.` : '정책 위반으로 전송하지 않았습니다.',
    }
  }
  if (verdict.decision === 'PENDING') {
    return {
      tone: 'pending',
      kind: '검토 대기',
      title: '보안 담당자 확인을 기다립니다',
      body: codes.length > 0 ? `${codes.join(', ')} 판단이 필요합니다.` : '판단이 필요한 표현이 있습니다.',
    }
  }
  if (verdict.decision === 'MASK') {
    // 라벨은 전송본에서 뽑는다. 규칙 응답에 라벨 필드가 없고, 실제로 나간 문장이
    // 무엇으로 바뀌었는지가 알림에서 알고 싶은 것이다.
    const labels = [...new Set((verdict.submittedText ?? '').match(/\[[^\]]+\]/g) ?? [])]
    return {
      tone: 'mask',
      kind: '마스킹',
      title: `${matches.length}건을 가리고 전송했습니다`,
      body: labels.length > 0 ? `${labels.join(' ')}(으)로 치환되었습니다.` : codes.join(', '),
    }
  }
  return {
    tone: 'allow',
    kind: '허용',
    title: '그대로 전송했습니다',
    body: '걸린 규칙이 없습니다.',
  }
}
