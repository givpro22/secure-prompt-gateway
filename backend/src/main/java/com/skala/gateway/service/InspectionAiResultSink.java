package com.skala.gateway.service;

import com.skala.gateway.ai.AiAssessment;
import com.skala.gateway.ai.AiResultSink;
import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.InspectionFinding;
import com.skala.gateway.domain.enums.AiStatus;
import com.skala.gateway.domain.enums.PolicyCategory;
import com.skala.gateway.domain.repository.InspectionFindingRepository;
import com.skala.gateway.domain.repository.InspectionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 비동기 검사 결과의 영속화 (계약서 §4 인계 4).
 *
 * <p>{@code ai} 패키지가 도메인·리포지토리에 직접 의존하지 않도록 하는 경계면의 구현체다.
 * 두 콜백 모두 호출 스레드에 트랜잭션이 없는 상태에서 실행되므로 {@code @Transactional}을 붙인다.
 *
 * <p><b>여기서 최종 판정을 바꾸지 않는다.</b> AI 후보는 {@code SUGGESTED}로 태어나고
 * {@code final_decision}은 {@code PENDING}, {@code message.status}는 {@code PENDING_REVIEW}로
 * 남는다. 판정을 옮기는 것은 사람의 ACCEPT/REJECT뿐이다 (기획서 4장 책임 경계). 이 클래스가
 * 상태를 바꾸면 "AI는 제안만 한다"는 주장이 코드에서 깨진다.
 */
@Component
public class InspectionAiResultSink implements AiResultSink {

    private static final Logger log = LoggerFactory.getLogger(InspectionAiResultSink.class);

    /**
     * AI 후보의 카테고리. 9.4 스키마가 이 한 값으로 제한한다 — PII·SECRET은 규칙 엔진의
     * 영역이므로 AI가 후보로 만들지 않는다 (9.2 금지 조항).
     */
    private static final PolicyCategory DEFAULT_AI_CATEGORY = PolicyCategory.CONFIDENTIAL;

    private final InspectionRepository inspectionRepository;
    private final InspectionFindingRepository findingRepository;

    public InspectionAiResultSink(InspectionRepository inspectionRepository,
                                  InspectionFindingRepository findingRepository) {
        this.inspectionRepository = inspectionRepository;
        this.findingRepository = findingRepository;
    }

    @Override
    @Transactional
    public void onCompleted(long inspectionId, AiAssessment assessment) {
        Inspection inspection = inspectionRepository.findById(inspectionId).orElse(null);
        if (inspection == null) {
            log.error("AI 결과를 저장할 inspection이 없습니다. inspectionId={}", inspectionId);
            return;
        }

        inspection.setAiResult(assessment);
        inspection.setAiStatus(AiStatus.COMPLETED);
        inspection.setCompletedAt(OffsetDateTime.now());

        List<AiAssessment.RiskCandidate> candidates =
                assessment == null || assessment.riskCandidates() == null
                        ? List.of() : assessment.riskCandidates();
        for (AiAssessment.RiskCandidate candidate : candidates) {
            findingRepository.save(InspectionFinding.ofAi(
                    inspection,
                    candidate.code(),
                    category(candidate.category(), candidate.code()),
                    candidate.rationale(),
                    candidate.evidence()));
        }
        log.debug("AI 결과 저장 inspectionId={} 후보 {}건", inspectionId, candidates.size());
    }

    @Override
    @Transactional
    public void onFailed(long inspectionId, String reason) {
        Inspection inspection = inspectionRepository.findById(inspectionId).orElse(null);
        if (inspection == null) {
            log.error("AI 실패를 기록할 inspection이 없습니다. inspectionId={}", inspectionId);
            return;
        }

        inspection.setAiStatus(AiStatus.FAILED);
        // FAILED도 "결말이 났다"는 뜻이다. NULL로 두면 FE가 PENDING과 구분하지 못해
        // 폴링이 끝나지 않는다 (계약서 §1-5).
        inspection.setCompletedAt(OffsetDateTime.now());
        // message.status는 PENDING_REVIEW를 유지한다. ALLOWED로 떨어뜨리면 검사되지 않은
        // 프롬프트가 통과 기록으로 남는다 (9.5, UC-03 예외). finding도 만들지 않는다.
        log.warn("AI 검사 실패를 기록했습니다 inspectionId={} reason={} — 사람 검토로 폴백합니다",
                inspectionId, reason);
    }

    /**
     * 후보 카테고리 문자열 → enum. 스키마 밖의 값이 와도 후보를 버리지 않는다 — 후보를 잃는 것이
     * 카테고리가 틀린 것보다 나쁘다. 대신 경고를 남겨 픽스처·프롬프트 스키마 위반이 드러나게 한다.
     */
    private static PolicyCategory category(String raw, String code) {
        if (raw == null) {
            return DEFAULT_AI_CATEGORY;
        }
        try {
            return PolicyCategory.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("AI 후보 {}의 category가 9.4 스키마 밖의 값입니다: {} — {}로 저장합니다",
                    code, raw, DEFAULT_AI_CATEGORY);
            return DEFAULT_AI_CATEGORY;
        }
    }
}
