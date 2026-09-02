package com.skala.gateway.engine;

import com.skala.gateway.domain.enums.RuleAction;
import com.skala.gateway.domain.enums.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 마스킹 (기획서 7.6).
 *
 * <p><b>최종 판정이 BLOCK이면 호출되지 않는다</b> (0.5 D5). BLOCK 규칙(SEC-DBURL-02,
 * SEC-AWSKEY-01)에는 {@code mask_label}이 없어서(NULL) 무조건 실행하면 NPE가 나고, 그것이
 * 데모 첫 케이스인 Case A다. 이 분기는 {@link RuleEngine}이 판정 결과로 만든다.
 *
 * <p>치환 단위는 매칭 전체다. 뒤 4자리 보존 같은 부분 마스킹은 Future다.
 */
@Component
public class Masker {

    /**
     * 치환 구간 하나. 엔티티에 의존하지 않으므로 순수 단위 테스트가 가능하다.
     *
     * @param start    원문 기준 시작 (포함)
     * @param end      원문 기준 끝 (미포함)
     * @param label    치환 문자열 ({@code [주민번호]} 등)
     * @param severity 겹침 해결용
     * @param code     severity 동률일 때의 사전순 기준
     */
    public record MaskTarget(int start, int end, String label, Severity severity, String code) {
    }

    private static final Comparator<MaskTarget> TARGET_ORDER =
            Comparator.comparingInt(MaskTarget::start)
                    .thenComparing(Comparator.comparingInt(MaskTarget::end).reversed());

    /** 겹치는 구간에서 살아남을 라벨: severity 높은 쪽, 동률이면 rule code 사전순 (7.6). */
    private static final Comparator<MaskTarget> LABEL_PRIORITY =
            Comparator.<MaskTarget>comparingInt(t -> t.severity().ordinal())
                    .thenComparing(MaskTarget::code);

    /**
     * 중첩 억제 후 남은 매칭 중 MASK 액션만 골라 마스킹본을 만든다.
     *
     * @param originalText 원문
     * @param findings     중첩 억제 후 남은 매칭. BLOCK·REVIEW 매칭은 여기서 걸러진다
     * @return 마스킹 적용본. 대상이 없으면 원문과 같은 문자열
     */
    public String mask(String originalText, List<RuleHit> findings) {
        List<MaskTarget> targets = findings.stream()
                .filter(hit -> hit.action() == RuleAction.MASK)
                // BLOCK·REVIEW 규칙에는 라벨이 없다. 여기까지 왔다면 액션 필터가 이미 걸렀지만,
                // 라벨 없는 MASK 규칙이 시드에 들어오면 NPE 대신 조용히 건너뛰는 쪽이 안전하다.
                .filter(hit -> hit.rule().getMaskLabel() != null)
                .map(hit -> new MaskTarget(hit.spanStart(), hit.spanEnd(),
                        hit.rule().getMaskLabel(), hit.severity(), hit.code()))
                .toList();
        return applyTargets(originalText, targets);
    }

    /**
     * 치환 구간 목록을 실제로 적용한다.
     *
     * <p><b>뒤에서 앞 방향으로 치환한다.</b> 앞에서부터 치환하면 라벨과 매칭 문자열의 길이가 달라
     * 뒤 매칭의 오프셋이 전부 밀린다 ({@code 900101-1234567} 14자 → {@code [주민번호]} 6자).
     *
     * <p>부분 겹침 구간은 하나로 합쳐 severity 높은 규칙의 라벨을 쓴다 (7.6). 합치지 않고 각각
     * 치환하면 앞선 치환이 뒤 구간의 오프셋을 무너뜨려 문자열이 깨진다.
     */
    public String applyTargets(String originalText, List<MaskTarget> targets) {
        if (targets.isEmpty()) {
            return originalText;
        }
        List<MaskTarget> merged = merge(targets);

        StringBuilder sb = new StringBuilder(originalText);
        for (int i = merged.size() - 1; i >= 0; i--) {
            MaskTarget target = merged.get(i);
            sb.replace(target.start(), target.end(), target.label());
        }
        return sb.toString();
    }

    private static List<MaskTarget> merge(List<MaskTarget> targets) {
        List<MaskTarget> sorted = new ArrayList<>(targets);
        sorted.sort(TARGET_ORDER);

        List<MaskTarget> merged = new ArrayList<>();
        for (MaskTarget target : sorted) {
            if (merged.isEmpty()) {
                merged.add(target);
                continue;
            }
            MaskTarget last = merged.get(merged.size() - 1);
            if (target.start() >= last.end()) {
                merged.add(target);
                continue;
            }
            MaskTarget winner = LABEL_PRIORITY.compare(last, target) <= 0 ? last : target;
            merged.set(merged.size() - 1, new MaskTarget(
                    last.start(), Math.max(last.end(), target.end()),
                    winner.label(), winner.severity(), winner.code()));
        }
        return merged;
    }
}
