package com.skala.gateway.domain;

import com.skala.gateway.domain.enums.PolicyCategory;
import com.skala.gateway.domain.enums.PolicyScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 정책 헤더 (기획서 6.2, 7.1).
 *
 * <p>{@code scope=GLOBAL}이면 {@code department_policy} 매핑 없이 전 부서에 적용된다.
 * 전사 정책까지 매핑 행을 만들면 부서가 추가될 때 누락이 생기는 구조로 되돌아간다 (6.4).
 *
 * <p>{@code version}은 규칙이 바뀔 때 증가하며, 판정 시점 값이
 * {@link com.skala.gateway.domain.jsonb.PolicySnapshot}에 얼려진다. 별도 이력 테이블
 * ({@code policy_audit})은 Future다.
 */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PolicyCategory category;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private PolicyScope scope;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Policy() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (version == null) {
            version = 1;
        }
    }

    public Long getPolicyId() {
        return policyId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PolicyCategory getCategory() {
        return category;
    }

    public Integer getVersion() {
        return version;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public PolicyScope getScope() {
        return scope;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
