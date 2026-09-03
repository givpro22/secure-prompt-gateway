package com.skala.gateway.api;

import com.skala.gateway.api.dto.ErrorResponse;
import com.skala.gateway.api.dto.MessageRequest;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.api.dto.ResponseInspectionRequest;
import com.skala.gateway.api.dto.ResponseVerdictResponse;
import org.springframework.web.bind.annotation.PathVariable;
import com.skala.gateway.config.CurrentUserId;
import com.skala.gateway.domain.repository.AppUserRepository;
import com.skala.gateway.service.AnswerService;
import com.skala.gateway.service.InspectionService;
import org.springframework.web.bind.annotation.GetMapping;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
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
    private final AnswerService answerService;
    private final AppUserRepository appUserRepository;

    /**
     * 검사 대상 텍스트의 최대 길이 ({@code gateway.limits.max-input-chars}).
     *
     * <p>파일은 프론트에서 텍스트로 추출해 이 엔드포인트로 들어온다(결정 1). 상한이 없으면
     * 2,600행짜리 백로그가 20만 자로 그대로 들어오고, {@code ConflictResolver}의 중첩 억제가
     * 매칭 개수에 대해 O(n²)이라 요청 하나가 스레드를 오래 잡는다. 검사에 3초가 넘게 걸리면
     * 사용자는 게이트웨이를 우회할 길을 찾는다 — 그때 통제율은 0이 된다.
     */
    private final int maxInputChars;

    public MessageController(InspectionService inspectionService, AnswerService answerService,
                             AppUserRepository appUserRepository,
                             @Value("${gateway.limits.max-input-chars}") int maxInputChars) {
        this.inspectionService = inspectionService;
        this.answerService = answerService;
        this.appUserRepository = appUserRepository;
        this.maxInputChars = maxInputChars;
    }

    @PostMapping("/messages")
    public ResponseEntity<?> submit(@CurrentUserId Long userId, @RequestBody(required = false) MessageRequest request) {
        if (request == null || request.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.invalidRequest("text는 비어 있을 수 없습니다."));
        }
        // 잘라서 통과시키지 않는다. 뒷부분을 버리고 검사하면 "검사했다"는 기록만 남고
        // 실제로는 안 본 구간이 생긴다 — 감사 기록 자체가 거짓이 된다.
        if (request.text().length() > maxInputChars) {
            return ResponseEntity.badRequest().body(ErrorResponse.invalidRequest(
                    "text가 최대 길이를 초과했습니다. (%,d자 / 최대 %,d자) 내용을 나눠서 보내십시오."
                            .formatted(request.text().length(), maxInputChars)));
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
    /**
     * {@code POST /api/v1/messages/{id}/answer} — 마스킹본을 Claude에 보내 답변을 받고
     * 곧바로 출력 검사에 넘긴다 (UC-08 한 바퀴).
     *
     * <p>응답은 출력 검사 판정 그대로다. 사람이 붙여넣은 것과 같은 모양이라 화면이
     * 두 경로를 구분하지 않는다. 키가 없으면 503 {@code ANSWER_UNAVAILABLE}이고 화면은
     * 붙여넣기로 물러난다.
     */
    @PostMapping("/messages/{id}/answer")
    public ResponseEntity<ResponseVerdictResponse> answer(@PathVariable("id") Long messageId,
                                                          @CurrentUserId Long userId) {
        ResponseVerdictResponse verdict = answerService.answer(messageId, userId);
        return switch (verdict.decision()) {
            case BLOCK -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(verdict);
            case PENDING -> ResponseEntity.accepted().body(verdict);
            default -> ResponseEntity.ok(verdict);
        };
    }

    /** {@code GET /api/v1/messages/answer/available} — 화면이 버튼을 그릴지 정하는 데 쓴다 */
    @GetMapping("/messages/answer/available")
    public java.util.Map<String, Object> answerAvailable() {
        // Map.of는 null을 거부한다. 제공자 이름이 비어 있어도 응답은 나가야 한다.
        String provider = answerService.enabled() ? answerService.providerName() : null;
        return java.util.Map.of(
                "available", answerService.enabled(),
                "provider", provider == null ? "" : provider);
    }

    /**
     * {@code POST /api/v1/messages/{id}/response} — 출력 검사 (UC-08).
     *
     * <p>모델이 돌려준 답변을 같은 정책으로 다시 본다. 입력과 같은 파이프라인이라
     * 상태 코드도 같다 — 200 ALLOW·MASK / 202 REVIEW / 403 BLOCK.
     */
    @PostMapping("/messages/{id}/response")
    public ResponseEntity<ResponseVerdictResponse> inspectResponse(
            @PathVariable("id") Long messageId,
            @CurrentUserId Long userId,
            @RequestBody(required = false) ResponseInspectionRequest request) {
        String text = request == null || request.text() == null ? "" : request.text();
        if (text.trim().isEmpty()) {
            throw ApiException.invalidRequest("검사할 답변이 비어 있습니다.");
        }
        if (text.length() > maxInputChars) {
            throw ApiException.invalidRequest(
                    "답변이 최대 길이를 초과했습니다. (" + text.length() + "자 / 최대 " + maxInputChars + "자)");
        }
        ResponseVerdictResponse verdict = inspectionService.inspectResponse(messageId, userId, text);
        return switch (verdict.decision()) {
            case BLOCK -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(verdict);
            case PENDING -> ResponseEntity.accepted().body(verdict);
            default -> ResponseEntity.ok(verdict);
        };
    }
}
