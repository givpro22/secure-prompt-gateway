package com.skala.gateway.api;

import com.skala.gateway.api.dto.ErrorResponse;
import com.skala.gateway.api.dto.PageEnvelope;
import com.skala.gateway.api.dto.PolicyDto;
import com.skala.gateway.service.PolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/policies?deptId=} (기획서 8.3, 계약서 §1-3).
 *
 * <p>SCR-01 하단 캡션("부서: 개발팀 · 적용 정책 2건")과 정책 패널이 쓴다.
 *
 * <p><b>GLOBAL + 매핑된 DEPT를 합쳐 반환한다.</b> 부서 필터만 걸면 전사 정책이 누락되어
 * 화면이 "적용 정책 0건"을 그린다.
 */
@RestController
@RequestMapping("/api/v1")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    /**
     * @param deptId <b>필수</b>다 (계약서 C6). 생략하면 GLOBAL만 줄지 전체를 줄지가 모호하고,
     *               모호한 기본값은 화면마다 다른 결과를 만든다
     */
    @GetMapping("/policies")
    public ResponseEntity<?> policies(@RequestParam(required = false) String deptId) {
        Long parsedDeptId;
        try {
            parsedDeptId = QueryParams.optionalLong(deptId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.invalidParameter("deptId는 숫자여야 합니다: " + deptId));
        }
        if (parsedDeptId == null) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.invalidParameter("deptId는 필수입니다."));
        }

        PageEnvelope<PolicyDto> body = PageEnvelope.ofAll(policyService.policiesForDept(parsedDeptId));
        return ResponseEntity.ok(body);
    }
}
