package com.skala.gateway.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.skala.gateway.config.AiProperties;

/**
 * 실제 모델을 호출하는 구현체의 골격 (기획서 9.1, 9.6).
 *
 * <p><b>골격만 있는 것은 미완성이 아니라 범위 결정이다</b> (기획서 0.4). 이번 범위에서 증명할 것은
 * "교체가 가능한 구조인가"이지 "모델이 잘 판단하는가"가 아니다. 교체 절차(9.6)를 문서로 남기고
 * 프로파일과 설정 키를 실제로 갖춰 두는 것으로 충분하다.
 *
 * <p>교체 시 이 클래스가 할 일은 셋뿐이다.
 * <ol>
 *   <li>{@link PromptAssembler}로 9.2 시스템 프롬프트 + 9.3 사용자 메시지 조립 — <b>이미 구현되어 있다</b></li>
 *   <li>{@code ai.endpoint}로 HTTP 호출 (temperature·max-tokens는 {@link AiProperties}에서)</li>
 *   <li>응답을 {@link AiAssessment}(9.4 스키마)로 역직렬화·검증. 실패하면 예외를 던져
 *       {@code ai_status=FAILED} → 사람 검토 폴백으로 떨어뜨린다</li>
 * </ol>
 *
 * <p>{@code ai.endpoint}가 사내 주소를 가리키면 외부 API를 호출하지 않으므로 "검사하려고 원문을
 * 밖으로 보내는" 문제가 생기지 않는다 (교수 피드백 F3). 어느 쪽이든 이 클래스가 받는 것은
 * {@link AiInspectionRequest#maskedText()}뿐이고 원문에 접근할 경로가 없다.
 */
@Component
@Profile("llm")
public class LlmAiInspector implements AiInspector {

    private static final Logger log = LoggerFactory.getLogger(LlmAiInspector.class);

    private final AiProperties properties;
    private final PromptAssembler promptAssembler;

    public LlmAiInspector(AiProperties properties, PromptAssembler promptAssembler) {
        this.properties = properties;
        this.promptAssembler = promptAssembler;
        log.info("LlmAiInspector 활성화 — endpoint={}, model={}, temperature={}, maxTokens={}",
                properties.endpoint(), properties.model(), properties.temperature(), properties.maxTokens());
    }

    @Override
    public AiAssessment inspect(AiInspectionRequest request) {
        if (request == null || request.hits() == null || request.hits().isEmpty()) {
            throw new IllegalStateException(
                    "AiInspector가 hits 없이 호출되었습니다. 규칙 엔진이 REVIEW 판정 없이 AI를 호출했는지 확인하십시오.");
        }

        String systemPrompt = PromptAssembler.SYSTEM_PROMPT;
        String userMessage = promptAssembler.assembleUserMessage(request);
        boolean truncated = promptAssembler.isTruncated(request.maskedText());

        log.debug("LLM 요청 조립 완료 — systemPrompt {}자, userMessage {}자, truncated={}",
                systemPrompt.length(), userMessage.length(), truncated);

        // 남은 것은 HTTP 호출과 9.4 스키마 검증뿐이다. 이번 범위 밖 (기획서 0.4).
        throw new UnsupportedOperationException(
                "LlmAiInspector는 골격만 구현되어 있습니다. 교체 절차는 기획서 9.6을 참조하십시오. "
                        + "endpoint=" + properties.endpoint());
    }
}
