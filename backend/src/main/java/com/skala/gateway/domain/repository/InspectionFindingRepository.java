package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.InspectionFinding;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionFindingRepository extends JpaRepository<InspectionFinding, Long> {

    /**
     * 상세 화면의 {@code findings[]}. <b>RULE이 먼저 오고</b> 그 안에서는 생성 순이다.
     *
     * <p>정렬을 파생 쿼리 이름(`OrderBySourceAsc`)에 맡기면 안 된다.
     * {@code @Enumerated(STRING)}이라 DB에서 문자열 정렬되고 {@code 'AI' < 'RULE'}이므로
     * AI 후보가 먼저 나온다. 실제로 그렇게 나가던 것을 고친 자리다.
     *
     * <p>순서가 화면의 주장이다 — SCR-02 상세 패널이 "규칙 판정(결정)"을 위에,
     * "AI 제안(후보)"을 아래에 놓아 "규칙이 결정하고 AI는 제안한다"는 책임 경계(4장)를
     * 시각적으로 전달한다. 뒤집히면 AI 후보가 먼저 눈에 들어온다.
     *
     * <p>{@code OrderBySourceDesc}로 바꾸는 것도 답이 아니다. 지금은 맞지만
     * {@code FindingSource}에 값이 하나라도 추가되면 알파벳 순서가 다시 어긋난다.
     * CASE로 명시한다.
     */
    @Query("""
            select f
              from InspectionFinding f
             where f.inspection.inspectionId = :inspectionId
             order by case f.source
                           when com.skala.gateway.domain.enums.FindingSource.RULE then 0
                           else 1 end,
                      f.findingId asc
            """)
    List<InspectionFinding> findByInspectionIdRuleFirst(@Param("inspectionId") Long inspectionId);

    /**
     * @deprecated 이름이 실제 정렬과 어긋난다. {@link #findByInspectionIdRuleFirst}를 쓴다.
     *             호출부가 남아 있어 위임만 해 둔 것이며, 옮기고 나면 삭제한다.
     */
    @Deprecated
    default List<InspectionFinding> findByInspectionInspectionIdOrderBySourceAscFindingIdAsc(
            Long inspectionId) {
        return findByInspectionIdRuleFirst(inspectionId);
    }

    /**
     * 목록 행의 {@code ruleCount} (계약서 §1-6). D1 중첩 억제 후의 값이라
     * Case A에서 2가 된다.
     *
     * <p>페이지 단위로 한 번에 센다. 행마다 count 쿼리를 날리면 20행에 20쿼리다.
     */
    @Query("""
            select f.inspection.inspectionId as inspectionId, count(f) as ruleCount
              from InspectionFinding f
             where f.inspection.inspectionId in :inspectionIds
               and f.source = com.skala.gateway.domain.enums.FindingSource.RULE
             group by f.inspection.inspectionId
            """)
    List<RuleCountRow> countRuleFindings(@Param("inspectionIds") Collection<Long> inspectionIds);

    /** {@link #countRuleFindings} 결과 투영. */
    interface RuleCountRow {
        Long getInspectionId();

        long getRuleCount();
    }
}
