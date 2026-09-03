package com.skala.gateway.ai;

import java.util.List;

/**
 * 규칙 엔진이 이미 보고 있는 것을 {@code ai} 패키지에 알려주는 경계면.
 *
 * <p>{@link AiResultSink}와 같은 방향의 인터페이스다 — {@code ai}가 도메인·리포지토리에
 * 직접 의존하지 않도록 값만 건네받는다. 구현체는 {@code service} 패키지에 있다.
 *
 * <p><b>왜 필요한가.</b> 골든셋에서 나온 오탐 6건 중 5건이 마스킹 자리표시자였다. 모델이
 * {@code [고객사]}·{@code [전화번호]}를 후보로 만들었고, 시스템 프롬프트에 "대괄호로 마스킹된
 * 자리는 후보로 만들지 않는다"고 명시했는데도 무시했다. 프롬프트로는 못 고치고 호출 자체를
 * 막아야 한다. 이 필터 하나로 오탐이 6에서 0이 됐다 (_workspace/05 §1-4).
 */
public interface RuleCoverageSource {

    /** 활성 정책의 {@code mask_label} 전체. 예: {@code [주민번호]}, {@code [전화번호]} */
    List<String> maskLabels();

    /** 활성 REGEX 규칙의 패턴 전체. 규칙이 잡을 문자열을 AI가 다시 볼 이유가 없다. */
    List<String> regexPatterns();
}
