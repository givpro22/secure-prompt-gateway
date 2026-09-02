package com.skala.gateway.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code X-User-Id} 헤더의 사용자 id를 컨트롤러 파라미터로 주입한다 (기획서 8.1).
 *
 * <p>인증은 이번 범위에 없다(기획서 0.3). 헤더 값은 화면의 계정 전환 드롭다운이 보내는 것이며,
 * 신뢰 경계가 아니라 "누가 제출/확정했는지"를 감사 기록에 남기기 위한 식별자다.
 *
 * <pre>{@code
 * @PostMapping("/messages")
 * ResponseEntity<?> submit(@CurrentUserId Long userId, @RequestBody MessageRequest req) { ... }
 * }</pre>
 *
 * <p>헤더가 없거나 숫자가 아니면 400으로 응답한다 (계약서 §1 에러 코드 표).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
