package com.skala.gateway.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.skala.gateway.DemoCases;

/**
 * 사전 필터는 골든셋 오탐 6건 중 5건을 없앤 장치다 (_workspace/05 §1-4).
 * 여기서 깨지면 오탐이 조용히 돌아온다.
 */
class SentenceFilterTest {

    private final SentenceSplitter splitter = new SentenceSplitter();

    /** 시드(V2)의 마스킹 라벨과 REGEX 패턴 일부. */
    private final SentenceFilter filter = new SentenceFilter(provider(new RuleCoverageSource() {
        @Override
        public List<String> maskLabels() {
            return List.of("[주민번호]", "[전화번호]", "[이메일]", "[내부IP]", "[카드번호]");
        }

        @Override
        public List<String> regexPatterns() {
            return List.of("\\d{6}-?[1-4]\\d{6}", "01[016789]-?\\d{3,4}-?\\d{4}",
                    "[\\w.+-]+@[\\w-]+\\.[\\w.]+");
        }
    }));

    @Test
    @DisplayName("마스킹 라벨이 든 문장은 LLM에 보내지 않는다")
    void maskLabelIsExcluded() {
        assertThat(retain("지원자 연락처 [전화번호] 로 안내 문자 써줘")).isEmpty();
    }

    @Test
    @DisplayName("규칙 정규식에 걸리는 문장은 LLM에 보내지 않는다 — 기획서 9.2 금지 조항")
    void regexMatchIsExcluded() {
        assertThat(retain("담당자 주민번호 900101-1234567 기준으로 조회해줘")).isEmpty();
        assertThat(retain("hong@example.com 으로 초대 메일 보내줘")).isEmpty();
    }

    @Test
    @DisplayName("맥락형 기밀 문장은 남는다 — 이것이 LLM이 볼 유일한 종류다")
    void contextualSentenceIsKept() {
        assertThat(retain("우리 마지노선이 42억인데 45억까지 받아낼 수 있을 것 같아")).hasSize(1);
    }

    @Test
    @DisplayName("Case E의 [백로그!2행]은 마스킹 라벨이 아니므로 제외되지 않는다")
    void spreadsheetBracketIsNotAMaskLabel() {
        assertThat(retain(DemoCases.CASE_E)).hasSize(1);
    }

    @Test
    @DisplayName("RuleCoverageSource가 없으면 필터 없이 통과시킨다 — 검사가 멈추지 않는다")
    void missingCoverageSourceDoesNotBreakInspection() {
        SentenceFilter noCoverage = new SentenceFilter(provider(null));

        assertThat(noCoverage.retain(splitter.split("담당자 주민번호 900101-1234567 조회"))).hasSize(1);
    }

    private List<SentenceSplitter.Sentence> retain(String text) {
        return filter.retain(splitter.split(text));
    }

    /** {@link ObjectProvider}에서 이 테스트가 쓰는 것은 {@code getIfAvailable()} 하나다. */
    private static ObjectProvider<RuleCoverageSource> provider(RuleCoverageSource value) {
        return new ObjectProvider<>() {
            @Override
            public RuleCoverageSource getIfAvailable() {
                return value;
            }

            @Override
            public RuleCoverageSource getObject() {
                return value;
            }

            @Override
            public RuleCoverageSource getObject(Object... args) {
                return value;
            }

            @Override
            public RuleCoverageSource getIfUnique() {
                return value;
            }
        };
    }
}
