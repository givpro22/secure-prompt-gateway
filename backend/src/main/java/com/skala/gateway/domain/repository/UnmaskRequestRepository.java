package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.UnmaskRequest;
import com.skala.gateway.domain.enums.UnmaskStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 내가 올린 요청 전부. 요청자가 결과를 받아 보는 유일한 길이다 (D25).
     *
     * <p>메시지 하나씩 묻는 방법도 있지만 그러려면 화면이 messageId를 기억하고 있어야
     * 한다. 새로고침하거나 다른 자리에서 올린 요청은 그 기억 밖이라 확정이 나도 요청자에게
     * 돌아가지 못했다. 목록으로 물으면 그 구멍이 없다.
     */
    @Query("""
            select r from UnmaskRequest r
              join fetch r.message m
              join fetch r.requester u
             where u.userId = :userId
             order by r.createdAt desc
            """)
    List<UnmaskRequest> findMine(@Param("userId") Long userId);
}
