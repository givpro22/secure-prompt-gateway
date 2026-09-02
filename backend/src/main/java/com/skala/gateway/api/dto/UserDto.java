package com.skala.gateway.api.dto;

import com.skala.gateway.domain.AppUser;
import com.skala.gateway.domain.enums.UserRole;

/**
 * 계정 전환 드롭다운용 사용자 (계약서 §1-2).
 *
 * <p>{@code department}는 <b>중첩 객체</b>다. 평탄화({@code deptCode})하지 않는 이유는 헤더가
 * "김OO (영업팀)"을 그리는 데 부서명이 필요하기 때문이다.
 */
public record UserDto(Long userId, String name, String email, UserRole role, DepartmentDto department) {

    public static UserDto from(AppUser user) {
        return new UserDto(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                DepartmentDto.from(user.getDepartment()));
    }
}
