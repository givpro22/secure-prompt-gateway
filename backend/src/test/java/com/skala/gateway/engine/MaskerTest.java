package com.skala.gateway.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.gateway.domain.enums.Severity;
import com.skala.gateway.engine.Masker.MaskTarget;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 마스킹 치환 (기획서 7.6). 엔티티·DB 없이 도는 순수 단위 테스트다.
 */
class MaskerTest {

    private final Masker masker = new Masker();

    @Test
    @DisplayName("치환은 뒤에서 앞으로 — 앞에서부터 하면 뒤 매칭의 오프셋이 전부 밀린다")
    void replacesFromBackToFront() {
        String text = "주민번호 900101-1234567 연락처 010-1234-5678";
        List<MaskTarget> targets = List.of(
                new MaskTarget(5, 19, "[주민번호]", Severity.HIGH, "PII-RRN-01"),
                new MaskTarget(24, 37, "[전화번호]", Severity.MEDIUM, "PII-PHONE-03"));

        assertThat(masker.applyTargets(text, targets))
                .isEqualTo("주민번호 [주민번호] 연락처 [전화번호]");
    }

    @Test
    @DisplayName("대상이 없으면 원문 그대로")
    void returnsOriginalWhenNoTarget() {
        assertThat(masker.applyTargets("평범한 업무 프롬프트", List.of()))
                .isEqualTo("평범한 업무 프롬프트");
    }

    @Test
    @DisplayName("부분 겹침은 하나로 합쳐 severity 높은 규칙의 라벨을 쓴다")
    void mergesPartialOverlapWithHigherSeverityLabel() {
        String text = "0123456789";
        List<MaskTarget> targets = List.of(
                new MaskTarget(0, 6, "[낮음]", Severity.MEDIUM, "B-RULE"),
                new MaskTarget(4, 10, "[높음]", Severity.HIGH, "A-RULE"));

        // 합치지 않고 각각 치환하면 앞선 치환이 뒤 구간의 오프셋을 무너뜨려 문자열이 깨진다.
        assertThat(masker.applyTargets(text, targets)).isEqualTo("[높음]");
    }

    @Test
    @DisplayName("severity가 같으면 rule code 사전순으로 라벨을 정한다")
    void breaksSeverityTieByRuleCode() {
        String text = "0123456789";
        List<MaskTarget> targets = List.of(
                new MaskTarget(2, 6, "[비]", Severity.MEDIUM, "B-RULE"),
                new MaskTarget(4, 8, "[에이]", Severity.MEDIUM, "A-RULE"));

        assertThat(masker.applyTargets(text, targets)).isEqualTo("01[에이]89");
    }
}
