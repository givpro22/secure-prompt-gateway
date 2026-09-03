package com.skala.gateway.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.skala.gateway.config.AnswerProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI 호환 {@code /v1/chat/completions} 로 답변을 받아온다.
 *
 * <p>이 한 클래스가 OpenAI, Groq, OpenRouter, Together, vLLM, 그리고 Ollama의
 * {@code /v1} 엔드포인트를 전부 덮는다. 요청·응답 모양이 같기 때문이다. 어디로 가는지는
 * {@code ANSWER_ENDPOINT}와 {@code ANSWER_MODEL}이 정한다.
 *
 * <p>SDK 없이 REST로 부른다. 제공자마다 SDK를 들이면 "코드 변경 없이 교체"가 거짓이
 * 된다. Claude만 공식 SDK를 쓰는 이유는 응답 구조(거절 사유 등)가 달라서다.
 */
@Component
@ConditionalOnProperty(name = "answer.provider", havingValue = "openai")
public class OpenAiCompatibleAnswerClient implements AnswerClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAnswerClient.class);

    private final AnswerProperties properties;
    private final RestClient rest;

    public OpenAiCompatibleAnswerClient(AnswerProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        RestClient.Builder b = RestClient.builder()
                .baseUrl(properties.endpoint())
                .requestFactory(factory);
        // Ollama /v1 처럼 키가 필요 없는 곳도 있다. 비어 있으면 헤더를 붙이지 않는다.
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + properties.apiKey());
        }
        this.rest = b.build();
    }

    @Override
    public boolean enabled() {
        return properties.endpoint() != null && !properties.endpoint().isBlank()
                && properties.model() != null && !properties.model().isBlank();
    }

    @Override
    public String providerName() {
        return "OpenAI 호환 (" + properties.model() + ")";
    }

    @Override
    public Result ask(String maskedPrompt) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "max_tokens", properties.maxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", ClaudeAnswerClient.SYSTEM),
                        Map.of("role", "user", "content", maskedPrompt)));
        JsonNode json;
        try {
            json = rest.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AnswerCallException("호출 실패: " + e.getMessage(), e);
        }
        if (json == null || json.path("choices").isEmpty()) {
            throw new AnswerCallException("응답에 choices가 없습니다", null);
        }
        JsonNode choice = json.path("choices").get(0);
        String finish = choice.path("finish_reason").asText("");
        if ("content_filter".equals(finish)) {
            return new Refused("content_filter");
        }
        String text = choice.path("message").path("content").asText("").trim();
        log.info("답변 수신 provider=openai model={} finish={}", properties.model(), finish);
        return new Answered(text, properties.model());
    }
}
