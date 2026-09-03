package com.skala.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.gateway.DemoCases;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.service.InspectionService;
import java.nio.charset.StandardCharsets;
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
 * 마스킹 해제 검토 (D25).
 *
 * <p>고정하는 것은 두 가지다. 하나는 <b>원문이 어디까지 열리는가</b> — 요청을 올린
 * 본인에게도 응답으로 되돌려주지 않고, 담당자 목록에서만 열린다. 다른 하나는
 * <b>누가 확정하는가</b> — 보안 담당자만이며 직원이 직접 부르면 403이다 (D24와 같은 이유).
 *
 * <p>해제가 승인돼도 이미 나간 마스킹본은 그대로다. 회수할 수 없는 것을 회수한 척하지
 * 않는다는 판단이고, 감사 기록은 그 시점에 실제로 나간 것을 담아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnmaskApiTest {

    private static final long EMPLOYEE = DemoCases.USER_HR;
    private static final long ADMIN = DemoCases.USER_ADMIN;

    /** 고객 명단과 이름이 같은 직원을 부르는 문장. 규칙은 이것을 [고객명]으로 가린다 */
    private static final String COLLIDING_NAME = "서지윤 대리한테 인수인계 부탁해";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InspectionService inspectionService;

    @Test
    @DisplayName("동명이인 — 직원 이름도 [고객명]으로 가려진다. 규칙이 문맥을 보지 못한다")
    void employeeSharingCustomerNameGetsMasked() {
        MessageVerdictResponse verdict = inspectionService.submit(EMPLOYEE, COLLIDING_NAME);

        assertThat(verdict.decision().name()).isEqualTo("MASK");
        assertThat(verdict.submittedText()).isEqualTo("[고객명] 대리한테 인수인계 부탁해");
    }

    @Test
    @DisplayName("요청 — 사유를 적어 올리면 PENDING이 되고 원문은 응답에 실리지 않는다")
    void requestCreatesPendingWithoutOriginal() throws Exception {
        long messageId = maskedMessage();

        JsonNode body = json(request(messageId, EMPLOYEE, "우리 팀 서지윤 대리입니다. 고객이 아닙니다."));

        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("messageId").asLong()).isEqualTo(messageId);
        assertThat(body.path("requester").path("userId").asLong()).isEqualTo(EMPLOYEE);
        // 요청자에게도 원문을 되돌려주지 않는다. 자기 문장은 화면이 이미 들고 있다.
        assertThat(body.path("originalText").isNull()).isTrue();
        assertThat(body.path("submittedText").isNull()).isTrue();
    }

    @Test
    @DisplayName("사유 없이 올리면 400")
    void reasonIsRequired() throws Exception {
        long messageId = maskedMessage();

        mockMvc.perform(post("/api/v1/messages/{id}/unmask-request", messageId)
                        .header("X-User-Id", EMPLOYEE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    @DisplayName("남의 건에는 요청할 수 없다 — 403. 이 요청이 원문 열람의 근거가 된다")
    void onlyAuthorMayRequest() throws Exception {
        long messageId = maskedMessage();

        mockMvc.perform(post("/api/v1/messages/{id}/unmask-request", messageId)
                        .header("X-User-Id", DemoCases.USER_SALES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"확인 부탁\"}"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(403);
                    assertThat(errorCode(result)).isEqualTo("FORBIDDEN_NOT_AUTHOR");
                });
    }

    @Test
    @DisplayName("같은 건에 두 번 올리면 409 — 담당자를 밀어붙일 수 없다")
    void oneRequestPerMessage() throws Exception {
        long messageId = maskedMessage();
        request(messageId, EMPLOYEE, "첫 번째 요청");

        mockMvc.perform(post("/api/v1/messages/{id}/unmask-request", messageId)
                        .header("X-User-Id", EMPLOYEE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"두 번째 요청\"}"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(409);
                    assertThat(errorCode(result)).isEqualTo("UNMASK_REQUEST_EXISTS");
                });
    }

    @Test
    @DisplayName("마스킹되지 않은 건은 409 — 가린 것이 없으면 풀 것도 없다")
    void onlyMaskedMessagesAreEligible() throws Exception {
        MessageVerdictResponse allowed = inspectionService.submit(EMPLOYEE, "FAQ 초안 10개 뽑아줘");
        assertThat(allowed.decision().name()).isEqualTo("ALLOW");

        mockMvc.perform(post("/api/v1/messages/{id}/unmask-request", allowed.messageId())
                        .header("X-User-Id", EMPLOYEE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"확인 부탁\"}"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(409);
                    assertThat(errorCode(result)).isEqualTo("NOT_MASKED");
                });
    }

    @Test
    @DisplayName("담당자 목록에서만 원문과 마스킹본이 나란히 열린다")
    void consoleShowsOriginalBesideMasked() throws Exception {
        long messageId = maskedMessage();
        request(messageId, EMPLOYEE, "우리 팀 서지윤 대리입니다.");

        MvcResult result = mockMvc.perform(get("/api/v1/unmask-requests")
                        .header("X-User-Id", ADMIN)
                        .param("status", "PENDING"))
                .andReturn();
        JsonNode row = json(result).path("items").get(0);

        assertThat(row.path("originalText").asText()).isEqualTo(COLLIDING_NAME);
        assertThat(row.path("submittedText").asText()).isEqualTo("[고객명] 대리한테 인수인계 부탁해");
        assertThat(row.path("reason").asText()).contains("서지윤 대리");
    }

    @Test
    @DisplayName("목록은 보안 담당자만 본다 — 직원이 부르면 403")
    void consoleIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/unmask-requests").header("X-User-Id", EMPLOYEE))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(403);
                    assertThat(errorCode(result)).isEqualTo("FORBIDDEN_ROLE");
                });
    }

    @Test
    @DisplayName("해제 확정 — APPROVED로 남지만 이미 나간 마스킹본은 되돌리지 않는다")
    void approveRecordsDecisionWithoutRewritingHistory() throws Exception {
        long messageId = maskedMessage();
        long requestId = json(request(messageId, EMPLOYEE, "우리 팀 대리입니다.")).path("requestId").asLong();

        JsonNode body = json(decide(requestId, ADMIN, true, "직원 확인함. 다음부터 사번으로 지칭할 것."));

        assertThat(body.path("status").asText()).isEqualTo("APPROVED");
        assertThat(body.path("decidedBy").asText()).isEqualTo("박OO");
        assertThat(body.path("decidedAt").isNull()).isFalse();
        // 회수할 수 없는 것을 회수한 척하지 않는다. 감사 기록은 그때 실제로 나간 것을 담는다.
        assertThat(body.path("submittedText").asText()).isEqualTo("[고객명] 대리한테 인수인계 부탁해");
    }

    @Test
    @DisplayName("확정은 보안 담당자만 — 직원이 API를 직접 불러도 403")
    void decisionIsAdminOnly() throws Exception {
        long messageId = maskedMessage();
        long requestId = json(request(messageId, EMPLOYEE, "확인 부탁")).path("requestId").asLong();

        mockMvc.perform(post("/api/v1/unmask-requests/{id}/decision", requestId)
                        .header("X-User-Id", EMPLOYEE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(403);
                    assertThat(errorCode(result)).isEqualTo("FORBIDDEN_ROLE");
                });
    }

    @Test
    @DisplayName("한 번 확정된 요청은 다시 열지 않는다 — 409")
    void decisionIsFinal() throws Exception {
        long messageId = maskedMessage();
        long requestId = json(request(messageId, EMPLOYEE, "확인 부탁")).path("requestId").asLong();
        decide(requestId, ADMIN, false, "고객 명단과 구분되지 않음. 유지.");

        mockMvc.perform(post("/api/v1/unmask-requests/{id}/decision", requestId)
                        .header("X-User-Id", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(409);
                    assertThat(errorCode(result)).isEqualTo("UNMASK_ALREADY_DECIDED");
                });
    }

    @Test
    @DisplayName("요청자는 자기 건의 처리 상태를 본다 — 원문은 실리지 않는다")
    void requesterSeesOwnOutcome() throws Exception {
        long messageId = maskedMessage();
        long requestId = json(request(messageId, EMPLOYEE, "우리 팀 대리입니다.")).path("requestId").asLong();
        decide(requestId, ADMIN, true, "직원 확인함.");

        MvcResult result = mockMvc.perform(get("/api/v1/messages/{id}/unmask-request", messageId)
                        .header("X-User-Id", EMPLOYEE))
                .andReturn();
        JsonNode body = json(result);

        assertThat(body.path("status").asText()).isEqualTo("APPROVED");
        assertThat(body.path("decisionNote").asText()).isEqualTo("직원 확인함.");
        assertThat(body.path("decidedBy").asText()).isEqualTo("박OO");
        // 자기 원문은 화면이 이미 들고 있다. 여기서 필요한 것은 담당자의 판단뿐이다.
        assertThat(body.path("originalText").isNull()).isTrue();
    }

    @Test
    @DisplayName("남의 건 상태는 볼 수 없다 — 403")
    void othersOutcomeIsHidden() throws Exception {
        long messageId = maskedMessage();
        request(messageId, EMPLOYEE, "확인 부탁");

        mockMvc.perform(get("/api/v1/messages/{id}/unmask-request", messageId)
                        .header("X-User-Id", DemoCases.USER_SALES))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(403);
                    assertThat(errorCode(result)).isEqualTo("FORBIDDEN_NOT_AUTHOR");
                });
    }

    // --- 도우미 -------------------------------------------------------------

    private long maskedMessage() {
        MessageVerdictResponse verdict = inspectionService.submit(EMPLOYEE, COLLIDING_NAME);
        assertThat(verdict.decision().name()).isEqualTo("MASK");
        return verdict.messageId();
    }

    private MvcResult request(long messageId, long userId, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/messages/{id}/unmask-request", messageId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReasonBody(reason))))
                .andReturn();
    }

    private MvcResult decide(long requestId, long userId, boolean approve, String note) throws Exception {
        return mockMvc.perform(post("/api/v1/unmask-requests/{id}/decision", requestId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecisionBody(approve, note))))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String errorCode(MvcResult result) throws Exception {
        return json(result).path("code").asText();
    }

    private record ReasonBody(String reason) {
    }

    private record DecisionBody(boolean approve, String note) {
    }
}
