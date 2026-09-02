<script setup>
import { onMounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import { useSessionStore } from './stores/session'

const session = useSessionStore()
const apiBase = import.meta.env.VITE_API_BASE

onMounted(() => {
  // 계정 전환 드롭다운과 정책 캡션이 쓰는 마스터 데이터
  session.loadDirectory()
})
</script>

<template>
  <AppHeader />
  <p v-if="session.loadError" class="load-error">
    {{ session.loadError }}
    <span class="caption">API_BASE: {{ apiBase }}</span>
  </p>
  <main>
    <RouterView />
  </main>
</template>

<style scoped>
.load-error {
  margin: 0;
  padding: 10px 16px;
  border-bottom: 1px solid var(--red);
  color: var(--red);
  background: var(--card);
  font-size: var(--font-caption);
}

.load-error .caption {
  margin-left: 8px;
}
</style>
