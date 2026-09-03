package com.skala.gateway.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock 답변 제공자.
 *
 * <p>여기서 지키려는 것은 두 가지다. 코드 질문에는 코드를 되돌려 유출 검사가 걸리게 하고,
 * 그 외에는 질문을 인용하지 않아 깨끗한 질문이 검토 대기로 새지 않게 한다.
 */
class MockAnswerClientTest {

    private final MockAnswerClient client = new MockAnswerClient();
    private final QuoteOverlapDetector overlap = new QuoteOverlapDetector();

    @Test
    @DisplayName("항상 켜져 있다 — 키도 네트워크도 쓰지 않는다")
    void alwaysEnabled() {
        assertThat(client.enabled()).isTrue();
        assertThat(client.providerName()).isNotBlank();
    }

    @Test
    @DisplayName("코드가 섞인 질문에는 그 코드를 되돌려준다")
    void echoesCode() {
        String masked = """
                결제 재시도에서 NPE 나는데 봐줘.
                RetryPolicy policy = config.getRetryPolicy();
                int max = policy.getMaxAttempts();""";

        AnswerClient.Result result = client.ask(masked);

        assertThat(result).isInstanceOf(AnswerClient.Answered.class);
        String text = ((AnswerClient.Answered) result).text();
        assertThat(text).contains("config.getRetryPolicy();");
        assertThat(text).contains("policy.getMaxAttempts();");
        // 되돌린 조각이 검사 문턱(40자)을 넘어야 유출 의심이 걸린다
        assertThat(overlap.detect(text, masked, 40).quote()).isTrue();
    }

    @Test
    @DisplayName("일반 질문에는 질문을 인용하지 않는다 — 인용하면 깨끗한 건도 검토 대기가 된다")
    void doesNotQuotePlainQuestion() {
        String masked = "이번 분기 팀 회고를 정리하는 방법을 알려줘. 항목은 다섯 개 정도면 좋겠어. 형식은 자유롭게.";

        AnswerClient.Result result = client.ask(masked);

        String text = ((AnswerClient.Answered) result).text();
        assertThat(text).doesNotContain("회고를 정리하는 방법");
        assertThat(overlap.detect(text, masked, 40).quote()).isFalse();
    }

    @Test
    @DisplayName("빈 입력에도 답을 준다")
    void handlesEmpty() {
        assertThat(client.ask("")).isInstanceOf(AnswerClient.Answered.class);
        assertThat(client.ask(null)).isInstanceOf(AnswerClient.Answered.class);
    }
}
