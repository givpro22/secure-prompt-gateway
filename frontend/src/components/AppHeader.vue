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
      <span class="live">
        <span class="dot" aria-hidden="true" />
        게이트웨이 보호 활성
      </span>
      <span v-if="session.policiesLoaded" class="meta">
        적용 정책 <strong>{{ session.policies.length }}</strong> · 규칙
        <strong>{{ ruleCount }}</strong>종
      </span>
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding: 22px 30px;
  border-bottom: 1px solid var(--border-strong);
  background: var(--page-bg);
}

.titles h1 {
  margin: 0;
  font-size: 25px;
  letter-spacing: -0.02em;
  color: var(--navy);
}

.subtitle {
  margin: 6px 0 0;
  font-size: 14.5px;
  color: var(--gray);
}

.status {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 14.5px;
}

/* 알약 테두리를 걷어냈다. 상태는 점과 글자로 충분하고, 둥근 배지를 늘어놓으면
   화면이 실제 정보보다 장식처럼 읽힌다 */
.live {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--green);
  font-weight: 600;
}

.meta {
  padding-left: 18px;
  border-left: 1px solid var(--border-strong);
  color: var(--gray);
  font-variant-numeric: tabular-nums;
}

.meta strong {
  color: var(--navy);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
}
</style>
