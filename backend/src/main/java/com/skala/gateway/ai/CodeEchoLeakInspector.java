package com.skala.gateway.ai;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 코드 원문 유출 의심 — <b>답변이 프롬프트의 본문을 그대로 되돌려주는가.</b>
 *
 * <p>개발자가 오류 난 코드를 붙여 넣고 고쳐 달라고 하면 모델은 고친 코드를 통째로
 * 돌려준다. 그 순간 사내 코드가 외부 모델을 한 바퀴 돌아 나온 것이고, 답변 안에 원문이
 * 상당 부분 그대로 남는다. 개인정보 규칙에는 안 걸린다 — 이름도 번호도 없다. 그래서
 * 규칙 검사와 별도로 "글자 그대로 얼마나 겹치는가"를 본다.
 *
 * <p>판정은 {@link QuoteOverlapDetector}의 최장 공통 부분문자열이다. 순수 문자열
 * 문제라 LLM에 맡기지 않는다(그 클래스 주석 참조). 임계 40자는 탐지규칙 6.2의 기본값 —
 * 코드는 한 줄만 되돌아와도 넘는다.
 *
 * <p>모델 없이 돈다. 서버에서도 살아 있다.
 */
@Component
public class CodeEchoLeakInspector implements AnswerLeakInspector {

    static final String CODE = "LEAK-CODE-ECHO";
    static final int THRESHOLD = 40;

    private final QuoteOverlapDetector detector;

    public CodeEchoLeakInspector(QuoteOverlapDetector detector) {
        this.detector = detector;
    }

    @Override
    public AiAssessment check(String original, String masked, String answer, String departmentCode) {
        if (masked == null || answer == null) {
            return new AiAssessment(List.of(), List.of(), false);
        }
        QuoteOverlapDetector.Overlap o = detector.detect(answer, masked, THRESHOLD);
        if (!o.quote()) {
            return new AiAssessment(List.of(), List.of(), false);
        }
        return new AiAssessment(List.of(new AiAssessment.RiskCandidate(
                CODE, "CONFIDENTIAL",
                "답변이 프롬프트 본문을 " + o.length() + "자 이상 그대로 되돌려줍니다. 사내 코드나 문서가 외부 모델을 거쳐 그대로 나온 것으로, 원문 유출 여부를 담당자가 확인해야 합니다.",
                List.of(new AiAssessment.Evidence("answer", o.fragment())))), List.of(), true);
    }
}
