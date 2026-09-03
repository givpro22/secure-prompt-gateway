<script setup>
import { computed, onMounted, ref } from 'vue'
import AiCandidateList from '../components/AiCandidateList.vue'
import MaskedText from '../components/MaskedText.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { fetchInspection, fetchInspections, reviewFinding } from '../api/inspections'
import { errorText, expectField } from '../lib/contract'
import {
  ACTION_TERMS,
  AI_STATUS_TERMS,
  CATEGORY_TERMS,
  DECIDED_BY_TERMS,
  OBLIGATION_TERMS,
  STATUS_FILTER_OPTIONS,
  term,
} from '../lib/terms'
import { useSessionStore } from '../stores/session'

/*
 * SCR-02 관리자 감사 콘솔 (기획서 5.4).
 * 좌측 목록 + 우측 상세 패널. 상세 패널의 "규칙 판정(결정)"과 "AI 제안(후보)"을 갈라 두는 것이
 * "규칙은 결정하고 AI는 제안한다"(4장)를 화면으로 증명하는 장치다.
 */

const PAGE_SIZE = 20

const session = useSessionStore()

const filters = ref({ deptId: '', status: '', from: '', to: '' })
const page = ref(0)
const rows = ref([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref('')

const selectedId = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const notice = ref('')
const busyFindingId = ref(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

/* ── 기간 필터 ────────────────────────────────────────────────
 * API의 `to`는 미만(exclusive)이다 (계약서 §1-6). 화면의 종료일은 포함으로 다루므로
 * 질의 시 하루를 더해 넘긴다. 시각 표기·필터 모두 UTC 기준으로 통일한다.
 */
function isoDay(date) {
  return date.toISOString().slice(0, 10)
}

function startOfDayIso(day) {
  return `${day}T00:00:00Z`
}

function exclusiveEndIso(day) {
  const date = new Date(`${day}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + 1)
  return `${isoDay(date)}T00:00:00Z`
}

function defaultRange() {
  const today = new Date()
  const from = new Date(today)
  from.setUTCDate(from.getUTCDate() - 6)
  return { from: isoDay(from), to: isoDay(today) }
}

/** ISO 8601(UTC) → `MM-DD HH:mm` (화면 명세 3.2) */
function formatTime(iso) {
  const matched = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso ?? '')
  if (!matched) return iso ?? '—'
  return `${matched[2]}-${matched[3]} ${matched[4]}:${matched[5]}`
}

async function loadList(resetPage = false) {
  if (resetPage) page.value = 0
  listLoading.value = true
  listError.value = ''
  try {
    const params = { page: page.value, size: PAGE_SIZE }
    if (filters.value.deptId !== '') params.deptId = Number(filters.value.deptId)
    if (filters.value.status !== '') params.status = filters.value.status
    if (filters.value.from !== '') params.from = startOfDayIso(filters.value.from)
    if (filters.value.to !== '') params.to = exclusiveEndIso(filters.value.to)

    // 목록 응답은 봉투다. 배열이 아니므로 res.items를 꺼내 쓴다 (C1)
    const envelope = await fetchInspections(params)
    expectField(
      Array.isArray(envelope.items) && typeof envelope.total === 'number',
      'AuditView',
      'GET /inspections 응답이 목록 봉투 {items,page,size,total}가 아닙니다 (계약서 C1)',
      envelope,
    )
    rows.value = envelope.items
    total.value = envelope.total
  } catch (err) {
    rows.value = []
    total.value = 0
    listError.value = errorText(err, '감사 목록을 불러오지 못했습니다.')
  } finally {
    listLoading.value = false
  }
}

async function loadDetail(inspectionId) {
  selectedId.value = inspectionId
  detailLoading.value = true
  detailError.value = ''
  notice.value = ''
  try {
    detail.value = await fetchInspection(inspectionId)
  } catch (err) {
    detail.value = null
    detailError.value = errorText(err, '판정 상세를 불러오지 못했습니다.')
  } finally {
    detailLoading.value = false
  }
}

function goPage(next) {
  if (next < 0 || next >= totalPages.value) return
  page.value = next
  loadList()
}

/* ── 상세 패널 ────────────────────────────────────────────── */

/**
 * 규칙 finding은 `findings[]`에 코드·카테고리·액션만 있고 의무·출처는 `ruleResult.matches[]`에 있다
 * (계약서 §1-5 / 인계 2). 코드로 이어 붙여 한 줄로 그린다.
 */
const ruleFindings = computed(() => {
  if (!detail.value) return []
  const matches = detail.value.ruleResult?.matches ?? []
  return detail.value.findings
    .filter((finding) => finding.source === 'RULE')
    .map((finding) => ({
      ...finding,
      match: matches.find((m) => m.code === finding.code) ?? null,
    }))
})

const aiFindings = computed(() =>
  detail.value ? detail.value.findings.filter((finding) => finding.source === 'AI') : [],
)

/** 화면 명세 3.5-3 — 모든 AI 후보 처리가 끝나면 최종 판정 배지를 상단에 표시한다 */
const humanDecided = computed(
  () => Boolean(detail.value) && detail.value.decidedBy === 'HUMAN',
)

async function onReview({ finding, reviewStatus }) {
  busyFindingId.value = finding.findingId
  notice.value = ''
  try {
    const result = await reviewFinding(detail.value.inspectionId, finding.findingId, reviewStatus)

    // 재조회 없이 패널과 목록 행을 즉시 갱신한다. PATCH 응답이 재산출된 상태를 함께 싣는다 (§1-7)
    const target = detail.value.findings.find((f) => f.findingId === result.findingId)
    if (target) {
      target.reviewStatus = result.reviewStatus
      target.reviewedBy = result.reviewedBy
      target.reviewedAt = result.reviewedAt
    }
    detail.value.finalDecision = result.inspection.finalDecision
    detail.value.status = result.inspection.status
    detail.value.decidedBy = result.inspection.decidedBy

    /*
     * D14 — 서버가 준 값을 그대로 쓰고 상태로 추론하지 않는다.
     * `submittedText`가 null인 것은 "마스킹본이 생성된 적이 없다"는 뜻이고 규칙 BLOCK 경로에서만
     * 발생한다. 사람이 확정한 BLOCK은 본문이 남으므로 `status`로는 두 경우를 구분할 수 없다.
     * 확정 직후 본문을 지우면 감사 담당자가 방금 판단한 근거가 화면에서 사라진다.
     */
    if (
      expectField(
        'submittedText' in result.inspection,
        'AuditView',
        'PATCH 응답의 inspection에 submittedText가 없습니다',
        result,
      )
    ) {
      detail.value.submittedText = result.inspection.submittedText
    }

    /*
     * 서버가 확정 시각으로 `completed_at`을 갱신한다 (QA F6).
     * 반영하지 않으면 이력 섹션에 "완료 07:25:07"과 "확정자 박OO 07:25:12"가
     * 서로 어긋난 시각으로 나란히 표시된다.
     */
    if (
      expectField(
        'completedAt' in result.inspection,
        'AuditView',
        'PATCH 응답의 inspection에 completedAt이 없습니다',
        result,
      )
    ) {
      detail.value.completedAt = result.inspection.completedAt
    }

    const row = rows.value.find((r) => r.inspectionId === result.inspection.inspectionId)
    if (row) {
      row.status = result.inspection.status
      row.decidedBy = result.inspection.decidedBy
    }
  } catch (err) {
    if (err.response?.status === 409) {
      // 다른 탭에서 먼저 처리된 경우다. 해당 건만 재조회한다 (화면 명세 3.5-4)
      notice.value = '이미 처리된 항목입니다. 최신 상태로 다시 불러왔습니다.'
      await loadDetail(detail.value.inspectionId)
    } else {
      notice.value = errorText(err, '확정 처리에 실패했습니다.')
    }
  } finally {
    busyFindingId.value = null
  }
}

onMounted(async () => {
  const range = defaultRange()
  filters.value.from = range.from
  filters.value.to = range.to
  await loadList()
})
</script>

<template>
  <div class="audit">
    <section class="filters">
      <label>
        <span class="caption">부서</span>
        <!-- 정보보안팀은 검토자 역할만 하므로 필터에 넣지 않는다 (D2) -->
        <select v-model="filters.deptId" @change="loadList(true)">
          <option value="">전체</option>
          <option v-for="dept in session.filterDepartments" :key="dept.deptId" :value="dept.deptId">
            {{ dept.name }}
          </option>
        </select>
      </label>

      <label>
        <span class="caption">상태</span>
        <select v-model="filters.status" @change="loadList(true)">
          <option value="">전체</option>
          <option v-for="option in STATUS_FILTER_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>

      <label>
        <span class="caption">기간 시작</span>
        <input v-model="filters.from" type="date" @change="loadList(true)" />
      </label>

      <label>
        <span class="caption">기간 종료</span>
        <input v-model="filters.to" type="date" @change="loadList(true)" />
      </label>

      <span class="grow" />
      <strong class="total">총 {{ total }}건</strong>
    </section>

    <div class="split">
      <section class="list">
        <p v-if="listError" class="error">{{ listError }}</p>

        <table>
          <thead>
            <tr>
              <th>시각</th>
              <th>부서</th>
              <th>사용자</th>
              <th>판정</th>
              <th class="num">규칙 수</th>
              <th>AI 상태</th>
              <th>확정</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="listLoading">
              <td colspan="7" class="placeholder caption">불러오는 중…</td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td colspan="7" class="placeholder caption">조건에 맞는 기록이 없습니다.</td>
            </tr>
            <template v-else>
              <tr
                v-for="row in rows"
                :key="row.inspectionId"
                class="row"
                :class="{ selected: row.inspectionId === selectedId }"
                @click="loadDetail(row.inspectionId)"
              >
                <td>{{ formatTime(row.createdAt) }}</td>
                <td>{{ row.department }}</td>
                <td>{{ row.userName }}</td>
                <td><StatusBadge :value="row.status" /></td>
                <td class="num">{{ row.ruleCount }}</td>
                <td class="caption">{{ term(AI_STATUS_TERMS, row.aiStatus, '') }}</td>
                <td class="caption">{{ term(DECIDED_BY_TERMS, row.decidedBy) }}</td>
              </tr>
            </template>
          </tbody>
        </table>

        <nav class="pager">
          <button type="button" :disabled="page === 0" @click="goPage(page - 1)">이전</button>
          <span class="caption">{{ page + 1 }} / {{ totalPages }}</span>
          <button type="button" :disabled="page + 1 >= totalPages" @click="goPage(page + 1)">
            다음
          </button>
        </nav>
      </section>

      <aside class="panel">
        <p v-if="!selectedId" class="placeholder caption">행을 선택하면 판정 상세가 표시됩니다.</p>
        <p v-else-if="detailLoading" class="placeholder caption">불러오는 중…</p>
        <p v-else-if="detailError" class="error">{{ detailError }}</p>

        <template v-else-if="detail">
          <header class="panel-head">
            <div class="panel-title">
              <span class="insp-id">#{{ detail.inspectionId }}</span>
              <StatusBadge :value="detail.status" />
              <StatusBadge v-if="humanDecided" :value="detail.finalDecision" prefix="최종 판정" />
            </div>
            <p class="caption">
              {{ detail.user.name }} · {{ detail.user.department }} ·
              {{ formatTime(detail.createdAt) }}
            </p>
          </header>

          <p v-if="notice" class="notice">{{ notice }}</p>

          <!-- 1. 원문 — 마스킹본만. 원문(original_text)은 응답에 없고 표시하지도 않는다 -->
          <section class="section">
            <h3 class="section-title">원문 (마스킹 적용본)</h3>
            <p v-if="detail.submittedText === null" class="empty caption">
              차단되어 전송 본문이 저장되지 않았습니다.
            </p>
            <p v-else class="body"><MaskedText :text="detail.submittedText" /></p>
          </section>

          <!-- 2. 규칙 판정 (결정) — CONFIRMED이므로 ACCEPT/REJECT를 노출하지 않는다 (D6) -->
          <section class="section">
            <h3 class="section-title">
              규칙 판정 <span class="tag decision">결정</span>
            </h3>
            <p v-if="ruleFindings.length === 0" class="empty caption">매칭된 규칙이 없습니다.</p>
            <ul v-else class="rules">
              <li v-for="finding in ruleFindings" :key="finding.findingId" class="rule">
                <span class="code">{{ finding.code }}</span>
                <span>{{ term(CATEGORY_TERMS, finding.category) }}</span>
                <span class="action" :class="`action-${(finding.action ?? '').toLowerCase()}`">
                  {{ term(ACTION_TERMS, finding.action) }}
                </span>
                <span class="grow" />
                <span v-if="finding.match" class="caption">
                  {{ term(OBLIGATION_TERMS, finding.match.obligation) }} ·
                  {{ finding.match.source }}
                </span>
                <StatusBadge :value="finding.reviewStatus" />
              </li>
            </ul>
          </section>

          <!-- 3. AI 제안 (후보) — SUGGESTED에만 버튼 -->
          <section class="section">
            <h3 class="section-title">
              AI 제안 <span class="tag candidate">후보</span>
              <span class="caption ai-status">{{ term(AI_STATUS_TERMS, detail.aiStatus, '') }}</span>
            </h3>

            <!--
              D16 — AI의 상태는 "분석"이다. "검토"는 사람의 절차에 예약된 단어이며(5.6),
              바로 위 배지가 "분석 중"인데 본문이 "AI 검토 중"이면 같은 섹션에서 어긋난다.
            -->
            <p v-if="detail.aiStatus === 'SKIPPED'" class="empty caption">
              규칙 판정으로 종결되어 AI 분석을 실행하지 않았습니다.
            </p>
            <p v-else-if="detail.aiStatus === 'PENDING'" class="empty caption">
              AI 분석이 진행 중입니다.
            </p>
            <p v-else-if="detail.aiStatus === 'FAILED'" class="empty caption">
              분석 실패 — 담당자 판단이 필요합니다.
            </p>
            <AiCandidateList
              v-else
              :findings="aiFindings"
              :assessment="detail.aiAssessment"
              :readonly="false"
              :busy-finding-id="busyFindingId"
              @review="onReview"
            />
          </section>

          <!-- 4. 이력 -->
          <section class="section">
            <h3 class="section-title">이력</h3>
            <dl class="history">
              <dt>정책 버전</dt>
              <dd>
                <span
                  v-for="policy in detail.policySnapshot.policies"
                  :key="policy.policyId"
                  class="policy"
                >
                  {{ policy.code }} v{{ policy.version }}
                </span>
              </dd>
              <dt>확정 주체</dt>
              <dd>{{ term(DECIDED_BY_TERMS, detail.decidedBy) }}</dd>
              <dt>확정자</dt>
              <dd>
                <template v-for="finding in aiFindings" :key="finding.findingId">
                  <span v-if="finding.reviewedBy" class="reviewer">
                    {{ finding.reviewedBy.name }} · {{ finding.reviewedAt }}
                  </span>
                </template>
                <span v-if="aiFindings.every((f) => !f.reviewedBy)">—</span>
              </dd>
              <dt>완료 시각</dt>
              <dd>{{ detail.completedAt ?? '—' }}</dd>
            </dl>
          </section>
        </template>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.audit {
  max-width: 1280px;
  margin: 0 auto;
  padding: 16px;
}

.filters {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--card);
}

.filters label {
  display: grid;
  gap: 3px;
}

.filters select,
.filters input {
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  background: #fff;
}

.grow {
  flex: 1;
}

.total {
  color: var(--navy);
}

.split {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
  align-items: start;
}

@media (max-width: 1080px) {
  .split {
    grid-template-columns: 1fr;
  }
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-caption);
}

th,
td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

th {
  color: var(--gray);
  font-weight: 600;
  border-bottom: 1px solid var(--border-strong);
}

.num {
  text-align: right;
}

.row {
  cursor: pointer;
  border-left: 3px solid transparent;
}

.row:hover {
  background: var(--card);
}

.row.selected {
  background: var(--card);
  border-left-color: var(--blue);
}

.placeholder {
  padding: 24px 10px;
  text-align: center;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px 0;
}

.pager button {
  padding: 4px 12px;
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  background: #fff;
  font-size: var(--font-caption);
}

.panel {
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #fff;
  position: sticky;
  top: 16px;
}

.panel-head {
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.insp-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 700;
}

.panel-head .caption {
  margin: 4px 0 0;
}

.notice {
  margin: 10px 0 0;
  padding: 8px 10px;
  border: 1px solid var(--amber);
  border-radius: 4px;
  color: var(--amber);
  background: var(--card);
  font-size: var(--font-caption);
}

.section {
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}

.section:last-child {
  border-bottom: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  font-size: var(--font-caption);
  font-weight: 700;
  color: var(--gray);
}

.tag {
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 700;
}

.tag.decision {
  color: #fff;
  background: var(--blue);
}

.tag.candidate {
  color: #fff;
  background: var(--purple);
}

.ai-status {
  margin-left: auto;
  font-weight: 500;
}

.body {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--card);
}

.empty {
  margin: 0;
}

.rules {
  list-style: none;
  margin: 0;
  padding: 0;
}

.rule {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 7px 0;
  border-bottom: 1px solid var(--border);
  font-size: var(--font-caption);
}

.rule:last-child {
  border-bottom: 0;
}

.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 700;
  color: var(--blue);
}

.action {
  padding: 1px 6px;
  border: 1px solid var(--border-strong);
  border-radius: 3px;
  color: var(--gray);
  font-weight: 600;
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

.history {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 4px 10px;
  margin: 0;
  font-size: var(--font-caption);
}

.history dt {
  color: var(--gray);
}

.history dd {
  margin: 0;
}

.policy {
  margin-right: 8px;
  color: var(--blue);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.reviewer {
  margin-right: 8px;
}

.error {
  margin: 0 0 10px;
  padding: 8px 10px;
  border: 1px solid var(--red);
  border-radius: 4px;
  color: var(--red);
  background: var(--card);
  font-size: var(--font-caption);
}
</style>
