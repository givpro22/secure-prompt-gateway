package com.skala.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.gateway.DemoCases;
import com.skala.gateway.ai.AnswerClient;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.domain.repository.MessageRepository;
import com.skala.gateway.service.InspectionService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 답변 받기 (UC-08 한 바퀴). Claude 호출은 모킹한다 — 테스트가 외부로 나가지 않고 돈을
 * 쓰지 않는다. 고정하는 것은 세 가지다.
 *
 * <ul>
 *   <li><b>나가는 것은 마스킹본이다.</b> 원문이 클라이언트에 닿으면 게이트웨이가 아니다</li>
 *   <li>돌아온 답변은 사람이 붙여넣은 것과 같은 출력 검사를 탄다</li>
 *   <li>키가 없으면 503이고, 조건에 안 맞는 요청은 호출 전에 거절된다</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnswerApiTest {

    private static final long EMPLOYEE = DemoCases.USER_HR;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private MessageRepository messageRepository;

    @MockitoBean
    private AnswerClient claude;

    @Test
    @DisplayName("Claude에 가는 것은 마스킹본이다 — 원문의 이름과 번호가 호출에 실리지 않는다")
    void sendsMaskedTextOnly() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.ask(anyString())).thenReturn(new AnswerClient.Answered("정리해 드렸습니다.", "claude-opus-5"));

        MessageVerdictResponse input = inspectionService.submit(EMPLOYEE, "서지윤 대리 연락처 010-1234-5678 확인 부탁");
        assertThat(input.decision().name()).isEqualTo("MASK");

        answer(input.messageId(), EMPLOYEE);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(claude).ask(sent.capture());
        assertThat(sent.getValue())
                .isEqualTo("[고객명] 대리 연락처 [전화번호] 확인 부탁")
                .doesNotContain("서지윤")
                .doesNotContain("010-1234-5678");
    }

    @Test
    @DisplayName("돌아온 답변은 출력 검사를 탄다 — 답변에 섞인 번호가 가려진다")
    void answerGoesThroughOutputInspection() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.ask(anyString())).thenReturn(new AnswerClient.Answered(
                "담당자 연락처는 010-9999-8888 입니다. 회신은 lee@example.com 으로 주세요.", "claude-opus-5"));
        long messageId = sentMessage();

        JsonNode body = json(answer(messageId, EMPLOYEE));

        assertThat(body.path("decision").asText()).isEqualTo("MASK");
        assertThat(body.path("inspectedText").asText())
                .contains("[전화번호]").contains("[이메일]")
                .doesNotContain("010-9999-8888");
        // 감사용 원문은 남고 화면용은 가려진다.
        var saved = messageRepository.findById(messageId).orElseThrow();
        assertThat(saved.getResponseText()).contains("010-9999-8888");
        assertThat(saved.getResponseMasked()).contains("[전화번호]");
    }

    @Test
    @DisplayName("키가 없으면 503 ANSWER_UNAVAILABLE — 화면은 붙여넣기로 물러난다")
    void unavailableWithoutKey() throws Exception {
        when(claude.enabled()).thenReturn(false);
        long messageId = sentMessage();

        MvcResult result = answer(messageId, EMPLOYEE);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(errorCode(result)).isEqualTo("ANSWER_UNAVAILABLE");
        verify(claude, never()).ask(anyString());
    }

    @Test
    @DisplayName("가용 여부 조회 — 키 유무를 그대로 돌려준다")
    void availabilityEndpoint() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.providerName()).thenReturn("테스트 제공자");

        MvcResult result = mockMvc.perform(get("/api/v1/messages/answer/available")
                .header("X-User-Id", EMPLOYEE)).andReturn();

        JsonNode body = json(result);
        assertThat(body.path("available").asBoolean()).isTrue();
        assertThat(body.path("provider").asText()).isEqualTo("테스트 제공자");
    }

    @Test
    @DisplayName("제공자 이름이 비어도 가용 여부 응답은 나간다 — Map.of의 null 거부에 걸리지 않는다")
    void availabilityWithoutProviderName() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.providerName()).thenReturn(null);

        MvcResult result = mockMvc.perform(get("/api/v1/messages/answer/available")
                .header("X-User-Id", EMPLOYEE)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).path("provider").asText()).isEmpty();
    }

    @Test
    @DisplayName("모델이 거절하면 422 ANSWER_REFUSED — 오류가 아니라 결과다")
    void refusalIsSurfaced() throws Exception {
        when(claude.enabled()).thenReturn(true);
        when(claude.ask(anyString())).thenReturn(new AnswerClient.Refused("policy"));
        long messageId = sentMessage();

        MvcResult result = answer(messageId, EMPLOYEE);

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(errorCode(result)).isEqualTo("ANSWER_REFUSED");
    }

    @Test
    @DisplayName("차단된 프롬프트는 호출 전에 409 — 외부 호출 비용을 쓰지 않는다")
    void blockedPromptNeverCallsClaude() throws Exception {
        when(claude.enabled()).thenReturn(true);
        MessageVerdictResponse blocked = inspectionService.submit(EMPLOYEE, DemoCases.CASE_A);

        MvcResult result = answer(blocked.messageId(), EMPLOYEE);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("NOT_SENT");
        verify(claude, never()).ask(anyString());
    }

    @Test
    @DisplayName("남의 대화에는 답변을 받을 수 없다 — 403, 호출 없음")
    void onlyAuthor() throws Exception {
        when(claude.enabled()).thenReturn(true);
        long messageId = sentMessage();

        MvcResult result = answer(messageId, DemoCases.USER_SALES);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        verify(claude, never()).ask(anyString());
    }

    // --- 도우미 -------------------------------------------------------------

    private long sentMessage() {
        MessageVerdictResponse v = inspectionService.submit(EMPLOYEE, "3분기 릴리스 일정 정리해줘");
        assertThat(v.submittedText()).isNotNull();
        return v.messageId();
    }

    private MvcResult answer(long messageId, long userId) throws Exception {
        return mockMvc.perform(post("/api/v1/messages/{id}/answer", messageId)
                .header("X-User-Id", userId)).andReturn();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String errorCode(MvcResult r) throws Exception {
        return json(r).path("code").asText();
    }
}
