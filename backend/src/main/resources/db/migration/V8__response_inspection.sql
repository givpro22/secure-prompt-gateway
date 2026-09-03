-- 출력 검사 (UC-08, 17장 3단계)
--
-- 지금까지 게이트웨이는 나가는 것만 봤다. 승인된 본문이 상용 LLM으로 가고, 거기서
-- 돌아온 답변은 어디도 거치지 않았다. 그런데 답변에도 같은 위험이 있다 — 모델이
-- 이름이나 번호를 지어내기도 하고, 사내 문서 조각을 물고 나오기도 한다.
--
-- 스키마는 처음부터 이 자리를 비워 뒀다. `inspection.phase`가 INPUT / OUTPUT 두 값을
-- 갖고 있었고 message_id에 UNIQUE가 없다. 한 메시지에 검사가 둘 붙는 구조다.
-- 여기서 채우는 것은 텍스트를 둘 자리뿐이고, 판정·마스킹·감사 기록은 입력과 같은
-- 파이프라인을 그대로 탄다.
--
-- 이름을 original/submitted 와 짝이 맞게 골랐다. response_text 가 모델이 돌려준 그대로고,
-- response_masked 가 검사를 거쳐 직원 화면에 그려지는 것이다. 차단이면 후자가 NULL이다 —
-- 차단은 마스킹을 실행하지 않으므로 만들어진 본문이 없다 (0.5 D5와 같은 규칙).

ALTER TABLE message ADD COLUMN response_text   TEXT;
ALTER TABLE message ADD COLUMN response_masked TEXT;

COMMENT ON COLUMN message.response_text   IS '모델이 돌려준 답변 원문. 화면 미노출, 감사 보관용';
COMMENT ON COLUMN message.response_masked IS '출력 검사를 거쳐 직원에게 보이는 본문. 차단이면 NULL';
COMMENT ON COLUMN inspection.phase        IS 'INPUT 프롬프트 검사 / OUTPUT 응답 재검사 (UC-08)';
