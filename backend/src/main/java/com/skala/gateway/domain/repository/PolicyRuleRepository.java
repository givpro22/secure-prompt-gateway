package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.PolicyRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {

    Optional<PolicyRule> findByCode(String code);

    /**
     * 정책 로드 — {@code data-architect} → {@code rule-engine-dev} 인계 시그니처
     * (계약서 §4 인계 1). 이 시그니처는 계약이므로 바꾸지 않는다.
     *
     * <p>정렬이 곧 기획서 7.4의 실행 순서다: REGEX 규칙을 severity 내림차순으로 먼저
     * 실행하고(7.4-4), 그 다음 KEYWORD 규칙을 실행한다(7.4-5). 동률은 rule code
     * 사전순 — 7.6의 라벨 충돌 해결 규칙과 같은 기준이다.
     *
     * <p>정책도 규칙도 {@code is_active=true}인 것만 나온다. 반환 목록의 code 전체가
     * {@code ruleResult.appliedRuleCodes}가 된다 (중첩 억제로 finding이 사라진 규칙도
     * 여기엔 남는다).
     */
    @Query("""
            select r
              from PolicyRule r
              join r.policy p
             where r.isActive = true
               and p.isActive = true
               and (p.scope = com.skala.gateway.domain.enums.PolicyScope.GLOBAL
                    or exists (select 1
                                 from DepartmentPolicy dp
                                where dp.id.policyId = p.policyId
                                  and dp.id.deptId = :deptId))
             order by case r.ruleType
                           when com.skala.gateway.domain.enums.RuleType.REGEX then 0
                           else 1 end,
                      case r.severity
                           when com.skala.gateway.domain.enums.Severity.HIGH then 0
                           when com.skala.gateway.domain.enums.Severity.MEDIUM then 1
                           else 2 end,
                      r.code
            """)
    List<PolicyRule> findActiveByDept(@Param("deptId") Long deptId);

    /** 정책 패널(SCR-01)용. 정책별 활성 규칙. */
    List<PolicyRule> findByPolicyPolicyIdAndIsActiveTrueOrderByCodeAsc(Long policyId);
}
