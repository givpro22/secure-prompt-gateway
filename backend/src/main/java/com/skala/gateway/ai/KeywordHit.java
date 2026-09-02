package com.skala.gateway.ai;

/**
 * KEYWORD 규칙 매칭 근거 (기획서 9.1).
 *
 * <p>{@code source}는 현재 {@code policy_rule.source} 값에서 온다. RAG 확장 시 이 자리가
 * {@code knowledge_source} 검색 결과로 대체된다 (기획서 3.4, 교수 피드백 F4).
 *
 * @param keyword  매칭된 키워드 (예: "A사")
 * @param ruleCode 매칭시킨 규칙 코드 (예: "CONF-CLIENT-01")
 * @param source   근거 출처 (예: "고객사 NDA 목록 v3")
 */
public record KeywordHit(String keyword, String ruleCode, String source) {
}
