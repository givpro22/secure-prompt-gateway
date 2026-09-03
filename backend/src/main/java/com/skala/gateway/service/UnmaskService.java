package com.skala.gateway.service;

import com.skala.gateway.api.ApiException;
import com.skala.gateway.api.dto.UnmaskDecisionBody;
import com.skala.gateway.api.dto.UnmaskRequestBody;
import com.skala.gateway.api.dto.UnmaskRequestDto;
import com.skala.gateway.domain.AppUser;
import com.skala.gateway.domain.Message;
import com.skala.gateway.domain.UnmaskRequest;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.domain.enums.UnmaskStatus;
import com.skala.gateway.domain.enums.UserRole;
import com.skala.gateway.domain.repository.AppUserRepository;
import com.skala.gateway.domain.repository.MessageRepository;
import com.skala.gateway.domain.repository.UnmaskRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마스킹 해제 검토 (D25).
 *
 * <p>규칙이 가린 이름을 사람이 도로 여는 유일한 경로다. 고객 명단과 이름이 같은 직원을
 * 쓰면 규칙은 그것을 {@code [고객명]}으로 가리는데, 문자열만 보는 규칙으로는 둘을 가를
 * 방법이 없다. 사람이 원문과 마스킹본을 비교해 정한다.
 *
 * <p>확정은 보안 담당자만 한다 — {@code ReviewService}와 같은 이유다 (0.5.1 D24). 여기서는
 * 그 결과가 남의 원문 열람이므로 더욱 그렇다.
 *
 * <h2>해제가 무엇을 바꾸지 않는가</h2>
 *
 * <p>APPROVED가 되어도 <b>이미 나간 마스킹본을 되돌리지 않는다.</b> 외부 서비스로 보낸
 * 것은 회수할 수 없고, 회수한 척하는 화면이 더 위험하다. 해제는 "이 건은 가릴 필요가
 * 없었다"는 판단을 기록으로 남기는 것이고, 직원은 그 판단을 근거로 원문을 다시 보낸다.
 * {@code message.submitted_text}를 손대지 않는 이유도 같다 — 감사 기록은 그 시점에 실제로
 * 나간 것을 담아야 한다.
 */
@Service
public class UnmaskService {

    private static final Logger log = LoggerFactory.getLogger(UnmaskService.class);

    private static final int MAX_REASON = 500;

    private final MessageRepository messageRepository;
    private final AppUserRepository appUserRepository;
    private final UnmaskRequestRepository unmaskRepository;

    public UnmaskService(MessageRepository messageRepository,
                         AppUserRepository appUserRepository,
                         UnmaskRequestRepository unmaskRepository) {
        this.messageRepository = messageRepository;
        this.appUserRepository = appUserRepository;
        this.unmaskRepository = unmaskRepository;
    }

    /** 직원이 자기 마스킹 건에 사유를 적어 올린다 */
    @Transactional
    public UnmaskRequestDto request(Long messageId, Long userId, UnmaskRequestBody body) {
        String reason = body == null || body.reason() == null ? "" : body.reason().trim();
        if (reason.isEmpty()) {
            throw ApiException.invalidRequest("해제 사유를 적어야 합니다.");
        }
        if (reason.length() > MAX_REASON) {
            throw ApiException.invalidRequest("해제 사유는 " + MAX_REASON + "자를 넘을 수 없습니다.");
        }

        AppUser requester = appUserRepository.findById(userId)
                .orElseThrow(() -> ApiException.invalidUser(userId));
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ApiException.messageNotFound(messageId));

        if (!message.getUser().getUserId().equals(userId)) {
            throw ApiException.notAuthor(messageId);
        }
        // 가린 것이 없으면 풀 것도 없다. 차단은 아예 나가지 않았으므로 해제의 대상이 아니다.
        if (message.getStatus() != MessageStatus.MASKED) {
            throw ApiException.notMasked(messageId);
        }
        if (unmaskRepository.existsByMessage_MessageId(messageId)) {
            throw ApiException.unmaskRequestExists(messageId);
        }

        UnmaskRequest saved = unmaskRepository.save(new UnmaskRequest(message, requester, reason));
        log.info("해제 요청 생성 requestId={} messageId={} requester={}",
                saved.getRequestId(), messageId, userId);
        return UnmaskRequestDto.forRequester(saved);
    }

    /**
     * 요청자가 자기 건의 처리 상태를 본다 (D25).
     *
     * <p>목록({@link #forConsole})은 담당자 전용이라 요청자가 결과를 알 길이 없었다. 확정이
     * 기록으로만 남고 요청한 사람에게 돌아가지 않으면 고리가 닫히지 않는다.
     *
     * <p>원문은 싣지 않는다. 자기 문장은 화면이 이미 들고 있고, 여기서 필요한 것은
     * 담당자가 무엇으로 정했는지다.
     */
    @Transactional(readOnly = true)
    public UnmaskRequestDto mine(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ApiException.messageNotFound(messageId));
        if (!message.getUser().getUserId().equals(userId)) {
            throw ApiException.notAuthor(messageId);
        }
        return unmaskRepository.findByMessage_MessageId(messageId)
                .map(UnmaskRequestDto::forRequester)
                .orElseThrow(() -> ApiException.unmaskRequestNotFound(messageId));
    }

    /**
     * 담당자 콘솔 목록. <b>원문이 실린다</b> — 요청 행이 있는 건에 한해서다.
     */
    @Transactional(readOnly = true)
    public Page<UnmaskRequestDto> forConsole(String status, Long userId, int page, int size) {
        requireReviewer(userId);
        UnmaskStatus filter = parseStatus(status);
        return unmaskRepository.findForConsole(filter, PageRequest.of(page, size))
                .map(UnmaskRequestDto::forReviewer);
    }

    /** 담당자가 해제/유지를 확정한다. 한 번 정해지면 다시 열지 않는다 */
    @Transactional
    public UnmaskRequestDto decide(Long requestId, Long userId, UnmaskDecisionBody body) {
        AppUser decider = requireReviewer(userId);
        if (body == null || body.approve() == null) {
            throw ApiException.invalidRequest("approve는 true 또는 false여야 합니다.");
        }

        UnmaskRequest request = unmaskRepository.findById(requestId)
                .orElseThrow(() -> ApiException.unmaskRequestNotFound(requestId));
        if (request.getStatus() != UnmaskStatus.PENDING) {
            throw ApiException.unmaskAlreadyDecided(requestId, request.getStatus());
        }

        request.decide(decider, body.approve(), body.note());
        log.info("해제 요청 확정 requestId={} status={} decider={}",
                requestId, request.getStatus(), userId);
        return UnmaskRequestDto.forReviewer(request);
    }

    private AppUser requireReviewer(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> ApiException.invalidUser(userId));
        if (user.getRole() != UserRole.SECURITY_ADMIN) {
            throw ApiException.notReviewer(userId);
        }
        return user;
    }

    private static UnmaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UnmaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalidParameter("status는 PENDING, APPROVED, REJECTED 중 하나여야 합니다.");
        }
    }
}
