package com.skala.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.gateway.DemoCases;
import com.skala.gateway.ai.AnswerClient;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.domain.enums.MessageStatus;
import com.skala.gateway.domain.repository.MessageRepository;
import com.skala.gateway.service.InspectionService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 답변 유출 검사 (UC-08 후단). 규칙이 통과시킨 답변을 한 번 더 보고, 의심이 있으면 검토
 * 대기로 돌려 담당자에게 넘긴다. 확정은 사람이 한다 — 여기서도 4장의 경계가 그대로다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnswerLeakApiTest {

    private static final long DEV = DemoCases.USER_DEV;
    private static final long ADMIN = DemoCases.USER_ADMIN;

    /** 사내 IP가 들어 있어 [내부IP]로 마스킹되는 코드 질문. 개인정보는 없다 */
    private static final String CODE_PROMPT = """
            결제 재시도에서 NPE 나는데 봐줘.
            RetryPolicy policy = config.getRetryPolicy();
            int max = policy.getMaxAttempts();
            client.connect("10.0.3.21", 5432);
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired InspectionService inspectionService;
    @Autowired MessageRepository messageRepository;
    @MockitoBean AnswerClient claude;

    @Test
    @DisplayName("코드 되돌림 — 답변이 프롬프트 코드를 그대로 담으면 검토 대기가 되고 담당자 제안이 붙는다")
    void codeEchoGoesToReview() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.ask(anyString())).thenReturn(new AnswerClient.Answered(
                "policy가 null일 수 있습니다. 이렇게 고치세요.\n"
                + "RetryPolicy policy = config.getRetryPolicy();\n"
                + "if (policy == null) policy = RetryPolicy.defaults();\n"
                + "int max = policy.getMaxAttempts();", "gemini"));
        long messageId = sent(CODE_PROMPT);

        MvcResult result = answer(messageId, DEV);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        JsonNode body = json(result);
        assertThat(body.path("decision").asText()).isEqualTo("PENDING");
        assertThat(body.path("aiStatus").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("decidedBy").isNull()).isTrue();

        JsonNode detail = json(mockMvc.perform(get("/api/v1/inspections/{id}", body.path("inspectionId").asLong())
                .header("X-User-Id", ADMIN)).andReturn());
        assertThat(detail.path("phase").asText()).isEqualTo("OUTPUT");
        assertThat(detail.path("aiAssessment").path("reviewRequired").asBoolean()).isTrue();
        assertThat(detail.path("findings").toString()).contains("LEAK-CODE-ECHO");
        // 담당자가 보는 본문은 답변이지 프롬프트가 아니다.
        assertThat(detail.path("submittedText").asText()).contains("policy == null");
    }

    @Test
    @DisplayName("가린 값 되살림 — 프롬프트에서 가린 번호가 답변에 나타나면 검토 대기")
    void reconstructionGoesToReview() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.ask(anyString())).thenReturn(new AnswerClient.Answered(
                "확인했습니다. 010-1234-5678 로 연락드리겠습니다.", "gemini"));
        long messageId = sent("서지윤 대리 연락처 010-1234-5678 확인 부탁");

        JsonNode body = json(answer(messageId, DEV));

        assertThat(body.path("decision").asText()).isEqualTo("PENDING");
        JsonNode detail = json(mockMvc.perform(get("/api/v1/inspections/{id}", body.path("inspectionId").asLong())
                .header("X-User-Id", ADMIN)).andReturn());
        assertThat(detail.path("findings").toString()).contains("LEAK-RECONSTRUCT");
    }

    @Test
    @DisplayName("깨끗한 답변은 그대로 통과 — 유출 검사가 거짓 양성을 내지 않는다")
    void cleanAnswerPasses() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.ask(anyString())).thenReturn(new AnswerClient.Answered("정리해 드렸습니다. 더 필요하면 말씀해 주세요.", "gemini"));
        long messageId = sent("3분기 릴리스 일정 정리해줘");

        JsonNode body = json(answer(messageId, DEV));

        assertThat(body.path("decision").asText()).isEqualTo("ALLOW");
        assertThat(body.path("aiStatus").asText()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("담당자 목록 — phase=OUTPUT & 검토 대기 필터에 답변 검사가 잡힌다 (종이 이걸 폴링한다)")
    void pendingOutputAppearsInConsoleFilter() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.ask(anyString())).thenReturn(new AnswerClient.Answered(
                "RetryPolicy policy = config.getRetryPolicy();\nint max = policy.getMaxAttempts();", "gemini"));
        long messageId = sent(CODE_PROMPT);
        long inspectionId = json(answer(messageId, DEV)).path("inspectionId").asLong();

        JsonNode page = json(mockMvc.perform(get("/api/v1/inspections")
                .param("phase", "OUTPUT").param("status", "PENDING_REVIEW")
                .header("X-User-Id", ADMIN)).andReturn());

        assertThat(page.path("items").toString()).contains("\"inspectionId\":" + inspectionId);
        JsonNode row = null;
        for (JsonNode r : page.path("items")) if (r.path("inspectionId").asLong() == inspectionId) row = r;
        assertThat(row).isNotNull();
        assertThat(row.path("phase").asText()).isEqualTo("OUTPUT");
        assertThat(row.path("status").asText()).isEqualTo("PENDING_REVIEW");
        // 프롬프트 행은 여전히 MASKED다 — 답변 검사가 프롬프트 판정을 바꾸지 않는다.
        assertThat(messageRepository.findById(messageId).orElseThrow().getStatus()).isEqualTo(MessageStatus.MASKED);
    }

    @Test
    @DisplayName("담당자 확정 — 답변 검사를 ACCEPT해도 프롬프트 상태는 그대로다")
    void reviewingOutputDoesNotTouchMessageStatus() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.ask(anyString())).thenReturn(new AnswerClient.Answered(
                "RetryPolicy policy = config.getRetryPolicy();\nint max = policy.getMaxAttempts();", "gemini"));
        long messageId = sent(CODE_PROMPT);
        JsonNode body = json(answer(messageId, DEV));
        long inspectionId = body.path("inspectionId").asLong();
        JsonNode detail = json(mockMvc.perform(get("/api/v1/inspections/{id}", inspectionId).header("X-User-Id", ADMIN)).andReturn());
        long findingId = -1;
        for (JsonNode f : detail.path("findings")) if ("AI".equals(f.path("source").asText())) findingId = f.path("findingId").asLong();
        assertThat(findingId).isPositive();

        JsonNode reviewed = json(mockMvc.perform(patch("/api/v1/inspections/{id}/findings/{fid}", inspectionId, findingId)
                .header("X-User-Id", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewStatus\":\"ACCEPTED\"}")).andReturn());

        assertThat(reviewed.path("inspection").path("finalDecision").asText()).isEqualTo("BLOCK");
        assertThat(reviewed.path("inspection").path("decidedBy").asText()).isEqualTo("HUMAN");
        assertThat(messageRepository.findById(messageId).orElseThrow().getStatus()).isEqualTo(MessageStatus.MASKED);
    }

    // --- 도우미 ---
    private long sent(String text) {
        MessageVerdictResponse v = inspectionService.submit(DEV, text);
        assertThat(v.submittedText()).isNotNull();
        return v.messageId();
    }
    private MvcResult answer(long messageId, long userId) throws Exception {
        return mockMvc.perform(post("/api/v1/messages/{id}/answer", messageId).header("X-User-Id", userId)).andReturn();
    }
    private JsonNode json(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
