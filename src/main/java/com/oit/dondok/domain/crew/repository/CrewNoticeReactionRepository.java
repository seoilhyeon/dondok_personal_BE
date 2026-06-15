package com.oit.dondok.domain.crew.repository;

import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewNoticeReactionRepository extends JpaRepository<CrewNoticeReaction, Long> {

  @EntityGraph(attributePaths = "member")
  List<CrewNoticeReaction> findByCrewNoticeId(Long crewNoticeId);

  @EntityGraph(attributePaths = "member")
  List<CrewNoticeReaction> findByCrewNoticeIdIn(Collection<Long> crewNoticeIds);

  Optional<CrewNoticeReaction> findByCrewNoticeIdAndMemberIdAndReactionType(
      Long crewNoticeId, Long memberId, String reactionType);

  // 멱등 추가: (crew_notice_id, member_id, reaction_type) unique 충돌 시 no-op으로 흡수한다.
  // 같은 트랜잭션에서 INSERT가 즉시 반영되어 직후 집계 조회가 방금 추가한 리액션을 본다.
  // created_at/updated_at은 명시적으로 채워 엔티티 기반 DDL/H2에서도 동작하게 한다.
  @Modifying
  @Query(
      value =
          """
          INSERT INTO crew_notice_reaction
              (crew_notice_id, member_id, reaction_type, created_at, updated_at)
          VALUES
              (:crewNoticeId, :memberId, :reactionType, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          ON DUPLICATE KEY UPDATE reaction_type = reaction_type
          """,
      nativeQuery = true)
  void upsert(
      @Param("crewNoticeId") Long crewNoticeId,
      @Param("memberId") Long memberId,
      @Param("reactionType") String reactionType);
}
