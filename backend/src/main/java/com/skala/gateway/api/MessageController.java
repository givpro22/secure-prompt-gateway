package com.skala.gateway.api;

import com.skala.gateway.api.dto.ErrorResponse;
import com.skala.gateway.api.dto.MessageRequest;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.config.CurrentUserId;
import com.skala.gateway.domain.repository.AppUserRepository;
import com.skala.gateway.service.InspectionService;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/messages} — 프롬프트 제출과 규칙 판정 (기획서 8.4).
 *
 * <p>판정에 따라 <b>200 / 202 / 403</b>으로 갈린다. 각 상태 코드가 고유한 의미를 갖는 것이
 * 이 설계의 요점이며, 그래서 201을 쓰지 않는다 (0.5 D4) — {@code message} 리소스는 실제로
 * 생성되지만 클라이언트가 받아야 할 주 정보는 "생성 사실"이 아니라 "판정 결과"이고,
 * BLOCK(전송 거부)을 201로 표현할 방법이 없다.
 *
 * <p><b>403도 판정 본문을 반환한다</b> (계약서 C2). BLOCK은 처리 실패가 아니라 정상 수행된
 * 판정이고, FE가 차단 사유(규칙 코드·출처)를 S4 화면에 표시해야 하므로 에러 봉투로는 화면을
 * 그릴 수 없다.
 */
@RestController
@RequestMapping("/api/v1")
public class MessageController {

    private final InspectionService inspectionService;
    private final AppUserRepository appUserRepository;

    public MessageController(InspectionService inspectionService, AppUserRepository appUserRepository) {
        this.inspectionService = inspectionService;
        this.appUserRepository = appUserRepository;
    }

    @PostMapping("/messages")
    public ResponseEntity<?> submit(@CurrentUserId Long userId, @RequestBody(required = false) MessageRequest request) {
        if (request == null || request.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.invalidRequest("text는 비어 있을 수 없습니다."));
        }
        // 401이 아니라 400이다. 401은 인증 체계가 있다는 뜻인데 이번 범위에 인증이 없다
        // (0.3, 계약서 C8).
        if (!appUserRepository.existsById(userId)) {
            return ResponseEntity.badRequest().body(ErrorResponse.invalidUser(userId));
        }

        MessageVerdictResponse verdict = inspectionService.submit(userId, request.text());

        return switch (verdict.decision()) {
            case ALLOW, MASK -> ResponseEntity.ok(verdict);
            // 202의 Location은 폴링 URL이다. WebConfig가 CORS exposedHeaders에 넣어 두었으므로
            // 브라우저에서 읽을 수 있다.
            case PENDING -> ResponseEntity.accepted()
                    .location(URI.create("/api/v1/inspections/" + verdict.inspectionId()))
                    .body(verdict);
            case BLOCK -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(verdict);
        };
    }
}
