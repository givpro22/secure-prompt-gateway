package com.skala.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 고객 명단 (0.5.1 D23).
 *
 * <p>고객 명단을 사내 DB에 두는 것은 정상이다. 문제는 그것이 외부 LLM으로 나가는 순간이다.
 * 게이트웨이가 명단을 이미 알고 있으므로 나가려는 자리에서 막는다.
 *
 * <p>명단을 {@code policy_rule.pattern}에 박지 않고 테이블로 분리한 이유는, 조직이 바뀔 때
 * 필요한 작업이 "코드 수정"이 아니라 "명단 적재"가 되게 하기 위함이다. 실제 도입에서는
 * CRM 동기화 배치가 이 테이블을 채운다.
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 성을 뗀 부분. 단독 등장은 동음 명사와 겹칠 수 있어 마스킹까지 가지 않는다. */
    @Column(name = "given_name", nullable = false, length = 50)
    private String givenName;

    @Column(name = "company", length = 100)
    private String company;

    @Column(name = "source", length = 100)
    private String source;

    /**
     * 이름 단독 탐지 대상 여부 (0.5.1 D23). 일반 명사와 겹치는 이름을 뺀다 —
     * `재현`은 재현성, `인체`는 人體다. 전체 이름 마스킹과는 무관하다.
     */
    @Column(name = "given_name_detectable", nullable = false)
    private Boolean givenNameDetectable = Boolean.TRUE;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Customer() {
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getGivenName() {
        return givenName;
    }

    public Boolean getGivenNameDetectable() {
        return givenNameDetectable;
    }

    public String getCompany() {
        return company;
    }

    public String getSource() {
        return source;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
