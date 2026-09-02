package com.skala.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.gateway.DemoCases;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 엔드포인트 4종 (계약서 §1-1·§1-2·§1-3·§1-5·§1-6).
 *
 * <p>가장 먼저 확인하는 것은 <b>목록 봉투</b>다. 배열을 직접 반환하면 FE가 {@code .items}를
 * 꺼내지 않아 {@code filter is not a function}이 난다 — 경계면 버그의 전형이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MasterDataApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /departments — 봉투로 5행. INFOSEC·PR도 포함한다 (드롭다운에 필요)")
    void departments() throws Exception {
        JsonNode body = getJson("/api/v1/departments", 200);

        assertEnvelope(body, 5);
        assertThat(codes(body.path("items"), "code"))
                .containsExactly("DEV", "SALES", "HR", "INFOSEC", "PR");
    }

    @Test
    @DisplayName("GET /users — department는 중첩 객체다. 평탄화하지 않는다")
    void users() throws Exception {
        JsonNode body = getJson("/api/v1/users", 200);

        assertEnvelope(body, 5);
        JsonNode first = body.path("items").get(0);
        assertThat(first.path("name").asText()).isEqualTo("이OO");
        assertThat(first.path("role").asText()).isEqualTo("EMPLOYEE");
        assertThat(first.path("department").path("name").asText()).isEqualTo("개발팀");
    }

    @Test
    @DisplayName("GET /users?deptId= — 부서 필터")
    void usersByDept() throws Exception {
        JsonNode body = getJson("/api/v1/users?deptId=" + DemoCases.DEPT_SALES, 200);

        assertEnvelope(body, 1);
        assertThat(body.path("items").get(0).path("name").asText()).isEqualTo("김OO");
    }

    @Test
    @DisplayName("GET /policies?deptId=1 — 개발팀은 GLOBAL 2건 + P-EMBARGO. P-CONF는 매핑이 없다")
    void policiesForDev() throws Exception {
        JsonNode body = getJson("/api/v1/policies?deptId=" + DemoCases.DEPT_DEV, 200);

        assertEnvelope(body, 3);
        assertThat(codes(body.path("items"), "code")).containsExactly("P-PII", "P-SEC", "P-EMBARGO");
        assertThat(codes(body.path("items"), "appliedVia")).containsExactly("GLOBAL", "GLOBAL", "DEPT");
        // 엠바고를 건 주체가 화면에 드러나야 차단이 납득된다 (결정 3).
        assertThat(codes(body.path("items"), "ownerDept"))
                .containsExactly("정보보안팀", "정보보안팀", "홍보팀");
        // 탐지 정규식은 응답에 넣지 않는다 (계약서 C5). 나가면 우회 입력을 만들 수 있다.
        assertThat(body.toString()).doesNotContain("pattern");
    }

    @Test
    @DisplayName("GET /policies?deptId=2 — 영업팀은 GLOBAL 2건 + 매핑 2건")
    void policiesForSales() throws Exception {
        JsonNode body = getJson("/api/v1/policies?deptId=" + DemoCases.DEPT_SALES, 200);

        assertEnvelope(body, 4);
        assertThat(codes(body.path("items"), "appliedVia"))
                .containsExactly("GLOBAL", "GLOBAL", "DEPT", "DEPT");
        JsonNode conf = body.path("items").get(2);
        assertThat(conf.path("code").asText()).isEqualTo("P-CONF");
        assertThat(conf.path("rules").get(0).path("code").asText()).isEqualTo("CONF-CLIENT-01");
    }

    @Test
    @DisplayName("GET /policies — deptId 누락은 400 INVALID_PARAMETER (계약서 C6)")
    void policiesRequiresDeptId() throws Exception {
        assertThat(getJson("/api/v1/policies", 400).path("code").asText()).isEqualTo("INVALID_PARAMETER");
        assertThat(getJson("/api/v1/policies?deptId=abc", 400).path("code").asText())
                .isEqualTo("INVALID_PARAMETER");
    }

    @Test
    @DisplayName("GET /inspections — 봉투와 ruleCount. 정렬은 createdAt DESC 고정")
    void inspectionList() throws Exception {
        JsonNode body = getJson("/api/v1/inspections?size=5", 200);

        assertThat(body.path("page").asInt()).isZero();
        assertThat(body.path("size").asInt()).isEqualTo(5);
        assertThat(body.path("total").asLong()).isGreaterThanOrEqualTo(100);
        assertThat(body.path("items").size()).isEqualTo(5);

        JsonNode row = body.path("items").get(0);
        // 목록 행에 필요한 것은 문자열이다. 중첩 객체로 만들지 않는다.
        assertThat(row.path("department").isTextual()).isTrue();
        assertThat(row.path("userName").isTextual()).isTrue();
        assertThat(row.hasNonNull("ruleCount")).isTrue();
        assertThat(row.hasNonNull("aiStatus")).isTrue();
    }

    @Test
    @DisplayName("GET /inspections?status= — enum 밖의 값은 400")
    void inspectionListRejectsUnknownStatus() throws Exception {
        assertThat(getJson("/api/v1/inspections?status=APPROVED", 400).path("code").asText())
                .isEqualTo("INVALID_PARAMETER");
    }

    @Test
    @DisplayName("GET /inspections/{id} — 원문은 어떤 상태에서도 응답에 없다")
    void inspectionDetail() throws Exception {
        // 시드의 데모 백업 Case A (BLOCKED, 규칙 2건).
        JsonNode body = getJson("/api/v1/inspections/101", 200);

        assertThat(body.path("inspectionId").asLong()).isEqualTo(101);
        assertThat(body.path("user").path("department").isTextual()).isTrue();
        assertThat(body.has("originalText")).isFalse();
        assertThat(body.has("aiAssessment")).isTrue();
        assertThat(body.path("findings").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("GET /inspections/{id} — 없으면 404 INSPECTION_NOT_FOUND")
    void inspectionDetailNotFound() throws Exception {
        assertThat(getJson("/api/v1/inspections/999999", 404).path("code").asText())
                .isEqualTo("INSPECTION_NOT_FOUND");
    }

    private JsonNode getJson(String url, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(url)).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("%s 의 상태 코드", url)
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 목록은 전부 {@code {items, page, size, total}} 봉투다 (계약서 C1). */
    private static void assertEnvelope(JsonNode body, int expectedSize) {
        assertThat(body.isArray()).as("목록을 배열로 직접 반환하면 안 된다").isFalse();
        assertThat(body.path("items").size()).isEqualTo(expectedSize);
        assertThat(body.path("page").asInt()).isZero();
        assertThat(body.path("size").asInt()).isEqualTo(expectedSize);
        assertThat(body.path("total").asLong()).isEqualTo(expectedSize);
    }

    private static String[] codes(JsonNode items, String field) {
        String[] values = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            values[i] = items.get(i).path(field).asText();
        }
        return values;
    }
}
