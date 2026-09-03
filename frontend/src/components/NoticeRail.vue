<script setup>
/*
 * 우측 알림 레일 — 이 서비스를 쓰는 직원이 알아둘 소식.
 *
 * **지어내지 않는다.** 항목은 전부 GET /policies?deptId= 응답에서 파생된다.
 * 엠바고 해제일은 규칙의 embargoUntil, 등록일은 정책의 registeredAt, 적용 경로는
 * appliedVia다. 계정을 바꾸면 이 목록이 바뀌는 것이 부서별 적용의 증명이다.
 *
 * 하단 "정책 기준"은 적용 정책 중 가장 최근 등록일이다. 버전 정수(v5)는 판정
 * 스냅샷 대조용이지 사람이 읽는 값이 아니라 날짜로 보여준다.
 */
import { computed, ref } from 'vue'
import { useSessionStore } from '../stores/session'

/*
 * 접힘 상태를 모듈 스코프에 둔다. 챗 ↔ 감사 콘솔을 오갈 때 컴포넌트가 다시 마운트되는데,
 * 그때마다 레일이 다시 펼쳐지면 접어 둔 의미가 없다. 새로고침하면 초기화된다.
 */
const collapsed = ref(false)

const session = useSessionStore()

const notices = computed(() => {
  const out = []
  for (const policy of session.policies) {
    for (const rule of policy.rules ?? []) {
      if (!rule.embargoUntil) continue
      out.push({
        key: `emb-${rule.code}`,
        kind: '엠바고',
        tone: 'purple',
        date: rule.embargoUntil,
        title: rule.description ?? rule.code,
        body: `${rule.embargoUntil}부터 공개할 수 있습니다. 그전에는 외부 AI로 보낼 수 없습니다.`,
        source: rule.source,
      })
    }
    if (policy.registeredAt) {
      out.push({
        key: `pol-${policy.policyId}`,
        kind: policy.appliedVia === 'DEPT' ? '부서 적용' : '전사 적용',
        tone: policy.appliedVia === 'DEPT' ? 'blue' : 'gray',
        date: policy.registeredAt,
        title: `${policy.name} v${policy.version}`,
        body:
          policy.appliedVia === 'DEPT'
            ? `${policy.ownerDept ?? '소유 부서'}가 정한 정책이 우리 부서에 적용됩니다. 규칙 ${policy.rules?.length ?? 0}종.`
            : `전사 공통으로 적용됩니다. 규칙 ${policy.rules?.length ?? 0}종.`,
        source: null,
      })
    }
  }
  return out.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
})

/*
 * 정책 세트 리비전. 빌드 시점에 마이그레이션 개수를 세어 넣는다(vite.config.js).
 * 정책·규칙이 바뀔 때마다 마이그레이션이 하나 늘고 그게 커밋 하나라, 이 숫자는
 * 커밋 이력을 따라간다. 개별 정책의 version(P-PII v5)과는 다른 축이다.
 */
const revision = __POLICY_REVISION__

const baseDate = computed(() => {
  const dates = session.policies.map((p) => p.registeredAt).filter(Boolean)
  return dates.length === 0 ? null : dates.slice().sort().at(-1)
})
</script>

<template>
  <!-- 접었을 때는 다시 펼 수 있는 얇은 띠만 남긴다 -->
  <aside v-if="collapsed" class="rail collapsed">
    <button type="button" class="toggle" aria-label="알아둘 소식 펼치기" @click="collapsed = false">
      ‹
    </button>
    <span class="spine" aria-hidden="true">알아둘 소식</span>
    <span v-if="notices.length > 0" class="count" aria-hidden="true">{{ notices.length }}</span>
  </aside>

  <aside v-else class="rail">
    <div class="rail-head">
      <h2>알아둘 소식</h2>
      <button type="button" class="toggle" aria-label="알아둘 소식 접기" @click="collapsed = true">
        ›
      </button>
    </div>
    <p class="sub">{{ session.currentDeptName }}에 적용되는 정책에서 나온 것만 보여줍니다.</p>

    <p v-if="!session.policiesLoaded" class="loading caption">불러오는 중…</p>

    <ul v-else class="notices">
      <li v-for="n in notices" :key="n.key" class="notice" :class="`tone-${n.tone}`">
        <span class="head">
          <span class="kind">{{ n.kind }}</span>
          <span class="date">{{ n.date }}</span>
        </span>
        <span class="title">{{ n.title }}</span>
        <span class="body">{{ n.body }}</span>
        <span v-if="n.source" class="source">{{ n.source }}</span>
      </li>
    </ul>

    <footer class="base">
      <span class="base-row">
        <span class="base-key">정책 버전</span>
        <span class="base-val">v{{ revision }}</span>
      </span>
      <span v-if="baseDate" class="base-row">
        <span class="base-key">기준일</span>
        <span class="base-val">{{ baseDate }}</span>
      </span>
    </footer>
  </aside>
</template>

<style scoped>
.rail.collapsed {
  width: 44px;
  align-items: center;
  gap: 10px;
  padding: 14px 6px;
}

.spine {
  writing-mode: vertical-rl;
  font-size: 12.5px;
  letter-spacing: 0.06em;
  color: var(--gray);
}

.count {
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--border-strong);
  color: var(--navy);
  font-size: 11px;
  font-weight: 700;
}

.rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.toggle {
  flex: none;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  background: #fff;
  color: var(--gray);
  font: inherit;
  font-size: 15px;
  line-height: 1;
}

.toggle:hover {
  border-color: var(--blue);
  color: var(--blue);
}

.rail {
  width: 296px;
  flex: none;
  padding: 18px 16px;
  border-left: 1px solid var(--border);
  background: var(--card);
  display: flex;
  flex-direction: column;
  gap: 4px;
  /* 목록이 길어져도 "정책 기준"이 잘리지 않게 레일이 스스로 스크롤한다. */
  height: 100%;
  overflow: hidden;
}

h2 {
  margin: 0;
  flex: 1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--gray);
}

.sub {
  margin: 0 0 10px;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--gray);
}

.notices {
  margin: 0;
  padding: 0 2px 0 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.notice {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 11px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--gray);
  border-radius: 6px;
  background: #fff;
}

.tone-purple {
  border-left-color: var(--purple);
}
.tone-blue {
  border-left-color: var(--blue);
}
.tone-gray {
  border-left-color: var(--border-strong);
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.kind {
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--gray);
}

.tone-purple .kind {
  color: var(--purple);
}
.tone-blue .kind {
  color: var(--blue);
}

.date {
  font-size: 12px;
  color: var(--gray);
  font-variant-numeric: tabular-nums;
}

.title {
  font-size: 13.5px;
  line-height: 1.45;
  color: var(--navy);
}

.body {
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--gray);
}

.source {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--gray);
}

.base {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--border-strong);
  font-size: 12.5px;
  color: var(--gray);
  font-variant-numeric: tabular-nums;
}

.base-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.base-val {
  color: var(--navy);
  font-weight: 600;
}
</style>
