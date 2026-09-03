package com.skala.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.gateway.DemoCases;
import com.skala.gateway.ai.AiAssessment;
import com.skala.gateway.api.dto.MessageVerdictResponse;
import com.skala.gateway.config.WebConfig;
import com.skala.gateway.domain.Inspection;
import com.skala.gateway.domain.InspectionFinding;
import com.skala.gateway.domain.enums.FindingSource;
import com.skala.gateway.domain.repository.InspectionFindingRepository;
import com.skala.gateway.domain.repository.InspectionRepository;
import com.skala.gateway.service.InspectionAiResultSink;
import com.skala.gateway.service.InspectionService;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
 * {@code PATCH /api/v1/inspections/{id}/findings/{findingId}} — 사람 확정과 최종 판정 재산출
 * (기획서 3.3 UC-06, 8.4, 계약서 §1-7) + 에러 봉투 (계약서 §1).
 *
 * <p>이 테스트가 고정하는 것은 <b>판정이 어디서 움직이는가</b>다. 규칙은 {@code RULE}로,
 * AI는 후보로만 남고, {@code decided_by=HUMAN}은 여기서만 생긴다 (기획서 4장).
 *
 * <p>{@code @Transactional}이라 각 케이스는 롤백된다 — 감사 콘솔에 행을 남기지 않고,
 * {@code AiInspectionRunner.schedule}의 {@code afterCommit}이 실행되지 않아 Mock의 2.5초 지연도
 * 타지 않는다. AI 결과는 {@code InspectionAiResultSink}를 직접 호출해 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewApiTest {

    /** 감사 담당자 박OO. {@code X-User-Id}가 그대로 {@code reviewed_by}가 된다. */
    private static final long REVIEWER = 4L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private InspectionAiResultSink sink;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private InspectionFindingRepository findingRepository;

    @Test
    @DisplayName("ACCEPT — BLOCK/BLOCKED/HUMAN으로 재산출하고 submittedText는 보존한다 (D14)")
    void acceptBlocksAndPreservesSubmittedText() throws Exception {
        long inspectionId = pendingInspection(1);
        long aiFindingId = aiFindingIds(inspectionId).get(0);

        JsonNode body = json(review(inspectionId, aiFindingId, REVIEWER, "ACCEPTED", "NDA 대상 고객사 일정"));

        assertThat(body.path("findingId").asLong()).isEqualTo(aiFindingId);
        assertThat(body.path("reviewStatus").asText()).isEqualTo("ACCEPTED");
        assertThat(body.path("reviewedBy").path("userId").asLong()).isEqualTo(REVIEWER);
        assertThat(body.path("reviewedBy").path("name").asText()).isEqualTo("박OO");
        assertThat(body.path("reviewedAt").isNull()).isFalse();
        // comment는 저장하지도 에코하지도 않는다 (계약서 §1-7).
        assertThat(body.has("comment")).isFalse();

        // 재조회 없이 화면을 갱신할 수 있도록 inspection 상태를 함께 싣는다 (8.4).
        JsonNode inspectionState = body.path("inspection");
        assertThat(inspectionState.path("inspectionId").asLong()).isEqualTo(inspectionId);
        assertThat(inspectionState.path("finalDecision").asText()).isEqualTo("BLOCK");
        assertThat(inspectionState.path("decidedBy").asText()).isEqualTo("HUMAN");
        assertThat(inspectionState.path("status").asText()).isEqualTo("BLOCKED");
        // D14 — BLOCKED가 되어도 응답이 본문을 그대로 싣는다. 빼면 FE가 "BLOCKED니까 null이겠지"를
        // 추론해 화면에서 지운다 (실제로 그렇게 하고 있었다).
        assertThat(inspectionState.path("submittedText").asText()).isEqualTo(DemoCases.CASE_B);

        Inspection inspection = inspectionRepository.findDetailById(inspectionId).orElseThrow();
        assertThat(inspection.getCompletedAt()).isNotNull();
        // QA F6 — 서버가 completedAt을 확정 시각으로 갱신하므로 응답에도 실어야 한다. 빠지면 상세
        // 패널의 "완료 시각"이 AI 완료 시각에 머물러 확정자 시각과 어긋난 채 나란히 표시된다.
        assertThat(inspectionState.path("completedAt").isNull()).isFalse();
        assertThat(OffsetDateTime.parse(inspectionState.path("completedAt").asText()))
                .isEqualTo(inspection.getCompletedAt().withOffsetSameInstant(ZoneOffset.UTC));
        // aiStatus는 사람의 확정으로 바뀌지 않는다. FE 폴링은 이 값으로만 끝난다 (D12).
        assertThat(inspection.getAiStatus().name()).isEqualTo("COMPLETED");

        // D14 — 사람이 확정한 BLOCK은 본문을 보존한다. NULL이 되는 것은 규칙 BLOCK 경로뿐이며
        // (Masker 미호출), 여기서 지우면 담당자가 방금 무엇을 보고 판단했는지가 사라진다.
        assertThat(inspection.getMessage().getSubmittedText()).isEqualTo(DemoCases.CASE_B);
    }

    @Test
    @DisplayName("REJECT — 전부 기각이면 ALLOW/ALLOWED/HUMAN이고 본문은 그대로다")
    void rejectAllowsAndPreservesSubmittedText() throws Exception {
        long inspectionId = pendingInspection(1);
        long aiFindingId = aiFindingIds(inspectionId).get(0);

        JsonNode body = json(review(inspectionId, aiFindingId, REVIEWER, "REJECTED", null));

        assertThat(body.path("reviewStatus").asText()).isEqualTo("REJECTED");
        assertThat(body.path("inspection").path("finalDecision").asText()).isEqualTo("ALLOW");
        assertThat(body.path("inspection").path("decidedBy").asText()).isEqualTo("HUMAN");
        assertThat(body.path("inspection").path("status").asText()).isEqualTo("ALLOWED");
        assertThat(body.path("inspection").path("submittedText").asText()).isEqualTo(DemoCases.CASE_B);

        Inspection inspection = inspectionRepository.findDetailById(inspectionId).orElseThrow();
        assertThat(inspection.getMessage().getSubmittedText()).isEqualTo(DemoCases.CASE_B);
    }

    @Test
    @DisplayName("후보가 남아 있으면 PENDING_REVIEW를 유지하고, 한 건이라도 ACCEPTED면 BLOCK이다")
    void partialReviewKeepsPendingUntilAllResolved() throws Exception {
        long inspectionId = pendingInspection(2);
        List<Long> aiFindingIds = aiFindingIds(inspectionId);
        assertThat(aiFindingIds).hasSize(2);
        OffsetDateTime aiCompletedAt = inspectionRepository.findDetailById(inspectionId)
                .orElseThrow().getCompletedAt();

        // 1건 기각 — 아직 SUGGESTED가 남았으므로 판정은 움직이지 않는다.
        JsonNode first = json(review(inspectionId, aiFindingIds.get(0), REVIEWER, "REJECTED", null));
        assertThat(first.path("inspection").path("finalDecision").asText()).isEqualTo("PENDING");
        assertThat(first.path("inspection").path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(first.path("inspection").path("decidedBy").isNull()).isTrue();
        assertThat(first.path("inspection").path("submittedText").asText()).isEqualTo(DemoCases.CASE_B);
        // 판정이 움직이지 않았으므로 completedAt도 AI 완료 시각 그대로다 (F6 반대 방향).
        assertThat(OffsetDateTime.parse(first.path("inspection").path("completedAt").asText()))
                .isEqualTo(aiCompletedAt.withOffsetSameInstant(ZoneOffset.UTC));

        // 남은 1건 승인 — ACCEPTED가 REJECTED를 이긴다. 한 건이라도 위반이면 전송할 수 없다.
        JsonNode second = json(review(inspectionId, aiFindingIds.get(1), REVIEWER, "ACCEPTED", null));
        assertThat(second.path("inspection").path("finalDecision").asText()).isEqualTo("BLOCK");
        assertThat(second.path("inspection").path("status").asText()).isEqualTo("BLOCKED");
        assertThat(second.path("inspection").path("decidedBy").asText()).isEqualTo("HUMAN");
        assertThat(second.path("inspection").path("submittedText").asText()).isEqualTo(DemoCases.CASE_B);
    }

    @Test
    @DisplayName("이미 확정된 finding에 재요청하면 409 — 멱등 200을 주면 reviewedAt이 덮어써진다")
    void secondReviewConflicts() throws Exception {
        long inspectionId = pendingInspection(1);
        long aiFindingId = aiFindingIds(inspectionId).get(0);

        review(inspectionId, aiFindingId, REVIEWER, "ACCEPTED", null);
        JsonNode error = json(patchExpecting(409, inspectionId, aiFindingId, REVIEWER,
                "{\"reviewStatus\":\"REJECTED\"}"));

        assertThat(error.path("code").asText()).isEqualTo("FINDING_ALREADY_REVIEWED");
        assertThat(error.has("details")).isTrue();
    }

    @Test
    @DisplayName("규칙 finding에 PATCH가 오면 409 RULE_FINDING_NOT_REVIEWABLE (D13)")
    void ruleFindingIsNotReviewable() throws Exception {
        long inspectionId = pendingInspection(1);
        long ruleFindingId = ruleFindingId(inspectionId);

        JsonNode error = json(patchExpecting(409, inspectionId, ruleFindingId, REVIEWER,
                "{\"reviewStatus\":\"ACCEPTED\"}"));

        // 규칙 판정은 사람이 번복하지 않는다 (기획서 4장). ALREADY_REVIEWED와 코드를 구분하는 것이
        // D13의 요구다 — 사유가 "이미 확정됨"이면 규칙 판정도 번복 가능한 것처럼 읽힌다.
        assertThat(error.path("code").asText()).isEqualTo("RULE_FINDING_NOT_REVIEWABLE");
    }

    @Test
    @DisplayName("404 — 없는 inspection, 없는 finding, 그리고 다른 inspection의 finding")
    void notFoundPaths() throws Exception {
        long inspectionId = pendingInspection(1);
        long aiFindingId = aiFindingIds(inspectionId).get(0);
        long otherInspectionId = pendingInspection(1);

        String accept = "{\"reviewStatus\":\"ACCEPTED\"}";

        assertThat(json(patchExpecting(404, 999_999L, aiFindingId, REVIEWER, accept))
                .path("code").asText()).isEqualTo("INSPECTION_NOT_FOUND");
        assertThat(json(patchExpecting(404, inspectionId, 999_999L, REVIEWER, accept))
                .path("code").asText()).isEqualTo("FINDING_NOT_FOUND");
        // 경로의 inspection에 속하지 않는 finding도 404다. 다른 코드로 구분하면 남의 검사 건
        // finding id의 존재 여부를 알려 주는 셈이 된다.
        assertThat(json(patchExpecting(404, otherInspectionId, aiFindingId, REVIEWER, accept))
                .path("code").asText()).isEqualTo("FINDING_NOT_FOUND");
    }

    @Test
    @DisplayName("400 — reviewStatus는 ACCEPTED/REJECTED만 허용한다")
    void invalidReviewStatus() throws Exception {
        long inspectionId = pendingInspection(1);
        long aiFindingId = aiFindingIds(inspectionId).get(0);

        for (String body : List.of("{\"reviewStatus\":\"SUGGESTED\"}",
                                   "{\"reviewStatus\":\"CONFIRMED\"}",
                                   "{\"reviewStatus\":\"APPROVED\"}",
                                   "{\"comment\":\"사유만 보냄\"}",
                                   "{}")) {
            assertThat(json(patchExpecting(400, inspectionId, aiFindingId, REVIEWER, body))
                    .path("code").asText())
                    .as("요청 본문 %s", body)
                    .isEqualTo("INVALID_REQUEST");
        }
    }

    @Test
    @DisplayName("X-User-Id 누락·비숫자·미존재가 전부 에러 봉투로 나간다 (Spring 기본 400 본문이 아니다)")
    void userHeaderErrorsUseEnvelope() throws Exception {
        long inspectionId = pendingInspection(1);
        long aiFindingId = aiFindingIds(inspectionId).get(0);
        String accept = "{\"reviewStatus\":\"ACCEPTED\"}";

        MvcResult missing = mockMvc.perform(patch(path(inspectionId, aiFindingId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accept))
                .andReturn();
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(missing).path("code").asText()).isEqualTo("MISSING_USER_HEADER");

        assertThat(json(patchExpectingRaw(400, inspectionId, aiFindingId, "abc", accept))
                .path("code").asText()).isEqualTo("INVALID_USER");
        assertThat(json(patchExpecting(400, inspectionId, aiFindingId, 9_999L, accept))
                .path("code").asText()).isEqualTo("INVALID_USER");
    }

    @Test
    @DisplayName("advice가 생겨도 403 BLOCK은 여전히 판정 객체다 (계약서 C2)")
    void blockVerdictIsNotAnErrorEnvelope() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/messages")
                        .header(WebConfig.USER_HEADER, DemoCases.USER_DEV)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("text", DemoCases.CASE_A))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        JsonNode body = json(result);
        // 봉투로 바뀌면 FE의 S4 화면이 차단 사유(규칙 코드·출처)를 잃는다.
        assertThat(body.has("code")).isFalse();
        assertThat(body.path("decision").asText()).isEqualTo("BLOCK");
        assertThat(body.path("status").asText()).isEqualTo("BLOCKED");
        assertThat(body.path("ruleResult").path("matches").size()).isEqualTo(2);
    }

    // --- 픽스처 -------------------------------------------------------------

    /**
     * Case B를 판정해 PENDING 상태를 만들고 AI 후보 {@code candidateCount}건을 붙인다.
     *
     * <p>{@code AiInspectionRunner}를 거치지 않고 sink를 직접 호출한다 — Mock의 2.5초 지연은
     * 화면에서 증명할 것이지 테스트에서 기다릴 것이 아니다.
     */
    private long pendingInspection(int candidateCount) {
        MessageVerdictResponse verdict = inspectionService.submit(DemoCases.USER_SALES, DemoCases.CASE_B);
        List<AiAssessment.RiskCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < candidateCount; i++) {
            candidates.add(new AiAssessment.RiskCandidate(
                    "CONF-CLIENT-PROJECT-" + i,
                    "CONFIDENTIAL",
                    "고객사명과 미공개 일정을 동시에 특정함 #" + i,
                    List.of(new AiAssessment.Evidence("고객사 NDA 목록 v3", "A사 — 비밀유지 2027.03까지"))));
        }
        sink.onCompleted(verdict.inspectionId(),
                new AiAssessment(candidates, List.of("대외 공개 여부 확인 필요"), true));
        return verdict.inspectionId();
    }

    private List<Long> aiFindingIds(long inspectionId) {
        return findingIds(inspectionId, FindingSource.AI);
    }

    private long ruleFindingId(long inspectionId) {
        return findingIds(inspectionId, FindingSource.RULE).get(0);
    }

    private List<Long> findingIds(long inspectionId, FindingSource source) {
        return findingRepository.findByInspectionIdRuleFirst(inspectionId).stream()
                .filter(f -> f.getSource() == source)
                .map(InspectionFinding::getFindingId)
                .toList();
    }

    // --- 요청 헬퍼 ----------------------------------------------------------

    private MvcResult review(long inspectionId, long findingId, long userId,
                             String reviewStatus, String comment) throws Exception {
        String body = comment == null
                ? "{\"reviewStatus\":\"" + reviewStatus + "\"}"
                : "{\"reviewStatus\":\"" + reviewStatus + "\",\"comment\":\"" + comment + "\"}";
        return patchExpecting(200, inspectionId, findingId, userId, body);
    }

    private MvcResult patchExpecting(int expectedStatus, long inspectionId, long findingId,
                                     long userId, String body) throws Exception {
        return patchExpectingRaw(expectedStatus, inspectionId, findingId, String.valueOf(userId), body);
    }

    private MvcResult patchExpectingRaw(int expectedStatus, long inspectionId, long findingId,
                                        String userId, String body) throws Exception {
        MvcResult result = mockMvc.perform(patch(path(inspectionId, findingId))
                        .header(WebConfig.USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("PATCH %s 본문 %s", path(inspectionId, findingId), body)
                .isEqualTo(expectedStatus);
        return result;
    }

    private static String path(long inspectionId, long findingId) {
        return "/api/v1/inspections/" + inspectionId + "/findings/" + findingId;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("보안 담당자가 아니면 확정할 수 없다 — 403 FORBIDDEN_ROLE (0.5.1 D24)")
    void onlySecurityAdminCanReview() throws Exception {
        long inspectionId = pendingInspection(1);
        Long findingId = aiFindingIds(inspectionId).get(0);

        MvcResult result = mockMvc.perform(
                        patch("/api/v1/inspections/{id}/findings/{findingId}",
                                inspectionId, findingId)
                                .header(WebConfig.USER_HEADER, DemoCases.USER_DEV)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reviewStatus\":\"ACCEPTED\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(result).path("code").asText()).isEqualTo("FORBIDDEN_ROLE");
    }
}
