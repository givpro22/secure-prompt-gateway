package com.skala.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code answer.*} — Claude에 마스킹본을 보내 답변을 받아오는 설정 (UC-08 앞단).
 *
 * <p>{@link AiProperties}와 따로 두는 이유는 역할이 다르기 때문이다. {@code ai.*}는 판정을
 * 돕는 검사기(사내 Ollama, 외부로 나가지 않음)이고, 여기는 <b>답변을 만드는 외부 모델</b>이다.
 * 키·모델·엔드포인트가 다르고, 한쪽이 꺼져도 다른 쪽은 돌아야 한다.
 *
 * <p>키가 비어 있으면 기능이 꺼진다. 코드 변경 없이 환경변수만으로 켜고 끄는 것이
 * 기획서 4쪽 Security &amp; Config Isolation 이다.
 *
 * @param provider  claude | openai | ollama. 어느 구현이 뜨는지 정한다
 * @param endpoint  openai·ollama 전용 base URL
 * @param apiKey    claude는 {@code ANTHROPIC_API_KEY}, openai는 {@code ANSWER_API_KEY}. 비면 비활성
 * @param model     기본 {@code claude-opus-5}
 * @param maxTokens 답변 상한
 * @param effort    low | medium | high
 * @param timeoutMs 호출 타임아웃
 */
@ConfigurationProperties("answer")
public record AnswerProperties(String provider, String endpoint, String apiKey, String model,
                               long maxTokens, String effort, long timeoutMs) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
