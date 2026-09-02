-- =============================================================================
-- V3__embargo.sql — 홍보팀 엠바고 정책
--
-- 원본: 착수 전 영향 분석 결정 2·3·4 (_workspace/04_홍보팀엠바고_영향분석.md)
--
-- 배경
--   개발팀이 릴리스 백로그를 외부 LLM에 넣으면, 유출이 없어도 그 시점에 공개된 것이다.
--   미발표 제품명·런칭 일정이 거기 섞여 있으면 홍보팀의 발표 통제가 무너진다.
--   이 정책은 정보가 민감해서 막는 것이 아니라 **아직 때가 아니라서** 막는다.
--
-- 결정 3 — policy.owner_dept_id 신설
--   department_policy는 "적용되는 부서"이지 "만든 부서"가 아니다. 엠바고는 홍보팀이 걸고
--   개발팀·영업팀이 걸리므로 두 개념이 갈린다. 컬럼으로 분리한다.
--
-- 결정 4 — policy_rule.embargo_until 신설 + 만료 시 자동 해제
--   날짜를 코드나 화면에 하드코딩하면 "정책·임계값은 DB"라는 주장(기획서 11.3)이 무너진다.
--
--   embargo_until의 의미는 **해제일이다. 이 날부터 공개할 수 있다.**
--   차단 조건은 today < embargo_until 이며 경계일 당일에는 해제된다.
--   "○○일까지 불가"가 아니라 "○○일부터 가능"으로 읽어야 하루 어긋나지 않는다.
--
-- 규칙 2종을 넣는 이유는 시연이다. 같은 파일에 두 제품이 들어 있고 하나만 걸린다 —
-- 부서로 갈리는 것(Case B/C)과 같은 증명을 시간 축에서 한 번 더 한다.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 스키마 확장
-- -----------------------------------------------------------------------------

ALTER TABLE policy ADD COLUMN owner_dept_id BIGINT;

ALTER TABLE policy
    ADD CONSTRAINT fk_policy_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES department (dept_id);

COMMENT ON COLUMN policy.owner_dept_id IS
    '정책을 만든 부서. department_policy(적용 부서)와 다르다. 엠바고는 홍보팀이 걸고 개발팀이 걸린다';

-- EMBARGO 카테고리 추가. CONFIDENTIAL에 얹으면 화면에 "기밀"로 떠서 시연의 요점이 흐려진다.
ALTER TABLE policy DROP CONSTRAINT chk_policy_category;
ALTER TABLE policy
    ADD CONSTRAINT chk_policy_category CHECK (category IN ('PII', 'SECRET', 'CONFIDENTIAL', 'EMBARGO'));

ALTER TABLE policy_rule ADD COLUMN embargo_until DATE;

COMMENT ON COLUMN policy_rule.embargo_until IS
    '엠바고 해제일. 이 날부터 공개 가능하며 차단 조건은 today < embargo_until. NULL이면 기한 없는 규칙';

-- -----------------------------------------------------------------------------
-- 2. 홍보팀 (결정 2)
--    department.code에 CHECK 제약이 없으므로 INSERT만으로 추가된다.
-- -----------------------------------------------------------------------------

INSERT INTO department (dept_id, code, name) VALUES (5, 'PR', '홍보팀');

INSERT INTO app_user (user_id, dept_id, name, email, role) VALUES
    (5, 5, '한OO', 'han@example.com', 'EMPLOYEE');

-- -----------------------------------------------------------------------------
-- 3. 기존 정책의 소유 부서
--    P-PII·P-SEC·P-CONF는 정보보안팀이 관리한다. INFOSEC이 검토자이면서 관리자인 것은
--    7.3 매트릭스와 어긋나지 않는다 — 매핑(적용)이 없을 뿐 소유는 있다.
-- -----------------------------------------------------------------------------

UPDATE policy SET owner_dept_id = 4 WHERE code IN ('P-PII', 'P-SEC', 'P-CONF');

-- -----------------------------------------------------------------------------
-- 4. P-EMBARGO 정책과 규칙 2종
--
--    action=BLOCK인 이유: 엠바고는 날짜까지 무조건 불가라 사람이 판단할 여지가 없다.
--    REVIEW로 두면 Case B와 같은 202 폴링 흐름이 되어 시연 장면이 겹치기도 한다.
-- -----------------------------------------------------------------------------

INSERT INTO policy (policy_id, code, name, category, version, is_active, scope, owner_dept_id) VALUES
    (4, 'P-EMBARGO', '보도자료 엠바고', 'EMBARGO', 1, true, 'DEPT', 5);

INSERT INTO policy_rule
    (rule_id, policy_id, code, rule_type, pattern, action, mask_label, severity, obligation,
     source, description, embargo_until) VALUES
    -- 해제일이 아직 오지 않았다 → 차단된다
    (9, 4, 'EMB-NOVA-01', 'KEYWORD', '노바,NOVA,SKALA NOVA',
        'BLOCK', NULL, 'HIGH', 'INTERNAL',
        '홍보팀 엠바고 공지 2026-09-01', '신제품 SKALA NOVA 관련 표현. 2026-09-20 해제', DATE '2026-09-20'),
    -- 해제일이 지났다 → 같은 형태의 규칙인데 걸리지 않는다
    (10, 4, 'EMB-ATLAS-02', 'KEYWORD', '아틀라스,ATLAS',
        'BLOCK', NULL, 'HIGH', 'INTERNAL',
        '홍보팀 엠바고 공지 2026-06-10', '제품 SKALA ATLAS 관련 표현. 2026-09-04 해제', DATE '2026-09-04');

-- 적용 부서. 홍보팀 자신은 매핑하지 않는다 — 발표 주체는 자기 엠바고에 걸리지 않는다.
INSERT INTO department_policy (dept_id, policy_id) VALUES
    (1, 4),   -- 개발팀
    (2, 4);   -- 영업팀

-- -----------------------------------------------------------------------------
-- 5. 홍보팀 감사 로그 5건
--
--    V2의 100건은 부서 1~3만 쓴다. 홍보팀을 추가만 하면 감사 콘솔 부서 필터에서
--    홍보팀을 골랐을 때 0건이 나오고, 그 자리는 시연 중에 눌러볼 수 있다.
--
--    홍보팀에는 GLOBAL 정책만 적용된다(P-EMBARGO는 DEV·SALES 매핑). 스냅샷도 그렇게 나온다.
--
--    PK를 하드코딩하지 않는다. message·inspection·inspection_finding은 V2 이후에도
--    런타임이 계속 채번하는 테이블이라, 이미 떠 있는 DB에는 104~108번이 사용자가 보낸
--    프롬프트로 이미 차 있다. 하드코딩하면 그 환경에서 duplicate key로 마이그레이션이
--    통째로 실패하고 백엔드가 기동하지 못한다 (2026-09-03 배포 장애).
--    시퀀스에서 받아 쓰고, 행 구분은 seq와 marker로 한다.
--
--    department·app_user·policy·policy_rule의 PK는 그대로 명시한다. 이 4개는 런타임
--    삽입 경로가 없고(기획서 0.3 — 정책 편집 UI 없음), 규칙 코드가 계약·테스트·픽스처에서
--    고정 ID로 참조된다.
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS seed_pr_ctx;
CREATE TABLE seed_pr_ctx AS
SELECT jsonb_build_object('policies', (
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
                               WHERE dp.dept_id = 5 AND dp.policy_id = p.policy_id))
       )) AS policy_snapshot,
       (SELECT jsonb_agg(r.code ORDER BY r.code)
          FROM policy p
          JOIN policy_rule r ON r.policy_id = p.policy_id AND r.is_active
         WHERE p.is_active
           AND (p.scope = 'GLOBAL'
                OR EXISTS (SELECT 1 FROM department_policy dp
                            WHERE dp.dept_id = 5 AND dp.policy_id = p.policy_id))) AS applied_rule_codes;

-- marker가 NULL이 아니면 마스킹 건이다. 그 자리에서 span과 규칙 코드가 모두 파생된다.
DROP TABLE IF EXISTS seed_pr_row;
CREATE TABLE seed_pr_row (
    seq           int,
    original_text text,
    status        text,
    marker        text,
    created_at    timestamptz
);

INSERT INTO seed_pr_row (seq, original_text, status, marker, created_at) VALUES
    (1, '다음 주 보도자료 초안 톤을 좀 더 부드럽게 다듬어줘',
        'ALLOWED', NULL,        now() - interval '5 hours'),
    (2, '기자 간담회 Q&A 예상 질문 10개만 뽑아줘',
        'ALLOWED', NULL,        now() - interval '4 hours'),
    (3, '사내 공지문 문구 검토해줘 (담당자 연락처 [전화번호] 포함)',
        'MASKED',  '[전화번호]', now() - interval '3 hours'),
    (4, '행사 참석자 안내 메일 초안 써줘 (회신 주소 [이메일])',
        'MASKED',  '[이메일]',   now() - interval '2 hours'),
    (5, '분기 홍보 성과 요약 슬라이드 목차 잡아줘',
        'ALLOWED', NULL,        now() - interval '1 hours');

INSERT INTO message (user_id, original_text, submitted_text, status, created_at)
SELECT 5, r.original_text, r.original_text, r.status, r.created_at
  FROM seed_pr_row r
 ORDER BY r.seq;

-- 채번된 message_id를 되받는다. 본문이 5건 모두 서로 다르고 V2 100건과도 겹치지 않으므로
-- original_text로 join하면 1:1로 붙는다.
DROP TABLE IF EXISTS seed_pr_msg;
CREATE TABLE seed_pr_msg AS
SELECT m.message_id, r.seq, r.marker, r.status, r.created_at, m.original_text
  FROM seed_pr_row r
  JOIN message m ON m.original_text = r.original_text AND m.user_id = 5;

INSERT INTO inspection (message_id, phase, policy_snapshot, rule_result,
                        ai_status, ai_result, final_decision, decided_by, created_at, completed_at)
SELECT s.message_id, 'INPUT', ctx.policy_snapshot,
       jsonb_build_object(
           'matches',
           CASE WHEN s.marker IS NOT NULL THEN jsonb_build_array(jsonb_build_object(
                    'code',           CASE s.marker WHEN '[전화번호]' THEN 'PII-PHONE-03' ELSE 'PII-EMAIL-04' END,
                    'category',       'PII',
                    'action',         'MASK',
                    'span',           jsonb_build_array(
                                          strpos(s.original_text, s.marker) - 1,
                                          strpos(s.original_text, s.marker) - 1 + length(s.marker)),
                    'matchedKeyword', NULL::text,
                    'severity',       CASE s.marker WHEN '[전화번호]' THEN 'MEDIUM' ELSE 'LOW' END,
                    'obligation',     'LEGAL',
                    'source',         '개인정보보호법',
                    'embargoUntil',   NULL::text))
                ELSE '[]'::jsonb END,
           'appliedRuleCodes', ctx.applied_rule_codes),
       'SKIPPED', NULL,
       CASE WHEN s.marker IS NOT NULL THEN 'MASK' ELSE 'ALLOW' END, 'RULE',
       s.created_at, s.created_at
  FROM seed_pr_msg s CROSS JOIN seed_pr_ctx ctx
 ORDER BY s.seq;

INSERT INTO inspection_finding
    (inspection_id, source, rule_id, code, category, span_start, span_end, action, review_status)
SELECT i.inspection_id, 'RULE',
       CASE s.marker WHEN '[전화번호]' THEN 3 ELSE 4 END,
       CASE s.marker WHEN '[전화번호]' THEN 'PII-PHONE-03' ELSE 'PII-EMAIL-04' END,
       'PII',
       strpos(s.original_text, s.marker) - 1,
       strpos(s.original_text, s.marker) - 1 + length(s.marker),
       'MASK', 'CONFIRMED'
  FROM seed_pr_msg s
  JOIN inspection i ON i.message_id = s.message_id
 WHERE s.marker IS NOT NULL
 ORDER BY s.seq;

DROP TABLE seed_pr_msg;
DROP TABLE seed_pr_row;
DROP TABLE seed_pr_ctx;

-- -----------------------------------------------------------------------------
-- 6. 시퀀스 재동기화. PK를 명시 지정한 4개 테이블만 대상이다 — 맞춰두지 않으면
--    첫 API 호출이 duplicate key로 죽는다. message·inspection·inspection_finding은
--    위에서 시퀀스로 채번했으므로 손대지 않는다.
-- -----------------------------------------------------------------------------

SELECT setval(pg_get_serial_sequence('department',  'dept_id'),   (SELECT max(dept_id)   FROM department));
SELECT setval(pg_get_serial_sequence('app_user',    'user_id'),   (SELECT max(user_id)   FROM app_user));
SELECT setval(pg_get_serial_sequence('policy',      'policy_id'), (SELECT max(policy_id) FROM policy));
SELECT setval(pg_get_serial_sequence('policy_rule', 'rule_id'),   (SELECT max(rule_id)   FROM policy_rule));
