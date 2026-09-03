package com.skala.gateway.api.dto;

import com.skala.gateway.domain.UnmaskRequest;
import java.time.OffsetDateTime;

/**
 * 해제 요청 한 건 (D25).
 *
 * <p>{@code originalText}가 실리는 <b>유일한 응답</b>이다. 기획서 5.4의 원문 미노출은
 * 감사 콘솔이 남의 원문을 기본으로 펼치지 않는다는 뜻이고, 여기는 작성자가 자기 문장을
 * 스스로 내놓으며 봐 달라고 한 건이다. 그래서 요청 행이 있을 때만 열린다.
 *
 * <p>직원 화면에는 {@link #forRequester} 로 만든 것을 준다 — 자기 원문은 이미 알고 있고,
 * 담당자가 남긴 판단만 필요하다.
 */
public record UnmaskRequestDto(
        Long requestId,
        Long messageId,
        UserRefDto requester,
        String reason,
        String status,
        String originalText,
        String submittedText,
        String decisionNote,
        String decidedBy,
        OffsetDateTime createdAt,
        OffsetDateTime decidedAt) {

    /** 담당자 콘솔용. 원문과 마스킹본을 나란히 싣는다 */
    public static UnmaskRequestDto forReviewer(UnmaskRequest r) {
        return new UnmaskRequestDto(
                r.getRequestId(),
                r.getMessage().getMessageId(),
                UserRefDto.from(r.getRequester()),
                r.getReason(),
                r.getStatus().name(),
                r.getMessage().getOriginalText(),
                r.getMessage().getSubmittedText(),
                r.getDecisionNote(),
                r.getDecidedBy() == null ? null : r.getDecidedBy().getName(),
                ApiTimes.utc(r.getCreatedAt()),
                ApiTimes.utc(r.getDecidedAt()));
    }

    /** 요청자용. 원문·마스킹본을 빼고 상태만 돌려준다 */
    public static UnmaskRequestDto forRequester(UnmaskRequest r) {
        return new UnmaskRequestDto(
                r.getRequestId(),
                r.getMessage().getMessageId(),
                UserRefDto.from(r.getRequester()),
                r.getReason(),
                r.getStatus().name(),
                null,
                null,
                r.getDecisionNote(),
                r.getDecidedBy() == null ? null : r.getDecidedBy().getName(),
                ApiTimes.utc(r.getCreatedAt()),
                ApiTimes.utc(r.getDecidedAt()));
    }
}
