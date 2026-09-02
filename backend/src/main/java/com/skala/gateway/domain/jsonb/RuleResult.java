package com.skala.gateway.domain.jsonb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.skala.gateway.domain.enums.Obligation;
import com.skala.gateway.domain.enums.PolicyCategory;
import com.skala.gateway.domain.enums.RuleAction;
import com.skala.gateway.domain.enums.Severity;
import java.util.List;

/**
 * {@code inspection.rule_result} JSONB의 Java 표현 (기획서 6.2, 8.4, 계약서 §4 인계 2).
 *
 * <p>{@code matches}와 {@code appliedRuleCodes}는 다르다. 앞은 finding이 생성된 매칭이고
 * 뒤는 로드된 활성 규칙 전체다. D1 중첩 억제로 매칭됐으나 finding이 없는 규칙
 * ({@code SEC-PRIVIP-03})은 {@code appliedRuleCodes}에만 남는다.
 *
 * @param matches          중첩 억제(0.5 D1) 후의 매칭 목록
 * @param appliedRuleCodes 로드된 활성 규칙 코드 전체
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleResult(List<RuleMatch> matches, List<String> appliedRuleCodes) {

    /**
     * @param code           규칙 코드
     * @param category       규칙이 속한 정책의 카테고리
     * @param action         MASK / BLOCK / REVIEW
     * @param span           {@code [start, end)} 2원소. <b>원문(original_text) 기준</b>이며
     *                       재계산하지 않는다 (0.5 D3)
     * @param matchedKeyword KEYWORD 규칙 매칭에만 값이 있다. REGEX는 {@code null}이다 —
     *                       매칭 문자열을 넣으면 주민번호 원문이 JSONB에 그대로 남는다
     * @param severity       HIGH / MEDIUM / LOW
     * @param obligation     LEGAL / INTERNAL
     * @param source         법령 조문 또는 사규 항목
     * @param embargoUntil   엠바고 <b>해제일</b>({@code yyyy-MM-dd}). 이 날부터 공개할 수 있다.
     *                       엠바고 규칙이 아니면 {@code null}이다.
     *                       <p>{@link java.time.LocalDate}가 아니라 문자열인 이유는 이 record가
     *                       JSONB로 저장되기 때문이다. Hibernate는 자체 ObjectMapper로 직렬화하므로
     *                       JavaTimeModule 등록 여부에 따라 {@code "2026-09-20"}이 되기도
     *                       {@code [2026,9,20]}이 되기도 한다. 화면 계약이 그것에 흔들려서는 안 된다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleMatch(
            String code,
            PolicyCategory category,
            RuleAction action,
            List<Integer> span,
            String matchedKeyword,
            Severity severity,
            Obligation obligation,
            String source,
            String embargoUntil) {
    }
}
