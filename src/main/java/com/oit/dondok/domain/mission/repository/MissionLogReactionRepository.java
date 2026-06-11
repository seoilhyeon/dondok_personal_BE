package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.MissionLogReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionLogReactionRepository extends JpaRepository<MissionLogReaction, Long> {
  // 멱등 추가: (mission_log_id, member_id, reaction_type) unique 충돌 시 no-op으로 흡수한다.
  // ON DUPLICATE KEY UPDATE에 같은 값을 대입하므로 실제 변경이 없어 updated_at도 갱신되지 않고,
  // 동시 중복 요청도 DB 레벨에서 1개로 수렴하며 예외를 던지지 않는다.
  // created_at/updated_at은 JPA auditing이 아니라 직접 INSERT하므로 명시적으로 채운다
  // (DB default(Flyway 스키마)에만 의존하지 않게 해 엔티티 기반 DDL/H2에서도 동작).
  @Modifying
  @Query(
      value =
          """
              INSERT INTO mission_log_reaction
                  (mission_log_id, member_id, reaction_type, created_at, updated_at)
              VALUES
                  (:missionLogId, :memberId, :reactionType, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
              ON DUPLICATE KEY UPDATE reaction_type = reaction_type
              """,
      nativeQuery = true)
  void upsert(
      @Param("missionLogId") Long missionLogId,
      @Param("memberId") Long memberId,
      @Param("reactionType") String reactionType);

  // 멱등 삭제: 매칭 row가 없어도 0건 삭제로 정상 종료하고, 다른 emoji token row는 유지한다.
  // 벌크 delete라 엔티티 로딩 없이 단일 statement로 처리한다(select 없음).
  @Modifying
  @Query(
      """
      delete from MissionLogReaction r
      where r.missionLog.id = :missionLogId
          and r.member.id = :memberId
          and r.reactionType = :reactionType
      """)
  void deleteReaction(
      @Param("missionLogId") Long missionLogId,
      @Param("memberId") Long memberId,
      @Param("reactionType") String reactionType);
}
