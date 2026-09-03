package com.skala.gateway.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuoteOverlapDetectorTest {

    private static final int THRESHOLD = 40;

    private final QuoteOverlapDetector detector = new QuoteOverlapDetector();

    private static final String DOC =
            "2026년 2분기 목표 전환율은 3.8%이며 이는 전년 동기 대비 0.6%p 상승한 수치다. "
                    + "프로젝트 오로라의 3차 마일스톤은 결제 모듈 리팩터링이다.";

    @Test
    @DisplayName("원문을 그대로 옮기면 임계를 넘어 인용으로 확정된다 — LLM 없이")
    void verbatimQuoteIsDeterministic() {
        QuoteOverlapDetector.Overlap overlap = detector.detect(
                "2026년 2분기 목표 전환율은 3.8%이며 이는 전년 동기 대비 0.6%p 상승한 수치다.",
                DOC, THRESHOLD);

        assertThat(overlap.quote()).isTrue();
        assertThat(overlap.length()).isGreaterThanOrEqualTo(THRESHOLD);
        assertThat(DOC).contains(overlap.fragment());
    }

    @Test
    @DisplayName("일반 상식 문장은 임계에 못 미쳐 회색지대로 남는다 — 여기서만 LLM을 부른다")
    void commonSentenceFallsBelowThreshold() {
        QuoteOverlapDetector.Overlap overlap = detector.detect(
                "전환율을 높이려면 랜딩 페이지 로딩 속도를 줄이는 게 일반적입니다.", DOC, THRESHOLD);

        assertThat(overlap.quote()).isFalse();
    }

    @Test
    @DisplayName("fragment는 실제로 두 텍스트 모두에 들어 있다")
    void fragmentIsASubstringOfBoth() {
        String a = "오로라 3차 마일스톤은 결제 모듈 리팩터링입니다.";

        QuoteOverlapDetector.Overlap overlap = detector.detect(a, DOC, THRESHOLD);

        assertThat(a).contains(overlap.fragment());
        assertThat(DOC).contains(overlap.fragment());
    }

    @Test
    @DisplayName("빈 입력은 겹침 0")
    void emptyInput() {
        assertThat(detector.detect(null, DOC, THRESHOLD).length()).isZero();
        assertThat(detector.detect("", DOC, THRESHOLD).length()).isZero();
    }
}
