-- =============================================================================
-- V5__customer_roster.sql — 고객 명단 테이블과 명단 기반 탐지
--
-- 왜 테이블인가
--   이름은 정규식으로 일반화할 수 없다. `[가-힣]{2,4}`면 김치·박스·한우가 걸리고 외자
--   이름은 놓친다. NER은 기획서 0.4가 범위 밖으로 뒀다.
--
--   그리고 애초에 이건 탐지 문제가 아니라 **데이터 문제**다. 고객 명단을 사내 DB에 두는
--   것은 정상이고, 문제는 그것이 외부 LLM으로 나가는 순간이다. 게이트웨이가 명단을 이미
--   알고 있으니 나가려는 자리에서 막으면 된다.
--
--   명단을 규칙의 pattern에 박아 넣지 않고 테이블로 분리하면, 조직이 바뀔 때 필요한 작업이
--   "코드 수정"이 아니라 "명단 적재"가 된다. 실제 도입에서는 CRM에서 동기화한다.
--
-- 2단 판정
--   PII-CUST-07  성+이름 전체 일치  → MASK [고객명]   확정이다
--   PII-CUST-08  이름만 일치        → REVIEW          실명 의심. 사람이 판단한다
--
--   성을 뗀 이름은 동음 명사가 있어 기계가 확정하지 않는다. 앞뒤 한글 경계로
--   '서준이가'의 서준, '우진공업'의 우진 같은 접사 결합을 거른다.
--
-- rule_type = ROSTER
--   pattern에 정규식 대신 조회할 컬럼명을 둔다. PolicyService가 판정 직전에 명단을 읽어
--   정규식으로 펼친다. 엔진은 여전히 REGEX만 알고, 순수 함수 성질도 유지된다.
--
-- ⚠ 아래 명단은 전부 **합성**이다. 실제 인물·회사가 아니다 (기획서 10.1).
--   실 데이터는 저장소에 두지 않고 운영 DB에 적재한다.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 고객 명단 (Core 9번째 테이블)
-- -----------------------------------------------------------------------------

CREATE TABLE customer (
    customer_id BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    given_name  VARCHAR(50)  NOT NULL,
    company     VARCHAR(100),
    source      VARCHAR(100),
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_name UNIQUE (name, company)
);

COMMENT ON TABLE  customer            IS '고객 명단. 게이트웨이가 이 이름들의 외부 전송을 막는다';
COMMENT ON COLUMN customer.given_name IS '성을 뗀 부분. 단독 등장은 마스킹까지 가지 않고 검토로 보낸다';
COMMENT ON COLUMN customer.source     IS '명단 출처. 실제 도입에서는 CRM 동기화 배치가 채운다';

CREATE INDEX idx_customer_active ON customer (is_active);

INSERT INTO customer (customer_id, name, given_name, company, source) VALUES
    (1, '강도현', '도현', '대한물산', 'CRM 고객 마스터 2026-09'),
    (2, '고은비', '은비', '대한물산', 'CRM 고객 마스터 2026-09'),
    (3, '권나윤', '나윤', '세영테크', 'CRM 고객 마스터 2026-09'),
    (4, '김서준', '서준', '세영테크', 'CRM 고객 마스터 2026-09'),
    (5, '남우진', '우진', '세영테크', 'CRM 고객 마스터 2026-09'),
    (6, '노하람', '하람', '한빛리테일', 'CRM 고객 마스터 2026-09'),
    (7, '문지후', '지후', '한빛리테일', 'CRM 고객 마스터 2026-09'),
    (8, '박예린', '예린', '금강에너지', 'CRM 고객 마스터 2026-09'),
    (9, '배시우', '시우', '금강에너지', 'CRM 고객 마스터 2026-09'),
    (10, '서채원', '채원', '금강에너지', 'CRM 고객 마스터 2026-09'),
    (11, '손유찬', '유찬', '동방해운', 'CRM 고객 마스터 2026-09'),
    (12, '신소율', '소율', '동방해운', 'CRM 고객 마스터 2026-09'),
    (13, '안건우', '건우', '누리소프트', 'CRM 고객 마스터 2026-09'),
    (14, '양다인', '다인', '누리소프트', 'CRM 고객 마스터 2026-09'),
    (15, '오태윤', '태윤', '누리소프트', 'CRM 고객 마스터 2026-09'),
    (16, '유하준', '하준', '백양제약', 'CRM 고객 마스터 2026-09'),
    (17, '윤서아', '서아', '백양제약', 'CRM 고객 마스터 2026-09'),
    (18, '이민재', '민재', '태산건설', 'CRM 고객 마스터 2026-09'),
    (19, '장준서', '준서', '태산건설', 'CRM 고객 마스터 2026-09'),
    (20, '전지안', '지안', '태산건설', 'CRM 고객 마스터 2026-09'),
    (21, '조현우', '현우', '오름식품', 'CRM 고객 마스터 2026-09'),
    (22, '최수아', '수아', '오름식품', 'CRM 고객 마스터 2026-09'),
    (23, '한연우', '연우', '성진기계', 'CRM 고객 마스터 2026-09'),
    (24, '황도윤', '도윤', '성진기계', 'CRM 고객 마스터 2026-09');

SELECT setval(pg_get_serial_sequence('customer', 'customer_id'), (SELECT max(customer_id) FROM customer));

-- -----------------------------------------------------------------------------
-- 2. ROSTER 규칙 2종
-- -----------------------------------------------------------------------------

ALTER TABLE policy_rule DROP CONSTRAINT chk_rule_type;
ALTER TABLE policy_rule
    ADD CONSTRAINT chk_rule_type CHECK (rule_type IN ('REGEX', 'KEYWORD', 'ROSTER'));

COMMENT ON COLUMN policy_rule.pattern IS
    'REGEX면 정규식, KEYWORD면 쉼표 구분 키워드, ROSTER면 customer 테이블의 조회 컬럼명';

INSERT INTO policy_rule
    (rule_id, policy_id, code, rule_type, pattern, action, mask_label, severity, obligation, source, description) VALUES
    (13, 1, 'PII-CUST-07', 'ROSTER', 'name',
        'MASK', '[고객명]', 'HIGH', 'LEGAL', '개인정보보호법', '고객 명단 전체 일치'),
    (14, 1, 'PII-CUST-08', 'ROSTER', 'given_name',
        'REVIEW', NULL, 'LOW', 'LEGAL', '개인정보보호법', '고객 명단의 이름 부분만 일치. 실명 의심');

-- 규칙이 6종에서 8종이 되었다. 명시값을 쓴다 — 상대 증가는 돌리는 DB에 따라 값이 갈린다.
UPDATE policy SET version = 5 WHERE code = 'P-PII';

SELECT setval(pg_get_serial_sequence('policy_rule', 'rule_id'), (SELECT max(rule_id) FROM policy_rule));
