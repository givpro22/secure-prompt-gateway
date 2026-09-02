package com.skala.gateway.api;

import com.skala.gateway.api.dto.DepartmentDto;
import com.skala.gateway.api.dto.ErrorResponse;
import com.skala.gateway.api.dto.PageEnvelope;
import com.skala.gateway.api.dto.UserDto;
import com.skala.gateway.domain.repository.AppUserRepository;
import com.skala.gateway.domain.repository.DepartmentRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마스터 데이터 조회 — {@code GET /departments}, {@code GET /users} (계약서 §1-1, §1-2).
 *
 * <p>둘 다 헤더 없이 호출되는 읽기 전용 마스터이고 계정 전환 드롭다운 하나가 함께 쓴다.
 * 컨트롤러를 나누면 두 줄짜리 클래스가 하나 더 생길 뿐이라 여기에 함께 둔다.
 *
 * <p>{@code INFOSEC}은 두 응답 모두에 포함된다. 드롭다운에 박OO(정보보안팀, SECURITY_ADMIN)이
 * 필요하기 때문이다. 감사 콘솔 부서 필터에서 INFOSEC을 빼는 것(0.5 D2)은 FE의 표시 결정이며
 * API는 마스터 4행을 그대로 반환한다.
 */
@RestController
@RequestMapping("/api/v1")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;
    private final AppUserRepository appUserRepository;

    public DepartmentController(DepartmentRepository departmentRepository,
                                AppUserRepository appUserRepository) {
        this.departmentRepository = departmentRepository;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/departments")
    public PageEnvelope<DepartmentDto> departments() {
        return PageEnvelope.ofAll(departmentRepository.findAllByOrderByDeptIdAsc().stream()
                .map(DepartmentDto::from)
                .toList());
    }

    /**
     * @param deptId 선택. 생략하면 전체. 비숫자면 400 {@code INVALID_PARAMETER}
     */
    @GetMapping("/users")
    public ResponseEntity<?> users(@RequestParam(required = false) String deptId) {
        Long parsedDeptId;
        try {
            parsedDeptId = QueryParams.optionalLong(deptId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.invalidParameter("deptId는 숫자여야 합니다: " + deptId));
        }

        List<UserDto> users = (parsedDeptId == null
                ? appUserRepository.findAllByOrderByUserIdAsc()
                : appUserRepository.findByDepartmentDeptId(parsedDeptId))
                .stream()
                .map(UserDto::from)
                .toList();
        return ResponseEntity.ok(PageEnvelope.ofAll(users));
    }
}
