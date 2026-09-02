package com.skala.gateway.api.dto;

/**
 * {@code POST /api/v1/messages} 요청 (기획서 8.4).
 *
 * <p>본문 누락·빈 문자열·공백은 400 {@code INVALID_REQUEST}다. 검증을
 * {@code jakarta.validation}이 아니라 컨트롤러에서 직접 하는 이유는 계약서 §1의 에러 봉투를
 * 그대로 내보내기 위해서다 — Bean Validation 실패는 {@code @RestControllerAdvice}가 생기기
 * 전까지 Spring 기본 400 본문으로 나간다.
 */
public record MessageRequest(String text) {

    public boolean isBlank() {
        return text == null || text.isBlank();
    }
}
