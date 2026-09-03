/*
 * 피그마 반입용 화면 캡처를 다시 만든다.
 *
 * 손으로 찍은 예전 자산은 상단 헤더와 좌측 사이드바가 통째로 잘려 있었다. 창 일부만
 * 오려낸 캡처였기 때문이다. 여기서는 뷰포트 전체를 찍고, 찍기 전에 뷰포트를 콘텐츠
 * 높이에 맞춰 늘린다 — 감사 콘솔은 1440x900에 들어가지 않아 목록 아래쪽이 잘린다.
 *
 * 실행:
 *   GATEWAY_EMBARGO_REFERENCE_DATE=2026-09-04 npm run dev:fixtures
 *   node dev/capture-figma-assets.mjs [--base http://localhost:5173]
 *
 * 브라우저는 내려받지 않는다. 시스템에 깔린 Chrome을 그대로 몬다(channel: 'chrome').
 */
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ASSETS = path.resolve(HERE, '../../docs/figma-assets')

const argBase = process.argv.indexOf('--base')
/* 5173이 이미 물려 있으면 vite가 다음 포트로 올라간다. 그때는 --base로 알려준다 */
const BASE = argBase !== -1 ? process.argv[argBase + 1] : 'http://localhost:5173'

const WIDTH = 1440
const BASE_HEIGHT = 900
/* 뷰포트를 무한정 늘리지 않는다. 이 위로는 슬라이드에 넣을 수 없는 크기다 */
const MAX_HEIGHT = 2600
const DPR = 2

/** 기획서 10.4의 데모 입력. 한 글자도 바꾸지 않는다 */
const PROMPTS = {
  block:
    '이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나',
  review: 'A사 차세대 프로젝트 오픈 일정이 언제였지?',
  mask: '지원자 연락처 010-1234-5678 로 면접 안내 문자 초안 써줘',
  embargo:
    '2026 4Q 릴리스 백로그 정리 좀 해줘. SKALA NOVA 런칭 일정이랑 ATLAS 업데이트 항목이 섞여 있어',
}

const USERS = { dev: 1, sales: 2, hr: 3, infosec: 4 }

const captured = []

/**
 * 스크롤이 생긴 컨테이너만큼 뷰포트를 늘린다.
 *
 * `.shell`이 height:100vh · overflow:hidden이라 내부 컨테이너가 알아서 스크롤된다.
 * 그래서 "페이지 전체 캡처"(fullPage)로는 잘린 화면이 그대로 찍힌다. 뷰포트 자체를
 * 키워야 잘리지 않는다. 알림 레일은 대상에서 뺀다 — 원래 스크롤되는 옆 패널이라
 * 여기까지 맞추면 이미지가 쓸데없이 길어진다.
 */
async function fitViewport(page, selectors) {
  /*
   * 기준 높이로 되돌리고 시작한다. 이전 샷에서 늘려 둔 뷰포트를 물고 있으면 스크롤이
   * 안 생겨 "늘릴 필요 없음"으로 읽히고, 화면마다 필요한 만큼이 아니라 그 회차에서
   * 가장 길었던 화면 높이로 전부 찍힌다 — 챗 화면 아래가 빈 여백으로 남는다.
   */
  let height = BASE_HEIGHT
  await page.setViewportSize({ width: WIDTH, height })
  await page.waitForTimeout(300)

  for (let i = 0; i < 4; i += 1) {
    const extra = await page.evaluate((sels) => {
      let need = 0
      for (const sel of sels) {
        for (const el of document.querySelectorAll(sel)) {
          need = Math.max(need, el.scrollHeight - el.clientHeight)
        }
      }
      return Math.ceil(need)
    }, selectors)
    if (extra <= 1) break
    const next = Math.min(height + extra + 8, MAX_HEIGHT)
    if (next === height) break
    height = next
    await page.setViewportSize({ width: WIDTH, height })
    await page.waitForTimeout(300)
  }
  return height
}

/**
 * 화면 전체(full/)와 노드 크롭(nodes/)을 한 상태에서 같이 찍는다.
 *
 * 크롭은 셀렉터 하나가 가리키는 요소를 그대로 잘라낸다. 여러 요소를 합집합으로 묶는
 * 방식은 두지 않았다 — 하나가 안 잡혀도 나머지로 그럴듯한 상자가 나와서, 잘린 이미지가
 * 조용히 통과한다. 그런 크롭이 필요해지면 안 잡힌 셀렉터에 반드시 던지게 만들어야 한다.
 */
async function shot(page, name, { node, fit = ['.audit', '.thread', '.chat'] }) {
  const height = await fitViewport(page, fit)

  await page.screenshot({ path: path.join(ASSETS, 'full', `${name}.png`) })

  const el = page.locator(node).last()
  await el.waitFor({ state: 'visible' })
  await el.screenshot({ path: path.join(ASSETS, 'nodes', `${name}.png`) })

  captured.push({ name, viewport: `${WIDTH}x${height}` })
  console.log(`  ✓ ${name}  (viewport ${WIDTH}x${height})`)
}

/** 계정을 바꾸고 화면을 다시 연다. 전환은 localStorage 키 하나다 (stores/session.js) */
async function open(page, userId, route) {
  await page.addInitScript(
    (id) => window.localStorage.setItem('gateway.currentUserId', String(id)),
    userId,
  )
  await page.goto(`${BASE}${route}`, { waitUntil: 'networkidle' })
  await page.evaluate((id) => window.localStorage.setItem('gateway.currentUserId', String(id)), userId)
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
}

/**
 * 프롬프트를 넣고 전송한다. 판정 카드가 뜰 때까지 기다린다.
 *
 * 턴이 하나 늘어나는 것으로 확인한다. `article.turn`이 보이는지만 보면 앞 화면에 남아
 * 있던 턴을 새 턴으로 착각한다. 라우터가 첫 그리기 직후 한 번 더 그리는 구간에 클릭이
 * 떨어지면 전송이 통째로 삼켜지므로, 그때만 한 번 다시 친다.
 */
async function send(page, text, { waitForCard = true } = {}) {
  const box = page.locator('.message-input textarea')
  await box.waitFor({ state: 'visible' })
  const grew = (n) =>
    page.waitForFunction((c) => document.querySelectorAll('article.turn').length > c, n, {
      timeout: 15000,
    })

  const before = await page.locator('article.turn').count()
  await box.fill(text)
  await page.click('.message-input button.send')
  if (!waitForCard) return

  try {
    await grew(before)
  } catch {
    /* 늦게 도착했을 뿐이면 다시 치지 않는다 — 같은 문장이 두 번 올라간다 */
    if ((await page.locator('article.turn').count()) === before) {
      await box.fill(text)
      await page.click('.message-input button.send')
      await grew(before)
    }
  }
  await page.locator('article.turn').last().waitFor({ state: 'visible' })
}

const browser = await chromium.launch({ channel: 'chrome' })
const ctx = await browser.newContext({
  viewport: { width: WIDTH, height: BASE_HEIGHT },
  deviceScaleFactor: DPR,
  locale: 'ko-KR',
  timezoneId: 'Asia/Seoul',
  reducedMotion: 'reduce',
})
const page = await ctx.newPage()

await mkdir(path.join(ASSETS, 'full'), { recursive: true })
await mkdir(path.join(ASSETS, 'nodes'), { recursive: true })

/*
 * 감사 콘솔을 먼저 찍는다. 직원 챗에서 전송하면 픽스처 서버에 행이 쌓여 목록 건수가
 * 달라진다. 시드 상태 그대로의 목록을 남기려면 순서가 이래야 한다.
 */
console.log('SCR-02 감사 콘솔')
await open(page, USERS.infosec, '/admin/audit')
await page.locator('.split .list tbody tr.row').first().waitFor({ state: 'visible' })
await shot(page, '08_audit_list', { node: '.split .list' })

/* 검토 대기 + 분석 완료 행이라야 AI 후보에 ACCEPT/REJECT가 붙는다 */
const targetRow = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.split .list tbody tr.row')]
  const hit = rows.find((r) => {
    const cells = [...r.querySelectorAll('td')]
    return cells[3]?.innerText.includes('검토 대기') && cells[5]?.innerText.includes('분석 완료')
  })
  if (!hit) return -1
  return [...document.querySelectorAll('.split .list tbody tr.row')].indexOf(hit)
})
if (targetRow === -1) throw new Error('검토 대기 · 분석 완료 행을 찾지 못했다')
await page.locator('.split .list tbody tr.row').nth(targetRow).click()
await page.locator('.panel .candidates button.accept').first().waitFor({ state: 'visible' })
await page.waitForTimeout(400)
await shot(page, '09_audit_detail_before', { node: 'aside.panel' })

await page.locator('.panel .candidates button.accept').first().click()
await page.waitForTimeout(1500)
await shot(page, '10_audit_detail_after', { node: 'aside.panel' })

console.log('SCR-01 직원 챗')
/*
 * 01 — S1 초기.
 *
 * 노드 크롭은 입력 블록만 잘라낸다. 인사말까지 함께 담으면 그 사이의 여백
 * (대화가 시작되면 0으로 줄어드는 tail)이 이미지 절반을 먹는다. 인사말이 있는
 * 초기 상태 전체는 full/ 쪽에 남는다.
 */
await open(page, USERS.dev, '/chat')
await shot(page, '01_s1_input', { node: '.chat footer.composer' })

/* 02 — 허용. 같은 문장이 개발팀에서는 규칙 0건으로 통과한다 */
await open(page, USERS.dev, '/chat')
await send(page, PROMPTS.review)
await shot(page, '02_allow', { node: 'article.turn' })

/* 03 — 마스킹 */
await open(page, USERS.hr, '/chat')
await send(page, PROMPTS.mask)
await shot(page, '03_mask', { node: 'article.turn' })

/* 04 — 차단. 정규식 4건이 걸리지만 중첩 억제로 화면에는 규칙 2건 */
await open(page, USERS.dev, '/chat')
await send(page, PROMPTS.block)
await shot(page, '04_block', { node: 'article.turn' })

/* 05 — 엠바고 차단. 기준일 2026-09-04에는 ATLAS가 이미 풀려 NOVA만 걸린다 */
await open(page, USERS.dev, '/chat')
await send(page, PROMPTS.embargo)
await shot(page, '05_embargo', { node: 'article.turn' })

/* 06·07 — 검토 대기 → AI 후보. 같은 턴의 앞뒤라 한 번에 찍는다 */
await open(page, USERS.sales, '/chat')
await send(page, PROMPTS.review)
/*
 * 폴링이 한 번 돈 뒤에 찍는다. 보내자마자 찍으면 "폴링 0회"가 남아 이 화면이
 * 증명해야 할 것(주기적으로 다시 묻고 있다)이 화면에 없다.
 * 픽스처는 2초 주기로 묻고 2.5초에 완료되므로 1회차와 2회차 사이가 유일한 창이다.
 */
await page.getByText(/폴링 [1-9]\d*회/).waitFor({ state: 'visible', timeout: 20000 })
await shot(page, '06_review_pending', { node: 'article.turn' })

await page.locator('article.turn .candidates').last().waitFor({ state: 'visible', timeout: 20000 })
await page.waitForTimeout(600)
await shot(page, '07_ai_candidate', { node: 'article.turn' })

await browser.close()

await writeFile(
  path.join(ASSETS, 'capture-manifest.json'),
  `${JSON.stringify({ base: BASE, width: WIDTH, dpr: DPR, shots: captured }, null, 2)}\n`,
)
console.log(`\n${captured.length}장 — ${ASSETS}`)
