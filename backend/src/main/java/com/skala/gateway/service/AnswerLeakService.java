package com.skala.gateway.service;

import com.skala.gateway.ai.AiAssessment;
import com.skala.gateway.ai.AnswerLeakInspector;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 답변 유출 검사기들을 순서대로 돌리고 제안을 합친다 (UC-08 후단).
 *
 * <p>규칙 검사는 항상 있고, LLM 검사는 프로파일에 따라 있거나 없다. 어느 하나라도
 * 의심을 내면 검토 대기로 넘어가 보안 담당자가 본다.
 */
@Service
public class AnswerLeakService {

    private final List<AnswerLeakInspector> inspectors;

    public AnswerLeakService(List<AnswerLeakInspector> inspectors) {
        this.inspectors = inspectors;
    }

    public AiAssessment check(String original, String masked, String answer, String departmentCode) {
        List<AiAssessment.RiskCandidate> candidates = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (AnswerLeakInspector inspector : inspectors) {
            AiAssessment a = inspector.check(original, masked, answer, departmentCode);
            if (a == null) {
                continue;
            }
            if (a.riskCandidates() != null) {
                candidates.addAll(a.riskCandidates());
            }
            if (a.missingContext() != null) {
                missing.addAll(a.missingContext());
            }
        }
        return new AiAssessment(candidates, missing, !candidates.isEmpty());
    }
}
