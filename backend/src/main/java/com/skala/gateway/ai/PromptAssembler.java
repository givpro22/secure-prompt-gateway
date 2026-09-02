package com.skala.gateway.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import com.skala.gateway.config.AiProperties;

/**
 * 시스템 프롬프트(기획서 9.2)와 사용자 메시지 조립(9.3).
 *
 * <p>{@code mock} 프로파일에서는 사용되지 않지만 항상 빈으로 등록된다. 조립 규칙을 코드로 고정해
 * 두면 LLM 교체 시 프롬프트가 문서와 어긋날 여지가 없고, 순수 함수라 테스트로 검증할 수 있다.
 *
 * <p>이 클래스에 원문을 받는 경로가 없다. 입력은 {@link AiInspectionRequest} 하나뿐이고 그 안에
 * {@code maskedText}만 있다 (기획서 9.3 제약).
 */
@Component
public class PromptAssembler {

    /** 기획서 9.2 시스템 프롬프트 전문. 문서 사본은 {@code docs/ai-prompt.md}. */
    public static final String SYSTEM_PROMPT = """
            당신은 사내 정보보안팀의 프롬프트 검토 보조 시스템이다.

            [역할]
            - 입력된 텍스트에서 규칙 엔진이 잡지 못한 맥락형 기밀 노출 후보를 찾는다.
            - 각 후보에 대해 어떤 서술에서 도출했는지 rationale을 쓴다.
            - 제공된 참조 근거(evidence) 중 관련 있는 것을 후보에 연결한다.
            - 판단에 필요한데 입력에 없는 정보는 missingContext에 기록한다.

            [금지]
            - 허용, 마스킹, 차단 여부를 판단하지 않는다.
            - 입력에 없는 사실을 확정적으로 생성하지 않는다.
            - 근거가 불충분하면 후보를 만들지 말고 missingContext에 남긴다.
            - 개인정보로 보이는 문자열이 있어도 그것은 규칙 엔진의 영역이므로 후보로 만들지 않는다.

            [출력]
            - 아래 JSON 스키마만 반환한다. 설명 문장, 마크다운, 코드 펜스를 붙이지 않는다.
            - riskCandidates가 없으면 빈 배열을 반환한다.
            """;

    /** 입력이 잘렸을 때 {@code missingContext}에 넣을 문구 (기획서 9.3). */
    public static final String TRUNCATION_NOTICE = "입력 절단: 최대 입력 길이를 초과해 앞부분만 검토함";

    private static final String DEPT_DEV = "DEV";
    private static final String DEPT_SALES = "SALES";
    private static final String DEPT_HR = "HR";

    private final AiProperties properties;

    public PromptAssembler(AiProperties properties) {
        this.properties = properties;
    }

    /**
     * 기획서 9.3의 4단 구성으로 사용자 메시지를 만든다.
     * 순서는 부서 컨텍스트 → 적용 정책 카테고리 → 참조 근거 → 검토 대상 텍스트로 고정한다.
     */
    public String assembleUserMessage(AiInspectionRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("요청자 부서: ").append(departmentLabel(request.departmentCode())).append('\n');
        sb.append("적용 정책: ").append(categoryLine(request.categories())).append('\n');
        sb.append("참조 근거:\n");
        List<KeywordHit> hits = request.hits() == null ? List.of() : request.hits();
        if (hits.isEmpty()) {
            sb.append("- 없음\n");
        } else {
            for (KeywordHit hit : hits) {
                sb.append("- 키워드 \"").append(hit.keyword()).append("\" — ").append(hit.source()).append('\n');
            }
        }
        sb.append("검토 대상:\n<text>").append(truncate(request.maskedText())).append("</text>");
        return sb.toString();
    }

    /** 입력이 {@code ai.max-input-chars}를 초과해 잘렸는지. 잘렸으면 missingContext에 기록해야 한다. */
    public boolean isTruncated(String maskedText) {
        return maskedText != null && maskedText.length() > properties.maxInputChars();
    }

    /** 초과분은 앞부분만 남긴다 (기획서 9.3). */
    public String truncate(String maskedText) {
        if (maskedText == null) {
            return "";
        }
        int limit = properties.maxInputChars();
        return maskedText.length() > limit ? maskedText.substring(0, limit) : maskedText;
    }

    private static String departmentLabel(String code) {
        if (code == null) {
            return "미상";
        }
        return switch (code) {
            case DEPT_DEV -> "개발팀";
            case DEPT_SALES -> "영업팀";
            case DEPT_HR -> "인사팀";
            default -> code;
        };
    }

    private static String categoryLine(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return "없음";
        }
        return String.join(", ", categories.stream().map(PromptAssembler::categoryLabel).toList());
    }

    private static String categoryLabel(String category) {
        return switch (category) {
            case "CONFIDENTIAL" -> "CONFIDENTIAL(고객사 프로젝트 정보 통제)";
            case "PII" -> "PII(개인정보 보호)";
            case "SECRET" -> "SECRET(자격증명·인프라 정보 보호)";
            default -> category;
        };
    }
}
