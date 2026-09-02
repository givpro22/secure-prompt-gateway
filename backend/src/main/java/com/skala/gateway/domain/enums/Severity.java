package com.skala.gateway.domain.enums;

/**
 * policy_rule.severity — 기획서 6.2, 7.2.
 *
 * <p>선언 순서가 곧 우선순위다. 기획서 7.4-4가 "severity 내림차순으로 규칙을 실행"하라고
 * 하므로 {@code Comparator.comparing(PolicyRule::getSeverity)}가 그대로 원하는 순서를 준다.
 * 상수 순서를 바꾸면 규칙 실행 순서가 조용히 바뀐다.
 */
public enum Severity {
    HIGH, MEDIUM, LOW
}
