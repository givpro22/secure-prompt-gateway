<script setup>
/*
 * 좌하단 계정 메뉴.
 *
 * 항목 내용은 전부 지금 화면이 이미 들고 있는 데이터에서 만든다. 사규 목록은 적용
 * 규칙의 source, 정책 버전은 registeredAt, 검토자는 사용자 목록의 SECURITY_ADMIN이다.
 * 새로 지어낸 항목은 없다 — 눌러도 아무것도 없는 메뉴를 만들지 않기 위함이다.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import RuleBook from './RuleBook.vue'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const router = useRouter()

const open = ref(false)
const panel = ref(null)
const root = ref(null)
/* 계정 변경은 별도 창을 띄우지 않고 메뉴 옆으로 펼친다. 한 번 더 누르는 단계를 없앤다 */
const accountOpen = ref(false)

const MENU = [
  { id: 'settings', label: '설정', icon: 'gear' },
  { id: 'security', label: '보안담당자 요청', icon: 'shield' },
  { id: 'rules', label: '사규 열람', icon: 'book' },
  { id: 'help', label: '도움말', icon: 'help' },
  { id: 'account', label: '계정 변경', icon: 'switch', submenu: true },
]

const ICONS = {
  gear: 'M8 5.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8Zm5.6 2.4c0 .3 0 .6-.1.9l1.4 1.1-1.4 2.4-1.7-.6q-.7.6-1.5.9L10 14h-2.8l-.3-1.7q-.8-.3-1.5-.9l-1.7.6-1.4-2.4 1.4-1.1a5 5 0 0 1 0-1.8L2.3 5.6l1.4-2.4 1.7.6q.7-.6 1.5-.9L7.2 1H10l.3 1.9q.8.3 1.5.9l1.7-.6 1.4 2.4-1.4 1.1c.1.3.1.6.1.9Z',
  shield: 'M8 1.2 13.4 3v4.3c0 3.2-2.2 6.1-5.4 7.5-3.2-1.4-5.4-4.3-5.4-7.5V3L8 1.2Zm0 1.7L4.2 4.2v3.1c0 2.4 1.5 4.6 3.8 5.8 2.3-1.2 3.8-3.4 3.8-5.8V4.2L8 2.9Zm-.8 2.9h1.6v3.6H7.2V5.5Zm0 4.6h1.6v1.5H7.2v-1.5Z',
  book: 'M2.4 2.2h4.2c.8 0 1.4.3 1.4.9v10.1c0-.4-.6-.7-1.4-.7H2.4V2.2Zm11.2 0H9.4c-.8 0-1.4.3-1.4.9v10.1c0-.4.6-.7 1.4-.7h4.2V2.2Zm0 1.5V11H9.4q-.7 0-1.4.3V3.7q.6-.2 1.4-.2h4.2Z',
  help: 'M8 1.4a6.6 6.6 0 1 0 0 13.2A6.6 6.6 0 0 0 8 1.4Zm0 1.6a5 5 0 1 1 0 10 5 5 0 0 1 0-10Zm-.8 7.4h1.6v1.5H7.2v-1.5Zm.8-5.6c1.3 0 2.3.9 2.3 2 0 .8-.4 1.2-1 1.7-.5.4-.7.6-.7 1.1H7.2c0-1 .4-1.5 1-2 .4-.3.6-.5.6-.8 0-.4-.4-.6-.8-.6s-.8.3-.8.8H5.7c0-1.2 1-2.2 2.3-2.2Z',
  switch: 'M5.4 1.9 6.5 3 4.8 4.7h7.4v1.6H4.8L6.5 8 5.4 9.1 1.8 5.5l3.6-3.6Zm5.2 5.8 3.6 3.6-3.6 3.6-1.1-1.1 1.7-1.7H1.8v-1.6h9.4L9.5 8.8l1.1-1.1Z',
}

const admin = computed(() => session.users.find((u) => u.role === 'SECURITY_ADMIN') ?? null)

const ruleCount = computed(() =>
  session.policies.reduce((n, p) => n + (p.rules?.length ?? 0), 0),
)

const baseDate = computed(() => {
  const dates = session.policies.map((p) => p.registeredAt).filter(Boolean)
  return dates.length === 0 ? '—' : dates.slice().sort().at(-1)
})

function pick(item) {
  if (item.submenu) {
    accountOpen.value = !accountOpen.value
    return
  }
  open.value = false
  accountOpen.value = false
  panel.value = item.id
}

function switchTo(userId) {
  session.setCurrentUser(userId)
  accountOpen.value = false
  open.value = false
}

function goAudit() {
  panel.value = null
  router.push('/admin/audit')
}

function onOutside(event) {
  if (root.value && !root.value.contains(event.target)) {
    open.value = false
    accountOpen.value = false
  }
}
function onKey(event) {
  if (event.key !== 'Escape') return
  if (panel.value) panel.value = null
  else if (accountOpen.value) accountOpen.value = false
  else open.value = false
}
onMounted(() => {
  document.addEventListener('mousedown', onOutside)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutside)
  document.removeEventListener('keydown', onKey)
})

const PANEL_TITLE = {
  settings: '설정',
  security: '보안담당자 요청',
  rules: '사규 열람',
  help: '도움말',
}
</script>

<template>
  <div ref="root" class="user">
    <button type="button" class="chip" :aria-expanded="open" @click="open = !open">
      <span class="avatar" aria-hidden="true">{{ session.currentUser?.name?.[0] ?? '·' }}</span>
      <span class="who">
        <strong>{{ session.currentUser?.name ?? '—' }}</strong>
        <em>{{ session.currentDeptName }}</em>
      </span>
      <span class="caret" :class="{ up: open }" aria-hidden="true">▾</span>
    </button>

    <ul v-if="open" class="menu" role="menu">
      <li v-for="m in MENU" :key="m.id" class="menu-row">
        <button
          type="button"
          class="menu-item"
          role="menuitem"
          :aria-expanded="m.submenu ? accountOpen : undefined"
          @click="pick(m)"
        >
          <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <path :d="ICONS[m.icon]" fill="currentColor" />
          </svg>
          <span class="mi-label">{{ m.label }}</span>
          <span v-if="m.submenu" class="arrow" :class="{ open: accountOpen }" aria-hidden="true">›</span>
        </button>

        <!-- 계정 목록. 옆으로 펼쳐 바로 고른다 -->
        <ul v-if="m.submenu && accountOpen" class="submenu" role="menu">
          <li v-for="u in session.users" :key="u.userId">
            <button
              type="button"
              class="menu-item sub"
              role="menuitemradio"
              :aria-checked="u.userId === session.currentUserId"
              @click="switchTo(u.userId)"
            >
              <span class="tick" aria-hidden="true">{{ u.userId === session.currentUserId ? '✓' : '' }}</span>
              <span class="mi-label">{{ u.name }} · {{ u.department.name }}</span>
            </button>
          </li>
        </ul>
      </li>
    </ul>
  </div>

  <Teleport to="body">
    <div v-if="panel" class="overlay" @click.self="panel = null">
      <section class="dialog" role="dialog" aria-modal="true">
        <header class="dialog-head">
          <h2>{{ PANEL_TITLE[panel] }}</h2>
          <button type="button" class="close" aria-label="닫기" @click="panel = null">×</button>
        </header>

        <!-- 설정 — 지금 게이트웨이가 어떤 상태로 도는지. 값은 전부 실제 응답에서 온다 -->
        <dl v-if="panel === 'settings'" class="rows">
          <div><dt>소속 부서</dt><dd>{{ session.currentDeptName }}</dd></div>
          <div><dt>적용 정책</dt><dd>{{ session.policies.length }}건 · 규칙 {{ ruleCount }}종</dd></div>
          <div><dt>정책 기준일</dt><dd>{{ baseDate }}</dd></div>
          <div><dt>전송 대상 모델</dt><dd>Llama-3.1-70B · 사내 GPU</dd></div>
          <div><dt>원문 보관</dt><dd>감사 목적으로 저장하되 화면에는 표시하지 않습니다</dd></div>
          <div><dt>로그인</dt><dd>구현하지 않았습니다. 계정 전환으로 부서를 바꿉니다</dd></div>
        </dl>

        <!-- 보안담당자 요청 — 요청 폼을 만들지 않는다. 확정 경로가 이미 정해져 있다 -->
        <div v-else-if="panel === 'security'" class="body">
          <p>
            검토가 필요한 판정은 <strong>보안 담당자가 감사 콘솔에서 확정</strong>합니다.
            직원 화면에서는 AI 후보를 읽을 수만 있고 승인·기각 버튼이 없습니다.
          </p>
          <dl class="rows">
            <div><dt>담당자</dt><dd>{{ admin?.name ?? '—' }} · {{ admin?.department?.name ?? '정보보안팀' }}</dd></div>
            <div><dt>확정 방법</dt><dd>감사 콘솔 → 해당 행 → AI 제안 섹션에서 승인 또는 기각</dd></div>
          </dl>
          <p class="note">
            차단된 건은 요청 대상이 아닙니다. 규칙이 이미 결정한 것이라 사람이 번복하지 않습니다.
          </p>
          <button type="button" class="go" @click="goAudit">감사 콘솔 열기</button>
        </div>

        <!-- 사규 열람 — 조문은 규칙에서 생성한다. RuleBook 주석 참고 -->
        <RuleBook v-else-if="panel === 'rules'" />

        <!-- 도움말 -->
        <div v-else-if="panel === 'help'" class="body">
          <dl class="rows">
            <div><dt class="t-green">허용</dt><dd>위반 신호가 없어 그대로 전송됩니다.</dd></div>
            <div><dt class="t-amber">마스킹</dt><dd>탐지된 항목을 라벨로 치환한 본문만 전송됩니다. 원문은 화면에 남습니다.</dd></div>
            <div><dt class="t-red">차단</dt><dd>전송하지 않습니다. 사유에 적힌 항목을 빼고 다시 보내면 됩니다.</dd></div>
            <div><dt class="t-purple">검토 대기</dt><dd>AI가 후보를 제시하고 보안 담당자가 확정합니다. AI는 결정하지 않습니다.</dd></div>
          </dl>
          <p class="note">
            판정은 부서에 따라 갈립니다. 같은 문장도 적용 정책이 다르면 결과가 다릅니다.
          </p>
        </div>


      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.user {
  position: relative;
}

.chip {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px 9px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--nav-fg);
  font: inherit;
  text-align: left;
}

.chip:hover {
  background: var(--nav-bg-soft);
}

.avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 50%;
  background: var(--nav-bg-active);
  font-size: 13px;
  font-weight: 700;
}

.who {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  line-height: 1.3;
}

.who strong {
  font-size: 14px;
}

.who em {
  font-style: normal;
  font-size: 12px;
  color: var(--nav-muted);
}

.caret {
  color: var(--nav-muted);
  font-size: 10px;
  transition: transform 140ms ease;
}
.caret.up {
  transform: rotate(180deg);
}

.menu {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 6px);
  z-index: 20;
  margin: 0;
  padding: 5px;
  list-style: none;
  border: 1px solid var(--nav-line);
  border-radius: 10px;
  background: var(--nav-bg-soft);
  box-shadow: 0 10px 26px rgb(0 0 0 / 32%);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px 9px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--nav-fg);
  font: inherit;
  font-size: 13.5px;
  text-align: left;
}

.menu-item:hover {
  background: var(--nav-bg-active);
}

.menu-row {
  position: relative;
}

.mi-label {
  flex: 1;
}

.arrow {
  color: var(--nav-muted);
  font-size: 14px;
  line-height: 1;
  transition: transform 140ms ease;
}

.arrow.open {
  transform: rotate(90deg);
}

.submenu {
  position: absolute;
  left: calc(100% + 6px);
  bottom: -5px;
  z-index: 25;
  margin: 0;
  padding: 5px;
  list-style: none;
  min-width: 176px;
  border: 1px solid var(--nav-line);
  border-radius: 10px;
  background: var(--nav-bg-soft);
  box-shadow: 0 10px 26px rgb(0 0 0 / 32%);
}

.menu-item.sub {
  font-size: 13px;
}

.tick {
  width: 12px;
  flex: none;
  color: var(--nav-fg);
  font-size: 12px;
}

.overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(22 32 46 / 42%);
}

.dialog {
  width: min(560px, 100%);
  max-height: 80vh;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 18px 20px 20px;
  border-radius: 12px;
  background: var(--page-bg);
  box-shadow: 0 18px 48px rgb(22 32 46 / 26%);
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.dialog-head h2 {
  margin: 0;
  font-size: 16.5px;
  color: var(--navy);
}

.close {
  border: 0;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: var(--gray);
}

.rows {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rows > div {
  display: grid;
  grid-template-columns: 92px 1fr;
  gap: 10px;
  font-size: 13.5px;
}

dt {
  color: var(--gray);
  font-weight: 700;
}

dd {
  margin: 0;
  color: var(--navy);
  line-height: 1.6;
}

.t-green {
  color: var(--green);
}
.t-amber {
  color: var(--amber);
}
.t-red {
  color: var(--red);
}
.t-purple {
  color: var(--purple);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 13.5px;
  color: var(--navy);
  line-height: 1.65;
}

.body p {
  margin: 0;
}

.note {
  color: var(--gray);
}

.go {
  align-self: flex-start;
  padding: 7px 14px;
  border: 0;
  border-radius: 999px;
  background: var(--navy);
  color: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
}

</style>
