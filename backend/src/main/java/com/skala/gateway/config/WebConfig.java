package com.skala.gateway.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS와 {@code X-User-Id} 인자 리졸버 (기획서 8.1, 11.4).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 기획서 8.1이 지정한 현재 사용자 전달 헤더. */
    public static final String USER_HEADER = "X-User-Id";

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${gateway.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                // Location은 202 응답의 폴링 URL을 담는다. 노출하지 않으면 브라우저에서 읽을 수 없다.
                .exposedHeaders("Location")
                .maxAge(3600);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
    }

    /**
     * {@code @CurrentUserId Long} 파라미터에 헤더 값을 주입한다.
     *
     * <p>여기서 던지는 두 예외는 Spring 기본 처리로 400이 된다. {@code api} 패키지에
     * {@code @RestControllerAdvice}가 추가되면 계약서 §1의 에러 봉투
     * ({@code MISSING_USER_HEADER} / {@code INVALID_USER})로 변환할 것.
     */
    static class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUserId.class)
                    && Long.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      WebDataBinderFactory binderFactory) throws Exception {
            String raw = webRequest.getHeader(USER_HEADER);
            if (raw == null || raw.isBlank()) {
                throw new MissingRequestHeaderException(USER_HEADER, parameter);
            }
            try {
                return Long.valueOf(raw.trim());
            } catch (NumberFormatException e) {
                throw new ServletRequestBindingException(
                        USER_HEADER + " 헤더가 숫자가 아닙니다: " + raw, e);
            }
        }
    }
}
