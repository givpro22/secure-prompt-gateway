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
 * 사내 Ollama {@code /api/chat}로 답변을 받아온다. 외부로 나가는 요청이 없다.
 *
 * <p>검사기용 {@link OllamaChatClient}와 엔드포인트를 같이 쓸 수 있지만 클래스는 따로다 —
 * 그쪽은 JSON 스키마를 강제하는 판정 호출이고 여기는 자유 답변이다.
 */
@Component
@ConditionalOnProperty(name = "answer.provider", havingValue = "ollama")
public class OllamaAnswerClient implements AnswerClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaAnswerClient.class);

    private final AnswerProperties properties;
    private final RestClient rest;

    public OllamaAnswerClient(AnswerProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        this.rest = RestClient.builder().baseUrl(properties.endpoint()).requestFactory(factory).build();
    }

    @Override
    public boolean enabled() {
        return properties.endpoint() != null && !properties.endpoint().isBlank();
    }

    @Override
    public String providerName() {
        return "사내 Ollama (" + properties.model() + ")";
    }

    @Override
    public Result ask(String maskedPrompt) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "stream", false,
                "options", Map.of("num_predict", properties.maxTokens()),
                "messages", List.of(
                        Map.of("role", "system", "content", ClaudeAnswerClient.SYSTEM),
                        Map.of("role", "user", "content", maskedPrompt)));
        JsonNode json;
        try {
            json = rest.post().uri("/api/chat").body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AnswerCallException("Ollama 호출 실패: " + e.getMessage(), e);
        }
        String text = json == null ? "" : json.path("message").path("content").asText("").trim();
        log.info("답변 수신 provider=ollama model={}", properties.model());
        return new Answered(text, properties.model());
    }
}
