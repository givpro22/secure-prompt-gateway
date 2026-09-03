/*
 * 피그마 반입용 화면 목업을 그린다.
 *
 * 캡처가 아니다. 구현된 화면을 찍으면 "이미 만든 것"으로 읽혀서, 구현 전에 그린 설계본으로
 * 쓸 수 없다. 여기서는 `docs/screen-spec.md`(Figma 작업 지침)만 보고 다시 그린다 — 그래서
 * 실제 앱에 있는 좌측 사이드바·알림 레일·세션 집계가 없다. 명세 1장이 정한 골격은
 * 헤더 56px + 탭 2개 + 계정 전환이고, 좌측 네비게이션은 없다.
 *
 * 회색조 와이어프레임에 판정 4색만 얹는다. 판정 분기가 이 서비스의 전부라 그것만 색으로
 * 구분하고 나머지는 전부 무채색으로 둔다.
 *
 * 실행: node docs/figma-mockups/build.mjs
 */
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.join(HERE, 'svg')

/* 색상 토큰 — screen-spec.md 1.1 표 그대로. 피그마에서 Color Style 이름을 여기 맞춘다 */
const T = {
  navy: '#16202E',
  blue: '#2F5D8A',
  red: '#C2452D',
  amber: '#B7791F',
  purple: '#5B4B8A',
  green: '#2E7D5B',
  gray: '#6B7280',
  card: '#F4F6F9',
  /* 와이어프레임 골격용 — 명세에 없는 값이라 목업 전용이다 */
  edge: '#000000',
  line: '#C9D0D9',
  bar: '#E2E7ED',
  soft: '#EFF2F6',
  white: '#FFFFFF',
}

const FONT = "Pretendard, 'Noto Sans KR', 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif"
const MONO = "'SF Mono', ui-monospace, Menlo, 'Courier New', monospace"

const W = 1280
const H = 800
const HEADER_H = 56
/* 챗 본문 최대 폭 880 (명세 1장). 1280 안에서 좌우 200씩 남는다 */
const CX = 200
const CW = 880

const esc = (s) =>
  String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

/**
 * 글자 폭 어림값. 배지 폭과 하이라이트 위치를 잡는 데만 쓴다.
 * 한글은 폰트 크기와 거의 같은 폭, 라틴은 절반쯤으로 센다.
 */
function tw(s, size = 13) {
  let u = 0
  for (const ch of String(s)) {
    if (/[ᄀ-ᇿ㄰-㆏가-힣一-鿿]/.test(ch)) u += 0.92
    else if (ch === ' ') u += 0.28
    else if (/[iIl.,:;'|!\[\]()`·]/.test(ch)) u += 0.3
    else if (/[A-Z0-9@#%mwMW]/.test(ch)) u += 0.6
    else u += 0.5
  }
  return u * size
}

const rect = (x, y, w, h, o = {}) =>
  `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${o.r ?? 6}"` +
  ` fill="${o.fill ?? 'none'}"` +
  (o.stroke ? ` stroke="${o.stroke}" stroke-width="${o.sw ?? 1.25}"` : '') +
  (o.dash ? ` stroke-dasharray="${o.dash}"` : '') +
  `/>`

const txt = (x, y, s, o = {}) =>
  `<text x="${x}" y="${y}" font-family="${o.mono ? MONO : FONT}"` +
  ` font-size="${o.size ?? 13}" fill="${o.fill ?? T.navy}"` +
  (o.weight ? ` font-weight="${o.weight}"` : '') +
  (o.anchor ? ` text-anchor="${o.anchor}"` : '') +
  (o.spacing ? ` letter-spacing="${o.spacing}"` : '') +
  `>${esc(s)}</text>`

const ln = (x1, y1, x2, y2, o = {}) =>
  `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${o.stroke ?? T.line}"` +
  ` stroke-width="${o.sw ?? 1}"` +
  (o.dash ? ` stroke-dasharray="${o.dash}"` : '') +
  `/>`

/** 자리표시 막대. 의미 없는 본문을 글자 대신 이걸로 둔다 */
const bar = (x, y, w, h = 7) => rect(x, y, w, h, { r: h / 2, fill: T.bar })

/** 상태 배지. 색상만으로 구분하지 않도록 항상 한글 라벨을 같이 둔다 (명세 1.2) */
function badge(x, y, label, color, o = {}) {
  const size = o.size ?? 12
  const h = o.h ?? 22
  const w = Math.round(tw(label, size) + 18)
  const s =
    rect(x, y, w, h, { r: 4, fill: o.solid ? color : T.card, stroke: color, sw: 1 }) +
    txt(x + 9, y + h / 2 + size * 0.36, label, {
      size,
      fill: o.solid ? T.white : color,
      weight: 600,
    })
  return { s, w, h }
}

/** 오른쪽 끝을 맞춰 놓는 배지. 라벨 길이가 제각각이라 좌표를 손으로 빼면 어긋난다 */
function badgeRight(xRight, y, label, color, o = {}) {
  const w = Math.round(tw(label, o.size ?? 12) + 18)
  return badge(xRight - w, y, label, color, o)
}

/** 드롭다운·입력 같은 폼 껍데기 */
function field(x, y, w, h, label, o = {}) {
  return (
    rect(x, y, w, h, { r: 5, fill: T.white, stroke: T.line }) +
    txt(x + 10, y + h / 2 + 4.5, label, { size: o.size ?? 12.5, fill: o.fill ?? T.gray }) +
    (o.caret
      ? `<path d="M${x + w - 18} ${y + h / 2 - 2} l4 5 l4 -5" fill="none" stroke="${T.gray}" stroke-width="1.3" stroke-linecap="round"/>`
      : '')
  )
}

function button(x, y, w, h, label, o = {}) {
  const c = o.color ?? T.navy
  return (
    rect(x, y, w, h, { r: 5, fill: o.solid ? c : T.white, stroke: c, sw: 1.2 }) +
    txt(x + w / 2, y + h / 2 + 4.5, label, {
      size: o.size ?? 12.5,
      fill: o.solid ? T.white : c,
      weight: 600,
      anchor: 'middle',
    })
  )
}

/* ---------------------------------------------------------------- 공통 껍데기 */

/**
 * 헤더. 명세 1장 — 서비스명 좌측, 탭 2개 중앙, 계정 전환 우측. 좌측 네비게이션은 없다.
 */
function header(active, account) {
  const tabs = [
    { key: 'chat', label: '챗' },
    { key: 'audit', label: '감사 콘솔' },
  ]
  let out =
    rect(0, 0, W, HEADER_H, { r: 0, fill: T.white }) +
    ln(0, HEADER_H, W, HEADER_H, { stroke: T.line }) +
    rect(24, 18, 20, 20, { r: 5, fill: T.navy }) +
    txt(54, 33, '사내 AI 게이트웨이', { size: 14, weight: 700 })

  let tx = W / 2 - 70
  for (const t of tabs) {
    const on = t.key === active
    const w = tw(t.label, 13.5)
    out += txt(tx, 33, t.label, {
      size: 13.5,
      weight: on ? 700 : 400,
      fill: on ? T.navy : T.gray,
    })
    if (on) out += rect(tx - 2, 41, w + 4, 2.5, { r: 1.5, fill: T.navy })
    tx += w + 32
  }

  /* 캐럿이 오른쪽 16px을 먹는다. 이름 길이에 그만큼을 더 얹지 않으면 글자와 붙는다 */
  const aw = Math.round(tw(account, 12.5) + 58)
  out +=
    rect(W - 24 - aw, 15, aw, 26, { r: 13, fill: T.white, stroke: T.line }) +
    `<circle cx="${W - 24 - aw + 14}" cy="28" r="8" fill="${T.soft}"/>` +
    txt(W - 24 - aw + 28, 32.5, account, { size: 12.5, fill: T.navy }) +
    `<path d="M${W - 40} 26 l4 5 l4 -5" fill="none" stroke="${T.gray}" stroke-width="1.3" stroke-linecap="round"/>`
  return out
}

function svg(w, h, body, title) {
  /* 흰 화면을 흰 배경에 놓으면 한 장이 어디서 끝나는지 안 보인다. 여러 장을 늘어놓는
     피그마 캔버스·컨택트 시트에서 특히 그렇다. 경계는 뷰박스 안쪽에 그어야 잘리지 않는다 */
  const bw = 2
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">\n` +
    `<title>${esc(title)}</title>\n` +
    rect(0, 0, w, h, { r: 0, fill: T.white }) +
    '\n' +
    body +
    '\n' +
    rect(bw / 2, bw / 2, w - bw, h - bw, { r: 0, stroke: T.edge, sw: bw }) +
    '\n</svg>\n'
  )
}

function screen(body, { active, account, title }) {
  return svg(W, H, header(active, account) + '\n' + body, title)
}

/* ------------------------------------------------------------ 챗 화면 부품 */

/** 직원 발화 버블. 작성자 본인의 입력 원문이다 (명세 2.4 규칙 5, D15) */
function bubble(y, lines, o = {}) {
  const h = 34 + lines.length * 20
  let s =
    rect(CX, y, CW, h, { r: 8, fill: T.white, stroke: T.line }) +
    txt(CX + 16, y + 21, '직원', { size: 11.5, fill: T.gray, weight: 600 })
  lines.forEach((l, i) => {
    s += txt(CX + 16, y + 42 + i * 20, l, { size: 13 })
  })
  /* 마스킹 라벨 하이라이트 — submittedText에서 라벨 문자열을 찾아 칠한다 (명세 2.4 규칙 4) */
  if (o.mark) {
    const li = lines.findIndex((l) => l.includes(o.mark))
    if (li !== -1) {
      const pre = lines[li].slice(0, lines[li].indexOf(o.mark))
      const mx = CX + 16 + tw(pre, 13)
      const mw = tw(o.mark, 13) + 6
      s =
        rect(CX, y, CW, h, { r: 8, fill: T.white, stroke: T.line }) +
        txt(CX + 16, y + 21, '직원', { size: 11.5, fill: T.gray, weight: 600 }) +
        rect(mx - 4, y + 42 - 13, mw, 20, { r: 3, fill: '#FBF0DC' })
      lines.forEach((l, i) => {
        s += txt(CX + 16, y + 42 + i * 20, l, { size: 13 })
      })
    }
  }
  return { s, h }
}

/**
 * 판정 카드. 헤더의 "규칙 N건"은 matches 배열 길이다 — 적용 규칙 수가 아니다 (명세 2.1, D1).
 */
function verdictCard(y, o) {
  const rows = o.rows ?? []
  const hintH = o.hint ? 26 : 0
  const h = 44 + rows.length * 26 + hintH + (rows.length ? 10 : 0)
  let s =
    rect(CX, y, CW, h, { r: 8, fill: T.white, stroke: T.line }) +
    rect(CX, y, 4, h, { r: 2, fill: o.color })

  const b = badge(CX + 18, y + 13, o.decision, o.color)
  s += b.s
  let hx = CX + 18 + b.w + 12
  s += txt(hx, y + 28, `규칙 ${o.ruleCount}건`, { size: 13, weight: 700 })
  hx += tw(`규칙 ${o.ruleCount}건`, 13) + 12
  s += txt(hx, y + 28, o.headline, { size: 12.5, fill: T.gray })

  rows.forEach((r, i) => {
    const ry = y + 52 + i * 26
    s += txt(CX + 18, ry + 12, r.code, { size: 12.5, mono: true, fill: T.blue, weight: 600 })
    let rx = CX + 18 + tw(r.code, 12.5) * 1.06 + 14
    s += txt(rx, ry + 12, r.category, { size: 12, fill: T.gray })
    rx += tw(r.category, 12) + 10
    const ab = badge(rx, ry, r.action, r.actionColor, { size: 11, h: 18 })
    s += ab.s
    rx += ab.w + 10
    if (r.chip) {
      const cb = badge(rx, ry, r.chip, T.gray, { size: 11, h: 18 })
      s += cb.s
    }
    const tail = `${r.obligation}  ${r.source}`
    s += txt(CX + CW - 18, ry + 12, tail, { size: 11.5, fill: T.gray, anchor: 'end' })
  })

  if (o.hint) {
    s += txt(CX + 18, y + h - 12, o.hint, {
      size: 12,
      fill: o.hintColor ?? T.gray,
      weight: o.hintColor ? 600 : 400,
    })
  }
  return { s, h }
}

/** 입력줄 + 하단 캡션. 차단이면 원문이 복원돼 있고, 마스킹·허용이면 비어 있다 (명세 2.4 규칙 3) */
function composer(y, o) {
  const boxH = 74
  let s =
    rect(CX, y, CW - 92, boxH, { r: 8, fill: T.white, stroke: T.line }) +
    button(CX + CW - 80, y, 80, boxH, '전송', { solid: !o.disabled, color: o.disabled ? T.line : T.navy })
  if (o.value) {
    o.value.forEach((l, i) => {
      s += txt(CX + 14, y + 24 + i * 19, l, { size: 12.5 })
    })
  } else {
    s += txt(CX + 14, y + 26, o.placeholder ?? 'AI에게 보낼 프롬프트를 입력하세요.', {
      size: 12.5,
      fill: T.gray,
    })
  }
  if (o.note) s += txt(CX, y + boxH + 20, o.note, { size: 11.5, fill: T.gray })
  s += txt(CX + CW, y + boxH + 20, o.caption, { size: 11.5, fill: T.gray, anchor: 'end' })
  return { s, h: boxH }
}

/* -------------------------------------------------------------- 화면 11장 */

const CAPTION = {
  dev: '부서: 개발팀 · 적용 정책 3건 (P-PII, P-SEC, P-EMBARGO)',
  hr: '부서: 인사팀 · 적용 정책 3건 (P-PII, P-SEC, P-CONF)',
  sales: '부서: 영업팀 · 적용 정책 4건 (P-PII, P-SEC, P-CONF, P-EMBARGO)',
}

const frames = []

/* 01 — S1 초기. 빈 대화 영역 + 입력창 + 캡션 (명세 2.2) */
{
  let s = ''
  s += txt(W / 2, 300, '무엇이든 물어보세요.', { size: 20, weight: 700, anchor: 'middle' })
  s += txt(W / 2, 328, '보내기 전에 개발팀 정책으로 검사하고, 판정과 근거를 기록으로 남깁니다.', {
    size: 13,
    fill: T.gray,
    anchor: 'middle',
  })
  s += composer(560, { caption: CAPTION.dev }).s
  frames.push({ name: '01_s1_input', svg: screen(s, { active: 'chat', account: '이OO · 개발팀', title: 'SCR-01 / S1 초기' }) })
}

/* 02 — S2 허용. 규칙 0건, 카드는 한 줄로 축약 (명세 2.2) */
{
  let s = ''
  let y = 92
  const b = bubble(y, ['A사 차세대 프로젝트 오픈 일정이 언제였지?'])
  s += b.s
  y += b.h + 16
  const c = verdictCard(y, {
    decision: '허용',
    color: T.green,
    ruleCount: 0,
    headline: '정책 위반이 없어 그대로 전송되었습니다.',
    rows: [],
  })
  s += c.s
  s += composer(560, { caption: CAPTION.dev }).s
  frames.push({ name: '02_allow', svg: screen(s, { active: 'chat', account: '이OO · 개발팀', title: 'SCR-01 / S2 허용' }) })
}

/* 03 — S3 마스킹. 마스킹본의 [전화번호] 하이라이트 + 규칙 1건 */
{
  let s = ''
  let y = 92
  const b = bubble(y, ['지원자 연락처 [전화번호] 로 면접 안내 문자 초안 써줘'], { mark: '[전화번호]' })
  s += b.s
  y += b.h + 16
  const c = verdictCard(y, {
    decision: '마스킹',
    color: T.amber,
    ruleCount: 1,
    headline: '마스킹 후 전송되었습니다.',
    rows: [
      {
        code: 'PII-PHONE-03',
        category: '개인정보',
        action: '마스킹',
        actionColor: T.amber,
        obligation: '법령',
        source: '개인정보보호법',
      },
    ],
    hint: '탐지된 구간이 [전화번호] 라벨로 치환되어 전송되었습니다.',
  })
  s += c.s
  s += composer(560, { caption: CAPTION.hr }).s
  frames.push({ name: '03_mask', svg: screen(s, { active: 'chat', account: '정OO · 인사팀', title: 'SCR-01 / S3 마스킹' }) })
}

/* 04 — S4 차단. 규칙 2건 + 의무/출처, 입력창에 원문 복원 */
{
  let s = ''
  let y = 92
  const b = bubble(y, [
    '이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데',
    '담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나',
  ])
  s += b.s
  y += b.h + 16
  const c = verdictCard(y, {
    decision: '차단',
    color: T.red,
    ruleCount: 2,
    headline: '전송이 차단되었습니다. 내용을 수정한 뒤 재전송하세요.',
    rows: [
      {
        code: 'SEC-DBURL-02',
        category: '자격증명',
        action: '차단',
        actionColor: T.red,
        obligation: '사규',
        source: '정보보안규정 4.2',
      },
      {
        code: 'PII-RRN-01',
        category: '개인정보',
        action: '마스킹',
        actionColor: T.amber,
        obligation: '법령',
        source: '개인정보보호법 제24조',
      },
    ],
    hint: '차단된 항목을 제거하거나 대체한 뒤 다시 전송하세요.',
  })
  s += c.s
  s += composer(560, {
    value: [
      '이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데',
      '담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나',
    ],
    note: '입력창에 방금 입력한 내용이 그대로 남아 있습니다.',
    caption: CAPTION.dev,
  }).s
  frames.push({ name: '04_block', svg: screen(s, { active: 'chat', account: '이OO · 개발팀', title: 'SCR-01 / S4 차단' }) })
}

/* 05 — S4′ 엠바고 차단. 언제 다시 시도하면 되는지 알려준다 (명세 2.1, D20) */
{
  let s = ''
  let y = 92
  const b = bubble(y, [
    '2026 4Q 릴리스 백로그 정리 좀 해줘. SKALA NOVA 런칭 일정이랑',
    'ATLAS 업데이트 항목이 섞여 있어',
  ])
  s += b.s
  y += b.h + 16
  const c = verdictCard(y, {
    decision: '차단',
    color: T.red,
    ruleCount: 1,
    headline: '전송이 차단되었습니다.',
    rows: [
      {
        code: 'EMB-NOVA-01',
        category: '엠바고',
        action: '차단',
        actionColor: T.red,
        chip: '2026-09-20 해제',
        obligation: '사규',
        source: '홍보팀 엠바고 공지',
      },
    ],
    hint: '2026-09-20부터 공개할 수 있는 내용입니다. 그때까지는 외부 AI로 보낼 수 없습니다.',
    hintColor: T.navy,
  })
  s += c.s
  s += composer(560, {
    value: [
      '2026 4Q 릴리스 백로그 정리 좀 해줘. SKALA NOVA 런칭 일정이랑',
      'ATLAS 업데이트 항목이 섞여 있어',
    ],
    note: '해제일이 지난 표현은 같은 문장이어도 걸리지 않습니다.',
    caption: CAPTION.dev,
  }).s
  frames.push({ name: '05_embargo', svg: screen(s, { active: 'chat', account: '이OO · 개발팀', title: "SCR-01 / S4' 엠바고 차단" }) })
}

/* 06 — S5-a 폴링 중. Mock 지연 2.5초 동안 실제로 보이는 구간 (명세 2.2) */
{
  let s = ''
  let y = 92
  const b = bubble(y, ['A사 차세대 프로젝트 오픈 일정이 언제였지?'])
  s += b.s
  y += b.h + 16
  const c = verdictCard(y, {
    decision: '검토 대기',
    color: T.purple,
    ruleCount: 1,
    headline: '보안 검토가 필요한 내용입니다.',
    rows: [
      {
        code: 'CONF-CLIENT-01',
        category: '기밀',
        action: '검토',
        actionColor: T.purple,
        obligation: '사규',
        source: '고객사 NDA 목록 v3',
      },
    ],
  })
  s += c.s
  y += c.h + 16
  s +=
    rect(CX, y, CW, 52, { r: 8, fill: T.card, stroke: T.line, dash: '5 4' }) +
    `<path d="M${CX + 34} ${y + 26} a12 12 0 1 1 8.5 11.5" fill="none" stroke="${T.purple}" stroke-width="2.4" stroke-linecap="round"/>` +
    txt(CX + 62, y + 31, '보안 검토 중', { size: 13, weight: 700, fill: T.purple }) +
    txt(CX + 62 + tw('보안 검토 중', 13) + 12, y + 31, '2초 경과 · 폴링 1회', { size: 12, fill: T.gray }) +
    txt(CX + CW - 18, y + 31, '2,000ms 간격 · 최대 30회', { size: 11.5, fill: T.gray, anchor: 'end' })
  s += composer(560, { disabled: true, placeholder: '검토가 끝날 때까지 입력이 잠깁니다.', caption: CAPTION.sales }).s
  frames.push({ name: '06_review_pending', svg: screen(s, { active: 'chat', account: '김OO · 영업팀', title: 'SCR-01 / S5-a 폴링 중' }) })
}

/* 07 — S5-b 검토 대기 + AI 후보. 직원 화면에는 확정 버튼이 없다 (명세 3.4) */
{
  let s = ''
  let y = 92
  const b = bubble(y, ['A사 차세대 프로젝트 오픈 일정이 언제였지?'])
  s += b.s
  y += b.h + 14
  const c = verdictCard(y, {
    decision: '검토 대기',
    color: T.purple,
    ruleCount: 1,
    headline: '보안 검토가 필요한 내용입니다.',
    rows: [
      {
        code: 'CONF-CLIENT-01',
        category: '기밀',
        action: '검토',
        actionColor: T.purple,
        obligation: '사규',
        source: '고객사 NDA 목록 v3',
      },
    ],
  })
  s += c.s
  y += c.h + 14

  const panelH = 214
  s += rect(CX, y, CW, panelH, { r: 8, fill: T.white, stroke: T.line })
  const hb = badge(CX + 18, y + 14, '검토 대기', T.purple)
  s += hb.s
  s += txt(CX + 18 + hb.w + 12, y + 29, '검토 대기 (담당자 확정 필요)', { size: 13, weight: 700 })
  s += txt(CX + 18, y + 52, '아래는 AI가 제시한 후보입니다. 확정은 보안 담당자가 감사 콘솔에서 합니다.', {
    size: 12,
    fill: T.gray,
  })

  const cy = y + 64
  s += rect(CX + 18, cy, CW - 36, 132, { r: 6, fill: T.card, stroke: T.line })
  s += txt(CX + 34, cy + 24, 'CONF-CLIENT-PROJECT', { size: 12.5, mono: true, fill: T.purple, weight: 700 })
  s += txt(CX + 34 + tw('CONF-CLIENT-PROJECT', 12.5) * 1.06 + 14, cy + 24, '기밀', { size: 12, fill: T.gray })
  const sb = badgeRight(CX + CW - 52, cy + 10, '제안됨', T.purple, { size: 11, h: 18 })
  s += sb.s
  s += txt(CX + 34, cy + 48, "'A사 차세대 프로젝트 오픈 일정'이라는 서술이 계약 상대방과", { size: 12.5 })
  s += txt(CX + 34, cy + 67, '미공개 일정을 동시에 특정함', { size: 12.5 })
  s += `<circle cx="${CX + 38}" cy="${cy + 88}" r="2.2" fill="${T.gray}"/>`
  s += txt(CX + 48, cy + 92, '고객사 NDA 목록 v3 — A사 — 비밀유지 2027.03까지, 일정·범위 포함', {
    size: 11.5,
    fill: T.gray,
  })
  s += txt(CX + 34, cy + 116, '확인이 필요한 맥락 — 해당 일정이 대외 공개된 정보인지 확인 필요', {
    size: 11.5,
    fill: T.gray,
  })

  s += txt(CX, y + panelH + 22, '확정 버튼은 이 화면에 없습니다. 담당자가 확정하면 새로고침으로 결과가 반영됩니다.', {
    size: 11.5,
    fill: T.gray,
  })
  s += composer(628, { caption: CAPTION.sales }).s
  frames.push({ name: '07_ai_candidate', svg: screen(s, { active: 'chat', account: '김OO · 영업팀', title: 'SCR-01 / S5-b 검토 대기 + AI 후보' }) })
}

/* ------------------------------------------------------------ 감사 콘솔 부품 */

const AX = 24
const AW = W - 48

const ROWS = [
  ['09-03 04:55', '개발팀', '이OO', '차단', T.red, '2', '', '규칙'],
  ['09-03 04:18', '인사팀', '정OO', '마스킹', T.amber, '1', '', '규칙'],
  ['09-03 00:30', '영업팀', '김OO', '검토 대기', T.purple, '1', '분석 완료', '—'],
  ['09-02 20:11', '인사팀', '정OO', '허용', T.green, '0', '', '규칙'],
  ['09-02 15:52', '영업팀', '김OO', '마스킹', T.amber, '1', '', '규칙'],
  ['09-02 04:12', '인사팀', '정OO', '마스킹', T.amber, '1', '', '규칙'],
  ['09-01 23:53', '개발팀', '이OO', '차단', T.red, '2', '', '규칙'],
  ['09-01 19:34', '영업팀', '김OO', '검토 대기', T.purple, '1', '분석 실패', '—'],
  ['09-01 15:15', '인사팀', '정OO', '허용', T.green, '0', '', '규칙'],
  ['09-01 03:35', '영업팀', '김OO', '검토 대기', T.purple, '1', '분석 완료', '—'],
  ['08-31 23:16', '인사팀', '정OO', '마스킹', T.amber, '1', '', '규칙'],
  ['08-31 18:57', '개발팀', '이OO', '차단', T.red, '2', '', '규칙'],
]

/** 필터 바 — 부서 옵션에 정보보안팀이 없다 (명세 3.1, D2) */
function filterBar(y, w) {
  let s = rect(AX, y, w, 48, { r: 8, fill: T.card, stroke: T.line })
  let x = AX + 14
  s += txt(x, y + 29, '부서', { size: 12, fill: T.gray })
  x += 32
  s += field(x, y + 12, 96, 24, '전체', { caret: true })
  x += 108
  s += txt(x, y + 29, '상태', { size: 12, fill: T.gray })
  x += 32
  s += field(x, y + 12, 96, 24, '전체', { caret: true })
  x += 108
  s += txt(x, y + 29, '기간', { size: 12, fill: T.gray })
  x += 32
  s += field(x, y + 12, 108, 24, '2026-08-28')
  x += 114
  s += txt(x, y + 29, '~', { size: 12, fill: T.gray })
  x += 14
  s += field(x, y + 12, 108, 24, '2026-09-03')
  s += txt(AX + w - 14, y + 29, '총 25건', { size: 12.5, weight: 700, anchor: 'end' })
  return { s, h: 48 }
}

/** 목록 표. 규칙이 결정한 행은 AI 상태가 공란이다 (명세 1.3) */
function auditTable(x, y, w, { cols, rows, selected = -1, dense = false }) {
  const rh = dense ? 30 : 34
  let s = rect(x, y, w, 34 + rows.length * rh, { r: 8, fill: T.white, stroke: T.line })
  const at = cols.map((c) => x + Math.round((c.at / 100) * w))
  cols.forEach((c, i) => {
    s += txt(at[i], y + 22, c.label, { size: 11.5, fill: T.gray, weight: 600, anchor: c.anchor })
  })
  s += ln(x, y + 34, x + w, y + 34, { stroke: T.line })

  rows.forEach((r, ri) => {
    const ry = y + 34 + ri * rh
    if (ri === selected) {
      s += rect(x + 1, ry, w - 2, rh, { r: 0, fill: T.soft })
      s += rect(x + 1, ry, 3, rh, { r: 0, fill: T.blue })
    } else if (ri > 0) {
      s += ln(x + 12, ry, x + w - 12, ry, { stroke: '#EDF0F4' })
    }
    const cy = ry + rh / 2 + 4
    /* 시각·부서·사용자 */
    s += txt(at[0], cy, r[0], { size: 11.5, mono: true, fill: T.gray })
    if (cols.length > 5) {
      s += txt(at[1], cy, r[1], { size: 11.5, fill: T.gray })
      s += txt(at[2], cy, r[2], { size: 11.5 })
    }
    const bi = cols.length > 5 ? 3 : 1
    const b = badge(at[bi], ry + (rh - 18) / 2, r[3], r[4], { size: 10.5, h: 18 })
    s += b.s
    if (cols.length > 5) {
      s += txt(at[4], cy, r[5], { size: 11.5, anchor: 'middle' })
      s += txt(at[5], cy, r[6], { size: 11.5, fill: T.gray })
      s += txt(at[6], cy, r[7], { size: 11.5, fill: T.gray, anchor: 'end' })
    }
  })
  return { s, h: 34 + rows.length * rh }
}

/* 08 — SCR-02 목록 */
{
  let s = ''
  let y = HEADER_H + 24
  s += txt(AX, y + 12, '관리자 감사 콘솔', { size: 17, weight: 700 })
  s += txt(AX + tw('관리자 감사 콘솔', 17) + 14, y + 12, '원문은 화면에 표시하지 않습니다. 마스킹 본문 기준 감사.', {
    size: 12,
    fill: T.gray,
  })
  y += 30
  const f = filterBar(y, AW)
  s += f.s
  y += f.h + 16
  const t = auditTable(AX, y, AW, {
    cols: [
      { at: 1.5, label: '시각' },
      { at: 12, label: '부서' },
      { at: 20, label: '사용자' },
      { at: 30, label: '판정' },
      { at: 46, label: '규칙 수', anchor: 'middle' },
      { at: 56, label: 'AI 상태' },
      { at: 98.5, label: '확정', anchor: 'end' },
    ],
    rows: ROWS,
    dense: true,
  })
  s += t.s
  y += t.h + 18
  s += txt(AX, y, '페이지 크기 20 · page는 0부터', { size: 11.5, fill: T.gray })
  s += button(W - AX - 176, y - 15, 56, 26, '이전', { color: T.line })
  s += txt(W - AX - 88, y + 3, '1 / 2', { size: 12, fill: T.gray, anchor: 'middle' })
  s += button(W - AX - 56, y - 15, 56, 26, '다음')
  frames.push({ name: '08_audit_list', svg: screen(s, { active: 'audit', account: '박OO · 정보보안팀', title: 'SCR-02 / 목록' }) })
}

/**
 * 상세 패널 4개 섹션. 2(규칙)와 3(AI)을 시각적으로 가르는 것이 이 화면의 핵심이다 —
 * 그 분리가 기획서 4장 책임 경계를 화면으로 증명한다 (명세 3.3).
 */
function detailPanel(x, y, w, { after }) {
  let s = rect(x, y, w, 560, { r: 8, fill: T.white, stroke: T.line })
  let cy = y + 26

  s += txt(x + 16, cy, '#2090', { size: 12.5, mono: true, weight: 700 })
  let hx = x + 16 + tw('#2090', 12.5) * 1.06 + 12
  if (after) {
    const fb = badge(hx, cy - 14, '차단', T.red, { size: 11, h: 18 })
    s += fb.s
    s += txt(hx + fb.w + 8, cy, '최종 판정 · 확정 주체 담당자', { size: 11.5, fill: T.gray })
  } else {
    const fb = badge(hx, cy - 14, '검토 대기', T.purple, { size: 11, h: 18 })
    s += fb.s
    s += txt(hx + fb.w + 8, cy, '김OO · 영업팀 · 09-01 03:35', { size: 11.5, fill: T.gray })
  }
  cy += 24

  /* 1. 원문 — 마스킹된 본문만. ACCEPT 후에도 본문이 사라지지 않는다 (D14) */
  s += txt(x + 16, cy, '① 원문 — 마스킹 본문만 표시합니다', { size: 11.5, fill: T.gray, weight: 600 })
  cy += 10
  s += rect(x + 16, cy, w - 32, 40, { r: 6, fill: T.card, stroke: T.line })
  s += txt(x + 30, cy + 25, 'A사 차세대 프로젝트 오픈 일정이 언제였지?', { size: 12.5 })
  cy += 62

  /* 2. 규칙 판정 (결정) */
  s += txt(x + 16, cy, '② 규칙 판정', { size: 11.5, fill: T.gray, weight: 600 })
  const kb = badge(x + 16 + tw('② 규칙 판정', 11.5) + 10, cy - 13, '결정', T.blue, { size: 10.5, h: 17 })
  s += kb.s
  cy += 12
  s += rect(x + 16, cy, w - 32, 54, { r: 6, fill: T.white, stroke: T.line })
  s += rect(x + 16, cy, 4, 54, { r: 2, fill: T.blue })
  s += txt(x + 32, cy + 22, 'CONF-CLIENT-01', { size: 12.5, mono: true, fill: T.blue, weight: 600 })
  let rx = x + 32 + tw('CONF-CLIENT-01', 12.5) * 1.06 + 12
  s += txt(rx, cy + 22, '기밀', { size: 11.5, fill: T.gray })
  const ab = badge(rx + tw('기밀', 11.5) + 10, cy + 8, '검토', T.purple, { size: 10.5, h: 17 })
  s += ab.s
  const cfb = badgeRight(x + w - 32, cy + 8, '확정(규칙)', T.blue, { size: 10.5, h: 17 })
  s += cfb.s
  s += txt(x + 32, cy + 42, '사규 · 고객사 NDA 목록 v3 · 버튼 없음', { size: 11, fill: T.gray })
  cy += 76

  /* 3. AI 제안 (후보) */
  s += txt(x + 16, cy, '③ AI 제안', { size: 11.5, fill: T.gray, weight: 600 })
  const kb2 = badge(x + 16 + tw('③ AI 제안', 11.5) + 10, cy - 13, '후보', T.purple, { size: 10.5, h: 17 })
  s += kb2.s
  cy += 12
  const aih = after ? 118 : 150
  s += rect(x + 16, cy, w - 32, aih, { r: 6, fill: T.card, stroke: T.line })
  s += rect(x + 16, cy, 4, aih, { r: 2, fill: after ? T.red : T.purple })
  s += txt(x + 32, cy + 24, 'CONF-CLIENT-PROJECT', {
    size: 12.5,
    mono: true,
    fill: after ? T.red : T.purple,
    weight: 700,
  })
  const stb = after
    ? badgeRight(x + w - 32, cy + 10, '확정(위반)', T.red, { size: 10.5, h: 17 })
    : badgeRight(x + w - 32, cy + 10, '제안됨', T.purple, { size: 10.5, h: 17 })
  s += stb.s
  s += txt(x + 32, cy + 48, "'A사 차세대 프로젝트 오픈 일정'이라는 서술이", { size: 12 })
  s += txt(x + 32, cy + 66, '계약 상대방과 미공개 일정을 동시에 특정함', { size: 12 })
  s += txt(x + 32, cy + 90, '고객사 NDA 목록 v3 — 비밀유지 2027.03까지', { size: 11, fill: T.gray })
  if (!after) {
    const aw = Math.round(tw('ACCEPT (위반 확정)', 11) + 26)
    const rw = Math.round(tw('REJECT (기각)', 11) + 26)
    s += button(x + 32, cy + 106, aw, 28, 'ACCEPT (위반 확정)', { color: T.red, size: 11 })
    s += button(x + 32 + aw + 10, cy + 106, rw, 28, 'REJECT (기각)', { color: T.gray, size: 11 })
  }
  cy += aih + 24

  /* 4. 이력 */
  s += txt(x + 16, cy, '④ 이력', { size: 11.5, fill: T.gray, weight: 600 })
  cy += 20
  const hist = after
    ? [
        ['정책 버전', 'P-PII v5 · P-SEC v7 · P-CONF v2 · P-EMBARGO v1'],
        ['확정 주체', '담당자'],
        ['확정자', '박OO · 정보보안팀'],
        ['확정 시각', '2026-09-03 05:00'],
      ]
    : [
        ['정책 버전', 'P-PII v5 · P-SEC v7 · P-CONF v2 · P-EMBARGO v1'],
        ['확정 주체', '—'],
        ['확정자', '—'],
        ['확정 시각', '—'],
      ]
  hist.forEach((r, i) => {
    s += txt(x + 16, cy + i * 19, r[0], { size: 11.5, fill: T.gray })
    s += txt(x + 104, cy + i * 19, r[1], { size: 11.5 })
  })
  return s
}

/* 09 · 10 — SCR-02 상세. 확정 전/후 두 장 */
for (const after of [false, true]) {
  let s = ''
  let y = HEADER_H + 24
  s += txt(AX, y + 12, '관리자 감사 콘솔', { size: 17, weight: 700 })
  y += 30
  const f = filterBar(y, AW)
  s += f.s
  y += f.h + 16

  const listW = 560
  const t = auditTable(AX, y, listW, {
    cols: [
      { at: 3, label: '시각' },
      { at: 62, label: '판정' },
    ],
    rows: ROWS.slice(0, 12),
    selected: 9,
    dense: true,
  })
  s += t.s
  s += detailPanel(AX + listW + 16, y, AW - listW - 16, { after })
  frames.push({
    name: after ? '10_audit_detail_after' : '09_audit_detail_before',
    svg: screen(s, {
      active: 'audit',
      account: '박OO · 정보보안팀',
      title: after ? 'SCR-02 / 상세 · 확정 후' : 'SCR-02 / 상세 · 확정 전',
    }),
  })
}

/* 11 — User Flow. 명세 4장 + 2.3 전이도 */
{
  const FW = 1440
  const FH = 900
  let s = txt(48, 56, 'User Flow — SCR-01 · SCR-02', { size: 20, weight: 700 })
  s += txt(48, 80, '기획서 5.5 · 화면 명세 2.3. 노드 이름은 S1~S5와 5.6 용어를 씁니다.', {
    size: 12.5,
    fill: T.gray,
  })

  const node = (x, y, w, h, title, sub, color, o = {}) => {
    let n = rect(x, y, w, h, { r: 8, fill: o.fill ?? T.white, stroke: color ?? T.line, sw: color ? 1.6 : 1.25 })
    if (color) n += rect(x, y, 4, h, { r: 2, fill: color })
    n += txt(x + 16, y + 26, title, { size: 13.5, weight: 700, fill: color ?? T.navy })
    if (sub) n += txt(x + 16, y + 46, sub, { size: 11.5, fill: T.gray })
    return n
  }

  const arrow = (x1, y1, x2, y2, label, o = {}) => {
    const mx = o.mx ?? (x1 + x2) / 2
    const d = o.elbow
      ? `M${x1} ${y1} H${mx} V${y2} H${x2}`
      : `M${x1} ${y1} L${x2} ${y2}`
    let a = `<path d="${d}" fill="none" stroke="${o.stroke ?? T.line}" stroke-width="1.4"` +
      (o.dash ? ` stroke-dasharray="${o.dash}"` : '') +
      ` marker-end="url(#ah${o.stroke === T.purple ? 'p' : ''})"/>`
    if (label) {
      const lx = o.lx ?? (x1 + x2) / 2
      const ly = o.ly ?? y1 - 8
      a +=
        rect(lx - tw(label, 11) / 2 - 7, ly - 12, tw(label, 11) + 14, 18, { r: 9, fill: T.white }) +
        txt(lx, ly + 1, label, { size: 11, fill: o.stroke === T.purple ? T.purple : T.gray, anchor: 'middle' })
    }
    return a
  }

  const defs =
    `<defs>` +
    `<marker id="ah" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">` +
    `<path d="M0 0 L8 4 L0 8 z" fill="${T.line}"/></marker>` +
    `<marker id="ahp" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">` +
    `<path d="M0 0 L8 4 L0 8 z" fill="${T.purple}"/></marker>` +
    `</defs>`

  /* 좌: 입력 → 판정 분기 4갈래 → 우: 검토 확정 루프 */
  s += node(48, 140, 220, 66, 'S1 초기', '입력창 활성 · 정책 캡션', null)
  s += node(330, 140, 200, 66, '규칙 판정', '전송 전 검사', T.blue)
  s += arrow(268, 173, 322, 173, null)

  const ys = [250, 344, 438, 556]
  s += node(620, ys[0], 300, 66, 'S2 허용', '200 · 규칙 0건', T.green)
  s += node(620, ys[1], 300, 66, 'S3 마스킹', '200 · 라벨 치환 후 전송', T.amber)
  s += node(620, ys[2], 300, 66, 'S4 차단', '403 · 규칙 목록 + 의무/출처', T.red)
  s += node(620, ys[3], 300, 66, 'S5-a 폴링 중', '202 · 2,000ms 간격 최대 30회', T.purple)

  /*
   * 분기 줄기는 회색 한 줄로 먼저 긋고, 가지만 갈래 색으로 낸다. 갈래마다 줄기를 다시
   * 그리면 마지막에 그린 색이 줄기 전체를 덮어서 — 검토 대기 경로가 판정 직후부터
   * 시작하는 것처럼 읽힌다.
   */
  s += `<path d="M430 206 H520 V${ys[3] + 33}" fill="none" stroke="${T.line}" stroke-width="1.4"/>`
  ys.forEach((yy, i) => {
    const codes = ['200 ALLOW', '200 MASK', '403 BLOCK', '202 PENDING']
    const c = i === 3 ? T.purple : T.line
    s += `<path d="M520 ${yy + 33} H612" fill="none" stroke="${c}" stroke-width="1.4" marker-end="url(#ah${i === 3 ? 'p' : ''})"/>`
    s +=
      rect(520 - tw(codes[i], 11) / 2 - 7, yy + 12, tw(codes[i], 11) + 14, 18, { r: 9, fill: T.white }) +
      txt(520, yy + 25, codes[i], { size: 11, fill: i === 3 ? T.purple : T.gray, anchor: 'middle' })
  })

  s += node(620, 660, 300, 66, 'S5-b 검토 대기', 'AI 후보 목록 (읽기 전용)', T.purple)
  s += arrow(770, ys[3] + 66, 770, 654, 'aiStatus=COMPLETED', { stroke: T.purple, ly: 640 })

  s += node(1010, 660, 380, 66, 'SCR-02 상세 — 담당자 확정', 'ACCEPT / REJECT · AI 후보만', T.purple)
  s += arrow(920, 693, 1002, 693, null, { stroke: T.purple })

  s += node(1010, 438, 380, 66, '확정 결과 반영', '차단 또는 허용 · 확정 주체 담당자', T.navy)
  s += arrow(1200, 654, 1200, 512, '새로고침', { stroke: T.purple, ly: 590 })

  /* 입력창 복원 루프 — 차단만 원문이 남는다. 한 줄로 그린다: 끊으면 화살촉이 허공에 뜬다 */
  s += `<path d="M770 ${ys[2] + 66} V522 H430 V212" fill="none" stroke="${T.line}" stroke-width="1.4" stroke-dasharray="5 4" marker-end="url(#ah)"/>`
  s +=
    rect(560, 512, 136, 20, { r: 10, fill: T.white }) +
    txt(628, 526, '입력창에 원문 복원', { size: 11, fill: T.gray, anchor: 'middle' })

  s += txt(48, 800, '허용·마스킹은 전송 후 입력창을 비우고 S1로 돌아갑니다.', { size: 12, fill: T.gray })
  s += txt(48, 822, '마지막 전이는 폴링이 아니라 새로고침입니다 — 사람의 확정 시점은 예측할 수 없습니다 (D12).', {
    size: 12,
    fill: T.gray,
  })
  s += txt(48, 844, '규칙 판정 finding에는 ACCEPT/REJECT를 노출하지 않습니다 (D6).', { size: 12, fill: T.gray })

  frames.push({ name: '11_user_flow', svg: svg(FW, FH, defs + s, 'Flow / User Flow') })
}

/* ------------------------------------------------------------------- 출력 */

await mkdir(OUT, { recursive: true })
for (const f of frames) {
  await writeFile(path.join(OUT, `${f.name}.svg`), f.svg)
  console.log(`  ✓ ${f.name}.svg`)
}
console.log(`\n${frames.length}장 — ${OUT}`)
