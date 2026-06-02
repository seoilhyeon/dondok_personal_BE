package com.oit.dondok.domain.mission.repository;

import com.oit.dondok.domain.mission.entity.MissionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionLogRepository extends JpaRepository<MissionLog, Long> {
  // 같은 크루(crewParticipant.crew.id) 안에 동일 image_hash를 가진 로그가 이미 있는지 (중복 인증 signal)
  boolean existsByCrewParticipantCrewIdAndImageHash(Long crewId, String imageHash);
}
