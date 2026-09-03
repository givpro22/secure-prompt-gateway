package com.skala.gateway.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.skala.gateway.DemoCases;
import com.skala.gateway.ai.KeywordHit;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.RuleAction;
import com.skala.gateway.domain.repository.PolicyRuleRepository;
import com.skala.gateway.service.RosterExpander;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판정 엔진의 데모 케이스 고정 (기획서 10.4, 0.5 D1·D9·D11).
 *
 * <p><b>정규식은 DB에서 읽는다.</b> 테스트도 {@code policy_rule}에서 규칙을 로드한다 —
 * 패턴을 테스트 상수로 박으면 시드가 깨져도 테스트가 통과해 버려 "규칙은 DB에 있다"는 주장을
 * 검증하지 못한다.
 */
@SpringBootTest
@Transactional
class RuleEngineDemoCaseTest {

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private PolicyRuleRepository policyRuleRepository;

    @Autowired
    private RosterExpander rosterExpander;

    @Test
    @DisplayName("Case A — 원시 매칭 3건이 중첩 억제로 finding 2건이 된다 (D11, D21로 갱신)")
    void caseA_suppressesTwoNestedMatches() {
        EngineVerdict verdict = evaluate(DemoCases.CASE_A, DemoCases.DEPT_DEV);

        // D21 이전에는 여기가 4건이었다. PII-EMAIL-04가 `p%40ss@10.0.3.21`을 이메일로
        // 오탐했고 SEC-DBURL-02 구간에 포함되어 억제됐다. 이메일 정규식의 최상위 도메인을
        // 알파벳으로 못 박으면서 그 오탐이 애초에 발생하지 않는다 — 억제로 가리던 것을
        // 정규식이 직접 해결한 것이다.
        //
        // 중첩 억제 시연은 그대로다. SEC-PRIVIP-03이 여전히 SEC-DBURL-02 안에 들어간다.
        assertThat(verdict.rawMatches())
                .extracting(RuleHit::code, RuleHit::spanStart, RuleHit::spanEnd)
                .containsExactlyInAnyOrder(
                        tuple("SEC-DBURL-02", 18, 56),
                        tuple("SEC-PRIVIP-03", 42, 51),
                        tuple("PII-RRN-01", 73, 87));

        assertThat(verdict.findings())
                .extracting(RuleHit::code, RuleHit::spanStart, RuleHit::spanEnd)
                .containsExactly(
                        tuple("SEC-DBURL-02", 18, 56),
                        tuple("PII-RRN-01", 73, 87));

        assertThat(verdict.decision()).isEqualTo(FinalDecision.BLOCK);
        // BLOCK이면 마스킹을 실행하지 않는다 (D5). 실행했다면 BLOCK 규칙에 mask_label이 없어 NPE다.
        assertThat(verdict.maskedText()).isNull();
        // 억제된 규칙도 appliedRuleCodes에는 남는다 — 적용된 규칙과 매칭된 규칙은 다르다.
        // 7 → 9 → 11 → 13. P-EMBARGO 2종(D20), PII 정밀화 2종(D21), 고객 명단 2종(D23)이
        // 차례로 늘었다.
        // 로드되는 규칙이 늘어도 이 문장에 걸리는 것은 그대로 2건이다 —
        // 적용된 규칙과 매칭된 규칙은 다르다.
        assertThat(verdict.ruleResult().appliedRuleCodes())
                .contains("SEC-PRIVIP-03", "PII-EMAIL-04", "EMB-NOVA-01", "EMB-ATLAS-02",
                          "PII-BIZNO-05", "PII-ACCOUNT-06",
                          "PII-CUST-07", "PII-CUST-08")
                .hasSize(13);
        assertThat(verdict.ruleResult().matches()).hasSize(2);
        // REGEX 매칭 문자열을 JSONB에 남기지 않는다 — 남기면 주민번호 원문이 rule_result에 박힌다.
        assertThat(verdict.ruleResult().matches())
                .allSatisfy(match -> assertThat(match.matchedKeyword()).isNull());
        // BLOCK이면 AI를 호출하지 않으므로 넘길 근거도 없다.
        assertThat(verdict.hits()).isEmpty();
        assertThat(verdict.categories()).isEmpty();
    }

    @Test
    @DisplayName("Case B — KEYWORD finding은 규칙당 1건, hits는 키워드당 1건 (D9)")
    void caseB_oneFindingTwoHits() {
        EngineVerdict verdict = evaluate(DemoCases.CASE_B, DemoCases.DEPT_SALES);

        assertThat(verdict.decision()).isEqualTo(FinalDecision.PENDING);
        assertThat(verdict.findings())
                .extracting(RuleHit::code, RuleHit::action, RuleHit::spanStart, RuleHit::spanEnd)
                .containsExactly(tuple("CONF-CLIENT-01", RuleAction.REVIEW, 0, 2));
        // 첫 매칭(가장 앞선 오프셋)이 matchedKeyword가 된다.
        assertThat(verdict.findings().get(0).matchedKeyword()).isEqualTo("A사");

        // 'A사'와 '차세대'가 둘 다 매칭된다. finding은 1건, hits는 2건이다.
        assertThat(verdict.hits())
                .extracting(KeywordHit::keyword, KeywordHit::ruleCode, KeywordHit::source)
                .containsExactly(
                        tuple("A사", "CONF-CLIENT-01", "고객사 NDA 목록 v3"),
                        tuple("차세대", "CONF-CLIENT-01", "고객사 NDA 목록 v3"));

        assertThat(verdict.categories()).containsExactly("CONFIDENTIAL");
        // MASK 매칭이 없으므로 마스킹본은 원문과 같다. 그래도 null이 아니다 (D7).
        assertThat(verdict.maskedText()).isEqualTo(DemoCases.CASE_B);
    }

    @Test
    @DisplayName("Case C — 같은 문장인데 개발팀에는 P-CONF가 로드되지 않아 ALLOW")
    void caseC_allowsSameSentenceForDev() {
        EngineVerdict verdict = evaluate(DemoCases.CASE_C, DemoCases.DEPT_DEV);

        assertThat(verdict.decision()).isEqualTo(FinalDecision.ALLOW);
        assertThat(verdict.findings()).isEmpty();
        assertThat(verdict.ruleResult().matches()).isEmpty();
        // 규칙 자체가 로드되지 않는다. 매칭이 안 되는 것이 아니라 적용 대상이 아니다.
        assertThat(verdict.ruleResult().appliedRuleCodes()).doesNotContain("CONF-CLIENT-01");
        assertThat(verdict.maskedText()).isEqualTo(DemoCases.CASE_C);
    }

    @Test
    @DisplayName("Case D — 전화번호 1건 MASK, 마스킹본에 [전화번호]")
    void caseD_masksPhoneNumber() {
        EngineVerdict verdict = evaluate(DemoCases.CASE_D, DemoCases.DEPT_HR);

        assertThat(verdict.decision()).isEqualTo(FinalDecision.MASK);
        assertThat(verdict.findings())
                .extracting(RuleHit::code, RuleHit::spanStart, RuleHit::spanEnd)
                .containsExactly(tuple("PII-PHONE-03", 8, 21));
        assertThat(verdict.maskedText()).isEqualTo("지원자 연락처 [전화번호] 로 면접 안내 문자 초안 써줘");
        // 인사팀에는 P-CONF가 매핑돼 있다. 로드는 되지만 키워드가 없어 매칭되지 않는다.
        assertThat(verdict.ruleResult().appliedRuleCodes()).contains("CONF-CLIENT-01");
    }

    @Test
    @DisplayName("규칙이 하나도 안 걸리면 finding 0건 ALLOW")
    void allowsPlainPrompt() {
        EngineVerdict verdict = evaluate("회의록 요약해줘", DemoCases.DEPT_DEV);

        assertThat(verdict.decision()).isEqualTo(FinalDecision.ALLOW);
        assertThat(verdict.rawMatches()).isEmpty();
    }

    @Test
    @DisplayName("BLOCK을 만나도 나머지 규칙을 전부 실행한다 — 감사 기록은 걸린 것을 전부 남긴다")
    void doesNotStopEarlyOnBlock() {
        EngineVerdict verdict = evaluate(
                "키는 AKIAABCDEFGHIJKLMNOP 이고 주민번호는 900101-1234567 이야", DemoCases.DEPT_DEV);

        assertThat(verdict.decision()).isEqualTo(FinalDecision.BLOCK);
        assertThat(verdict.findings()).extracting(RuleHit::code)
                .containsExactly("SEC-AWSKEY-01", "PII-RRN-01");
    }

    private EngineVerdict evaluate(String text, long deptId) {
        // ROSTER 규칙은 PolicyService가 판정 직전에 펼친다. 테스트도 같은 경로를 탄다 (D23).
        return ruleEngine.evaluate(text, rosterExpander.expand(policyRuleRepository.findActiveByDept(deptId)));
    }
}
