package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 계정 전환 드롭다운용. 부서를 같이 끌어온다 — 화면이 "김OO (영업팀)"을 그린다. */
    @EntityGraph(attributePaths = "department")
    List<AppUser> findAllByOrderByUserIdAsc();

    @EntityGraph(attributePaths = "department")
    List<AppUser> findByDepartmentDeptId(Long deptId);
}
