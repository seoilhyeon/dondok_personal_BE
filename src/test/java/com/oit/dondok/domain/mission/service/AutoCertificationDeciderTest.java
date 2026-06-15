package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AutoCertificationDeciderTest {

  private final AutoCertificationDecider decider = new AutoCertificationDecider();

  // 중복 해시가 있으면 EXIF가 정상이더라도 자동 반려한다.
  @Test
  void rejectDuplicateHash() {
    assertThat(decider.isApproved(missionLog(ExifRisk.NORMAL, true))).isFalse();
  }

  // EXIF 위험도가 NORMAL이면 자동 승인한다.
  @Test
  void approveNormalExifRisk() {
    assertThat(decider.isApproved(missionLog(ExifRisk.NORMAL, false))).isTrue();
  }

  // EXIF가 없지만 이미지가 제출된 로그는 MVP 정책에 따라 자동 승인한다.
  @Test
  void approveMissingExifRisk() {
    assertThat(decider.isApproved(missionLog(ExifRisk.MISSING, false))).isTrue();
  }

  // EXIF 시간이 유효하지 않으면 자동 반려한다.
  @Test
  void rejectInvalidExifRisk() {
    assertThat(decider.isApproved(missionLog(ExifRisk.TIME_INVALID, false))).isFalse();
  }

  private MissionLog missionLog(ExifRisk exifRisk, boolean duplicateHash) {
    LocalDateTime now = LocalDateTime.of(2026, 6, 10, 8, 0);
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
}
