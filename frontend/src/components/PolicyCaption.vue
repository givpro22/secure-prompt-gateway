<script setup>
import { useSessionStore } from '../stores/session'

/*
 * SCR-01 하단 캡션 — "부서: 개발팀 · 적용 정책 2건".
 * 건수는 GET /policies?deptId= 응답 배열 길이를 그대로 쓴다 (D8). 하드코딩하지 않는다.
 * 개발팀은 2건(P-PII·P-SEC), 영업·인사팀은 3건(+P-CONF)이 되는 것이
 * 부서별 N:M 적용의 증명이다 (기획서 7.3).
 */
const session = useSessionStore()
</script>

<template>
  <p class="policy-caption caption">
    <template v-if="session.policiesLoaded">
      부서: {{ session.currentDeptName }} · 적용 정책 {{ session.policies.length }}건
      <span class="codes">({{ session.policies.map((p) => p.code).join(', ') }})</span>
    </template>
    <template v-else> 적용 정책을 불러오는 중… </template>
  </p>
</template>

<style scoped>
.policy-caption {
  margin: 6px 2px 0;
}

.codes {
  color: var(--blue);
}
</style>
