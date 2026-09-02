package com.skala.gateway.ai;

import java.util.List;

/**
 * AI 검사 입력 (기획서 9.1, 9.4).
 *
 * <p><b>원문 필드가 없다.</b> {@code original_text}는 어떤 경로로도 이 레코드에 들어가지 않는다.
 * "검사하려고 결국 원문을 밖으로 보내는 것 아닌가"(기획서 16장 예상 질의 2번)에 대한 답이 이
 * 필드 구성이며, 필드를 만들지 않는 것으로 코드가 답을 증명한다. 편의를 위해서라도 추가하지 않는다.
 *
 * @param maskedText     규칙 엔진의 MASK가 이미 적용된 텍스트. 원문이 아니다
 * @param departmentCode 요청자 부서 코드. {@code DEV} / {@code SALES} / {@code HR} 셋뿐이다.
 *                       {@code INFOSEC}은 넣지 않는다 — 정보보안팀은 검토자 전용이라 프롬프트를
 *                       제출하지 않으므로 이 자리에 나타날 수 없다(기획서 0.5 D2). 도달할 수 없는
 *                       값을 열거하면 FE가 불필요한 분기를 만든다
 * @param categories     매칭된 REVIEW 규칙이 속한 정책 카테고리 목록
 * @param hits           KEYWORD 규칙 매칭 근거. <b>키워드당 1건</b>이다 — 한 규칙이 여러 키워드에
 *                       매칭되면 finding은 1건이지만 여기에는 전부 담긴다(리더 결정 D9).
 *                       프롬프트 조립의 "참조 근거"가 되며, RAG 확장 시
 *                       {@code knowledge_source} 검색 결과로 대체되는 자리다 (기획서 3.4)
 * @param policyVersion  정책 스냅샷 식별용. {@code code:version} 쌍을 code 사전순으로 {@code ;} 연결
 */
public record AiInspectionRequest(
        String maskedText,
        String departmentCode,
        List<String> categories,
        List<KeywordHit> hits,
        String policyVersion) {
}
