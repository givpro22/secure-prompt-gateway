package com.skala.gateway.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 검토 대상 텍스트를 문장으로 자른다.
 *
 * <p><b>LLM에게 위험 구간을 직접 인용시키지 않기 위한 장치다.</b> 모델에 구간을 뽑게 했더니
 * 원문과 글자가 달라져 돌아왔고({@code 2차} → {@code 2 차}) 오프셋 역산이 불가능해졌다.
 * 그래서 문장은 코드가 자르고 번호를 붙여 보내며, 모델은 번호와 라벨만 돌려준다. 근거 문장은
 * 우리가 가진 분할 결과에서 꺼내므로 어긋날 경로가 없다 (_workspace/05 §1-2).
 *
 * <p>문장 부호가 없는 입력도 있다. Case E의 엑셀 추출 텍스트가
 * {@code [백로그!2행] REL-0001 | ... | 런칭 2주 전 지표 확정} 형태라 마침표가 하나도 없다.
 * 그런 줄은 개행 단위로 남는다 — 잘게 쪼개려고 {@code |}까지 경계로 삼으면 표의 한 행이
 * 조각나 맥락이 사라진다.
 */
@Component
public class SentenceSplitter {

    /** 문장 종결로 보는 문자. 한국어 입력이라 물음표·느낌표까지만 본다. */
    private static final String TERMINATORS = ".!?";

    /** 이보다 짧은 조각은 앞 문장에 붙인다. "네." 같은 파편이 따로 판정되면 잡음만 는다. */
    private static final int MIN_LENGTH = 6;

    /**
     * 분할된 문장 하나.
     *
     * @param start 입력 텍스트 기준 시작 (포함)
     * @param end   입력 텍스트 기준 끝 (미포함)
     * @param text  {@code [start, end)} 구간의 문자열. 앞뒤 공백은 제거되어 있다
     */
    public record Sentence(int start, int end, String text) {
    }

    /**
     * 종결 부호와 개행을 경계로 자른다. 빈 조각과 너무 짧은 조각은 만들지 않는다.
     *
     * @param text 검토 대상. {@code null}이면 빈 목록
     */
    public List<Sentence> split(String text) {
        List<Sentence> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        int cursor = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean boundary = c == '\n' || (TERMINATORS.indexOf(c) >= 0 && endsSentence(text, i));
            if (boundary) {
                add(result, text, cursor, i + 1);
                cursor = i + 1;
            }
        }
        add(result, text, cursor, text.length());
        return result;
    }

    /**
     * 종결 부호 <b>다음이 공백이거나 텍스트 끝</b>일 때만 문장이 끝난 것으로 본다.
     *
     * <p>이 조건이 없으면 세 가지가 조용히 깨진다.
     * <ul>
     *   <li>{@code 3.8%} — 소수점에서 잘려 금액·비율이 든 문장이 두 조각이 된다</li>
     *   <li>{@code hong@example.com} — 도메인에서 잘려 이메일 정규식이 어느 조각에도 안 걸린다.
     *       그러면 사전 필터가 통과시켜 규칙 엔진의 영역이 LLM으로 넘어간다</li>
     *   <li>{@code [백로그!2행]} — Case E의 시트 좌표에서 잘려 표의 한 행이 조각난다</li>
     * </ul>
     */
    private static boolean endsSentence(String text, int i) {
        return i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1));
    }

    /** 앞뒤 공백을 뺀 실제 구간으로 좁혀 담는다. 짧은 조각은 직전 문장에 흡수시킨다. */
    private static void add(List<Sentence> result, String text, int from, int to) {
        int s = from;
        int e = to;
        while (s < e && Character.isWhitespace(text.charAt(s))) {
            s++;
        }
        while (e > s && Character.isWhitespace(text.charAt(e - 1))) {
            e--;
        }
        if (s >= e) {
            return;
        }
        if (e - s < MIN_LENGTH && !result.isEmpty()) {
            Sentence last = result.remove(result.size() - 1);
            result.add(new Sentence(last.start(), e, text.substring(last.start(), e)));
            return;
        }
        result.add(new Sentence(s, e, text.substring(s, e)));
    }
}
