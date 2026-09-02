package com.skala.gateway.ai;

import java.util.List;

/**
 * AI 검사 출력 (기획서 9.4).
 *
 * <p><b>결정 필드가 없다.</b> {@code decision}, {@code action}, {@code block}, {@code allow}가
 * 스키마에 존재하지 않는 것이 책임 경계(기획서 4장)를 스키마 수준에서 강제하는 장치이며,
 * "AI가 오판하면 어떻게 되나"라는 예상 질의에 대한 답이다. 편의를 위해서라도 추가하지 않는다.
 *
 * <p>{@code confidence}도 두지 않는다. 실제 확률이 아닌 값을 확률처럼 보이게 하면 사람의 판단을
 * 왜곡한다.
 *
 * <p>AI가 할 수 있는 것은 후보 제안·근거 서술·확인 필요 항목 보고까지이며, 허용/마스킹/차단 결정은
 * 규칙 엔진과 사람의 몫이다.
 *
 * @param riskCandidates 위험 후보 목록. 근거가 불충분하면 빈 배열
 * @param missingContext 판단에 필요한데 입력에 없는 정보
 * @param reviewRequired 사람 검토가 필요한지 여부. 이것도 결정이 아니라 신호다
 */
public record AiAssessment(
        List<RiskCandidate> riskCandidates,
        List<String> missingContext,
        boolean reviewRequired) {

    /**
     * @param code      후보 코드. 패턴 {@code ^[A-Z]+-[A-Z-]+$}
     * @param category  기획서 9.4 스키마상 {@code CONFIDENTIAL}만 허용된다.
     *                  PII·SECRET은 규칙 엔진의 영역이므로 AI가 후보로 만들지 않는다 (9.2 금지 조항)
     * @param rationale 어떤 서술에서 후보를 도출했는지. 최소 10자
     * @param evidence  연결된 참조 근거
     */
    public record RiskCandidate(
            String code,
            String category,
            String rationale,
            List<Evidence> evidence) {
    }

    /**
     * @param source  근거 문서명
     * @param excerpt 해당 문서의 관련 발췌. 선택
     */
    public record Evidence(String source, String excerpt) {
    }
}
