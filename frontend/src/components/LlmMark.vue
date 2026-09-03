<script setup>
/*
 * 상용 서비스 마크.
 *
 * 윤곽선은 각 사 로고 이미지를 추적한 것이다 (`llmMarkPaths.js`).
 *
 * Gemini만 칠하는 방식이 다르다. 원본은 위 빨강·왼 노랑·아래 초록·오른 파랑으로
 * 색이 한 바퀴 도는데, SVG 선형 그라디언트로는 축이 하나뿐이라 이 회전을 담지
 * 못한다. 그래서 같은 별을 두 번 그리고 왼쪽 반과 오른쪽 반에 세로 그라디언트를
 * 따로 걸었다 — 가운데 색만 노랑/파랑으로 갈린다.
 */
import { computed } from 'vue'
import { CHATGPT_MARK, CLAUDE_MARK, GEMINI_MARK, GROK_MARK } from './llmMarkPaths'

const props = defineProps({
  id: { type: String, required: true },
  size: { type: Number, default: 13 },
})

const MARKS = {
  chatgpt: { d: CHATGPT_MARK, color: '#0f172a' },
  claude: { d: CLAUDE_MARK, color: '#d97757' },
  grok: { d: GROK_MARK, color: '#0f172a' },
  gemini: {
    d: GEMINI_MARK,
    split: {
      left: ['#ea4335', '#fbbc04', '#34a853'],
      right: ['#ea4335', '#4285f4', '#34a853'],
    },
  },
}

const mark = computed(() => MARKS[props.id] ?? MARKS.chatgpt)
/** id가 문서 안에서 겹치지 않게 서비스 이름으로 고정한다 */
const uid = computed(() => `llm-${props.id}`)
const stopOffset = (i, n) => `${(i / (n - 1)) * 100}%`
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
    <template v-if="mark.split">
      <defs>
        <linearGradient
          v-for="(stops, side) in mark.split"
          :id="`${uid}-${side}`"
          :key="side"
          x1="0"
          y1="0"
          x2="0"
          y2="1"
        >
          <stop
            v-for="(c, i) in stops"
            :key="c"
            :offset="stopOffset(i, stops.length)"
            :stop-color="c"
          />
        </linearGradient>
        <clipPath :id="`${uid}-clip-left`"><rect x="0" y="0" width="8" height="16" /></clipPath>
        <clipPath :id="`${uid}-clip-right`"><rect x="8" y="0" width="8" height="16" /></clipPath>
      </defs>
      <path
        v-for="side in ['left', 'right']"
        :key="side"
        :d="mark.d"
        :fill="`url(#${uid}-${side})`"
        :clip-path="`url(#${uid}-clip-${side})`"
        fill-rule="evenodd"
      />
    </template>
    <path v-else :d="mark.d" :fill="mark.color" fill-rule="evenodd" />
  </svg>
</template>

<style scoped>
.mark {
  display: block;
  flex: none;
}
</style>
