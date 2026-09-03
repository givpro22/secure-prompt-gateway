package com.skala.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.gateway.DemoCases;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.domain.enums.InspectionPhase;
import com.skala.gateway.domain.repository.InspectionRepository;
import com.skala.gateway.domain.repository.MessageRepository;
import com.skala.gateway.service.InspectionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출력 검사 (UC-08, 17장 3단계).
 *
 * <p>고정하는 것은 <b>같은 파이프라인이 두 번 돈다</b>는 것이다. 정책·규칙·마스킹·감사
 * 기록이 입력과 한 줄도 다르지 않고, 갈리는 것은 {@code phase}와 텍스트가 들어가는
 * 칸뿐이다. 그래서 한 메시지에 검사가 둘 붙는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResponseInspectionApiTest {

    private static final long EMPLOYEE = DemoCases.USER_HR;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Test
    @DisplayName("답변에 개인정보가 섞이면 마스킹한다 — 나가는 것만 보던 게이트웨이가 돌아오는 것도 본다")
    void masksSensitiveResponse() throws Exception {
        long messageId = sentMessage();

        JsonNode body = json(inspect(messageId, EMPLOYEE,
                "확인했습니다. 담당자 연락처는 010-1234-5678 이고 회신은 hong@example.com 으로 주세요."));

        assertThat(body.path("decision").asText()).isEqualTo("MASK");
        assertThat(body.path("inspectedText").asText())
                .contains("[전화번호]")
                .contains("[이메일]")
                .doesNotContain("010-1234-5678");
        // 답변 원문은 돌려주지 않는다. 직원은 이미 갖고 있다.
        assertThat(body.has("responseText")).isFalse();
    }

    @Test
    @DisplayName("한 메시지에 검사가 둘 붙는다 — INPUT과 OUTPUT")
    void oneMessageCarriesTwoInspections() throws Exception {
        long messageId = sentMessage();
        inspect(messageId, EMPLOYEE, "정리해 드렸습니다. 추가로 필요한 것이 있을까요?");

        List<InspectionPhase> phases = inspectionRepository.findAll().stream()
                .filter(i -> i.getMessage().getMessageId().equals(messageId))
                .map(i -> i.getPhase())
                .toList();

        assertThat(phases).containsExactlyInAnyOrder(InspectionPhase.INPUT, InspectionPhase.OUTPUT);
    }

    @Test
    @DisplayName("깨끗한 답변은 그대로 통과한다")
    void cleanResponsePasses() throws Exception {
        long messageId = sentMessage();

        JsonNode body = json(inspect(messageId, EMPLOYEE, "정리해 드렸습니다. 표로 옮겨 드릴까요?"));

        assertThat(body.path("decision").asText()).isEqualTo("ALLOW");
        assertThat(body.path("ruleResult").path("matches")).isEmpty();
    }

    @Test
    @DisplayName("차단된 답변은 403이고 본문이 남지 않는다 — 마스킹을 실행하지 않으므로 (D5)")
    void blockedResponseHasNoBody() throws Exception {
        long messageId = sentMessage();

        MvcResult result = inspect(messageId, EMPLOYEE,
                "접속 정보는 DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 입니다.");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        JsonNode body = json(result);
        assertThat(body.path("decision").asText()).isEqualTo("BLOCK");
        assertThat(body.path("inspectedText").isNull()).isTrue();
        assertThat(messageRepository.findById(messageId).orElseThrow().getResponseMasked()).isNull();
        // 원문은 감사 목적으로 남는다. 무엇이 걸렸는지 나중에 볼 수 없으면 기록이 아니다.
        assertThat(messageRepository.findById(messageId).orElseThrow().getResponseText()).isNotNull();
    }

    @Test
    @DisplayName("전송되지 않은 프롬프트에는 검사할 답변이 없다 — 409")
    void blockedPromptHasNoResponse() throws Exception {
        MessageVerdictResponse blocked = inspectionService.submit(EMPLOYEE, DemoCases.CASE_A);
        assertThat(blocked.decision().name()).isEqualTo("BLOCK");

        MvcResult result = inspect(blocked.messageId(), EMPLOYEE, "답변입니다.");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("NOT_SENT");
    }

    @Test
    @DisplayName("남의 대화에 답변을 끼워 넣을 수 없다 — 403")
    void onlyAuthorMayInspect() throws Exception {
        long messageId = sentMessage();

        MvcResult result = inspect(messageId, DemoCases.USER_SALES, "답변입니다.");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(result)).isEqualTo("FORBIDDEN_NOT_AUTHOR");
    }

    @Test
    @DisplayName("답변은 한 번만 검사한다 — 409")
    void responseIsInspectedOnce() throws Exception {
        long messageId = sentMessage();
        inspect(messageId, EMPLOYEE, "첫 번째 답변입니다.");

        MvcResult result = inspect(messageId, EMPLOYEE, "두 번째 답변입니다.");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("RESPONSE_ALREADY_INSPECTED");
    }

    @Test
    @DisplayName("빈 답변은 400")
    void emptyResponseIsRejected() throws Exception {
        long messageId = sentMessage();

        MvcResult result = inspect(messageId, EMPLOYEE, "   ");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("INVALID_REQUEST");
    }

    // --- 도우미 -------------------------------------------------------------

    /** 실제로 나간 프롬프트. 출력 검사는 여기에만 붙는다 */
    private long sentMessage() {
        MessageVerdictResponse verdict = inspectionService.submit(EMPLOYEE, "3분기 릴리스 일정 정리해줘");
        assertThat(verdict.submittedText()).isNotNull();
        return verdict.messageId();
    }

    private MvcResult inspect(long messageId, long userId, String text) throws Exception {
        return mockMvc.perform(post("/api/v1/messages/{id}/response", messageId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Body(text))))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String errorCode(MvcResult result) throws Exception {
        return json(result).path("code").asText();
    }

    private record Body(String text) {
    }
}
