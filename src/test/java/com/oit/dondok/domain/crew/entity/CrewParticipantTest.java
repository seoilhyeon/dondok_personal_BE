package com.oit.dondok.domain.crew.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CrewParticipantTest {

  @Test
  void linkReleasedPointHistoryStoresHistoryReference() {
    Crew crew =
        Crew.create(
            buildMember(),
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
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(), 10_000L, LocalDateTime.now());
    PointHistory history =
        PointHistory.create(
            buildMember(),
            -10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_DEPOSIT_RESERVE,
            PointReferenceType.CREW_PARTICIPANT,
            1L,
            "crew:10:participant:1:reserve:1");

    participant.linkReleasedPointHistory(history);

    assertThat(participant).extracting("releasedPointHistory").isEqualTo(history);
  }

  @Test
  void linkReleasedPointHistoryCannotOverrideAlreadyLinkedHistory() {
    Crew crew =
        Crew.create(
            buildMember(),
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
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(), 10_000L, LocalDateTime.now());
    PointHistory history =
        PointHistory.create(
            buildMember(),
            -10_000L,
            0L,
            10_000L,
            0L,
            PointTransactionType.CREW_DEPOSIT_RESERVE,
            PointReferenceType.CREW_PARTICIPANT,
            1L,
            "crew:10:participant:1:reserve:1");
    PointHistory anotherHistory =
        PointHistory.create(
            buildMember(),
            -5_000L,
            0L,
            10_000L,
            5_000L,
            PointTransactionType.CREW_DEPOSIT_RESERVE,
            PointReferenceType.CREW_PARTICIPANT,
            2L,
            "crew:10:participant:2:reserve:1");

    participant.linkReleasedPointHistory(history);

    assertThatThrownBy(() -> participant.linkReleasedPointHistory(anotherHistory))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void linkReleasedPointHistoryRejectsNull() {
    Crew crew =
        Crew.create(
            buildMember(),
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
    CrewParticipant participant =
        CrewParticipant.create(crew, buildMember(), 10_000L, LocalDateTime.now());

    assertThatThrownBy(() -> participant.linkReleasedPointHistory(null))
        .isInstanceOf(NullPointerException.class);
  }

  private Member buildMember() {
    return Member.create("member@example.com", "pw", "닉네임");
  }
}
