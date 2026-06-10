package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.member.entity.Member;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PendingApplicationAutoRejectServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final Long DEPOSIT = 10_000L;

  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private CrewPointPort crewPointPort;

  @InjectMocks private PendingApplicationAutoRejectService service;

  // ======================== rejectExpiredApplications ========================

  @Test
  void rejectExpiredApplicationsDoesNothingWhenNoTargetsFound() {
    given(
            crewParticipantRepository
                .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                    eq(CrewParticipantStatus.PENDING), eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of());

    service.rejectExpiredApplications();

    then(crewParticipantRepository).should(times(0)).saveAndFlush(any());
    then(crewPointPort).shouldHaveNoInteractions();
  }

  @Test
  void rejectExpiredApplicationsSetsExpiredStatusAndExpiredAt() {
    Member member = buildMember();
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildPendingParticipant(crew, member, 1L);

    given(
            crewParticipantRepository
                .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                    eq(CrewParticipantStatus.PENDING), eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of(participant));
    given(crewParticipantRepository.saveAndFlush(participant)).willReturn(participant);

    service.rejectExpiredApplications();

    assertThat(participant.getStatus()).isEqualTo(CrewParticipantStatus.EXPIRED);
    assertThat(participant.getExpiredAt()).isNotNull();
  }

  @Test
  void rejectExpiredApplicationsCallsReleaseExpiredReserveAfterSave() {
    Member member = buildMember();
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildPendingParticipant(crew, member, 1L);

    given(
            crewParticipantRepository
                .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                    eq(CrewParticipantStatus.PENDING), eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of(participant));
    given(crewParticipantRepository.saveAndFlush(participant)).willReturn(participant);

    service.rejectExpiredApplications();

    then(crewParticipantRepository).should().saveAndFlush(participant);
    then(crewPointPort).should().releaseExpiredReserve(participant);
  }

  @Test
  void rejectExpiredApplicationsProcessesAllTargets() {
    Member member = buildMember();
    Crew crew = buildCrew(member);
    CrewParticipant p1 = buildPendingParticipant(crew, member, 1L);
    CrewParticipant p2 = buildPendingParticipant(crew, member, 2L);
    CrewParticipant p3 = buildPendingParticipant(crew, member, 3L);

    given(
            crewParticipantRepository
                .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                    eq(CrewParticipantStatus.PENDING), eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of(p1, p2, p3));
    given(crewParticipantRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

    service.rejectExpiredApplications();

    assertThat(p1.getStatus()).isEqualTo(CrewParticipantStatus.EXPIRED);
    assertThat(p2.getStatus()).isEqualTo(CrewParticipantStatus.EXPIRED);
    assertThat(p3.getStatus()).isEqualTo(CrewParticipantStatus.EXPIRED);
    then(crewParticipantRepository).should(times(3)).saveAndFlush(any());
    then(crewPointPort).should().releaseExpiredReserve(p1);
    then(crewPointPort).should().releaseExpiredReserve(p2);
    then(crewPointPort).should().releaseExpiredReserve(p3);
  }

  @Test
  void rejectExpiredApplicationsPropagatesExceptionWhenSaveAndFlushFails() {
    Member member = buildMember();
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildPendingParticipant(crew, member, 1L);
    RuntimeException cause = new RuntimeException("DB error");

    given(
            crewParticipantRepository
                .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                    eq(CrewParticipantStatus.PENDING), eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of(participant));
    given(crewParticipantRepository.saveAndFlush(participant)).willThrow(cause);

    assertThatThrownBy(() -> service.rejectExpiredApplications()).isSameAs(cause);
    then(crewPointPort).shouldHaveNoInteractions();
  }

  @Test
  void rejectExpiredApplicationsPropagatesExceptionWhenReleaseExpiredReserveFails() {
    Member member = buildMember();
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildPendingParticipant(crew, member, 1L);
    RuntimeException cause = new RuntimeException("point release error");

    given(
            crewParticipantRepository
                .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                    eq(CrewParticipantStatus.PENDING), eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of(participant));
    given(crewParticipantRepository.saveAndFlush(participant)).willReturn(participant);
    doThrow(cause).when(crewPointPort).releaseExpiredReserve(participant);

    assertThatThrownBy(() -> service.rejectExpiredApplications()).isSameAs(cause);
  }

  // ======================== helpers ========================

  private Member buildMember() {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", 1L);
    return member;
  }

  private Crew buildCrew(Member hostMember) {
    Crew crew =
        Crew.create(
            hostMember,
            "테스트 크루",
            "크루 설명",
            null,
            "EXERCISE",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(SEOUL_ZONE),
            DEPOSIT,
            2,
            5,
            LocalDateTime.now(SEOUL_ZONE).minusDays(1),
            LocalDateTime.now(SEOUL_ZONE).plusDays(5),
            LocalDateTime.now(SEOUL_ZONE).plusDays(35));
    ReflectionTestUtils.setField(crew, "id", 1L);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private CrewParticipant buildPendingParticipant(Crew crew, Member member, Long id) {
    CrewParticipant participant =
        CrewParticipant.createPending(crew, member, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));
    ReflectionTestUtils.setField(participant, "id", id);
    ReflectionTestUtils.setField(participant, "version", 0L);
    return participant;
  }
}
