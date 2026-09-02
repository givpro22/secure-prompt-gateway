package com.skala.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 부서 ↔ 정책 N:M 매핑 (기획서 6.2, 6.4, 7.3).
 *
 * <p>{@code scope=DEPT} 정책만 여기에 들어온다. 시드는 (SALES, P-CONF), (HR, P-CONF)
 * 2행뿐이다. GLOBAL 정책을 매핑하면 부서 추가 시 누락이 생기는 구조로 되돌아간다.
 */
@Entity
@Table(name = "department_policy")
public class DepartmentPolicy {

    @EmbeddedId
    private DepartmentPolicyId id;

    @MapsId("deptId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dept_id", nullable = false)
    private Department department;

    @MapsId("policyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt;

    protected DepartmentPolicy() {
    }

    public DepartmentPolicy(Department department, Policy policy) {
        this.id = new DepartmentPolicyId(department.getDeptId(), policy.getPolicyId());
        this.department = department;
        this.policy = policy;
    }

    @PrePersist
    void onCreate() {
        if (appliedAt == null) {
            appliedAt = OffsetDateTime.now();
        }
    }

    public DepartmentPolicyId getId() {
        return id;
    }

    public Department getDepartment() {
        return department;
    }

    public Policy getPolicy() {
        return policy;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }
}
