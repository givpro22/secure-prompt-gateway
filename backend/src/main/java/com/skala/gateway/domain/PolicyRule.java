package com.skala.gateway.domain;

import com.skala.gateway.domain.enums.Obligation;
import com.skala.gateway.domain.enums.RuleAction;
import com.skala.gateway.domain.enums.RuleType;
import com.skala.gateway.domain.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 규칙 (기획서 6.2, 7.2).
 *
 * <p>{@code rule-engine-dev}와 {@code data-architect}의 경계면이다. 이 shape이 바뀌면
 * 규칙 엔진이 깨지므로 변경 시 반드시 통보한다.
 *
 * <p>{@code pattern}은 정규식 원문이며 API 응답에 노출하지 않는다 (계약서 C5) — 탐지
 * 정규식이 클라이언트에 나가면 우회 입력을 만들 수 있다.
 *
 * <p>{@code maskLabel}은 {@code action=MASK}일 때만 존재하고 DB CHECK로 강제된다.
 * BLOCK 규칙에 라벨이 없다는 사실이 D5(BLOCK이면 마스킹 미실행)의 근거 중 하나다.
 */
@Entity
@Table(name = "policy_rule")
public class PolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private RuleType ruleType;

    @Column(name = "pattern", nullable = false, columnDefinition = "text")
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private RuleAction action;

    @Column(name = "mask_label", length = 30)
    private String maskLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "obligation", nullable = false, length = 20)
    private Obligation obligation;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    protected PolicyRule() {
    }

    public Long getRuleId() {
        return ruleId;
    }

    public Policy getPolicy() {
        return policy;
    }

    public String getCode() {
        return code;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public String getPattern() {
        return pattern;
    }

    public RuleAction getAction() {
        return action;
    }

    public String getMaskLabel() {
        return maskLabel;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Obligation getObligation() {
        return obligation;
    }

    public String getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getIsActive() {
        return isActive;
    }
}
