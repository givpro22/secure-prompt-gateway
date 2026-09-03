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
import UserMenu from './UserMenu.vue'
import { useSessionStore } from '../stores/session'
import { useThreadStore } from '../stores/thread'
import { STATUS_TERMS } from '../lib/terms'
import { computed } from 'vue'

const session = useSessionStore()
const thread = useThreadStore()
const router = useRouter()

/*
 * 시연용 대화 이력. **전부 (demo) 표시가 붙는다.**
 *
 * 대화 영속화는 범위 밖이라(0.3) 실제 지난 대화가 없다. 표시 없이 그려두면 화면에
 * 있는 것과 없는 것을 구분할 수 없고, 시연 중에 눌렀을 때 아무것도 안 열린다.
 * 눌리면 그 문장이 입력창에 들어가도록 해 죽은 목록이 되지 않게 했다.
 *
 * `answers`는 `prompts`와 자리를 맞춘 응답이다. **모델을 부른 결과가 아니라 미리 적어
 * 둔 문장이다** (0.3 — 실제 LLM 호출은 범위 밖). 판정만 뜨고 답이 없으면 대화로
 * 보이지 않아서 넣었다.
 *
 * 내용은 모델이 **마스킹된 본문만 봤다는 전제로** 썼다. 가려진 자리를 답변이 그대로
 * 라벨로 되받는 것이 이 서비스가 하는 일을 가장 짧게 보여준다. 차단된 턴은 애초에
 * 나가지 않았으므로 답이 없다 — `null`로 둔다.
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
        answers: [
          '아래 세 가지를 손봤습니다.\n\n1. "확인 부탁드립니다" → "확인해 주시면 감사하겠습니다"\n2. 사과 문장을 앞으로 옮기고 원인 설명을 뒤에 뒀습니다\n3. 맺음말에 회신 기한을 넣었습니다\n\n원문의 요구사항은 그대로 두고 어조만 바꿨습니다.',
        ],
      },
      {
        key: 'd2',
        decision: 'MASK',
        text: '환불 요청 정리',
        prompts: [
          '환불 요청 건 정리해줘. 담당자 연락처 010-1234-5678 포함해서.',
          '방금 정리한 내용을 표로 바꿔줘. 사유별로 묶어서.',
        ],
        answers: [
          '환불 요청 건을 아래로 정리했습니다.\n\n· 접수 경로 — 고객센터 유선, 웹 문의\n· 처리 단계 — 접수 → 사유 확인 → 승인 → 환불 실행\n· 담당자 연락처 — [전화번호]\n\n연락처가 [전화번호]로 들어와 있어 그대로 뒀습니다. 실제 번호가 필요하면 사내 시스템에서 확인해 주세요.',
          '사유별로 묶어 표로 옮겼습니다.\n\n| 사유 | 건수 | 평균 처리일 |\n| --- | --- | --- |\n| 단순 변심 | 12 | 2.1일 |\n| 배송 지연 | 7 | 3.4일 |\n| 상품 불량 | 5 | 4.8일 |\n\n상품 불량이 가장 오래 걸립니다. 검수 단계가 하나 더 있어서입니다.',
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
        answers: [
          null,
          '커넥션 타임아웃이면 먼저 볼 곳은 세 군데입니다.\n\n1. 커넥션 풀 고갈 — 반납되지 않은 커넥션이 쌓이면 새 요청이 대기만 하다 끊깁니다\n2. 네트워크 경로 — 보안 그룹이나 방화벽이 특정 포트만 막고 있는 경우\n3. DB 쪽 max_connections 상한\n\n풀 사용률 그래프가 상한에 붙어 있는지부터 보시는 걸 권합니다. 대개 1번입니다.',
          '지수 백오프에 지터를 얹는 방식이 무난합니다.\n\n· 초기 대기 200ms, 배수 2, 최대 3회\n· 매 회차에 ±20% 무작위를 더해 재시도가 한꺼번에 몰리지 않게\n· 타임아웃 자체는 재시도하되 인증 실패나 문법 오류는 즉시 포기\n\n재시도 상한을 넘기면 회로를 열어 두고 일정 시간 뒤에 반만 흘려보내는 편이 낫습니다.',
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
        answers: [
          '안내 문자 초안입니다.\n\n"[고객명] 고객님, 안녕하세요. 요청하신 건 처리 결과를 안내드립니다. 자세한 내용은 첨부를 확인해 주세요. 문의는 담당자에게 회신 주시면 됩니다."\n\n수신자 이름이 [고객명]으로 들어와 있어 자리만 잡아 뒀습니다. 발송 전에 실제 이름으로 채워 주세요.',
          '이메일 문구로 옮겼습니다.\n\n제목: 요청하신 건 처리 결과 안내\n\n"[고객명] 고객님, 안녕하세요.\n요청하신 건의 처리 결과를 안내드립니다. 상세 내역은 아래 표를 확인해 주시고, 추가로 궁금한 점이 있으시면 [이메일] 로 회신 부탁드립니다."\n\n회신 주소도 [이메일] 로 가려져 있어 그대로 뒀습니다.',
        ],
      },
      {
        key: 'd5',
        decision: 'ALLOW',
        text: 'FAQ 초안',
        prompts: ['자주 묻는 질문 FAQ 초안 10개만 뽑아줘.'],
        answers: [
          'FAQ 초안 10개입니다.\n\n1. 주문 취소는 언제까지 되나요\n2. 배송 조회는 어디서 하나요\n3. 환불은 며칠 걸리나요\n4. 교환과 반품의 차이가 뭔가요\n5. 영수증은 어떻게 받나요\n6. 회원 등급은 어떻게 올라가나요\n7. 쿠폰이 중복 적용되나요\n8. 비회원도 주문할 수 있나요\n9. 배송지는 언제까지 바꿀 수 있나요\n10. 재입고 알림은 어떻게 신청하나요\n\n답변은 각 항목 아래 3~4줄로 붙이는 것을 권합니다. 더 필요하시면 항목만 늘려 드리겠습니다.',
        ],
      },
    ],
  },
]

/*
 * 계정별 시연용 작성 중 초안. 부서에 따라 쓰는 말이 다르다는 것을 보여주는 자리다.
 *
 * **비어 있는 계정이 둘 있다.** 모두가 뭔가를 쓰다 만 상태로 두면 자리 채우기로
 * 보인다. 하필 정보보안팀과 홍보팀을 비운 것도 이유가 있다 — 정책을 만드는 쪽이라
 * 검사받는 프롬프트를 쓰고 있을 자리가 아니다.
 */
const DEMO_DRAFTS = {
  1: {
    key: 'w-dev',
    text: '결제 재시도 설계 검토',
    prompt: '결제 실패 재시도 로직 설계 검토해줘. 백오프 간격이랑 상한 어떻게 잡는 게 좋아?',
  },
  2: {
    key: 'w-sales',
    text: '제안서 요약 초안',
    prompt: '고객사 제안서 요약 초안 잡아줘. 도입 효과랑 일정 위주로.',
  },
  3: {
    key: 'w-hr',
    text: '분기 보고서 개요',
    prompt: '3분기 실적 보고서 개요 잡아줘. 매출·이슈·다음 분기 계획 순서로.',
  },
}

const demoDraft = computed(() => DEMO_DRAFTS[session.currentUserId] ?? null)

const isAdmin = computed(() => session.currentUser?.role === 'SECURITY_ADMIN')

/*
 * 데모 대화는 지금 대화를 갈아끼운다. 이어붙이면 서로 다른 세션의 판정이 한 흐름처럼
 * 보이고, 누를수록 쌓이기만 한다.
 */
function openDemo(item) {
  thread.openDemo(item.key, item.prompts, item.answers ?? [])
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
}

/*
 * 작성 중을 누르면 **새 대화로 연다.** 지금 대화 뒤에 붙이면 보내지도 않은 문장이
 * 앞 판정들 아래에 끼어들어, 한 흐름처럼 보인다. 쓰다 만 것은 아직 시작하지 않은
 * 대화이므로 빈 화면에 입력만 얹은 상태가 맞다.
 */
function restoreDraft(text, key) {
  thread.openDraft(key, text)
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
}




function newChat() {
  thread.clear()
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
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
      <!-- 감사 콘솔은 보안 담당자에게만 보인다. 라우터도 같은 조건으로 막는다 -->
      <RouterLink v-if="isAdmin" to="/admin/audit" class="nav-item">관리자 감사 콘솔</RouterLink>
    </nav>

    <div class="scroll">
      <section v-if="thread.current" class="history">
        <h2>이번 세션</h2>
        <ul>
          <li>
            <button
              type="button"
              class="history-item"
              :class="{ current: thread.viewing === 'own' }"
              @click="thread.resumeOwn()"
            >
              <span class="dot" :class="`t-${token(thread.current.decision)}`" aria-hidden="true" />
              <span class="text">{{ thread.current.title }}</span>
              <span v-if="thread.current.turns > 1" class="turns">{{ thread.current.turns }}턴</span>
            </button>
          </li>
        </ul>
      </section>

      <section class="history">
        <h2>작성 중</h2>
        <ul>
          <li v-if="thread.writing">
            <button
              type="button"
              class="history-item"
              @click="restoreDraft(thread.writing.text, 'w-live')"
            >
              <span class="dot writing" aria-hidden="true" />
              <span class="text">{{ thread.writing.text }}</span>
            </button>
          </li>
          <li v-if="demoDraft">
            <button
              type="button"
              class="history-item"
              @click="restoreDraft(demoDraft.prompt, demoDraft.key)"
            >
              <span class="dot writing" aria-hidden="true" />
              <span class="text">{{ demoDraft.text }}</span>
              <span class="demo">(demo)</span>
            </button>
          </li>
          <li v-if="!thread.writing && !demoDraft" class="none">작성 중인 내용이 없습니다</li>
        </ul>
      </section>

      <section v-for="block in DEMO_HISTORY" :key="block.group" class="history">
        <h2>{{ block.group }}</h2>
        <ul>
          <li v-for="item in block.items" :key="item.key">
            <button
              type="button"
              class="history-item"
              :class="{ current: thread.viewing === item.key }"
              @click="openDemo(item)"
            >
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
      <UserMenu />
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 258px;
  flex: none;
  height: 100%;
  overflow: visible;
  padding: 18px 16px;
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
  font-size: 13px;
  font-weight: 700;
}

.name {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.name strong {
  font-size: 15.5px;
}

.name em {
  font-style: normal;
  font-size: 12.5px;
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
  font-size: 14px;
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
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--nav-muted);
  text-decoration: none;
  font-size: 15px;
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
  font-size: 12px;
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
  font-size: 13.5px;
  text-align: left;
}

.history-item.current {
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
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

.none {
  padding: 7px 12px;
  color: var(--nav-fg-dim);
  font-size: 13px;
}

/* 작성 중 — 판정이 아직 없다. 채우지 않고 테두리만 둔다 */
.dot.writing {
  background: transparent;
  border: 1.5px solid var(--nav-muted);
}

.turns {
  flex: none;
  font-size: 11.5px;
  color: var(--nav-muted);
}

.demo {
  flex: none;
  font-size: 11.5px;
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
  padding-top: 10px;
  border-top: 1px solid var(--nav-line);
}


.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
