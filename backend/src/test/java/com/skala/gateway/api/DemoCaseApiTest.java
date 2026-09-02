package com.skala.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.gateway.DemoCases;
import com.skala.gateway.config.WebConfig;
import com.skala.gateway.domain.repository.InspectionFindingRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
 * {@code POST /api/v1/messages} 데모 케이스 4종 (기획서 10.4, 계약서 §1-4).
 *
 * <p>이 테스트가 통과하지 않으면 데모가 실패한다. 판정·HTTP 상태·응답 필드까지 한 번에 고정한다.
 *
 * <p>{@code @Transactional}이라 각 케이스는 롤백된다 — 테스트가 감사 콘솔에 행을 남기지 않는다.
 * 부수 효과로 {@code AiInspectionRunner.schedule}의 {@code afterCommit}이 실행되지 않으므로
 * Mock의 2.5초 지연도 타지 않는다. 실제 비동기 완주는 {@code bootRun} 상태에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoCaseApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InspectionFindingRepository findingRepository;

    @Test
    @DisplayName("Case A — 개발팀 이OO: BLOCK · 403 · finding 2건 · submittedText null")
    void caseA_block() throws Exception {
        MvcResult result = submit(DemoCases.USER_DEV, DemoCases.CASE_A, 403);
        JsonNode body = json(result);

        assertThat(body.path("decision").asText()).isEqualTo("BLOCK");
        assertThat(body.path("status").asText()).isEqualTo("BLOCKED");
        // BLOCK은 마스킹을 실행하지 않아 만들어진 본문이 없다 (D5). NULL인 유일한 상태다 (D7).
        assertThat(body.path("submittedText").isNull()).isTrue();
        assertThat(body.path("aiStatus").asText()).isEqualTo("SKIPPED");
        assertThat(body.path("decidedBy").asText()).isEqualTo("RULE");
        assertThat(body.path("pollAfterMs").isNull()).isTrue();
        // 403은 에러 봉투가 아니라 판정 객체다 (계약서 C2). FE가 code를 찾지 않는다.
        assertThat(body.has("code")).isFalse();

        JsonNode matches = body.path("ruleResult").path("matches");
        assertThat(matches.size()).isEqualTo(2);
        assertThat(matches.get(0).path("code").asText()).isEqualTo("SEC-DBURL-02");
        assertThat(span(matches.get(0))).containsExactly(18, 56);
        assertThat(matches.get(1).path("code").asText()).isEqualTo("PII-RRN-01");
        assertThat(span(matches.get(1))).containsExactly(73, 87);
        // 억제된 규칙은 appliedRuleCodes에만 남는다.
        assertThat(body.path("ruleResult").path("appliedRuleCodes").toString())
                .contains("SEC-PRIVIP-03", "PII-EMAIL-04");

        assertThat(ruleFindingCount(body)).isEqualTo(2);
        // 개발팀에도 P-EMBARGO가 매핑됐다 (결정 2). 스냅샷은 판정 시점의 적용 정책 전부다 —
        // 이 문장에 엠바고 키워드가 없어 매칭되지 않았을 뿐이다.
        assertThat(policyCodes(body)).containsExactly("P-PII", "P-SEC", "P-EMBARGO");
    }

    @Test
    @DisplayName("Case B — 영업팀 김OO: PENDING · 202 · finding 1건 · submittedText 채워짐")
    void caseB_pendingReview() throws Exception {
        MvcResult result = submit(DemoCases.USER_SALES, DemoCases.CASE_B, 202);
        JsonNode body = json(result);

        assertThat(body.path("decision").asText()).isEqualTo("PENDING");
        assertThat(body.path("status").asText()).isEqualTo("PENDING_REVIEW");
        // PENDING_REVIEW도 마스킹본을 싣는다 (D7). MASK 매칭이 없어 원문과 같은 값이다.
        assertThat(body.path("submittedText").asText()).isEqualTo(DemoCases.CASE_B);
        assertThat(body.path("aiStatus").asText()).isEqualTo("PENDING");
        assertThat(body.path("decidedBy").isNull()).isTrue();
        assertThat(body.path("pollAfterMs").asInt()).isEqualTo(2000);
        // 202 시점에 AI 결과는 존재하지 않는다. FE가 여기서 aiAssessment를 참조하면 크래시한다.
        assertThat(body.has("aiAssessment")).isFalse();
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/inspections/" + body.path("inspectionId").asLong());

        JsonNode matches = body.path("ruleResult").path("matches");
        assertThat(matches.size()).isEqualTo(1);
        assertThat(matches.get(0).path("code").asText()).isEqualTo("CONF-CLIENT-01");
        assertThat(matches.get(0).path("action").asText()).isEqualTo("REVIEW");
        assertThat(matches.get(0).path("matchedKeyword").asText()).isEqualTo("A사");
        assertThat(span(matches.get(0))).containsExactly(0, 2);

        assertThat(ruleFindingCount(body)).isEqualTo(1);
        // 영업팀은 GLOBAL 2건 + 매핑된 P-CONF·P-EMBARGO까지 4건이다.
        assertThat(policyCodes(body)).containsExactly("P-PII", "P-SEC", "P-CONF", "P-EMBARGO");
    }

    @Test
    @DisplayName("Case C — 같은 문장을 개발팀 이OO가 보내면 ALLOW · 200 · finding 0건")
    void caseC_allow() throws Exception {
        MvcResult result = submit(DemoCases.USER_DEV, DemoCases.CASE_C, 200);
        JsonNode body = json(result);

        assertThat(body.path("decision").asText()).isEqualTo("ALLOW");
        assertThat(body.path("status").asText()).isEqualTo("ALLOWED");
        assertThat(body.path("submittedText").asText()).isEqualTo(DemoCases.CASE_C);
        assertThat(body.path("aiStatus").asText()).isEqualTo("SKIPPED");
        assertThat(body.path("decidedBy").asText()).isEqualTo("RULE");
        assertThat(body.path("ruleResult").path("matches").size()).isZero();

        assertThat(ruleFindingCount(body)).isZero();
        // 부서↔정책 N:M 설계의 증명 지점 — P-CONF가 스냅샷에 없다.
        assertThat(policyCodes(body)).doesNotContain("P-CONF");
    }

    @Test
    @DisplayName("Case D — 인사팀 정OO: MASK · 200 · submittedText에 [전화번호]")
    void caseD_mask() throws Exception {
        MvcResult result = submit(DemoCases.USER_HR, DemoCases.CASE_D, 200);
        JsonNode body = json(result);

        assertThat(body.path("decision").asText()).isEqualTo("MASK");
        assertThat(body.path("status").asText()).isEqualTo("MASKED");
        assertThat(body.path("submittedText").asText())
                .isEqualTo("지원자 연락처 [전화번호] 로 면접 안내 문자 초안 써줘");
        assertThat(body.path("aiStatus").asText()).isEqualTo("SKIPPED");
        assertThat(body.path("decidedBy").asText()).isEqualTo("RULE");

        JsonNode matches = body.path("ruleResult").path("matches");
        assertThat(matches.size()).isEqualTo(1);
        assertThat(matches.get(0).path("code").asText()).isEqualTo("PII-PHONE-03");
        // span은 원문 기준이다. 마스킹본 기준으로 재계산하지 않는다 (D3).
        assertThat(span(matches.get(0))).containsExactly(8, 21);

        assertThat(ruleFindingCount(body)).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 본문은 400 INVALID_REQUEST — 판정이 아니라 요청 오류다")
    void rejectsBlankText() throws Exception {
        MvcResult result = submit(DemoCases.USER_DEV, "   ", 400);
        assertThat(json(result).path("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 400 INVALID_USER — 인증이 없으므로 401이 아니다")
    void rejectsUnknownUser() throws Exception {
        MvcResult result = submit(9999L, DemoCases.CASE_C, 400);
        assertThat(json(result).path("code").asText()).isEqualTo("INVALID_USER");
    }

    private MvcResult submit(long userId, String text, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/messages")
                        .header(WebConfig.USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsBytes(Map.of("text", text))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return result;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static int[] span(JsonNode match) {
        JsonNode span = match.path("span");
        return new int[]{span.get(0).asInt(), span.get(1).asInt()};
    }

    private static String[] policyCodes(JsonNode body) {
        JsonNode policies = body.path("policySnapshot").path("policies");
        String[] codes = new String[policies.size()];
        for (int i = 0; i < policies.size(); i++) {
            codes[i] = policies.get(i).path("code").asText();
        }
        return codes;
    }

    /** {@code source='RULE'}인 finding만 센다 — 감사 목록의 {@code ruleCount}와 같은 값이다. */
    private long ruleFindingCount(JsonNode body) {
        long inspectionId = body.path("inspectionId").asLong();
        return findingRepository.countRuleFindings(java.util.List.of(inspectionId)).stream()
                .mapToLong(InspectionFindingRepository.RuleCountRow::getRuleCount)
                .sum();
    }
}
