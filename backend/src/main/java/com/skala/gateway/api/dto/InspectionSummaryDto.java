package com.skala.gateway.api.dto;

import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.Message;
import com.skala.gateway.domain.enums.AiStatus;
import com.skala.gateway.domain.enums.DecidedBy;
import com.skala.gateway.domain.enums.MessageStatus;
import java.time.OffsetDateTime;

/**
 * 감사 콘솔 목록 행 (기획서 5.4, 8.4, 계약서 §1-6).
 *
 * <p>{@code department}·{@code userName}은 문자열이다. 목록 행에 필요한 것이 그것뿐이라
 * 중첩 객체로 만들 이유가 없다.
 *
 * @param ruleCount {@code source='RULE'}인 finding 개수다. <b>AI finding을 세지 않는다</b>
 *                  (5.4 목록 컬럼 정의). 0.5 D1 중첩 억제 후의 값이므로 Case A에서 2다
 */
public record InspectionSummaryDto(
        Long inspectionId,
        OffsetDateTime createdAt,
        String department,
        String userName,
        MessageStatus status,
        long ruleCount,
        AiStatus aiStatus,
        DecidedBy decidedBy,
        /**
         * 마스킹 적용본. 감사 목록이 훑어볼 대상은 이것이다 (기획서 5.4 —
         * "원문 — submitted_text (마스킹된 본문). 원문(original_text)은 표시하지 않음").
         *
         * <p>규칙 BLOCK이면 {@code null}이다. 마스킹본이 생성된 적이 없다 (0.5 D5·D14).
         */
        String submittedText) {

    public static InspectionSummaryDto of(Inspection inspection, long ruleCount) {
        Message message = inspection.getMessage();
        return new InspectionSummaryDto(
                inspection.getInspectionId(),
                ApiTimes.utc(inspection.getCreatedAt()),
                message.getUser().getDepartment().getName(),
                message.getUser().getName(),
                message.getStatus(),
                ruleCount,
                inspection.getAiStatus(),
                inspection.getDecidedBy(),
                message.getSubmittedText());
    }
}
