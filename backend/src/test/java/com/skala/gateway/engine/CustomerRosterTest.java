package com.skala.gateway.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skala.gateway.DemoCases;
import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.repository.PolicyRuleRepository;
import com.skala.gateway.service.RosterExpander;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객 명단 기반 탐지 (0.5.1 D23).
 *
 * <p>고객 명단을 사내 DB에 두는 것은 정상이다. 막아야 할 것은 그것이 외부로 나가는 순간이다.
 *
 * <p>시드 명단은 합성이다. 기획서 10.1이 실명을 금지하므로 테스트도 실명을 쓰지 않는다.
 */
@SpringBootTest
@Transactional
class CustomerRosterTest {

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private PolicyRuleRepository policyRuleRepository;

    @Autowired
    private RosterExpander rosterExpander;

    /** 판정 경로와 같게, 명단을 펼친 뒤 엔진에 넣는다. */
    private EngineVerdict eval(String text) {
        List<PolicyRule> rules =
                rosterExpander.expand(policyRuleRepository.findActiveByDept(DemoCases.DEPT_HR));
        return ruleEngine.evaluate(text, rules);
    }

    @Test
    @DisplayName("명단에 있는 고객명은 마스킹한다")
    void listedCustomerIsMasked() {
        EngineVerdict v = eval("담당자 김서준 고객님께 안내 문자 보내줘");

        assertThat(v.decision()).isEqualTo(FinalDecision.MASK);
        assertThat(v.findings()).extracting(RuleHit::code).contains("PII-CUST-07");
        assertThat(v.maskedText()).isEqualTo("담당자 [고객명] 고객님께 안내 문자 보내줘");
    }

    @Test
    @DisplayName("여러 명이면 전부 마스킹한다 — 명단이 표째로 나가는 것을 막는 자리다")
    void everyListedCustomerIsMasked() {
        EngineVerdict v = eval("박예린, 조현우, 한연우 세 분께 발송");

        assertThat(v.maskedText()).isEqualTo("[고객명], [고객명], [고객명] 세 분께 발송");
        assertThat(v.findings()).filteredOn(h -> h.code().equals("PII-CUST-07")).hasSize(3);
    }

    @Test
    @DisplayName("성을 뗀 이름만 나오면 마스킹하지 않고 검토로 보낸다")
    void givenNameOnlyGoesToReview() {
        EngineVerdict v = eval("담당 서준 확인 부탁해");

        assertThat(v.decision()).isEqualTo(FinalDecision.PENDING);
        assertThat(v.findings()).extracting(RuleHit::code).contains("PII-CUST-08");
        assertThat(v.maskedText()).isEqualTo("담당 서준 확인 부탁해");
    }

    @Test
    @DisplayName("REGEX REVIEW도 AI 근거를 만들되 이름은 넘기지 않는다")
    void reviewHitsAreRedacted() {
        EngineVerdict v = eval("담당 서준 확인 부탁해");

        assertThat(v.hits()).isNotEmpty();
        assertThat(v.hits()).noneMatch(h -> h.keyword().contains("서준"));
        assertThat(v.hits()).extracting("ruleCode").contains("PII-CUST-08");
    }

    @Test
    @DisplayName("접사가 붙거나 명단에 없으면 통과한다")
    void boundariesAndUnlistedNames() {
        assertThat(eval("서준이가 그러던데").decision()).isEqualTo(FinalDecision.ALLOW);
        assertThat(eval("우진공업 견적 확인").decision()).isEqualTo(FinalDecision.ALLOW);
        assertThat(eval("재현 가능한 결과인지 확인").decision()).isEqualTo(FinalDecision.ALLOW);
        assertThat(eval("김치찌개 주문해줘").decision()).isEqualTo(FinalDecision.ALLOW);
    }

    @Test
    @DisplayName("스냅샷에는 규칙 코드만 남고 그 시점 명단은 남지 않는다")
    void snapshotKeepsRuleCodeNotTheRoster() {
        EngineVerdict v = eval("담당자 김서준 고객님께");

        assertThat(v.ruleResult().appliedRuleCodes()).contains("PII-CUST-07", "PII-CUST-08");
        // 매칭 문자열을 JSONB에 남기지 않는다 — 고객 이름이 감사 데이터에 박힌다.
        assertThat(v.ruleResult().matches()).allSatisfy(
                m -> assertThat(m.matchedKeyword()).isNull());
    }

    @Test
    @DisplayName("펼쳐지지 않은 ROSTER 규칙이 엔진에 들어오면 예외 — 조용히 지나가면 안 된다")
    void unexpandedRosterRuleIsRejected() {
        List<PolicyRule> raw = policyRuleRepository.findActiveByDept(DemoCases.DEPT_HR);

        assertThatThrownBy(() -> ruleEngine.evaluate("담당자 김서준", raw))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("펼쳐지지 않은 채");
    }
}
