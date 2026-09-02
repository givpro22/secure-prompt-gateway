package com.skala.gateway.service;

import com.skala.gateway.ai.AiInspectionRequest;
import com.skala.gateway.ai.AiInspectionRunner;
import com.skala.gateway.api.dto.InspectionDetailResponse;
import com.skala.gateway.api.dto.InspectionSummaryDto;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.api.dto.PageEnvelope;
import com.skala.gateway.domain.AppUser;
import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.InspectionFinding;
import com.skala.gateway.domain.Message;
import com.skala.gateway.domain.enums.AiStatus;
import com.skala.gateway.domain.enums.DecidedBy;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.domain.repository.AppUserRepository;
import com.skala.gateway.domain.repository.InspectionFindingRepository;
import com.skala.gateway.domain.repository.InspectionRepository;
import com.skala.gateway.domain.repository.InspectionSpecs;
import com.skala.gateway.domain.repository.MessageRepository;
import com.skala.gateway.engine.EngineVerdict;
import com.skala.gateway.engine.RuleEngine;
import com.skala.gateway.engine.RuleHit;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판정 파이프라인의 저장·응답 단계 (기획서 7.4-9)와 감사 조회.
 *
 * <p>매칭·억제·충돌 해결·마스킹은 {@link RuleEngine}이 하고, 이 클래스는 정책 로드부터
 * 영속화와 AI 인계까지를 트랜잭션으로 묶는다.
 */
@Service
public class InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);

    /** 감사 목록 페이지 크기 상한 (계약서 §1-6). 초과 요청은 거부하지 않고 절삭한다. */
    public static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository appUserRepository;
    private final MessageRepository messageRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionFindingRepository findingRepository;
    private final PolicyService policyService;
    private final RuleEngine ruleEngine;
    private final AiInspectionRunner aiInspectionRunner;
    private final int pollAfterMs;

    public InspectionService(AppUserRepository appUserRepository,
                             MessageRepository messageRepository,
                             InspectionRepository inspectionRepository,
                             InspectionFindingRepository findingRepository,
                             PolicyService policyService,
                             RuleEngine ruleEngine,
                             AiInspectionRunner aiInspectionRunner,
                             @Value("${gateway.polling.interval-ms}") int pollAfterMs) {
        this.appUserRepository = appUserRepository;
        this.messageRepository = messageRepository;
        this.inspectionRepository = inspectionRepository;
        this.findingRepository = findingRepository;
        this.policyService = policyService;
        this.ruleEngine = ruleEngine;
        this.aiInspectionRunner = aiInspectionRunner;
        this.pollAfterMs = pollAfterMs;
    }

    /**
     * 프롬프트 제출 → 규칙 판정 → 저장 → (REVIEW면) AI 인계.
     *
     * <p>판정에 따라 컨트롤러가 200 / 202 / 403으로 갈린다. 어느 쪽이든 응답 본문은 같은
     * 필드 집합이다 (계약서 §1-4).
     *
     * @param userId {@code X-User-Id}. 호출 전에 존재를 확인한다
     * @param text   원문. 여기서만 다루고 응답·AI 입력 어디에도 싣지 않는다
     */
    @Transactional
    public MessageVerdictResponse submit(Long userId, String text) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));

        // 1~3. 정책 로드와 스냅샷 (7.4-1~3)
        PolicyService.PolicyContext context = policyService.loadForDecision(user.getDepartment().getDeptId());

        // 4~8. 매칭 → 중첩 억제 → 충돌 해결 → 마스킹 (7.4-4~8)
        EngineVerdict verdict = ruleEngine.evaluate(text, context.rules());
        FinalDecision decision = verdict.decision();

        // 9. 저장. submitted_text가 NULL인 것은 BLOCK뿐이다 (0.5 D7) —
        //    BLOCK은 마스킹 자체를 실행하지 않아 만들어진 본문이 없다 (0.5 D5).
        Message message = messageRepository.save(
                new Message(user, text, decision == FinalDecision.BLOCK ? null : verdict.maskedText(), statusOf(decision)));

        boolean pending = decision == FinalDecision.PENDING;
        Inspection inspection = new Inspection(
                message,
                context.snapshot(),
                verdict.ruleResult(),
                pending ? AiStatus.PENDING : AiStatus.SKIPPED,
                decision,
                // 사람이 확정하기 전까지 REVIEW 건의 decidedBy는 비어 있다 (계약서 §1-4).
                pending ? null : DecidedBy.RULE);
        if (!pending) {
            inspection.setCompletedAt(OffsetDateTime.now());
        }
        inspectionRepository.save(inspection);

        for (RuleHit hit : verdict.findings()) {
            findingRepository.save(InspectionFinding.ofRule(
                    inspection, hit.rule(), hit.category(), hit.spanStart(), hit.spanEnd()));
        }

        if (pending) {
            scheduleAiInspection(inspection, user, verdict, context);
        }

        return MessageVerdictResponse.of(message, inspection, pending ? pollAfterMs : null);
    }

    /**
     * REVIEW 판정의 AI 인계 (계약서 §4 인계 3).
     *
     * <p>{@code schedule()}은 활성 트랜잭션이 있으면 <b>커밋 후로</b> 실행을 미룬다. 커밋 전에
     * {@code @Async} 메서드가 시작되면 새 스레드가 아직 커밋되지 않은 inspection을 조회해
     * 실패한다. {@code rule-engine-dev}가 {@code TransactionSynchronizationManager}를 직접
     * 다루지 않도록 계약 지점에서 한 번 막아 둔 것이므로 그대로 호출한다.
     *
     * <p>넘기는 것은 <b>마스킹 적용본</b>이다. 원문은 어떤 경로로도 이 요청에 들어가지 않는다 (9.3).
     */
    private void scheduleAiInspection(Inspection inspection, AppUser user,
                                      EngineVerdict verdict, PolicyService.PolicyContext context) {
        aiInspectionRunner.schedule(inspection.getInspectionId(), new AiInspectionRequest(
                verdict.maskedText(),
                user.getDepartment().getCode(),
                verdict.categories(),
                verdict.hits(),
                context.policyVersion()));
        log.debug("AI 검사 예약 inspectionId={} hits={}", inspection.getInspectionId(), verdict.hits().size());
    }

    /** {@code GET /api/v1/inspections/{id}} (계약서 §1-5). 폴링 겸용이다. */
    @Transactional(readOnly = true)
    public Optional<InspectionDetailResponse> detail(Long inspectionId) {
        return inspectionRepository.findDetailById(inspectionId)
                .map(inspection -> InspectionDetailResponse.of(inspection,
                        findingRepository.findByInspectionInspectionIdOrderBySourceAscFindingIdAsc(inspectionId)));
    }

    /**
     * {@code GET /api/v1/inspections} 감사 목록 (계약서 §1-6).
     *
     * <p>정렬은 {@code createdAt DESC} 고정이다. {@code ruleCount}는 페이지 단위로 한 번에 센다 —
     * 행마다 count 쿼리를 날리면 20행에 20쿼리다.
     */
    @Transactional(readOnly = true)
    public PageEnvelope<InspectionSummaryDto> list(Long deptId, MessageStatus status,
                                                   OffsetDateTime from, OffsetDateTime to,
                                                   int page, int size) {
        Page<Inspection> found = inspectionRepository.findAll(
                InspectionSpecs.of(deptId, status, from, to),
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), InspectionSpecs.DEFAULT_SORT));

        Map<Long, Long> ruleCounts = ruleCounts(found.getContent().stream()
                .map(Inspection::getInspectionId)
                .toList());

        return PageEnvelope.of(found, found.getContent().stream()
                .map(inspection -> InspectionSummaryDto.of(
                        inspection, ruleCounts.getOrDefault(inspection.getInspectionId(), 0L)))
                .toList());
    }

    /** {@code source='RULE'}인 finding만 센다. AI finding을 세면 안 된다 (5.4 목록 컬럼 정의). */
    private Map<Long, Long> ruleCounts(List<Long> inspectionIds) {
        if (inspectionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (InspectionFindingRepository.RuleCountRow row : findingRepository.countRuleFindings(inspectionIds)) {
            counts.put(row.getInspectionId(), row.getRuleCount());
        }
        return counts;
    }

    /** 7.5의 판정 ↔ {@code message.status} 대응. */
    private static MessageStatus statusOf(FinalDecision decision) {
        return switch (decision) {
            case ALLOW -> MessageStatus.ALLOWED;
            case MASK -> MessageStatus.MASKED;
            case BLOCK -> MessageStatus.BLOCKED;
            case PENDING -> MessageStatus.PENDING_REVIEW;
        };
    }
}
