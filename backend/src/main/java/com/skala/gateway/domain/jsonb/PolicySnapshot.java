package com.skala.gateway.domain.jsonb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * {@code inspection.policy_snapshot} JSONB의 Java 표현 (기획서 6.2, 7.4-3).
 *
 * <p>판정 시점의 정책 id·version·규칙 코드를 그대로 얼려둔다. 정책이 나중에 바뀌어도
 * 당시 어떤 버전으로 판정했는지가 남는다. 원본은 {@code policy}/{@code policy_rule}에
 * 정규화되어 있고 이 JSONB는 시점 스냅샷이다 (6.4).
 *
 * <p>API 응답 필드명은 {@code policySnapshot}이며 shape은 그대로 노출된다
 * (계약서 §2 inspection 매핑).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicySnapshot(List<PolicyRef> policies) {

    /**
     * @param policyId  정책 PK
     * @param code      P-PII / P-SEC / P-CONF
     * @param version   판정 시점의 {@code policy.version}
     * @param ruleCodes 해당 정책에서 로드된 활성 규칙 코드
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolicyRef(Long policyId, String code, Integer version, List<String> ruleCodes) {
    }
}
