package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.Inspection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionRepository
        extends JpaRepository<Inspection, Long>, JpaSpecificationExecutor<Inspection> {

    /** 상세 조회(SCR-02 드로어). message·user·department를 한 번에 끌어온다. */
    @Query("""
            select i
              from Inspection i
              join fetch i.message m
              join fetch m.user u
              join fetch u.department
             where i.inspectionId = :id
            """)
    Optional<Inspection> findDetailById(@Param("id") Long id);

    /**
     * 감사 콘솔 목록 (기획서 5.4, 계약서 §1-6). {@link InspectionSpecs}로 조건을 조립한다.
     *
     * <p>선택 필터를 {@code (:param is null or ...)} JPQL로 쓰지 않은 이유가 있다.
     * PostgreSQL이 타입 없는 바인딩의 {@code $n IS NULL}을 해석하지 못해
     * {@code could not determine data type of parameter}로 죽는다. 요청에 있는 조건만
     * Specification으로 붙이면 애초에 null 파라미터가 SQL에 나가지 않는다.
     *
     * <p>{@code @EntityGraph}가 없으면 20행 목록에서 부서·사용자 조회가 행마다 나간다.
     */
    @Override
    @EntityGraph(attributePaths = {"message", "message.user", "message.user.department"})
    Page<Inspection> findAll(Specification<Inspection> spec, Pageable pageable);
}
