package com.skala.gateway.engine;

import com.skala.gateway.ai.KeywordHit;
import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.RuleAction;
import com.skala.gateway.domain.enums.RuleType;
import com.skala.gateway.domain.jsonb.RuleResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 판정 엔진 (기획서 7.4).
 *
 * <p>파이프라인 순서가 곧 설계다. 순서를 바꾸면 결과가 달라진다.
 *
 * <pre>
 * 0. 엠바고 만료   해제일이 지난 규칙 제외          ← 결정 4
 * 1. REGEX 실행    severity 내림차순, 전부 실행 (조기 종료 없음)
 * 2. KEYWORD 실행  매칭 시 규칙당 1건            ← 0.5 D9
 * 3. 중첩 억제     포함 관계 매칭 제거            ← 0.5 D1
 * 4. 충돌 해결     BLOCK &gt; REVIEW &gt; MASK &gt; ALLOW
 * 5. 마스킹        판정이 BLOCK이 아닐 때만 실행  ← 0.5 D5
 * </pre>
 *
 * <p>정책 로드(7.4-1~3)와 저장·응답(7.4-9)은 {@code service} 계층이 맡는다. 이 클래스는
 * 순수 함수에 가깝다 — 같은 입력·같은 규칙 목록이면 항상 같은 결과가 나온다. 데모가 이 성질에
 * 의존하므로 랜덤·시각·해시 순서에 의존하는 요소를 넣지 않는다.
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final RegexMatcher regexMatcher;
    private final KeywordMatcher keywordMatcher;
    private final ConflictResolver conflictResolver;
    private final Masker masker;
    private final Clock clock;

    public RuleEngine(RegexMatcher regexMatcher, KeywordMatcher keywordMatcher,
                      ConflictResolver conflictResolver, Masker masker, Clock clock) {
        this.regexMatcher = regexMatcher;
        this.keywordMatcher = keywordMatcher;
        this.conflictResolver = conflictResolver;
        this.masker = masker;
        this.clock = clock;
    }

    /**
     * @param originalText 제출된 원문
     * @param rules        {@code PolicyRuleRepository.findActiveByDept}가 돌려준 활성 규칙.
     *                     반환 순서가 곧 7.4의 실행 순서다 (REGEX severity 내림차순 → KEYWORD)
     */
    public EngineVerdict evaluate(String originalText, List<PolicyRule> rules) {
        // 0. 엠바고 만료 (결정 4). 해제일이 지난 규칙은 매칭시키지 않는다.
        //    appliedRuleCodes에는 그대로 남긴다 — 로드된 규칙과 매칭된 규칙은 다르며(8.4),
        //    "적용은 됐는데 이미 풀린 규칙"이 감사 기록에 보이는 편이 사후 소명에 낫다.
        // ROSTER 규칙은 PolicyService가 판정 직전에 정규식으로 펼쳐서 넘긴다 (0.5.1 D23).
        // 여기까지 펼쳐지지 않은 채로 오면 아래 매칭 루프가 REGEX도 KEYWORD도 아니라 조용히
        // 건너뛴다 — 적용돼야 할 규칙이 검사 없이 지나가고 결과는 ALLOWED로 기록된다.
        // 감사 기록이 거짓이 되는 경로라 조용히 넘기지 않는다.
        rules.stream()
                .filter(rule -> rule.getRuleType() == RuleType.ROSTER)
                .findFirst()
                .ifPresent(rule -> {
                    throw new IllegalStateException(
                            "ROSTER 규칙 " + rule.getCode() + "이 펼쳐지지 않은 채 엔진에 들어왔습니다. "
                                    + "PolicyService.loadForDecision을 거치거나 RosterExpander.expand를 먼저 부르십시오.");
                });

        LocalDate today = LocalDate.now(clock);
        List<PolicyRule> effective = rules.stream().filter(rule -> !isReleased(rule, today)).toList();

        // 1~2. 매칭. BLOCK을 만나도 나머지 규칙을 전부 실행한다 — 감사 기록의 목적이
        //      "무엇이 걸렸는지 전부 남기는 것"이라 일부만 남으면 사후 소명이 불가능해진다.
        List<RuleHit> rawMatches = new ArrayList<>();
        for (PolicyRule rule : effective) {
            if (rule.getRuleType() == RuleType.REGEX) {
                rawMatches.addAll(regexMatcher.match(originalText, rule));
            }
        }
        for (PolicyRule rule : effective) {
            if (rule.getRuleType() == RuleType.KEYWORD) {
                keywordMatcher.match(originalText, rule).ifPresent(rawMatches::add);
            }
        }

        // 3. 중첩 억제 (0.5 D1)
        List<RuleHit> findings = conflictResolver.suppressNested(rawMatches);

        // 4. 충돌 해결 (7.5)
        FinalDecision decision = conflictResolver.resolve(findings);

        // 5. 마스킹 (0.5 D5). BLOCK이면 실행하지 않는다 — BLOCK 규칙에는 mask_label이 없다.
        String maskedText = decision == FinalDecision.BLOCK ? null : masker.mask(originalText, findings);

        if (log.isDebugEnabled()) {
            log.debug("판정 {} — 기준일 {} · 규칙 {}건 중 만료 {}건 제외 · 원시 매칭 {}건 → finding {}건 {}",
                    decision, today, rules.size(), rules.size() - effective.size(),
                    rawMatches.size(), findings.size(), findings.stream().map(RuleHit::code).toList());
        }

        return new EngineVerdict(
                decision,
                findings,
                List.copyOf(rawMatches),
                new RuleResult(toMatches(findings), appliedRuleCodes(rules)),
                maskedText,
                keywordHits(findings),
                reviewCategories(findings));
    }

    /**
     * 엠바고가 이미 풀렸는가 (결정 4).
     *
     * <p>{@code embargoUntil}은 <b>해제일</b>이다. 기준일이 그 날이거나 그 뒤면 공개할 수 있다 —
     * 경계일 당일은 해제된 쪽이다. 반대로 읽으면 하루 어긋나고, 그 하루가 발표 당일일 수 있다.
     *
     * <p>기한이 없는 규칙({@code null})은 절대 풀리지 않는다. 주민번호는 다음 달이 된다고
     * 덜 민감해지지 않는다.
     */
    private static boolean isReleased(PolicyRule rule, LocalDate today) {
        LocalDate until = rule.getEmbargoUntil();
        return until != null && !today.isBefore(until);
    }

    /**
     * {@code inspection.rule_result.matches} (계약서 §4 인계 2). 중첩 억제 <b>후</b>의 목록이며
     * Case A에서 2건이다.
     */
    private static List<RuleResult.RuleMatch> toMatches(List<RuleHit> findings) {
        return findings.stream()
                .map(hit -> new RuleResult.RuleMatch(
                        hit.code(),
                        hit.category(),
                        hit.action(),
                        List.of(hit.spanStart(), hit.spanEnd()),
                        // REGEX는 null이다. 매칭 문자열을 넣으면 주민번호 원문이 JSONB에 그대로 남는다.
                        hit.matchedKeyword(),
                        hit.severity(),
                        hit.rule().getObligation(),
                        hit.rule().getSource(),
                        // 문자열로 넣는다. JSONB 직렬화는 Hibernate가 자체 ObjectMapper로 하므로
                        // LocalDate를 그대로 두면 JavaTimeModule 등록 여부에 결과가 달라진다.
                        hit.embargoUntil() == null ? null : hit.embargoUntil().toString()))
                .toList();
    }

    /**
     * 로드된 활성 규칙 전체. 억제되어 finding이 없는 {@code SEC-PRIVIP-03}·{@code PII-EMAIL-04}도
     * 여기엔 남는다 — 적용된 규칙과 매칭된 규칙은 다르다 (8.4).
     */
    private static List<String> appliedRuleCodes(List<PolicyRule> rules) {
        return rules.stream().map(PolicyRule::getCode).toList();
    }

    /**
     * {@code AiInspectionRequest.hits} (계약서 §4). <b>REVIEW 매칭에서만</b> 만든다 —
     * PII·SECRET의 확정 판정은 AI의 영역이 아니다 (9.2 금지 조항).
     *
     * <p>KEYWORD는 키워드당 1건이다 (0.5 D9). Case B는 finding 1건에 hits 2건
     * ({@code A사}, {@code 차세대})이다.
     *
     * <p>REGEX REVIEW는 <b>매칭 문자열을 넣지 않고</b> 규칙당 1건만 만든다. 실명 의심처럼
     * 매칭 자체가 개인정보인 규칙이 있어서, 그대로 넣으면 이름이 AI 요청과 로그에 남는다.
     * {@code hits}가 비면 {@code AiInspector}가 무결성 위반으로 예외를 던지므로
     * 자리는 채우되 내용은 넘기지 않는다.
     */
    private static final String REDACTED_HIT = "(형식 일치)";

    private static List<KeywordHit> keywordHits(List<RuleHit> findings) {
        List<KeywordHit> hits = new ArrayList<>();
        Set<String> regexReviewSeen = new LinkedHashSet<>();
        for (RuleHit finding : findings) {
            if (finding.action() != RuleAction.REVIEW) {
                continue;
            }
            if (finding.ruleType() == RuleType.KEYWORD) {
                for (String keyword : finding.matchedKeywords()) {
                    hits.add(new KeywordHit(keyword, finding.code(), finding.rule().getSource()));
                }
            } else if (regexReviewSeen.add(finding.code())) {
                hits.add(new KeywordHit(REDACTED_HIT, finding.code(), finding.rule().getSource()));
            }
        }
        return List.copyOf(hits);
    }

    /** 매칭된 REVIEW 규칙이 속한 정책의 카테고리. 적용된 정책 전체가 아니다 (계약서 §4). */
    private static List<String> reviewCategories(List<RuleHit> findings) {
        Set<String> categories = new LinkedHashSet<>();
        for (RuleHit finding : findings) {
            if (finding.action() == RuleAction.REVIEW) {
                categories.add(finding.category().name());
            }
        }
        return List.copyOf(categories);
    }
}
