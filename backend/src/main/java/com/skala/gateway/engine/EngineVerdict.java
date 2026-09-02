package com.skala.gateway.engine;

import com.skala.gateway.ai.KeywordHit;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.jsonb.RuleResult;
import java.util.List;

/**
 * 규칙 판정 결과. {@link RuleEngine}의 산출물이며 영속화·응답 조립의 입력이다.
 *
 * <p>판정까지가 규칙 엔진의 범위이고, 저장과 AI 인계는 {@code service} 계층이 한다.
 * 이 레코드가 그 경계면이다.
 *
 * @param decision      최종 판정 (7.5). {@code BLOCK}이면 AI를 호출하지 않는다
 * @param findings      중첩 억제 후 남은 매칭. {@code inspection_finding} 행이 될 것들이다
 * @param rawMatches    억제 <b>전</b> 원시 매칭 전체. 저장하지 않고 테스트·로그에서만 쓴다 —
 *                      Case A의 "원시 4건 → finding 2건"을 고정하는 자리다 (0.5 D11)
 * @param ruleResult    {@code inspection.rule_result}에 그대로 저장될 JSONB (계약서 §4 인계 2)
 * @param maskedText    마스킹 적용본. <b>판정이 BLOCK이면 {@code null}</b>이다 (0.5 D5)
 * @param hits          KEYWORD 매칭 근거. 키워드당 1건이며 {@code AiInspectionRequest.hits}가 된다
 * @param categories    매칭된 REVIEW 규칙이 속한 정책 카테고리. 적용된 정책 전체가 아니다
 */
public record EngineVerdict(
        FinalDecision decision,
        List<RuleHit> findings,
        List<RuleHit> rawMatches,
        RuleResult ruleResult,
        String maskedText,
        List<KeywordHit> hits,
        List<String> categories) {

    /** 규칙 finding 개수. 감사 목록의 {@code ruleCount}와 같은 값이다 (계약서 §1-6). */
    public int ruleCount() {
        return findings.size();
    }
}
