package com.skala.gateway.api.dto;

import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.Message;
import com.skala.gateway.domain.enums.AiStatus;
import com.skala.gateway.domain.enums.DecidedBy;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.jsonb.PolicySnapshot;
import com.skala.gateway.domain.jsonb.RuleResult;
import java.time.OffsetDateTime;

/**
 * 출력 검사 판정 (UC-08).
 *
 * <p>입력 판정({@link MessageVerdictResponse})과 필드가 같고 본문 하나만 다르다.
 * {@code inspectedText}는 <b>검사를 거친 답변</b>이며, 차단이면 {@code null}이다 —
 * 차단은 마스킹을 실행하지 않으므로 만들어진 본문이 없다 (0.5 D5).
 *
 * <p>답변 원문은 싣지 않는다. 직원은 자기가 받은 답변을 이미 갖고 있고, 게이트웨이가
 * 되돌려줄 이유가 없다.
 */
public record ResponseVerdictResponse(
        Long messageId,
        Long inspectionId,
        FinalDecision decision,
        String inspectedText,
        PolicySnapshot policySnapshot,
        RuleResult ruleResult,
        AiStatus aiStatus,
        DecidedBy decidedBy,
        Integer pollAfterMs,
        OffsetDateTime createdAt) {

    public static ResponseVerdictResponse of(Message message, Inspection inspection, Integer pollAfterMs) {
        return new ResponseVerdictResponse(
                message.getMessageId(),
                inspection.getInspectionId(),
                inspection.getFinalDecision(),
                message.getResponseMasked(),
                inspection.getPolicySnapshot(),
                inspection.getRuleResult(),
                inspection.getAiStatus(),
                inspection.getDecidedBy(),
                pollAfterMs,
                ApiTimes.utc(inspection.getCreatedAt()));
    }
}
