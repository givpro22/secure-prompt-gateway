package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.DepartmentPolicy;
import com.skala.gateway.domain.DepartmentPolicyId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentPolicyRepository
        extends JpaRepository<DepartmentPolicy, DepartmentPolicyId> {

    List<DepartmentPolicy> findByIdDeptId(Long deptId);
}
