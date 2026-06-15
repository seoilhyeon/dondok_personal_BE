package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.MissionLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionLogRepository extends JpaRepository<MissionLog, Long> {

  // 같은 크루(crewParticipant.crew.id) 안에 동일 image_hash를 가진 로그가 이미 있는지 (중복 인증 signal)
  boolean existsByCrewParticipantCrewIdAndImageHash(Long crewId, String imageHash);

  // 당일 cadence slot 내 특정 상태의 인증 로그가 이미 있는지 (재업로드 거절 판정용).
  boolean
      existsByCrewParticipantIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
          Long crewParticipantId,
          CertificationStatus certificationStatus,
          LocalDateTime startInclusive,
          LocalDateTime endExclusive);

  @Query(
      """
      select m
      from MissionLog m
      where m.crewParticipant.crew.id = :crewId
        and m.certificationStatus = :certificationStatus
        and m.serverTime >= :startInclusive
        and m.serverTime <= :endInclusive
      """)
  List<MissionLog>
      findByCrewIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThanEqual(
          @Param("crewId") Long crewId,
          @Param("certificationStatus") CertificationStatus certificationStatus,
          @Param("startInclusive") LocalDateTime startInclusive,
          @Param("endInclusive") LocalDateTime endInclusive);

  @Query(
      """
      select m
      from MissionLog m
      where m.crewParticipant.crew.id = :crewId
        and m.certificationStatus = :certificationStatus
        and m.serverTime >= :startInclusive
        and m.serverTime < :endExclusive
      """)
  List<MissionLog>
      findByCrewIdAndCertificationStatusAndServerTimeGreaterThanEqualAndServerTimeLessThan(
          @Param("crewId") Long crewId,
          @Param("certificationStatus") CertificationStatus certificationStatus,
          @Param("startInclusive") LocalDateTime startInclusive,
          @Param("endExclusive") LocalDateTime endExclusive);

  @Query(
      """
      select m
      from MissionLog m
      where m.crewParticipant.crew.id = :crewId
        and m.certificationStatus = com.oit.dondok.domain.mission.entity.CertificationStatus.SUCCESS
        and m.decisionType = com.oit.dondok.domain.mission.entity.ModerationDecisionType.MANUAL_APPROVE
        and m.serverTime >= :startInclusive
        and m.serverTime < :endExclusive
      """)
  List<MissionLog> findManuallyApprovedLogsForDailySettlementProjection(
      @Param("crewId") Long crewId,
      @Param("startInclusive") LocalDateTime startInclusive,
      @Param("endExclusive") LocalDateTime endExclusive);

  @Query(
      """
      select m
      from MissionLog m
      where m.crewParticipant.crew.id = :crewId
        and m.certificationStatus = com.oit.dondok.domain.mission.entity.CertificationStatus.SUCCESS
        and m.decisionType in (
          com.oit.dondok.domain.mission.entity.ModerationDecisionType.MANUAL_APPROVE,
          com.oit.dondok.domain.mission.entity.ModerationDecisionType.AUTO_APPROVE
        )
        and m.serverTime >= :startInclusive
        and m.serverTime < :endExclusive
      """)
  List<MissionLog> findFinalizedApprovedLogsForDailySettlementProjection(
      @Param("crewId") Long crewId,
      @Param("startInclusive") LocalDateTime startInclusive,
      @Param("endExclusive") LocalDateTime endExclusive);
}
