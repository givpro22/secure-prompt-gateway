<script setup>
/*
 * 상용 서비스 마크.
 *
 * 각 서비스 로고를 단순화한 도형이며 **원본 브랜드 자산이 아니다** — 목록에서
 * 서비스를 알아보게 하는 용도다. 정식 배포물에는 각 사 브랜드 가이드의 자산을
 * 받아 교체해야 한다.
 */
import { computed } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  size: { type: Number, default: 13 },
})

const MARKS = {
  chatgpt: {
    color: '#111827',
    paths: [
      // 육각 매듭 로제트 외곽
      {
        d: 'M8 1.9a2.9 2.9 0 0 1 2.5 1.45 2.9 2.9 0 0 1 3.55 4.15 2.9 2.9 0 0 1-2.5 4.35 2.9 2.9 0 0 1-5.1 0A2.9 2.9 0 0 1 1.95 7.5 2.9 2.9 0 0 1 5.5 3.35 2.9 2.9 0 0 1 8 1.9Z',
        stroke: true,
      },
      // 매듭이 겹치는 안쪽 육각형
      { d: 'M8 5.2 10.4 6.6v2.8L8 10.8 5.6 9.4V6.6L8 5.2Z', stroke: true },
    ],
  },
  claude: {
    color: '#d97757',
    paths: [
      // 길이가 제각각인 방사형 다발
      {
        d: 'M7.4 1.2h1.2l.35 5.1 2.5-3.5.95.7-1.95 4 4.1-1.6.42 1.1-4.4 1.1 4.15.75-.2 1.15-4.2-.6 3.25 3.1-.85.85-2.9-3.4.55 4.4-1.2.2-.85-4.5-1.5 4.2-1.1-.45 1.35-4.3-3.2 2.9-.8-.9 3.35-3-4.4 1.05-.3-1.15 4.35-.85-4.5-.6.1-1.2 4.6.2-3.6-2.5.7-.95 3.9 2.4-1.7-4.2Z',
      },
    ],
  },
  gemini: {
    // 빨강 → 노랑 → 초록 → 파랑
    gradient: ['#ea4335', '#fbbc04', '#34a853', '#4285f4'],
    paths: [
      // 오목한 네 꼭짓점 별
      { d: 'M8 0c.62 4.4 3.6 7.38 8 8-4.4.62-7.38 3.6-8 8-.62-4.4-3.6-7.38-8-8 4.4-.62 7.38-3.6 8-8Z' },
    ],
  },
  grok: {
    color: '#111827',
    paths: [
      // 고리
      {
        d: 'M8 2.5a5.5 5.5 0 1 1 0 11 5.5 5.5 0 0 1 0-11Zm0 1.7a3.8 3.8 0 1 0 0 7.6 3.8 3.8 0 0 0 0-7.6Z',
        evenodd: true,
      },
      // 고리를 가로지르는 뾰족한 사선
      { d: 'M15.6.4 6.1 8.9.4 15.6l8.2-6.4L15.6.4Z' },
    ],
  },
}

const mark = computed(() => MARKS[props.id] ?? MARKS.chatgpt)
/** 그라디언트 id가 문서 안에서 겹치지 않게 서비스 이름으로 고정한다 */
const gradientId = computed(() => `llm-grad-${props.id}`)
</script>

<template>
  <svg
    class="mark"
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    aria-hidden="true"
    focusable="false"
  >
    <defs v-if="mark.gradient">
      <linearGradient :id="gradientId" x1="0" y1="0" x2="1" y2="1">
        <stop
          v-for="(c, i) in mark.gradient"
          :key="c"
          :offset="`${(i / (mark.gradient.length - 1)) * 100}%`"
          :stop-color="c"
        />
      </linearGradient>
    </defs>
    <path
      v-for="(p, i) in mark.paths"
      :key="i"
      :d="p.d"
      :fill="p.stroke ? 'none' : mark.gradient ? `url(#${gradientId})` : mark.color"
      :stroke="p.stroke ? mark.color : 'none'"
      :stroke-width="p.stroke ? 1.25 : 0"
      stroke-linejoin="round"
      :fill-rule="p.evenodd ? 'evenodd' : 'nonzero'"
    />
  </svg>
</template>

<style scoped>
.mark {
  display: block;
  flex: none;
}
</style>
