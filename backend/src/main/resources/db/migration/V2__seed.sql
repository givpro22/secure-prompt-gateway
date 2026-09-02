-- =============================================================================
-- V2__seed.sql — 시드 데이터
--
-- 원본: 기획서 7.1(정책 3종) / 7.2(규칙 8종) / 7.3(부서 매트릭스) / 10장(시드 계획)
--
-- 원칙 (10.1)
--   - 재실행 가능. TRUNCATE ... RESTART IDENTITY CASCADE 후 INSERT
--   - 이름은 실명 대신 `김OO` 형식, 이메일은 example.com
--   - 실제 개인정보 형태 문자열을 넣지 않는다. original_text도 이미 라벨이
--     적용된 상태로 저장한다 (10.5)
--
-- ID 고정
--   마스터·감사 로그·데모 백업 모두 PK를 명시적으로 지정한다. RESTART IDENTITY만
--   믿으면 INSERT 순서에 따라 ID가 밀리고 데모 URL(/inspections/{id})이 흔들린다.
--   시퀀스는 파일 끝에서 setval로 맞춘다.
--
-- 정규식 리터럴 주의
--   PostgreSQL은 standard_conforming_strings=on이 기본이므로 일반 문자열('')에서
--   백슬래시가 그대로 보존된다. E''를 쓰면 \d가 d로 유실된다. 절대 E''를 쓰지 않는다.
-- =============================================================================

TRUNCATE inspection_finding, inspection, message,
         department_policy, policy_rule, policy, app_user, department
    RESTART IDENTITY CASCADE;

-- -----------------------------------------------------------------------------
-- 1. 부서 4행 (기획서 10.2, 0.5 D2)
--    정보보안팀 code는 INFOSEC. `SEC`는 정책 코드 P-SEC와 혼동되어 쓰지 않는다.
-- -----------------------------------------------------------------------------
INSERT INTO department (dept_id, code, name) VALUES
    (1, 'DEV',     '개발팀'),
    (2, 'SALES',   '영업팀'),
    (3, 'HR',      '인사팀'),
    (4, 'INFOSEC', '정보보안팀');

-- -----------------------------------------------------------------------------
-- 2. 사용자 4명 (기획서 10.2)
-- -----------------------------------------------------------------------------
INSERT INTO app_user (user_id, dept_id, name, email, role) VALUES
    (1, 1, '이OO', 'lee@example.com',  'EMPLOYEE'),        -- Case A, Case C
    (2, 2, '김OO', 'kim@example.com',  'EMPLOYEE'),        -- Case B
    (3, 3, '정OO', 'jung@example.com', 'EMPLOYEE'),        -- 시드 로그
    (4, 4, '박OO', 'park@example.com', 'SECURITY_ADMIN');  -- SCR-02 확정

-- -----------------------------------------------------------------------------
-- 3. 정책 3종 (기획서 7.1)
--    P-CONF만 scope=DEPT. 나머지는 GLOBAL이라 department_policy 매핑이 없다 (6.4).
--
--    version은 기획서 8.4의 policySnapshot 예시(P-PII:3, P-SEC:7, P-CONF:2)를 그대로
--    쓴다. 계약서 §4의 policyVersion 예시 `P-CONF:2;P-PII:3;P-SEC:7`이 이 값들로
--    조립되며, Mock의 결정론적 픽스처가 이 문자열에 걸려 있다. 전부 1로 두면 문서의
--    모든 예시가 시드와 어긋난다.
-- -----------------------------------------------------------------------------
INSERT INTO policy (policy_id, code, name, category, version, is_active, scope) VALUES
    (1, 'P-PII',  '개인정보 보호',             'PII',          3, true, 'GLOBAL'),
    (2, 'P-SEC',  '자격증명·인프라 정보 보호', 'SECRET',       7, true, 'GLOBAL'),
    (3, 'P-CONF', '고객사 프로젝트 정보 통제', 'CONFIDENTIAL', 2, true, 'DEPT');

-- -----------------------------------------------------------------------------
-- 4. 규칙 8종 (기획서 7.2) — pattern은 기획서와 문자 단위로 일치해야 한다
-- -----------------------------------------------------------------------------
INSERT INTO policy_rule
    (rule_id, policy_id, code, rule_type, pattern, action, mask_label, severity, obligation, source, description) VALUES
    (1, 1, 'PII-RRN-01',    'REGEX',   '\d{6}-?[1-4]\d{6}',
                            'MASK',  '[주민번호]', 'HIGH',   'LEGAL',    '개인정보보호법 제24조', '주민등록번호 형식 탐지'),
    (2, 1, 'PII-CARD-02',   'REGEX',   '\b(\d{4}-?){3}\d{4}\b',
                            'MASK',  '[카드번호]', 'HIGH',   'LEGAL',    '개인정보보호법',        '신용카드 번호 형식 탐지'),
    (3, 1, 'PII-PHONE-03',  'REGEX',   '01[016789]-?\d{3,4}-?\d{4}',
                            'MASK',  '[전화번호]', 'MEDIUM', 'LEGAL',    '개인정보보호법',        '휴대전화 번호 형식 탐지'),
    (4, 1, 'PII-EMAIL-04',  'REGEX',   '[\w.+-]+@[\w-]+\.[\w.]+',
                            'MASK',  '[이메일]',   'LOW',    'LEGAL',    '개인정보보호법',        '이메일 주소 형식 탐지'),
    (5, 2, 'SEC-AWSKEY-01', 'REGEX',   'AKIA[0-9A-Z]{16}',
                            'BLOCK', NULL,         'HIGH',   'INTERNAL', '정보보안규정 4.2',      'AWS 액세스 키 ID 탐지'),
    (6, 2, 'SEC-DBURL-02',  'REGEX',   '(postgres|mysql|jdbc)[\w+]*://[^\s]+',
                            'BLOCK', NULL,         'HIGH',   'INTERNAL', '정보보안규정 4.2',      'DB 접속 문자열 탐지'),
    (7, 2, 'SEC-PRIVIP-03', 'REGEX',   '\b(10\.\d{1,3}|192\.168|172\.(1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b',
                            'MASK',  '[내부IP]',   'MEDIUM', 'INTERNAL', '정보보안규정 4.3',      '사설 IP 대역 탐지'),
    (8, 3, 'CONF-CLIENT-01', 'KEYWORD', 'A사,B사,C사,프로젝트 오메가,차세대',
                            'REVIEW', NULL,        'MEDIUM', 'INTERNAL', '고객사 NDA 목록 v3',    '고객사명·프로젝트명 언급 시 검토');

-- -----------------------------------------------------------------------------
-- 5. 부서 N:M 정책 매핑 2행 (기획서 7.3, 10.3)
--    GLOBAL 정책은 매핑하지 않는다. INFOSEC은 검토자 역할이라 매핑이 없다 (0.5 D2).
-- -----------------------------------------------------------------------------
INSERT INTO department_policy (dept_id, policy_id) VALUES
    (2, 3),   -- 영업팀 ← P-CONF
    (3, 3);   -- 인사팀 ← P-CONF

-- =============================================================================
-- 6. 감사 로그 100건 (기획서 10.5)
--
--    분포 → 상태 ALLOWED 55 / MASKED 25 / BLOCKED 12 / PENDING_REVIEW 8
--           부서 DEV 45 / SALES 35 / HR 20
--    PENDING_REVIEW 8건 → ai_status COMPLETED 5(AI finding SUGGESTED) / FAILED 3
--
--    부서 배분은 (i*37)%92 순열의 순위로 나눈다. 상태 블록이 연속이라 단순 modulo를
--    쓰면 특정 상태에 특정 부서가 몰린다. PENDING_REVIEW 8건은 P-CONF가 적용되는
--    SALES/HR로 고정한다 — DEV에 CONF-CLIENT-01이 걸리면 7.3 매트릭스와 어긋난다.
-- =============================================================================

-- 부서별 적용 정책 스냅샷과 appliedRuleCodes를 한 번만 계산해 재사용한다.
-- 마이그레이션 끝에서 DROP한다.
DROP TABLE IF EXISTS seed_dept_ctx;
CREATE TABLE seed_dept_ctx (
    dept_id            BIGINT PRIMARY KEY,
    policy_snapshot    JSONB NOT NULL,
    applied_rule_codes JSONB NOT NULL
);

INSERT INTO seed_dept_ctx (dept_id, policy_snapshot, applied_rule_codes)
SELECT d.dept_id,
       jsonb_build_object('policies', (
           SELECT jsonb_agg(jsonb_build_object(
                      'policyId',  p.policy_id,
                      'code',      p.code,
                      'version',   p.version,
                      'ruleCodes', (SELECT jsonb_agg(r.code ORDER BY r.code)
                                      FROM policy_rule r
                                     WHERE r.policy_id = p.policy_id AND r.is_active)
                  ) ORDER BY p.policy_id)
             FROM policy p
            WHERE p.is_active
              AND (p.scope = 'GLOBAL'
                   OR EXISTS (SELECT 1 FROM department_policy dp
                               WHERE dp.dept_id = d.dept_id AND dp.policy_id = p.policy_id))
       )),
       (SELECT jsonb_agg(r.code ORDER BY r.code)
          FROM policy p
          JOIN policy_rule r ON r.policy_id = p.policy_id AND r.is_active
         WHERE p.is_active
           AND (p.scope = 'GLOBAL'
                OR EXISTS (SELECT 1 FROM department_policy dp
                            WHERE dp.dept_id = d.dept_id AND dp.policy_id = p.policy_id)))
  FROM department d;

-- 100건의 파생값을 한 번 계산해 고정한다. created_at이 random()이므로 message와
-- inspection이 같은 값을 써야 한다 — 두 문장에서 각각 계산하면 시각이 어긋난다.
DROP TABLE IF EXISTS seed_audit_row;
CREATE TABLE seed_audit_row (
    i               INT PRIMARY KEY,
    dept_id         BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    rule_id         BIGINT,
    rule_code       VARCHAR(30),
    rule_category   VARCHAR(20),
    rule_action     VARCHAR(20),
    original_text   TEXT        NOT NULL,
    submitted_text  TEXT,
    span_start      INT,
    span_end        INT,
    ai_status       VARCHAR(20) NOT NULL,
    final_decision  VARCHAR(20) NOT NULL,
    decided_by      VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,
    policy_snapshot JSONB       NOT NULL,
    rule_result     JSONB       NOT NULL,
    ai_result       JSONB
);

INSERT INTO seed_audit_row
WITH tpl AS (
    SELECT ARRAY[
        '이번 분기 매출 리포트 초안 좀 잡아줘',
        '신규 입사자 온보딩 체크리스트 만들어줘',
        '배포 실패 로그 원인 분석해줘',
        '주간 회의록 요약해줘',
        '고객 문의 응대 템플릿 작성해줘',
        '재고 관리 화면 기획 문구 다듬어줘',
        '테스트 케이스 목록 정리해줘',
        '사내 공지 문구 좀 부드럽게 바꿔줘',
        '성능 개선 아이디어 다섯 개만 제안해줘',
        '연차 사용 안내 메일 초안 써줘',
        'API 문서 설명 문장 다듬어줘',
        '장애 보고서 양식에 맞춰 정리해줘',
        '경쟁사 기능 비교표 항목 뽑아줘',
        '월간 KPI 대시보드 지표 추천해줘',
        '코드 리뷰 코멘트 정중하게 다시 써줘',
        '교육 과정 커리큘럼 목차 잡아줘',
        '데이터 마이그레이션 절차 요약해줘',
        '워크숍 아이스브레이킹 아이디어 알려줘',
        '분기 목표 문장 다듬어줘',
        '고객 만족도 설문 문항 만들어줘'
    ] AS t
),
-- 상태 블록: 55 / 25 / 12 / 8
status_map AS (
    SELECT i,
           CASE WHEN i <= 55 THEN 'ALLOWED'
                WHEN i <= 80 THEN 'MASKED'
                WHEN i <= 92 THEN 'BLOCKED'
                ELSE 'PENDING_REVIEW' END AS status
      FROM generate_series(1, 100) AS i
),
-- 부서 배분: 1~92행은 DEV 45 / SALES 30 / HR 17, 93~100행은 SALES 5 / HR 3
dept_map AS (
    SELECT i, CASE WHEN rn <= 45 THEN 1 WHEN rn <= 75 THEN 2 ELSE 3 END AS dept_id
      FROM (SELECT i, row_number() OVER (ORDER BY (i * 37) % 92) AS rn
              FROM generate_series(1, 92) AS i) ranked
    UNION ALL
    SELECT i, CASE WHEN i <= 97 THEN 2 ELSE 3 END FROM generate_series(93, 100) AS i
),
-- 규칙 선택: MASKED는 PII 4종 중, BLOCKED는 BLOCK 액션 SEC 2종 중, REVIEW는 CONF
picked AS (
    SELECT s.i, s.status, d.dept_id,
           CASE s.status
                WHEN 'MASKED'         THEN ((s.i * 7) % 4) + 1
                WHEN 'BLOCKED'        THEN 5 + (s.i % 2)
                WHEN 'PENDING_REVIEW' THEN 8
           END::bigint AS rule_id,
           (SELECT t[((s.i * 13) % 20) + 1] FROM tpl) AS template
      FROM status_map s
      JOIN dept_map  d USING (i)
),
-- 본문 조립. 실제 개인정보 형태 문자열 대신 라벨을 그대로 심는다 (10.5).
texted AS (
    SELECT p.*,
           r.code AS rule_code, r.action AS rule_action, pol.category AS rule_category,
           r.severity, r.obligation, r.source AS rule_source, r.rule_type,
           CASE p.status
                WHEN 'MASKED'         THEN r.mask_label
                WHEN 'BLOCKED'        THEN CASE r.code WHEN 'SEC-AWSKEY-01' THEN '[자격증명]'
                                                       ELSE '[DB접속정보]' END
                WHEN 'PENDING_REVIEW' THEN 'A사'
           END AS token
      FROM picked p
      LEFT JOIN policy_rule r  ON r.rule_id = p.rule_id
      LEFT JOIN policy      pol ON pol.policy_id = r.policy_id
),
composed AS (
    SELECT t.*,
           CASE t.status
                WHEN 'ALLOWED'        THEN t.template
                WHEN 'MASKED'         THEN t.template || ' (참고 정보: ' || t.token || ')'
                WHEN 'BLOCKED'        THEN t.template || ' 접속 정보 ' || t.token || ' 도 같이 확인해줘'
                WHEN 'PENDING_REVIEW' THEN t.template || ' A사 관련 건도 같이 정리해줘'
           END AS original_text,
           now() - (random() * interval '7 days') AS created_at
      FROM texted t
),
spanned AS (
    SELECT c.*,
           CASE WHEN c.token IS NULL THEN NULL ELSE strpos(c.original_text, c.token) - 1 END AS span_start,
           CASE WHEN c.token IS NULL THEN NULL
                ELSE strpos(c.original_text, c.token) - 1 + length(c.token) END AS span_end,
           CASE WHEN c.status <> 'PENDING_REVIEW' THEN 'SKIPPED'
                WHEN c.i IN (96, 97, 100)         THEN 'FAILED'
                ELSE 'COMPLETED' END AS ai_status
      FROM composed c
)
SELECT s.i,
       s.dept_id,
       s.dept_id                                        AS user_id,   -- DEV→1, SALES→2, HR→3
       s.status,
       s.rule_id, s.rule_code, s.rule_category, s.rule_action,
       s.original_text,
       -- submitted_text는 규칙 BLOCK일 때만 NULL이다 (0.5 D7).
       -- PENDING_REVIEW 8건은 마스킹본을 채운다 — 이 8건이 감사 콘솔에서 ACCEPT/REJECT를
       -- 눌러볼 유일한 대상이라, 비어 있으면 상세 패널에 보여줄 본문이 없다.
       -- 템플릿이 이미 라벨 적용본이므로(10.5) 마스킹본 == original_text다.
       CASE WHEN s.status = 'BLOCKED' THEN NULL ELSE s.original_text END AS submitted_text,
       s.span_start, s.span_end,
       s.ai_status,
       CASE s.status WHEN 'ALLOWED' THEN 'ALLOW' WHEN 'MASKED' THEN 'MASK'
                     WHEN 'BLOCKED' THEN 'BLOCK' ELSE 'PENDING' END AS final_decision,
       CASE WHEN s.status = 'PENDING_REVIEW' THEN NULL ELSE 'RULE' END AS decided_by,
       s.created_at,
       -- ai_status=FAILED도 completed_at을 채운다. 계약서 §1-5가 FAILED의 completedAt을
       -- "실패 시각"으로 못 박았다. NULL로 두면 FE가 PENDING과 FAILED를 구분하지 못한다.
       CASE WHEN s.status <> 'PENDING_REVIEW' THEN s.created_at
            WHEN s.ai_status = 'COMPLETED'    THEN s.created_at + interval '3 seconds'
            ELSE s.created_at + interval '10 seconds' END AS completed_at,
       ctx.policy_snapshot,
       -- matchedKeyword는 REGEX 매칭에서 JSON null로 남긴다(계약서 §4 인계 2).
       -- 키를 없애면 FE가 "필드 없음"과 "null"을 구분하는 방어 코드를 쓰게 된다.
       -- REGEX 매칭 문자열은 여기에 넣지 않는다 — 원문 개인정보가 JSONB에 남는다.
       jsonb_build_object(
           'matches',
           CASE WHEN s.rule_id IS NULL THEN '[]'::jsonb
                ELSE jsonb_build_array(
                     jsonb_build_object(
                         'code',           s.rule_code,
                         'category',       s.rule_category,
                         'action',         s.rule_action,
                         'span',           jsonb_build_array(s.span_start, s.span_end),
                         'matchedKeyword', CASE WHEN s.rule_type = 'KEYWORD' THEN s.token END,
                         'severity',       s.severity,
                         'obligation',     s.obligation,
                         'source',         s.rule_source))
           END,
           'appliedRuleCodes', ctx.applied_rule_codes) AS rule_result,
       CASE WHEN s.ai_status = 'COMPLETED' THEN jsonb_build_object(
                'riskCandidates', jsonb_build_array(jsonb_build_object(
                    'code',      'CONF-CLIENT-PROJECT',
                    'category',  'CONFIDENTIAL',
                    'rationale', '고객사 식별 표현이 포함되어 계약 상대방을 특정할 수 있음',
                    'evidence',  jsonb_build_array(jsonb_build_object(
                        'source',  '고객사 NDA 목록 v3',
                        'excerpt', 'A사 — 비밀유지 2027.03까지, 일정·범위 포함')))),
                'missingContext', jsonb_build_array('해당 내용이 대외 공개된 정보인지 확인 필요'),
                'reviewRequired', true)
       END AS ai_result
  FROM spanned s
  JOIN seed_dept_ctx ctx USING (dept_id);

INSERT INTO message (message_id, user_id, original_text, submitted_text, status, created_at)
SELECT i, user_id, original_text, submitted_text, status, created_at
  FROM seed_audit_row;

INSERT INTO inspection (inspection_id, message_id, phase, policy_snapshot, rule_result,
                        ai_status, ai_result, final_decision, decided_by, created_at, completed_at)
SELECT i, i, 'INPUT', policy_snapshot, rule_result,
       ai_status, ai_result, final_decision, decided_by, created_at, completed_at
  FROM seed_audit_row;

-- 규칙 finding 45건 (MASKED 25 + BLOCKED 12 + PENDING_REVIEW 8).
-- 규칙 판정은 사람의 검토 대상이 아니므로 CONFIRMED 고정 (0.5 D6).
INSERT INTO inspection_finding
    (inspection_id, source, rule_id, code, category, span_start, span_end, action, review_status)
SELECT i, 'RULE', rule_id, rule_code, rule_category, span_start, span_end, rule_action, 'CONFIRMED'
  FROM seed_audit_row
 WHERE rule_id IS NOT NULL
 ORDER BY i;

-- AI finding 5건. review_status는 DB DEFAULT와 같은 SUGGESTED — 사람이 확정하기
-- 전까지 효력이 없다는 것이 감사 콘솔의 ACCEPT/REJECT 데모 대상이다.
INSERT INTO inspection_finding
    (inspection_id, source, rule_id, code, category, rationale, evidence, review_status)
SELECT i, 'AI', NULL, 'CONF-CLIENT-PROJECT', 'CONFIDENTIAL',
       '고객사 식별 표현이 포함되어 계약 상대방을 특정할 수 있음',
       '[{"source": "고객사 NDA 목록 v3", "excerpt": "A사 — 비밀유지 2027.03까지, 일정·범위 포함"}]'::jsonb,
       'SUGGESTED'
  FROM seed_audit_row
 WHERE ai_status = 'COMPLETED'
 ORDER BY i;

-- =============================================================================
-- 7. 데모 백업 레코드 3건 (기획서 10.1, 10.4)
--    데모 케이스 A·B·C와 같은 결과를 가진 완료 상태 레코드. 현장에서 실시간 입력이
--    실패해도 감사 콘솔에서 같은 판정을 보여줄 수 있다. 100건과 별개다.
--
--    Case A의 original_text는 라벨 적용본이다. 실제 개인정보 형태 문자열을 시드에
--    넣지 않는다는 원칙(10.1, 10.5)이 백업 레코드에도 적용된다. original_text는
--    화면 미노출(6.2)이고 Case A의 submitted_text는 NULL이므로 감사 콘솔이 보여주는
--    정보(BLOCKED · 규칙 2건 · 코드 SEC-DBURL-02/PII-RRN-01)는 실시간 입력과 같다.
-- =============================================================================

--    submitted_text 규약 (0.5 D7·D14)
--      NULL은 "차단됨"이 아니라 "마스킹본이 생성된 적 없음"을 뜻하며 규칙 BLOCK 경로에서만
--      발생한다 (D14).
--      Case A(101) — 규칙 BLOCK. 마스킹을 아예 실행하지 않았으므로(D5) 만들어진 본문이
--                    없다. NULL이다
--      Case B(102) — REVIEW 경로라 마스킹이 이미 실행됐고(D5), 그 본문을 담당자가 검토해
--                    ACCEPT한 것이다. 사람이 확정한 BLOCK은 본문을 보존한다 (D14).
--                    PATCH도 submitted_text를 지우지 않는다
--      Case C(103) — ALLOW. 원문 그대로
INSERT INTO message (message_id, user_id, original_text, submitted_text, status, created_at) VALUES
    (101, 1,
     '이 에러 좀 봐줘. DB_URL=[DB접속정보] 로 붙었는데 담당자 주민번호 [주민번호] 기준으로 조회하면 타임아웃 나',
     NULL, 'BLOCKED', now() - interval '35 minutes'),
    (102, 2,
     'A사 차세대 프로젝트 오픈 일정이 언제였지?',
     'A사 차세대 프로젝트 오픈 일정이 언제였지?', 'BLOCKED', now() - interval '22 minutes'),
    (103, 1,
     'A사 차세대 프로젝트 오픈 일정이 언제였지?',
     'A사 차세대 프로젝트 오픈 일정이 언제였지?', 'ALLOWED', now() - interval '11 minutes');

-- Case A — BLOCK, 규칙 2건. SEC-PRIVIP-03은 SEC-DBURL-02 매칭 구간에 완전히 포함되어
-- 억제된다(0.5 D1). appliedRuleCodes에는 그대로 남는다 — 적용된 규칙과 매칭된 규칙은 다르다.
INSERT INTO inspection (inspection_id, message_id, phase, policy_snapshot, rule_result,
                        ai_status, ai_result, final_decision, decided_by, created_at, completed_at)
SELECT 101, 101, 'INPUT', ctx.policy_snapshot,
       jsonb_build_object(
           'matches', jsonb_build_array(
               jsonb_build_object('code', 'SEC-DBURL-02', 'category', 'SECRET', 'action', 'BLOCK',
                   'span', jsonb_build_array(strpos(m.original_text, '[DB접속정보]') - 1,
                                             strpos(m.original_text, '[DB접속정보]') - 1 + length('[DB접속정보]')),
                   'matchedKeyword', NULL::text,
                   'severity', 'HIGH', 'obligation', 'INTERNAL', 'source', '정보보안규정 4.2'),
               jsonb_build_object('code', 'PII-RRN-01', 'category', 'PII', 'action', 'MASK',
                   'span', jsonb_build_array(strpos(m.original_text, '[주민번호]') - 1,
                                             strpos(m.original_text, '[주민번호]') - 1 + length('[주민번호]')),
                   'matchedKeyword', NULL::text,
                   'severity', 'HIGH', 'obligation', 'LEGAL', 'source', '개인정보보호법 제24조')),
           'appliedRuleCodes', ctx.applied_rule_codes),
       'SKIPPED', NULL, 'BLOCK', 'RULE', m.created_at, m.created_at
  FROM message m JOIN seed_dept_ctx ctx ON ctx.dept_id = 1
 WHERE m.message_id = 101;

INSERT INTO inspection_finding
    (inspection_id, source, rule_id, code, category, span_start, span_end, action, review_status)
SELECT 101, 'RULE', 6, 'SEC-DBURL-02', 'SECRET',
       strpos(m.original_text, '[DB접속정보]') - 1,
       strpos(m.original_text, '[DB접속정보]') - 1 + length('[DB접속정보]'), 'BLOCK', 'CONFIRMED'
  FROM message m WHERE m.message_id = 101
UNION ALL
SELECT 101, 'RULE', 1, 'PII-RRN-01', 'PII',
       strpos(m.original_text, '[주민번호]') - 1,
       strpos(m.original_text, '[주민번호]') - 1 + length('[주민번호]'), 'MASK', 'CONFIRMED'
  FROM message m WHERE m.message_id = 101;

-- Case B — REVIEW → AI 후보 1건 → 담당자(박OO) ACCEPT → BLOCKED, decided_by=HUMAN
INSERT INTO inspection (inspection_id, message_id, phase, policy_snapshot, rule_result,
                        ai_status, ai_result, final_decision, decided_by, created_at, completed_at)
SELECT 102, 102, 'INPUT', ctx.policy_snapshot,
       jsonb_build_object(
           'matches', jsonb_build_array(
               jsonb_build_object('code', 'CONF-CLIENT-01', 'category', 'CONFIDENTIAL', 'action', 'REVIEW',
                   'span', jsonb_build_array(0, 2), 'matchedKeyword', 'A사',
                   'severity', 'MEDIUM', 'obligation', 'INTERNAL', 'source', '고객사 NDA 목록 v3')),
           'appliedRuleCodes', ctx.applied_rule_codes),
       'COMPLETED',
       jsonb_build_object(
           'riskCandidates', jsonb_build_array(jsonb_build_object(
               'code',      'CONF-CLIENT-PROJECT',
               'category',  'CONFIDENTIAL',
               'rationale', '''A사 차세대 프로젝트 오픈 일정''이라는 서술이 계약 상대방과 미공개 일정을 동시에 특정함',
               'evidence',  jsonb_build_array(jsonb_build_object(
                   'source',  '고객사 NDA 목록 v3',
                   'excerpt', 'A사 — 비밀유지 2027.03까지, 일정·범위 포함')))),
           'missingContext', jsonb_build_array('해당 일정이 대외 공개된 정보인지 확인 필요'),
           'reviewRequired', true),
       'BLOCK', 'HUMAN', m.created_at, m.created_at + interval '3 seconds'
  FROM message m JOIN seed_dept_ctx ctx ON ctx.dept_id = 2
 WHERE m.message_id = 102;

INSERT INTO inspection_finding
    (inspection_id, source, rule_id, code, category, span_start, span_end, action,
     rationale, evidence, review_status, reviewed_by, reviewed_at)
VALUES
    (102, 'RULE', 8, 'CONF-CLIENT-01', 'CONFIDENTIAL', 0, 2, 'REVIEW',
     NULL, NULL, 'CONFIRMED', NULL, NULL),
    (102, 'AI', NULL, 'CONF-CLIENT-PROJECT', 'CONFIDENTIAL', NULL, NULL, NULL,
     '''A사 차세대 프로젝트 오픈 일정''이라는 서술이 계약 상대방과 미공개 일정을 동시에 특정함',
     '[{"source": "고객사 NDA 목록 v3", "excerpt": "A사 — 비밀유지 2027.03까지, 일정·범위 포함"}]'::jsonb,
     'ACCEPTED', 4, now() - interval '20 minutes');

-- Case C — Case B와 같은 문장인데 개발팀이라 P-CONF가 적용되지 않아 ALLOW.
-- 부서별 N:M 설계의 증명이므로 policy_snapshot에 P-CONF가 없어야 한다.
INSERT INTO inspection (inspection_id, message_id, phase, policy_snapshot, rule_result,
                        ai_status, ai_result, final_decision, decided_by, created_at, completed_at)
SELECT 103, 103, 'INPUT', ctx.policy_snapshot,
       jsonb_build_object('matches', '[]'::jsonb, 'appliedRuleCodes', ctx.applied_rule_codes),
       'SKIPPED', NULL, 'ALLOW', 'RULE', m.created_at, m.created_at
  FROM message m JOIN seed_dept_ctx ctx ON ctx.dept_id = 1
 WHERE m.message_id = 103;

-- -----------------------------------------------------------------------------
-- 8. 정리 — 헬퍼 테이블 제거 및 시퀀스 동기화
--    PK를 명시 지정했으므로 시퀀스가 그대로 1이다. 맞춰두지 않으면 첫 API 호출이
--    duplicate key로 죽는다.
-- -----------------------------------------------------------------------------
DROP TABLE seed_audit_row;
DROP TABLE seed_dept_ctx;

SELECT setval(pg_get_serial_sequence('department',         'dept_id'),    (SELECT max(dept_id)    FROM department));
SELECT setval(pg_get_serial_sequence('app_user',           'user_id'),    (SELECT max(user_id)    FROM app_user));
SELECT setval(pg_get_serial_sequence('policy',             'policy_id'),  (SELECT max(policy_id)  FROM policy));
SELECT setval(pg_get_serial_sequence('policy_rule',        'rule_id'),    (SELECT max(rule_id)    FROM policy_rule));
SELECT setval(pg_get_serial_sequence('message',            'message_id'), (SELECT max(message_id) FROM message));
SELECT setval(pg_get_serial_sequence('inspection',         'inspection_id'), (SELECT max(inspection_id) FROM inspection));
SELECT setval(pg_get_serial_sequence('inspection_finding', 'finding_id'), (SELECT max(finding_id) FROM inspection_finding));
