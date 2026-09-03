package com.skala.gateway.ai;

/**
 * 마스킹본을 보내 답변을 받아오는 제공자 (UC-08 앞단).
 *
 * <p>구현이 셋이다 — Claude SDK, OpenAI 호환 REST, 사내 Ollama. 어느 것이 도는지는
 * {@code ANSWER_PROVIDER} 환경변수가 정하고 코드는 모른다. {@code AnswerService}는 이
 * 인터페이스만 본다. 기획서 4쪽 Security &amp; Config Isolation — "코드 변경 없이
 * 모델·키 교체"를 이 인터페이스 하나가 증명한다.
 *
 * <p>어느 구현이든 들어오는 텍스트는 마스킹본뿐이다.
 */
public interface AnswerClient {

    boolean enabled();

    /** 사람이 읽을 제공자 이름. 화면과 감사 로그에 남긴다 */
    String providerName();

    Result ask(String maskedPrompt);

    sealed interface Result permits Answered, Refused {
    }

    record Answered(String text, String model) implements Result {
    }

    record Refused(String explanation) implements Result {
    }

    class AnswerCallException extends RuntimeException {
        /** 과부하·할당량처럼 다른 모델로 넘어가면 될 수 있는 오류인지 */
        public final boolean retryable;

        public AnswerCallException(String message, Throwable cause) {
            this(message, cause, false);
        }

        public AnswerCallException(String message, Throwable cause, boolean retryable) {
            super(message, cause);
            this.retryable = retryable;
        }
    }
}
