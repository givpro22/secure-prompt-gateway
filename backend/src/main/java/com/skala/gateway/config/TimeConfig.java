package com.skala.gateway.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 판정 기준일 (기획서 11.3 Config Isolation).
 *
 * <p>엠바고 해제 여부는 "오늘"에 달려 있다. {@code LocalDate.now()}를 엔진 안에서 직접 부르면
 * 테스트가 시스템 날짜에 묶이고, 리허설에서 미래 날짜를 흉내 낼 수도 없다. {@link Clock}을
 * 주입해 두면 둘 다 해결된다.
 *
 * <p>{@code gateway.embargo.reference-date}가 비어 있으면 실제 오늘이다. 값이 있으면 그 날짜로
 * 고정된다 — <b>발표 전 리허설에서 발표 당일을 흉내 내는 용도</b>다. 엠바고 규칙 2종의 해제일이
 * 2026-09-20과 2026-09-04이라, 그 사이 날짜로 고정해야 "하나는 걸리고 하나는 풀린" 장면이 나온다.
 *
 * <pre>
 * GATEWAY_EMBARGO_REFERENCE_DATE=2026-09-04 ./gradlew bootRun
 * </pre>
 *
 * <p>운영에서는 비워 둔다. 값이 박힌 채 배포되면 시간이 멈춘 게이트웨이가 된다.
 */
@Configuration
public class TimeConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeConfig.class);

    @Bean
    public Clock gatewayClock(@Value("${gateway.embargo.reference-date:}") String referenceDate) {
        if (referenceDate == null || referenceDate.isBlank()) {
            return Clock.systemDefaultZone();
        }
        LocalDate fixed = LocalDate.parse(referenceDate.trim());
        ZoneId zone = ZoneId.systemDefault();
        // 기준일을 쓰는 곳이 엠바고 만료 판정뿐이라 자정으로 고정해도 무해하다.
        // 다른 곳에서 Clock을 쓰기 시작하면 이 주석이 경고가 된다.
        log.warn("판정 기준일이 {}로 고정되었습니다. 리허설 설정이며 운영에서는 비워 두십시오.", fixed);
        return Clock.fixed(fixed.atStartOfDay(zone).toInstant(), zone);
    }
}
