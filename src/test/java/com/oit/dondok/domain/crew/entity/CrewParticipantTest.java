package com.oit.dondok.domain.crew.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CrewParticipantTest {

  @Test
  void linkReleasedPointHistoryStoresHistoryReference() {
    Long participantId = 1L;
    Long memberId = 10L;
    Crew crew =
        Crew.create(
            buildMember(memberId),
            "제목",
            "설명",
            null,
            "운동",
            "{\"host\":true}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(),
            10_000L,
            1,
            3,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            LocalDateTime.now().plusDays(30));
    ReflectionTestUtils.setField(crew, "id", 10L);
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(participant, "id", participantId);
    ReflectionTestUtils.setField(participant, "member", buildMember(memberId));
    PointHistory history =
        PointHistory.create(
            buildMember(memberId),
            10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_RESERVE_RELEASE,
            PointReferenceType.CREW_PARTICIPANT,
            participantId,
            "crew:10:participant:1:reserve-release:1");

    participant.linkReleasedPointHistory(history);

    assertThat(participant).extracting("releasedPointHistory").isEqualTo(history);
  }

  @Test
  void linkReleasedPointHistoryCannotOverrideAlreadyLinkedHistory() {
    Long participantId = 1L;
    Crew crew =
        Crew.create(
            buildMember(10L),
            "제목",
            "설명",
            null,
            "운동",
            "{\"host\":true}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(),
            10_000L,
            1,
            3,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            LocalDateTime.now().plusDays(30));
    ReflectionTestUtils.setField(crew, "id", 10L);
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(participant, "id", participantId);
    ReflectionTestUtils.setField(participant, "member", buildMember(10L));
    PointHistory history =
        PointHistory.create(
            buildMember(10L),
            10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_RESERVE_RELEASE,
            PointReferenceType.CREW_PARTICIPANT,
            participantId,
            "crew:10:participant:1:reserve-release:1");
    PointHistory anotherHistory =
        PointHistory.create(
            buildMember(20L),
            5_000L,
            0L,
            10_000L,
            5_000L,
            PointTransactionType.CREW_RESERVE_RELEASE,
            PointReferenceType.CREW_PARTICIPANT,
            participantId,
            "crew:10:participant:1:reserve-release:1");

    participant.linkReleasedPointHistory(history);

    assertThatThrownBy(() -> participant.linkReleasedPointHistory(anotherHistory))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void linkReleasedPointHistoryRejectsNull() {
    Crew crew =
        Crew.create(
            buildMember(10L),
            "제목",
            "설명",
            null,
            "운동",
            "{\"host\":true}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(),
            10_000L,
            1,
            3,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            LocalDateTime.now().plusDays(30));
    ReflectionTestUtils.setField(crew, "id", 10L);
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(participant, "member", buildMember(10L));

    assertThatThrownBy(() -> participant.linkReleasedPointHistory(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void linkReleasedPointHistoryRejectsWrongTransactionType() {
    Crew crew =
        Crew.create(
            buildMember(10L),
            "제목",
            "설명",
            null,
            "운동",
            "{\"host\":true}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(),
            10_000L,
            1,
            3,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            LocalDateTime.now().plusDays(30));
    ReflectionTestUtils.setField(crew, "id", 10L);
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(10L), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "member", buildMember(10L));
    PointHistory history =
        PointHistory.create(
            buildMember(10L),
            10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_RESERVE_RELEASE,
            PointReferenceType.CREW_PARTICIPANT,
            1L,
            "crew:10:participant:1:reserve-release:1");
    ReflectionTestUtils.setField(
        history, "transactionType", PointTransactionType.CREW_DEPOSIT_RESERVE);

    assertThatThrownBy(() -> participant.linkReleasedPointHistory(history))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("release link에는 CREW_RESERVE_RELEASE 타입만 허용됩니다.");
  }

  @Test
  void linkReleasedPointHistoryRejectsWrongReferenceType() {
    Crew crew =
        Crew.create(
            buildMember(10L),
            "제목",
            "설명",
            null,
            "운동",
            "{\"host\":true}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(),
            10_000L,
            1,
            3,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            LocalDateTime.now().plusDays(30));
    ReflectionTestUtils.setField(crew, "id", 10L);
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(10L), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "member", buildMember(10L));
    PointHistory history =
        PointHistory.create(
            buildMember(10L),
            10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_RESERVE_RELEASE,
            PointReferenceType.CREW_PARTICIPANT,
            1L,
            "crew:10:participant:1:reserve-release:1");
    ReflectionTestUtils.setField(history, "referenceType", PointReferenceType.POINT_CHARGE);

    assertThatThrownBy(() -> participant.linkReleasedPointHistory(history))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("release link에는 CREW_PARTICIPANT 타입만 허용됩니다.");
  }

  @Test
  void linkReleasedPointHistoryRejectsMismatchedParticipant() {
    Crew crew =
        Crew.create(
            buildMember(10L),
            "제목",
            "설명",
            null,
            "운동",
            "{\"host\":true}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(),
            10_000L,
            1,
            3,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            LocalDateTime.now().plusDays(30));
    ReflectionTestUtils.setField(crew, "id", 10L);
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(10L), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "member", buildMember(10L));
    PointHistory history =
        PointHistory.create(
            buildMember(20L),
            10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_RESERVE_RELEASE,
            PointReferenceType.CREW_PARTICIPANT,
            2L,
            "crew:10:participant:2:reserve-release:1");

    assertThatThrownBy(() -> participant.linkReleasedPointHistory(history))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("동일 참가자");
  }

  @Test
  void linkReleasedPointHistoryRejectsMismatchedMember() {
    Crew crew =
        Crew.create(
            buildMember(10L),
            "제목",
            "설명",
            null,
            "운동",
            "{\"host\":true}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(),
            10_000L,
            1,
            3,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            LocalDateTime.now().plusDays(30));
    ReflectionTestUtils.setField(crew, "id", 10L);
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(10L), 10_000L, LocalDateTime.now());
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "member", buildMember(10L));
    PointHistory history =
        PointHistory.create(
            buildMember(20L),
            10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_RESERVE_RELEASE,
            PointReferenceType.CREW_PARTICIPANT,
            1L,
            "crew:10:participant:1:reserve-release:1");

    assertThatThrownBy(() -> participant.linkReleasedPointHistory(history))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("사용자가 일치");
  }

  @Test
  void expireSetsExpiredStatusAndExpiredAt() {
    LocalDateTime now = LocalDateTime.now();
    CrewParticipant participant =
        CrewParticipant.createPending(buildCrew(), buildMember(), 10_000L, now);

    participant.expire(now);

    assertThat(participant.getStatus()).isEqualTo(CrewParticipantStatus.EXPIRED);
    assertThat(participant.getExpiredAt()).isEqualTo(now);
  }

  @Test
  void expireThrowsWhenStatusIsNotPending() {
    LocalDateTime now = LocalDateTime.now();
    CrewParticipant participant =
        CrewParticipant.createPending(buildCrew(), buildMember(), 10_000L, now);
    participant.cancel(now);

    assertThatThrownBy(() -> participant.expire(now))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("expire는 PENDING 상태에서만 가능합니다.");
  }

  @Test
  void cancelOnCrewCancelledTransitionsLockedParticipantToCancelled() {
    Crew crew = buildCrew();
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(), 10_000L, LocalDateTime.now());
    LocalDateTime now = LocalDateTime.now();

    participant.cancelOnCrewCancelled(now);

    assertThat(participant.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    assertThat(participant.getCancelledAt()).isEqualTo(now);
  }

  @Test
  void cancelOnCrewCancelledTransitionsPendingParticipantToCancelled() {
    Crew crew = buildCrew();
    CrewParticipant participant =
        CrewParticipant.createPending(crew, buildMember(), 10_000L, LocalDateTime.now());
    LocalDateTime now = LocalDateTime.now();

    participant.cancelOnCrewCancelled(now);

    assertThat(participant.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    assertThat(participant.getCancelledAt()).isEqualTo(now);
  }

  @Test
  void cancelOnCrewCancelledThrowsForRejectedParticipant() {
    Crew crew = buildCrew();
    CrewParticipant participant =
        CrewParticipant.createPending(crew, buildMember(), 10_000L, LocalDateTime.now());
    participant.reject(LocalDateTime.now());

    assertThatThrownBy(() -> participant.cancelOnCrewCancelled(LocalDateTime.now()))
        .isInstanceOf(CustomException.class);
  }

  private Crew buildCrew() {
    return Crew.create(
        buildMember(10L),
        "제목",
        "설명",
        null,
        "운동",
        "{\"host\":true}",
        HostPolicyVersion.HOST_POLICY_V1,
        LocalDateTime.now(),
        10_000L,
        2,
        3,
        LocalDateTime.now().plusDays(1),
        LocalDateTime.now().plusDays(2),
        LocalDateTime.now().plusDays(30));
  }

  private Member buildMember() {
    return buildMember(10L);
  }

  private Member buildMember(Long id) {
    Member member = Member.create("member" + id + "@example.com", "pw", "닉네임" + id);
    ReflectionTestUtils.setField(member, "id", id);
    return member;
  }
}
