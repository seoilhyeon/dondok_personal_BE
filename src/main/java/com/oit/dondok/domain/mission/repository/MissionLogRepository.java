package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.entity.MissionLog;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  // 방장 검수 처리에 필요한 미션 로그와 크루/ 방장 정보를 함계 조회한다.
  // 권한 검증에 crew.hsotMember가 필요하므로 fetch join으로 로딩
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select missionLog
      from MissionLog missionLog
      join fetch missionLog.crewParticipant participant
      join fetch participant.crew crew
      join fetch crew.hostMember host
      where missionLog.id = :missionLogId
      """)
  Optional<MissionLog> findByIdWithCrewForModeration(@Param("missionLogId") Long missionLogId);
}
