package com.oit.dondok.domain.crew.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.repository.CrewRepository;
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
class CrewCloseBatchServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  @Mock private CrewRepository crewRepository;
  @Mock private CrewCloseProcessor crewCloseProcessor;

  @InjectMocks private CrewCloseBatchService crewCloseBatchService;

  @Test
  void closeCrewsDelegatesEachExpiredCrewToProcessor() {
    Crew crew1 = buildActiveCrew(1L);
    Crew crew2 = buildActiveCrew(2L);
    given(crewRepository.findByStatusAndEndAtLessThanEqual(eq(CrewStatus.ACTIVE), any()))
        .willReturn(List.of(crew1, crew2));

    crewCloseBatchService.closeCrews();

    then(crewCloseProcessor).should().closeOne(eq(1L));
    then(crewCloseProcessor).should().closeOne(eq(2L));
  }

  @Test
  void closeCrewsDoesNothingWhenNoExpiredCrew() {
    given(crewRepository.findByStatusAndEndAtLessThanEqual(any(), any())).willReturn(List.of());

    crewCloseBatchService.closeCrews();

    then(crewCloseProcessor).shouldHaveNoInteractions();
  }

  @Test
  void continuesProcessingRemainingCrewsWhenOneProcessorFails() {
    Crew crew1 = buildActiveCrew(1L);
    Crew crew2 = buildActiveCrew(2L);
    given(crewRepository.findByStatusAndEndAtLessThanEqual(any(), any()))
        .willReturn(List.of(crew1, crew2));
    willThrow(new RuntimeException("처리 실패")).given(crewCloseProcessor).closeOne(eq(1L));

    crewCloseBatchService.closeCrews();

    then(crewCloseProcessor).should().closeOne(eq(1L));
    then(crewCloseProcessor).should().closeOne(eq(2L));
  }

  @Test
  void verifyActiveStatusFilterExcludesAlreadyClosedCrews() {
    given(crewRepository.findByStatusAndEndAtLessThanEqual(eq(CrewStatus.ACTIVE), any()))
        .willReturn(List.of());

    crewCloseBatchService.closeCrews();

    then(crewRepository)
        .should()
        .findByStatusAndEndAtLessThanEqual(eq(CrewStatus.ACTIVE), any(LocalDateTime.class));
    then(crewCloseProcessor).shouldHaveNoInteractions();
  }

  // ======================== helpers ========================

  private Crew buildActiveCrew(Long id) {
    LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
    Crew crew =
        Crew.create(
            buildMember(),
            "테스트 크루",
            "크루 설명",
            null,
            "EXERCISE",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            now.minusDays(10),
            10_000L,
            2,
            5,
            now.minusDays(3),
            now.minusDays(7),
            now.minusDays(1));
    ReflectionTestUtils.setField(crew, "id", id);
    ReflectionTestUtils.setField(crew, "version", 0L);
    ReflectionTestUtils.setField(crew, "status", CrewStatus.ACTIVE);
    return crew;
  }

  private Member buildMember() {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", 1L);
    return member;
  }
}
