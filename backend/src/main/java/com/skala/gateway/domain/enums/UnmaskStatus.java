package com.skala.gateway.domain.enums;

/** 마스킹 해제 요청의 상태 (D25). PENDING 대기 / APPROVED 해제 / REJECTED 유지 */
public enum UnmaskStatus {
    PENDING,
    APPROVED,
    REJECTED,
}
