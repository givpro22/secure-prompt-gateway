package com.skala.gateway.service;

import com.skala.gateway.api.ApiException;
import com.skala.gateway.api.dto.ReviewRequest;
import com.skala.gateway.api.dto.ReviewResponse;
import com.skala.gateway.domain.AppUser;
import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.InspectionFinding;
import com.skala.gateway.domain.enums.DecidedBy;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.UserRole;
import com.skala.gateway.domain.enums.FindingSource;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.domain.enums.ReviewStatus;
import com.skala.gateway.domain.repository.AppUserRepository;
import com.skala.gateway.domain.repository.InspectionFindingRepository;
import com.skala.gateway.domain.repository.InspectionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 후보의 사람 확정과 최종 판정 재산출 (기획서 3.3 UC-06, 8.4, 계약서 §1-7).
 *
 * <p>이 클래스가 책임 경계(기획서 4장)의 마지막 조각이다. 규칙은 {@code RuleEngine}이 판정하고,
 * AI는 {@code AiResultSink}로 후보만 남기며 최종 판정을 건드리지 않는다. {@code PENDING}에서
 * {@code BLOCK}/{@code ALLOW}로 옮기고 {@code decided_by}에 {@code HUMAN}을 찍는 것은
 * <b>오직 이 경로</b>다.
 *
 * <h2>PATCH가 하지 않는 것 — {@code message.submitted_text} (0.5 D14)</h2>
 *
 * <p>ACCEPT로 {@code BLOCKED}가 되어도 <b>본문을 지우지 않는다.</b>
 * {@code submitted_text IS NULL}은 "차단됨"이 아니라 "마스킹본이 생성된 적 없음"을 뜻하며,
 * {@code Masker}를 아예 호출하지 않는 규칙 BLOCK 경로에서만 발생한다 (0.5 D5·D14).
 * REVIEW 경로는 마스킹을 실행한 뒤 AI를 호출하므로 본문이 이미 있고, 담당자는 그것을 보고
 * 확정한다. 확정 시점에 지우면 감사 시스템이 방금 판단한 근거를 스스로 파기하는 셈이라
 * "판단의 근거를 남긴다"는 서비스 핵심 가치(2.4)에 어긋나고, 데모 1:50에서 ACCEPT를 누르는
 * 순간 상세 패널 본문이 사라진다.
 *
 * <p>불변식: {@code decided_by='RULE' AND status='BLOCKED' ⇒ submitted_text IS NULL}.
 * 이 서비스는 {@code decided_by='HUMAN'}만 만들므로 불변식을 깨지 않는다.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** 요청으로 받을 수 있는 값 (계약서 §3). SUGGESTED·CONFIRMED는 사람이 지정할 수 없다. */
    private static final Set<ReviewStatus> ALLOWED_DECISIONS =
            Set.of(ReviewStatus.ACCEPTED, ReviewStatus.REJECTED);

    private final AppUserRepository appUserRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionFindingRepository findingRepository;

    public ReviewService(AppUserRepository appUserRepository,
                         InspectionRepository inspectionRepository,
                         InspectionFindingRepository findingRepository) {
        this.appUserRepository = appUserRepository;
        this.inspectionRepository = inspectionRepository;
        this.findingRepository = findingRepository;
    }

    /**
     * AI 후보 1건을 확정하고 해당 검사의 최종 판정을 재산출한다.
     *
     * <p>검사 순서가 응답 코드를 정한다 — 존재(404)를 먼저 보고, 그다음 확정 가능 여부(409),
     * 마지막이 값 검증(400)이다. 존재하지 않는 finding에 400을 주면 클라이언트가 "값이 틀렸나"를
     * 먼저 의심한다.
     *
     * @param userId {@code X-User-Id}. 그대로 {@code reviewed_by}에 기록한다 — 역할 검사
     *               <b>역할은 검사한다</b> — SECURITY_ADMIN이 아니면 403이다 (0.5.1 D24).
     *               인증은 여전히 없다. X-User-Id는 누구인지 말할 뿐 그 사람임을 증명하지 않는다
     */
    @Transactional
    public ReviewResponse review(Long inspectionId, Long findingId, Long userId, ReviewRequest request) {
        AppUser reviewer = appUserRepository.findById(userId)
                .orElseThrow(() -> ApiException.invalidUser(userId));

        // 확정은 보안 담당자만 (0.5.1 D24). 화면에서만 버튼을 가리면 경계가 주장에 그친다.
        if (reviewer.getRole() != UserRole.SECURITY_ADMIN) {
            throw ApiException.notReviewer(userId);
        }

        Inspection inspection = inspectionRepository.findDetailById(inspectionId)
                .orElseThrow(() -> ApiException.inspectionNotFound(inspectionId));

        InspectionFinding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> ApiException.findingNotFound(findingId));
        // 경로의 inspection에 속하지 않는 finding도 404다 (계약서 §1 에러 코드 표).
        if (!inspectionId.equals(finding.getInspection().getInspectionId())) {
            throw ApiException.findingNotFound(findingId);
        }

        // D13이 ALREADY_REVIEWED보다 앞이다. 규칙 finding은 항상 CONFIRMED라 두 조건이 겹치는데,
        // 이 순서가 아니면 "이미 확정됨"이라는 잘못된 사유가 나가 규칙 판정이 번복 가능한 것처럼 보인다.
        if (finding.getSource() == FindingSource.RULE
                || finding.getReviewStatus() == ReviewStatus.CONFIRMED) {
            throw ApiException.ruleFindingNotReviewable(findingId);
        }
        if (finding.getReviewStatus() != ReviewStatus.SUGGESTED) {
            throw ApiException.findingAlreadyReviewed(findingId, finding.getReviewStatus());
        }

        finding.review(parseDecision(request), reviewer, OffsetDateTime.now());
        recomputeFinalDecision(inspection);

        log.debug("AI 후보 확정 inspectionId={} findingId={} → {} (reviewer={}), 재산출 {}/{}",
                inspectionId, findingId, finding.getReviewStatus(), userId,
                inspection.getFinalDecision(), inspection.getMessage().getStatus());

        return ReviewResponse.of(finding, inspection);
    }

    /**
     * 최종 판정 재산출 (UC-06, 계약서 §1-7).
     *
     * <table>
     *   <caption>해당 inspection의 AI finding 상태별 결과</caption>
     *   <tr><th>AI finding</th><th>finalDecision</th><th>status</th><th>decidedBy</th></tr>
     *   <tr><td>ACCEPTED 1건 이상</td><td>BLOCK</td><td>BLOCKED</td><td>HUMAN</td></tr>
     *   <tr><td>전부 REJECTED</td><td>ALLOW</td><td>ALLOWED</td><td>HUMAN</td></tr>
     *   <tr><td>SUGGESTED가 남음</td><td>PENDING 유지</td><td>PENDING_REVIEW 유지</td><td>null 유지</td></tr>
     * </table>
     *
     * <p>ACCEPTED 우선이다. 한 건이라도 위반으로 확정되면 나머지가 기각이어도 전송할 수 없다.
     *
     * <p>{@code SUGGESTED}가 남아 있으면 <b>아무것도 건드리지 않는다.</b> 부분 확정 상태를
     * 중간 판정으로 옮기면 감사 목록에 "차단됨"이 뜬 뒤 남은 후보를 기각하면서 "허용"으로
     * 되돌아가고, 그 사이의 기록이 거짓이 된다.
     *
     * <p>{@code aiStatus}는 그대로 둔다 — 사람의 확정은 AI 검사의 상태가 아니고, FE 폴링은
     * {@code aiStatus}로만 끝난다 (0.5 D12).
     */
    private void recomputeFinalDecision(Inspection inspection) {
        List<InspectionFinding> aiFindings =
                findingRepository.findByInspectionIdRuleFirst(inspection.getInspectionId()).stream()
                        .filter(f -> f.getSource() == FindingSource.AI)
                        .toList();

        boolean anyAccepted = aiFindings.stream()
                .anyMatch(f -> f.getReviewStatus() == ReviewStatus.ACCEPTED);
        boolean anyPending = aiFindings.stream()
                .anyMatch(f -> f.getReviewStatus() == ReviewStatus.SUGGESTED);

        if (!anyAccepted && anyPending) {
            return;
        }

        FinalDecision decision = anyAccepted ? FinalDecision.BLOCK : FinalDecision.ALLOW;
        inspection.setFinalDecision(decision);
        inspection.setDecidedBy(DecidedBy.HUMAN);
        // completedAt은 AI 완료 시각에서 사람의 확정 시각으로 갱신된다 (계약서 §1-7).
        // 감사 화면의 "완료" 시각은 판정이 끝난 시점이고, REVIEW 건에서 그것은 사람이 누른 순간이다.
        inspection.setCompletedAt(OffsetDateTime.now());
        inspection.getMessage().setStatus(decision == FinalDecision.BLOCK
                ? MessageStatus.BLOCKED
                : MessageStatus.ALLOWED);
        // message.submitted_text는 어느 쪽으로도 건드리지 않는다 (0.5 D14). 클래스 javadoc 참조.
    }

    /** {@code ACCEPTED}/{@code REJECTED}만 허용한다. 그 밖의 값은 400 {@code INVALID_REQUEST}다. */
    private static ReviewStatus parseDecision(ReviewRequest request) {
        String raw = request == null ? null : request.reviewStatus();
        if (raw == null || raw.isBlank()) {
            throw ApiException.invalidRequest("reviewStatus는 필수입니다: ACCEPTED 또는 REJECTED");
        }
        ReviewStatus parsed;
        try {
            parsed = ReviewStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalidRequest("reviewStatus는 ACCEPTED 또는 REJECTED여야 합니다: " + raw);
        }
        if (!ALLOWED_DECISIONS.contains(parsed)) {
            throw ApiException.invalidRequest("reviewStatus는 ACCEPTED 또는 REJECTED여야 합니다: " + raw);
        }
        return parsed;
    }
}
