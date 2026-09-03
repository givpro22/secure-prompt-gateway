package com.skala.gateway.service;

import com.skala.gateway.ai.AnswerClient;
import com.skala.gateway.api.ApiException;
import com.skala.gateway.api.dto.ResponseVerdictResponse;
import com.skala.gateway.domain.Message;
import com.skala.gateway.domain.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 마스킹본을 Claude에 보내 답변을 받고, 그 답변을 출력 검사에 넘긴다 (UC-08 한 바퀴).
 *
 * <pre>
 *   프롬프트 → 입력 검사 → 마스킹본 → [Claude] → 답변 → 출력 검사 → 화면
 * </pre>
 *
 * <p>여기가 게이트웨이의 존재 이유를 한 줄로 보여주는 자리다. Claude에 가는 것은
 * {@code message.submitted_text}이고, 원문은 이 클래스에 들어오지도 않는다. 돌아온
 * 답변은 {@link InspectionService#inspectResponse}로 간다 — 입력과 같은 규칙, 같은
 * 마스킹, 같은 감사 기록이다.
 *
 * <p>순서를 지킨다. 답변을 받기 전에 조건(작성자, 전송 여부, 중복)을 먼저 본다 —
 * 어차피 거절할 요청으로 외부 호출 비용을 쓰지 않는다.
 */
@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private final AnswerClient client;
    private final MessageRepository messageRepository;
    private final InspectionService inspectionService;

    public AnswerService(AnswerClient client, MessageRepository messageRepository,
                         InspectionService inspectionService) {
        this.client = client;
        this.messageRepository = messageRepository;
        this.inspectionService = inspectionService;
    }

    public boolean enabled() {
        return client.enabled();
    }

    public String providerName() {
        return client.providerName();
    }

    public ResponseVerdictResponse answer(Long messageId, Long userId) {
        if (!client.enabled()) {
            throw ApiException.answerUnavailable();
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ApiException.messageNotFound(messageId));
        if (!message.getUser().getUserId().equals(userId)) {
            throw ApiException.notAuthor(messageId);
        }
        if (message.getSubmittedText() == null) {
            throw ApiException.notSent(messageId);
        }
        if (message.getResponseText() != null) {
            throw ApiException.responseAlreadyInspected(messageId);
        }

        // 나가는 것은 마스킹본이다. getOriginalText()는 여기서 부르지 않는다.
        AnswerClient.Result result;
        try {
            result = client.ask(message.getSubmittedText());
        } catch (AnswerClient.AnswerCallException e) {
            log.warn("답변 호출 실패 messageId={} : {}", messageId, e.getMessage());
            throw ApiException.answerFailed(e.getMessage());
        }

        if (result instanceof AnswerClient.Refused refused) {
            throw ApiException.answerRefused(refused.explanation());
        }
        String text = ((AnswerClient.Answered) result).text();
        if (text.isBlank()) {
            throw ApiException.answerFailed("빈 답변이 돌아왔습니다.");
        }

        // 받은 답변은 사람이 붙여넣은 것과 똑같이 취급한다. 별도 경로가 없다.
        return inspectionService.inspectResponse(messageId, userId, text);
    }
}
