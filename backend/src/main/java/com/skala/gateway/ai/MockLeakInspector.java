package com.skala.gateway.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 문맥 유출 검사의 Mock. {@code mock} 프로파일에서 {@link LlmLeakInspector} 자리를 채운다.
 *
 * <p>기획서 9장의 {@code MockAiInspector}와 같은 원칙이다 — 진짜 모델이 들어올 자리를
 * 비워 두되, 그 자리가 어떤 모양의 제안을 내는지는 지금 보여준다. 사내 모델을 배포에
 * 담을 수 없어서(저장소·EC2 제약) 서버에서는 이 구현이 뜬다.
 *
 * <p>판단은 단순한 표지어다. 답변이 "사내", "내부", "미공개", "대외비", "NDA" 같은 말과
 * 함께 구체 정보를 담고 있으면 사내 정보가 섞였을 수 있다고 제안한다. 규칙 유출
 * 검사({@link RuleLeakInspector})가 못 보는 종류 — 가리지 않았지만 밖에 있으면 안 되는
 * 것 — 를 흉내 낸 것이다. Mock임은 rationale에 밝힌다.
 */
@Component
@Profile("mock")
public class MockLeakInspector implements AnswerLeakInspector {

    static final String CODE = "LEAK-INTERNAL-CONTEXT";
    private static final List<String> MARKERS = List.of("사내", "내부", "미공개", "대외비", "nda", "엠바고", "런칭 일정");

    @Override
    public AiAssessment check(String original, String masked, String answer, String departmentCode) {
        if (answer == null || answer.isBlank()) {
            return new AiAssessment(List.of(), List.of(), false);
        }
        // [내부IP] 같은 마스킹 라벨의 글자는 답변이 아니라 우리가 붙인 것이다. 검사에서 뺀다.
        String lower = answer.replaceAll("\\[[^\\]]*\\]", " ").toLowerCase(Locale.ROOT);
        List<AiAssessment.Evidence> evidence = new ArrayList<>();
        for (String marker : MARKERS) {
            int i = lower.indexOf(marker);
            if (i >= 0) {
                int from = Math.max(0, i - 20);
                int to = Math.min(answer.length(), i + marker.length() + 30);
                evidence.add(new AiAssessment.Evidence("answer", answer.substring(from, to).trim()));
            }
        }
        if (evidence.isEmpty()) {
            return new AiAssessment(List.of(), List.of(), false);
        }
        return new AiAssessment(List.of(new AiAssessment.RiskCandidate(
                CODE, "CONFIDENTIAL",
                "답변이 사내 맥락을 가리키는 표현과 함께 구체 정보를 담고 있습니다. (Mock 검사기 — 사내 모델이 들어오면 문맥 판단으로 대체됩니다)",
                evidence)), List.of("mock-leak-inspector"), true);
    }
}
