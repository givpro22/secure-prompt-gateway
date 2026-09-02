/*
 * 화면 용어 — 기획서 5.6 고정. 내부 enum 값을 화면에 그대로 노출하지 않는다.
 * enum → 한글 라벨 매핑은 이 파일 한 곳에서만 한다.
 */

/** message.status / decision / finding.reviewStatus → { label, token } */
export const STATUS_TERMS = {
  // decision (POST 응답)
  ALLOW: { label: '허용', token: 'green' },
  MASK: { label: '마스킹', token: 'amber' },
  BLOCK: { label: '차단', token: 'red' },
  PENDING: { label: '검토 대기', token: 'purple' },
  // message.status
  ALLOWED: { label: '허용', token: 'green' },
  MASKED: { label: '마스킹', token: 'amber' },
  BLOCKED: { label: '차단', token: 'red' },
  PENDING_REVIEW: { label: '검토 대기', token: 'purple' },
  // finding.reviewStatus
  SUGGESTED: { label: '제안됨', token: 'purple' },
  ACCEPTED: { label: '확정(위반)', token: 'red' },
  REJECTED: { label: '기각', token: 'gray' },
  CONFIRMED: { label: '확정(규칙)', token: 'blue' },
}

/**
 * inspection.aiStatus → 보조 텍스트. SKIPPED는 공란 (화면 명세 1.2)
 *
 * **D16 — `aiStatus`는 "분석", `message.status`·`reviewStatus`는 "검토"다.**
 * "검토"는 5.6이 사람의 절차에 예약한 단어다. 감사 목록 한 행에 판정 "검토 대기"와
 * AI 상태가 나란히 서는데 후자까지 "검토 중"이면 한 행에 "검토"가 두 번 나와
 * 행위 주체가 구분되지 않는다. 4장 책임 경계가 이 프로젝트의 핵심 주장이라
 * 화면에서 흐려지면 안 된다.
 */
export const AI_STATUS_TERMS = {
  SKIPPED: '',
  PENDING: '분석 중',
  COMPLETED: '분석 완료',
  FAILED: '분석 실패',
}

export const ACTION_TERMS = { MASK: '마스킹', BLOCK: '차단', REVIEW: '검토' }
export const OBLIGATION_TERMS = { LEGAL: '법령', INTERNAL: '사규' }
/*
 * EMBARGO는 '기밀'과 다르다. 기밀은 정보가 민감해서 막고, 엠바고는 아직 때가 아니라서 막는다.
 * 같은 문장이 해제일 다음 날에는 그냥 통과하므로 라벨도 갈라 둔다.
 */
export const CATEGORY_TERMS = {
  PII: '개인정보',
  SECRET: '자격증명',
  CONFIDENTIAL: '기밀',
  EMBARGO: '엠바고',
}
export const SEVERITY_TERMS = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' }
export const DECIDED_BY_TERMS = { RULE: '규칙', HUMAN: '담당자' }
export const SCOPE_TERMS = { GLOBAL: '전사', DEPT: '부서' }
export const APPLIED_VIA_TERMS = { GLOBAL: '전사 적용', DEPT: '부서 적용' }

/** 감사 콘솔 상태 필터 옵션 — message.status 4값 */
export const STATUS_FILTER_OPTIONS = [
  { value: 'ALLOWED', label: '허용' },
  { value: 'MASKED', label: '마스킹' },
  { value: 'BLOCKED', label: '차단' },
  { value: 'PENDING_REVIEW', label: '검토 대기' },
]

/**
 * 마스킹 라벨 5종 (기획서 7.2 규칙 8종의 mask_label).
 * D3 — finding의 span은 원문 기준이라 마스킹본에서 밀린다. 오프셋 산술 대신 이 문자열을 검색한다.
 */
export const MASK_LABELS = ['[주민번호]', '[카드번호]', '[전화번호]', '[이메일]', '[내부IP]']

export function term(map, value, fallback = '—') {
  if (value === null || value === undefined || value === '') return fallback
  return map[value] ?? value
}
