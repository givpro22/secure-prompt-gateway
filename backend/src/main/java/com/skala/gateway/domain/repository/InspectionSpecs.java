package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.enums.MessageStatus;
import jakarta.persistence.criteria.Join;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 감사 콘솔 목록(`GET /api/v1/inspections`)의 선택 필터 (기획서 5.4, 계약서 §1-6).
 *
 * <p>{@code null}인 필터는 Specification 자체를 만들지 않는다. 조건이 SQL에 나가지
 * 않으므로 "값이 없으면 전체"가 자연스럽게 성립한다.
 */
public final class InspectionSpecs {

    /** 정렬은 {@code createdAt DESC} 고정이다. 정렬 파라미터를 두지 않는다 (계약서 §1-6). */
    public static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private InspectionSpecs() {
    }

    /** 제출자 부서. INFOSEC은 프롬프트를 제출하지 않아 항상 0건이다 (0.5 D2). */
    public static Specification<Inspection> deptId(Long deptId) {
        return (root, query, cb) -> {
            Join<?, ?> user = root.join("message").join("user");
            return cb.equal(user.get("department").get("deptId"), deptId);
        };
    }

    /** {@code message.status} 4값. */
    public static Specification<Inspection> status(MessageStatus status) {
        return (root, query, cb) -> cb.equal(root.join("message").get("status"), status);
    }

    /** {@code from} 이상. */
    public static Specification<Inspection> createdFrom(OffsetDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    /** {@code to} <b>미만</b>. 경계를 포함하면 하루 단위 필터가 다음 날 00:00을 삼킨다. */
    public static Specification<Inspection> createdBefore(OffsetDateTime to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }

    /** null이 아닌 필터만 AND로 묶는다. 전부 null이면 조건 없는 전체 조회다. */
    public static Specification<Inspection> of(Long deptId, MessageStatus status,
                                               OffsetDateTime from, OffsetDateTime to) {
        List<Specification<Inspection>> specs = new ArrayList<>();
        if (deptId != null) {
            specs.add(deptId(deptId));
        }
        if (status != null) {
            specs.add(status(status));
        }
        if (from != null) {
            specs.add(createdFrom(from));
        }
        if (to != null) {
            specs.add(createdBefore(to));
        }
        return specs.stream().reduce(Specification::and).orElse(null);
    }
}
