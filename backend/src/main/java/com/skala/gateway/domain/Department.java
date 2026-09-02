package com.skala.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 부서 마스터 (기획서 6.2).
 *
 * <p>code 4값: DEV, SALES, HR, INFOSEC. INFOSEC은 검토자 역할이라 {@code department_policy}
 * 매핑이 없고 감사 콘솔 부서 필터에도 넣지 않는다 (0.5 D2).
 */
@Entity
@Table(name = "department")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    protected Department() {
    }

    public Department(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getDeptId() {
        return deptId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
