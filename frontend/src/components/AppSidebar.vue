<script setup>
/*
 * 좌측 사이드바 — 브랜드 · 네비 · 계정 전환 · 판정 책임 구조.
 *
 * 계정 전환 드롭다운이 여기 있는 이유는 **로그인이 없기 때문**이다 (기획서 0.3).
 * 인증을 만들지 않고 X-User-Id 헤더로 식별하며, 부서에 따라 판정이 갈리는 것이
 * 데모의 핵심이라 계정 전환은 상시 보이는 자리에 둔다.
 *
 * 하단 "판정 책임 구조"는 장식이 아니다. 4장의 책임 경계가 이 프로젝트의 핵심
 * 주장이고, 화면 어디에서나 그것이 보이는 것이 주장의 일부다.
 */
import { useSessionStore } from '../stores/session'

const session = useSessionStore()

function onSelect(event) {
  session.setCurrentUser(Number(event.target.value))
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="mark" aria-hidden="true">SP</span>
      <span class="name">
        <strong>Secure Prompt</strong>
        <em>Gateway</em>
      </span>
    </div>

    <nav class="nav">
      <RouterLink to="/chat" class="nav-item">직원 AI 챗</RouterLink>
      <RouterLink to="/admin/audit" class="nav-item">관리자 감사 콘솔</RouterLink>
    </nav>

    <div class="account">
      <label class="label" for="account-select">계정 전환</label>
      <select id="account-select" :value="session.currentUserId" @change="onSelect">
        <option v-for="user in session.users" :key="user.userId" :value="user.userId">
          {{ user.name }} · {{ user.department.name }}
        </option>
      </select>
      <p class="hint">로그인은 구현하지 않습니다. 부서에 따라 판정이 갈립니다.</p>
    </div>

    <div class="spacer" />

    <section class="responsibility">
      <h2>판정 책임 구조</h2>
      <ul>
        <li><span class="who rule">규칙 엔진</span>은 결정</li>
        <li><span class="who ai">AI</span>는 제안</li>
        <li><span class="who human">사람</span>은 확정</li>
      </ul>
    </section>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 232px;
  flex: none;
  height: 100vh;
  position: sticky;
  top: 0;
  padding: 18px 16px;
  border-right: 1px solid var(--border);
  background: var(--card);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--navy);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.name {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.name strong {
  font-size: 14px;
  color: var(--navy);
}

.name em {
  font-style: normal;
  font-size: var(--font-caption);
  color: var(--gray);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav-item {
  padding: 8px 10px;
  border-radius: 6px;
  color: var(--navy);
  text-decoration: none;
  font-size: 13.5px;
}

.nav-item:hover {
  background: #fff;
}

.nav-item.router-link-active {
  background: #fff;
  border: 1px solid var(--border-strong);
  font-weight: 600;
}

.account {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--gray);
}

select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  background: #fff;
  font: inherit;
  font-size: 13px;
}

.hint {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--gray);
}

.spacer {
  flex: 1;
}

.responsibility {
  padding-top: 14px;
  border-top: 1px solid var(--border-strong);
}

.responsibility h2 {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--gray);
}

.responsibility ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12.5px;
  color: var(--navy);
}

.who {
  font-weight: 700;
}

.who.rule {
  color: var(--blue);
}
.who.ai {
  color: var(--purple);
}
.who.human {
  color: var(--green);
}
</style>
