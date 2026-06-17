package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CrewCloseNotificationServiceIntegrationTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Autowired private CrewCloseNotificationService crewCloseNotificationService;

  @PersistenceContext private EntityManager entityManager;

  @MockBean private NotificationSender notificationSender;

  @Test
  void sendsCloseReminderOnlyToLockedParticipantsOfActiveCrewsEndingInThreeDays() {
    LocalDateTime now = LocalDateTime.now(SEOUL);
    LocalDate targetDate = LocalDate.now(SEOUL).plusDays(3);

    Member host = persistMember("host-close-it@example.com", "host-close-it");
    Member lockedMember = persistMember("locked-close-it@example.com", "locked-close-it");
    Member pendingMember = persistMember("pending-close-it@example.com", "pending-close-it");

    Crew targetCrew = persistCrew(host, "종료 예정 크루", targetDate.atTime(23, 59));
    targetCrew.activate(now);

    Crew wrongDateCrew = persistCrew(host, "날짜 제외 크루", targetDate.plusDays(1).atStartOfDay());
    wrongDateCrew.activate(now);

    persistCrew(host, "상태 제외 크루", targetDate.atTime(12, 0));

    CrewParticipant locked =
        CrewParticipant.create(targetCrew, lockedMember, 10_000L, now.minusDays(1));
    CrewParticipant pending =
        CrewParticipant.createPending(targetCrew, pendingMember, 10_000L, now.minusDays(1));

    entityManager.persist(locked);
    entityManager.persist(pending);
    entityManager.flush();
    entityManager.clear();

    crewCloseNotificationService.sendCloseReminders();

    ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
    ArgumentCaptor<NotificationPayload> payloadCaptor =
        ArgumentCaptor.forClass(NotificationPayload.class);

    then(notificationSender).should(times(1)).send(memberCaptor.capture(), payloadCaptor.capture());
    assertThat(memberCaptor.getValue().getUuid()).isEqualTo(lockedMember.getUuid());
    assertThat(memberCaptor.getValue().getUuid()).isNotEqualTo(pendingMember.getUuid());

    NotificationPayload payload = payloadCaptor.getValue();
    assertThat(payload.eventType()).isEqualTo("CREW_CLOSE_SOON");
    assertThat(payload.resourceType()).isEqualTo("crew");
    assertThat(payload.resourceId()).isEqualTo(String.valueOf(targetCrew.getId()));
    assertThat(payload.deepLink()).isEqualTo("dondok://crews/" + targetCrew.getId());
  }

  private Member persistMember(String email, String nickname) {
    Member member = Member.create(email, "password-hash", nickname);
    entityManager.persist(member);
    return member;
  }

  private Crew persistCrew(Member host, String title, LocalDateTime endAt) {
    LocalDateTime now = LocalDateTime.now(SEOUL);
    Crew crew =
        Crew.create(
            host,
            title,
            "크루 설명",
            null,
            "EXERCISE",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            now,
            10_000L,
            2,
            5,
            now.minusDays(5),
            now.minusDays(1),
            endAt);
    entityManager.persist(crew);
    return crew;
  }
}
