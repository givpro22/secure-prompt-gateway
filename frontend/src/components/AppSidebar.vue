<script setup>
/*
 * 좌측 사이드바.
 *
 * 계정 전환이 하단 사용자 칩에 붙어 있는 이유는 로그인이 없기 때문이다(기획서 0.3).
 * 부서에 따라 판정이 갈리는 것이 데모의 핵심이라 상시 손닿는 자리에 둔다.
 *
 * "이번 세션"은 시안의 오늘/어제 이력 자리다. 대화 영속화가 범위 밖이라 지난 대화는
 * 만들 수 없고, 지금 화면에 있는 것만 보여준다 — 눌러도 아무것도 안 열리는 목록보다 낫다.
 */
import { useRouter } from 'vue-router'
import { useSessionStore } from '../stores/session'
import { useThreadStore } from '../stores/thread'
import { STATUS_TERMS } from '../lib/terms'

const session = useSessionStore()
const thread = useThreadStore()
const router = useRouter()

/*
 * 시연용 대화 이력. **전부 (demo) 표시가 붙는다.**
 *
 * 대화 영속화는 범위 밖이라(0.3) 실제 지난 대화가 없다. 표시 없이 그려두면 화면에
 * 있는 것과 없는 것을 구분할 수 없고, 시연 중에 눌렀을 때 아무것도 안 열린다.
 * 눌리면 그 문장이 입력창에 들어가도록 해 죽은 목록이 되지 않게 했다.
 */
const DEMO_HISTORY = [
  {
    group: '오늘',
    kind: 'done',
    items: [
      {
        key: 'd1',
        decision: 'ALLOW',
        text: '고객 응대 이메일 문구',
        prompts: ['고객 응대 이메일 문구를 정중한 톤으로 다듬어줘.'],
      },
      {
        key: 'd2',
        decision: 'MASK',
        text: '환불 요청 정리',
        prompts: [
          '환불 요청 건 정리해줘. 담당자 연락처 010-1234-5678 포함해서.',
          '방금 정리한 내용을 표로 바꿔줘. 사유별로 묶어서.',
        ],
      },
      {
        // 이 서비스가 무엇을 하는지 가장 잘 보여주는 대화다.
        // 차단당하고 → 문제가 된 부분을 빼고 다시 보내 → 통과한다.
        key: 'd3',
        decision: 'BLOCK',
        text: '결제 오류 로그 확인',
        prompts: [
          '결제 오류 로그 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 붙이면 죽어.',
          '접속 문자열은 빼고 다시. 커넥션 타임아웃 오류 원인만 짚어줘.',
          '재시도 로직은 어떻게 잡는 게 좋아?',
        ],
      },
    ],
  },
  {
    group: '어제',
    kind: 'done',
    items: [
      {
        key: 'd4',
        decision: 'MASK',
        text: '고객 안내 발송 명단',
        prompts: [
          '담당자 김서준 고객님과 박예린 고객님께 안내 문자 보내줘.',
          '문자 대신 이메일 문구로 바꿔줘. 회신은 hong@example.com 으로.',
        ],
      },
      {
        key: 'd5',
        decision: 'ALLOW',
        text: 'FAQ 초안',
        prompts: ['자주 묻는 질문 FAQ 초안 10개만 뽑아줘.'],
      },
    ],
  },
]

const DEMO_DRAFT = {
  key: 'w1',
  text: '분기 보고서 개요',
  prompt: '3분기 실적 보고서 개요 잡아줘. 매출·이슈·다음 분기 계획 순서로.',
}

/*
 * 데모 대화는 지금 대화를 갈아끼운다. 이어붙이면 서로 다른 세션의 판정이 한 흐름처럼
 * 보이고, 누를수록 쌓이기만 한다.
 */
function openDemo(item) {
  thread.openDemo(item.prompts)
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
}

function restoreDraft(text) {
  thread.requestDraft(text)
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
}



function onSelect(event) {
  session.setCurrentUser(Number(event.target.value))
}

function newChat() {
  thread.clear()
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
}

function goTo(key) {
  if (router.currentRoute.value.name !== 'chat') {
    router.push('/chat')
    return
  }
  document.getElementById(`turn-${key}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function token(decision) {
  return STATUS_TERMS[decision]?.token ?? 'gray'
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="mark" aria-hidden="true">SP</span>
      <span class="name">
        <strong>Secure Prompt</strong>
        <em>Gateway</em>
      </span>
    </div>

    <button type="button" class="new-chat" @click="newChat">＋ 새 대화</button>

    <nav class="nav">
      <RouterLink to="/chat" class="nav-item">직원 AI 챗</RouterLink>
      <RouterLink to="/admin/audit" class="nav-item">관리자 감사 콘솔</RouterLink>
    </nav>

    <div class="scroll">
      <section v-if="thread.items.length > 0" class="history">
        <h2>이번 세션</h2>
        <ul>
          <li v-for="item in thread.items" :key="item.key">
            <button type="button" class="history-item" @click="goTo(item.key)">
              <span class="dot" :class="`t-${token(item.decision)}`" aria-hidden="true" />
              <span class="text">{{ item.text }}</span>
            </button>
          </li>
        </ul>
      </section>

      <section class="history">
        <h2>작성 중</h2>
        <ul>
          <li v-if="thread.writing">
            <button type="button" class="history-item" @click="restoreDraft(thread.writing.text)">
              <span class="dot writing" aria-hidden="true" />
              <span class="text">{{ thread.writing.text }}</span>
            </button>
          </li>
          <li>
            <button type="button" class="history-item" @click="restoreDraft(DEMO_DRAFT.prompt)">
              <span class="dot writing" aria-hidden="true" />
              <span class="text">{{ DEMO_DRAFT.text }}</span>
              <span class="demo">(demo)</span>
            </button>
          </li>
        </ul>
      </section>

      <section v-for="block in DEMO_HISTORY" :key="block.group" class="history">
        <h2>{{ block.group }}</h2>
        <ul>
          <li v-for="item in block.items" :key="item.key">
            <button type="button" class="history-item" @click="openDemo(item)">
              <span class="dot" :class="`t-${token(item.decision)}`" aria-hidden="true" />
              <span class="text">{{ item.text }}</span>
              <span v-if="item.prompts.length > 1" class="turns">{{ item.prompts.length }}턴</span>
              <span class="demo">(demo)</span>
            </button>
          </li>
        </ul>
      </section>
    </div>

    <div class="account">
      <label class="sr-only" for="account-select">계정 전환</label>
      <span class="avatar" aria-hidden="true">{{ session.currentUser?.name?.[0] ?? '·' }}</span>
      <select id="account-select" :value="session.currentUserId" @change="onSelect">
        <option v-for="user in session.users" :key="user.userId" :value="user.userId">
          {{ user.name }} · {{ user.department.name }}
        </option>
      </select>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 236px;
  flex: none;
  height: 100%;
  overflow: hidden;
  padding: 16px 14px;
  background: var(--nav-bg);
  color: var(--nav-fg);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 4px;
}

.mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--nav-bg-active);
  color: var(--nav-fg);
  font-size: 12px;
  font-weight: 700;
}

.name {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.name strong {
  font-size: 14.5px;
}

.name em {
  font-style: normal;
  font-size: 11.5px;
  color: var(--nav-muted);
}

.new-chat {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--nav-line);
  border-radius: 8px;
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
}

.new-chat:hover {
  background: var(--nav-bg-active);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  padding: 9px 11px;
  border-radius: 8px;
  color: var(--nav-muted);
  text-decoration: none;
  font-size: 13.5px;
}

.nav-item:hover {
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
}

.nav-item.router-link-active {
  background: var(--nav-bg-active);
  color: var(--nav-fg);
  font-weight: 700;
}

.history h2 {
  margin: 6px 0 6px 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--nav-muted);
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--nav-muted);
  font: inherit;
  font-size: 12.5px;
  text-align: left;
}

.history-item:hover {
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
  background: var(--nav-muted);
}

.t-green {
  background: #4ec98a;
}
.t-amber {
  background: #e0a63c;
}
.t-red {
  background: #e5705a;
}
.t-purple {
  background: #a78bde;
}

/* 작성 중 — 판정이 아직 없다. 채우지 않고 테두리만 둔다 */
.dot.writing {
  background: transparent;
  border: 1.5px solid var(--nav-muted);
}

.turns {
  flex: none;
  font-size: 10.5px;
  color: var(--nav-muted);
}

.demo {
  flex: none;
  font-size: 10.5px;
  color: var(--nav-line);
}

.text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account {
  display: flex;
  align-items: center;
  gap: 9px;
  padding-top: 12px;
  border-top: 1px solid var(--nav-line);
}

.avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 50%;
  background: var(--nav-bg-active);
  color: var(--nav-fg);
  font-size: 12px;
  font-weight: 700;
}

select {
  flex: 1;
  min-width: 0;
  padding: 6px 6px;
  border: 1px solid var(--nav-line);
  border-radius: 7px;
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
  font: inherit;
  font-size: 12.5px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
