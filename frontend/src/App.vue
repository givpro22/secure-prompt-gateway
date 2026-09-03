<script setup>
import { onMounted } from 'vue'
import AppSidebar from './components/AppSidebar.vue'
import AppHeader from './components/AppHeader.vue'
import { useSessionStore } from './stores/session'

const session = useSessionStore()
const apiBase = import.meta.env.VITE_API_BASE

onMounted(() => {
  // 계정 전환 드롭다운과 정책 레일이 쓰는 마스터 데이터
  session.loadDirectory()
})
</script>

<template>
  <div class="shell">
    <AppSidebar />
    <div class="column">
      <AppHeader />
      <p v-if="session.loadError" class="load-error">
        {{ session.loadError }}
        <span class="caption">API_BASE: {{ apiBase }}</span>
      </p>
      <main>
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
/*
 * 화면 전체를 뷰포트에 가둔다. 예전엔 calc(100vh - var(--header-h))로 본문 높이를
 * 잡았는데 헤더가 그 토큰보다 커져서 아래가 잘렸다. 헤더 높이를 상수로 가정하지 않고
 * flex로 남는 만큼 준다.
 */
.shell {
  display: flex;
  align-items: stretch;
  height: 100vh;
  overflow: hidden;
}

.column {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

main {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

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
