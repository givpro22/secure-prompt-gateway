-- =============================================================================
-- V1__schema.sql — Core Domain 8 테이블
--
-- 원본: 기획서 6.2(테이블 상세) / 6.3(관계) / 부록 B(DBML)
-- Future Domain 4개(attachment, knowledge_source, policy_audit, ai_provider_config)는
-- 여기서 만들지 않는다. Logical Model(docs/erd.dbml)에만 표기한다 (기획서 6.1, 6.5).
--
-- enum 성격의 컬럼은 전부 CHECK 제약으로 막는다. 애플리케이션에만 두면 DB에 잘못된
-- 값이 들어가고, 그것이 감사 데이터의 신뢰를 깬다.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. department — 부서 마스터 (기획서 6.2)
--    code 4값: DEV, SALES, HR, INFOSEC (0.5 D2)
-- -----------------------------------------------------------------------------
CREATE TABLE department (
    dept_id BIGSERIAL   PRIMARY KEY,
    code    VARCHAR(20) NOT NULL,
    name    VARCHAR(50) NOT NULL,
    CONSTRAINT uq_department_code UNIQUE (code)
);

COMMENT ON TABLE  department      IS '부서 마스터';
COMMENT ON COLUMN department.code IS 'DEV | SALES | HR | INFOSEC';

-- -----------------------------------------------------------------------------
-- 2. app_user — 사용자. 로그인 없이 계정 전환용 (기획서 6.2, 8.1 X-User-Id)
-- -----------------------------------------------------------------------------
CREATE TABLE app_user (
    user_id    BIGSERIAL    PRIMARY KEY,
    dept_id    BIGINT       NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    email      VARCHAR(100),
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email  UNIQUE (email),
    CONSTRAINT fk_app_user_dept   FOREIGN KEY (dept_id) REFERENCES department (dept_id),
    CONSTRAINT chk_app_user_role  CHECK (role IN ('EMPLOYEE', 'SECURITY_ADMIN'))
);

COMMENT ON TABLE  app_user      IS '사용자. 인증 없이 X-User-Id 헤더로 식별';
COMMENT ON COLUMN app_user.role IS 'EMPLOYEE | SECURITY_ADMIN';

-- -----------------------------------------------------------------------------
-- 3. policy — 정책 헤더 (기획서 6.2, 7.1)
--    scope=GLOBAL 정책은 department_policy 매핑 없이 전 부서에 적용된다 (6.4)
-- -----------------------------------------------------------------------------
CREATE TABLE policy (
    policy_id  BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(20)  NOT NULL,
    version    INT          NOT NULL DEFAULT 1,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    scope      VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_policy_code     UNIQUE (code),
    CONSTRAINT chk_policy_category CHECK (category IN ('PII', 'SECRET', 'CONFIDENTIAL')),
    CONSTRAINT chk_policy_scope    CHECK (scope IN ('GLOBAL', 'DEPT'))
);

COMMENT ON TABLE  policy          IS '정책 헤더. 카테고리·버전·활성 여부';
COMMENT ON COLUMN policy.version  IS '규칙 변경 시 증가. inspection.policy_snapshot이 이 값을 시점 보존';
COMMENT ON COLUMN policy.scope    IS 'GLOBAL(전사, 매핑 불필요) | DEPT(department_policy 매핑 필요)';

-- -----------------------------------------------------------------------------
-- 4. policy_rule — 규칙 (기획서 6.2, 7.2)
--    pattern은 정규식 원문 그대로. 백슬래시가 유실되면 안 되므로 시드에서는
--    반드시 일반 문자열 리터럴('')을 쓴다. E''는 백슬래시를 이스케이프한다.
-- -----------------------------------------------------------------------------
CREATE TABLE policy_rule (
    rule_id     BIGSERIAL    PRIMARY KEY,
    policy_id   BIGINT       NOT NULL,
    code        VARCHAR(30)  NOT NULL,
    rule_type   VARCHAR(20)  NOT NULL,
    pattern     TEXT         NOT NULL,
    action      VARCHAR(20)  NOT NULL,
    mask_label  VARCHAR(30),
    severity    VARCHAR(10)  NOT NULL,
    obligation  VARCHAR(20)  NOT NULL,
    source      VARCHAR(100),
    description VARCHAR(200),
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    CONSTRAINT uq_policy_rule_code    UNIQUE (code),
    CONSTRAINT fk_policy_rule_policy  FOREIGN KEY (policy_id) REFERENCES policy (policy_id),
    CONSTRAINT chk_rule_type          CHECK (rule_type  IN ('REGEX', 'KEYWORD')),
    CONSTRAINT chk_rule_action        CHECK (action     IN ('MASK', 'BLOCK', 'REVIEW')),
    CONSTRAINT chk_rule_severity      CHECK (severity   IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_rule_obligation    CHECK (obligation IN ('LEGAL', 'INTERNAL')),
    -- MASK 규칙에 mask_label이 없으면 Masker가 NPE로 죽는다 (기획서 7.5, 0.5 D5).
    -- BLOCK/REVIEW 규칙의 mask_label은 NULL이 정상이므로 한 방향만 강제한다.
    CONSTRAINT chk_rule_mask_label    CHECK (action <> 'MASK' OR mask_label IS NOT NULL)
);

COMMENT ON TABLE  policy_rule            IS '규칙. 발표·로그에서 code를 그대로 사용';
COMMENT ON COLUMN policy_rule.pattern    IS 'REGEX면 정규식, KEYWORD면 쉼표 구분 키워드';
COMMENT ON COLUMN policy_rule.mask_label IS '마스킹 치환 라벨. action=MASK일 때만 존재';

-- -----------------------------------------------------------------------------
-- 5. department_policy — 부서 N:M 정책 (기획서 6.2, 6.4, 7.3)
--    복합 PK. 서러게이트 PK를 두면 같은 (부서, 정책) 중복 매핑을 막지 못한다.
-- -----------------------------------------------------------------------------
CREATE TABLE department_policy (
    dept_id    BIGINT      NOT NULL,
    policy_id  BIGINT      NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_department_policy        PRIMARY KEY (dept_id, policy_id),
    CONSTRAINT fk_department_policy_dept   FOREIGN KEY (dept_id)   REFERENCES department (dept_id),
    CONSTRAINT fk_department_policy_policy FOREIGN KEY (policy_id) REFERENCES policy (policy_id)
);

COMMENT ON TABLE department_policy IS 'scope=DEPT 정책만 매핑한다. GLOBAL 정책은 매핑 없이 전 부서 적용';

-- -----------------------------------------------------------------------------
-- 6. message — 직원이 제출한 프롬프트 (기획서 6.2, 7.5)
--    original_text: 감사 목적 보관. 화면 미노출
--    submitted_text: 마스킹 적용 후 본문. NULL이면 미전송(BLOCK 판정)
-- -----------------------------------------------------------------------------
CREATE TABLE message (
    message_id     BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    original_text  TEXT        NOT NULL,
    submitted_text TEXT,
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_message_user   FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT chk_message_status CHECK (status IN ('ALLOWED', 'MASKED', 'BLOCKED', 'PENDING_REVIEW'))
);

COMMENT ON COLUMN message.original_text  IS '원문. 화면 미노출';
COMMENT ON COLUMN message.submitted_text IS '마스킹 적용 후 본문. NULL이면 미전송';

-- -----------------------------------------------------------------------------
-- 7. inspection — 검사 1회의 결과 (기획서 6.2, 6.4)
--    JSONB 3개가 AI-Ready "Structured Data" 원칙의 증거다.
--    AI 스키마가 확장돼도 컬럼을 추가하지 않는다.
-- -----------------------------------------------------------------------------
CREATE TABLE inspection (
    inspection_id   BIGSERIAL   PRIMARY KEY,
    message_id      BIGINT      NOT NULL,
    phase           VARCHAR(10) NOT NULL DEFAULT 'INPUT',
    policy_snapshot JSONB       NOT NULL,
    rule_result     JSONB       NOT NULL,
    ai_status       VARCHAR(20) NOT NULL,
    ai_result       JSONB,
    final_decision  VARCHAR(20),
    decided_by      VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT fk_inspection_message         FOREIGN KEY (message_id) REFERENCES message (message_id),
    CONSTRAINT chk_inspection_phase          CHECK (phase          IN ('INPUT', 'OUTPUT')),
    CONSTRAINT chk_inspection_ai_status      CHECK (ai_status      IN ('SKIPPED', 'PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_inspection_final_decision CHECK (final_decision IN ('ALLOW', 'MASK', 'BLOCK', 'PENDING')),
    CONSTRAINT chk_inspection_decided_by     CHECK (decided_by     IN ('RULE', 'HUMAN'))
);

COMMENT ON COLUMN inspection.phase           IS 'INPUT. OUTPUT은 Future(기획서 0.3 범위 밖)';
COMMENT ON COLUMN inspection.policy_snapshot IS '판정 시점의 정책 id·version·규칙 코드 목록 (7.4-3)';
COMMENT ON COLUMN inspection.rule_result     IS '규칙 엔진 원본 결과 (9.4 ruleResult)';
COMMENT ON COLUMN inspection.ai_result       IS 'AI 원본 응답 (9.4 aiAssessment). BLOCK이면 AI 미호출이라 NULL';

-- -----------------------------------------------------------------------------
-- 8. inspection_finding — 검사에서 발견된 항목 (기획서 6.2, 4장)
--
--    review_status DEFAULT 'SUGGESTED' + CHECK 4값(0.5 D6).
--    이것이 "AI는 결정하지 못한다"는 책임 경계를 DB 수준에서 보증하는 장치다.
--    애플리케이션 기본값으로만 두면, 엔티티를 우회한 INSERT 한 번에 무너진다.
-- -----------------------------------------------------------------------------
CREATE TABLE inspection_finding (
    finding_id    BIGSERIAL   PRIMARY KEY,
    inspection_id BIGINT      NOT NULL,
    source        VARCHAR(10) NOT NULL,
    rule_id       BIGINT,
    code          VARCHAR(30) NOT NULL,
    category      VARCHAR(20) NOT NULL,
    span_start    INT,
    span_end      INT,
    action        VARCHAR(20),
    rationale     TEXT,
    evidence      JSONB,
    review_status VARCHAR(20) NOT NULL DEFAULT 'SUGGESTED',
    reviewed_by   BIGINT,
    reviewed_at   TIMESTAMPTZ,
    CONSTRAINT fk_finding_inspection   FOREIGN KEY (inspection_id) REFERENCES inspection (inspection_id),
    CONSTRAINT fk_finding_rule         FOREIGN KEY (rule_id)       REFERENCES policy_rule (rule_id),
    CONSTRAINT fk_finding_reviewed_by  FOREIGN KEY (reviewed_by)   REFERENCES app_user (user_id),
    CONSTRAINT chk_finding_source      CHECK (source IN ('RULE', 'AI')),
    CONSTRAINT chk_finding_action      CHECK (action IN ('MASK', 'BLOCK', 'REVIEW')),
    CONSTRAINT chk_finding_review_status
        CHECK (review_status IN ('SUGGESTED', 'ACCEPTED', 'REJECTED', 'CONFIRMED')),
    -- AI 후보는 규칙을 참조하지 않는다 (기획서 6.2). 반대 방향(RULE이면 rule_id 필수)은
    -- 강제하지 않는다 — 규칙 엔진이 rule_id 없이 finding을 만드는 경로를 막지 않기 위함.
    CONSTRAINT chk_finding_ai_no_rule  CHECK (source <> 'AI' OR rule_id IS NULL)
);

COMMENT ON COLUMN inspection_finding.span_start    IS '원문(original_text) 기준 시작 오프셋. 재계산하지 않는다 (0.5 D3)';
COMMENT ON COLUMN inspection_finding.review_status IS 'SUGGESTED | ACCEPTED | REJECTED | CONFIRMED. 규칙 finding은 CONFIRMED 고정 (0.5 D6)';
COMMENT ON COLUMN inspection_finding.evidence      IS 'AI 후보의 참조 출처 배열 [{source, excerpt}]';

-- -----------------------------------------------------------------------------
-- 인덱스 (기획서 5.4 감사 콘솔 조회 패턴)
--   목록은 deptId·status·기간으로 필터하고 created_at 역순 정렬한다.
--   시드 100건 규모에서는 없어도 되지만 설계 의도를 남긴다.
-- -----------------------------------------------------------------------------
CREATE INDEX idx_inspection_created_at ON inspection (created_at DESC);
CREATE INDEX idx_inspection_message    ON inspection (message_id);
CREATE INDEX idx_message_user_status   ON message (user_id, status);
CREATE INDEX idx_finding_inspection    ON inspection_finding (inspection_id);
CREATE INDEX idx_policy_rule_policy    ON policy_rule (policy_id);
CREATE INDEX idx_app_user_dept         ON app_user (dept_id);
