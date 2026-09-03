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

    /**
     * 문장 단위 판정용 시스템 프롬프트 (_workspace/05 §1-3, §2).
     *
     * <p>{@link #SYSTEM_PROMPT}(9.2)와 목적은 같고 <b>출력 프로토콜만 다르다.</b> 9.2는 모델이
     * 후보와 rationale을 직접 쓰게 하는데, 그렇게 하면 모델이 인용을 다시 써서 원문과 글자가
     * 어긋난다. 여기서는 번호 붙은 문장을 주고 번호와 라벨만 돌려받는다. rationale은 코드가
     * 근거 문장으로 만든다. 9.2의 금지 조항 네 줄은 그대로 유지된다.
     *
     * <p><b>본문 세 줄과 예시 열두 줄은 전부 측정으로 들어온 것이다</b> (골든셋 201문장 기준).
     * <ul>
     *   <li>"이미 공개된 정보인가" 기준이 없으면 공시 매출·홈페이지 요금제를 기밀로 찍는다</li>
     *   <li>"우리 회사 안에서만 아는 사실이면…" 세 줄이 없으면 재현율이 0.667에 머문다. 모델이
     *       "아직 공지 전" 같은 신호어가 붙은 문장만 잡고 사실만 서술한 문장을 놓친다.
     *       이 세 줄로 0.667 → 0.947</li>
     *   <li>계약·장애·인사·일정 예시 네 줄이 없으면 그 주제들이 46~60%로 떨어진다</li>
     *   <li>NONE 예시 세 줄이 없으면 개발 업무 문장(스토리 포인트·p95)을 기밀로 찍어
     *       오탐률이 4.8%에서 7.1%로 오른다</li>
     *   <li><b>"구체적인 수치·단가·조건·상태·명단을 단정적으로 서술하면" 네 줄</b>이 없으면
     *       완곡·사실서술형을 놓친다. `우리 쪽 최저 수용 단가는 장당 780원입니다`처럼 기밀임을
     *       알리는 말 없이 사실만 적은 문장이 그렇다. 사내 대화에서 가장 흔한 형태다.
     *       개발셋 326문장 기준 완곡형 7/12 → 11/12, F1 0.913 → 0.972</li>
     * </ul>
     * 개발셋 326문장: 정밀도 0.961 · 재현율 0.984 · F1 0.972 · 오탐 5/202(2.5%).
     * <b>문구를 바꾸면 `_workspace/golden/run_v2.py`를 다시 돌려야 한다.</b>
     */
    public static final String SENTENCE_SYSTEM_PROMPT = """
            너는 사내 정보보안팀의 프롬프트 검토 보조기다. 번호가 붙은 문장 목록을 읽고 각 문장을 판정한다.

            CONFIDENTIAL - 사내 기밀 노출 후보
            NONE - 후보 아님

            판정 기준의 핵심은 "이미 공개된 정보인가"다. 공시·보도자료·홈페이지·채용공고·IR자료에 나온 값은 숫자가 커도 NONE이다.
            대괄호로 마스킹된 자리([주민번호] 등)는 이미 처리된 것이므로 후보로 만들지 않는다.
            확신이 없으면 NONE. 입력 문장 개수와 출력 배열 길이는 반드시 같아야 한다.

            개인정보·자격증명으로 보이는 문자열은 규칙 엔진의 영역이므로 후보로 만들지 않는다. 너가 볼 것은 맥락형 기밀뿐이다.

            우리 회사 안에서만 아는 사실이면 내용이 나쁘지 않아도 CONFIDENTIAL이다.
            아직 공지·발표·통보되지 않은 결정, 수치, 조건, 명단, 진행 상황이 전부 여기 해당한다.
            문장에 "아직", "비밀" 같은 말이 없어도 마찬가지다 — 사실 자체가 내부 정보인지로 판단한다.

            문장이 우리 회사의 구체적인 수치·단가·조건·상태·명단을 단정적으로 서술하면 CONFIDENTIAL이다.
            "아직", "비공개" 같은 말이 붙어 있지 않아도 마찬가지다. 그 값이 공개 자료에 실렸다고 문장이
            스스로 밝히지 않는 한 내부 수치로 본다.
            반대로 업무 진행 방식, 도구 사용법, 기술 지표, 일정 조율처럼 밖으로 나가도 손해가 없는 내용은
            숫자가 있어도 NONE이다.

            예시:
            - "다음 회의 안건 정리해줘" -> NONE
            - "협력사 단가표가 업계 평균보다 낮다는 건 밖에 알리면 안 돼" -> CONFIDENTIAL
            - "우리 주력 제품 가격은 공식 홈페이지에 다 나와 있어" -> NONE
            - "직원 주민번호 850303-2xxxxxx 확인해줘" -> NONE (규칙 엔진 영역)
            - "리콜 결정이 내부적으로 났는데 아직 발표 전이야" -> CONFIDENTIAL
            - "우리 계약에 최소구매수량 조항이 걸려 있어" -> CONFIDENTIAL (우리 계약의 구체 조건)
            - "서비스가 30분 멈춘 건 고객 공지 전이야" -> CONFIDENTIAL (미공개 장애)
            - "그 직원 연봉 조정폭이 본인한테 아직 안 갔어" -> CONFIDENTIAL (미공개 인사 정보)
            - "다음 배포 일정이 사내 캘린더에 잡혀 있어" -> CONFIDENTIAL (대외 미공개 일정)
            - "이번 스프린트 스토리 포인트가 40점이야" -> NONE (개발 작업량 지표, 기밀 아님)
            - "응답 지연이 400ms까지 올라갔어" -> NONE (기술 지표, 기밀 아님)
            - "A사 미팅 자료 초안 잡아줘" -> NONE (고객사 언급뿐, 내부 사실 없음)
            - "우리 쪽 회수 기준 단가는 킬로당 340원입니다" -> CONFIDENTIAL (내부 단가를 단정 서술)
            - "그 대리점 미수금이 넉 달째입니다" -> CONFIDENTIAL (거래처 상태)
            - "해당 공정의 실제 수율은 목표에 못 미칩니다" -> CONFIDENTIAL (내부 실적)
            - "빌드가 12분 걸립니다" -> NONE (기술 지표, 밖으로 나가도 손해 없음)
            - "회의는 매주 수요일에 합니다" -> NONE (업무 진행 방식)
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

    /**
     * 문장 단위 판정용 사용자 메시지 (_workspace/05 §2).
     *
     * <p>9.3의 4단 구성에서 마지막 단을 통짜 텍스트가 아니라 <b>번호 붙은 문장 목록</b>으로 바꾸고,
     * <b>참조 근거 단을 뺐다.</b> 번호가 곧 응답의 {@code index}이고, 근거 문장은 이 목록에서
     * 꺼내므로 모델이 인용을 다시 쓸 일이 없다.
     *
     * <p><b>9.3의 머리말(부서·정책·참조 근거)을 전부 뺐다.</b> 편의가 아니라 측정 결과다.
     * <ul>
     *   <li>참조 근거를 넣으면 모델이 "A사 프로젝트 자료 정리해줘"처럼 <b>키워드가 들어 있을 뿐인
     *       문장</b>을 후보로 만든다. 그 키워드는 KEYWORD 규칙이 이미 잡았고 AI를 부른 계기 자체라
     *       근거로 주면 미끼가 된다</li>
     *   <li>프롬프트로 "키워드만으로는 후보가 아니다"라고 막았더니 모델이 전반적으로 위축돼 진짜
     *       기밀 8건을 놓쳤다 (골든셋 44/47 → 36/47)</li>
     *   <li>"적용 정책: CONFIDENTIAL(고객사 프로젝트 정보 통제)" 한 줄이 모델의 시야를 고객사
     *       프로젝트로 좁혀 계약·장애·감사 관련 기밀을 놓치게 했다 (43/46 → 41/46)</li>
     * </ul>
     * 부서와 정책 맥락은 판정 후 {@code evidence}와 {@code policySnapshot}으로 기록에 남으므로
     * 잃는 것이 없다 (_workspace/05 §1-6).
     *
     * @param request 부서·정책·참조 근거의 출처. {@code maskedText}는 여기서 쓰지 않는다
     * @param batch   이번 배치의 문장. 목록 순서대로 1번부터 번호가 붙는다
     */
    public String assembleSentenceMessage(AiInspectionRequest request,
                                          List<SentenceSplitter.Sentence> batch) {
        StringBuilder sb = new StringBuilder("문장 목록:\n");
        for (int i = 0; i < batch.size(); i++) {
            sb.append(i + 1).append(". ").append(batch.get(i).text()).append('\n');
        }
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
