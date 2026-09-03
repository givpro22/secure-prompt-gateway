<script setup>
/*
 * 빈 상태에 놓는 시나리오 카드 — 데모 케이스 A~D를 그대로 버튼으로 만든 것.
 *
 * 문자열은 기획서 10.4 원본이다. **한 글자도 바꾸지 않는다** — span 기대값이 길이에
 * 걸려 있고, 발표 현장에서 이 버튼이 붙여넣기를 대신한다.
 *
 * 각 카드에 필요한 계정을 함께 적는다. 계정이 다르면 결과가 갈리는 것이 데모의
 * 핵심인데, 버튼만 있고 계정 안내가 없으면 현장에서 엉뚱한 판정이 나온다.
 */
const emit = defineEmits(['pick'])

const SCENARIOS = [
  {
    decision: 'ALLOW',
    label: '허용',
    account: '이OO · 개발팀',
    text: 'A사 차세대 프로젝트 오픈 일정이 언제였지?',
    note: '개발팀에는 고객사 정책이 매핑되지 않아 그대로 전송',
  },
  {
    decision: 'MASK',
    label: '마스킹',
    account: '정OO · 인사팀',
    text: '지원자 연락처 010-1234-5678 로 면접 안내 문자 초안 써줘',
    note: '개인정보를 라벨로 치환한 본문만 전송',
  },
  {
    decision: 'BLOCK',
    label: '차단',
    account: '이OO · 개발팀',
    text: '이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나',
    note: '자격증명 포함 · 전송하지 않음',
  },
  {
    decision: 'PENDING',
    label: '검토 대기',
    account: '김OO · 영업팀',
    text: 'A사 차세대 프로젝트 오픈 일정이 언제였지?',
    note: '같은 문장인데 영업팀이라 갈림 · AI 후보 제안 후 담당자 확정',
  },
]
</script>

<template>
  <section class="scenarios">
    <h2>시나리오로 시작하기</h2>
    <div class="grid">
      <button
        v-for="s in SCENARIOS"
        :key="s.decision"
        type="button"
        class="card"
        :class="`d-${s.decision.toLowerCase()}`"
        @click="emit('pick', s.text)"
      >
        <span class="head">
          <span class="dot" aria-hidden="true" />
          <span class="label">{{ s.label }}</span>
          <span class="account">{{ s.account }}</span>
        </span>
        <span class="text">{{ s.text }}</span>
        <span class="note">{{ s.note }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.scenarios {
  margin-top: 22px;
}

.scenarios h2 {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--gray);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(268px, 1fr));
  gap: 10px;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--gray);
  border-radius: 6px;
  background: #fff;
  text-align: left;
  font: inherit;
}

.card:hover {
  border-color: var(--border-strong);
}

.d-allow {
  border-left-color: var(--green);
}
.d-mask {
  border-left-color: var(--amber);
}
.d-block {
  border-left-color: var(--red);
}
.d-pending {
  border-left-color: var(--purple);
}

.head {
  display: flex;
  align-items: center;
  gap: 7px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.d-allow .label,
.d-allow .dot {
  color: var(--green);
}
.d-mask .label,
.d-mask .dot {
  color: var(--amber);
}
.d-block .label,
.d-block .dot {
  color: var(--red);
}
.d-pending .label,
.d-pending .dot {
  color: var(--purple);
}

.label {
  font-size: 12px;
  font-weight: 700;
}

.account {
  margin-left: auto;
  font-size: 11px;
  color: var(--gray);
}

.text {
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--navy);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note {
  font-size: 11px;
  color: var(--gray);
}
</style>
