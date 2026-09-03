package com.skala.gateway.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 결정적 유출 검사 — <b>가렸던 값이 답변에 되살아났는가.</b>
 *
 * <p>원문과 마스킹본을 나란히 놓으면 무엇을 가렸는지 나온다. 그 값이 답변에 그대로
 * 있으면 모델이 어딘가에서 그것을 알아낸 것이다 — 이전 대화, 학습 데이터, 혹은
 * 라벨을 보고 추측. 어느 쪽이든 게이트웨이가 가린 의미가 사라졌으므로 사람이 봐야 한다.
 *
 * <p>모델 없이 돈다. 서버(mock 프로파일)에서도 이 검사는 살아 있다.
 */
@Component
public class RuleLeakInspector implements AnswerLeakInspector {

    static final String CODE = "LEAK-RECONSTRUCT";
    /** 두 글자짜리는 우연히 겹친다. 세 글자부터 값으로 본다 */
    private static final int MIN_LEN = 3;

    @Override
    public AiAssessment check(String original, String masked, String answer, String departmentCode) {
        if (original == null || masked == null || answer == null || original.equals(masked)) {
            return new AiAssessment(List.of(), List.of(), false);
        }
        List<AiAssessment.RiskCandidate> found = new ArrayList<>();
        for (String hidden : hiddenValues(original, masked)) {
            if (hidden.length() >= MIN_LEN && answer.contains(hidden)) {
                found.add(new AiAssessment.RiskCandidate(
                        CODE,
                        "CONFIDENTIAL",
                        "프롬프트에서 가렸던 값이 답변에 그대로 나타났습니다. 모델이 가려진 값을 알아낸 것이므로 마스킹이 무력화됐습니다.",
                        List.of(new AiAssessment.Evidence("answer", hidden))));
            }
        }
        return new AiAssessment(found, List.of(), !found.isEmpty());
    }

    /**
     * 원문에서 마스킹본으로 갈 때 사라진 조각들. 라벨 {@code [...]}이 들어간 자리마다
     * 원문 쪽의 대응 구간을 잘라낸다. 앞뒤 공통 접두·접미로 정렬한다.
     */
    static Set<String> hiddenValues(String original, String masked) {
        Set<String> out = new LinkedHashSet<>();
        int oi = 0;
        int mi = 0;
        while (mi < masked.length()) {
            if (masked.charAt(mi) == '[') {
                int close = masked.indexOf(']', mi);
                if (close < 0) {
                    break;
                }
                // 라벨 뒤에 오는 문자열이 원문에서 다음으로 나타나는 자리까지가 가려진 값이다.
                String after = masked.substring(close + 1, Math.min(masked.length(), close + 6));
                int end = after.isEmpty() ? original.length() : original.indexOf(after, oi);
                if (end < 0) {
                    end = original.length();
                }
                String hidden = original.substring(oi, end).trim();
                if (!hidden.isEmpty()) {
                    out.add(hidden);
                }
                oi = end;
                mi = close + 1;
            } else {
                oi++;
                mi++;
            }
        }
        return out;
    }
}
