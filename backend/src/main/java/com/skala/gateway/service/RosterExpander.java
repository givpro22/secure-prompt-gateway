package com.skala.gateway.service;

import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.enums.RuleType;
import com.skala.gateway.domain.repository.CustomerRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ROSTER 규칙을 정규식으로 펼친다 (0.5.1 D23).
 *
 * <p>고객 명단은 {@code customer} 테이블에 있고 규칙 행의 {@code pattern}에는 조회할 컬럼명
 * ({@code name} 또는 {@code given_name})만 있다. 판정 직전에 명단을 읽어 대안 정규식으로
 * 조립한다. 엔진은 REGEX만 알면 되고, 명단이 바뀌어도 규칙 행은 그대로다.
 */
@Component
public class RosterExpander {

    private static final Logger log = LoggerFactory.getLogger(RosterExpander.class);

    /** 앞뒤 한글 경계. '서준이가'의 서준, '우진공업'의 우진 같은 접사 결합을 거른다. */
    private static final String PREFIX = "(?<![가-힣])(?:";
    private static final String SUFFIX = ")(?![가-힣])";

    static final String FIELD_NAME = "name";
    static final String FIELD_GIVEN_NAME = "given_name";

    private final CustomerRepository customerRepository;

    public RosterExpander(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * ROSTER 규칙만 골라 정규식 규칙으로 바꾼다. 나머지는 그대로 통과시킨다.
     *
     * <p>명단이 비면 그 규칙을 <b>목록에서 뺀다</b>. 빈 대안 {@code (?:)}은 모든 위치에서
     * 길이 0으로 매칭되어 문서 전체가 걸린다.
     */
    public List<PolicyRule> expand(List<PolicyRule> rules) {
        List<PolicyRule> out = new ArrayList<>(rules.size());
        for (PolicyRule rule : rules) {
            if (rule.getRuleType() != RuleType.ROSTER) {
                out.add(rule);
                continue;
            }
            List<String> names = lookup(rule);
            if (names.isEmpty()) {
                log.warn("ROSTER 규칙 {}의 명단이 비어 있어 이번 판정에서 제외합니다. customer 테이블을 확인하십시오.",
                        rule.getCode());
                continue;
            }
            out.add(rule.materializedAsRegex(toPattern(names)));
        }
        return List.copyOf(out);
    }

    private List<String> lookup(PolicyRule rule) {
        String field = rule.getPattern() == null ? "" : rule.getPattern().trim();
        return switch (field) {
            case FIELD_NAME -> customerRepository.findActiveNames();
            case FIELD_GIVEN_NAME -> customerRepository.findActiveGivenNames();
            // 규칙 행이 잘못 들어온 것이다. 조용히 통과시키면 검사 없이 지나간 프롬프트가
            // ALLOWED로 기록되어 감사 기록 자체가 거짓이 된다.
            default -> throw new IllegalStateException(
                    "ROSTER 규칙 " + rule.getCode() + "의 pattern이 조회 가능한 컬럼이 아닙니다: '" + field + "'");
        };
    }

    /**
     * 이름을 {@link Pattern#quote}로 감싼다. 한글 이름에 정규식 메타문자가 들어올 일은 없지만,
     * 명단은 외부에서 적재되는 데이터라 그 가정을 코드가 떠안지 않는다.
     */
    private static String toPattern(List<String> names) {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(Pattern.quote(names.get(i)));
        }
        return sb.append(SUFFIX).toString();
    }
}
