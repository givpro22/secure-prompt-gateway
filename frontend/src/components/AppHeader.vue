<script setup>
/*
 * 본문 상단 바 — 화면 제목과 게이트웨이 상태.
 *
 * 브랜드·네비·계정 전환은 사이드바로 옮겼다. 여기 남는 것은 "지금 어느 화면이고
 * 무엇이 보장되는가"다.
 *
 * 우측 규칙 수는 GET /policies 응답에서 센다. 단일 전역 정책 버전 같은 것은 두지
 * 않는다 — 우리 스키마는 정책마다 version이 따로이고(P-PII v5 · P-SEC v7 …),
 * 그 값이 판정 스냅샷에 시점 보존된다. 하나로 뭉뚱그리면 그 설계가 화면에서 사라진다.
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from '../stores/session'

const route = useRoute()
const session = useSessionStore()

const PAGES = {
  chat: {
    title: '직원 AI 챗',
    subtitle: '모든 프롬프트는 전송 전 부서 정책으로 검사됩니다.',
  },
  audit: {
    title: '관리자 감사 콘솔',
    subtitle: '원문은 화면에 표시하지 않습니다. 마스킹 본문 기준 감사.',
  },
}

const page = computed(() => PAGES[route.name] ?? PAGES.chat)

const ruleCount = computed(() =>
  session.policies.reduce((n, p) => n + (p.rules?.length ?? 0), 0),
)
</script>

<template>
  <header class="page-header">
    <div class="titles">
      <h1>{{ page.title }}</h1>
      <p class="subtitle">{{ page.subtitle }}</p>
    </div>

    <div class="status">
      <span class="pill live">
        <span class="dot" aria-hidden="true" />
        게이트웨이 보호 활성
      </span>
      <span v-if="session.policiesLoaded" class="pill">
        정책 {{ session.policies.length }} · 규칙 {{ ruleCount }}종
      </span>
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 22px;
  border-bottom: 1px solid var(--border);
  background: var(--page-bg);
}

.titles h1 {
  margin: 0;
  font-size: 17px;
  color: var(--navy);
}

.subtitle {
  margin: 3px 0 0;
  font-size: var(--font-caption);
  color: var(--gray);
}

.status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 11px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  color: var(--gray);
  font-variant-numeric: tabular-nums;
}

.pill.live {
  border-color: var(--green);
  color: var(--green);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
}
</style>
