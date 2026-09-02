package com.skala.gateway.api.dto;

/**
 * 에러 봉투 (기획서 8.1, 계약서 §1).
 *
 * <p><b>400·404·409에만 쓴다.</b> 403(BLOCK)은 처리 실패가 아니라 정상 수행된 판정이므로
 * 판정 객체를 반환한다 (계약서 C2). FE는 403 응답에서 {@code code} 필드를 찾지 않는다.
 *
 * <p>{@code details}는 값이 없어도 키를 남긴다 ({@code default-property-inclusion: always}).
 * 필드가 사라지면 FE가 "필드 없음"과 "null"을 구분하는 방어 코드를 쓰게 된다 (계약서 C3).
 */
public record ErrorResponse(String code, String message, Object details) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    /** 요청 본문 누락, {@code text}가 빈 문자열/공백. */
    public static ErrorResponse invalidRequest(String message) {
        return of("INVALID_REQUEST", message);
    }

    /**
     * {@code X-User-Id}가 숫자가 아니거나 존재하지 않는 사용자.
     *
     * <p>401이 아닌 이유는 인증 체계가 있다는 뜻이 되기 때문이다 — 이번 범위에 인증이 없다
     * (0.3, 계약서 C8).
     */
    public static ErrorResponse invalidUser(Object userId) {
        return of("INVALID_USER", "존재하지 않는 사용자입니다: " + userId);
    }

    /** {@code deptId} 비숫자·누락, {@code status} enum 외 값, {@code page}/{@code size} 음수. */
    public static ErrorResponse invalidParameter(String message) {
        return of("INVALID_PARAMETER", message);
    }

    public static ErrorResponse inspectionNotFound(Object inspectionId) {
        return of("INSPECTION_NOT_FOUND", "존재하지 않는 inspection입니다: " + inspectionId);
    }
}
