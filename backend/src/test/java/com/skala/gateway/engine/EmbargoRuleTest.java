package com.skala.gateway.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.gateway.DemoCases;
import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.repository.PolicyRuleRepository;
import com.skala.gateway.service.RosterExpander;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 엠바고 규칙의 만료 판정 (결정 4).
 *
 * <p>기준일을 {@code gateway.embargo.reference-date}로 고정한다 — 시스템 날짜에 묶이면
 * 이 테스트가 2026-09-20에 저절로 깨진다. 고정값은 발표 당일이며, 그 날 하나는 걸리고
 * 하나는 풀린 상태여야 시연이 성립한다.
 */
@SpringBootTest
@TestPropertySource(properties = "gateway.embargo.reference-date=" + DemoCases.DEMO_REFERENCE_DATE)
@Transactional
class EmbargoRuleTest {

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private PolicyRuleRepository policyRuleRepository;

    @Autowired
    private RosterExpander rosterExpander;

    @Autowired
    private RegexMatcher regexMatcher;

    @Autowired
    private KeywordMatcher keywordMatcher;

    @Autowired
    private ConflictResolver conflictResolver;

    @Autowired
    private Masker masker;

    @Test
    @DisplayName("Case E — 개발팀이 넣은 백로그가 해제 전 엠바고에 걸려 차단된다")
    void blocksBeforeRelease() {
        List<PolicyRule> rules = rosterExpander.expand(policyRuleRepository.findActiveByDept(DemoCases.DEPT_DEV));

        EngineVerdict verdict = ruleEngine.evaluate(DemoCases.CASE_E, rules);

        assertThat(verdict.decision()).isEqualTo(FinalDecision.BLOCK);
        assertThat(verdict.findings()).extracting(RuleHit::code).containsExactly("EMB-NOVA-01");
        // BLOCK이므로 마스킹본을 만들지 않는다 (0.5 D5).
        assertThat(verdict.maskedText()).isNull();
        // 해제일이 화면까지 나가야 "언제 다시 시도하면 되는지"를 알 수 있다.
        assertThat(verdict.ruleResult().matches())
                .singleElement()
                .extracting("embargoUntil")
                .isEqualTo("2026-09-20");
    }

    @Test
    @DisplayName("Case E-2 — 해제일이 지난 엠바고는 같은 형태인데도 걸리지 않는다")
    void allowsAfterRelease() {
        List<PolicyRule> rules = rosterExpander.expand(policyRuleRepository.findActiveByDept(DemoCases.DEPT_DEV));

        EngineVerdict verdict = ruleEngine.evaluate(DemoCases.CASE_E_RELEASED, rules);

        assertThat(verdict.decision()).isEqualTo(FinalDecision.ALLOW);
        assertThat(verdict.findings()).isEmpty();
        // 만료돼도 로드된 규칙에는 남는다 — 적용된 규칙과 매칭된 규칙은 다르다 (8.4).
        assertThat(verdict.ruleResult().appliedRuleCodes()).contains("EMB-ATLAS-02");
    }

    @Test
    @DisplayName("홍보팀에는 P-EMBARGO가 매핑되지 않아 같은 문장이 통과한다")
    void ownerDepartmentIsNotSubjectToItsOwnEmbargo() {
        List<PolicyRule> rules = rosterExpander.expand(policyRuleRepository.findActiveByDept(DemoCases.DEPT_PR));

        EngineVerdict verdict = ruleEngine.evaluate(DemoCases.CASE_E, rules);

        assertThat(verdict.decision()).isEqualTo(FinalDecision.ALLOW);
        assertThat(verdict.ruleResult().appliedRuleCodes()).doesNotContain("EMB-NOVA-01");
    }

    @Test
    @DisplayName("해제일 당일에는 이미 풀린 것이다 — 경계는 today < embargoUntil")
    void releaseDateItselfIsAlreadyReleased() {
        List<PolicyRule> rules = rosterExpander.expand(policyRuleRepository.findActiveByDept(DemoCases.DEPT_DEV));

        // 09-19: 아직 하루 남았다
        assertThat(engineAt("2026-09-19").evaluate(DemoCases.CASE_E, rules).decision())
                .isEqualTo(FinalDecision.BLOCK);
        // 09-20: 해제일 당일. 이 하루가 어긋나면 발표 당일에 판정이 뒤집힌다
        assertThat(engineAt("2026-09-20").evaluate(DemoCases.CASE_E, rules).decision())
                .isEqualTo(FinalDecision.ALLOW);
    }

    /** 기준일만 다른 엔진. 컨텍스트를 새로 띄우지 않고 경계값을 확인한다. */
    private RuleEngine engineAt(String date) {
        Clock clock = Clock.fixed(
                LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        return new RuleEngine(regexMatcher, keywordMatcher, conflictResolver, masker, clock);
    }
}
