package com.skala.gateway.api;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 쿼리 파라미터 파싱.
 *
 * <p>{@code @RequestParam Long}으로 받으면 비숫자 입력에서 Spring이
 * {@code MethodArgumentTypeMismatchException}을 던지고 계약서 §1의 에러 봉투가 아니라 기본 400
 * 본문이 나간다. 문자열로 받아 여기서 파싱하고 컨트롤러가 봉투를 만든다.
 *
 * <p>{@code @RestControllerAdvice}가 추가되면({@code api-ai-architect}, 다음 라운드) 이 방식을
 * 예외 변환으로 옮길 수 있다. 그때까지는 각 컨트롤러가 직접 봉투를 반환한다.
 */
final class QueryParams {

    private QueryParams() {
    }

    /** 빈 문자열은 "생략"으로 본다. 비숫자면 {@link NumberFormatException}. */
    static Long optionalLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Long.valueOf(raw.trim());
    }

    /** 기본값이 있는 정수. 비숫자면 {@link NumberFormatException}. */
    static int intOrDefault(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(raw.trim());
    }

    /** ISO 8601 오프셋 시각. 형식이 틀리면 {@link DateTimeParseException}. */
    static OffsetDateTime optionalTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(raw.trim());
    }
}
