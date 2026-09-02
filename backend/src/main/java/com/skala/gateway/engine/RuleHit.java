package com.skala.gateway.engine;

import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.enums.PolicyCategory;
import com.skala.gateway.domain.enums.RuleAction;
import com.skala.gateway.domain.enums.RuleType;
import com.skala.gateway.domain.enums.Severity;
import java.time.LocalDate;
import java.util.List;

/**
 * 규칙 하나가 원문에서 잡아낸 자리 (기획서 7.4-4, 7.4-5).
 *
 * <p>{@code span}은 <b>원문(original_text) 기준</b>이다. 마스킹은 길이를 바꾸므로 마스킹본
 * 기준 좌표와 섞으면 하이라이트가 조용히 밀린다 (0.5 D3). 두 좌표계를 한 타입에 담지 않는다.
 *
 * <p>REGEX는 매칭마다 1건이고 KEYWORD는 <b>규칙당 1건</b>이다 (0.5 D9). KEYWORD의
 * {@code matchedKeyword}는 가장 앞선 오프셋의 키워드이며, 매칭된 키워드 전부는
 * {@code matchedKeywords}에 남아 {@code AiInspectionRequest.hits}가 된다.
 *
 * @param rule            매칭시킨 규칙. finding INSERT 때 {@code rule_id}로 들어간다
 * @param spanStart       원문 기준 시작 오프셋 (포함)
 * @param spanEnd         원문 기준 끝 오프셋 (미포함)
 * @param matchedKeyword  KEYWORD 규칙만 값을 갖는다. REGEX는 {@code null} —
 *                        매칭 문자열을 넣으면 주민번호 원문이 {@code rule_result} JSONB에 남는다
 * @param matchedKeywords 이 규칙이 잡은 키워드 전체. REGEX는 빈 목록
 */
public record RuleHit(
        PolicyRule rule,
        int spanStart,
        int spanEnd,
        String matchedKeyword,
        List<String> matchedKeywords) {

    public static RuleHit regex(PolicyRule rule, int spanStart, int spanEnd) {
        return new RuleHit(rule, spanStart, spanEnd, null, List.of());
    }

    public static RuleHit keyword(PolicyRule rule, int spanStart, int spanEnd,
                                  String matchedKeyword, List<String> matchedKeywords) {
        return new RuleHit(rule, spanStart, spanEnd, matchedKeyword, List.copyOf(matchedKeywords));
    }

    public String code() {
        return rule.getCode();
    }

    public RuleAction action() {
        return rule.getAction();
    }

    public RuleType ruleType() {
        return rule.getRuleType();
    }

    public Severity severity() {
        return rule.getSeverity();
    }

    public PolicyCategory category() {
        return rule.getPolicy().getCategory();
    }

    /**
     * 엠바고 해제일. 기한 없는 규칙은 {@code null}이다.
     *
     * <p>여기까지 온 매칭은 해제일이 지나지 않은 것뿐이다 — 만료된 규칙은
     * {@link RuleEngine}이 매칭 전에 걸러낸다.
     */
    public LocalDate embargoUntil() {
        return rule.getEmbargoUntil();
    }

    /**
     * 다른 매칭이 이 매칭의 span에 <b>완전히</b> 포함되는가 (0.5 D1).
     *
     * <p>부분 겹침은 포함이 아니다. 부분 겹침에서 두 규칙이 만나면 억제하지 않고 7.6의
     * severity 규칙으로 라벨만 정한다.
     */
    public boolean contains(RuleHit other) {
        return this.spanStart <= other.spanStart && other.spanEnd <= this.spanEnd;
    }
}
