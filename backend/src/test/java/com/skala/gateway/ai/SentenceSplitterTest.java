package com.skala.gateway.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.skala.gateway.DemoCases;

class SentenceSplitterTest {

    private final SentenceSplitter splitter = new SentenceSplitter();

    @Test
    @DisplayName("start/end로 원문을 자르면 text와 같다 — 오프셋이 어긋나지 않는다")
    void offsetsAreExact() {
        String text = "우리 마지노선은 42억이야. 회의실 예약해줘.\n분기 마감이 다음주야";

        for (SentenceSplitter.Sentence s : splitter.split(text)) {
            assertThat(text.substring(s.start(), s.end()))
                    .as("문장 [%d,%d)", s.start(), s.end())
                    .isEqualTo(s.text());
        }
    }

    @Test
    @DisplayName("3.8% 같은 소수점을 문장 끝으로 보지 않는다")
    void decimalPointIsNotATerminator() {
        List<SentenceSplitter.Sentence> sentences =
                splitter.split("목표 전환율은 3.8%이며 전년 대비 0.6%p 올랐다.");

        assertThat(sentences).hasSize(1);
    }

    @Test
    @DisplayName("Case E의 엑셀 추출 텍스트는 한 행이 한 문장으로 남는다")
    void spreadsheetRowStaysWhole() {
        List<SentenceSplitter.Sentence> sentences = splitter.split(DemoCases.CASE_E);

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo(DemoCases.CASE_E);
    }

    @Test
    @DisplayName("짧은 파편은 앞 문장에 흡수된다")
    void shortFragmentIsAbsorbed() {
        List<SentenceSplitter.Sentence> sentences = splitter.split("계약 조건 확인해줘. 응.");

        assertThat(sentences).hasSize(1);
    }

    @Test
    @DisplayName("빈 입력과 null은 빈 목록")
    void emptyInput() {
        assertThat(splitter.split(null)).isEmpty();
        assertThat(splitter.split("   ")).isEmpty();
    }
}
