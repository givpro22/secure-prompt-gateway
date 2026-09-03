package com.skala.gateway.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.skala.gateway.config.AnswerProperties;
import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Claude Messages API로 답변을 받아온다 (UC-08 앞단).
 *
 * <p><b>여기로 들어오는 텍스트는 마스킹본뿐이다.</b> 호출자({@code AnswerService})가
 * {@code message.submitted_text}만 넘기며, 원문은 어떤 경로로도 이 클래스에 닿지 않는다.
 * 게이트웨이가 하는 일이 정확히 이것이다 — 모델은 {@code [고객명]}을 보지 {@code 서지윤}을
 * 보지 않는다.
 *
 * <p>공식 Java SDK({@code com.anthropic:anthropic-java})를 쓴다. 모델·키·상한은 전부
 * {@link AnswerProperties}(환경변수)에서 오고 코드에는 없다.
 *
 * <p>Claude Opus 5는 thinking이 기본으로 켜져 있어 따로 지정하지 않는다. 깊이는
 * {@code effort}로만 조절한다. 서버 측 fallback(거절 시 다른 모델로 재시도)은 붙이지
 * 않았다 — 거절이면 그대로 거절로 돌려주고 사람이 본다.
 */
@Component
@ConditionalOnProperty(name = "answer.provider", havingValue = "claude", matchIfMissing = true)
public class ClaudeAnswerClient implements AnswerClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAnswerClient.class);

    /**
     * 답변의 성격만 정한다. 무엇을 가리라고 하지 않는다 — 가리는 것은 돌아온 뒤 출력
     * 검사가 하는 일이고, 모델에게 부탁해서 되는 일이 아니다.
     */
    static final String SYSTEM = """
            당신은 사내 직원의 업무를 돕는 비서다. 한국어로 간결하게 답한다.
            질문에 [고객명], [전화번호] 같은 대괄호 라벨이 있으면 그것은 가려진 자리다.
            라벨을 그대로 두고 답하며, 그 자리에 들어갈 값을 추측하거나 지어내지 않는다.
            """;

    private final AnswerProperties properties;
    private final AnthropicClient client;

    public ClaudeAnswerClient(AnswerProperties properties) {
        this.properties = properties;
        // 키가 없으면 클라이언트를 만들지 않는다. enabled()가 false인 동안은 부르지 않는다.
        this.client = properties.enabled()
                ? AnthropicOkHttpClient.builder()
                        .apiKey(properties.apiKey())
                        .timeout(Duration.ofMillis(properties.timeoutMs()))
                        .build()
                : null;
    }

    @Override
    public boolean enabled() {
        return client != null;
    }

    @Override
    public String providerName() {
        return "Claude (" + properties.model() + ")";
    }

    /** 성공하면 답변 본문, 거절이면 {@link Refused}. 네트워크·서비스 오류는 {@link AnswerCallException} */
    @Override
    public Result ask(String maskedPrompt) {
        if (!enabled()) {
            throw new IllegalStateException("answer.api-key가 비어 있어 호출할 수 없습니다");
        }
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(SYSTEM)
                .outputConfig(OutputConfig.builder().effort(effortOf(properties.effort())).build())
                .addUserMessage(maskedPrompt)
                .build();

        Message response;
        try {
            response = client.messages().create(params);
        } catch (AnthropicServiceException e) {
            throw new AnswerCallException("Claude 호출 실패: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new AnswerCallException("Claude 연결 실패: " + e.getMessage(), e);
        }

        String stop = response.stopReason().map(Object::toString).orElse("");
        if ("refusal".equalsIgnoreCase(stop)) {
            String why = response.stopDetails()
                    .map(d -> d.explanation().orElse(""))
                    .orElse("");
            log.info("Claude 거절 model={} explanation={}", properties.model(), why);
            return new Refused(why);
        }

        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining("\n"))
                .trim();
        log.info("Claude 답변 model={} stop={} in={} out={}", properties.model(), stop,
                response.usage().inputTokens(), response.usage().outputTokens());
        return new Answered(text, properties.model());
    }

    private static OutputConfig.Effort effortOf(String value) {
        return switch ((value == null ? "medium" : value).toLowerCase(Locale.ROOT)) {
            case "low" -> OutputConfig.Effort.LOW;
            case "high" -> OutputConfig.Effort.HIGH;
            default -> OutputConfig.Effort.MEDIUM;
        };
    }
}
