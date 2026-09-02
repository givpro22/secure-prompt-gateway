package com.skala.gateway.domain.enums;

/**
 * inspection_finding.review_status — 기획서 6.2, 0.5 D6.
 *
 * <p>4값이다. DB DEFAULT는 {@link #SUGGESTED}이며 CHECK 제약으로 강제된다.
 * AI 후보는 SUGGESTED로 태어나 사람이 ACCEPTED/REJECTED로 확정한다.
 * 규칙 finding은 사람의 검토 대상이 아니므로 CONFIRMED 고정이며,
 * 화면에 ACCEPT/REJECT 버튼을 노출하지 않는다.
 */
public enum ReviewStatus {
    SUGGESTED, ACCEPTED, REJECTED, CONFIRMED
}
