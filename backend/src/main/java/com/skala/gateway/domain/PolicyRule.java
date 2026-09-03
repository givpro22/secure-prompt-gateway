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
import java.time.LocalDate;

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

    /**
     * 엠바고 해제일. <b>이 날부터 공개할 수 있다</b> — 차단 조건은 {@code today < embargoUntil}이며
     * 경계일 당일에는 이미 풀린 것이다. "○○일까지 불가"로 읽으면 하루가 어긋난다.
     *
     * <p>{@code null}이면 기한 없는 규칙이다. 기존 8종이 전부 여기 해당한다 — 주민번호는
     * 다음 달이 된다고 덜 민감해지지 않는다.
     */
    @Column(name = "embargo_until")
    private LocalDate embargoUntil;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    protected PolicyRule() {
    }

    /**
     * ROSTER 규칙을 정규식으로 펼친 <b>비영속</b> 사본 (0.5.1 D23).
     *
     * <p>명단은 {@code customer} 테이블에 있고 규칙 행에는 조회할 컬럼명만 있다. 판정 직전에
     * {@code PolicyService}가 명단을 읽어 이 메서드로 정규식 규칙을 만들어 넘긴다. 덕분에
     * 엔진은 여전히 REGEX만 알면 되고, 같은 입력에 같은 결과를 내는 순수 함수 성질도 남는다.
     *
     * <p><b>절대 저장하지 않는다.</b> {@code ruleId}를 원본과 같게 두는 것은 finding의 FK와
     * {@code appliedRuleCodes}가 실제 규칙 행을 가리켜야 하기 때문이다. 이 인스턴스를
     * {@code save}하면 원본 행의 pattern이 펼쳐진 명단으로 덮인다.
     */
    public PolicyRule materializedAsRegex(String expandedPattern) {
        PolicyRule copy = new PolicyRule();
        copy.ruleId = this.ruleId;
        copy.policy = this.policy;
        copy.code = this.code;
        copy.ruleType = RuleType.REGEX;
        copy.pattern = expandedPattern;
        copy.action = this.action;
        copy.maskLabel = this.maskLabel;
        copy.severity = this.severity;
        copy.obligation = this.obligation;
        copy.source = this.source;
        copy.description = this.description;
        copy.embargoUntil = this.embargoUntil;
        copy.isActive = this.isActive;
        return copy;
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

    public LocalDate getEmbargoUntil() {
        return embargoUntil;
    }

    public Boolean getIsActive() {
        return isActive;
    }
}
