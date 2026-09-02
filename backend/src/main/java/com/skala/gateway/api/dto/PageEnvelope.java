package com.skala.gateway.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 응답 봉투 (기획서 8.1, 계약서 §0 C1).
 *
 * <p><b>배열을 직접 반환하지 않는다.</b> 한 엔드포인트라도 배열을 그대로 주면 FE가
 * {@code .items}를 꺼내지 않아 {@code filter is not a function}이 난다 — 경계면 버그의 전형이다.
 * 비페이징 엔드포인트({@code /departments}, {@code /users}, {@code /policies})도 같은 봉투를
 * 쓰고 {@code page=0}, {@code size=total=items.length}로 채운다. FE의 목록 접근 코드가
 * {@code res.data.items} 하나로 통일된다.
 */
public record PageEnvelope<T>(List<T> items, int page, int size, long total) {

    /** 페이징하지 않는 목록. */
    public static <T> PageEnvelope<T> ofAll(List<T> items) {
        return new PageEnvelope<>(items, 0, items.size(), items.size());
    }

    /** 페이징 목록. {@code size}는 요청값이 아니라 실제 페이지 크기다. */
    public static <T> PageEnvelope<T> of(Page<?> page, List<T> items) {
        return new PageEnvelope<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
