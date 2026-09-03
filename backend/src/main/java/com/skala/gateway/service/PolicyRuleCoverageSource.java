package com.skala.gateway.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.skala.gateway.ai.RuleCoverageSource;
import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.enums.RuleType;
import com.skala.gateway.domain.repository.PolicyRuleRepository;

/**
 * {@link RuleCoverageSource} 구현. {@code ai} ↔ {@code domain} 경계면이다.
 *
 * <p>부서를 가리지 않고 활성 규칙 전체를 본다. 요청자 부서에 적용되지 않는 규칙이라도 그 패턴에
 * 걸리는 문자열은 규칙 엔진이 다루는 종류이므로 AI가 후보로 만들 대상이 아니다 (기획서 9.2).
 *
 * <p>KEYWORD 규칙은 <b>제외 기준에 넣지 않는다.</b> AI가 호출되는 계기 자체가 KEYWORD 규칙의
 * REVIEW 판정이므로(7.5), 그 문장을 걸러내면 검사할 것이 남지 않는다.
 */
@Component
public class PolicyRuleCoverageSource implements RuleCoverageSource {

    private final PolicyRuleRepository policyRuleRepository;

    public PolicyRuleCoverageSource(PolicyRuleRepository policyRuleRepository) {
        this.policyRuleRepository = policyRuleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> maskLabels() {
        return active()
                .map(PolicyRule::getMaskLabel)
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> regexPatterns() {
        return active()
                .filter(rule -> rule.getRuleType() == RuleType.REGEX)
                .map(PolicyRule::getPattern)
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .distinct()
                .toList();
    }

    private java.util.stream.Stream<PolicyRule> active() {
        return policyRuleRepository.findAll().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getIsActive()));
    }
}
