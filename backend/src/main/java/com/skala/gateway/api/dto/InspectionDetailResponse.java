package com.skala.gateway.api.dto;

import com.skala.gateway.ai.AiAssessment;
import com.skala.gateway.domain.AppUser;
import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.InspectionFinding;
import com.skala.gateway.domain.Message;
import com.skala.gateway.domain.enums.AiStatus;
import com.skala.gateway.domain.enums.DecidedBy;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.InspectionPhase;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.domain.jsonb.PolicySnapshot;
import com.skala.gateway.domain.jsonb.RuleResult;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code GET /api/v1/inspections/{id}} 응답 (기획서 8.4, 계약서 §1-5). 폴링 겸용이다.
 *
 * <p><b>원문({@code original_text})은 어떤 상태에서도 응답에 넣지 않는다</b> (6.2 "원문. 화면 미노출").
 * 나가는 것은 {@code submittedText}뿐이며 {@code POST /messages} 응답과 같은 값이다.
 *
 * <p>{@code aiStatus}가 폴링 FE의 분기 근거다. {@code PENDING}이면 {@code aiAssessment}가
 * {@code null}이고 {@code findings}에 RULE만 있다. {@code FAILED}여도 {@code status}는
 * {@code PENDING_REVIEW}를 유지한다 — {@code ALLOWED}로 떨어뜨리면 검사되지 않은 프롬프트가
 * 통과 기록으로 남는다 (9.5, UC-03 예외).
 *
 * @param user {@code department}는 <b>부서명 문자열</b>이다 ({@code "영업팀"}). 8.4 예시 그대로이며
 *             객체가 아니다 — 상세 패널이 이름 한 줄만 그린다
 */
public record InspectionDetailResponse(
        Long inspectionId,
        Long messageId,
        InspectionPhase phase,
        InspectionUser user,
        String submittedText,
        MessageStatus status,
        PolicySnapshot policySnapshot,
        RuleResult ruleResult,
        AiStatus aiStatus,
        AiAssessment aiAssessment,
        List<FindingDto> findings,
        FinalDecision finalDecision,
        DecidedBy decidedBy,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {

    public record InspectionUser(Long userId, String name, String department) {

        public static InspectionUser from(AppUser user) {
            return new InspectionUser(user.getUserId(), user.getName(), user.getDepartment().getName());
        }
    }

    public static InspectionDetailResponse of(Inspection inspection, List<InspectionFinding> findings) {
        Message message = inspection.getMessage();
        return new InspectionDetailResponse(
                inspection.getInspectionId(),
                message.getMessageId(),
                inspection.getPhase(),
                InspectionUser.from(message.getUser()),
                message.getSubmittedText(),
                message.getStatus(),
                inspection.getPolicySnapshot(),
                inspection.getRuleResult(),
                inspection.getAiStatus(),
                // DB 컬럼은 ai_result, API 필드는 aiAssessment다. 이름이 바뀌는 유일한 지점 (계약서 §2).
                inspection.getAiResult(),
                findings.stream().map(FindingDto::from).toList(),
                inspection.getFinalDecision(),
                inspection.getDecidedBy(),
                ApiTimes.utc(inspection.getCreatedAt()),
                ApiTimes.utc(inspection.getCompletedAt()));
    }
}
