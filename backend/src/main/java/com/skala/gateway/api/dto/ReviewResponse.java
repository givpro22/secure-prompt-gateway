package com.skala.gateway.api.dto;

import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.InspectionFinding;
import com.skala.gateway.domain.enums.DecidedBy;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.domain.enums.ReviewStatus;
import java.time.OffsetDateTime;

/**
 * {@code PATCH /api/v1/inspections/{id}/findings/{findingId}} 200 응답 (기획서 8.4, 계약서 §1-7).
 *
 * <p><b>갱신된 finding과 재산출된 inspection 상태를 함께 싣는다.</b> FE가 한 번 더 조회하지 않고
 * 감사 목록 행({@code status}·{@code decidedBy})과 상세 패널({@code reviewStatus}·{@code reviewedBy})을
 * 동시에 갱신할 수 있다. 확정 직후의 재조회는 D12의 폴링 종료 조건({@code aiStatus})과 무관한
 * 별개 요청이라, 응답이 상태를 싣지 않으면 화면이 한 박자 늦게 바뀐다.
 *
 * <p>요청의 {@code comment}는 에코하지 않는다 — 저장하지 않는 값을 응답에 실으면 FE가
 * 저장된 것으로 읽는다.
 */
public record ReviewResponse(
        Long findingId,
        ReviewStatus reviewStatus,
        UserRefDto reviewedBy,
        OffsetDateTime reviewedAt,
        InspectionState inspection) {

    /**
     * 재산출된 검사 상태 (계약서 §1-7).
     *
     * <p><b>{@code submittedText}를 반드시 싣는다 (0.5 D14).</b> PATCH는 이 값을 건드리지 않으므로
     * 요청 전과 같은 값이지만, 응답에서 빼면 FE가 "BLOCKED가 됐으니 본문은 null이겠지"를 추론해
     * 화면에서 본문을 지운다 — 실제로 그렇게 하고 있었다. 계약 v1의 "BLOCK 전이 시 null로
     * 되돌린다"가 D14로 폐기된 이상, 값을 실어 추론할 여지를 없애는 것이 맞다.
     *
     * <p>"확정이 본문을 바꾼다"는 오해를 부를 수 있다는 우려로 처음에 뺐지만, 값을 감춰서 생긴
     * 추론이 실제 버그가 됐다. 데모 1:50에서 ACCEPT를 누르는 순간 상세 패널 본문이 사라지면
     * D14가 화면에서 무력화된다.
     *
     * <p><b>{@code completedAt}도 같은 이유로 싣는다 (QA F6).</b> 이쪽은 {@code submittedText}와
     * 반대로 <b>서버가 실제로 갱신하는</b> 값이다 — {@code ReviewService}가 AI 완료 시각을 사람의
     * 확정 시각으로 덮어쓴다(§1-7). 응답에 없으면 FE가 낡은 값을 그대로 둬서 상세 패널에
     * "완료 07:25:07"과 "확정자 박OO · 07:25:12"가 어긋난 채 나란히 표시된다.
     *
     * <p>규칙은 하나다 — <b>PATCH가 바꾸거나 화면이 다시 그려야 하는 값은 전부 응답에 싣는다.</b>
     * 빠진 값은 FE가 추론하거나 낡은 채로 남긴다. 재조회 없이 화면을 갱신하게 하는 것이
     * 이 객체의 존재 이유다(§1-7).
     */
    public record InspectionState(
            Long inspectionId,
            FinalDecision finalDecision,
            DecidedBy decidedBy,
            MessageStatus status,
            String submittedText,
            OffsetDateTime completedAt) {

        public static InspectionState of(Inspection inspection) {
            return new InspectionState(
                    inspection.getInspectionId(),
                    inspection.getFinalDecision(),
                    inspection.getDecidedBy(),
                    inspection.getMessage().getStatus(),
                    // PATCH 전후로 같은 값이다. ReviewService는 이 컬럼을 읽지도 쓰지도 않는다.
                    inspection.getMessage().getSubmittedText(),
                    // 이쪽은 재산출에서 실제로 갱신된 값이다. SUGGESTED가 남아 판정이 움직이지
                    // 않았다면 AI 완료 시각 그대로다.
                    ApiTimes.utc(inspection.getCompletedAt()));
        }
    }

    public static ReviewResponse of(InspectionFinding finding, Inspection inspection) {
        return new ReviewResponse(
                finding.getFindingId(),
                finding.getReviewStatus(),
                UserRefDto.from(finding.getReviewedBy()),
                ApiTimes.utc(finding.getReviewedAt()),
                InspectionState.of(inspection));
    }
}
