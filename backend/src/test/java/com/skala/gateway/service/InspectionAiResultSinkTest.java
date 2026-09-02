package com.skala.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.gateway.DemoCases;
import com.skala.gateway.ai.AiAssessment;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.InspectionFinding;
import com.skala.gateway.domain.enums.AiStatus;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.FindingSource;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.domain.enums.PolicyCategory;
import com.skala.gateway.domain.enums.ReviewStatus;
import com.skala.gateway.domain.repository.InspectionFindingRepository;
import com.skala.gateway.domain.repository.InspectionRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 결과 영속화 (계약서 §4 인계 4).
 *
 * <p>{@code AiInspectionRunner}를 거치지 않고 콜백을 직접 호출한다 — Mock의 2.5초 지연은
 * 화면에서 증명할 것이지 테스트에서 기다릴 것이 아니다. 검증 대상은 "결과가 어떻게 저장되는가"다.
 */
@SpringBootTest
@Transactional
class InspectionAiResultSinkTest {

    private static final AiAssessment ASSESSMENT = new AiAssessment(
            List.of(new AiAssessment.RiskCandidate(
                    "CONF-CLIENT-PROJECT",
                    "CONFIDENTIAL",
                    "'A사 차세대 프로젝트 오픈 일정'이라는 서술이 계약 상대방과 미공개 일정을 동시에 특정함",
                    List.of(new AiAssessment.Evidence("고객사 NDA 목록 v3", "A사 — 비밀유지 2027.03까지")))),
            List.of("해당 일정이 대외 공개된 정보인지 확인 필요"),
            true);

    @Autowired
    private InspectionAiResultSink sink;

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private InspectionFindingRepository findingRepository;

    @Test
    @DisplayName("onCompleted — ai_result 저장, AI finding은 SUGGESTED로 태어난다")
    void onCompletedStoresAssessmentAndSuggestedFindings() {
        long inspectionId = pendingInspectionId();

        sink.onCompleted(inspectionId, ASSESSMENT);

        Inspection inspection = inspectionRepository.findById(inspectionId).orElseThrow();
        assertThat(inspection.getAiStatus()).isEqualTo(AiStatus.COMPLETED);
        assertThat(inspection.getAiResult()).isEqualTo(ASSESSMENT);
        assertThat(inspection.getCompletedAt()).isNotNull();

        // 사람이 확정하기 전이므로 판정은 그대로다. AI는 제안만 한다 (기획서 4장).
        assertThat(inspection.getFinalDecision()).isEqualTo(FinalDecision.PENDING);
        assertThat(inspection.getDecidedBy()).isNull();
        assertThat(inspection.getMessage().getStatus()).isEqualTo(MessageStatus.PENDING_REVIEW);

        List<InspectionFinding> findings =
                findingRepository.findByInspectionInspectionIdOrderBySourceAscFindingIdAsc(inspectionId);
        assertThat(findings).hasSize(2);
        // 정렬은 source 문자열 오름차순이라 'AI'가 'RULE'보다 앞에 온다. 순서에 기대지 않고 고른다.
        InspectionFinding ruleFinding = bySource(findings, FindingSource.RULE);
        InspectionFinding aiFinding = bySource(findings, FindingSource.AI);

        // 규칙 finding은 사람의 검토 대상이 아니므로 CONFIRMED 고정이다 (D6).
        assertThat(ruleFinding.getReviewStatus()).isEqualTo(ReviewStatus.CONFIRMED);
        assertThat(ruleFinding.getCode()).isEqualTo("CONF-CLIENT-01");
        assertThat(aiFinding.getCode()).isEqualTo("CONF-CLIENT-PROJECT");
        assertThat(aiFinding.getCategory()).isEqualTo(PolicyCategory.CONFIDENTIAL);
        assertThat(aiFinding.getReviewStatus()).isEqualTo(ReviewStatus.SUGGESTED);
        // AI 후보는 액션도 span도 정하지 않는다 (계약서 §1-5).
        assertThat(aiFinding.getAction()).isNull();
        assertThat(aiFinding.getSpanStart()).isNull();
        assertThat(aiFinding.getRule()).isNull();
        assertThat(aiFinding.getEvidence()).hasSize(1);
    }

    @Test
    @DisplayName("onFailed — FAILED로 기록하되 status는 PENDING_REVIEW를 유지한다")
    void onFailedKeepsPendingReview() {
        long inspectionId = pendingInspectionId();

        sink.onFailed(inspectionId, "RuntimeException: Mock AI 실패 시뮬레이션");

        Inspection inspection = inspectionRepository.findById(inspectionId).orElseThrow();
        assertThat(inspection.getAiStatus()).isEqualTo(AiStatus.FAILED);
        // FAILED도 결말이다. NULL로 두면 FE 폴링이 PENDING과 구분하지 못한다 (계약서 §1-5).
        assertThat(inspection.getCompletedAt()).isNotNull();
        // ALLOWED로 떨어뜨리면 검사되지 않은 프롬프트가 통과 기록으로 남는다 (9.5, UC-03 예외).
        assertThat(inspection.getMessage().getStatus()).isEqualTo(MessageStatus.PENDING_REVIEW);
        assertThat(inspection.getAiResult()).isNull();

        assertThat(findingRepository.findByInspectionInspectionIdOrderBySourceAscFindingIdAsc(inspectionId))
                .extracting(InspectionFinding::getSource)
                .containsExactly(FindingSource.RULE);
    }

    private static InspectionFinding bySource(List<InspectionFinding> findings, FindingSource source) {
        return findings.stream()
                .filter(finding -> finding.getSource() == source)
                .findFirst()
                .orElseThrow(() -> new AssertionError(source + " finding이 없습니다"));
    }

    /** Case B를 실제로 판정해 PENDING 상태의 inspection을 만든다. */
    private long pendingInspectionId() {
        MessageVerdictResponse verdict = inspectionService.submit(DemoCases.USER_SALES, DemoCases.CASE_B);
        assertThat(verdict.decision()).isEqualTo(FinalDecision.PENDING);
        return verdict.inspectionId();
    }
}
