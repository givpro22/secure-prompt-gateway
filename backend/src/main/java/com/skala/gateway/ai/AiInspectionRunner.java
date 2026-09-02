package com.skala.gateway.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.skala.gateway.config.AsyncConfig;

/**
 * REVIEW 판정 이후의 비동기 검사 실행 (기획서 8.5). 계약서 §4 인계 3·4.
 *
 * <p>{@code rule-engine-dev}는 {@link #schedule(long, AiInspectionRequest)} 하나만 호출한다.
 * 트랜잭션 경계, 스레드 풀, 예외 분류는 전부 이 클래스가 처리한다.
 */
@Component
public class AiInspectionRunner {

    private static final Logger log = LoggerFactory.getLogger(AiInspectionRunner.class);

    private final AiInspector inspector;
    private final ObjectProvider<AiResultSink> sinkProvider;
    /** 자기 자신을 프록시로 참조한다. 같은 빈 안에서 직접 호출하면 {@code @Async}가 적용되지 않는다. */
    private final ObjectProvider<AiInspectionRunner> self;

    public AiInspectionRunner(AiInspector inspector,
                              ObjectProvider<AiResultSink> sinkProvider,
                              ObjectProvider<AiInspectionRunner> self) {
        this.inspector = inspector;
        this.sinkProvider = sinkProvider;
        this.self = self;
    }

    /**
     * 검사를 예약한다. <b>트랜잭션 안에서 호출해도 안전하다.</b>
     *
     * <p>트랜잭션이 활성이면 커밋 후로 실행을 미룬다. 커밋 전에 {@code @Async} 메서드가 시작되면
     * 새 스레드가 아직 커밋되지 않은 inspection을 조회해 실패한다. {@code @Async}에서 가장 흔한
     * 함정이며, 각자 조심하기로 하는 대신 계약 지점에서 한 번 막는다.
     *
     * @param inspectionId 저장이 끝난 inspection의 id
     * @param request      마스킹 적용본과 판정 맥락. 원문을 넣지 않는다
     */
    public void schedule(long inspectionId, AiInspectionRequest request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    self.getObject().runAsync(inspectionId, request);
                }
            });
        } else {
            self.getObject().runAsync(inspectionId, request);
        }
    }

    /**
     * 실제 검사. {@link AsyncConfig#AI_EXECUTOR} 풀에서 실행된다.
     *
     * <p>직접 호출하지 말고 {@link #schedule(long, AiInspectionRequest)}를 쓸 것.
     * 어떤 예외가 나오든 {@link AiResultSink#onFailed}로 귀결시켜 inspection이 PENDING에
     * 영구히 남지 않게 한다. FE 폴링은 유한하므로 결말이 나지 않으면 화면이 멈춘 것처럼 보인다.
     */
    @Async(AsyncConfig.AI_EXECUTOR)
    public void runAsync(long inspectionId, AiInspectionRequest request) {
        try {
            AiAssessment assessment = inspector.inspect(request);
            sink().onCompleted(inspectionId, assessment);
            log.debug("AI 검사 완료 inspectionId={} 후보 {}건",
                    inspectionId, assessment.riskCandidates() == null ? 0 : assessment.riskCandidates().size());
        } catch (IllegalStateException e) {
            // hits가 비었는데 호출된 경우 등 계약 위반. 규칙 엔진 버그이므로 ERROR로 남긴다.
            log.error("AI 검사 계약 위반 inspectionId={} — 규칙 엔진이 REVIEW 판정 없이 호출했는지 확인하십시오",
                    inspectionId, e);
            fail(inspectionId, e);
        } catch (RuntimeException e) {
            // AI 실패. 설계상 예상된 경로이며 사람 검토로 폴백된다 (기획서 9.5).
            log.warn("AI 검사 실패 inspectionId={} — 사람 검토로 폴백합니다", inspectionId, e);
            fail(inspectionId, e);
        }
    }

    private void fail(long inspectionId, RuntimeException cause) {
        try {
            sink().onFailed(inspectionId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException sinkFailure) {
            log.error("AI 실패 기록마저 실패했습니다 inspectionId={}", inspectionId, sinkFailure);
        }
    }

    /**
     * 구현체가 없어도 애플리케이션이 기동하도록 지연 조회한다. 생성자에서 요구하면 서비스 계층이
     * 준비되기 전까지 부팅이 막힌다.
     */
    private AiResultSink sink() {
        AiResultSink sink = sinkProvider.getIfAvailable();
        if (sink == null) {
            return NoOpSink.INSTANCE;
        }
        return sink;
    }

    /** {@link AiResultSink} 구현체가 아직 없을 때. 결과를 버리되 조용히 버리지는 않는다. */
    private enum NoOpSink implements AiResultSink {
        INSTANCE;

        @Override
        public void onCompleted(long inspectionId, AiAssessment assessment) {
            log.error("AiResultSink 구현체가 없어 AI 결과를 저장하지 못했습니다. inspectionId={} "
                    + "(service 패키지에 AiResultSink 구현이 필요합니다 — 계약서 §4 인계 4)", inspectionId);
        }

        @Override
        public void onFailed(long inspectionId, String reason) {
            log.error("AiResultSink 구현체가 없어 AI 실패를 기록하지 못했습니다. inspectionId={} reason={}",
                    inspectionId, reason);
        }
    }
}
