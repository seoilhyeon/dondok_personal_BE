package com.oit.dondok.domain.mission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.config.QuerydslConfig;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionLog;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class, FeedQueryRepository.class})
class FeedQueryRepositoryTest {

  private static final LocalDateTime SERVER_TIME = LocalDateTime.of(2026, 6, 9, 6, 5, 10);
  private static final LocalDateTime EXIF_TAKEN_AT = LocalDateTime.of(2026, 6, 9, 5, 55, 10);

  @Autowired private TestEntityManager entityManager;
  @Autowired private FeedQueryRepository feedQueryRepository;

  @Test
  void findFeedItemsProjectsExifAndDuplicateSignals() {
    Crew crew = givenCrewWithMissionLog(ExifRisk.TIME_INVALID, true);

    List<FeedItemRow> rows =
        feedQueryRepository.findFeedItems(List.of(crew.getId()), null, null, null, null, 20);

    assertThat(rows).hasSize(1);
    FeedItemRow row = rows.get(0);
    assertThat(row.exifTakenAt()).isEqualTo(EXIF_TAKEN_AT);
    assertThat(row.exifRisk()).isEqualTo(ExifRisk.TIME_INVALID);
    assertThat(row.duplicateHash()).isTrue();
  }

  @Test
  void findFeedItemByIdProjectsExifAndDuplicateSignals() {
    Crew crew = givenCrewWithMissionLog(ExifRisk.MISSING, false);
    Long missionLogId =
        feedQueryRepository
            .findFeedItems(List.of(crew.getId()), null, null, null, null, 20)
            .get(0)
            .missionLogId();

    FeedItemRow row = feedQueryRepository.findFeedItemById(missionLogId).orElseThrow();

    assertThat(row.exifTakenAt()).isEqualTo(EXIF_TAKEN_AT);
    assertThat(row.exifRisk()).isEqualTo(ExifRisk.MISSING);
    assertThat(row.duplicateHash()).isFalse();
  }

  private Crew givenCrewWithMissionLog(ExifRisk exifRisk, boolean duplicateHash) {
    Member member = entityManager.persistAndFlush(Member.create("feed@example.com", "hash", "피드"));
    Crew crew = entityManager.persistAndFlush(createCrew(member));
    CrewParticipant participant =
        entityManager.persistAndFlush(
            CrewParticipant.create(crew, member, 10_000L, LocalDateTime.of(2026, 5, 31, 9, 0)));
    entityManager.persistAndFlush(
        MissionLog.createPendingReview(
            participant,
            "mission/feed/9001.jpg",
            "valid caption",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            EXIF_TAKEN_AT,
            exifRisk,
            duplicateHash,
            SERVER_TIME));
    entityManager.clear();
    return crew;
  }

  private Crew createCrew(Member host) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 9, 0);
    return Crew.create(
        host,
        "feed crew",
        "feed crew description",
        null,
        "OTHER",
        "{}",
        HostPolicyVersion.HOST_POLICY_V1,
        now,
        10_000L,
        2,
        5,
        now.plusDays(3),
        now.plusDays(4),
        now.plusDays(30));
  }
}
