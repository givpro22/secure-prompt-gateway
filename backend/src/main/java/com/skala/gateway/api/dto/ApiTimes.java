package com.skala.gateway.api.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 응답 시각 변환 (기획서 8.1 "시각: ISO 8601, UTC").
 *
 * <p>DB는 {@code timestamptz}라 서버 로컬 오프셋({@code +09:00})으로 읽힌다. 그대로 내보내면
 * 같은 순간이 응답마다 다른 문자열로 보이고, FE가 문자열 비교로 정렬하면 순서가 뒤집힌다.
 * 경계에서 한 번 UTC로 고정한다.
 */
final class ApiTimes {

    private ApiTimes() {
    }

    static OffsetDateTime utc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
