/*
 * 개발 전용 픽스처 서버 (Vite 플러그인).
 *
 * 백엔드 컨트롤러가 아직 없는 동안 SCR-01 5상태와 SCR-02를 실제로 렌더링해 확인하기 위한 것이다.
 * 응답 shape은 `_workspace/01_api-ai-architect_contract-freeze.md` §1을 따른다 —
 * Postman Example은 일부 필드를 생략한 축약본이라 계약서 표를 기준으로 삼았다.
 * 판정 로직은 기획서 7.2 규칙 8종 + D1 중첩 억제 + D5 마스킹 분기를 그대로 흉내 낸다.
 *
 * 애플리케이션 코드는 이 파일을 import하지 않는다. `npm run dev:fixtures`로만 켜지고
 * 프로덕션 번들에 들어가지 않는다. 실제 BE가 뜨면 `npm run dev`로 돌아간다.
 */

const DEPARTMENTS = [
  { deptId: 1, code: 'DEV', name: '개발팀' },
  { deptId: 2, code: 'SALES', name: '영업팀' },
  { deptId: 3, code: 'HR', name: '인사팀' },
  { deptId: 4, code: 'INFOSEC', name: '정보보안팀' },
]

const USERS = [
  { userId: 1, name: '이OO', email: 'lee@example.com', role: 'EMPLOYEE', deptId: 1 },
  { userId: 2, name: '김OO', email: 'kim@example.com', role: 'EMPLOYEE', deptId: 2 },
  { userId: 3, name: '정OO', email: 'jung@example.com', role: 'EMPLOYEE', deptId: 3 },
  { userId: 4, name: '박OO', email: 'park@example.com', role: 'SECURITY_ADMIN', deptId: 4 },
]

/** 기획서 7.2 규칙 8종 */
const RULES = [
  {
    ruleId: 1, code: 'PII-RRN-01', policyCode: 'P-PII', ruleType: 'REGEX',
    pattern: /\d{6}-?[1-4]\d{6}/g, action: 'MASK', maskLabel: '[주민번호]',
    severity: 'HIGH', obligation: 'LEGAL', source: '개인정보보호법 제24조', description: '주민등록번호 마스킹',
  },
  {
    ruleId: 2, code: 'PII-CARD-02', policyCode: 'P-PII', ruleType: 'REGEX',
    pattern: /\b(?:\d{4}-?){3}\d{4}\b/g, action: 'MASK', maskLabel: '[카드번호]',
    severity: 'HIGH', obligation: 'LEGAL', source: '개인정보보호법', description: '카드번호 마스킹',
  },
  {
    ruleId: 3, code: 'PII-PHONE-03', policyCode: 'P-PII', ruleType: 'REGEX',
    pattern: /01[016789]-?\d{3,4}-?\d{4}/g, action: 'MASK', maskLabel: '[전화번호]',
    severity: 'MEDIUM', obligation: 'LEGAL', source: '개인정보보호법', description: '휴대전화번호 마스킹',
  },
  {
    ruleId: 4, code: 'PII-EMAIL-04', policyCode: 'P-PII', ruleType: 'REGEX',
    pattern: /[\w.+-]+@[\w-]+\.[\w.]+/g, action: 'MASK', maskLabel: '[이메일]',
    severity: 'LOW', obligation: 'LEGAL', source: '개인정보보호법', description: '이메일 마스킹',
  },
  {
    ruleId: 5, code: 'SEC-AWSKEY-01', policyCode: 'P-SEC', ruleType: 'REGEX',
    pattern: /AKIA[0-9A-Z]{16}/g, action: 'BLOCK', maskLabel: null,
    severity: 'HIGH', obligation: 'INTERNAL', source: '정보보안규정 4.2', description: 'AWS 액세스 키 차단',
  },
  {
    ruleId: 6, code: 'SEC-DBURL-02', policyCode: 'P-SEC', ruleType: 'REGEX',
    pattern: /(?:postgres|mysql|jdbc)[\w+]*:\/\/[^\s]+/g, action: 'BLOCK', maskLabel: null,
    severity: 'HIGH', obligation: 'INTERNAL', source: '정보보안규정 4.2', description: 'DB 접속 문자열 차단',
  },
  {
    ruleId: 7, code: 'SEC-PRIVIP-03', policyCode: 'P-SEC', ruleType: 'REGEX',
    pattern: /\b(?:10\.\d{1,3}|192\.168|172\.(?:1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b/g,
    action: 'MASK', maskLabel: '[내부IP]',
    severity: 'MEDIUM', obligation: 'INTERNAL', source: '정보보안규정 4.3', description: '사설 IP 마스킹',
  },
  {
    ruleId: 8, code: 'CONF-CLIENT-01', policyCode: 'P-CONF', ruleType: 'KEYWORD',
    keywords: ['A사', 'B사', 'C사', '프로젝트 오메가', '차세대'], action: 'REVIEW', maskLabel: null,
    severity: 'MEDIUM', obligation: 'INTERNAL', source: '고객사 NDA 목록 v3', description: '고객사명·프로젝트명 언급 시 검토',
  },
]

const POLICIES = [
  { policyId: 1, code: 'P-PII', name: '개인정보 보호', category: 'PII', version: 3, scope: 'GLOBAL' },
  { policyId: 2, code: 'P-SEC', name: '자격증명·인프라 정보 보호', category: 'SECRET', version: 7, scope: 'GLOBAL' },
  { policyId: 3, code: 'P-CONF', name: '고객사 프로젝트 정보 통제', category: 'CONFIDENTIAL', version: 2, scope: 'DEPT' },
]

/** 기획서 7.3 부서별 적용 매트릭스. 개발팀 2건, 영업·인사팀 3건 (D8) */
const DEPT_POLICY = { 1: ['P-PII', 'P-SEC'], 2: ['P-PII', 'P-SEC', 'P-CONF'], 3: ['P-PII', 'P-SEC', 'P-CONF'], 4: ['P-PII', 'P-SEC'] }

const AI_MOCK_DELAY_MS = 2500
const AI_MOCK_FAIL_KEYWORD = '__FAIL__'
const POLL_INTERVAL_MS = 2000

function policiesForDept(deptId) {
  const codes = DEPT_POLICY[deptId] ?? []
  return POLICIES.filter((p) => codes.includes(p.code)).map((policy) => ({
    ...policy,
    appliedVia: policy.scope === 'GLOBAL' ? 'GLOBAL' : 'DEPT',
    rules: RULES.filter((r) => r.policyCode === policy.code).map((rule) => ({
      ruleId: rule.ruleId,
      code: rule.code,
      ruleType: rule.ruleType,
      action: rule.action,
      maskLabel: rule.maskLabel,
      severity: rule.severity,
      obligation: rule.obligation,
      source: rule.source,
      description: rule.description,
      // pattern은 응답에 넣지 않는다 (C5)
    })),
  }))
}

function rulesForDept(deptId) {
  const codes = DEPT_POLICY[deptId] ?? []
  return RULES.filter((r) => codes.includes(r.policyCode))
}

function categoryOf(rule) {
  return POLICIES.find((p) => p.code === rule.policyCode).category
}

/** 규칙 판정 — 매칭 → D1 중첩 억제 → 우선순위 결정 → D5 마스킹 */
function evaluate(text, deptId) {
  const rules = rulesForDept(deptId)
  const raw = []

  for (const rule of rules) {
    if (rule.ruleType === 'REGEX') {
      const re = new RegExp(rule.pattern.source, 'g')
      let m
      while ((m = re.exec(text)) !== null) {
        raw.push({ rule, start: m.index, end: m.index + m[0].length, keyword: null })
        if (m[0].length === 0) re.lastIndex += 1
      }
    } else {
      for (const keyword of rule.keywords) {
        let from = 0
        let idx
        while ((idx = text.indexOf(keyword, from)) !== -1) {
          raw.push({ rule, start: idx, end: idx + keyword.length, keyword })
          from = idx + keyword.length
        }
      }
    }
  }

  // D1 — 다른 매칭 구간에 완전히 포함된 매칭은 finding으로 세지 않는다
  const kept = raw.filter(
    (m) => !raw.some((other) => other !== m && other.start <= m.start && other.end >= m.end && (other.end - other.start) > (m.end - m.start)),
  )

  // D9 — finding은 규칙당 1건, matchedKeyword는 첫 매칭
  const byRule = new Map()
  for (const m of kept.sort((a, b) => a.start - b.start)) {
    if (!byRule.has(m.rule.code)) byRule.set(m.rule.code, m)
  }
  const matched = [...byRule.values()].sort((a, b) => a.start - b.start)

  const actions = new Set(matched.map((m) => m.rule.action))
  let decision = 'ALLOW'
  if (actions.has('BLOCK')) decision = 'BLOCK'
  else if (actions.has('REVIEW')) decision = 'PENDING'
  else if (actions.has('MASK')) decision = 'MASK'

  // D5 — 마스킹은 최종 판정이 BLOCK이 아닐 때만. 치환은 뒤에서 앞으로
  let submittedText = text
  if (decision !== 'BLOCK') {
    const maskTargets = kept
      .filter((m) => m.rule.action === 'MASK')
      .sort((a, b) => b.start - a.start)
    for (const m of maskTargets) {
      submittedText = submittedText.slice(0, m.start) + m.rule.maskLabel + submittedText.slice(m.end)
    }
  } else {
    submittedText = null
  }

  const matches = matched.map((m) => ({
    code: m.rule.code,
    category: categoryOf(m.rule),
    action: m.rule.action,
    span: [m.start, m.end],
    matchedKeyword: m.keyword,
    severity: m.rule.severity,
    obligation: m.rule.obligation,
    source: m.rule.source,
  }))

  const policies = policiesForDept(deptId)
  return {
    decision,
    submittedText,
    ruleResult: { matches, appliedRuleCodes: rules.map((r) => r.code) },
    policySnapshot: {
      policies: policies.map((p) => ({
        policyId: p.policyId,
        code: p.code,
        version: p.version,
        ruleCodes: p.rules.map((r) => r.code),
      })),
    },
  }
}

const STATUS_OF = { ALLOW: 'ALLOWED', MASK: 'MASKED', BLOCK: 'BLOCKED', PENDING: 'PENDING_REVIEW' }

/** Case B 픽스처 (`mock/ai/case-b-client-project.json` 상당) */
function assessmentFor(text) {
  if (text.includes('A사')) {
    return {
      riskCandidates: [
        {
          code: 'CONF-CLIENT-PROJECT',
          category: 'CONFIDENTIAL',
          rationale: "'A사 차세대 프로젝트 오픈 일정'이라는 서술이 계약 상대방과 미공개 일정을 동시에 특정함",
          evidence: [{ source: '고객사 NDA 목록 v3', excerpt: 'A사 — 비밀유지 2027.03까지, 일정·범위 포함' }],
        },
      ],
      missingContext: ['해당 일정이 대외 공개된 정보인지 확인 필요'],
      reviewRequired: true,
    }
  }
  return {
    riskCandidates: [],
    missingContext: ['참조 근거와 대조할 사내 문서 없음'],
    reviewRequired: true,
  }
}

export function createFixtureState() {
  const inspections = new Map()
  let nextMessageId = 1041
  let nextInspectionId = 2088
  let nextFindingId = 501

  function createInspection({ text, userId, createdAt = new Date().toISOString(), forceAiStatus = null }) {
    const user = USERS.find((u) => u.userId === userId) ?? USERS[0]
    const dept = DEPARTMENTS.find((d) => d.deptId === user.deptId)
    const result = evaluate(text, user.deptId)

    const messageId = nextMessageId++
    const inspectionId = nextInspectionId++
    const isReview = result.decision === 'PENDING'

    const findings = result.ruleResult.matches.map((match) => ({
      findingId: nextFindingId++,
      source: 'RULE',
      code: match.code,
      category: match.category,
      spanStart: match.span[0],
      spanEnd: match.span[1],
      action: match.action,
      rationale: null,
      evidence: null,
      reviewStatus: 'CONFIRMED',
      reviewedBy: null,
      reviewedAt: null,
    }))

    const inspection = {
      inspectionId,
      messageId,
      phase: 'INPUT',
      user: { userId: user.userId, name: user.name, department: dept.name },
      submittedText: result.submittedText,
      status: STATUS_OF[result.decision],
      policySnapshot: result.policySnapshot,
      ruleResult: result.ruleResult,
      aiStatus: isReview ? 'PENDING' : 'SKIPPED',
      aiAssessment: null,
      findings,
      finalDecision: isReview ? 'PENDING' : result.decision,
      decidedBy: isReview ? null : 'RULE',
      createdAt,
      completedAt: isReview ? null : createdAt,
      // 내부용 (응답에 싣지 않는다)
      _deptId: user.deptId,
      _text: text,
      _aiReadyAt: isReview ? Date.now() + AI_MOCK_DELAY_MS : 0,
      _forceAiStatus: forceAiStatus,
    }

    inspections.set(inspectionId, inspection)
    return { inspection, decision: result.decision, messageId }
  }

  /** 폴링 시점에 Mock 지연(2.5초)이 지났으면 AI 결과를 채운다 */
  function settleAi(inspection) {
    if (inspection.aiStatus !== 'PENDING') return
    if (Date.now() < inspection._aiReadyAt) return

    const failed =
      inspection._forceAiStatus === 'FAILED' || inspection._text.includes(AI_MOCK_FAIL_KEYWORD)
    inspection.completedAt = new Date().toISOString()

    if (failed) {
      // 실패해도 status는 PENDING_REVIEW를 유지한다 (9.5, UC-03 예외)
      inspection.aiStatus = 'FAILED'
      inspection.aiAssessment = null
      return
    }

    const assessment = assessmentFor(inspection._text)
    inspection.aiStatus = 'COMPLETED'
    inspection.aiAssessment = assessment
    for (const candidate of assessment.riskCandidates) {
      inspection.findings.push({
        findingId: nextFindingId++,
        source: 'AI',
        code: candidate.code,
        category: candidate.category,
        spanStart: null,
        spanEnd: null,
        action: null,
        rationale: candidate.rationale,
        evidence: candidate.evidence,
        reviewStatus: 'SUGGESTED',
        reviewedBy: null,
        reviewedAt: null,
      })
    }
  }

  /** 계약서 §1-7 최종 판정 재산출 */
  function recompute(inspection, reviewer) {
    const ai = inspection.findings.filter((f) => f.source === 'AI')
    const now = new Date().toISOString()
    if (ai.some((f) => f.reviewStatus === 'ACCEPTED')) {
      inspection.finalDecision = 'BLOCK'
      inspection.status = 'BLOCKED'
      inspection.decidedBy = 'HUMAN'
      inspection.completedAt = now
      // D14 — submitted_text는 건드리지 않는다. NULL은 "마스킹본이 생성된 적이 없다"는 뜻이고
      // 규칙 BLOCK 경로에서만 발생한다. 사람이 확정한 BLOCK은 본문을 보존한다
    } else if (ai.every((f) => f.reviewStatus === 'REJECTED')) {
      inspection.finalDecision = 'ALLOW'
      inspection.status = 'ALLOWED'
      inspection.decidedBy = 'HUMAN'
      inspection.completedAt = now
    }
    return reviewer
  }

  function toRow(inspection) {
    settleAi(inspection)
    return {
      inspectionId: inspection.inspectionId,
      createdAt: inspection.createdAt,
      department: inspection.user.department,
      userName: inspection.user.name,
      status: inspection.status,
      ruleCount: inspection.findings.filter((f) => f.source === 'RULE').length,
      aiStatus: inspection.aiStatus,
      decidedBy: inspection.decidedBy,
    }
  }

  function toDetail(inspection) {
    settleAi(inspection)
    const { _deptId, _text, _aiReadyAt, _forceAiStatus, ...rest } = inspection
    return rest
  }

  // ── 감사 로그 시드 ────────────────────────────────────────────
  const SEED_TEXTS = [
    { text: '이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나', userId: 1 },
    { text: '지원자 연락처 010-1234-5678 로 면접 안내 문자 초안 써줘', userId: 3 },
    { text: 'A사 차세대 프로젝트 오픈 일정이 언제였지?', userId: 2 },
    { text: 'A사 차세대 프로젝트 오픈 일정이 언제였지?', userId: 1 },
    { text: '스프링 부트에서 트랜잭션 전파 옵션 설명해줘', userId: 1 },
    { text: '고객 카드번호 4111-1111-1111-1111 결제 오류 원인 알려줘', userId: 2 },
    { text: '사내 위키 문서 요약해줘', userId: 3 },
    { text: 'B사 프로젝트 오메가 산출물 목록 정리해줘 __FAIL__', userId: 2, forceAiStatus: 'FAILED' },
  ]

  const day = 24 * 60 * 60 * 1000
  for (let i = 0; i < 26; i += 1) {
    const seed = SEED_TEXTS[i % SEED_TEXTS.length]
    const createdAt = new Date(Date.now() - (i % 7) * day - (i * 37 + 11) * 60 * 1000).toISOString()
    const { inspection } = createInspection({ ...seed, createdAt })
    if (inspection.aiStatus === 'PENDING') {
      inspection._aiReadyAt = 0 // 과거 기록은 이미 AI 검토가 끝났다
      settleAi(inspection)
    }
  }

  return { inspections, createInspection, settleAi, recompute, toRow, toDetail }
}

function send(res, status, body, headers = {}) {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  for (const [key, value] of Object.entries(headers)) res.setHeader(key, value)
  res.end(JSON.stringify(body))
}

function fail(res, status, code, message) {
  send(res, status, { code, message, details: null })
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = ''
    req.on('data', (chunk) => {
      raw += chunk
    })
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch (err) {
        reject(err)
      }
    })
    req.on('error', reject)
  })
}

export function fixtureServer() {
  const state = createFixtureState()

  return {
    name: 'gateway-fixture-server',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/api/v1', async (req, res, next) => {
        const url = new URL(req.url, 'http://localhost')
        const path = url.pathname
        const query = url.searchParams
        const userId = Number(req.headers['x-user-id'])

        try {
          if (req.method === 'GET' && path === '/departments') {
            return send(res, 200, { items: DEPARTMENTS, page: 0, size: DEPARTMENTS.length, total: DEPARTMENTS.length })
          }

          if (req.method === 'GET' && path === '/users') {
            const deptId = query.get('deptId')
            const items = USERS.filter((u) => deptId === null || u.deptId === Number(deptId)).map((u) => ({
              userId: u.userId,
              name: u.name,
              email: u.email,
              role: u.role,
              department: DEPARTMENTS.find((d) => d.deptId === u.deptId),
            }))
            return send(res, 200, { items, page: 0, size: items.length, total: items.length })
          }

          if (req.method === 'GET' && path === '/policies') {
            const deptId = query.get('deptId')
            if (deptId === null || Number.isNaN(Number(deptId))) {
              return fail(res, 400, 'INVALID_PARAMETER', 'deptId is required')
            }
            const items = policiesForDept(Number(deptId))
            return send(res, 200, { items, page: 0, size: items.length, total: items.length })
          }

          if (req.method === 'POST' && path === '/messages') {
            if (!req.headers['x-user-id']) return fail(res, 400, 'MISSING_USER_HEADER', 'X-User-Id header is required')
            if (!USERS.some((u) => u.userId === userId)) return fail(res, 400, 'INVALID_USER', `user ${userId} not found`)

            const body = await readBody(req)
            if (typeof body.text !== 'string' || body.text.trim() === '') {
              return fail(res, 400, 'INVALID_REQUEST', 'text must not be blank')
            }

            const { inspection, decision, messageId } = state.createInspection({ text: body.text, userId })
            const verdict = {
              messageId,
              inspectionId: inspection.inspectionId,
              decision,
              status: inspection.status,
              submittedText: inspection.submittedText,
              policySnapshot: inspection.policySnapshot,
              ruleResult: inspection.ruleResult,
              aiStatus: inspection.aiStatus,
              decidedBy: inspection.decidedBy,
              pollAfterMs: decision === 'PENDING' ? POLL_INTERVAL_MS : null,
              createdAt: inspection.createdAt,
            }
            if (decision === 'BLOCK') return send(res, 403, verdict)
            if (decision === 'PENDING') {
              return send(res, 202, verdict, { Location: `/api/v1/inspections/${inspection.inspectionId}` })
            }
            return send(res, 200, verdict)
          }

          if (req.method === 'GET' && path === '/inspections') {
            let items = [...state.inspections.values()].map((i) => state.toRow(i))
            const deptId = query.get('deptId')
            const status = query.get('status')
            const from = query.get('from')
            const to = query.get('to')
            if (deptId) {
              const name = DEPARTMENTS.find((d) => d.deptId === Number(deptId))?.name
              items = items.filter((r) => r.department === name)
            }
            if (status) items = items.filter((r) => r.status === status)
            if (from) items = items.filter((r) => r.createdAt >= from)
            if (to) items = items.filter((r) => r.createdAt < to)
            items.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))

            const page = Number(query.get('page') ?? 0)
            const size = Math.min(Number(query.get('size') ?? 20), 100)
            const total = items.length
            return send(res, 200, { items: items.slice(page * size, page * size + size), page, size, total })
          }

          const detailMatch = /^\/inspections\/(\d+)$/.exec(path)
          if (req.method === 'GET' && detailMatch) {
            const inspection = state.inspections.get(Number(detailMatch[1]))
            if (!inspection) return fail(res, 404, 'INSPECTION_NOT_FOUND', `inspection ${detailMatch[1]} not found`)
            return send(res, 200, state.toDetail(inspection))
          }

          const patchMatch = /^\/inspections\/(\d+)\/findings\/(\d+)$/.exec(path)
          if (req.method === 'PATCH' && patchMatch) {
            if (!req.headers['x-user-id']) return fail(res, 400, 'MISSING_USER_HEADER', 'X-User-Id header is required')
            const inspection = state.inspections.get(Number(patchMatch[1]))
            if (!inspection) return fail(res, 404, 'INSPECTION_NOT_FOUND', `inspection ${patchMatch[1]} not found`)
            const finding = inspection.findings.find((f) => f.findingId === Number(patchMatch[2]))
            if (!finding) return fail(res, 404, 'FINDING_NOT_FOUND', `finding ${patchMatch[2]} not found`)

            const body = await readBody(req)
            if (!['ACCEPTED', 'REJECTED'].includes(body.reviewStatus)) {
              return fail(res, 400, 'INVALID_REQUEST', 'reviewStatus must be ACCEPTED or REJECTED')
            }
            if (finding.reviewStatus === 'CONFIRMED') {
              return fail(res, 409, 'RULE_FINDING_NOT_REVIEWABLE', `finding ${finding.findingId} is a rule decision`)
            }
            if (finding.reviewStatus !== 'SUGGESTED') {
              return fail(res, 409, 'FINDING_ALREADY_REVIEWED', `finding ${finding.findingId} is already ${finding.reviewStatus}`)
            }

            const reviewer = USERS.find((u) => u.userId === userId) ?? USERS[3]
            finding.reviewStatus = body.reviewStatus
            finding.reviewedBy = { userId: reviewer.userId, name: reviewer.name }
            finding.reviewedAt = new Date().toISOString()
            state.recompute(inspection)

            return send(res, 200, {
              findingId: finding.findingId,
              reviewStatus: finding.reviewStatus,
              reviewedBy: finding.reviewedBy,
              reviewedAt: finding.reviewedAt,
              inspection: {
                inspectionId: inspection.inspectionId,
                finalDecision: inspection.finalDecision,
                decidedBy: inspection.decidedBy,
                status: inspection.status,
                // D14 — PATCH가 건드리지 않은 값을 그대로 싣는다
                submittedText: inspection.submittedText,
                // QA F6 — 확정 시각으로 갱신된 값
                completedAt: inspection.completedAt,
              },
            })
          }

          return next()
        } catch (err) {
          server.config.logger.error(`[fixture-server] ${err.stack}`)
          return fail(res, 500, 'FIXTURE_ERROR', String(err.message))
        }
      })

      server.config.logger.info('  ➜  픽스처 서버 활성 — /api/v1 (개발 전용)')
    },
  }
}
