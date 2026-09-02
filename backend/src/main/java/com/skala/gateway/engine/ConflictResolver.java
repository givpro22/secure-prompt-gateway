package com.skala.gateway.engine;

import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.RuleAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 중첩 억제(기획서 7.4-6, 0.5 D1)와 충돌 해결(7.5).
 *
 * <p>두 단계가 한 클래스에 있는 것은 순서가 곧 설계이기 때문이다. 억제를 하지 않고 충돌을 풀면
 * 같은 문자열이 두 번 세어지고, 억제 후에 풀면 화면의 "규칙 N건"이 실제 위험 개수와 일치한다.
 */
@Component
public class ConflictResolver {

    /**
     * 매칭 정렬 기준. 이 순서가 곧 "앞선 매칭"의 정의다.
     *
     * <p>시작 오프셋 오름차순이 1차다. 같은 자리에서 시작하면 <b>긴 매칭이 앞선다</b> —
     * 짧은 쪽이 먼저 남으면 그것을 감싸는 넓은 매칭이 뒤늦게 살아남아 같은 구간이 두 번 세어진다.
     * span까지 같으면 severity 높은 쪽, 동률이면 rule code 사전순이다 (7.6과 같은 기준).
     */
    private static final Comparator<RuleHit> MATCH_ORDER =
            Comparator.comparingInt(RuleHit::spanStart)
                    .thenComparing(Comparator.comparingInt(RuleHit::spanEnd).reversed())
                    .thenComparing(hit -> hit.severity().ordinal())
                    .thenComparing(RuleHit::code);

    /**
     * 중첩 억제 (0.5 D1). span 시작 오프셋 순으로 정렬한 뒤, 앞선 매칭의 span에
     * <b>완전히 포함되는</b> 매칭은 finding을 만들지 않는다.
     *
     * <p>Case A의 {@code postgres://admin:p%40ss@10.0.3.21/prod}에서 SEC-DBURL-02 [18,56]이
     * 전체를 먹고 PII-EMAIL-04 [37,51]·SEC-PRIVIP-03 [42,51]이 그 안에 들어간다. 원시 매칭
     * 4건이 finding 2건이 되는 자리이며, 억제 대상이 사설 IP 하나뿐이라고 알고 짜면 3건이 나온다
     * (0.5 D11).
     *
     * <p>부분 겹침(포함 아님)은 억제하지 않는다. 7.6의 severity 규칙을 {@link Masker}가 적용한다.
     *
     * @return 억제 후 남은 매칭. 정렬은 span 시작 오름차순으로 고정된다
     */
    public List<RuleHit> suppressNested(List<RuleHit> rawMatches) {
        List<RuleHit> sorted = new ArrayList<>(rawMatches);
        sorted.sort(MATCH_ORDER);

        List<RuleHit> kept = new ArrayList<>();
        for (RuleHit candidate : sorted) {
            boolean nested = kept.stream().anyMatch(k -> k.contains(candidate));
            if (!nested) {
                kept.add(candidate);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * 충돌 해결 (7.5). 우선순위는 {@code BLOCK > REVIEW > MASK > ALLOW}다.
     *
     * <p>BLOCK이 최우선인 결과로 <b>AI를 호출하지 않는 분기</b>가 생긴다. 이미 확정된 위반에
     * 모델 비용을 쓸 이유가 없고, {@code submitted_text}가 NULL이라 애초에 밖으로 보낼 텍스트가 없다.
     *
     * @param findings 중첩 억제 후 남은 매칭
     */
    public FinalDecision resolve(List<RuleHit> findings) {
        boolean review = false;
        boolean mask = false;
        for (RuleHit hit : findings) {
            RuleAction action = hit.action();
            if (action == RuleAction.BLOCK) {
                return FinalDecision.BLOCK;
            }
            if (action == RuleAction.REVIEW) {
                review = true;
            } else if (action == RuleAction.MASK) {
                mask = true;
            }
        }
        if (review) {
            return FinalDecision.PENDING;
        }
        return mask ? FinalDecision.MASK : FinalDecision.ALLOW;
    }
}
