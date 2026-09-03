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

    /**
     * Case E — 이OO (개발팀, userId 1). 엠바고 차단 · 403.
     *
     * <p>개발팀이 4분기 릴리스 백로그 엑셀을 넣은 것이다. 프론트가 추출한 텍스트가
     * {@code [시트!N행] 셀 | 셀} 형태라 원문 오프셋 기반 마스킹이 그대로 동작하고,
     * 차단 사유에 위치가 함께 남는다 (docs/demo-files/make_demo_xlsx.py).
     *
     * <p>유출 의도도 개인정보도 없다. 그런데 외부 LLM에 넣는 순간 그것은 공개다.
     */
    public static final String CASE_E =
            "[백로그!2행] REL-0001 | SKALA NOVA | NOVA 추천 위젯 A/B 테스트 | 김OO | 리뷰 | S-23 | 런칭 2주 전 지표 확정";

    /**
     * Case E-2 — 같은 파일의 다른 행. 엠바고가 이미 풀린 제품이라 걸리지 않는다.
     *
     * <p>E와 E-2가 같은 형태의 규칙에 같은 방식으로 걸리는데 결과가 갈린다. 부서로 갈리는
     * Case B/C와 같은 증명을 시간 축에서 한 번 더 하는 자리다.
     */
    public static final String CASE_E_RELEASED =
            "[백로그!3행] REL-0002 | SKALA ATLAS | 아틀라스 대시보드 위젯 추가 | 정OO | 완료 | S-24 | 정식 출시 후 개선건";

    public static final long USER_DEV = 1L;
    public static final long USER_SALES = 2L;
    public static final long USER_HR = 3L;
    /** 박OO · 정보보안팀. 확정은 이 계정만 할 수 있다 (0.5.1 D24) */
    public static final long USER_ADMIN = 4L;

    public static final long DEPT_DEV = 1L;
    public static final long DEPT_SALES = 2L;
    public static final long DEPT_HR = 3L;
    public static final long DEPT_PR = 5L;

    /** 발표 당일. 엠바고 규칙 2종의 해제일(09-20 / 09-04) 사이에 있어 하나만 걸린다. */
    public static final String DEMO_REFERENCE_DATE = "2026-09-04";

    private DemoCases() {
    }
}
