<script setup>
/*
 * 사규 열람.
 *
 * 규칙 코드를 나열하면 사규로 읽히지 않는다. 근거 문서별로 묶고 조항 번호를 달아
 * 조문처럼 보여준다.
 *
 * **조문 문장은 지어내지 않고 규칙에서 만든다.** 우리 스키마에 규정 원문이 없기
 * 때문이다 — `source`는 "정보보안규정 4.2" 같은 출처 문자열이고, 무엇을 어떻게
 * 하라는 내용은 `description`·`action`·`maskLabel`에 있다. 그 셋을 문장으로 조립하면
 * 화면의 조문과 실제 판정이 어긋날 수 없다. 규정 원문을 따로 적어 두면 규칙을 고쳤을 때
 * 둘이 갈라진다.
 */
import { computed } from 'vue'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()

/** 출처 문자열에서 문서명과 조항을 가른다. "정보보안규정 4.2" → 정보보안규정 / 4.2 */
const ARTICLE = /\s(제\d+조|\d+(?:\.\d+)+|v\d+|\d{4}-\d{2}-\d{2})$/

function splitSource(source) {
  const m = source.match(ARTICLE)
  if (!m) return { doc: source, article: '일반' }
  return { doc: source.slice(0, m.index), article: m[1] }
}

/** 규칙 설명에서 조문의 주어를 뽑는다. 뒤에 붙은 검사 방법 서술은 떼어낸다 */
function subjectOf(rule) {
  let s = (rule.description ?? rule.code).split('.')[0].trim()
  s = s.replace(/\s*(형식\s*)?탐지$/, '')
  s = s.replace(/\s*언급\s*시\s*검토$/, '')
  s = s.replace(/\s*전체\s*일치$/, '')
  s = s.replace(/의\s*이름\s*부분만\s*일치$/, '의 이름 부분')
  return s
}

/** 받침 유무로 조사를 고른다. 조문이 문장으로 읽히려면 필요하다 */
function hasFinal(word) {
  const c = word.charCodeAt(word.length - 1)
  if (Number.isNaN(c) || c < 0xac00 || c > 0xd7a3) return true
  return (c - 0xac00) % 28 !== 0
}
const eun = (w) => (hasFinal(w) ? '은' : '는')
const i = (w) => (hasFinal(w) ? '이' : '가')
const ro = (w) => (hasFinal(w) && (w.charCodeAt(w.length - 1) - 0xac00) % 28 !== 8 ? '으로' : '로')

function clauseOf(rule) {
  const s = subjectOf(rule)
  if (rule.action === 'BLOCK') {
    const until = rule.embargoUntil
      ? ` ${rule.embargoUntil}부터 전송할 수 있다.`
      : ''
    return `${s}${i(s)} 포함된 프롬프트는 외부 AI로 전송하지 아니한다.${until}`
  }
  if (rule.action === 'MASK') {
    const label = rule.maskLabel ?? '[라벨]'
    return `${s}${eun(s)} ${label}${ro(label)} 치환한 뒤 전송한다.`
  }
  return `${s}${i(s)} 포함된 프롬프트는 보안 담당자의 검토를 거쳐 전송 여부를 정한다.`
}

const ACTION_LABEL = { BLOCK: '차단', MASK: '마스킹', REVIEW: '검토' }

/** 문서 → 조항 → 조문 */
const books = computed(() => {
  const docs = new Map()
  for (const policy of session.policies) {
    for (const rule of policy.rules ?? []) {
      if (!rule.source) continue
      const { doc, article } = splitSource(rule.source)
      const book = docs.get(doc) ?? { doc, obligation: rule.obligation, articles: new Map() }
      const arts = book.articles
      const art = arts.get(article) ?? { article, clauses: [] }
      art.clauses.push({
        code: rule.code,
        action: rule.action,
        text: clauseOf(rule),
        severity: rule.severity,
      })
      arts.set(article, art)
      docs.set(doc, book)
    }
  }
  return [...docs.values()].map((b) => ({ ...b, articles: [...b.articles.values()] }))
})
</script>

<template>
  <div class="book">
    <p class="lead">
      {{ session.currentDeptName }}에 적용되는 규정입니다. 조문은 실제 판정 규칙에서
      생성되므로 화면의 내용과 실제 동작이 어긋나지 않습니다.
    </p>

    <section v-for="b in books" :key="b.doc" class="doc">
      <header class="doc-head">
        <h3>{{ b.doc }}</h3>
        <span class="obligation" :class="b.obligation === 'LEGAL' ? 'legal' : 'internal'">
          {{ b.obligation === 'LEGAL' ? '법령' : '사규' }}
        </span>
      </header>

      <div v-for="a in b.articles" :key="a.article" class="article">
        <span class="art-no">{{ a.article }}</span>
        <ol class="clauses">
          <li v-for="(c, n) in a.clauses" :key="c.code" class="clause">
            <span class="num">{{ n + 1 }}</span>
            <span class="text">{{ c.text }}</span>
            <span class="meta">
              <span class="act" :class="`a-${c.action.toLowerCase()}`">
                {{ ACTION_LABEL[c.action] }}
              </span>
              <span class="code">{{ c.code }}</span>
            </span>
          </li>
        </ol>
      </div>
    </section>
  </div>
</template>

<style scoped>
.book {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lead {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--gray);
}

.doc {
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}

.doc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
  background: var(--card);
  border-bottom: 1px solid var(--border);
}

.doc-head h3 {
  margin: 0;
  font-size: 14px;
  color: var(--navy);
}

.obligation {
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid currentColor;
  font-size: 11.5px;
  font-weight: 700;
}

.legal {
  color: var(--red);
}
.internal {
  color: var(--blue);
}

.article {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 10px;
  padding: 11px 12px;
  border-bottom: 1px solid var(--border);
}

.article:last-child {
  border-bottom: 0;
}

.art-no {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--blue);
  font-variant-numeric: tabular-nums;
  padding-top: 1px;
}

.clauses {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.clause {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: 7px;
  align-items: baseline;
}

.num {
  font-size: 12px;
  color: var(--gray);
  font-variant-numeric: tabular-nums;
}

.text {
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--navy);
}

.meta {
  grid-column: 2;
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 2px;
}

.act {
  padding: 1px 6px;
  border: 1px solid currentColor;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
}

.a-block {
  color: var(--red);
}
.a-mask {
  color: var(--amber);
}
.a-review {
  color: var(--purple);
}

.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
  color: var(--gray);
}
</style>
