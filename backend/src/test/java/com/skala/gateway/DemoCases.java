package com.skala.gateway;

/**
 * 데모 케이스 4종의 입력 (기획서 10.4).
 *
 * <p><b>한 글자도 바꾸지 않는다.</b> 이 문자열이 발표 현장에서 그대로 입력되고, span 기대값
 * ([18,56]·[73,87] 등)이 전부 이 길이에 걸려 있다. 축약하거나 오타를 고치면 기대값이 통째로
 * 어긋난다 — 8.4 예시의 span이 실제와 맞지 않았던 것이 그 사례다 (계약서 C4-2).
 *
 * <p>B와 C는 <b>같은 문장</b>이다. 부서만 다르고 결과가 갈리는 것이 부서↔정책 N:M 설계의
 * 증명이며 데모의 핵심 장면이다.
 */
public final class DemoCases {

    /** Case A — 이OO (개발팀, userId 1). BLOCK · finding 2건 · 403 */
    public static final String CASE_A =
            "이 에러 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 로 붙었는데 담당자 주민번호 900101-1234567 기준으로 조회하면 타임아웃 나";

    /** Case B — 김OO (영업팀, userId 2). PENDING · finding 1건 · 202 */
    public static final String CASE_B = "A사 차세대 프로젝트 오픈 일정이 언제였지?";

    /** Case C — 이OO (개발팀, userId 1). B와 같은 문장인데 P-CONF 미적용이라 ALLOW · 200 */
    public static final String CASE_C = CASE_B;

    /** Case D — 정OO (인사팀, userId 3). MASK · finding 1건 · 200 */
    public static final String CASE_D = "지원자 연락처 010-1234-5678 로 면접 안내 문자 초안 써줘";

    public static final long USER_DEV = 1L;
    public static final long USER_SALES = 2L;
    public static final long USER_HR = 3L;

    public static final long DEPT_DEV = 1L;
    public static final long DEPT_SALES = 2L;
    public static final long DEPT_HR = 3L;

    private DemoCases() {
    }
}
