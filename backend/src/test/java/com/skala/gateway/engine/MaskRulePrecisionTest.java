package com.skala.gateway.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.gateway.DemoCases;
import com.skala.gateway.domain.PolicyRule;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.repository.PolicyRuleRepository;
import com.skala.gateway.service.RosterExpander;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마스킹 규칙의 정밀도 (0.5.1 D21).
 *
 * <p><b>미탐만 막는 테스트는 절반이다.</b> 오탐이 늘면 사용자가 게이트웨이를 우회하고,
 * 그때 통제율은 0이 된다. 그래서 "잡아야 할 것"과 "잡으면 안 되는 것"을 같은 무게로 고정한다.
 *
 * <p>정규식은 DB에서 읽는다. 패턴을 여기 상수로 박으면 {@code policy_rule} 테이블의 존재
 * 이유가 사라지고, 시드와 테스트가 조용히 갈라진다.
 */
@SpringBootTest
@Transactional
class MaskRulePrecisionTest {

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private PolicyRuleRepository policyRuleRepository;

    @Autowired
    private RosterExpander rosterExpander;

    /** 인사팀(HR)은 PII 6종이 전부 적용되고 엠바고는 매핑되지 않아 잡음이 없다. */
    private List<PolicyRule> rules() {
        return rosterExpander.expand(policyRuleRepository.findActiveByDept(DemoCases.DEPT_HR));
    }

    private EngineVerdict eval(String text) {
        return ruleEngine.evaluate(text, rules());
    }

    private void masksAs(String text, String label, String code) {
        EngineVerdict v = eval(text);
        assertThat(v.decision()).as("판정 — \"%s\"", text).isEqualTo(FinalDecision.MASK);
        assertThat(v.findings()).extracting(RuleHit::code).as("규칙 — \"%s\"", text).contains(code);
        assertThat(v.maskedText()).as("마스킹본 — \"%s\"", text).contains(label);
    }

    private void allows(String text) {
        EngineVerdict v = eval(text);
        assertThat(v.decision()).as("오탐 — \"%s\" 는 걸리면 안 된다", text).isEqualTo(FinalDecision.ALLOW);
    }

    @Nested
    @DisplayName("주민번호 — 월·일 범위와 숫자 경계")
    class Rrn {
        @Test void 잡는다() {
            masksAs("담당자 주민번호 900101-1234567 기준으로 조회", "[주민번호]", "PII-RRN-01");
            masksAs("번호 9001011234567 확인", "[주민번호]", "PII-RRN-01");
            masksAs("001231-4567890 조회", "[주민번호]", "PII-RRN-01");
        }

        @Test void 안잡는다() {
            allows("주문번호 991301-1234567 확인");   // 13월
            allows("주문번호 990132-1234567 확인");   // 32일
            allows("일련번호 1900101-1234567 확인");  // 앞에 숫자
            allows("코드 900101-5234567 확인");       // 성별자리 5
        }
    }

    @Nested
    @DisplayName("카드번호 — 구분자 일관성")
    class Card {
        @Test void 잡는다() {
            masksAs("카드 1234-5678-9012-3456 결제", "[카드번호]", "PII-CARD-02");
            masksAs("카드 1234567890123456 결제", "[카드번호]", "PII-CARD-02");
        }

        @Test void 안잡는다() {
            allows("코드 1234-5678-90123456 참고");  // 구분자 혼용
            allows("코드 12345678901234567 참고");   // 17자리
        }
    }

    @Nested
    @DisplayName("휴대전화 — 구분자 일관성")
    class Phone {
        @Test void 잡는다() {
            masksAs("지원자 연락처 010-1234-5678 로 안내", "[전화번호]", "PII-PHONE-03");
            masksAs("연락처 01012345678 로 안내", "[전화번호]", "PII-PHONE-03");
            masksAs("연락처 010-123-4567 로 안내", "[전화번호]", "PII-PHONE-03");
        }

        @Test void 안잡는다() {
            allows("코드 0101234-5678 참고");        // 반쪽 구분자
            allows("코드 77010-1234-5678 참고");    // 앞에 숫자가 붙어 전화번호가 아니다
        }

        @Test
        @DisplayName("13자리 숫자열은 전화번호가 아니라 주민번호로 잡힌다 — 규칙이 겹치는 자리")
        void 주민번호와_겹치는_구간() {
            // 9901012345678 = 99년 01월 01일 + 성별 2 + 6자리. 형식상 유효한 주민번호다.
            // 전화번호 규칙은 앞자리 숫자 때문에 걸리지 않고, 더 심각한 쪽이 잡는다.
            EngineVerdict v = eval("번호 9901012345678 참고");
            assertThat(v.findings()).extracting(RuleHit::code).containsExactly("PII-RRN-01");
            assertThat(v.maskedText()).isEqualTo("번호 [주민번호] 참고");
        }
    }

    @Nested
    @DisplayName("이메일 — 최상위 도메인은 알파벳")
    class Email {
        @Test void 잡는다() {
            masksAs("hong@example.com 으로 회신", "[이메일]", "PII-EMAIL-04");
            masksAs("a.b+c@sub.example.co.kr 로 회신", "[이메일]", "PII-EMAIL-04");
        }

        @Test void 안잡는다() {
            allows("주소는 a@b.1 형태입니다");   // 숫자 TLD
        }
    }

    @Nested
    @DisplayName("사업자등록번호 — 신규")
    class BizNo {
        @Test void 잡는다() {
            masksAs("사업자 123-45-67890 확인해줘", "[사업자번호]", "PII-BIZNO-05");
        }

        @Test void 안잡는다() {
            allows("코드 1234-45-67890 확인");  // 앞자리 4개
        }
    }

    @Nested
    @DisplayName("계좌번호 — 금융 문맥이 있을 때만")
    class Account {
        @Test void 잡는다() {
            masksAs("입금 계좌 110-234-567890 입니다", "[계좌번호]", "PII-ACCOUNT-06");
            masksAs("송금 계좌번호: 110-234-567890", "[계좌번호]", "PII-ACCOUNT-06");
        }

        @Test
        @DisplayName("문맥이 없으면 잡지 않는다 — 형식만으로는 버전 번호와 구분되지 않는다")
        void 안잡는다() {
            allows("빌드 버전 110-234-567890 참고");
        }

        @Test
        @DisplayName("lookbehind는 마스킹 구간에 들어가지 않아 '계좌'라는 단어는 남는다")
        void 문맥어는_보존된다() {
            EngineVerdict v = eval("입금 계좌 110-234-567890 입니다");
            assertThat(v.maskedText()).isEqualTo("입금 계좌 [계좌번호] 입니다");
        }
    }
}
