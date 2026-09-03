package com.skala.gateway.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 사내 Ollama가 답변을 읽고 유출 의심을 제안한다. {@code llm} 프로파일에서만 뜬다.
 *
 * <p>규칙 검사({@link RuleLeakInspector})가 "가린 값이 되살아났는가"를 보는 것과 달리,
 * 여기는 문맥을 본다 — 가리지 않았지만 사내에서만 알 수 있는 일정·제품명·고객사, 혹은
 * 라벨로부터 추측한 구체값 같은 것. 사내망 모델이라 답변이 밖으로 나가지 않는다.
 *
 * <p>원문은 넘기지 않는다. 마스킹본과 답변만 준다 — 검사기가 원문을 알아야 판단할 수
 * 있는 것은 이미 규칙 검사가 봤다.
 */
@Component
@Profile("llm")
public class LlmLeakInspector implements AnswerLeakInspector {

    private static final Logger log = LoggerFactory.getLogger(LlmLeakInspector.class);

    static final String SYSTEM = """
            당신은 사내 AI 게이트웨이의 출력 검사기다. 직원이 외부 모델에 보낸 프롬프트(가려진 부분은
            [고객명], [전화번호] 같은 대괄호 라벨)와 모델이 돌려준 답변을 받는다.
            답변이 다음에 해당하면 riskCandidates에 넣는다:
            - 라벨 자리의 실제 값을 추측하거나 지어냈다
            - 사내에서만 알 수 있는 제품명, 일정, 고객사, 내부 문서 내용을 담고 있다
            - 개인정보나 자격증명으로 보이는 값을 새로 만들어 냈다
            해당 없으면 riskCandidates는 빈 배열, reviewRequired는 false다.
            code는 대문자와 하이픈만 쓴다. category는 항상 CONFIDENTIAL이다. 한국어로 쓴다.
            """;

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "riskCandidates", Map.of("type", "array", "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "code", Map.of("type", "string"),
                                    "category", Map.of("type", "string"),
                                    "rationale", Map.of("type", "string"),
                                    "evidence", Map.of("type", "array", "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "source", Map.of("type", "string"),
                                                    "excerpt", Map.of("type", "string")),
                                            "required", List.of("source", "excerpt")))),
                            "required", List.of("code", "category", "rationale", "evidence"))),
                    "missingContext", Map.of("type", "array", "items", Map.of("type", "string")),
                    "reviewRequired", Map.of("type", "boolean")),
            "required", List.of("riskCandidates", "missingContext", "reviewRequired"));

    private final OllamaChatClient ollama;
    private final ObjectMapper objectMapper;

    public LlmLeakInspector(OllamaChatClient ollama, ObjectMapper objectMapper) {
        this.ollama = ollama;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAssessment check(String original, String masked, String answer, String departmentCode) {
        String user = "부서: " + departmentCode + "\n\n[보낸 프롬프트]\n" + masked + "\n\n[받은 답변]\n" + answer;
        try {
            JsonNode raw = ollama.chat(SYSTEM, user, SCHEMA);
            AiAssessment parsed = objectMapper.treeToValue(raw, AiAssessment.class);
            return parsed == null ? new AiAssessment(List.of(), List.of(), false) : parsed;
        } catch (Exception e) {
            // 검사기가 죽어도 답변은 나가야 한다. 규칙 검사는 이미 돌았고, 실패는 기록으로 남긴다.
            log.warn("LLM 유출 검사 실패: {}", e.getMessage());
            return new AiAssessment(List.of(), List.of("llm-leak-check-failed: " + e.getMessage()), false);
        }
    }
}
