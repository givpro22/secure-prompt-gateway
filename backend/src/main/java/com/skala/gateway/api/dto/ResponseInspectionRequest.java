package com.skala.gateway.api.dto;

/** {@code POST /messages/{id}/response} 요청 본문. 모델이 돌려준 답변 그대로다 (UC-08) */
public record ResponseInspectionRequest(String text) {
}
