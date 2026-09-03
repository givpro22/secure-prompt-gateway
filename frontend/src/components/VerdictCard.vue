<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchAnswerAvailable, requestAnswer, submitResponse } from '../api/messages'
import { requestUnmask } from '../api/unmask'
import { useNotificationStore } from '../stores/notifications'
import { useSessionStore } from '../stores/session'
import { useThreadStore } from '../stores/thread'
import LlmPicker from './LlmPicker.vue'
import MaskedText from './MaskedText.vue'
import StatusBadge from './StatusBadge.vue'
import { ACTION_TERMS, CATEGORY_TERMS, DECIDED_BY_TERMS, OBLIGATION_TERMS, term } from '../lib/terms'
import { expectField } from '../lib/contract'
import { shortTitle } from '../lib/text'

/*
 * 판정 결과 카드 (기획서 5.3).
 *
 * "규칙 N건"은 `ruleResult.matches` 길이다. `appliedRuleCodes` 길이가 아니다 —
 * 적용된 규칙(로드된 전부)과 매칭된 규칙(finding이 생성된 것)은 다르며,
 * D1 중첩 억제로 매칭됐으나 finding이 없는 규칙은 appliedRuleCodes에만 남는다 (계약서 §1-4).
 */
const props = defineProps({
  /** POST /messages 응답 본문 (200 / 202 / 403 모두 같은 필드 집합) */
  verdict: { type: Object, required: true },
  /** 작성자가 친 원문. 전송본과 다를 때만 비교해 보여준다 */
  originalText: { type: String, default: '' },
  /**
   * 판정이 나자마자 답변을 받아온다. 채팅이라면 보내고 나서 답이 오는 게 당연하고,
   * 버튼을 한 번 더 누르는 건 게이트웨이가 끼어 있다는 걸 굳이 보이는 셈이다.
   * 데모 재생은 미리 적어 둔 답이 있어 끄고, 실제로 보낸 것에만 켠다.
   */
  autoAnswer: { type: Boolean, default: false },
  /**
   * 마스킹 해제 요청의 확정 결과. 종이 물어 와서 대화에 적어 둔 것을 그대로 받는다.
   *
   * 이 카드는 세션을 옮길 때마다 다시 만들어져 자기 상태를 기억하지 못한다. 요청했다는
   * 사실도 확정 결과도 대화 쪽에 있어야 남는다.
   */
  unmask: { type: Object, default: null },
  /**
   * 이미 받아서 검사한 답변. 대화 쪽에 적어 둔 것을 그대로 받는다.
   *
   * 카드 안에만 두었더니 세션을 옮겼다 돌아오는 순간 사라졌다. 그리고 빈 상태로 다시
   * 만들어진 카드가 답을 또 요청해서, 서버가 409(이미 검사함)로 막고 화면에는 오류가
   * 떴다 — 답변이 있던 자리에 붙여넣기 입력창이 대신 나왔다.
   */
  answer: { type: Object, default: null },
  /** 답을 이미 요청했는지. 요청 도중에 세션을 옮겨 응답을 놓쳐도 다시 부르지 않는다 */
  answerAsked: { type: Boolean, default: false },
})

const emit = defineEmits(['answered', 'asking'])

const matches = computed(() => {
  const value = props.verdict.ruleResult?.matches
  if (!expectField(Array.isArray(value), 'VerdictCard', 'ruleResult.matches가 배열이 아닙니다', props.verdict)) {
    return []
  }
  return value
})

const policies = computed(() => {
  const snapshot = props.verdict.policySnapshot
  if (!snapshot) {
    expectField(false, 'VerdictCard', 'policySnapshot이 응답에 없습니다 (계약서 §1-4는 4개 상태 모두 포함)', props.verdict)
    return []
  }
  return snapshot.policies ?? []
})

/*
 * 엠바고로 막힌 매칭. 해제일이 있으면 "언제 다시 시도하면 되는지"를 알려줘야 한다 —
 * 이유 없는 차단은 사용자가 우회를 학습하게 만들고, 그게 이 시스템의 최대 실패 모드다.
 * 규칙이 여러 건이면 가장 늦게 풀리는 날이 실질적인 재시도 시점이다.
 */
const embargoUntil = computed(() => {
  const dates = matches.value.map((m) => m.embargoUntil).filter(Boolean)
  return dates.length === 0 ? null : dates.slice().sort().at(-1)
})

/*
 * 전송이 승인된 본문. ALLOW면 원문 그대로, MASK면 라벨로 치환된 본문이다.
 * BLOCK은 null이고 PENDING은 아직 확정 전이라 내보낼 수 없다.
 */
const approvedText = computed(() => {
  const d = props.verdict.decision
  if (d !== 'ALLOW' && d !== 'MASK') return null
  return props.verdict.submittedText ?? null
})

/*
 * 상용 LLM은 게이트웨이가 대신 호출하지 않는다 (기획서 0.3 — 실제 LLM 호출은 범위 밖).
 * 승인된 본문을 클립보드에 넣고 해당 서비스를 열어 사람이 붙여넣는다.
 *
 * 본문을 URL 쿼리에 실어 보내지 않는 것은 의도다. 게이트웨이가 승인한 본문이라도
 * 주소창·브라우저 기록·리퍼러에 남기면 통제한 경로 밖으로 한 번 더 새는 셈이다.
 */
/*
 * 전송본이 원문과 달라졌을 때만 따로 보여준다. 허용은 둘이 같아서 한 번 더 그리면
 * 같은 문장이 두 번 나오는 소음이 된다.
 */
const changedText = computed(() => {
  const sent = props.verdict.submittedText
  if (!sent || sent === props.originalText) return null
  return sent
})

/*
 * 상용 LLM은 게이트웨이가 대신 호출하지 않는다 (기획서 0.3 — 실제 LLM 호출은 범위 밖).
 * 서버가 API 키로 부르는 것이 아니라, 승인된 본문을 그 서비스의 입력창까지 실어
 * 보내고 나머지는 사람이 한다.
 *
 * `prefill`이 있는 서비스는 주소 쿼리로 본문을 넘긴다. ChatGPT는 그대로 전송까지
 * 가고 Claude는 입력창에 채워진다. Gemini와 Grok은 같은 용도의 파라미터를 공개하지
 * 않아 클립보드로만 넘긴다.
 *
 * 대가가 있다. 본문이 주소창에 실리므로 브라우저 기록과 리퍼러에 한 번 더 남는다.
 * 게이트웨이가 승인한 본문이고 목적지도 어차피 그 서비스지만, 통제한 경로 밖에
 * 사본이 하나 더 생기는 것은 맞다. 클립보드 복사는 그대로 하므로 이 경로가 막힌
 * 환경에서도 붙여넣을 수 있다.
 */
const EXTERNAL_LLMS = [
  { id: 'chatgpt', name: 'ChatGPT', url: 'https://chatgpt.com/', prefill: 'q' },
  { id: 'claude', name: 'Claude', url: 'https://claude.ai/new', prefill: 'q' },
  // Gemini는 URL 프리필이 없다. 로그인 상태에서 ?q= 와 ?text= 를 직접 열어 봤고 둘 다
  // 무시된다(2026-09-03). 클립보드로만 넘긴다. Gemini로 보내는 진짜 경로는 위의
  // "답변 받기"다 — 게이트웨이가 API로 직접 부르고 답변이 출력 검사를 거쳐 돌아온다.
  { id: 'gemini', name: 'Gemini', url: 'https://gemini.google.com/app' },
  // Grok은 ?q= 를 받으면 입력이 아니라 곧바로 전송까지 한다 (같은 날 확인).
  { id: 'grok', name: 'Grok', url: 'https://grok.com/', prefill: 'q' },
]

const copied = ref(false)
const copyFailed = ref(false)
const targetId = ref(EXTERNAL_LLMS[0].id)
const selectedLlm = computed(() => EXTERNAL_LLMS.find((l) => l.id === targetId.value))

/*
 * navigator.clipboard 는 보안 컨텍스트(HTTPS·localhost)에서만 존재한다. 배포 서버는
 * 평문 HTTP라 객체 자체가 없어서 "복사 후 열기"가 아무것도 하지 않고 창만 열었다.
 * 숨긴 textarea + execCommand('copy')로 떨어뜨린다 — 폐기 예정 API지만 비보안
 * 컨텍스트에서 동작하는 것이 이것뿐이다. HTTPS를 붙이면 위쪽 경로가 다시 쓰인다.
 */
function copyByTextarea(text) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.setAttribute('readonly', '')
  // 화면 밖으로 빼되 렌더 트리에는 남긴다 — display:none 이면 선택이 되지 않는다.
  ta.style.position = 'fixed'
  ta.style.top = '-1000px'
  ta.style.opacity = '0'
  document.body.appendChild(ta)

  const selection = document.getSelection()
  const previous = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null
  ta.select()
  ta.setSelectionRange(0, text.length) // iOS Safari는 select()만으로는 범위가 잡히지 않는다

  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }

  ta.remove()
  if (previous && selection) {
    selection.removeAllRanges()
    selection.addRange(previous)
  }
  return ok
}

/**
 * 보안 컨텍스트면 표준 API로, 아니면 곧바로 동기 대체 경로로. 표준 API를 먼저
 * 기다렸다가 실패한 뒤 대체하면 사용자 제스처가 이미 풀려 execCommand도 막힌다.
 */
function copyApproved(text) {
  if (!navigator.clipboard?.writeText) return Promise.resolve(copyByTextarea(text))
  return navigator.clipboard.writeText(text).then(
    () => true,
    () => copyByTextarea(text),
  )
}

async function sendToSelected() {
  const text = approvedText.value
  const target = EXTERNAL_LLMS.find((l) => l.id === targetId.value)
  if (!text || !target) return

  const ok = await copyApproved(text)
  copied.value = ok
  copyFailed.value = !ok
  // 실패 문구는 사용자가 직접 복사할 때까지 남긴다. 성공 표시만 지운다.
  if (ok) setTimeout(() => (copied.value = false), 2400)
  const url = target.prefill
    ? `${target.url}?${target.prefill}=${encodeURIComponent(text)}`
    : target.url
  window.open(url, '_blank', 'noopener,noreferrer')

  notifications.push(session.currentUserId, {
    tone: isMask.value ? 'mask' : 'allow',
    kind: '외부 전송',
    title: `${target.name}(으)로 승인 본문을 넘겼습니다`,
    body: isMask.value ? '마스킹 적용본이 나갔습니다.' : '원문 그대로 나갔습니다.',
  })
}

const isAllow = computed(() => props.verdict.decision === 'ALLOW')
const isBlock = computed(() => props.verdict.decision === 'BLOCK')
const session = useSessionStore()
const thread = useThreadStore()
const notifications = useNotificationStore()

const isMask = computed(() => props.verdict.decision === 'MASK')

/*
 * 마스킹 해제 검토 요청 (D25).
 *
 * 규칙은 명단의 문자열만 본다. 고객과 이름이 같은 직원을 "서지윤 대리한테"라고 쓰면
 * 그 이름도 [고객명]으로 가려지고, 문맥을 읽지 못하는 규칙으로는 둘을 가를 수 없다.
 * 여기서 사람에게 넘긴다 — 사유를 적어 올리면 보안 담당자가 원문과 마스킹본을 나란히
 * 놓고 정한다.
 *
 * 요청이 통과해도 이미 나간 본문은 되돌아오지 않는다. 회수할 수 없는 것을 회수한 척하는
 * 화면이 더 위험하다. 얻는 것은 "이 건은 가릴 필요가 없었다"는 판단이고, 그것을 근거로
 * 다시 보내는 것은 사람이 한다.
 */
/*
 * 출력 검사 (UC-08).
 *
 * 게이트웨이는 지금까지 나가는 것만 봤다. 답변에도 같은 위험이 있다 — 모델이 이름이나
 * 번호를 지어내기도 하고 사내 문서 조각을 물고 나오기도 한다.
 *
 * 답변을 여기로 가져오는 것은 사람이 한다. 상용 LLM 웹 UI로 보내는 구조라 응답이
 * 게이트웨이로 돌아오지 않기 때문이다. 게이트웨이가 API로 직접 부르는 구조가 되면
 * 이 붙여넣기는 사라지고 나머지는 그대로 쓰인다 — 검사하는 쪽은 이미 같은 파이프라인이다.
 */
const checking = ref(false)
const answer = ref('')
const answerBusy = ref(false)
const localAnswer = ref(null)
/** 방금 받은 것(로컬)이거나 대화에 적혀 있던 것 */
const answerVerdict = computed(() => props.answer ?? localAnswer.value)
const answerError = ref('')

const canCheckAnswer = computed(
  () => approvedText.value != null && props.verdict.messageId != null && answerVerdict.value === null,
)

const ANSWER_TONE = {
  ALLOW: '통과 — 질문에 대한 답변에 문제 없음',
  MASK: '답변에서 탐지된 항목을 라벨로 치환했습니다.',
  BLOCK: '이 답변은 그대로 두기 어렵습니다. 아래 규칙에 걸렸습니다.',
  PENDING: '보안 담당자 확인이 필요합니다.',
}

async function checkAnswer() {
  const text = answer.value.trim()
  if (text.length === 0 || answerBusy.value) return
  answerBusy.value = true
  answerError.value = ''
  try {
    const res = await submitResponse(props.verdict.messageId, text)
    localAnswer.value = res.data
    emit('answered', res.data)
    checking.value = false
    notifications.push(session.currentUserId, {
      tone: res.data.decision === 'ALLOW' ? 'allow' : res.data.decision.toLowerCase(),
      kind: '답변 검사',
      title: ANSWER_TONE[res.data.decision] ?? '답변을 검사했습니다',
      body: (res.data.ruleResult?.matches ?? []).map((m) => m.code).join(', ') || '걸린 규칙 없음',
    })
  } catch (err) {
    answerError.value =
      err?.response?.data?.message ?? '답변을 검사하지 못했습니다. 잠시 후 다시 시도하세요.'
  } finally {
    answerBusy.value = false
  }
}

const answerMatches = computed(() => answerVerdict.value?.ruleResult?.matches ?? [])
/** 담당자가 답변을 확정했으면 그 결과. 검토 대기로 남아 있으면 null */
const answerSettled = computed(() => answerVerdict.value?.settled ?? null)

/** 검사 기록의 message.status를 판정 값으로 되돌린다 */
const FROM_STATUS = { ALLOWED: 'ALLOW', MASKED: 'MASK', BLOCKED: 'BLOCK', PENDING_REVIEW: 'PENDING' }

/*
 * 화면에 그릴 답변 판정. 확정이 났으면 그 결과다 — 배지는 "검토 대기"인데 아래에는
 * "차단"이라고 적혀 있으면 어느 쪽이 사실인지 알 수 없다.
 */
const answerDecision = computed(() => {
  const settled = answerSettled.value?.status
  if (settled) return FROM_STATUS[settled] ?? answerVerdict.value?.decision
  return answerVerdict.value?.decision
})

/** 통과한 답변은 접어 둔다. 한 줄이면 충분하고, 내용은 세부사항으로 편다 */
/*
 * 답변 본문은 펴 둔다.
 *
 * 처음에는 통과한 답변을 접어 두고 "세부사항"으로 폈다. 감사 콘솔이라면 맞는 판단이지만
 * 여기는 직원 자기 채팅이고, 답변이 곧 물어본 것에 대한 대답이다. 접어 두면 담당자가
 * 허용해도 정작 답을 어디서 보는지 알 수 없다.
 */
const showAnswerBody = ref(true)
const answerPassed = computed(() => answerDecision.value === 'ALLOW')

/*
 * 답변 받기 — 게이트웨이가 모델을 직접 불러 답변을 받고 곧바로 출력 검사에 넘긴다.
 * 붙여넣기(checkAnswer)와 결과 모양이 같아서 아래 렌더링을 그대로 쓴다.
 *
 * 서버에 제공자가 켜져 있을 때만 버튼이 뜬다. 키가 없으면 붙여넣기만 남는다.
 */
const autoAnswer = ref({ available: false, provider: '' })
const fetching = ref(false)
onMounted(async () => {
  try {
    autoAnswer.value = await fetchAnswerAvailable()
  } catch {
    autoAnswer.value = { available: false, provider: '' }
  }
  // 나간 것(ALLOW·MASK)에만, 제공자가 켜져 있을 때만 이어서 답을 받는다.
  if (props.autoAnswer && !props.answerAsked && autoAnswer.value.available && canCheckAnswer.value) {
    await getAnswer()
  }
})

async function getAnswer() {
  if (fetching.value) return
  fetching.value = true
  answerError.value = ''
  // 요청했다는 사실을 먼저 대화에 남긴다. 응답을 기다리는 사이에 세션을 옮기면 이
  // 카드는 사라지고, 남은 표시가 없으면 돌아왔을 때 같은 답을 또 부른다.
  emit('asking')
  try {
    const res = await requestAnswer(props.verdict.messageId)
    localAnswer.value = res.data
    emit('answered', res.data)
    checking.value = false
    notifications.push(session.currentUserId, {
      tone: res.data.decision === 'ALLOW' ? 'allow' : res.data.decision.toLowerCase(),
      kind: '답변 검사',
      title: ANSWER_TONE[res.data.decision] ?? '답변을 검사했습니다',
      body: (res.data.ruleResult?.matches ?? []).map((m) => m.code).join(', ') || '걸린 규칙 없음',
    })
  } catch (err) {
    const code = err?.response?.data?.code
    /*
     * 이미 검사한 답변은 오류가 아니다. 요청 도중에 화면을 떠나 응답을 놓친 경우인데,
     * 붙여넣기 입력창을 띄우면 같은 답을 두 번 검사하게 된다.
     */
    if (code === 'RESPONSE_ALREADY_INSPECTED') {
      answerError.value = '이 답변은 이미 검사했습니다. 결과는 감사 기록에 남아 있습니다.'
      checking.value = false
      return
    }
    answerError.value =
      code === 'ANSWER_UNAVAILABLE'
        ? '답변 받기가 꺼져 있습니다. 받은 답변을 붙여넣어 검사하세요.'
        : (err?.response?.data?.message ?? '답변을 받지 못했습니다.')
    if (code === 'ANSWER_UNAVAILABLE') autoAnswer.value = { available: false, provider: '' }
    checking.value = true
  } finally {
    fetching.value = false
  }
}

const asking = ref(false)
const reason = ref('')
const submitting = ref(false)
const requested = ref(null)
/** 요청 상태는 방금 올린 것(로컬)이거나 대화에 적힌 확정 결과다 */
const unmaskState = computed(() => props.unmask ?? requested.value)
const unmaskDecided = computed(() => {
  const st = unmaskState.value?.status
  return st === 'APPROVED' || st === 'REJECTED'
})
const requestError = ref('')

/** 명단 규칙에 걸린 건에만 권한다. 주민번호를 풀어 달라고 할 자리는 아니다 */
const ROSTER_CODES = ['PII-CUST-07', 'PII-CUST-08']
const hasRosterHit = computed(() => matches.value.some((m) => ROSTER_CODES.includes(m.code)))
const canAskUnmask = computed(
  () => isMask.value && props.verdict.messageId != null && unmaskState.value === null,
)

function openAsk() {
  asking.value = true
  requestError.value = ''
  if (reason.value.length === 0 && hasRosterHit.value) {
    reason.value = '사내 직원 이름입니다. 고객 명단과 이름만 같습니다.'
  }
}

async function submitAsk() {
  const text = reason.value.trim()
  if (text.length === 0 || submitting.value) return
  submitting.value = true
  requestError.value = ''
  try {
    requested.value = await requestUnmask(props.verdict.messageId, text)
    asking.value = false
    /*
     * 어느 발화에 대한 요청인지 함께 남긴다. 알림함이 계정 단위라 세션을 여럿 돌리고
     * 나면 "마스킹 검토를 요청했습니다"만으로는 어느 건인지 알 수 없다.
     */
    const label = shortTitle(props.originalText)
    notifications.push(session.currentUserId, {
      tone: 'request',
      kind: '검토 요청',
      title: `${label} — 마스킹 검토를 요청했습니다`,
      body: text,
    })
    // 확정이 나면 이 계정 알림함으로 돌아온다. 목록 API는 담당자 전용이라 요청자는
    // 자기 건을 직접 물어야 한다 (D25).
    notifications.watch(session.currentUserId, {
      key: `unmask:${props.verdict.messageId}`,
      kind: 'unmask',
      messageId: props.verdict.messageId,
      // 알림에서 이 대화로 돌아올 좌표. 대화가 캐시에 없을 때 쓰는 대비책이다.
      sessionId: thread.activeId,
      title: label,
    })
  } catch (err) {
    requestError.value =
      err?.response?.data?.message ?? '검토 요청을 보내지 못했습니다. 잠시 후 다시 시도하세요.'
  } finally {
    submitting.value = false
  }
}

const summary = computed(() => {
  switch (props.verdict.decision) {
    case 'ALLOW':
      return '전송됨'
    case 'MASK':
      return '마스킹 후 전송됨'
    case 'BLOCK':
      return '전송이 차단되었습니다. 내용을 수정한 뒤 재전송하세요.'
    case 'PENDING':
      return '보안 검토가 필요한 내용입니다.'
    default:
      return ''
  }
})
</script>

<template>
  <section class="verdict" :class="`decision-${verdict.decision.toLowerCase()}`">
    <header class="head">
      <StatusBadge :value="verdict.decision" />
      <span class="rule-count">규칙 {{ matches.length }}건</span>
      <span class="summary">{{ summary }}</span>
    </header>

    <!-- 허용은 규칙 0건이라 표를 그리지 않고 1줄로 축약한다 (화면 명세 2.2 S2) -->
    <ul v-if="!isAllow && matches.length > 0" class="rules">
      <!--
           key에 span을 붙인다. 같은 규칙이 여러 번 걸릴 수 있어 code만으로는 중복된다 —
           고객 명단 규칙이 한 문장에서 여러 명을 잡는 경우가 그것이다.
        -->
        <li v-for="match in matches" :key="`${match.code}:${match.span?.[0]}`" class="rule">
        <span class="code">{{ match.code }}</span>
        <span class="category">{{ term(CATEGORY_TERMS, match.category) }}</span>
        <span class="action" :class="`action-${(match.action ?? '').toLowerCase()}`">
          {{ term(ACTION_TERMS, match.action) }}
        </span>
        <span v-if="match.matchedKeyword" class="keyword">‘{{ match.matchedKeyword }}’</span>
        <span v-if="match.embargoUntil" class="until">{{ match.embargoUntil }} 해제</span>
        <span class="spacer" />
        <span class="obligation">{{ term(OBLIGATION_TERMS, match.obligation) }}</span>
        <span class="source">{{ match.source }}</span>
      </li>
    </ul>

    <p v-if="isBlock && embargoUntil" class="hint embargo">
      <strong>{{ embargoUntil }}</strong>부터 공개할 수 있는 내용입니다. 그때까지는 외부 AI로 보낼 수 없습니다.
      해당 표현을 빼고 다시 전송하세요. 입력창에 방금 입력한 내용이 그대로 남아 있습니다.
    </p>
    <p v-else-if="isBlock" class="hint">
      차단된 항목을 제거하거나 대체한 뒤 다시 전송하세요. 입력창에 방금 입력한 내용이 그대로 남아 있습니다.
    </p>
    <p v-if="isMask" class="hint">
      탐지된 개인정보를 라벨로 치환한 본문만 전송되었습니다.
      <template v-if="hasRosterHit">
        고객 명단과 이름만 같은 직원이라면 검토를 요청할 수 있습니다.
      </template>
    </p>

    <!-- 마스킹 해제 검토 요청 (D25). 사람이 원문과 마스킹본을 비교해 정한다 -->
    <div v-if="asking" class="unmask">
      <form class="unmask-form" @submit.prevent="submitAsk">
        <label class="unmask-label" for="unmask-reason">
          왜 가리지 않아도 되는지 적어 주세요. 보안 담당자가 원문과 함께 봅니다.
        </label>
        <textarea
          id="unmask-reason"
          v-model="reason"
          rows="2"
          maxlength="500"
          :disabled="submitting"
          placeholder="예) 사내 직원 이름입니다. 고객 명단과 이름만 같습니다."
        />
        <div class="unmask-actions">
          <button type="button" class="unmask-cancel" :disabled="submitting" @click="asking = false">
            취소
          </button>
          <button type="submit" class="unmask-send" :disabled="submitting || reason.trim().length === 0">
            {{ submitting ? '보내는 중…' : '요청 보내기' }}
          </button>
        </div>
        <p v-if="requestError" class="unmask-error">{{ requestError }}</p>
      </form>
    </div>

    <!--
      출력 검사 (UC-08). 받은 답변을 같은 정책으로 다시 본다.
      가져오는 것은 사람이 한다 — 상용 LLM 웹 UI로 보내는 구조라 응답이 돌아오지 않는다.
    -->
    <form v-if="checking" class="answer-check" @submit.prevent="checkAnswer">
      <label class="answer-label" for="answer-text">
        받은 답변을 붙여넣으세요. 보낼 때와 같은 정책으로 다시 검사합니다.
      </label>
      <textarea
        id="answer-text"
        v-model="answer"
        rows="3"
        :disabled="answerBusy"
        placeholder="예) 확인했습니다. 담당자 연락처는 010-1234-5678 입니다."
      />
      <div class="answer-actions">
        <button type="button" class="unmask-cancel" :disabled="answerBusy" @click="checking = false">
          취소
        </button>
        <button type="submit" class="unmask-send" :disabled="answerBusy || answer.trim().length === 0">
          {{ answerBusy ? '검사 중…' : '답변 검사' }}
        </button>
      </div>
      <p v-if="answerError" class="unmask-error">{{ answerError }}</p>
    </form>

    <section
      v-if="answerVerdict"
      class="answer-result"
      :class="`t-${answerDecision.toLowerCase()}`"
    >
      <header class="answer-head">
        <StatusBadge :value="answerDecision" />
        <span class="answer-title">답변 재검사 · 규칙 {{ answerMatches.length }}건</span>
      </header>
      <p v-if="approvedText" class="answer-sent">
        모델에 보낸 것: <code>{{ approvedText }}</code>
      </p>
      <p class="answer-note">{{ ANSWER_TONE[answerDecision] }}</p>
      <ul v-if="answerMatches.length > 0" class="answer-rules">
        <li v-for="m in answerMatches" :key="`${m.code}:${m.span?.[0]}`">
          <code>{{ m.code }}</code>
          <span class="answer-cat">{{ term(CATEGORY_TERMS, m.category) }}</span>
        </li>
      </ul>
      <button
        v-if="answerPassed && answerVerdict.inspectedText"
        type="button"
        class="answer-detail-toggle"
        @click="showAnswerBody = !showAnswerBody"
      >
        {{ showAnswerBody ? '답변 접기' : '답변 펼치기' }}
      </button>
      <p
        v-if="answerVerdict.inspectedText && (!answerPassed || showAnswerBody)"
        class="answer-body"
      >
        <MaskedText :text="answerVerdict.inspectedText" />
      </p>

      <!--
        답변도 검토 대기로 갈 수 있다 (유출 의심, UC-08). 그 확정 결과는 종이 물어 와
        대화에 적어 두고, 여기서 그린다 — 프롬프트 쪽 "최종 판정"과 같은 자리다.
      -->
      <p v-if="answerSettled" class="answer-final caption">
        보안 담당자가 확정했습니다 · 확정 주체
        {{ term(DECIDED_BY_TERMS, answerSettled.decidedBy) }}
      </p>
    </section>

    <!--
      해제 검토 상태는 답변 재검사와 상관없는 별개의 줄이다. 예전에는 위 <section>에
      v-else-if로 매달려 있어서, 답변을 자동으로 받아오게 된 뒤로는 답변 절이 항상 있어
      "검토 요청됨"도 확정 결과도 영영 그려지지 않았다.
    -->
    <p v-if="unmaskDecided" class="unmask-done" :class="unmaskState.status.toLowerCase()">
      <template v-if="unmaskState.status === 'APPROVED'">
        해제 승인 — 가리지 않아도 되는 것으로 확정되었습니다.
      </template>
      <template v-else> 해제 거절 — 그대로 가려 두는 것으로 확정되었습니다. </template>
      <span v-if="unmaskState.decidedBy" class="unmask-by">
        {{ unmaskState.decidedBy }}{{ unmaskState.decisionNote ? `: ${unmaskState.decisionNote}` : ' 확정' }}
      </span>
      <span class="unmask-note">이미 전송된 본문은 되돌아오지 않습니다.</span>
    </p>

    <p v-else-if="unmaskState" class="unmask-done">
      검토 요청됨 — 보안 담당자가 원문과 마스킹본을 비교해 확정합니다.
      이미 전송된 본문은 되돌아오지 않습니다.
    </p>

    <!-- 원문은 위 버블에 그대로 있고, 실제로 나간 본문은 여기에 있다 -->
    <div v-if="changedText" class="sent">
      <span class="sent-label">실제 전송된 본문</span>
      <p class="sent-body"><MaskedText :text="changedText" /></p>
    </div>

    <footer v-if="policies.length > 0" class="policies caption">
      적용 정책
      <span v-for="policy in policies" :key="policy.policyId" class="policy">
        {{ policy.code }} v{{ policy.version }}
      </span>
    </footer>

    <!-- 승인된 본문만 밖으로 나갈 수 있다. 차단·검토 대기에는 이 줄이 뜨지 않는다 -->
    <div v-if="approvedText" class="egress">
      <span class="egress-label">
        전송 승인됨{{ isMask ? ' · 마스킹 적용본' : '' }}
        <span v-if="copied" class="copied" role="status">— 복사했습니다</span>
        <span v-else-if="copyFailed" class="copy-failed" role="status">
          — 복사가 막혀 있습니다. 위 본문을 직접 선택해 복사하세요
        </span>
      </span>
      <span class="egress-actions">
        <button
          v-if="canCheckAnswer && autoAnswer.available"
          type="button"
          class="egress-btn ghost"
          :disabled="fetching"
          :title="autoAnswer.provider ? `제공자: ${autoAnswer.provider}` : ''"
          @click="getAnswer"
        >
          {{ fetching ? '답변 받는 중…' : '답변 받기' }}
        </button>
        <button
          v-if="canCheckAnswer"
          type="button"
          class="egress-btn ghost"
          @click="checking = !checking"
        >
          답변 붙여넣기
        </button>
        <button
          v-if="canAskUnmask"
          type="button"
          class="egress-btn ghost"
          @click="openAsk"
        >
          검토 요청
        </button>
        <LlmPicker v-model="targetId" :options="EXTERNAL_LLMS" />
        <button type="button" class="egress-btn" @click="sendToSelected">
          {{ selectedLlm?.prefill ? '바로 전송' : '복사 후 열기' }}
        </button>
      </span>
    </div>
  </section>
</template>

<style scoped>
.verdict {
  margin-top: 10px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--gray);
  border-radius: 8px;
  background: var(--card);
}

.decision-allow {
  border-left-color: var(--green);
}
.decision-mask {
  border-left-color: var(--amber);
}
.decision-block {
  border-left-color: var(--red);
}
.decision-pending {
  border-left-color: var(--purple);
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.rule-count {
  font-weight: 700;
  font-size: 15px;
}

.summary {
  color: var(--gray);
  font-size: var(--font-caption);
}

.rules {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  border-top: 1px solid var(--border);
}

.rule {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
}

.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 700;
  color: var(--blue);
}

.category {
  color: var(--navy);
}

.action {
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 600;
  color: var(--gray);
  background: #fff;
  border: 1px solid var(--border-strong);
}

.action-block {
  color: var(--red);
  border-color: var(--red);
}
.action-mask {
  color: var(--amber);
  border-color: var(--amber);
}
.action-review {
  color: var(--purple);
  border-color: var(--purple);
}

.keyword {
  color: var(--purple);
}

.until {
  padding: 1px 6px;
  border: 1px solid var(--navy);
  border-radius: 3px;
  color: var(--navy);
  font-variant-numeric: tabular-nums;
}

.hint.embargo strong {
  font-variant-numeric: tabular-nums;
}

.spacer {
  flex: 1;
}

.obligation {
  color: var(--gray);
}

.source {
  color: var(--gray);
}

.sent {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--amber);
  border-radius: 6px;
  background: #fff;
}

.sent-label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--gray);
}

.sent-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
}

.hint {
  margin: 10px 0 0;
  font-size: var(--font-caption);
  color: var(--navy);
}

/* 출력 검사 (UC-08) */
.answer-check {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
}
.answer-label {
  color: var(--gray);
  font-size: 13px;
}
.answer-check textarea {
  resize: vertical;
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: 8px;
  font: inherit;
  font-size: 14px;
  line-height: 1.7;
  color: var(--navy);
}
.answer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.answer-result {
  margin-top: 12px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--line);
  border-radius: 10px;
  background: #fff;
}
.answer-result.t-block {
  border-left-color: #c5372c;
}
.answer-result.t-mask {
  border-left-color: #c08a2e;
}
.answer-result.t-allow {
  border-left-color: #2f7d54;
}
.answer-result.t-pending {
  border-left-color: #6b74d6;
}
.answer-head {
  display: flex;
  align-items: center;
  gap: 9px;
}
.answer-title {
  color: var(--navy);
  font-size: 13.5px;
  font-weight: 600;
}
.answer-sent {
  margin: 8px 0 0;
  color: var(--gray);
  font-size: 12.5px;
  line-height: 1.7;
  word-break: break-word;
}
.answer-sent code {
  color: var(--navy);
}
.answer-note {
  margin: 8px 0 0;
  color: var(--gray);
  font-size: 13px;
  line-height: 1.7;
}
.answer-rules {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
}
.answer-rules code {
  color: var(--navy);
}
.answer-cat {
  margin-left: 6px;
  color: var(--gray);
}
.answer-detail-toggle {
  margin-top: 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 4px 10px;
  background: #fff;
  color: var(--navy);
  font-size: 12.5px;
  cursor: pointer;
}
.answer-body {
  margin: 10px 0 0;
  padding: 11px 13px;
  border-radius: 8px;
  background: var(--bg-soft, #f6f7f9);
  font-size: 14px;
  line-height: 1.8;
  color: var(--navy);
  white-space: pre-wrap;
}

/* 마스킹 해제 검토 요청 (D25) */
.unmask {
  margin-top: 12px;
}
.unmask-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
}
.unmask-label {
  color: var(--gray);
  font-size: 13px;
}
.unmask-form textarea {
  resize: vertical;
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: 8px;
  font: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: var(--navy);
}
.unmask-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.unmask-cancel,
.unmask-send {
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  cursor: pointer;
}
.unmask-cancel {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--gray);
}
.unmask-send {
  border: 1px solid var(--navy);
  background: var(--navy);
  color: #fff;
}
.unmask-send:disabled,
.unmask-cancel:disabled {
  opacity: 0.55;
  cursor: default;
}
.unmask-error {
  margin: 0;
  color: #b3261e;
  font-size: 13px;
}
.unmask-done {
  margin: 12px 0 0;
  color: var(--gray);
  font-size: 13px;
  line-height: 1.7;
}

/* 확정이 난 건은 요청 중과 구별한다 — 둘 다 회색이면 무엇이 끝났는지 알 수 없다 */
.unmask-done.approved,
.unmask-done.rejected {
  padding: 8px 10px;
  border-left: 3px solid var(--gray);
  border-radius: 0 6px 6px 0;
  background: var(--card);
  color: var(--navy);
}

.unmask-done.approved {
  border-left-color: var(--green);
}

.unmask-done.rejected {
  border-left-color: var(--amber);
}

.unmask-by,
.unmask-note {
  display: block;
  color: var(--gray);
  font-size: 12px;
}

.egress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border);
  font-size: var(--font-caption);
}

.egress-label {
  color: var(--navy);
}

.egress-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.egress-btn {
  border-radius: 5px;
  padding: 5px 13px;
  border: 0;
  background: var(--navy);
  color: #fff;
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
}
/* 같은 줄의 두 번째 동작. 모양은 같고 채움만 뺀다 — 나가는 것은 하나뿐이라
   둘 다 진하게 두면 무엇이 주된 동작인지 흐려진다 */
.egress-btn.ghost {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--navy);
}
.egress-btn.ghost:hover {
  border-color: var(--navy);
}

.egress-btn:hover {
  background: var(--blue);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.copied {
  color: var(--green);
}

.copy-failed {
  color: var(--amber);
}

.answer-final {
  margin: 10px 0 0;
  padding-top: 10px;
  border-top: 1px solid var(--border);
  color: var(--gray);
  font-size: 12px;
}

.policies {
  margin-top: 10px;
}

.policy {
  margin-left: 6px;
  color: var(--blue);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
