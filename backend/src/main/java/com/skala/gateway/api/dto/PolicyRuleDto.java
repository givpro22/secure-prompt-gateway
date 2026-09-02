package com.skala.gateway.api.dto;

import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.enums.Obligation;
import com.skala.gateway.domain.enums.RuleAction;
import com.skala.gateway.domain.enums.RuleType;
import com.skala.gateway.domain.enums.Severity;

/**
 * 정책 패널(SCR-01)에 표시할 규칙 (계약서 §1-3).
 *
 * <p><b>{@code pattern}이 없다</b> (계약서 C5). 탐지 정규식이 클라이언트로 나가면 우회 입력을
 * 만들 수 있다. 화면에 필요한 것은 code·description·action·severity·source뿐이다.
 */
public record PolicyRuleDto(
        Long ruleId,
        String code,
        RuleType ruleType,
        RuleAction action,
        String maskLabel,
        Severity severity,
        Obligation obligation,
        String source,
        String description) {

    public static PolicyRuleDto from(PolicyRule rule) {
        return new PolicyRuleDto(
                rule.getRuleId(),
                rule.getCode(),
                rule.getRuleType(),
                rule.getAction(),
                rule.getMaskLabel(),
                rule.getSeverity(),
                rule.getObligation(),
                rule.getSource(),
                rule.getDescription());
    }
}
