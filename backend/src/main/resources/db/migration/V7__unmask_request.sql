-- 마스킹 해제 검토 요청 (D25)
--
-- 규칙은 명단의 문자열만 본다. 고객과 이름이 같은 직원을 쓰면 그 이름도 [고객명]으로
-- 가려진다. 판정은 안전한 쪽으로 틀린 것이지만 문장이 무슨 말인지 알 수 없게 되는
-- 일이 생기고, 규칙만으로는 이 둘을 가를 방법이 없다.
--
-- 그래서 사람에게 넘긴다. 직원이 사유를 적어 요청하면 보안 담당자가 원문과 마스킹본을
-- 나란히 놓고 해제 여부를 정한다. 규칙은 결정하고 AI는 제안하고 사람은 확정한다는
-- 4장의 경계가 여기서도 그대로다 — 다만 여기서 사람이 뒤집는 것은 규칙의 판정이다.
--
-- 원문 열람에 대해. 기획서 5.4는 감사 콘솔이 남의 원문을 기본으로 보여주지 않는다는
-- 뜻이고, 여기는 작성자가 자기 문장을 스스로 내놓으며 봐 달라고 하는 자리다. 요청이
-- 붙은 건에 한해서만 열리고 그 사실이 행으로 남는다.

CREATE TABLE unmask_request (
    request_id    BIGSERIAL   PRIMARY KEY,
    message_id    BIGINT      NOT NULL,
    requester_id  BIGINT      NOT NULL,
    reason        TEXT        NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by_id BIGINT,
    decided_at    TIMESTAMPTZ,
    decision_note TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_unmask_message   FOREIGN KEY (message_id)    REFERENCES message  (message_id),
    CONSTRAINT fk_unmask_requester FOREIGN KEY (requester_id)  REFERENCES app_user (user_id),
    CONSTRAINT fk_unmask_decider   FOREIGN KEY (decided_by_id) REFERENCES app_user (user_id),
    CONSTRAINT chk_unmask_status   CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- 한 건에 요청 하나. 같은 메시지로 여러 번 올려 담당자를 밀어붙일 수 없게 한다.
    CONSTRAINT uq_unmask_message   UNIQUE (message_id)
);

CREATE INDEX idx_unmask_status ON unmask_request (status, created_at DESC);

COMMENT ON TABLE  unmask_request        IS '마스킹 해제 검토 요청 (D25)';
COMMENT ON COLUMN unmask_request.reason IS '직원이 적은 사유. 담당자가 원문과 함께 본다';
COMMENT ON COLUMN unmask_request.status IS 'PENDING 대기 / APPROVED 해제 / REJECTED 유지';
