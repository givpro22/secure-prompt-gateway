package com.skala.gateway.api;

import com.skala.gateway.api.dto.ReviewRequest;
import com.skala.gateway.api.dto.ReviewResponse;
import com.skala.gateway.config.CurrentUserId;
import com.skala.gateway.service.ReviewService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code PATCH /api/v1/inspections/{id}/findings/{findingId}} — AI 후보의 사람 확정
 * (기획서 3.3 UC-06, 8.4, 계약서 §1-7).
 *
 * <p>감사 담당자가 ACCEPT/REJECT를 누르면 여기로 온다. 응답에 갱신된 finding과
 * <b>재산출된 inspection 상태</b>를 함께 실어 FE가 재조회 없이 목록 행과 상세 패널을
 * 동시에 갱신한다.
 *
 * <p>{@code InspectionController}와 분리한 것은 담당 경계 때문이다. 조회는
 * {@code rule-engine-dev}, 확정은 {@code api-ai-architect}이며 한 파일을 둘이 고치면
 * 병합 충돌이 판정 로직 한가운데에서 난다.
 *
 * <p>상태 코드는 200 / 400 / 404 / 409 넷이다. 조건 판정과 에러 봉투 변환은
 * {@link ReviewService}와 {@link GlobalExceptionHandler}가 하고, 컨트롤러는 경로와 헤더만 푼다.
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * @param userId  {@code X-User-Id}. {@code reviewed_by}에 그대로 기록된다 — 역할 검사는
     *                하지 않는다 (기획서 0.3)
     * @param request {@code {"reviewStatus": "ACCEPTED"|"REJECTED", "comment": "…"}}.
     *                {@code comment}는 수신만 하고 저장하지 않는다 (계약서 §1-7)
     */
    @PatchMapping("/inspections/{id}/findings/{findingId}")
    public ReviewResponse review(@PathVariable("id") Long inspectionId,
                                 @PathVariable("findingId") Long findingId,
                                 @CurrentUserId Long userId,
                                 @RequestBody(required = false) ReviewRequest request) {
        return reviewService.review(inspectionId, findingId, userId, request);
    }
}
