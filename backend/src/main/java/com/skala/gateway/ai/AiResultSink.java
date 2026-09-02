package com.skala.gateway.ai;

/**
 * AI 비동기 검사 결과를 받아 영속화하는 지점. 계약서 §4 인계 4.
 *
 * <p>{@code service} 패키지에서 {@code rule-engine-dev}가 구현한다. AI 패키지가 도메인·리포지토리에
 * 직접 의존하지 않도록 하는 경계이며, 덕분에 이 패키지는 영속 계층 없이도 컴파일되고 교체된다.
 *
 * <p>두 콜백 모두 <b>호출 스레드에 트랜잭션이 없는 상태</b>에서 실행된다. 구현체에
 * {@code @Transactional}을 붙일 것.
 *
 * <p>구현체가 없으면 결과는 저장되지 않고 ERROR 로그만 남는다. 애플리케이션 기동은 막지 않는다.
 */
public interface AiResultSink {

    /**
     * 검사 성공. 구현체가 할 일:
     * <ul>
     *   <li>{@code inspection.ai_result} = assessment JSON, {@code ai_status} = COMPLETED,
     *       {@code completed_at} = now()</li>
     *   <li>{@code riskCandidates[]} 각각을 {@code inspection_finding}으로 INSERT
     *       (source=AI, review_status=SUGGESTED, span/action = NULL)</li>
     * </ul>
     */
    void onCompleted(long inspectionId, AiAssessment assessment);

    /**
     * 검사 실패. 구현체가 할 일:
     * <ul>
     *   <li>{@code ai_status} = FAILED, {@code completed_at} = now()</li>
     *   <li><b>{@code message.status}는 PENDING_REVIEW를 유지한다.</b> ALLOWED로 떨어뜨리면
     *       검사되지 않은 프롬프트가 통과 기록으로 남는다 (기획서 9.5, UC-03 예외)</li>
     *   <li>finding은 만들지 않는다</li>
     * </ul>
     *
     * @param reason 실패 사유 요약. 원문·마스킹본을 넣지 않는다
     */
    void onFailed(long inspectionId, String reason);
}
