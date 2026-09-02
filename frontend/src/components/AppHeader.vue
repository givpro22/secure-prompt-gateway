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
</script>

<template>
  <header class="app-header">
    <div class="brand">사내 AI 게이트웨이</div>

    <nav class="tabs">
      <RouterLink to="/chat" class="tab">챗</RouterLink>
      <RouterLink to="/admin/audit" class="tab">감사 콘솔</RouterLink>
    </nav>

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
