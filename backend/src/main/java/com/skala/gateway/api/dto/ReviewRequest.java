package com.skala.gateway.api.dto;

/**
 * {@code PATCH /api/v1/inspections/{id}/findings/{findingId}} 요청 (기획서 8.4, 계약서 §1-7).
 *
 * <pre>{@code { "reviewStatus": "ACCEPTED", "comment": "NDA 대상 고객사 일정. 전송 불가" } }</pre>
 *
 * <p>{@code reviewStatus}를 enum이 아니라 {@code String}으로 받는다. enum으로 받으면
 * {@code "SUGGESTED"}는 Jackson을 통과해 서비스에서 걸리고 {@code "FOO"}는 Jackson이 먼저
 * 터져 두 경우의 400 메시지가 갈린다. 문자열로 받아 한 자리에서 판정하면 어떤 값이 와도
 * 같은 {@code INVALID_REQUEST} 봉투가 나간다.
 *
 * <p><b>{@code comment}는 저장하지 않는다.</b> {@code inspection_finding}에
 * {@code review_comment} 컬럼이 없다 (계약서 §2 대조 결과, {@code data-architect} 노트 4.6).
 * 응답에 에코하지도 않는다. 필드를 남겨 둔 것은 계약서와 Postman Example이 이 키를 싣기
 * 때문이며, 감사 증적에 코멘트가 필요해지면 {@code V3__*.sql}로 컬럼을 추가한다.
 */
public record ReviewRequest(String reviewStatus, String comment) {
}
