package com.oit.dondok.domain.mission.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MissionLogTest {

  @Test
  void approveAutomaticallyWithDuplicateImageHash() {
    MissionLog missionLog = pendingReviewLog(ExifRisk.NORMAL, true);

    assertThatThrownBy(() -> missionLog.approveAutomatically(host(missionLog), now()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_AUTO_APPROVAL_SIGNAL);
  }

  @Test
  void approveAutomaticallyWithInvalidExifRisk() {
    MissionLog missionLog = pendingReviewLog(ExifRisk.TIME_INVALID, false);

    assertThatThrownBy(() -> missionLog.approveAutomatically(host(missionLog), now()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_AUTO_APPROVAL_SIGNAL);
  }

  @Test
  void rejectAutomaticallyWithoutImageRiskSignal() {
    MissionLog missionLog = pendingReviewLog(ExifRisk.NORMAL, false);

    assertThatThrownBy(() -> missionLog.rejectAutomatically(host(missionLog), now()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.INVALID_AUTO_REJECTION_SIGNAL);
  }

  private MissionLog pendingReviewLog(ExifRisk exifRisk, boolean duplicateHash) {
    LocalDateTime now = now();
    Member host = Member.create("host@example.com", "password-hash", "host");
    Crew crew =
        Crew.create(
            host,
            "morning crew",
            "daily mission crew",
            null,
            "HEALTH",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            now,
            10_000L,
            2,
            10,
            now.plusDays(1),
            now.plusDays(2),
            now.plusDays(30));
    CrewParticipant participant = CrewParticipant.create(crew, host, 10_000L, now);
    return MissionLog.createPendingReview(
        participant,
        "mission/1/1/image.jpg",
        "daily mission done",
        "9b74c9897bac770ffc029102a200c5de8c0e9e5b9d3c9c7e5f4f5c1a2b3c4d5e",
        now,
        exifRisk,
        duplicateHash,
        now);
  }

  private Member host(MissionLog missionLog) {
    return missionLog.getCrewParticipant().getCrew().getHostMember();
  }

  private LocalDateTime now() {
    return LocalDateTime.of(2026, 6, 10, 12, 0);
  }
}
