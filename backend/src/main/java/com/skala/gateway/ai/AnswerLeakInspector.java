package com.skala.gateway.ai;

/**
 * 답변 유출 검사 (UC-08 후단). 모델이 돌려준 답변이 원문의 가려진 값을 되살렸거나
 * 사내 정보를 물고 나왔는지 본다.
 *
 * <p>구현이 둘이다. {@link RuleLeakInspector}는 항상 돌고 결정적이다 — 원문에서 가렸던
 * 값이 답변에 다시 나타나면 유출이다. {@link LlmLeakInspector}는 {@code llm} 프로파일에서만
 * 뜨고 사내 Ollama가 문맥을 본다. 둘 다 <b>제안</b>만 한다. 확정은 보안 담당자가 감사
 * 콘솔에서 한다 (기획서 4장 — 규칙은 결정, AI는 제안, 사람은 확정).
 */
public interface AnswerLeakInspector {

    /**
     * @param originalPrompt 직원이 친 원문. 여기서만 쓰이고 어디로도 안 나간다
     * @param maskedPrompt   모델에 실제로 보낸 것
     * @param answer         모델이 돌려준 것
     */
    AiAssessment check(String originalPrompt, String maskedPrompt, String answer, String departmentCode);
}
