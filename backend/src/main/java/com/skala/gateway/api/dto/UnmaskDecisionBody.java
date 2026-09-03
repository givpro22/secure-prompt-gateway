package com.skala.gateway.api.dto;

/**
 * {@code POST /unmask-requests/{id}/decision} 요청 본문 (D25).
 *
 * @param approve {@code true}면 해제, {@code false}면 유지
 * @param note    담당자가 남기는 한 줄. 없으면 {@code null}
 */
public record UnmaskDecisionBody(Boolean approve, String note) {
}
