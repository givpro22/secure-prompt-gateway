package com.skala.gateway.api.dto;

/** {@code POST /messages/{id}/unmask-request} 요청 본문. 사유는 필수다 (D25) */
public record UnmaskRequestBody(String reason) {
}
