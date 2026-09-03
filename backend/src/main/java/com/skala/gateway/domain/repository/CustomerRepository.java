package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.Customer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * ROSTER 규칙이 펼칠 이름 목록.
     *
     * <p>정렬을 고정하는 이유는 결정론이다. 순서가 흔들리면 생성되는 정규식 문자열이 매번
     * 달라져 {@code RegexMatcher}의 컴파일 캐시가 계속 무효화된다.
     */
    @Query("select c.name from Customer c where c.isActive = true order by c.name")
    List<String> findActiveNames();

    @Query("""
            select distinct c.givenName
              from Customer c
             where c.isActive = true
               and c.givenNameDetectable = true
             order by c.givenName
            """)
    List<String> findActiveGivenNames();
}
