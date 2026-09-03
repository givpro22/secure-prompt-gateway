package com.skala.gateway.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import com.skala.gateway.config.AiProperties;

/**
 * 로컬 LLM(Ollama)을 호출하는 구현체 (기획서 9.1, 9.6).
 *
 * <p>흐름은 넷이다.
 * <ol>
 *   <li>{@link SentenceSplitter}가 마스킹본을 문장으로 자른다</li>
 *   <li>{@link SentenceFilter}가 규칙 엔진이 이미 본 문장을 뺀다</li>
 *   <li>남은 문장을 번호 붙여 배치로 보내고 {@code {index, label}}만 받는다</li>
 *   <li>{@code CONFIDENTIAL}로 표시된 문장을 후보로 만든다</li>
 * </ol>
 *
 * <p><b>모델에게 구간을 뽑게 하지 않는다.</b> 번호와 라벨만 받고 근거 문장은 우리가 가진 분할
 * 결과에서 꺼낸다. 모델이 인용을 다시 쓰면 원문과 글자가 달라져 위치를 잃는다 (_workspace/05 §1-2).
 *
 * <p><b>span을 채우지 않는다.</b> 규칙 finding의 span은 원문 기준인데(0.5 D3) 이 클래스가 보는
 * 것은 마스킹본이라 좌표계가 다르다. 같은 컬럼에 섞으면 하이라이트가 조용히 밀린다. 근거 문장은
 * {@code evidence.excerpt}로 넘기고 화면은 D3와 같은 방식(문자열 검색)으로 처리한다.
 *
 * <p>홀드아웃 111문장 F1 0.892 · 오탐 1.5% (`_workspace/golden/`). 개발셋 326문장 F1 0.972.
 */
@Component
@Profile("llm")
public class LlmAiInspector implements AiInspector {

    private static final Logger log = LoggerFactory.getLogger(LlmAiInspector.class);

    /** AI 후보 코드. 9.4 스키마의 {@code ^[A-Z]+-[A-Z-]+$}를 지킨다. */
    static final String CANDIDATE_CODE = "AI-CONTEXT";

    /** 9.4 스키마가 AI 후보에 허용하는 유일한 카테고리. PII·SECRET은 규칙 엔진의 몫이다. */
    private static final String CANDIDATE_CATEGORY = "CONFIDENTIAL";

    /** 근거 문장을 담을 때 쓰는 출처 이름. */
    static final String EVIDENCE_SOURCE_SENTENCE = "입력 문장";

    private static final String LABEL_CONFIDENTIAL = "CONFIDENTIAL";
    private static final String LABEL_NONE = "NONE";

    /**
     * 응답 스키마. {@code index}는 우리가 붙인 번호를 그대로 돌려받는 자리이고 {@code label}은
     * 두 값만 허용한다. Ollama가 이 스키마를 강제하므로 라벨 오타가 나올 수 없다.
     */
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("items", Map.of(
                    "type", "array",
                    "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "index", Map.of("type", "integer"),
                                    "label", Map.of("type", "string",
                                            "enum", List.of(LABEL_CONFIDENTIAL, LABEL_NONE))),
                            "required", List.of("index", "label")))),
            "required", List.of("items"));

    private final AiProperties properties;
    private final PromptAssembler promptAssembler;
    private final SentenceSplitter splitter;
    private final SentenceFilter filter;
    private final OllamaChatClient client;

    public LlmAiInspector(AiProperties properties, PromptAssembler promptAssembler,
                          SentenceSplitter splitter, SentenceFilter filter, OllamaChatClient client) {
        this.properties = properties;
        this.promptAssembler = promptAssembler;
        this.splitter = splitter;
        this.filter = filter;
        this.client = client;
        log.info("LlmAiInspector 활성화 — endpoint={}, model={}, batchSize={}, timeoutMs={}",
                properties.endpoint(), properties.model(), properties.batchSize(), properties.timeoutMs());
    }

    @Override
    public AiAssessment inspect(AiInspectionRequest request) {
        if (request == null || request.hits() == null || request.hits().isEmpty()) {
            throw new IllegalStateException(
                    "AiInspector가 hits 없이 호출되었습니다. 규칙 엔진이 REVIEW 판정 없이 AI를 호출했는지 확인하십시오.");
        }

        String text = promptAssembler.truncate(request.maskedText());
        List<String> missingContext = new ArrayList<>();
        if (promptAssembler.isTruncated(request.maskedText())) {
            missingContext.add(PromptAssembler.TRUNCATION_NOTICE);
        }

        List<SentenceSplitter.Sentence> candidates = filter.retain(splitter.split(text));
        if (candidates.isEmpty()) {
            // 규칙 엔진이 본 것만 남은 입력이다. 후보 없음이 정상이며 실패가 아니다.
            log.debug("사전 필터 후 남은 문장이 없어 LLM을 호출하지 않았습니다.");
            return new AiAssessment(List.of(), missingContext, false);
        }

        Set<Integer> flagged = classify(candidates, request);

        List<AiAssessment.RiskCandidate> riskCandidates = new ArrayList<>();
        for (Integer i : flagged) {
            riskCandidates.add(new AiAssessment.RiskCandidate(
                    CANDIDATE_CODE,
                    CANDIDATE_CATEGORY,
                    "규칙에 걸리지 않았으나 미공개 사내 정보로 읽히는 서술입니다. 해당 문장: "
                            + candidates.get(i).text(),
                    evidenceOf(candidates.get(i), request)));
        }

        log.debug("LLM 검사 완료 — 문장 {}건 중 후보 {}건", candidates.size(), riskCandidates.size());
        return new AiAssessment(riskCandidates, missingContext, !riskCandidates.isEmpty());
    }

    /**
     * 문장을 배치로 나눠 판정한다. 반환 배열에서 빠진 번호는 1건짜리 배치로 다시 묻는다.
     *
     * <p><b>누락 재요청이 필요한 이유.</b> 배치를 11문장으로 하면 판정이 가장 정확한데 가끔
     * 배열이 입력보다 짧게 돌아온다. 배치를 6으로 줄이면 누락은 사라지지만 문장 간 맥락이 끊겨
     * 정확도가 40/42에서 38/42로 떨어졌다. 큰 배치를 유지하고 빠진 것만 다시 묻는 편이 낫다
     * (_workspace/05 §2).
     *
     * @return {@code candidates}에서 {@code CONFIDENTIAL}로 판정된 인덱스
     */
    private Set<Integer> classify(List<SentenceSplitter.Sentence> candidates, AiInspectionRequest request) {
        Set<Integer> flagged = new LinkedHashSet<>();
        int batchSize = Math.max(1, properties.batchSize());

        for (int from = 0; from < candidates.size(); from += batchSize) {
            int to = Math.min(from + batchSize, candidates.size());
            List<SentenceSplitter.Sentence> batch = candidates.subList(from, to);

            Map<Integer, String> labels = askBatch(batch, request);
            for (int i = 0; i < batch.size(); i++) {
                String label = labels.get(i + 1);
                if (label == null) {
                    label = askSingle(batch.get(i), request);
                }
                if (LABEL_CONFIDENTIAL.equals(label)) {
                    flagged.add(from + i);
                }
            }
        }
        return flagged;
    }

    private Map<Integer, String> askBatch(List<SentenceSplitter.Sentence> batch, AiInspectionRequest request) {
        JsonNode root = client.chat(
                PromptAssembler.SENTENCE_SYSTEM_PROMPT,
                promptAssembler.assembleSentenceMessage(request, batch),
                RESPONSE_SCHEMA);

        Map<Integer, String> labels = new LinkedHashMap<>();
        for (JsonNode item : root.path("items")) {
            if (item.hasNonNull("index") && item.hasNonNull("label")) {
                labels.put(item.get("index").asInt(), item.get("label").asText());
            }
        }
        if (labels.size() < batch.size()) {
            log.debug("배치 응답이 짧습니다 — 요청 {}건, 반환 {}건. 빠진 문장은 개별 재요청합니다.",
                    batch.size(), labels.size());
        }
        return labels;
    }

    /**
     * 배치에서 빠진 문장 하나를 다시 묻는다. 여기서도 빠지면 {@code NONE}으로 둔다 — 이 건은
     * 이미 규칙 엔진이 REVIEW로 보낸 상태이므로 사람 검토는 어차피 이뤄진다.
     */
    private String askSingle(SentenceSplitter.Sentence sentence, AiInspectionRequest request) {
        Map<Integer, String> labels = askBatch(List.of(sentence), request);
        return labels.getOrDefault(1, LABEL_NONE);
    }

    /** 근거 문장을 첫 항목으로, KEYWORD 매칭 근거를 뒤에 붙인다 (기획서 9.1). */
    private static List<AiAssessment.Evidence> evidenceOf(SentenceSplitter.Sentence sentence,
                                                          AiInspectionRequest request) {
        List<AiAssessment.Evidence> evidence = new ArrayList<>();
        evidence.add(new AiAssessment.Evidence(EVIDENCE_SOURCE_SENTENCE, sentence.text()));
        for (KeywordHit hit : request.hits()) {
            evidence.add(new AiAssessment.Evidence(hit.source(), hit.keyword()));
        }
        return evidence;
    }
}
