package com.skala.gateway.api.dto;

import com.skala.gateway.domain.AppUser;

/**
 * {@code finding.reviewedBy} (계약서 §2).
 *
 * <p>DB에서는 FK(BIGINT)지만 API에서는 {@code {userId, name}} 객체다. 화면이 "박OO 확정"을
 * 그려야 하므로 id만으로는 부족하고, 이 한 자리 때문에 FE가 사용자 목록을 별도 조회하게 만들
 * 이유가 없다.
 */
public record UserRefDto(Long userId, String name) {

    public static UserRefDto from(AppUser user) {
        return user == null ? null : new UserRefDto(user.getUserId(), user.getName());
    }
}
