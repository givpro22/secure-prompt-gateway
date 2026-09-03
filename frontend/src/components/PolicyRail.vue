<script setup>
/*
 * 우측 레일 — 이 부서에 적용되는 정책 목록.
 *
 * 건수도 목록도 GET /policies?deptId= 응답을 그대로 쓴다 (D8, 하드코딩 금지).
 * 계정을 바꾸면 이 레일이 바뀌는 것이 부서별 N:M 적용의 증명이며,
 * 데모 Case B/C에서 같은 문장이 갈리는 이유가 여기 눈에 보인다.
 */
import { useSessionStore } from '../stores/session'
import { APPLIED_VIA_TERMS, CATEGORY_TERMS, term } from '../lib/terms'

const session = useSessionStore()
</script>

<template>
  <aside class="rail">
    <section class="block">
      <h2>적용 정책</h2>
      <p v-if="!session.policiesLoaded" class="loading caption">불러오는 중…</p>
      <template v-else>
        <p class="count">
          {{ session.currentDeptName }} · <strong>{{ session.policies.length }}건</strong>
        </p>
        <ul class="policies">
          <li v-for="policy in session.policies" :key="policy.policyId" class="policy">
            <span class="dot" :class="`cat-${policy.category.toLowerCase()}`" aria-hidden="true" />
            <span class="body">
              <span class="pname">{{ policy.name }}</span>
              <span class="pmeta">
                {{ policy.code }} v{{ policy.version }} ·
                {{ term(CATEGORY_TERMS, policy.category) }} ·
                규칙 {{ policy.rules.length }}종
              </span>
            </span>
            <span class="via">{{ term(APPLIED_VIA_TERMS, policy.appliedVia) }}</span>
          </li>
        </ul>
        <p class="owner caption">
          소유 부서가 표시된 정책은 그 부서가 정하고 다른 부서가 적용받습니다.
        </p>
      </template>
    </section>
  </aside>
</template>

<style scoped>
.rail {
  width: 288px;
  flex: none;
  padding: 18px 16px;
  border-left: 1px solid var(--border);
  background: var(--card);
}

.block h2 {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--gray);
}

.count {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--navy);
}

.policies {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.policy {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #fff;
}

.dot {
  width: 7px;
  height: 7px;
  margin-top: 5px;
  border-radius: 50%;
  flex: none;
}

.cat-pii {
  background: var(--amber);
}
.cat-secret {
  background: var(--red);
}
.cat-confidential {
  background: var(--blue);
}
.cat-embargo {
  background: var(--purple);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.pname {
  font-size: 12.5px;
  color: var(--navy);
  line-height: 1.4;
}

.pmeta {
  font-size: 11px;
  color: var(--gray);
  font-variant-numeric: tabular-nums;
}

.via {
  flex: none;
  font-size: 10.5px;
  color: var(--gray);
  white-space: nowrap;
}

.owner {
  margin: 10px 0 0;
  line-height: 1.55;
}
</style>
