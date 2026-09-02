package com.skala.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * {@link DepartmentPolicy}의 복합 PK {@code (dept_id, policy_id)} (기획서 6.2).
 *
 * <p>서러게이트 PK를 추가하면 같은 (부서, 정책) 조합이 중복 INSERT되는 것을 막지 못한다.
 */
@Embeddable
public class DepartmentPolicyId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "policy_id")
    private Long policyId;

    protected DepartmentPolicyId() {
    }

    public DepartmentPolicyId(Long deptId, Long policyId) {
        this.deptId = deptId;
        this.policyId = policyId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DepartmentPolicyId other)) {
            return false;
        }
        return Objects.equals(deptId, other.deptId) && Objects.equals(policyId, other.policyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deptId, policyId);
    }

    @Override
    public String toString() {
        return "DepartmentPolicyId[deptId=" + deptId + ", policyId=" + policyId + "]";
    }
}
