package com.skala.gateway.engine;

import com.skala.gateway.domain.PolicyRule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * KEYWORD 규칙 실행 (기획서 7.2, 7.4-5).
 *
 * <p>{@code pattern}이 쉼표 구분 문자열이다 ({@code A사,B사,C사,프로젝트 오메가,차세대}).
 * split 후 각 키워드를 {@code indexOf}로 찾는다.
 *
 * <p><b>finding은 규칙당 1건이다</b> (0.5 D9). 한 규칙의 키워드가 여러 개 매칭돼도 항목은
 * 하나이며 가장 앞선 오프셋의 키워드가 {@code matchedKeyword}가 된다. 같은 규칙이 같은 문장에서
 * 여러 번 걸리는 것을 그대로 세면 화면의 "규칙 N건"이 실제 위험 개수를 부풀린다 — D1의 중첩
 * 억제와 같은 논리다. 매칭된 키워드 전부는 {@code matchedKeywords}에 남아
 * {@code AiInspectionRequest.hits}가 된다.
 *
 * <p>대소문자·공백 정규화는 하지 않는다. 한글 키워드라 의미가 없고 데모 문자열이 정확히 일치한다.
 */
@Component
public class KeywordMatcher {

    private static final String DELIMITER = ",";

    /**
     * 규칙 하나를 원문에 실행한다.
     *
     * @return 매칭이 하나라도 있으면 규칙당 1건의 {@link RuleHit}, 없으면 {@link Optional#empty()}
     */
    public Optional<RuleHit> match(String text, PolicyRule rule) {
        List<Found> found = new ArrayList<>();
        for (String raw : rule.getPattern().split(DELIMITER)) {
            String keyword = raw.trim();
            if (keyword.isEmpty()) {
                continue;
            }
            int at = text.indexOf(keyword);
            if (at >= 0) {
                found.add(new Found(keyword, at));
            }
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        // 등장 순서로 고정한다. 같은 오프셋이면 키워드 사전순 — 판정이 결정론적이어야 데모가 재현된다.
        found.sort(Comparator.comparingInt(Found::start).thenComparing(Found::keyword));

        Found first = found.get(0);
        List<String> keywords = found.stream().map(Found::keyword).toList();
        return Optional.of(RuleHit.keyword(
                rule, first.start(), first.start() + first.keyword().length(), first.keyword(), keywords));
    }

    private record Found(String keyword, int start) {
    }
}
