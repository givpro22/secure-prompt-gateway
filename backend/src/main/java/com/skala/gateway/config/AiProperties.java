package com.skala.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ai.*} 설정 바인딩 (기획서 11.3).
 *
 * <p>키·모델·지연은 전부 환경변수, 정책·규칙·임계값은 전부 DB. 코드에는 어느 쪽도 없다.
 * 이것이 Security & Config Isolation 원칙의 증거다.
 *
 * <p>{@code @Value}를 여기저기 뿌리지 않고 한 곳에 모으는 이유는 어떤 키가 존재하는지 한눈에
 * 보이게 하기 위함이다. 기본값은 {@code application.yml}이 환경변수 폴백으로 정의한다.
 *
 * @param provider      문서상 스위치 (mock | llm). 실제 빈 선택은 {@code @Profile}이 한다 (기획서 9.6)
 * @param endpoint      llm 전용. 사내 호스팅 모델 주소를 가리키면 외부 전송이 없다 (F3 대응)
 * @param apiKey        llm 전용
 * @param model         llm 전용
 * @param temperature   기획서 9.3 제약: 0
 * @param maxTokens     기획서 9.3 제약: 800
 * @param timeoutMs     llm 호출 타임아웃
 * @param maxInputChars 프롬프트 최대 입력 길이. 초과 시 앞부분만 전달하고 missingContext에 절단 기록
 * @param batchSize     llm 전용. 한 번의 호출에 넣는 문장 수. <b>기본 1이다.</b> 여러 문장을 묶으면
 *                      같은 문장이 이웃 문장에 따라 다른 판정을 받는다 — 배치 11에서 골든셋이
 *                      38~40/46 사이를 오갔고, 1로 낮추자 43/46으로 안정됐다 (_workspace/05 §2)
 * @param mock          Mock 전용 설정
 */
@ConfigurationProperties("ai")
public record AiProperties(
        String provider,
        String endpoint,
        String apiKey,
        String model,
        double temperature,
        int maxTokens,
        long timeoutMs,
        int maxInputChars,
        int batchSize,
        Mock mock) {

    /**
     * @param delayMs     Mock 지연. 기본 2500ms. <b>최적화하지 않는다.</b> 즉시 응답하면 202 비동기
     *                    설계가 화면에 드러나지 않아 Asynchronous Pipeline 원칙 증명이 실패한다
     *                    (기획서 9.5, 14장 리스크)
     * @param failKeyword 이 키워드가 텍스트에 포함되면 RuntimeException을 던진다. {@code ai_status=FAILED}
     *                    경로가 실제로 동작해야 "AI가 죽어도 사람 검토로 폴백된다"는 주장이 증명된다
     */
    public record Mock(long delayMs, String failKeyword) {
    }
}
