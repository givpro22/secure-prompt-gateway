package com.skala.gateway.api.dto;

import com.skala.gateway.ai.AiAssessment;
import com.skala.gateway.domain.InspectionFinding;
import com.skala.gateway.domain.enums.FindingSource;
import com.skala.gateway.domain.enums.PolicyCategory;
import com.skala.gateway.domain.enums.ReviewStatus;
import com.skala.gateway.domain.enums.RuleAction;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 검사에서 발견된 항목 (계약서 §1-5).
 *
 * <p>규칙 finding과 AI finding이 같은 shape을 쓰고 값으로 갈린다.
 *
 * <table>
 *   <caption>출처별 값</caption>
 *   <tr><th>필드</th><th>RULE</th><th>AI</th></tr>
 *   <tr><td>{@code spanStart}/{@code spanEnd}</td><td>원문 기준 오프셋</td><td>{@code null}</td></tr>
 *   <tr><td>{@code action}</td><td>MASK/BLOCK/REVIEW</td><td>{@code null}</td></tr>
 *   <tr><td>{@code rationale}/{@code evidence}</td><td>{@code null}</td><td>○</td></tr>
 *   <tr><td>{@code reviewStatus}</td><td>CONFIRMED 고정</td><td>SUGGESTED → ACCEPTED/REJECTED</td></tr>
 * </table>
 *
 * <p>{@code spanStart}/{@code spanEnd}는 <b>원문 기준</b>이다. FE는 이 값으로
 * {@code submittedText}를 자르지 않는다 — 마스킹이 길이를 바꾸므로 오프셋이 밀린다.
 * 하이라이트는 {@code submittedText}에서 {@code maskLabel} 문자열을 검색해 처리한다 (0.5 D3).
 *
 * <p>{@code rule_id}는 노출하지 않는다 (계약서 §2).
 */
public record FindingDto(
        Long findingId,
        FindingSource source,
        String code,
        PolicyCategory category,
        Integer spanStart,
        Integer spanEnd,
        RuleAction action,
        String rationale,
        List<AiAssessment.Evidence> evidence,
        ReviewStatus reviewStatus,
        UserRefDto reviewedBy,
        OffsetDateTime reviewedAt) {

    public static FindingDto from(InspectionFinding finding) {
        return new FindingDto(
                finding.getFindingId(),
                finding.getSource(),
                finding.getCode(),
                finding.getCategory(),
                finding.getSpanStart(),
                finding.getSpanEnd(),
                finding.getAction(),
                finding.getRationale(),
                finding.getEvidence(),
                finding.getReviewStatus(),
                UserRefDto.from(finding.getReviewedBy()),
                ApiTimes.utc(finding.getReviewedAt()));
    }
}
