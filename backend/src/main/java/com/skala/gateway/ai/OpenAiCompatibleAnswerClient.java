package com.skala.gateway.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.skala.gateway.config.AnswerProperties;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

    /**
     * 모델 대체 순서. {@code ANSWER_MODEL}이 쉼표로 여러 개면 앞에서부터 시도하고,
     * 과부하·할당량(429/503, "high demand") 오류일 때만 다음으로 넘어간다.
     * 인증 오류나 잘못된 요청은 모델을 바꿔도 같으므로 바로 올린다.
     *
     * 시연 전날 최신 모델이 "high demand"로 막히는 것을 봤다. 발표 중에 같은 일이
     * 나면 답변 받기 버튼이 그냥 실패하는데, 그건 게이트웨이 탓처럼 보인다.
     */
    @Override
    public Result ask(String maskedPrompt) {
        List<String> models = Arrays.stream(properties.model().split(","))
                .map(String::trim).filter(m -> !m.isEmpty()).toList();
        AnswerCallException last = null;
        for (String model : models) {
            try {
                return askModel(model, maskedPrompt);
            } catch (AnswerCallException e) {
                if (!e.retryable) throw e;
                log.warn("모델 {} 사용 불가, 다음으로 넘어감: {}", model, e.getMessage());
                last = e;
            }
        }
        throw last != null ? last : new AnswerCallException("시도할 모델이 없습니다", null);
    }

    private Result askModel(String model, String maskedPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", properties.maxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", ClaudeAnswerClient.SYSTEM),
                        Map.of("role", "user", "content", maskedPrompt)));
        JsonNode json;
        try {
            json = rest.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int st = e.getStatusCode().value();
            throw new AnswerCallException("호출 실패 (" + st + "): " + e.getResponseBodyAsString(), e,
                    st == 429 || st == 503 || st == 502);
        } catch (RestClientException e) {
            throw new AnswerCallException("호출 실패: " + e.getMessage(), e, true);
        }
        // Gemini의 OpenAI 호환 엔드포인트는 오류를 [{"error": …}] 처럼 배열로 감싼다.
        // 배열이면 첫 원소를 본다. 이유를 버리고 "choices 없음"이라고만 하면 디버깅이 안 된다.
        if (json != null && json.isArray() && json.size() > 0) {
            json = json.get(0);
        }
        if (json != null && json.has("error")) {
            JsonNode err = json.path("error");
            String why = err.isObject() ? err.path("message").asText(err.toString()) : err.asText();
            // Gemini 무료 등급은 모델별 하루 20회(RPD)가 먼저 닫힌다. 그 오류도 다음 모델로 넘긴다.
            String w = why.toLowerCase(Locale.ROOT);
            boolean busy = w.contains("high demand") || w.contains("quota") || w.contains("overloaded")
                    || w.contains("resource_exhausted") || w.contains("rate limit") || w.contains("exceeded");
            throw new AnswerCallException("제공자 오류: " + why, null, busy);
        }
        if (json == null || json.path("choices").isEmpty()) {
            throw new AnswerCallException("응답에 choices가 없습니다: " + (json == null ? "null" : json.toString()), null);
        }
        JsonNode choice = json.path("choices").get(0);
        String finish = choice.path("finish_reason").asText("");
        if ("content_filter".equals(finish)) {
            return new Refused("content_filter");
        }
        String text = choice.path("message").path("content").asText("").trim();
        log.info("답변 수신 provider=openai model={} finish={}", model, finish);
        return new Answered(text, model);
    }
}
