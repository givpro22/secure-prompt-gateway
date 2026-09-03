package com.skala.gateway.service;

import com.skala.gateway.api.dto.PolicyDto;
import com.skala.gateway.api.dto.PolicyRuleDto;
import com.skala.gateway.domain.Policy;
import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.jsonb.PolicySnapshot;
import com.skala.gateway.domain.repository.PolicyRepository;
import com.skala.gateway.domain.repository.PolicyRuleRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정책 로드 (기획서 7.4-1~3).
 *
 * <p>로드 순서가 계약이다 — 사용자 → 부서 → (scope=GLOBAL 활성 정책 전부 +
 * {@code department_policy}로 매핑된 scope=DEPT 정책) → 활성 규칙. 부서에 매핑된 DEPT 정책이
 * 0건이면 GLOBAL만 적용하고 예외를 던지지 않는다. Case C(개발팀)가 바로 그 경우이며,
 * P-CONF가 로드되지 않아 같은 문장이 ALLOW로 갈린다.
 */
@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final RosterExpander rosterExpander;

    public PolicyService(PolicyRepository policyRepository, PolicyRuleRepository policyRuleRepository,
                         RosterExpander rosterExpander) {
        this.policyRepository = policyRepository;
        this.policyRuleRepository = policyRuleRepository;
        this.rosterExpander = rosterExpander;
    }

    /**
     * 판정 1회에 쓰이는 정책 묶음.
     *
     * @param rules            실행할 활성 규칙. 순서가 곧 7.4의 실행 순서다
     * @param snapshot         {@code inspection.policy_snapshot}에 그대로 저장된다. 정책이 나중에
     *                         바뀌어도 판정 당시의 근거가 보존된다 (6.4)
     * @param policyVersion    {@code AiInspectionRequest.policyVersion}. {@code code:version} 쌍을
     *                         <b>code 사전순</b>으로 {@code ;} 연결한 것 ({@code P-CONF:2;P-PII:4;P-SEC:7}).
     *                         정렬을 고정하는 이유는 Mock의 결정론 때문이다
     */
    public record PolicyContext(List<PolicyRule> rules, PolicySnapshot snapshot, String policyVersion) {
    }

    /**
     * 판정용 정책 로드 (7.4-2, 7.4-3).
     *
     * <p><b>로드가 실패하면 예외를 던지고 전송을 보류한다</b> (UC-01 예외). 빈 정책으로
     * 통과시키면 검사 없이 지나간 프롬프트가 ALLOWED로 기록되어 감사 기록 자체가 거짓이 된다.
     */
    @Transactional(readOnly = true)
    public PolicyContext loadForDecision(Long deptId) {
        List<Policy> policies = policyRepository.findActiveByDept(deptId);
        List<PolicyRule> rules = policyRuleRepository.findActiveByDept(deptId);

        if (rules.isEmpty()) {
            // GLOBAL 정책은 매핑 없이 전 부서에 적용되므로(7.3) 활성 규칙이 0건이라는 것은
            // 정책 데이터가 비었다는 뜻이다. 통과시키지 않는다.
            throw new IllegalStateException(
                    "부서 " + deptId + "에 적용할 활성 규칙이 없습니다. policy/policy_rule 시드를 확인하십시오.");
        }

        // 스냅샷은 펼치기 <b>전</b> 규칙으로 만든다. 감사 기록에 남아야 할 것은 "고객 명단
        // 규칙이 적용됐다"이지 그 시점 명단 24명의 이름이 아니다 (0.5.1 D23).
        PolicySnapshot snapshot = snapshot(policies, rules);
        return new PolicyContext(rosterExpander.expand(rules), snapshot, policyVersion(policies));
    }

    /** {@code GET /api/v1/policies?deptId=} (계약서 §1-3). GLOBAL + 매핑된 DEPT를 합쳐 반환한다. */
    @Transactional(readOnly = true)
    public List<PolicyDto> policiesForDept(Long deptId) {
        List<Policy> policies = policyRepository.findActiveByDept(deptId);
        Map<Long, List<PolicyRule>> rulesByPolicy = rulesByPolicy(policyRuleRepository.findActiveByDept(deptId));

        return policies.stream()
                .map(policy -> PolicyDto.from(policy, rulesByPolicy
                        .getOrDefault(policy.getPolicyId(), List.of()).stream()
                        .map(PolicyRuleDto::from)
                        .toList()))
                .toList();
    }

    /**
     * {@code policy_snapshot} 조립 (7.4-3). 항목은 {@code {policyId, code, version, ruleCodes[]}}다
     * (계약서 C9) — API가 {@code {code, version}}으로 투영하면 판정 시점에 어떤 규칙이 활성이었는지를
     * 감사 화면에서 볼 수 없게 된다.
     */
    private static PolicySnapshot snapshot(List<Policy> policies, List<PolicyRule> rules) {
        Map<Long, List<PolicyRule>> rulesByPolicy = rulesByPolicy(rules);
        List<PolicySnapshot.PolicyRef> refs = policies.stream()
                .map(policy -> new PolicySnapshot.PolicyRef(
                        policy.getPolicyId(),
                        policy.getCode(),
                        policy.getVersion(),
                        rulesByPolicy.getOrDefault(policy.getPolicyId(), List.of()).stream()
                                .map(PolicyRule::getCode)
                                .sorted()
                                .toList()))
                .toList();
        return new PolicySnapshot(refs);
    }

    private static String policyVersion(List<Policy> policies) {
        return policies.stream()
                .sorted(Comparator.comparing(Policy::getCode))
                .map(policy -> policy.getCode() + ":" + policy.getVersion())
                .collect(Collectors.joining(";"));
    }

    /** 규칙을 정책별로 묶는다. 규칙 목록의 순서는 그대로 보존된다. */
    private static Map<Long, List<PolicyRule>> rulesByPolicy(List<PolicyRule> rules) {
        Map<Long, List<PolicyRule>> grouped = new LinkedHashMap<>();
        for (PolicyRule rule : rules) {
            grouped.computeIfAbsent(rule.getPolicy().getPolicyId(), key -> new ArrayList<>()).add(rule);
        }
        return grouped;
    }
}
