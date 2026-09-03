package com.skala.gateway.ai;

import org.springframework.stereotype.Component;

/**
 * 응답이 사내 문서를 그대로 옮겼는지 판정한다 (`02_탐지규칙.md` 6.2).
 *
 * <p><b>배선되어 있지 않다.</b> 출력 검사는 기획서 0.3의 범위 밖이고, 그 전에 응답을 만드는
 * 경로부터 없다. 판정 로직만 먼저 둔다 — 순수 함수라 배선 없이도 테스트로 굳혀 둘 수 있고,
 * 출력 검사를 열기로 결정하면 {@code phase=OUTPUT} inspection에 그대로 꽂힌다
 * (_workspace/05 §4).
 *
 * <p><b>이 판정을 LLM에게 맡기지 않는 이유.</b> "글자 그대로 옮겼는가"는 문자열 문제다. 최장
 * 공통 부분문자열은 정확하고, 근거 구간을 그대로 돌려주고, 1ms에 끝난다. 같은 일을 확률적으로
 * 하는 도구로 대체할 이유가 없다. LLM은 임계 미만의 회색지대(바꿔 쓴 인용인지 우연인지)에서만
 * 쓴다 (_workspace/05 §4).
 */
@Component
public class QuoteOverlapDetector {

    /**
     * 겹침 판정 결과.
     *
     * @param length   최장 공통 부분문자열 길이 (문자 수)
     * @param fragment 그 부분문자열. 관리자 화면의 근거 표시에 그대로 쓴다
     * @param quote    {@code length}가 임계 이상이라 결정론적으로 인용이라고 볼 수 있는지
     */
    public record Overlap(int length, String fragment, boolean quote) {
    }

    /**
     * 두 텍스트의 최장 공통 부분문자열을 찾는다.
     *
     * <p>구현은 길이 {@code m×n} 대신 <b>직전 행만 들고 도는</b> 방식이다. 응답과 문서 청크가
     * 각각 수천 자면 전체 표는 수백만 칸이 되는데, 필요한 것은 직전 행뿐이라 메모리를
     * {@code O(min(m,n))}으로 줄일 수 있다.
     *
     * @param a         비교 대상 1 (예: 응답 문장)
     * @param b         비교 대상 2 (예: 사내 문서 청크)
     * @param threshold 인용으로 확정할 최소 연속 일치 길이. 6.2의 기본값은 40
     */
    public Overlap detect(String a, String b, int threshold) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return new Overlap(0, "", false);
        }

        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        int best = 0;
        int bestEndInA = 0;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    current[j] = previous[j - 1] + 1;
                    if (current[j] > best) {
                        best = current[j];
                        bestEndInA = i;
                    }
                } else {
                    current[j] = 0;
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            java.util.Arrays.fill(current, 0);
        }

        String fragment = a.substring(bestEndInA - best, bestEndInA);
        return new Overlap(best, fragment, best >= threshold);
    }
}
