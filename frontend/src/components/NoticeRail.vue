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
import NotificationBell from './NotificationBell.vue'

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
 * 정책 버전. 빌드 시점의 git 커밋에서 온다(vite.config.js).
 * 화면에서 본 값으로 저장소의 그 시점을 바로 찾을 수 있다.
 *
 * 개별 정책의 version(P-PII v5)과는 다른 축이다. 저건 정책 하나의 개정 횟수이고
 * 판정 스냅샷 대조에 쓰인다.
 */
const gitVersion = __GIT_VERSION__
const versionText = gitVersion.sha ? `v${gitVersion.count} · ${gitVersion.sha}` : null

const baseDate = computed(() => {
  const dates = session.policies.map((p) => p.registeredAt).filter(Boolean)
  return dates.length === 0 ? null : dates.slice().sort().at(-1)
})
</script>

<template>
  <!--
    접힘·펼침을 v-if로 갈아끼우면 폭이 순간이동한다. aside 하나를 두고 width만
    보간하고, 안쪽 내용은 페이드로 교차시킨다.
  -->
  <aside class="rail" :class="{ collapsed }">
    <!-- 종은 접어도 남는다. 알림은 소식과 달리 접어 둘 수 있는 것이 아니다 -->
    <NotificationBell />

    <div class="panel" :aria-hidden="collapsed">
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
        <span v-if="versionText" class="base-row">
          <span class="base-key">정책 버전</span>
          <span class="base-val">{{ versionText }}</span>
        </span>
        <span v-if="baseDate" class="base-row">
          <span class="base-key">기준일</span>
          <span class="base-val">{{ baseDate }}</span>
        </span>
      </footer>
    </div>

    <button
      type="button"
      class="reopen"
      :tabindex="collapsed ? 0 : -1"
      :aria-hidden="!collapsed"
      @click="collapsed = false"
    >
      <span class="arrow" aria-hidden="true">‹</span>
      알아둘 소식
    </button>
  </aside>
</template>

<style scoped>
.rail.collapsed {
  /* 접으면 패널이 아니라 글자만 남는다. 배경·테두리가 남으면 본문 옆에 색 다른
     탭이 붙은 것처럼 보인다 */
  width: 152px;
  background: transparent;
  border-left-color: transparent;
}

/* 띠 전체가 버튼이다. 어디를 눌러도 펼쳐진다 */
.reopen {
  position: absolute;
  /* 종 아래로. 접힌 상태에서도 종은 남으므로 그 자리를 비켜야 한다 */
  top: 58px;
  left: 0;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--gray);
  font: inherit;
  font-size: 15.5px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 200ms ease;
}

.reopen:hover {
  color: var(--blue);
  background: color-mix(in srgb, var(--blue) 6%, transparent);
}

.arrow {
  font-size: 24px;
  line-height: 1;
}

.rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.toggle {
  /* 화살표만 남긴다. 테두리와 배경을 두면 그것부터 눈에 들어온다 */
  flex: none;
  padding: 0 2px;
  border: 0;
  background: transparent;
  color: var(--gray);
  font: inherit;
  font-size: 26px;
  line-height: 1;
}

.toggle:hover {
  border-color: var(--blue);
  color: var(--blue);
}

.rail {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 336px;
  flex: none;
  padding: 24px 22px;
  border-left: 1px solid var(--border);
  background: var(--card);
  height: 100%;
  overflow: hidden;
  transition:
    width 340ms cubic-bezier(0.22, 0.61, 0.36, 1),
    background-color 300ms ease,
    border-left-color 300ms ease;
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
  gap: 4px;
  padding: 13px 14px;
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
  font-size: 14.5px;
  line-height: 1.5;
  color: var(--navy);
}

.body {
  font-size: 13px;
  line-height: 1.6;
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
.panel {
  flex: 1;
  /* 폭이 줄어드는 동안 안쪽이 다시 흐르지 않게 너비를 고정한다. 그래야 접히는 것이
     글자가 재배치되는 게 아니라 패널이 미끄러지는 것으로 보인다 */
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 292px;
  min-height: 0;
  opacity: 1;
  transition: opacity 200ms ease 90ms;
}
.rail.collapsed .panel {
  opacity: 0;
  pointer-events: none;
  transition-delay: 0ms;
}
.rail.collapsed .reopen {
  opacity: 1;
  pointer-events: auto;
  transition-delay: 140ms;
}
@media (prefers-reduced-motion: reduce) {
  .rail,
  .panel,
  .reopen {
    transition: none;
  }
}
</style>
