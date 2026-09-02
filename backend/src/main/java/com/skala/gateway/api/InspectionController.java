package com.skala.gateway.api;

import com.skala.gateway.api.dto.ErrorResponse;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.service.InspectionService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 조회 — {@code GET /inspections}, {@code GET /inspections/{id}} (기획서 8.3, 계약서 §1-5·§1-6).
 *
 * <p>목록은 {@code {items, page, size, total}} 봉투로 반환한다. 배열을 직접 반환하면 FE가
 * {@code .items}를 꺼내지 않아 {@code filter is not a function}이 난다.
 *
 * <p>{@code PATCH /inspections/{id}/findings/{findingId}}는 여기 없다. AI 후보의
 * ACCEPT/REJECT와 최종 판정 재산출은 {@code api-ai-architect}의 {@code ReviewController} 몫이다
 * (계약서 §8).
 */
@RestController
@RequestMapping("/api/v1")
public class InspectionController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /**
     * 감사 목록. 정렬은 {@code createdAt DESC} 고정이며 정렬 파라미터를 두지 않는다.
     *
     * @param status {@code message.status} 4값
     * @param from   ISO 8601. {@code createdAt} 기준 <b>이상</b>
     * @param to     ISO 8601. {@code createdAt} 기준 <b>미만</b> — 경계를 포함하면 하루 단위
     *               필터가 다음 날 00:00을 삼킨다
     * @param size   최대 100. 초과하면 100으로 절삭한다
     */
    @GetMapping("/inspections")
    public ResponseEntity<?> list(@RequestParam(required = false) String deptId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String from,
                                  @RequestParam(required = false) String to,
                                  @RequestParam(required = false) String page,
                                  @RequestParam(required = false) String size) {
        Long parsedDeptId;
        MessageStatus parsedStatus;
        OffsetDateTime parsedFrom;
        OffsetDateTime parsedTo;
        int parsedPage;
        int parsedSize;
        try {
            parsedDeptId = QueryParams.optionalLong(deptId);
            parsedStatus = parseStatus(status);
            parsedFrom = QueryParams.optionalTime(from);
            parsedTo = QueryParams.optionalTime(to);
            parsedPage = QueryParams.intOrDefault(page, DEFAULT_PAGE);
            parsedSize = QueryParams.intOrDefault(size, DEFAULT_SIZE);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            // NumberFormatException은 IllegalArgumentException의 하위라 함께 잡힌다.
            return ResponseEntity.badRequest().body(ErrorResponse.invalidParameter(e.getMessage()));
        }
        if (parsedPage < 0 || parsedSize < 1) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.invalidParameter("page는 0 이상, size는 1 이상이어야 합니다."));
        }

        return ResponseEntity.ok(inspectionService.list(
                parsedDeptId, parsedStatus, parsedFrom, parsedTo, parsedPage, parsedSize));
    }

    /** 판정 상세. 202 이후 FE 폴링이 이 엔드포인트를 {@code aiStatus}가 끝날 때까지 호출한다. */
    @GetMapping("/inspections/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return inspectionService.detail(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(ErrorResponse.inspectionNotFound(id)));
    }

    private static MessageStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MessageStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status는 ALLOWED/MASKED/BLOCKED/PENDING_REVIEW 중 하나여야 합니다: " + raw);
        }
    }
}
