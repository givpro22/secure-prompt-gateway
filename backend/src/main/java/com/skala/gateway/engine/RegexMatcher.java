package com.skala.gateway.engine;

import com.skala.gateway.domain.PolicyRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * REGEX 규칙 실행 (기획서 7.2, 7.4-4).
 *
 * <p><b>패턴은 DB에서 읽는다.</b> Java 상수로 하드코딩하면 {@code policy_rule} 테이블의 존재
 * 이유가 사라지고 "정책·규칙·임계값은 DB"라는 Config Isolation 주장(11.3)이 무너진다.
 *
 * <p>매칭마다 finding을 만든다 (7.4-4). BLOCK을 만나도 조기 종료하지 않는 이유는 감사 기록의
 * 목적이 "무엇이 걸렸는지 전부 남기는 것"이기 때문이다 — 일부만 남으면 사후 소명이 불가능해진다.
 */
@Component
public class RegexMatcher {

    /**
     * {@code rule_id → 컴파일된 패턴}. 요청마다 {@link Pattern#compile}을 부르는 것은 낭비다.
     *
     * <p>패턴 문자열을 함께 들고 있다가 DB 값이 바뀌면 다시 컴파일한다. 3일 범위에서 정교한
     * 캐시 무효화 전략은 필요 없지만, 정책을 고친 뒤 재기동해야 반영되는 캐시는 디버깅을 어렵게 한다.
     */
    private final Map<Long, Compiled> cache = new ConcurrentHashMap<>();

    private record Compiled(String source, Pattern pattern) {
    }

    /**
     * 규칙 하나를 원문 전체에 실행한다.
     *
     * @return 매칭 목록. 매칭이 없으면 빈 목록
     */
    public List<RuleHit> match(String text, PolicyRule rule) {
        Matcher matcher = compiled(rule).matcher(text);
        List<RuleHit> hits = new ArrayList<>();
        while (matcher.find()) {
            // 길이 0 매칭은 finding으로 만들지 않는다. 마스킹 대상이 없고 화면에 표시할 구간도 없다.
            if (matcher.end() > matcher.start()) {
                hits.add(RuleHit.regex(rule, matcher.start(), matcher.end()));
            }
        }
        return hits;
    }

    private Pattern compiled(PolicyRule rule) {
        Compiled cached = cache.compute(rule.getRuleId(), (id, current) -> {
            if (current != null && current.source().equals(rule.getPattern())) {
                return current;
            }
            return new Compiled(rule.getPattern(), compile(rule));
        });
        return cached.pattern();
    }

    private static Pattern compile(PolicyRule rule) {
        try {
            return Pattern.compile(rule.getPattern());
        } catch (PatternSyntaxException e) {
            // 패턴을 임의로 고치지 않는다 (기획서 7.2는 발표 자료에 그대로 실린다).
            // 시드 왕복에서 백슬래시가 유실됐을 가능성이 가장 크므로 코드를 밝혀 원인을 좁힌다.
            throw new IllegalStateException(
                    "규칙 " + rule.getCode() + "의 정규식을 컴파일할 수 없습니다. "
                            + "policy_rule.pattern이 기획서 7.2와 문자 단위로 같은지 확인하십시오.", e);
        }
    }
}
