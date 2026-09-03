package com.skala.gateway.api.dto;

import com.skala.gateway.domain.Policy;
import com.skala.gateway.domain.enums.PolicyCategory;
import com.skala.gateway.domain.enums.PolicyScope;
import java.util.List;

/**
 * 부서에 적용되는 정책 (계약서 §1-3). SCR-01 하단 캡션 "부서: 개발팀 · 적용 정책 2건"이 쓴다.
 *
 * @param appliedVia {@code GLOBAL}(scope=GLOBAL이라 전사 적용) 또는 {@code DEPT}
 *                   ({@code department_policy} 매핑으로 적용). 7.3 매트릭스의
 *                   "○ (GLOBAL)" / "○ (매핑)"을 그대로 필드화한 것이다
 * @param registeredAt 정책이 등록된 날짜({@code yyyy-MM-dd}). 화면 하단이 "정책 기준일"로 쓴다.
 *                   버전 정수({@code v5})는 스냅샷 대조용이지 사람이 읽는 값이 아니다.
 *                   <p>시드 정책은 마이그레이션이 돌아간 날이 들어간다 — 운영에서 정책을
 *                   실제로 등록한 날과 같아지려면 정책 편집 UI가 필요하고 그것은 0.3 범위 밖이다
 * @param ownerDept  정책을 <b>만든</b> 부서명. 적용되는 부서와 다르다 — 엠바고는 홍보팀이 걸고
 *                   개발팀이 걸린다. 화면이 "누가 정한 규칙인가"를 보여줄 수 있어야
 *                   차단이 납득된다. 소유 부서가 없는 정책은 {@code null}
 */
public record PolicyDto(
        Long policyId,
        String code,
        String name,
        PolicyCategory category,
        Integer version,
        PolicyScope scope,
        String appliedVia,
        String registeredAt,
        String ownerDept,
        List<PolicyRuleDto> rules) {

    public static PolicyDto from(Policy policy, List<PolicyRuleDto> rules) {
        return new PolicyDto(
                policy.getPolicyId(),
                policy.getCode(),
                policy.getName(),
                policy.getCategory(),
                policy.getVersion(),
                policy.getScope(),
                policy.getScope() == PolicyScope.GLOBAL ? "GLOBAL" : "DEPT",
                policy.getCreatedAt() == null ? null : policy.getCreatedAt().toLocalDate().toString(),
                policy.getOwnerDept() == null ? null : policy.getOwnerDept().getName(),
                rules);
    }
}
