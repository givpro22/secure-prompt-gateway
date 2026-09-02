package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.Policy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    Optional<Policy> findByCode(String code);

    /**
     * 부서에 적용되는 활성 정책 (기획서 7.4-2).
     *
     * <p>scope=GLOBAL은 매핑 없이 전부, scope=DEPT는 {@code department_policy} 매핑이
     * 있는 것만. 이 한 쿼리가 7.3 매트릭스 전체를 대신한다.
     *
     * <p>{@code appliedVia}(계약서 §3)는 결과의 {@code scope}로 그대로 파생된다 —
     * GLOBAL이면 전사 적용, DEPT면 매핑 적용이다.
     */
    @Query("""
            select p
              from Policy p
             where p.isActive = true
               and (p.scope = com.skala.gateway.domain.enums.PolicyScope.GLOBAL
                    or exists (select 1
                                 from DepartmentPolicy dp
                                where dp.id.policyId = p.policyId
                                  and dp.id.deptId = :deptId))
             order by p.policyId
            """)
    List<Policy> findActiveByDept(@Param("deptId") Long deptId);
}
