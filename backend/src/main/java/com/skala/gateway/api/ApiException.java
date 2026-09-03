package com.skala.gateway.api;

import com.skala.gateway.domain.enums.ReviewStatus;
import org.springframework.http.HttpStatus;

/**
 * 계약서 §1 에러 봉투로 변환되는 예외. {@link GlobalExceptionHandler}가 받아
 * {@code {code, message, details}}와 상태 코드로 내보낸다.
 *
 * <p>서비스가 {@code ResponseEntity}를 만들지 않고 이 예외를 던지는 이유는 상태 코드와 에러
 * 코드를 한 자리에 묶어 두기 위해서다. 컨트롤러마다 404/409를 다시 조립하면 같은 조건에
 * 다른 코드가 붙는다.
 *
 * <p><b>403(BLOCK)은 여기로 오지 않는다.</b> 차단은 처리 실패가 아니라 정상 수행된 판정이므로
 * {@code MessageController}가 판정 객체를 직접 반환한다 (계약서 C2). 이 예외로 표현할 수 있게
 * 만들면 언젠가 BLOCK이 에러 봉투로 나가고 FE의 S4 화면이 차단 사유를 잃는다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** 400 — 요청 본문 누락, {@code reviewStatus}가 ACCEPTED/REJECTED 밖의 값. */
    public static ApiException invalidRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    /**
     * 400 — {@code X-User-Id} 헤더 없음.
     *
     * <p>401이 아닌 이유는 인증 체계가 있다는 뜻이 되기 때문이다. 이번 범위에 인증이 없다
     * (기획서 0.3, 계약서 C8).
     */
    public static ApiException missingUserHeader(String headerName) {
        return new ApiException(HttpStatus.BAD_REQUEST, "MISSING_USER_HEADER",
                headerName + " 헤더가 필요합니다.");
    }

    /** 400 — {@code X-User-Id}가 존재하지 않는 사용자. */
    public static ApiException invalidUser(Object userId) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER",
                "존재하지 않는 사용자입니다: " + userId);
    }

    /**
     * 400 — {@code X-User-Id}가 숫자가 아니다.
     *
     * <p>{@link #invalidUser}와 코드가 같고 메시지만 다르다. FE는 {@code code}로 분기하므로
     * 둘을 갈라 놓을 이유가 없지만, "숫자가 아님"과 "그런 사용자가 없음"은 고치는 방법이
     * 달라 메시지로는 구분한다.
     */
    public static ApiException malformedUserHeader(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER", detail);
    }

    /** 400 — 쿼리 파라미터·경로 변수 형식 오류. */
    public static ApiException invalidParameter(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message);
    }

    public static ApiException inspectionNotFound(Object inspectionId) {
        return new ApiException(HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND",
                "존재하지 않는 inspection입니다: " + inspectionId);
    }

    /**
     * 404 — finding이 없거나 <b>경로의 inspection에 속하지 않는다</b> (계약서 §1 에러 코드 표).
     *
     * <p>소속이 다른 것을 403이나 409로 구분하지 않는다. 다른 검사 건의 finding id를 넣어 보는
     * 것으로 "그 id가 존재하는가"를 알 수 있게 되면 안 되고, 화면상으로도 존재하지 않는 조합이다.
     */
    public static ApiException findingNotFound(Object findingId) {
        return new ApiException(HttpStatus.NOT_FOUND, "FINDING_NOT_FOUND",
                "존재하지 않는 finding입니다: " + findingId);
    }

    /**
     * 409 — 이미 ACCEPTED/REJECTED로 확정된 finding에 재요청.
     *
     * <p>멱등 처리로 200을 주지 않는다. 200을 주면 {@code reviewed_at}·{@code reviewed_by}가
     * 덮어써져 "누가 언제 확정했는가"라는 증적이 손상된다.
     */
    public static ApiException findingAlreadyReviewed(Object findingId, ReviewStatus current) {
        return new ApiException(HttpStatus.CONFLICT, "FINDING_ALREADY_REVIEWED",
                "finding " + findingId + "는 이미 " + current + " 상태입니다.");
    }

    /**
     * 409 — 규칙 finding({@code source='RULE'} 또는 {@code review_status='CONFIRMED'})에 PATCH
     * (기획서 0.5 D13).
     *
     * <p>규칙 판정은 사람이 번복하지 않는다 (기획서 4장). 사람이 확정하는 것은 AI 후보뿐이며,
     * 그 경계가 이 프로젝트의 핵심 설계 주장이다.
     */
    /**
     * 확정은 보안 담당자만 한다 (0.5.1 D24).
     *
     * <p>인증은 여전히 없다 — {@code X-User-Id}가 누구인지 말할 뿐 그 사람임을 증명하지
     * 않는다. 그럼에도 역할을 확인하는 이유는, "AI는 제안하고 사람이 확정한다"에서
     * 사람이 <b>아무나</b>가 아니기 때문이다. 화면에서만 버튼을 가리면 그 경계가 주장에
     * 그친다.
     */
    public static ApiException notReviewer(Object userId) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE",
                "확정은 보안 담당자만 할 수 있습니다. 사용자 " + userId + "는 SECURITY_ADMIN이 아닙니다.");
    }

    public static ApiException ruleFindingNotReviewable(Object findingId) {
        return new ApiException(HttpStatus.CONFLICT, "RULE_FINDING_NOT_REVIEWABLE",
                "finding " + findingId + "는 규칙 판정이라 사람이 확정하지 않습니다.");
    }

    public static ApiException messageNotFound(Object messageId) {
        return new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND",
                "message " + messageId + "를 찾을 수 없습니다.");
    }

    /**
     * 해제 요청은 자기 프롬프트에만 건다 (D25).
     *
     * <p>남의 문장을 두고 원문을 열어 달라고 할 수는 없다. 이 요청이 원문 열람의 근거가
     * 되므로, 근거를 만드는 사람과 문장을 쓴 사람이 같아야 한다.
     */
    public static ApiException notAuthor(Object messageId) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_NOT_AUTHOR",
                "해제 요청은 작성자만 할 수 있습니다. message " + messageId + "는 다른 사용자의 것입니다.");
    }

    /** 마스킹된 건에만 해제가 성립한다. 가린 것이 없으면 풀 것도 없다 */
    public static ApiException notMasked(Object messageId) {
        return new ApiException(HttpStatus.CONFLICT, "NOT_MASKED",
                "message " + messageId + "는 마스킹된 건이 아니라 해제 요청 대상이 아닙니다.");
    }

    public static ApiException unmaskRequestExists(Object messageId) {
        return new ApiException(HttpStatus.CONFLICT, "UNMASK_REQUEST_EXISTS",
                "message " + messageId + "에는 이미 해제 요청이 있습니다.");
    }

    public static ApiException unmaskRequestNotFound(Object requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "UNMASK_REQUEST_NOT_FOUND",
                "해제 요청 " + requestId + "를 찾을 수 없습니다.");
    }

    /** 출력 검사는 나간 적 있는 프롬프트에만 붙는다. 차단된 건은 애초에 답변이 없다 */
    public static ApiException notSent(Object messageId) {
        return new ApiException(HttpStatus.CONFLICT, "NOT_SENT",
                "message " + messageId + "는 전송되지 않아 검사할 답변이 없습니다.");
    }

    public static ApiException responseAlreadyInspected(Object messageId) {
        return new ApiException(HttpStatus.CONFLICT, "RESPONSE_ALREADY_INSPECTED",
                "message " + messageId + "의 답변은 이미 검사했습니다.");
    }

    /** 키가 없어 답변 받기가 꺼진 상태. 화면은 이걸 보고 붙여넣기 방식으로 물러난다 */
    public static ApiException answerUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ANSWER_UNAVAILABLE",
                "답변 받기가 꺼져 있습니다. ANTHROPIC_API_KEY가 설정되지 않았습니다.");
    }

    public static ApiException answerFailed(String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "ANSWER_FAILED",
                "모델에서 답변을 받지 못했습니다. " + detail);
    }

    /** 모델이 답변을 거절했다. 오류가 아니라 결과다 — 그대로 보여주고 사람이 판단한다 */
    public static ApiException answerRefused(String explanation) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ANSWER_REFUSED",
                "모델이 답변을 거절했습니다." + (explanation == null || explanation.isBlank() ? "" : " " + explanation));
    }

    public static ApiException unmaskAlreadyDecided(Object requestId, Object current) {
        return new ApiException(HttpStatus.CONFLICT, "UNMASK_ALREADY_DECIDED",
                "해제 요청 " + requestId + "는 이미 " + current + "로 확정되었습니다.");
    }
}
