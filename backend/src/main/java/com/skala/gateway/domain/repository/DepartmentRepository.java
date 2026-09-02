package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.Department;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    java.util.List<Department> findAllByOrderByDeptIdAsc();
}
