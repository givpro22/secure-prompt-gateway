package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.UnmaskRequest;
import com.skala.gateway.domain.enums.UnmaskStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UnmaskRequestRepository extends JpaRepository<UnmaskRequest, Long> {

    boolean existsByMessage_MessageId(Long messageId);

    Optional<UnmaskRequest> findByMessage_MessageId(Long messageId);

    /** 목록은 담당자가 본다. 원문·작성자를 함께 끌어와 N+1을 막는다 */
    @Query("""
            select r
              from UnmaskRequest r
              join fetch r.message m
              join fetch m.user u
              join fetch u.department
              join fetch r.requester
             where (:status is null or r.status = :status)
             order by r.createdAt desc
            """)
    Page<UnmaskRequest> findForConsole(UnmaskStatus status, Pageable pageable);

    /** 직원 화면이 자기 건의 요청 상태를 표시할 때 쓴다 */
    List<UnmaskRequest> findByMessage_MessageIdIn(List<Long> messageIds);
}
