package com.skala.gateway.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.skala.gateway.config.AiProperties;

/**
 * Ollama {@code /api/chat} 호출 (기획서 9.6 교체 절차).
 *
 * <p>의존성을 새로 넣지 않는다. Spring Boot 3.5의 {@code spring-web}에 {@link RestClient}가
 * 들어 있다.
 *
 * <p><b>{@code format}에 JSON Schema를 실어 보낸다.</b> 모델에게 "JSON만 반환하라"고 문장으로
 * 부탁하는 방식은 코드 펜스나 설명 문장이 섞여 파싱이 깨진다. Ollama의 구조화 출력은 enum까지
 * 강제하므로 라벨 오타나 스키마 밖의 값이 나올 수 없다.
 *
 * <p>{@code ai.endpoint}가 사내 주소를 가리키면 외부로 나가는 요청이 없다 (교수 피드백 F3).
 * 이 클래스가 받는 것은 {@link AiInspectionRequest#maskedText()}에서 잘라낸 문장뿐이고 원문에
 * 접근할 경로가 없다.
 */
@Component
@Profile("llm")
public class OllamaChatClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public OllamaChatClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        // 타임아웃을 걸지 않으면 모델이 멈춘 날 @Async 스레드가 영구히 잡힌다. 풀이 4개뿐이라
        // 네 건이면 AI 검사가 통째로 정지하고, inspection은 PENDING에 남아 FE 폴링이 끝나지 않는다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.endpoint())
                .build();
    }

    /**
     * 시스템 프롬프트와 사용자 메시지를 보내고 구조화된 JSON 응답을 받는다.
     *
     * @param systemPrompt 기획서 9.2 시스템 프롬프트
     * @param userMessage  번호가 붙은 문장 목록
     * @param schema       응답 JSON Schema. Ollama가 이 형태를 강제한다
     * @return 모델이 반환한 JSON의 루트 노드
     * @throws AiCallException 호출 실패 또는 응답이 JSON이 아닌 경우.
     *         {@link AiInspectionRunner}가 받아 {@code ai_status=FAILED}로 떨어뜨린다
     */
    public JsonNode chat(String systemPrompt, String userMessage, Map<String, Object> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("stream", false);
        // 추론 토큰을 켜면 지연이 배로 뛰는데 판정 품질은 나아지지 않았다. 라벨만 고르는 작업이다.
        body.put("think", false);
        body.put("format", schema);
        body.put("options", Map.of(
                "temperature", properties.temperature(),
                "num_predict", properties.maxTokens()));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));
        body.put("messages", messages);

        String raw;
        try {
            raw = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new AiCallException("Ollama 호출에 실패했습니다: " + properties.endpoint(), e);
        }

        try {
            JsonNode envelope = objectMapper.readTree(raw);
            String content = envelope.path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new AiCallException("Ollama 응답에 message.content가 없습니다.", null);
            }
            return objectMapper.readTree(content);
        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Ollama 응답 파싱 실패 — 원문 앞부분: {}",
                    raw == null ? "null" : raw.substring(0, Math.min(200, raw.length())));
            throw new AiCallException("Ollama 응답을 JSON으로 읽지 못했습니다.", e);
        }
    }

    /** 호출·파싱 실패. {@code RuntimeException}이라 러너의 폴백 경로로 그대로 흘러간다. */
    public static class AiCallException extends RuntimeException {
        public AiCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
