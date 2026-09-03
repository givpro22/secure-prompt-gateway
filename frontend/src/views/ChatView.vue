<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import AiCandidateList from '../components/AiCandidateList.vue'
import MessageBubble from '../components/MessageBubble.vue'
import MessageInput from '../components/MessageInput.vue'
import PendingIndicator from '../components/PendingIndicator.vue'
import ModelChip from '../components/ModelChip.vue'
import PolicyCaption from '../components/PolicyCaption.vue'
import NoticeRail from '../components/NoticeRail.vue'
import SessionTally from '../components/SessionTally.vue'
import StatusBadge from '../components/StatusBadge.vue'
import VerdictCard from '../components/VerdictCard.vue'
import { fetchInspection } from '../api/inspections'
import { submitMessage } from '../api/messages'
import { POLL_MAX_ATTEMPTS, usePolling } from '../composables/usePolling'
import { errorText, expectField } from '../lib/contract'
import { DECIDED_BY_TERMS, term } from '../lib/terms'
import { useSessionStore } from '../stores/session'
import { useThreadStore } from '../stores/thread'

/*
 * SCR-01 직원 AI 챗 — 상태 5종 (기획서 5.3).
 *
 *  S1 초기   진입             빈 대화 + PolicyCaption
 *  S2 허용   200 ALLOW        발화 그대로, 규칙 0건, 입력창 비움
 *  S3 마스킹 200 MASK         마스킹본 + 하이라이트, 입력창 비움
 *  S4 차단   403 BLOCK        규칙 목록 + 의무/출처, 입력창에 원문 복원
 *  S5 검토   202 PENDING      스피너 → COMPLETED 후 AI 후보(읽기 전용)
 */

const thread = useThreadStore()
const session = useSessionStore()
const draft = ref('')

/*
 * 빈 화면에서는 입력창이 가운데, 대화가 시작되면 아래로 내려간다.
 * justify-content는 애니메이션이 안 되므로 입력창 아래 여백의 flex-grow를 1에서 0으로
 * 줄인다 — 그건 보간되므로 입력창이 실제로 미끄러져 내려간다.
 */
const started = computed(() => entries.value.length > 0)

/*
 * 전송하지 않은 입력은 사이드바 "작성 중"으로 넘어간다. 타이핑마다 스토어를 때리지
 * 않도록 잠깐 멈췄을 때만 반영한다.
 */
let writingTimer = null
watch(draft, (text) => {
  clearTimeout(writingTimer)
  writingTimer = setTimeout(() => thread.setWriting(text), 500)
})

// 사이드바의 "새 대화" — 대화를 비운다. 영속화가 없으므로 화면 상태만 지우면 된다.
watch(
  () => thread.clearedAt,
  () => {
    entries.value = []
    draft.value = ''
    banner.value = ''
  },
)

// 작성 중 항목 — 입력창만 복원한다.
watch(
  () => thread.pendingDraft,
  (picked) => {
    if (picked) draft.value = picked.text
  },
)

/*
 * 데모 대화 열기. **지금 대화를 갈아끼운다** — 이어붙이면 서로 다른 세션의 판정이
 * 한 흐름처럼 보이고 누를수록 쌓이기만 한다.
 *
 * 판정 객체를 심지 않고 문장을 차례로 태운다. 앞 턴이 끝나야 다음 턴을 보내므로
 * 순서가 화면에 그대로 남는다 — 차단당하고 고쳐서 다시 보내는 흐름이 그 순서다.
 */
const replaying = ref(false)

watch(
  () => thread.pendingDemo,
  async (demo) => {
    if (!demo || replaying.value) return
    replaying.value = true
    entries.value = []
    // 내 대화가 화면에서 내려갔으니 "이번 세션"도 비운다. 화면에 없는 것을 가리키면
    // 사이드바가 거짓말을 한다.
    thread.startSession()
    banner.value = ''
    try {
      for (const prompt of demo.prompts) {
        draft.value = prompt
        await nextTick()
        await send()
      }
    } finally {
      draft.value = ''
      thread.setWriting(null)
      replaying.value = false
    }
  },
)
const sending = ref(false)
const banner = ref('')
const entries = ref([])
const pollingEntryKey = ref(null)
const refreshingKey = ref(null)

const {
  isPolling,
  elapsedSec,
  attempts,
  start: startPoll,
} = usePolling()
let nextKey = 1

/** 상태 코드와 decision이 계약(§1-4)대로 대응하는지 확인한다. */
function checkDecision(status, verdict) {
  const expected = { 200: ['ALLOW', 'MASK'], 202: ['PENDING'], 403: ['BLOCK'] }[status]
  expectField(
    Array.isArray(expected) && expected.includes(verdict.decision),
    'ChatView',
    `HTTP ${status}에 decision=${verdict.decision}이 왔습니다 (계약서 §1-4)`,
    verdict,
  )
}

async function send() {
  const text = draft.value.trim()
  if (text.length === 0 || sending.value) return

  sending.value = true
  banner.value = ''
  try {
    const res = await submitMessage(text)
    const verdict = res.data
    checkDecision(res.status, verdict)

    const entry = {
      key: nextKey++,
      verdict,
      // 클라이언트가 들고 있는 입력값. 차단(submittedText=null) 시 버블에 그린다 (D15)
      inputText: text,
      inspection: null,
      aiStatus: verdict.aiStatus,
      note: '',
    }
    entries.value.push(entry)

    /*
     * 차단은 수정 후 재전송을 유도하는 것이 목적이므로 입력을 날리지 않는다.
     * 여기서 복원하는 값은 클라이언트가 들고 있던 입력값이며 서버 응답이 아니다 (화면 명세 2.4-5).
     * 마스킹은 이미 마스킹본이 전송됐으므로 비운다 (기획서 5.3).
     */
    draft.value = verdict.decision === 'BLOCK' ? text : ''

    // 보냈으니 더 이상 작성 중이 아니다.
    thread.setWriting(null)
    // "이번 세션"은 **직접 입력해 답변을 받은 것**만 센다. 데모 대화를 열어보는 것은
    // 지난 대화를 훑는 행동이지 내가 이번에 한 일이 아니다.
    if (!replaying.value) thread.addTurn(text, verdict.decision)

    if (verdict.decision === 'PENDING') startPolling(entry)
  } catch (err) {
    banner.value = errorText(err, '전송에 실패했습니다.')
  } finally {
    sending.value = false
  }
}

function startPolling(entry) {
  // 폴러는 하나다. 이전 검토 건이 아직 PENDING이면 폴링이 멈춘 사실을 남긴다.
  const previous = entries.value.find(
    (e) => e.key === pollingEntryKey.value && e.aiStatus === 'PENDING',
  )
  if (previous) previous.note = '새 전송으로 자동 확인이 중단되었습니다. 아래 버튼으로 결과를 확인하세요.'

  /*
   * 폴링 간격은 서버가 지시한다 (계약서 §1-4). FE 상수를 쓰지 않는다.
   * 값이 없으면 계약 위반이므로 크게 남기고, 데모가 멈추지 않도록 서버 기본값으로 진행한다.
   */
  const pollAfterMs = entry.verdict.pollAfterMs
  const valid = typeof pollAfterMs === 'number' && pollAfterMs > 0
  expectField(valid, 'ChatView', '202 응답에 pollAfterMs가 없습니다 (계약서 §1-4)', entry.verdict)

  pollingEntryKey.value = entry.key
  startPoll({
    intervalMs: valid ? pollAfterMs : 2000,
    poll: () => fetchInspection(entry.verdict.inspectionId),
    // D12 — 종료 판단은 aiStatus로만 한다. 사람의 확정은 폴링으로 따라가지 않는다
    isDone: (inspection) => inspection.aiStatus !== 'PENDING',
    onTick: (inspection) => applyInspection(entry, inspection),
    onDone: (inspection) => {
      applyInspection(entry, inspection)
      // 직원 화면의 안내 문장이므로 D16의 "분석" 규칙 대상이 아니다 (기획서 5.3 문구를 따른다)
      entry.note =
        inspection.aiStatus === 'FAILED'
          ? '자동 검토에 실패했습니다. 담당자가 직접 확인합니다.'
          : ''
    },
    onExhausted: () => {
      entry.note = `검토가 지연되고 있습니다. (${POLL_MAX_ATTEMPTS}회 확인) 잠시 후 결과를 다시 확인해 주세요.`
    },
    onError: (err) => {
      entry.note = errorText(err, '검토 상태를 확인하지 못했습니다.')
    },
  })
}

function applyInspection(entry, inspection) {
  // 담당자가 확정하면 대화 전체의 대표 판정도 따라 올라간다.
  if (inspection?.status) thread.raiseDecision(inspection.status)
  entry.inspection = inspection
  entry.aiStatus = inspection.aiStatus
}

/**
 * D12 — 담당자 확정 결과는 폴링이 아니라 화면 재조회로 반영한다.
 * 확정 시점을 예측할 수 없어 폴링으로 따라가면 무한 폴링이 된다.
 */
async function refresh(entry) {
  refreshingKey.value = entry.key
  entry.note = ''
  try {
    applyInspection(entry, await fetchInspection(entry.verdict.inspectionId))
  } catch (err) {
    entry.note = errorText(err, '결과를 다시 불러오지 못했습니다.')
  } finally {
    refreshingKey.value = null
  }
}

function aiFindings(entry) {
  // 202 시점에는 inspection 자체가 없다. COMPLETED 가드 안에서만 호출한다
  return (entry.inspection?.findings ?? []).filter((f) => f.source === 'AI')
}

function isHumanDecided(entry) {
  const inspection = entry.inspection
  return Boolean(inspection) && inspection.finalDecision !== 'PENDING'
}
</script>

<template>
  <div class="layout">
  <div class="chat" :class="{ started }">
    <p v-if="banner" class="banner">{{ banner }}</p>

    <SessionTally :entries="entries" />

    <section class="thread">
      <!-- S1 초기 — 인사말만. 입력창이 가운데 있다가 대화가 시작되면 내려간다 -->
      <Transition name="greet">
        <div v-if="!started" class="empty">
          <h2 class="hello">안녕하세요, {{ session.currentUser?.name ?? '' }} 님</h2>
          <p class="lead">
            무엇이든 물어보세요. 보내기 전에 {{ session.currentDeptName }} 정책으로 검사하고,
            판정과 근거를 기록으로 남깁니다.
          </p>
        </div>
      </Transition>

      <article v-for="entry in entries" :id="`turn-${entry.key}`" :key="entry.key" class="turn">
        <!--
          버블은 **작성자가 친 원문 그대로**다. 마스킹본을 여기 그리면 무엇을 물어봤는지
          알아볼 수 없다 — 라벨로 바뀐 자리가 문장의 핵심일 때가 많다.
          전송된 본문은 판정 카드가 따로 보여준다.

          이 값은 API가 아니라 FE 로컬 상태에서 온다. 원문 미표시 원칙(5.4)의 대상은
          감사 콘솔에서 보는 타인의 원문이며, 작성자에게 자기 입력을 돌려주는 것은
          유출이 아니다 (D15).
        -->
        <MessageBubble :text="entry.inputText" :blocked="entry.verdict.decision === 'BLOCK'" />

        <VerdictCard :verdict="entry.verdict" :original-text="entry.inputText" />

        <!-- S5 검토 대기 -->
        <template v-if="entry.verdict.decision === 'PENDING'">
          <PendingIndicator
            v-if="isPolling && pollingEntryKey === entry.key"
            :elapsed-sec="elapsedSec"
            :attempts="attempts"
          />

          <!-- aiAssessment는 202 시점에 존재하지 않는다. COMPLETED 가드 안에서만 접근한다 -->
          <section v-if="entry.aiStatus === 'COMPLETED'" class="review">
            <header class="review-head">
              <StatusBadge value="PENDING_REVIEW" />
              <span class="review-title">검토 대기 (담당자 확정 필요)</span>
            </header>
            <p class="caption readonly-note">
              아래는 AI가 제시한 후보입니다. 확정은 보안 담당자가 감사 콘솔에서 합니다.
            </p>
            <AiCandidateList
              :findings="aiFindings(entry)"
              :assessment="entry.inspection.aiAssessment"
              readonly
            />
          </section>

          <!--
            직원 화면의 안내 문장은 D16의 "분석" 규칙 대상이 아니다. 고치지 말 것.
            D16은 `aiStatus` 값 라벨(감사 콘솔의 "분석 중"·"분석 완료"·"분석 실패")을 고정한 것이고,
            여기는 기획서 5.3의 프로세스 안내 문구다. 직원에게 필요한 것은 자기 프롬프트가
            보안 검토를 받고 있다는 사실이지 AI 개입 여부가 아니다.
          -->
          <section v-else-if="entry.aiStatus === 'FAILED'" class="review failed">
            <header class="review-head">
              <StatusBadge value="PENDING_REVIEW" />
              <span class="review-title">자동 검토 실패 — 담당자 확인 중</span>
            </header>
          </section>

          <p v-if="entry.note" class="note caption">{{ entry.note }}</p>

          <!-- 담당자 확정 후의 최종 판정 -->
          <p v-if="isHumanDecided(entry)" class="final">
            <StatusBadge :value="entry.inspection.status" prefix="최종 판정" />
            <span class="caption">
              확정 주체 {{ term(DECIDED_BY_TERMS, entry.inspection.decidedBy) }}
            </span>
          </p>

          <div class="refresh-row">
            <button
              type="button"
              class="refresh"
              :disabled="refreshingKey === entry.key"
              @click="refresh(entry)"
            >
              {{ refreshingKey === entry.key ? '확인 중…' : '결과 새로고침' }}
            </button>
            <span class="caption">담당자 확정 결과는 이 버튼으로 반영됩니다.</span>
          </div>
        </template>
      </article>
    </section>

    <footer class="composer">
      <MessageInput v-model="draft" :disabled="sending" @submit="send" />
      <div class="composer-meta">
        <ModelChip />
        <PolicyCaption />
      </div>
    </footer>

    <!-- 입력창을 가운데로 밀어 올리는 여백. 대화가 시작되면 0으로 줄며 내려간다 -->
    <div class="tail" aria-hidden="true" />
  </div>
  <NoticeRail />
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  align-items: stretch;
  height: 100%;
  min-height: 0;
}

.chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
  padding: 20px 16px 20px;
  height: 100%;
  min-height: 0;
}

.thread {
  flex: none;
  /* 인사말이 빠질 때 absolute로 떠서 자리를 즉시 비우지 않게 한다 */
  position: relative;
}

/* 대화가 쌓이면 스레드만 스크롤한다. 입력창과 캡션은 항상 보인다 */
.chat.started .thread {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.composer {
  flex: none;
}

.empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  text-align: center;
  padding: 8px 0 4px;
}

.hello {
  margin: 0;
  font-size: 29px;
  font-weight: 700;
  color: var(--navy);
  letter-spacing: -0.02em;
}

.empty .lead {
  margin: 0;
  max-width: 44ch;
  font-size: 14px;
  line-height: 1.7;
  color: var(--gray);
}

/* 입력창을 가운데로 밀어 올리는 여백. flex-grow는 보간되므로 실제로 미끄러진다 */
.tail {
  flex-grow: 1;
  transition: flex-grow 460ms cubic-bezier(0.22, 0.61, 0.36, 1);
}

.chat.started .tail {
  flex-grow: 0;
}

.greet-enter-active,
.greet-leave-active {
  transition: opacity 260ms ease, transform 320ms cubic-bezier(0.22, 0.61, 0.36, 1);
}

.greet-enter-from,
.greet-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.greet-leave-active {
  position: absolute;
  left: 0;
  right: 0;
}

@media (prefers-reduced-motion: reduce) {
  .tail,
  .greet-enter-active,
  .greet-leave-active {
    transition: none;
  }
}

.composer-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 8px;
}

@media (max-width: 1180px) {
  .layout > :last-child {
    display: none;
  }
}

.banner {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--red);
  border-radius: 6px;
  color: var(--red);
  background: var(--card);
  font-size: var(--font-caption);
}

.thread {
  flex: 1;
  display: grid;
  gap: 20px;
  align-content: start;
}

.empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
}

.turn {
  display: block;
}

.review {
  margin-top: 8px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--purple);
  border-radius: 6px;
  background: #fff;
}

.review-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.review-title {
  font-weight: 700;
  color: var(--purple);
}

.readonly-note {
  margin: 6px 0 10px;
}

.note {
  margin: 8px 2px 0;
  color: var(--amber);
}

.final {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 2px 0;
}

.refresh-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.refresh {
  padding: 5px 12px;
  border: 1px solid var(--blue);
  border-radius: 4px;
  background: #fff;
  color: var(--blue);
  font-size: var(--font-caption);
  font-weight: 600;
}

.composer {
  position: sticky;
  bottom: 0;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  background: var(--page-bg);
}
</style>
