package com.skala.gateway.api;

import com.skala.gateway.api.dto.ErrorResponse;
import com.skala.gateway.config.WebConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 계약서 §1의 에러 봉투 {@code {code, message, details}}로 변환한다 (기획서 8.1).
 *
 * <p>이 advice가 생기기 전까지 {@code X-User-Id} 누락은 Spring 기본 400 본문
 * ({@code {"timestamp":…,"status":400,"error":"Bad Request","path":…}})으로 나갔다.
 * FE가 {@code code}로 분기할 수 없어 "헤더가 없다"와 "본문이 비었다"를 구분하지 못한다.
 *
 * <h2>여기서 처리하지 않는 것</h2>
 *
 * <p><b>403(BLOCK)은 예외가 아니다.</b> {@code MessageController}가 판정 객체를 직접 반환하며
 * 이 advice를 거치지 않는다 (계약서 C2). BLOCK은 처리 실패가 아니라 정상 수행된 판정이고,
 * FE는 차단 사유(규칙 코드·출처)를 S4 화면에 그려야 하므로 {@code {code,message}}로는 화면을
 * 만들 수 없다.
 *
 * <p><b>기존 컨트롤러 4종이 직접 반환하는 400·404도 가로채지 않는다.</b> 그쪽은 예외를 던지지
 * 않고 {@code ResponseEntity.badRequest().body(ErrorResponse…)}로 반환하므로 advice와 경로가
 * 겹치지 않는다. 결과 봉투는 어느 쪽이든 같다.
 *
 * <p><b>포괄 {@code @ExceptionHandler(Exception.class)}를 두지 않는다.</b> 계약서 §1의 에러
 * 코드 표에 500이 없고, 무엇이든 봉투로 감싸면 {@code PolicyService}의 "활성 규칙 0건"처럼
 * 드러나야 할 서버 오류가 400대 응답처럼 보인다. 정의된 예외만 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 서비스·컨트롤러가 계약서 §1의 코드를 지정해 던진 예외. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    /**
     * {@code WebConfig}의 인자 리졸버가 헤더 부재에 던진다 → {@code MISSING_USER_HEADER}.
     *
     * <p>{@code MissingRequestHeaderException}은 {@code ServletRequestBindingException}의
     * 하위 타입이다. Spring이 더 구체적인 핸들러를 고르므로 두 메서드가 공존해도 헤더 부재가
     * {@code INVALID_USER}로 새지 않는다.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        ApiException mapped = WebConfig.USER_HEADER.equalsIgnoreCase(e.getHeaderName())
                ? ApiException.missingUserHeader(e.getHeaderName())
                : ApiException.invalidRequest(e.getHeaderName() + " 헤더가 필요합니다.");
        return handleApiException(mapped);
    }

    /** 같은 리졸버가 헤더 값이 숫자가 아닐 때 던진다 → {@code INVALID_USER}. */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponse> handleBindingException(ServletRequestBindingException e) {
        return handleApiException(ApiException.malformedUserHeader(e.getMessage()));
    }

    /** 본문이 JSON이 아니거나 필드 타입이 맞지 않는다 (빈 본문·깨진 JSON 포함). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("요청 본문을 읽을 수 없습니다: {}", e.getMessage());
        return handleApiException(ApiException.invalidRequest("요청 본문이 올바른 JSON이 아닙니다."));
    }

    /** {@code /inspections/{id}}에 숫자가 아닌 값이 오는 경우 등. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return handleApiException(ApiException.invalidParameter(
                e.getName() + "의 값이 올바르지 않습니다: " + e.getValue()));
    }
}
