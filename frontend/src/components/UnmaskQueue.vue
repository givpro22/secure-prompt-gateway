<script setup>
/*
 * 마스킹 해제 검토 대기열 (D25).
 *
 * 규칙은 명단의 문자열만 본다. 고객과 이름이 같은 직원을 쓰면 그 이름도 [고객명]으로
 * 가려지고, 문맥을 읽지 못하는 규칙으로는 둘을 가를 방법이 없다. 여기가 사람이 그것을
 * 가르는 자리다 — 원문과 마스킹본을 나란히 놓고 정한다.
 *
 * **이 화면이 원문을 여는 유일한 곳이다.** 기획서 5.4의 원문 미노출은 감사 콘솔이 남의
 * 원문을 기본으로 펼치지 않는다는 뜻이고, 여기는 작성자가 자기 문장을 스스로 내놓으며
 * 봐 달라고 한 건이다. 요청이 붙은 건에만, 요청이 있는 동안만 열린다.
 */
import { computed, onMounted, ref } from 'vue'
import { decideUnmask, fetchUnmaskRequests } from '../api/unmask'

const rows = ref([])
const loading = ref(false)
const error = ref('')
const notes = ref({})
const busyId = ref(null)
const showDecided = ref(false)

const pending = computed(() => rows.value.filter((r) => r.status === 'PENDING'))
const decided = computed(() => rows.value.filter((r) => r.status !== 'PENDING'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await fetchUnmaskRequests({ size: 50 })
    rows.value = page.items ?? []
  } catch (err) {
    error.value = err?.response?.data?.message ?? '해제 요청을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

async function decide(row, approve) {
  if (busyId.value != null) return
  busyId.value = row.requestId
  error.value = ''
  try {
    const updated = await decideUnmask(row.requestId, approve, notes.value[row.requestId])
    const i = rows.value.findIndex((r) => r.requestId === row.requestId)
    if (i >= 0) rows.value[i] = updated
    delete notes.value[row.requestId]
  } catch (err) {
    error.value = err?.response?.data?.message ?? '확정에 실패했습니다.'
  } finally {
    busyId.value = null
  }
}

const STATUS_LABEL = { APPROVED: '해제', REJECTED: '유지' }

onMounted(load)
defineExpose({ load })
</script>

<template>
  <section class="queue">
    <header class="head">
      <h2>마스킹 해제 요청</h2>
      <span class="count">{{ pending.length }}건 대기</span>
      <button type="button" class="reload" :disabled="loading" @click="load">
        {{ loading ? '불러오는 중…' : '새로고침' }}
      </button>
    </header>

    <p class="note">
      규칙은 명단에 있는 이름만 봅니다. 고객과 이름이 같은 직원은 구분하지 못합니다.
      원문과 실제로 나간 본문을 비교해 확정하세요.
      <strong>해제해도 이미 전송된 본문은 되돌아오지 않습니다.</strong>
    </p>

    <p v-if="error" class="error">{{ error }}</p>

    <p v-if="!loading && pending.length === 0" class="empty">대기 중인 요청이 없습니다.</p>

    <article v-for="row in pending" :key="row.requestId" class="item">
      <header class="item-head">
        <span class="who">{{ row.requester.name }}</span>
        <span class="mid">#{{ row.messageId }}</span>
        <span class="when">{{ row.createdAt?.slice(0, 16).replace('T', ' ') }}</span>
      </header>

      <p class="reason">{{ row.reason }}</p>

      <div class="compare">
        <div class="side">
          <span class="side-label">원문</span>
          <p class="side-body">{{ row.originalText }}</p>
        </div>
        <div class="side">
          <span class="side-label">실제 전송된 본문</span>
          <p class="side-body masked">{{ row.submittedText }}</p>
        </div>
      </div>

      <div class="actions">
        <input
          v-model="notes[row.requestId]"
          type="text"
          class="note-input"
          maxlength="200"
          placeholder="판단 근거 한 줄 (선택)"
          :disabled="busyId === row.requestId"
        />
        <button
          type="button"
          class="keep"
          :disabled="busyId === row.requestId"
          @click="decide(row, false)"
        >
          마스킹 유지
        </button>
        <button
          type="button"
          class="release"
          :disabled="busyId === row.requestId"
          @click="decide(row, true)"
        >
          해제 승인
        </button>
      </div>
    </article>

    <div v-if="decided.length > 0" class="decided">
      <button type="button" class="toggle" @click="showDecided = !showDecided">
        {{ showDecided ? '▾' : '▸' }} 확정된 요청 {{ decided.length }}건
      </button>
      <ul v-if="showDecided">
        <li v-for="row in decided" :key="row.requestId">
          <span class="tag" :class="row.status.toLowerCase()">{{ STATUS_LABEL[row.status] }}</span>
          <span class="who">{{ row.requester.name }}</span>
          <span class="mid">#{{ row.messageId }}</span>
          <span class="by">확정 {{ row.decidedBy }}</span>
          <span v-if="row.decisionNote" class="by-note">{{ row.decisionNote }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.queue {
  margin-bottom: 22px;
  padding: 20px 22px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.head h2 {
  margin: 0;
  font-size: 17px;
  color: var(--navy);
}
.count {
  color: var(--gray);
  font-size: 13px;
}
.reload {
  margin-left: auto;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 5px 11px;
  background: #fff;
  color: var(--gray);
  font-size: 12px;
  cursor: pointer;
}

.note {
  margin: 10px 0 0;
  color: var(--gray);
  font-size: 13px;
  line-height: 1.75;
}
.note strong {
  color: var(--navy);
  font-weight: 600;
}

.error {
  margin: 10px 0 0;
  color: #b3261e;
  font-size: 13px;
}
.empty {
  margin: 14px 0 0;
  color: var(--gray);
  font-size: 13px;
}

.item {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.item-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
}
.who {
  color: var(--navy);
  font-weight: 600;
}
.mid,
.when,
.by,
.by-note {
  color: var(--gray);
  font-variant-numeric: tabular-nums;
}
.reason {
  margin: 8px 0 0;
  color: var(--navy);
  font-size: 14px;
  line-height: 1.7;
}

.compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
}
@media (max-width: 900px) {
  .compare {
    grid-template-columns: 1fr;
  }
}
.side {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--bg-soft, #f6f7f9);
}
.side-label {
  display: block;
  margin-bottom: 6px;
  color: var(--gray);
  font-size: 12px;
}
.side-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--navy);
  word-break: break-word;
}
.side-body.masked {
  font-variant-numeric: tabular-nums;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.note-input {
  flex: 1;
  min-width: 0;
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 8px;
  font: inherit;
  font-size: 13px;
  color: var(--navy);
}
.keep,
.release {
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.keep {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--gray);
}
.release {
  border: 1px solid var(--navy);
  background: var(--navy);
  color: #fff;
}
.keep:disabled,
.release:disabled {
  opacity: 0.55;
  cursor: default;
}

.decided {
  margin-top: 16px;
}
.toggle {
  border: 0;
  background: none;
  padding: 0;
  color: var(--gray);
  font-size: 13px;
  cursor: pointer;
}
.decided ul {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}
.decided li {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 7px 0;
  border-top: 1px solid var(--line);
  font-size: 13px;
}
.tag {
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
}
.tag.approved {
  background: #e7f4ec;
  color: #1c6b3f;
}
.tag.rejected {
  background: #f1f2f4;
  color: #5b6370;
}
</style>
