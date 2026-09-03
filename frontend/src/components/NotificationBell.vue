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
import { fetchMyUnmaskRequest, fetchUnmaskRequests } from '../api/unmask'
import { useNotificationStore } from '../stores/notifications'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const notifications = useNotificationStore()

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
  try {
    const page = await fetchUnmaskRequests({ status: 'PENDING', size: 20 })
    for (const row of page.items ?? []) {
      notifications.pushOnce(userId, `unmask:${row.requestId}`, {
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
}

const OUTCOME = {
  APPROVED: { tone: 'allow', kind: '해제 승인', verb: '가리지 않아도 되는 것으로 확정되었습니다' },
  REJECTED: { tone: 'mask', kind: '마스킹 유지', verb: '그대로 가려 두는 것으로 확정되었습니다' },
}

/** 요청자 — 내가 올린 건이 어떻게 됐는지 하나씩 묻는다 */
async function pollOutcome() {
  const userId = session.currentUserId
  const watching = notifications.byUser[userId]?.watching ?? []
  for (const w of [...watching]) {
    try {
      const row = await fetchMyUnmaskRequest(w.messageId)
      if (row.status === 'PENDING') continue
      const o = OUTCOME[row.status]
      notifications.pushOnce(userId, `outcome:${row.requestId}`, {
        tone: o.tone,
        kind: o.kind,
        title: `${w.title} — ${o.verb}`,
        body: row.decisionNote
          ? `${row.decidedBy}: ${row.decisionNote}`
          : `${row.decidedBy} 확정. 이미 전송된 본문은 그대로입니다.`,
      })
      notifications.unwatch(userId, w.messageId)
    } catch {
      // 아직 없거나 잠깐 실패한 것이다. 다음 차례에 다시 묻는다.
    }
  }
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
        <button v-if="items.length > 0" type="button" class="pop-clear" @click="notifications.clear()">
          지우기
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
          </span>
          <span class="row-title">{{ n.title }}</span>
          <span v-if="n.body" class="row-body">{{ n.body }}</span>
          <RouterLink v-if="n.link" :to="n.link" class="row-link" @click="open = false">
            감사 콘솔에서 보기
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
