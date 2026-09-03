<script setup>
/*
 * 알림 종.
 *
 * 두 갈래를 한 곳에 모은다. 하나는 내 판정 결과 — 무엇이 가려졌고 무엇이 막혔는지.
 * 다른 하나는 보안 담당자에게만 오는 해제 요청이다.
 *
 * 해제 요청 알림은 **서버에 물어서 만든다**. 직원 화면이 담당자 화면에 직접 건네주는
 * 편이 짧지만, 계정을 옮겨 다니며 시연하는 자리에서 화면끼리 몰래 주고받으면 정말
 * 도는 것인지 흉내인지 구분할 수 없다.
 *
 * 그래서 양쪽이 각자 자기 것을 묻는다. 담당자는 목록을, 요청자는 자기 건 하나를.
 * 목록 API가 담당자 전용이라(D25) 요청자가 결과를 알 길이 그것뿐이기도 하다.
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { fetchInspection, fetchInspections } from '../api/inspections'
import { fetchMyUnmaskRequest, fetchUnmaskRequests } from '../api/unmask'
import { findByInspection, findByMessage } from '../stores/conversationCache'
import { useNotificationStore } from '../stores/notifications'
import { useSessionStore } from '../stores/session'
import { useThreadStore } from '../stores/thread'

const session = useSessionStore()
const notifications = useNotificationStore()
const thread = useThreadStore()

const open = ref(false)
const panel = ref(null)

const isAdmin = computed(() => session.currentUser?.role === 'SECURITY_ADMIN')
const items = computed(() => notifications.items)
const unread = computed(() => notifications.unread)

function toggle() {
  open.value = !open.value
  if (open.value) notifications.markAllRead()
}

function onOutside(event) {
  if (!open.value) return
  if (panel.value && !panel.value.contains(event.target)) open.value = false
}

/** 화면에 붙는 시각은 짧게. 어제 것을 볼 자리가 아니다 */
function clock(iso) {
  return iso?.slice(11, 16) ?? ''
}

// ── 담당자 폴링 ──────────────────────────────────────────────
const POLL_MS = 6000
let timer = null

/** 담당자 — 들어온 요청을 목록으로 받는다 */
async function pollIncoming() {
  const userId = session.currentUserId
  // 아직 열려 있는 건의 키. 목록에서 빠진 알림은 확정된 것이라 지울 수 있게 푼다.
  const alive = []
  // 답변 유출 의심 (UC-08 후단). 유출 검사가 검토 대기로 돌린 답변 검사가 여기 잡힌다.
  try {
    const page = await fetchInspections({ phase: 'OUTPUT', status: 'PENDING_REVIEW', size: 20 })
    for (const row of page.items ?? []) {
      alive.push(`leak:${row.inspectionId}`)
      notifications.pushOnce(userId, `leak:${row.inspectionId}`, {
        pending: true,
        watchKey: `leak:${row.inspectionId}`,
        tone: 'block',
        kind: '유출 의심',
        title: `${row.userName} 님이 받은 답변에 원문 유출 의심 — 확인 필요`,
        body: row.submittedText ? row.submittedText.slice(0, 80) : '',
        link: '/admin/audit',
      })
    }
  } catch {
    // 권한이 없거나 서버가 잠깐 없는 경우다.
  }
  try {
    const page = await fetchUnmaskRequests({ status: 'PENDING', size: 20 })
    for (const row of page.items ?? []) {
      alive.push(`unmask:${row.requestId}`)
      notifications.pushOnce(userId, `unmask:${row.requestId}`, {
        pending: true,
        watchKey: `unmask:${row.requestId}`,
        tone: 'request',
        kind: '해제 요청',
        title: `${row.requester.name} 님이 마스킹 검토를 요청했습니다`,
        body: row.reason,
        link: '/admin/audit',
      })
    }
  } catch {
    // 권한이 없거나 서버가 잠깐 없는 경우다. 종에 오류를 띄울 일은 아니다.
  }
  notifications.settle(userId, alive)
}

const UNMASK_OUTCOME = {
  APPROVED: { tone: 'allow', kind: '해제 승인', verb: '가리지 않아도 되는 것으로 확정되었습니다' },
  REJECTED: { tone: 'mask', kind: '마스킹 유지', verb: '그대로 가려 두는 것으로 확정되었습니다' },
}

/** 검토 대기 건의 확정. message.status 값이 그대로 온다 */
const REVIEW_OUTCOME = {
  ALLOWED: { tone: 'allow', kind: '검토 승인', verb: '전송이 허용되었습니다' },
  MASKED: { tone: 'mask', kind: '검토 승인', verb: '마스킹 후 전송으로 확정되었습니다' },
  BLOCKED: { tone: 'block', kind: '검토 거절', verb: '전송이 거절되었습니다' },
}

/*
 * 요청자 — 내가 올린 건이 어떻게 됐는지 하나씩 묻는다.
 *
 * 두 종류를 함께 본다. 마스킹 해제 요청(D25)과 검토 대기 건의 확정(D12)이다. 뒤엣것은
 * 예전에 화면의 "결과 새로고침" 버튼으로만 알 수 있었는데, 담당자가 거절해도 요청자
 * 쪽에는 아무 일도 일어나지 않아서 확정이 났는지조차 알 수 없었다. 확정은 남이 하는
 * 일이라 요청자 화면이 스스로 알아챌 방법이 폴링밖에 없다.
 *
 * 확정을 받으면 알림만 띄우고 끝내지 않는다. 그 대화의 판정 카드와 사이드바 점까지
 * 함께 고친다 — 알림에는 "거절됨"이라 떠 있는데 대화는 여전히 "검토 대기"면 어느
 * 쪽을 믿어야 할지 알 수 없다.
 */
async function pollOutcome() {
  const userId = session.currentUserId
  const watching = notifications.byUser[userId]?.watching ?? []
  for (const w of [...watching]) {
    try {
      if (w.kind === 'review') await settleReview(userId, w)
      else await settleUnmask(userId, w)
    } catch {
      // 아직 없거나 잠깐 실패한 것이다. 다음 차례에 다시 묻는다.
    }
  }
}

async function settleReview(userId, w) {
  const inspection = await fetchInspection(w.inspectionId)
  // 사람이 확정하기 전까지는 finalDecision이 PENDING이다. AI 분석이 끝난 것과 다르다.
  if (!inspection || inspection.finalDecision === 'PENDING') return

  const o = REVIEW_OUTCOME[inspection.status] ?? REVIEW_OUTCOME.BLOCKED
  notifications.pushOnce(userId, `review:${w.inspectionId}`, {
    tone: o.tone,
    kind: o.kind,
    title: `${w.title} — ${o.verb}`,
    body: '보안 담당자가 확정했습니다.',
    link: '/',
    linkLabel: '대화에서 보기',
  })

  // 대화에도 같은 결과를 적는다. 화면에 떠 있지 않은 세션이어도 캐시에서 찾아 고친다.
  const found = findByInspection(w.inspectionId)
  if (found) {
    found.entry.inspection = inspection
    found.entry.aiStatus = inspection.aiStatus
    found.entry.note = ''
    thread.settleDecision(found.sessionId, inspection.status)
  } else if (w.sessionId) {
    thread.settleDecision(w.sessionId, inspection.status)
  }

  notifications.settle(userId, [])
  notifications.unwatch(userId, w.key)
}

async function settleUnmask(userId, w) {
  const row = await fetchMyUnmaskRequest(w.messageId)
  if (row.status === 'PENDING') return

  const o = UNMASK_OUTCOME[row.status]
  notifications.pushOnce(userId, `outcome:${row.requestId}`, {
    tone: o.tone,
    kind: o.kind,
    title: `${w.title} — ${o.verb}`,
    body: row.decisionNote
      ? `${row.decidedBy}: ${row.decisionNote}`
      : `${row.decidedBy} 확정. 이미 전송된 본문은 그대로입니다.`,
    link: '/',
    linkLabel: '대화에서 보기',
  })

  // 판정 카드가 확정 결과를 그린다. 요청만 하고 결과를 모르는 화면이 남지 않게.
  const found = findByMessage(w.messageId)
  if (found) found.entry.unmask = row

  notifications.settle(userId, [])
  notifications.unwatch(userId, w.key)
}

async function poll() {
  if (isAdmin.value) await pollIncoming()
  else await pollOutcome()
}

function restartPolling() {
  clearInterval(timer)
  timer = null
  poll()
  timer = setInterval(poll, POLL_MS)
}

/*
 * 계정을 알림함에 묶는 것은 여기서 한다. 종이 헤더에 있어 감사 콘솔에도 뜨는데,
 * 챗 화면에서만 묶으면 콘솔에서 계정을 바꿨을 때 앞 계정의 알림함이 그대로 남는다.
 */
watch(
  () => session.currentUserId,
  (id) => {
    notifications.useAccount(id)
    restartPolling()
  },
  { immediate: true },
)
watch(isAdmin, restartPolling)

onMounted(() => {
  restartPolling()
  document.addEventListener('click', onOutside, true)
})
onUnmounted(() => {
  clearInterval(timer)
  document.removeEventListener('click', onOutside, true)
})
</script>

<template>
  <div ref="panel" class="bell-wrap">
    <button
      type="button"
      class="bell"
      :class="{ on: open }"
      :aria-label="`알림 ${unread}건`"
      @click="toggle"
    >
      <svg viewBox="0 0 20 20" width="22" height="22" aria-hidden="true">
        <path
          d="M10 2.4a4.6 4.6 0 0 0-4.6 4.6v2.7l-1.3 2.4a.6.6 0 0 0 .53.9h10.74a.6.6 0 0 0 .53-.9l-1.3-2.4V7A4.6 4.6 0 0 0 10 2.4Z"
          fill="none"
          stroke="currentColor"
          stroke-width="1.4"
          stroke-linejoin="round"
        />
        <path
          d="M8.2 15.2a1.9 1.9 0 0 0 3.6 0"
          fill="none"
          stroke="currentColor"
          stroke-width="1.4"
          stroke-linecap="round"
        />
      </svg>
      <span v-if="unread > 0" class="dot" aria-hidden="true">{{ unread > 9 ? '9+' : unread }}</span>
    </button>

    <div v-if="open" class="pop">
      <header class="pop-head">
        <span class="pop-title">알림</span>
        <span class="pop-who">{{ session.currentUser?.name }}</span>
        <!-- 열려 있는 건은 남는다. 문구로 미리 말해 두면 안 지워졌다고 오해하지 않는다 -->
        <button
          v-if="items.length > 0"
          type="button"
          class="pop-clear"
          title="검토가 끝난 알림만 지웁니다"
          @click="notifications.clear()"
        >
          읽은 알림 지우기
        </button>
      </header>

      <p v-if="items.length === 0" class="pop-empty">
        아직 알림이 없습니다. 프롬프트를 보내면 판정 결과가 여기에 쌓입니다.
      </p>

      <ul v-else class="pop-list">
        <li v-for="n in items" :key="n.id" class="row" :class="`tone-${n.tone}`">
          <span class="row-head">
            <span class="kind">{{ n.kind }}</span>
            <span class="time">{{ clock(n.at) }}</span>
            <button
              type="button"
              class="row-del"
              :disabled="n.pending"
              :title="n.pending ? '검토가 끝나야 지울 수 있습니다' : '알림 지우기'"
              :aria-label="`${n.kind} 알림 지우기`"
              @click="notifications.remove(n.id)"
            >
              ×
            </button>
          </span>
          <span class="row-title">{{ n.title }}</span>
          <span v-if="n.body" class="row-body">{{ n.body }}</span>
          <RouterLink v-if="n.link" :to="n.link" class="row-link" @click="open = false">
            {{ n.linkLabel ?? '감사 콘솔에서 보기' }}
          </RouterLink>
        </li>
      </ul>

      <p class="pop-foot">
        {{ isAdmin ? '들어온 해제 요청' : '내가 올린 해제 요청' }}은 서버에서
        {{ POLL_MS / 1000 }}초마다 확인합니다.
      </p>
    </div>
  </div>
</template>

<style scoped>
.bell-wrap {
  position: relative;
  display: flex;
  justify-content: flex-end;
}

/* 테두리도 채움도 없다. 종 하나면 눌리는 자리인 것이 읽힌다 */
.bell {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--gray);
  cursor: pointer;
  transition: color 160ms ease;
}
.bell:hover,
.bell.on {
  color: var(--navy);
}

.dot {
  position: absolute;
  top: -3px;
  right: -4px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 9px;
  background: #b3261e;
  color: #fff;
  font-size: 11px;
  line-height: 17px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.pop {
  position: absolute;
  top: 38px;
  right: 0;
  z-index: 40;
  width: 320px;
  max-height: 420px;
  overflow-y: auto;
  padding: 14px 16px 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 12px 34px rgba(16, 24, 40, 0.14);
}

.pop-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
}
.pop-title {
  color: var(--navy);
  font-size: 15px;
  font-weight: 600;
}
.pop-who {
  color: var(--gray);
  font-size: 12px;
}
.pop-clear {
  margin-left: auto;
  border: 0;
  background: none;
  padding: 0;
  color: var(--gray);
  font-size: 12px;
  cursor: pointer;
}

.pop-empty {
  margin: 0;
  color: var(--gray);
  font-size: 13px;
  line-height: 1.7;
}

.pop-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.row {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 0 10px 11px;
  border-top: 1px solid var(--border);
  border-left: 3px solid transparent;
}
.row:first-child {
  border-top: 0;
}
.row.tone-block {
  border-left-color: #c5372c;
}
.row.tone-mask {
  border-left-color: #c08a2e;
}
.row.tone-pending {
  border-left-color: #6b74d6;
}
.row.tone-allow {
  border-left-color: #2f7d54;
}
.row.tone-request {
  border-left-color: var(--navy);
}

.row-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
/* 검토가 끝난 알림만 지운다. 열려 있는 건은 흐리게 두어 왜 안 되는지 보이게 한다 */
.row-del {
  margin-left: auto;
  border: 0;
  background: none;
  padding: 0 2px;
  color: var(--gray);
  font-size: 15px;
  line-height: 1;
  opacity: 0;
  cursor: pointer;
  transition: opacity 120ms ease, color 120ms ease;
}
.row:hover .row-del,
.row-del:focus-visible {
  opacity: 1;
}
.row-del:hover:not(:disabled) {
  color: var(--navy);
}
.row-del:disabled {
  cursor: not-allowed;
}
.row:hover .row-del:disabled {
  opacity: 0.3;
}
.kind {
  color: var(--navy);
  font-size: 12px;
  font-weight: 600;
}
.time {
  color: var(--gray);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.row-title {
  color: var(--navy);
  font-size: 13.5px;
  line-height: 1.6;
}
.row-body {
  color: var(--gray);
  font-size: 12.5px;
  line-height: 1.65;
  word-break: break-word;
}
.row-link {
  margin-top: 3px;
  color: var(--navy);
  font-size: 12.5px;
}

.pop-foot {
  margin: 10px 0 0;
  padding-top: 9px;
  border-top: 1px solid var(--border);
  color: var(--gray);
  font-size: 11.5px;
}
</style>
