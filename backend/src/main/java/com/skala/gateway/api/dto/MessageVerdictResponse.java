package com.skala.gateway.api.dto;

import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.Message;
import com.skala.gateway.domain.enums.AiStatus;
import com.skala.gateway.domain.enums.DecidedBy;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.domain.jsonb.PolicySnapshot;
import com.skala.gateway.domain.jsonb.RuleResult;
import java.time.OffsetDateTime;

/**
 * {@code POST /api/v1/messages} 응답 (기획서 8.4, 계약서 §1-4).
 *
 * <p><b>필드 집합이 4개 상태에서 동일하다.</b> 상태별로 값만 달라진다 — FE가 상태 코드에 따라
 * 다른 파서를 쓰지 않아도 된다.
 *
 * <table>
 *   <caption>상태별 값</caption>
 *   <tr><th>필드</th><th>ALLOW(200)</th><th>MASK(200)</th><th>BLOCK(403)</th><th>REVIEW(202)</th></tr>
 *   <tr><td>{@code submittedText}</td><td>원문</td><td>마스킹본</td><td>{@code null}</td><td>마스킹본</td></tr>
 *   <tr><td>{@code aiStatus}</td><td>SKIPPED</td><td>SKIPPED</td><td>SKIPPED</td><td>PENDING</td></tr>
 *   <tr><td>{@code decidedBy}</td><td>RULE</td><td>RULE</td><td>RULE</td><td>{@code null}</td></tr>
 *   <tr><td>{@code pollAfterMs}</td><td>{@code null}</td><td>{@code null}</td><td>{@code null}</td><td>2000</td></tr>
 * </table>
 *
 * <p>{@code submittedText}가 {@code null}인 것은 <b>BLOCK뿐이다</b> (0.5 D7). PENDING_REVIEW도
 * 마스킹본을 싣는다 — 감사 담당자가 검토해야 할 바로 그 건의 본문이 비면 SCR-02 상세 패널이
 * 무용지물이고, AI에 넘기는 {@code maskedText}와 같은 값이라 따로 감출 이유가 없다.
 *
 * <p>202 응답에 <b>{@code aiAssessment}·AI findings·{@code completedAt}은 없다.</b> AI 결과는
 * {@code GET /inspections/{id}} 폴링으로만 얻는다.
 */
public record MessageVerdictResponse(
        Long messageId,
        Long inspectionId,
        FinalDecision decision,
        MessageStatus status,
        String submittedText,
        PolicySnapshot policySnapshot,
        RuleResult ruleResult,
        AiStatus aiStatus,
        DecidedBy decidedBy,
        Integer pollAfterMs,
        OffsetDateTime createdAt) {

    /**
     * @param pollAfterMs REVIEW 판정일 때만 값이 있다. 출처는 {@code gateway.polling.interval-ms}이며
     *                    FE는 이 값을 쓰고 자체 상수를 쓰지 않는다
     */
    public static MessageVerdictResponse of(Message message, Inspection inspection, Integer pollAfterMs) {
        return new MessageVerdictResponse(
                message.getMessageId(),
                inspection.getInspectionId(),
                inspection.getFinalDecision(),
                message.getStatus(),
                message.getSubmittedText(),
                inspection.getPolicySnapshot(),
                inspection.getRuleResult(),
                inspection.getAiStatus(),
                inspection.getDecidedBy(),
                pollAfterMs,
                ApiTimes.utc(inspection.getCreatedAt()));
    }
}
