package com.skala.gateway.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.skala.gateway.config.AiProperties;

/**
 * 모델을 부르지 않고 검사한다. 확인 대상은 모델의 판단력이 아니라 <b>이 클래스의 배선</b>이다 —
 * 문장 분할, 사전 필터, 배치 나눔, 누락 재요청, 후보 조립.
 */
class LlmAiInspectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final AiProperties PROPS = new AiProperties(
            "llm", "http://localhost:11434/api/chat", "", "qwen2.5:7b-instruct",
            0, 800, 60000, 4000, 2,
            new AiProperties.Mock(0, "__FAIL__"));

    private static final List<KeywordHit> HITS =
            List.of(new KeywordHit("A사", "CONF-CLIENT-01", "고객사 NDA 목록 v3"));

    @Test
    @DisplayName("CONFIDENTIAL로 표시된 문장만 후보가 되고, 근거 문장이 evidence에 담긴다")
    void flaggedSentenceBecomesCandidate() {
        StubClient client = new StubClient(List.of("NONE", "CONFIDENTIAL"));

        AiAssessment result = inspector(client).inspect(request(
                "회의실 예약해줘. 우리 마지노선이 42억인데 45억까지 받아낼 수 있어."));

        assertThat(result.riskCandidates()).hasSize(1);
        AiAssessment.RiskCandidate candidate = result.riskCandidates().get(0);
        assertThat(candidate.code()).isEqualTo("AI-CONTEXT");
        assertThat(candidate.category()).isEqualTo("CONFIDENTIAL");
        assertThat(candidate.evidence().get(0).excerpt())
                .isEqualTo("우리 마지노선이 42억인데 45억까지 받아낼 수 있어.");
        assertThat(result.reviewRequired()).isTrue();
    }

    @Test
    @DisplayName("후보가 없으면 빈 배열이고 reviewRequired는 false다 — 결정 필드는 어디에도 없다")
    void noCandidate() {
        AiAssessment result = inspector(new StubClient(List.of("NONE", "NONE")))
                .inspect(request("회의실 예약해줘. 분기 마감이 다음주야."));

        assertThat(result.riskCandidates()).isEmpty();
        assertThat(result.reviewRequired()).isFalse();
    }

    @Test
    @DisplayName("배치 응답에서 빠진 번호는 개별 재요청한다 — 배치 11을 유지하는 전제다")
    void missingIndexIsRetried() {
        // batchSize=2. 첫 배치가 1번만 반환하면 2번은 1건짜리 배치로 다시 묻는다.
        StubClient client = new StubClient();
        client.enqueue(Map.of(1, "NONE"));                 // 2번 누락
        client.enqueue(Map.of(1, "CONFIDENTIAL"));         // 재요청 응답

        AiAssessment result = inspector(client).inspect(request(
                "회의실 예약해줘. 우리 마지노선이 42억인데 45억까지 받아낼 수 있어."));

        assertThat(client.calls).isEqualTo(2);
        assertThat(result.riskCandidates()).hasSize(1);
    }

    @Test
    @DisplayName("재요청에서도 빠지면 NONE으로 둔다 — 이 건은 이미 사람 검토 큐에 있다")
    void stillMissingFallsBackToNone() {
        StubClient client = new StubClient();
        client.enqueue(Map.of());
        client.enqueue(Map.of());
        client.enqueue(Map.of());

        AiAssessment result = inspector(client).inspect(request("우리 마지노선이 42억이야."));

        assertThat(result.riskCandidates()).isEmpty();
    }

    @Test
    @DisplayName("규칙이 이미 본 문장만 남으면 모델을 부르지 않는다")
    void filteredOutInputSkipsTheCall() {
        StubClient client = new StubClient();

        AiAssessment result = inspector(client).inspect(request("주민번호 900101-1234567 조회해줘"));

        assertThat(client.calls).isZero();
        assertThat(result.riskCandidates()).isEmpty();
    }

    @Test
    @DisplayName("hits 없이 호출되면 계약 위반이다 — 규칙 엔진 버그를 조용히 넘기지 않는다")
    void emptyHitsIsAContractViolation() {
        LlmAiInspector inspector = inspector(new StubClient());

        assertThatThrownBy(() -> inspector.inspect(
                new AiInspectionRequest("아무 텍스트", "SALES", List.of(), List.of(), "P-CONF:1")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AiInspectionRequest request(String maskedText) {
        return new AiInspectionRequest(maskedText, "SALES", List.of("CONFIDENTIAL"), HITS, "P-CONF:1");
    }

    private static LlmAiInspector inspector(StubClient client) {
        return new LlmAiInspector(PROPS, new PromptAssembler(PROPS), new SentenceSplitter(),
                new SentenceFilter(coverage()), client);
    }

    /** 실제 호출 없이 응답만 흉내 내는 클라이언트. */
    private static class StubClient extends OllamaChatClient {
        private final List<Map<Integer, String>> responses = new ArrayList<>();
        private int calls;

        StubClient() {
            super(PROPS, MAPPER);
        }

        /** 배치 하나에 대해 1번부터 순서대로 라벨을 돌려준다. */
        StubClient(List<String> labels) {
            this();
            Map<Integer, String> byIndex = new java.util.LinkedHashMap<>();
            for (int i = 0; i < labels.size(); i++) {
                byIndex.put(i + 1, labels.get(i));
            }
            enqueue(byIndex);
        }

        void enqueue(Map<Integer, String> response) {
            responses.add(response);
        }

        @Override
        public JsonNode chat(String systemPrompt, String userMessage, Map<String, Object> schema) {
            Map<Integer, String> response = calls < responses.size() ? responses.get(calls) : Map.of();
            calls++;
            List<Map<String, Object>> items = response.entrySet().stream()
                    .map(e -> Map.<String, Object>of("index", e.getKey(), "label", e.getValue()))
                    .toList();
            return MAPPER.valueToTree(Map.of("items", items));
        }
    }

    private static ObjectProvider<RuleCoverageSource> coverage() {
        RuleCoverageSource source = new RuleCoverageSource() {
            @Override
            public List<String> maskLabels() {
                return List.of("[주민번호]", "[전화번호]");
            }

            @Override
            public List<String> regexPatterns() {
                return List.of("\\d{6}-?[1-4]\\d{6}");
            }
        };
        return new ObjectProvider<>() {
            @Override
            public RuleCoverageSource getIfAvailable() {
                return source;
            }

            @Override
            public RuleCoverageSource getObject() {
                return source;
            }

            @Override
            public RuleCoverageSource getObject(Object... args) {
                return source;
            }

            @Override
            public RuleCoverageSource getIfUnique() {
                return source;
            }
        };
    }
}
