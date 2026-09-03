<script setup>
import { useSessionStore } from '../stores/session'

/*
 * 공통 헤더 (기획서 5.2) — 높이 56px, 좌측 서비스명, 중앙 탭 2개, 우측 계정 전환.
 * 좌측 네비게이션은 두지 않는다.
 */
const session = useSessionStore()

function onSelect(event) {
  session.setCurrentUser(Number(event.target.value))
}

/*
 * AI 엔진 전환 (시연 전용). 화면·요청·응답이 전부 같고 백엔드 주소만 바뀐다 —
 * 그 사실 자체가 AiInspector가 유일한 교체 지점이라는 증거다 (기획서 9.6).
 * VITE_API_BASE_LLM이 없으면 셀렉터가 렌더되지 않는다.
 */
function onEngineSelect(event) {
  session.setEngine(event.target.value)
}
</script>

<template>
  <header class="app-header">
    <div class="brand">사내 AI 게이트웨이</div>

    <nav class="tabs">
      <RouterLink to="/chat" class="tab">챗</RouterLink>
      <RouterLink to="/admin/audit" class="tab">감사 콘솔</RouterLink>
    </nav>

    <div v-if="session.engineSwitchable" class="engine" :data-engine="session.engineId">
      <label class="sr-only" for="engine-select">AI 엔진 전환</label>
      <span aria-hidden="true" class="engine-dot" />
      <select id="engine-select" :value="session.engineId" @change="onEngineSelect">
        <option v-for="engine in session.engines" :key="engine.id" :value="engine.id">
          AI 엔진: {{ engine.label }}
        </option>
      </select>
    </div>

    <div class="account">
      <label class="sr-only" for="account-select">계정 전환</label>
      <select id="account-select" :value="session.currentUserId" @change="onSelect">
        <option v-for="user in session.users" :key="user.userId" :value="user.userId">
          {{ user.name }} · {{ user.department.name }}
        </option>
      </select>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  height: var(--header-h);
  padding: 0 20px;
  background: var(--navy);
  color: #fff;
}

.brand {
  font-weight: 700;
  letter-spacing: -0.01em;
}

.tabs {
  display: flex;
  gap: 4px;
  margin: 0 auto 0 12px;
}

.tab {
  padding: 6px 14px;
  border-radius: 4px;
  color: rgba(255, 255, 255, 0.72);
  text-decoration: none;
  font-size: var(--font-body);
}

.tab:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}

.tab.router-link-active {
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  font-weight: 600;
}

.engine {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 지금 어느 엔진에 붙어 있는지 한눈에 보여야 한다. 시연 중 잘못 말하면 곤란하다. */
.engine-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.45);
}

.engine[data-engine='llm'] .engine-dot {
  background: #4ade80;
}

.engine select {
  height: 32px;
  padding: 0 8px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: var(--font-caption);
}

.engine select option {
  color: var(--navy);
}

.account select {
  height: 32px;
  padding: 0 8px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: var(--font-caption);
}

.account select option {
  color: var(--navy);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
