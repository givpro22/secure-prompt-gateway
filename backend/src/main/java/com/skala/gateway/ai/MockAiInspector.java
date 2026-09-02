package com.skala.gateway.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.gateway.config.AiProperties;

/**
 * 케이스별 고정 JSON을 반환하는 Mock 구현 (기획서 9.5).
 *
 * <p><b>결정론적이다.</b> 같은 입력에 항상 같은 출력이 나온다. 데모가 이 성질에 의존하므로
 * 랜덤·시각·해시 순서에 의존하는 요소를 넣지 않는다. 분기 평가 순서 자체가 계약이며
 * 계약서 §5-3에 고정돼 있다.
 */
@Component
@Profile("mock")
public class MockAiInspector implements AiInspector {

    private static final Logger log = LoggerFactory.getLogger(MockAiInspector.class);

    private static final String CASE_CLIENT_PROJECT = "mock/ai/case-b-client-project.json";
    private static final String CASE_CLIENT_GENERIC = "mock/ai/case-client-generic.json";
    private static final String CASE_NO_REFERENCE = "mock/ai/case-no-reference.json";

    private final AiProperties properties;
    private final Map<String, AiAssessment> fixtures;

    public MockAiInspector(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        // 기동 시 1회 로드해 캐시한다. 픽스처가 9.4 스키마와 어긋나면 기동 실패로 즉시 드러난다.
        Map<String, AiAssessment> loaded = new LinkedHashMap<>();
        for (String path : List.of(CASE_CLIENT_PROJECT, CASE_CLIENT_GENERIC, CASE_NO_REFERENCE)) {
            loaded.put(path, load(objectMapper, path));
        }
        this.fixtures = Map.copyOf(loaded);
    }

    @Override
    public AiAssessment inspect(AiInspectionRequest request) {
        List<KeywordHit> hits = request == null ? null : request.hits();

        // 1. hits가 비었는데 호출됐다는 것은 규칙 엔진이 REVIEW 판정 없이 AI를 불렀다는 뜻이다.
        //    조용히 빈 결과를 반환하면 그 버그가 데모까지 살아남는다 (기획서 9.5).
        if (hits == null || hits.isEmpty()) {
            throw new IllegalStateException(
                    "AiInspector가 hits 없이 호출되었습니다. 규칙 엔진이 REVIEW 판정 없이 AI를 호출했는지 확인하십시오.");
        }

        // 2. 실패 시뮬레이션. ai_status=FAILED 경로가 실제로 동작해야
        //    "AI가 죽어도 사람 검토로 폴백된다"는 설계 주장이 증명된다.
        //    무결성 검사(1번)보다 뒤에 두므로 실패 데모 입력에는 REVIEW 키워드가 함께 있어야 한다.
        String failKeyword = properties.mock().failKeyword();
        String maskedText = request.maskedText() == null ? "" : request.maskedText();
        if (failKeyword != null && !failKeyword.isBlank() && maskedText.contains(failKeyword)) {
            // 기획서 9.5가 지정한 RuntimeException. IllegalStateException(무결성 위반)과 구분되어야
            // AiInspectionRunner가 "규칙 엔진 버그"와 "AI 실패"를 다른 심각도로 로깅할 수 있다.
            throw new RuntimeException("Mock AI 실패 시뮬레이션 (fail-keyword 감지)");
        }

        // 3. 의도된 지연. 최적화하지 않는다 — 즉시 응답하면 202 비동기 설계가 화면에 드러나지 않는다.
        sleep(properties.mock().delayMs());

        // 4~6. 키워드 매칭. "A사"가 "B사"보다 우선한다.
        String fixture = CASE_NO_REFERENCE;
        if (containsKeyword(hits, "A사")) {
            fixture = CASE_CLIENT_PROJECT;
        } else if (containsKeyword(hits, "B사")) {
            fixture = CASE_CLIENT_GENERIC;
        }

        log.debug("MockAiInspector dept={} hits={} → {}", request.departmentCode(), hits.size(), fixture);
        return fixtures.get(fixture);
    }

    private static boolean containsKeyword(List<KeywordHit> hits, String keyword) {
        return hits.stream()
                .map(KeywordHit::keyword)
                .anyMatch(k -> k != null && k.contains(keyword));
    }

    private static void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 검사가 중단되었습니다", e);
        }
    }

    private static AiAssessment load(ObjectMapper objectMapper, String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, AiAssessment.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Mock 픽스처를 읽을 수 없습니다: " + path + " (기획서 9.4 스키마를 확인하십시오)", e);
        }
    }
}
