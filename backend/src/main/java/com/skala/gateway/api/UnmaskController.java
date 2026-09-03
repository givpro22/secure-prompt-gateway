package com.skala.gateway.api;

import com.skala.gateway.api.dto.PageEnvelope;
import com.skala.gateway.api.dto.UnmaskDecisionBody;
import com.skala.gateway.api.dto.UnmaskRequestBody;
import com.skala.gateway.api.dto.UnmaskRequestDto;
import com.skala.gateway.config.CurrentUserId;
import com.skala.gateway.service.UnmaskService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마스킹 해제 검토 (D25).
 *
 * <ul>
 *   <li>{@code POST /messages/{id}/unmask-request} — 직원이 사유를 적어 올린다</li>
 *   <li>{@code GET  /unmask-requests} — 담당자 목록. 원문과 마스킹본이 함께 온다</li>
 *   <li>{@code POST /unmask-requests/{id}/decision} — 담당자가 해제/유지를 확정한다</li>
 * </ul>
 *
 * <p>조건 판정과 역할 검사는 {@link UnmaskService}가 하고 컨트롤러는 경로와 헤더만 푼다.
 */
@RestController
@RequestMapping("/api/v1")
public class UnmaskController {

    private final UnmaskService unmaskService;

    public UnmaskController(UnmaskService unmaskService) {
        this.unmaskService = unmaskService;
    }

    @PostMapping("/messages/{id}/unmask-request")
    @ResponseStatus(HttpStatus.CREATED)
    public UnmaskRequestDto request(@PathVariable("id") Long messageId,
                                    @CurrentUserId Long userId,
                                    @RequestBody(required = false) UnmaskRequestBody body) {
        return unmaskService.request(messageId, userId, body);
    }

    /** 요청자가 자기 건의 처리 상태를 확인한다. 원문은 실리지 않는다 */
    @GetMapping("/messages/{id}/unmask-request")
    public UnmaskRequestDto mine(@PathVariable("id") Long messageId,
                                 @CurrentUserId Long userId) {
        return unmaskService.mine(messageId, userId);
    }

    @GetMapping("/unmask-requests")
    public PageEnvelope<UnmaskRequestDto> list(@CurrentUserId Long userId,
                                               @RequestParam(value = "status", required = false) String status,
                                               @RequestParam(value = "page", defaultValue = "0") int page,
                                               @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<UnmaskRequestDto> found = unmaskService.forConsole(status, userId, page, size);
        return PageEnvelope.of(found, found.getContent());
    }

    @PostMapping("/unmask-requests/{id}/decision")
    public UnmaskRequestDto decide(@PathVariable("id") Long requestId,
                                   @CurrentUserId Long userId,
                                   @RequestBody(required = false) UnmaskDecisionBody body) {
        return unmaskService.decide(requestId, userId, body);
    }
}
