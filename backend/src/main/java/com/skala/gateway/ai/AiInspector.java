package com.skala.gateway.ai;

/**
 * AI 확장 지점 (기획서 3.4, 9.1).
 *
 * <p>인터페이스가 하나뿐인 것이 요점이다. Mock↔LLM 교체가 {@code @Profile} 전환과 환경변수
 * 주입만으로 끝나고, FE가 보는 엔드포인트와 JSON은 불변이다. 이것이 Interface First 원칙의 증거다.
 *
 * <p>구현체는 프로파일로 갈린다.
 * <ul>
 *   <li>{@link MockAiInspector} — {@code mock}(기본). 케이스별 고정 JSON, 2.5초 지연</li>
 *   <li>{@link LlmAiInspector} — {@code llm}. 클래스 골격과 프롬프트 조립만 (기획서 0.4 범위 결정)</li>
 * </ul>
 */
public interface AiInspector {

    /**
     * 마스킹 적용본을 검토해 위험 후보를 제안한다. 결정하지 않는다.
     *
     * @param request 원문이 아닌 마스킹 적용본과 그 판정 맥락
     * @return 위험 후보·확인 필요 항목. 결정 필드는 스키마에 존재하지 않는다
     * @throws IllegalStateException {@code hits}가 비어 있는 경우. 규칙 엔진이 REVIEW 판정 없이
     *         AI를 호출했다는 뜻이므로 버그다 (기획서 9.5)
     */
    AiAssessment inspect(AiInspectionRequest request);
}
