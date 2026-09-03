package com.skala.gateway.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 규칙 엔진이 이미 본 문장을 LLM 호출에서 제외한다 (_workspace/05 §1-4).
 *
 * <p>제외 기준은 둘이다.
 * <ol>
 *   <li>마스킹 라벨을 포함한 문장 — 이미 처리가 끝난 자리다</li>
 *   <li>활성 REGEX 규칙에 매칭되는 문장 — 규칙 엔진의 영역이다 (기획서 9.2 금지 조항)</li>
 * </ol>
 *
 * <p>라벨과 패턴을 코드에 박지 않고 {@link RuleCoverageSource}로 받는 이유는 정책이 DB에
 * 있기 때문이다. 시드를 복사해 두면 규칙을 하나 추가할 때마다 여기가 조용히 낡는다.
 */
@Component
public class SentenceFilter {

    private static final Logger log = LoggerFactory.getLogger(SentenceFilter.class);

    private final ObjectProvider<RuleCoverageSource> coverageProvider;

    public SentenceFilter(ObjectProvider<RuleCoverageSource> coverageProvider) {
        this.coverageProvider = coverageProvider;
    }

    /**
     * 판정에 넘길 문장만 남긴다.
     *
     * <p>{@link RuleCoverageSource} 구현체가 없으면 필터 없이 전부 통과시킨다. 필터가 없어
     * 오탐이 늘어나는 것과 검사 자체가 멈추는 것 중에서는 전자가 낫다. 대신 경고를 남긴다.
     */
    public List<SentenceSplitter.Sentence> retain(List<SentenceSplitter.Sentence> sentences) {
        RuleCoverageSource coverage = coverageProvider.getIfAvailable();
        if (coverage == null) {
            log.warn("RuleCoverageSource 구현체가 없어 문장 사전 필터를 건너뜁니다. 오탐이 늘어납니다.");
            return sentences;
        }

        List<String> labels = coverage.maskLabels();
        List<Pattern> patterns = compile(coverage.regexPatterns());

        List<SentenceSplitter.Sentence> kept = new ArrayList<>();
        for (SentenceSplitter.Sentence sentence : sentences) {
            String reason = coveredBy(sentence.text(), labels, patterns);
            if (reason == null) {
                kept.add(sentence);
            } else {
                log.debug("문장 제외({}) — {}", reason, abbreviate(sentence.text()));
            }
        }
        return kept;
    }

    private static String coveredBy(String text, List<String> labels, List<Pattern> patterns) {
        for (String label : labels) {
            if (label != null && !label.isBlank() && text.contains(label)) {
                return "마스킹 라벨 " + label;
            }
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return "규칙 매칭 " + pattern.pattern();
            }
        }
        return null;
    }

    /**
     * 컴파일 실패한 패턴은 버리고 나머지로 계속한다. 시드의 정규식 하나가 잘못됐다고 AI 검사
     * 전체가 멈추면 안 된다.
     */
    private static List<Pattern> compile(List<String> raw) {
        List<Pattern> compiled = new ArrayList<>();
        for (String p : raw) {
            if (p == null || p.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(p));
            } catch (PatternSyntaxException e) {
                log.warn("사전 필터에서 정규식을 건너뜁니다: {} — {}", p, e.getMessage());
            }
        }
        return compiled;
    }

    private static String abbreviate(String text) {
        return text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }
}
