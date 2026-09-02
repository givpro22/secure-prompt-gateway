package com.skala.gateway.domain.enums;

/**
 * policy.category — 기획서 6.2, 7.1
 *
 * <p>{@code EMBARGO}는 성격이 다르다. 나머지 셋은 <b>정보가 민감해서</b> 통제하지만 엠바고는
 * <b>아직 때가 아니라서</b> 통제한다. 같은 문장이 해제일 다음 날에는 그냥 통과한다.
 * {@code CONFIDENTIAL}에 얹지 않고 값을 나눈 이유는 화면에 "기밀"로 뜨면 그 구분이 사라지기
 * 때문이다.
 */
public enum PolicyCategory {
    PII, SECRET, CONFIDENTIAL, EMBARGO
}
