package com.oit.dondok.domain.crew.service;

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
  @Mock private PendingParticipantExpireProcessor expireProcessor;

  @InjectMocks private PendingApplicationAutoRejectService service;

  // ======================== rejectExpiredApplications  만료 신청 자동 거절 ========================

  @Test
  void rejectExpiredApplicationsDoesNothingWhenNoTargetsFound() {
    given(
            crewParticipantRepository
                .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                    eq(CrewParticipantStatus.PENDING), eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of());

    service.rejectExpiredApplications();

    then(expireProcessor).shouldHaveNoInteractions();
  }

  @Test
  void rejectExpiredApplicationsDelegatesProcessOneForEachTarget() {
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

    service.rejectExpiredApplications();

    then(expireProcessor).should(times(3)).processOne(any(), any());
  }

  @Test
  void rejectExpiredApplicationsContinuesWhenOneParticipantFails() {
    Member member = buildMember();
    Crew crew = buildCrew(member);
    CrewParticipant p1 = buildPendingParticipant(crew, member, 1L);
    CrewParticipant p2 = buildPendingParticipant(crew, member, 2L);

    given(
            crewParticipantRepository
                .findByStatusAndCrewStatusAndCrewRecruitmentDeadlineLessThanEqual(
                    eq(CrewParticipantStatus.PENDING), eq(CrewStatus.RECRUITING), any()))
        .willReturn(List.of(p1, p2));
    doThrow(new RuntimeException("point error")).when(expireProcessor).processOne(eq(p1), any());

    service.rejectExpiredApplications();

    then(expireProcessor).should().processOne(eq(p2), any());
  }

  // ======================== helpers 보조 메서드 ========================

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
