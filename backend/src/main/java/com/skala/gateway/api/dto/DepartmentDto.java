package com.skala.gateway.api.dto;

import com.skala.gateway.domain.Department;

/**
 * 부서 (계약서 §1-1).
 *
 * <p>{@code INFOSEC}도 응답에 포함된다 — 계정 전환 드롭다운에 박OO(정보보안팀)이 필요하다.
 * 감사 콘솔 부서 필터에서 INFOSEC을 빼는 것(0.5 D2)은 <b>FE의 표시 결정</b>이며 API는
 * 마스터 4행을 그대로 반환한다.
 */
public record DepartmentDto(Long deptId, String code, String name) {

    public static DepartmentDto from(Department department) {
        return new DepartmentDto(department.getDeptId(), department.getCode(), department.getName());
    }
}
