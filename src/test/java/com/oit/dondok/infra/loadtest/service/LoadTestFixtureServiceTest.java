package com.oit.dondok.infra.loadtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.point.repository.PointAccountRepository;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.domain.point.service.PointChargeRecoveryService;
import com.oit.dondok.domain.point.service.PointChargeService;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.DailySettlementParticipantSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.domain.settlement.service.SettlementBatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class LoadTestFixtureServiceTest {
  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final PointAccountRepository pointAccountRepository = mock(PointAccountRepository.class);
  private final CrewRepository crewRepository = mock(CrewRepository.class);
  private final CrewParticipantRepository crewParticipantRepository =
      mock(CrewParticipantRepository.class);
  private final MissionRuleRepository missionRuleRepository = mock(MissionRuleRepository.class);
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository =
      mock(DailySettlementSnapshotRepository.class);
  private final DailySettlementParticipantSnapshotRepository
      dailySettlementParticipantSnapshotRepository =
          mock(DailySettlementParticipantSnapshotRepository.class);
  private final SettlementRepository settlementRepository = mock(SettlementRepository.class);
  private final SettlementItemRepository settlementItemRepository =
      mock(
          SettlementItemRepository.class,
          invocation -> {
            if (invocation.getMethod().getName().equals("countBySettlementIdIn")) {
              return 4L;
            }
            if (invocation
                .getMethod()
                .getName()
                .equals("countBySettlementIdInAndPointHistoryIsNotNull")) {
              return 4L;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
          });
  private final TokenProvider tokenProvider = mock(TokenProvider.class);
  private final SettlementBatchService settlementBatchService = mock(SettlementBatchService.class);
  private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private final LoadTestFixtureService service =
      new LoadTestFixtureService(
          memberRepository,
          pointAccountRepository,
          crewRepository,
          crewParticipantRepository,
          missionRuleRepository,
          dailySettlementSnapshotRepository,
          dailySettlementParticipantSnapshotRepository,
          settlementRepository,
          settlementItemRepository,
          mock(PointChargeRepository.class),
          mock(PointChargeService.class),
          mock(PointChargeRecoveryService.class),
          settlementBatchService,
          tokenProvider,
          mock(LoadTestFixtureResetService.class),
          transactionTemplate);

  @Test
  void pointBaselineAndLimitRunsReuseTheSameAccountPool() {
    Map<String, com.oit.dondok.domain.member.entity.Member> membersByEmail = new HashMap<>();
    Map<Long, com.oit.dondok.domain.member.entity.Member> membersById = new HashMap<>();
    Set<Long> accountMemberIds = new HashSet<>();
    AtomicLong memberSequence = new AtomicLong();

    when(memberRepository.findByEmail(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> Optional.ofNullable(membersByEmail.get(invocation.getArgument(0))));
    when(memberRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              com.oit.dondok.domain.member.entity.Member member = invocation.getArgument(0);
              long id = memberSequence.incrementAndGet();
              ReflectionTestUtils.setField(member, "id", id);
              membersByEmail.put(member.getEmail(), member);
              membersById.put(id, member);
              return member;
            });
    when(memberRepository.findById(org.mockito.ArgumentMatchers.anyLong()))
        .thenAnswer(invocation -> Optional.ofNullable(membersById.get(invocation.getArgument(0))));
    when(pointAccountRepository.findByMemberId(org.mockito.ArgumentMatchers.anyLong()))
        .thenAnswer(
            invocation ->
                accountMemberIds.contains(invocation.<Long>getArgument(0))
                    ? Optional.of(mock(com.oit.dondok.domain.point.entity.PointAccount.class))
                    : Optional.empty());
    when(pointAccountRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              com.oit.dondok.domain.point.entity.PointAccount account = invocation.getArgument(0);
              accountMemberIds.add(account.getMember().getId());
              return account;
            });
    when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation ->
                invocation
                    .<TransactionCallback<?>>getArgument(0)
                    .doInTransaction(
                        mock(org.springframework.transaction.TransactionStatus.class)));
    when(tokenProvider.createAccessToken(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> "access-" + invocation.getArgument(0));

    service.preparePointRun("baseline", 20);
    List<java.util.UUID> baselineAccounts =
        service.pointTokens("baseline").accounts().stream()
            .map(LoadTestFixtureService.PointToken::memberUuid)
            .toList();
    service.preparePointRun("limit-40", 20);
    List<java.util.UUID> limitAccounts =
        service.pointTokens("limit-40").accounts().stream()
            .map(LoadTestFixtureService.PointToken::memberUuid)
            .toList();

    assertThat(limitAccounts).containsExactlyElementsOf(baselineAccounts);
    assertThat(membersByEmail.keySet())
        .allMatch(email -> email.startsWith("load-test+point-pool-"));
  }

  @Test
  void settlementPreflightRejectsOrdinaryPendingSettlementWithoutMutatingIt() {
    Crew ordinaryCrew = mock(Crew.class);
    when(ordinaryCrew.getTitle()).thenReturn("ordinary-crew");
    Settlement pending = mock(Settlement.class);
    when(pending.getCrew()).thenReturn(ordinaryCrew);
    when(pending.getStatus()).thenReturn(SettlementStatus.PENDING);
    when(crewRepository.findByStatusAndEndAtLessThanEqual(
            org.mockito.ArgumentMatchers.eq(CrewStatus.ACTIVE), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
    when(crewRepository.findClosedWithoutSettlement()).thenReturn(List.of());
    when(settlementRepository.findAllBy()).thenReturn(List.of(pending));

    LoadTestFixtureService.SettlementPreflight result = service.preflightFinalSettlements();

    assertThat(result.safe()).isFalse();
    assertThat(result.nonRunPending()).isEqualTo(1);
    verify(settlementRepository).findAllBy();
    verifyNoMoreInteractions(settlementRepository);
  }

  @Test
  void settlementTriggerRechecksPreflightBeforeRunningBatch() {
    @SuppressWarnings("unchecked")
    Map<String, List<Long>> settlementRunIds =
        (Map<String, List<Long>>) ReflectionTestUtils.getField(service, "settlementRunIds");
    settlementRunIds.put("smoke", List.of(1L));
    Crew ordinaryCrew = mock(Crew.class);
    when(ordinaryCrew.getTitle()).thenReturn("ordinary-crew");
    Settlement pending = mock(Settlement.class);
    when(pending.getCrew()).thenReturn(ordinaryCrew);
    when(pending.getStatus()).thenReturn(SettlementStatus.PENDING);
    when(crewRepository.findByStatusAndEndAtLessThanEqual(
            org.mockito.ArgumentMatchers.eq(CrewStatus.ACTIVE), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
    when(crewRepository.findClosedWithoutSettlement()).thenReturn(List.of());
    when(settlementRepository.findAllBy()).thenReturn(List.of(pending));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.triggerFinalSettlementRun("smoke"))
        .isInstanceOf(LoadTestFixtureService.UnsafeSettlementPreflightException.class);

    verifyNoInteractions(settlementBatchService);
  }

  @Test
  void finalSettlementPreparationUsesOneTransactionPerFixture() {
    stubSafeSettlementPreflight();
    when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation ->
                invocation
                    .<TransactionCallback<?>>getArgument(0)
                    .doInTransaction(
                        mock(org.springframework.transaction.TransactionStatus.class)));
    when(memberRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(crewRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(crewParticipantRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(missionRuleRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(dailySettlementSnapshotRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(dailySettlementParticipantSnapshotRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(settlementRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.prepareFinalSettlementRun("settlement-run", 2);

    verify(transactionTemplate, org.mockito.Mockito.times(2))
        .execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void settlementResultCountsItemsWithTwoBatchQueries() {
    @SuppressWarnings("unchecked")
    Map<String, List<Long>> settlementRunIds =
        (Map<String, List<Long>>) ReflectionTestUtils.getField(service, "settlementRunIds");
    settlementRunIds.put("batch-count", List.of(1L, 2L));
    stubSafeSettlementPreflight();
    Settlement first = mock(Settlement.class);
    Settlement second = mock(Settlement.class);
    when(first.getStatus()).thenReturn(SettlementStatus.SUCCEEDED);
    when(second.getStatus()).thenReturn(SettlementStatus.SUCCEEDED);
    when(settlementRepository.findById(1L)).thenReturn(Optional.of(first));
    when(settlementRepository.findById(2L)).thenReturn(Optional.of(second));

    LoadTestFixtureService.SettlementRunResult result =
        service.triggerFinalSettlementRun("batch-count");

    assertThat(result.settlementItems()).isEqualTo(4);
    assertThat(result.refundedItems()).isEqualTo(4);
    assertThat(org.mockito.Mockito.mockingDetails(settlementItemRepository).getInvocations())
        .extracting(invocation -> invocation.getMethod().getName())
        .containsExactly("countBySettlementIdIn", "countBySettlementIdInAndPointHistoryIsNotNull");
  }

  private void stubSafeSettlementPreflight() {
    when(crewRepository.findByStatusAndEndAtLessThanEqual(
            org.mockito.ArgumentMatchers.eq(CrewStatus.ACTIVE), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
    when(crewRepository.findClosedWithoutSettlement()).thenReturn(List.of());
    when(settlementRepository.findAllBy()).thenReturn(List.of());
  }
}
