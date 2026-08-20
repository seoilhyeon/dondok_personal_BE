package com.oit.dondok.infra.loadtest.service;

import com.oit.dondok.domain.auth.service.TokenProvider;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.point.dto.request.PointChargeRequest;
import com.oit.dondok.domain.point.entity.PointAccount;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.repository.PointAccountRepository;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.domain.point.service.PointChargeRecoveryService;
import com.oit.dondok.domain.point.service.PointChargeService;
import com.oit.dondok.domain.settlement.entity.DailySettlementParticipantSnapshot;
import com.oit.dondok.domain.settlement.entity.DailySettlementSnapshot;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.DailySettlementParticipantSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.DailySettlementSnapshotRepository;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.domain.settlement.service.SettlementBatchService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Local smoke ingress. It is absent unless the explicit non-production profile is active. */
@Service
@Profile("load-test & !prod")
@RequiredArgsConstructor
public class LoadTestFixtureService {
  private static final String EMAIL = "load-test@local.invalid";
  private static final String NICKNAME = "load-test";
  private static final long AMOUNT = 10_000L;
  private static final int MAX_POINT_ACCOUNTS = 200;
  private static final int MAX_SETTLEMENTS = 1_000;
  private static final Pattern RUN_ID_PATTERN = Pattern.compile("[a-z0-9-]{1,32}");

  private final MemberRepository memberRepository;
  private final PointAccountRepository pointAccountRepository;
  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  private final DailySettlementParticipantSnapshotRepository
      dailySettlementParticipantSnapshotRepository;
  private final SettlementRepository settlementRepository;
  private final SettlementItemRepository settlementItemRepository;
  private final PointChargeRepository pointChargeRepository;
  private final PointChargeService pointChargeService;
  private final PointChargeRecoveryService pointChargeRecoveryService;
  private final SettlementBatchService settlementBatchService;
  private final TokenProvider tokenProvider;
  private final LoadTestFixtureResetService fixtureResetService;
  private final TransactionTemplate transactionTemplate;
  private final Map<Long, Long> retryFixtureMemberIds = new ConcurrentHashMap<>();
  private final Map<String, List<Long>> pointRunMemberIds = new ConcurrentHashMap<>();
  private final Map<String, List<Long>> settlementRunIds = new ConcurrentHashMap<>();

  public Map<String, String> seed() {
    return Objects.requireNonNull(transactionTemplate.execute(status -> seedFixture()));
  }

  private Map<String, String> seedFixture() {
    Member member =
        memberRepository
            .findByEmail(EMAIL)
            .orElseGet(() -> memberRepository.save(Member.create(EMAIL, null, NICKNAME)));
    if (pointAccountRepository.findByMemberId(member.getId()).isEmpty()) {
      pointAccountRepository.save(PointAccount.create(member));
    }
    return Map.of("memberUuid", member.getUuid().toString());
  }

  public Map<String, String> reset() {
    fixtureResetService.reset();
    retryFixtureMemberIds.clear();
    pointRunMemberIds.clear();
    settlementRunIds.clear();
    return seed();
  }

  public PreparedRun preparePointRun(String runId, int accounts) {
    validateRunId(runId);
    validateCount(accounts, MAX_POINT_ACCOUNTS, "accounts");

    List<Long> existingMemberIds = pointRunMemberIds.get(runId);
    if (existingMemberIds != null) {
      if (existingMemberIds.size() != accounts) {
        throw new IllegalArgumentException("동일 runId에는 동일 accounts 값만 사용할 수 있습니다.");
      }
      return new PreparedRun(runId, accounts, existingMemberIds.size());
    }

    List<Long> memberIds =
        Objects.requireNonNull(
            transactionTemplate.execute(status -> createPointRunMembers(accounts)));
    List<Long> previous = pointRunMemberIds.putIfAbsent(runId, memberIds);
    if (previous != null) {
      if (previous.size() != accounts) {
        throw new IllegalArgumentException("동일 runId에는 동일 accounts 값만 사용할 수 있습니다.");
      }
      return new PreparedRun(runId, accounts, previous.size());
    }
    return new PreparedRun(runId, accounts, memberIds.size());
  }

  public PointTokens pointTokens(String runId) {
    validateRunId(runId);
    List<Long> memberIds = requiredRun(pointRunMemberIds, runId, "point");
    List<PointToken> accounts =
        memberIds.stream()
            .map(memberRepository::findById)
            .flatMap(java.util.Optional::stream)
            .map(
                member ->
                    new PointToken(
                        member.getUuid(), tokenProvider.createAccessToken(member.getUuid())))
            .toList();
    if (accounts.size() != memberIds.size()) {
      throw new IllegalStateException("point fixture member를 찾을 수 없습니다.");
    }
    return new PointTokens(runId, accounts);
  }

  public SettlementPreflight preflightFinalSettlements() {
    LocalDateTime now = LocalDateTime.now();
    long nonRunEligibleCrews =
        crewRepository.findByStatusAndEndAtLessThanEqual(CrewStatus.ACTIVE, now).stream()
                .filter(crew -> !isSettlementFixture(crew))
                .count()
            + crewRepository.findClosedWithoutSettlement().stream()
                .filter(crew -> !isSettlementFixture(crew))
                .count();
    List<Settlement> nonFixtureSettlements =
        settlementRepository.findAllBy().stream()
            .filter(settlement -> !isSettlementFixture(settlement.getCrew()))
            .toList();
    long nonRunPending = countByStatus(nonFixtureSettlements, SettlementStatus.PENDING);
    long nonRunRetryWait = countByStatus(nonFixtureSettlements, SettlementStatus.RETRY_WAIT);
    long nonRunRunning = countByStatus(nonFixtureSettlements, SettlementStatus.RUNNING);
    return new SettlementPreflight(
        nonRunEligibleCrews,
        nonRunPending,
        nonRunRetryWait,
        nonRunRunning,
        nonRunEligibleCrews == 0
            && nonRunPending == 0
            && nonRunRetryWait == 0
            && nonRunRunning == 0);
  }

  public PreparedRun prepareFinalSettlementRun(String runId, int settlements) {
    validateRunId(runId);
    validateCount(settlements, MAX_SETTLEMENTS, "settlements");
    requireSafeSettlementPreflight();

    List<Long> existingSettlementIds = settlementRunIds.get(runId);
    if (existingSettlementIds != null) {
      if (existingSettlementIds.size() != settlements) {
        throw new IllegalArgumentException("동일 runId에는 동일 settlements 값만 사용할 수 있습니다.");
      }
      return new PreparedRun(runId, settlements, existingSettlementIds.size());
    }

    List<Long> settlementIds = createFinalSettlementRun(runId, settlements);
    List<Long> previous = settlementRunIds.putIfAbsent(runId, settlementIds);
    if (previous != null) {
      if (previous.size() != settlements) {
        throw new IllegalArgumentException("동일 runId에는 동일 settlements 값만 사용할 수 있습니다.");
      }
      return new PreparedRun(runId, settlements, previous.size());
    }
    return new PreparedRun(runId, settlements, settlementIds.size());
  }

  public SettlementRunResult triggerFinalSettlementRun(String runId) {
    validateRunId(runId);
    List<Long> settlementIds = requiredRun(settlementRunIds, runId, "settlement");
    requireSafeSettlementPreflight();

    LocalDateTime startedAt = LocalDateTime.now();
    settlementBatchService.runFinalSettlementBatch(DailySettlementType.A);
    LocalDateTime finishedAt = LocalDateTime.now();

    List<Settlement> settlements =
        settlementIds.stream()
            .map(settlementRepository::findById)
            .flatMap(java.util.Optional::stream)
            .toList();
    long claimed =
        settlements.stream()
            .filter(settlement -> settlement.getStatus() != SettlementStatus.PENDING)
            .count();
    long succeeded = countByStatus(settlements, SettlementStatus.SUCCEEDED);
    long retryWait = countByStatus(settlements, SettlementStatus.RETRY_WAIT);
    long failed = countByStatus(settlements, SettlementStatus.FAILED) + retryWait;
    long nonTerminal =
        countByStatus(settlements, SettlementStatus.PENDING)
            + countByStatus(settlements, SettlementStatus.RUNNING);
    long settlementItems = settlementItemRepository.countBySettlementIdIn(settlementIds);
    long refundedItems =
        settlementItemRepository.countBySettlementIdInAndPointHistoryIsNotNull(settlementIds);
    return new SettlementRunResult(
        runId,
        settlementIds.size(),
        claimed,
        succeeded + failed,
        succeeded,
        failed,
        nonTerminal,
        settlementItems,
        refundedItems,
        startedAt,
        finishedAt);
  }

  public void pointCharge(String paymentId, String orderId) {
    Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
    pointChargeService.charge(member.getUuid(), new PointChargeRequest(paymentId, orderId, AMOUNT));
  }

  public void recovery() {
    transactionTemplate.executeWithoutResult(status -> createRecoveryFixtures());
    pointChargeRecoveryService.runRecoveryBatch(LocalDateTime.now().plusMinutes(6));
  }

  private void createRecoveryFixtures() {
    Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
    for (String suffix :
        new String[] {
          "recover-lookup-exception",
          "recover-not-done",
          "recover-mismatch",
          "recover-completion-mismatch",
          "recover-recovered"
        }) {
      createPendingRecoveryCharge(member, suffix);
    }
    // These charges deliberately have no PointAccount, so ledger completion deterministically
    // fails.
    Member unfundedMember = recoveryFailureMember();
    createPendingRecoveryCharge(unfundedMember, "recover-ledger-error");
    createPendingRecoveryCharge(unfundedMember, "recover-cancel-error");
    pointChargeRepository.flush();
  }

  public void finalBatch() {
    createSettlementFixture("success", true);
    Settlement retryFixture = createSettlementFixture("retry", false);
    retryFixtureMemberIds.put(retryFixture.getId(), retryFixture.getCrew().getHostMember().getId());
    settlementBatchService.runFinalSettlementBatch(DailySettlementType.A);
  }

  public void retryBatch() {
    // First preserve the deterministic insufficient-credit fixture for a retry failure,
    // then repair it and run once more to produce the retry success tuple.
    settlementBatchService.runRetrySettlementBatch();
    retryFixtureMemberIds.forEach(this::repairLockedBalance);
    settlementBatchService.runRetrySettlementBatch();
    retryFixtureMemberIds.keySet().removeIf(this::isNoLongerRetryable);
  }

  private Member recoveryFailureMember() {
    return memberRepository
        .findByEmail("load-test-recovery-failure@local.invalid")
        .orElseGet(
            () ->
                memberRepository.save(
                    Member.create(
                        "load-test-recovery-failure@local.invalid",
                        null,
                        "load-recovery-failure")));
  }

  private void createPendingRecoveryCharge(Member member, String suffix) {
    String paymentId = "load-test-" + suffix;
    if (pointChargeRepository.findByPaymentId(paymentId).isEmpty()) {
      pointChargeRepository.save(
          PointCharge.createPending(member, paymentId, "load-test-order-" + suffix, AMOUNT));
    }
  }

  private List<Long> createPointRunMembers(int accounts) {
    return java.util.stream.IntStream.range(0, accounts)
        .mapToObj(index -> createPointRunMember(index).getId())
        .toList();
  }

  private Member createPointRunMember(int index) {
    String email = "load-test+point-pool-" + index + "@local.invalid";
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseGet(
                () -> memberRepository.save(Member.create(email, null, "load-point-" + index)));
    if (pointAccountRepository.findByMemberId(member.getId()).isEmpty()) {
      pointAccountRepository.save(PointAccount.create(member));
    }
    return member;
  }

  private List<Long> createFinalSettlementRun(String runId, int settlements) {
    return java.util.stream.IntStream.range(0, settlements)
        .mapToObj(index -> createNamedSettlementFixture(runId + "-" + index, true).getId())
        .toList();
  }

  private void requireSafeSettlementPreflight() {
    SettlementPreflight preflight = preflightFinalSettlements();
    if (!preflight.safe()) {
      throw new UnsafeSettlementPreflightException(preflight);
    }
  }

  private boolean isSettlementFixture(Crew crew) {
    return crew.getTitle().startsWith("load-test-settlement-");
  }

  private long countByStatus(List<Settlement> settlements, SettlementStatus status) {
    return settlements.stream().filter(settlement -> settlement.getStatus() == status).count();
  }

  private void validateRunId(String runId) {
    if (runId == null || !RUN_ID_PATTERN.matcher(runId).matches()) {
      throw new IllegalArgumentException("runId 형식이 올바르지 않습니다.");
    }
  }

  private void validateCount(int count, int maximum, String name) {
    if (count < 1 || count > maximum) {
      throw new IllegalArgumentException(name + "는 1부터 " + maximum + " 사이여야 합니다.");
    }
  }

  private List<Long> requiredRun(Map<String, List<Long>> runs, String runId, String type) {
    List<Long> ids = runs.get(runId);
    if (ids == null) {
      throw new IllegalArgumentException(type + " runId를 먼저 prepare해야 합니다.");
    }
    return ids;
  }

  public record PreparedRun(String runId, int requested, int prepared) {}

  public record PointToken(UUID memberUuid, String accessToken) {}

  public record PointTokens(String runId, List<PointToken> accounts) {}

  public record SettlementPreflight(
      long nonRunEligibleCrews,
      long nonRunPending,
      long nonRunRetryWait,
      long nonRunRunning,
      boolean safe) {}

  public record SettlementRunResult(
      String runId,
      long requested,
      long claimed,
      long processed,
      long succeeded,
      long failed,
      long nonTerminal,
      long settlementItems,
      long refundedItems,
      LocalDateTime startedAt,
      LocalDateTime finishedAt) {}

  public static class UnsafeSettlementPreflightException extends RuntimeException {
    private final SettlementPreflight preflight;

    public UnsafeSettlementPreflightException(SettlementPreflight preflight) {
      this.preflight = preflight;
    }

    public SettlementPreflight preflight() {
      return preflight;
    }
  }

  private Settlement createSettlementFixture(String outcome, boolean fundHost) {
    String fixtureName = outcome + "-" + UUID.randomUUID();
    return createNamedSettlementFixture(fixtureName, fundHost);
  }

  private Settlement createNamedSettlementFixture(String fixtureName, boolean fundHost) {
    return Objects.requireNonNull(
        transactionTemplate.execute(
            status -> createSettlementFixtureInTransaction(fixtureName, fundHost)));
  }

  private Settlement createSettlementFixtureInTransaction(String fixtureName, boolean fundHost) {
    LocalDateTime endAt =
        LocalDateTime.now().minusDays(2).withHour(0).withMinute(0).withSecond(0).withNano(0);
    Member host =
        memberRepository.save(
            Member.create(
                "load-test+" + fixtureName + "-settlement-host@local.invalid",
                null,
                "load-host-" + fixtureName));
    Member member =
        memberRepository.save(
            Member.create(
                "load-test+" + fixtureName + "-settlement-member@local.invalid",
                null,
                "load-member-" + fixtureName));
    Crew crew =
        crewRepository.save(
            Crew.create(
                host,
                "load-test-settlement-" + fixtureName,
                "local fixture",
                null,
                "OTHER",
                "{}",
                HostPolicyVersion.HOST_POLICY_V1,
                endAt.minusDays(2),
                AMOUNT,
                2,
                2,
                endAt.minusDays(2),
                endAt,
                endAt));
    missionRuleRepository.save(
        MissionRule.create(crew, MissionFrequencyType.DAILY, DailySettlementType.A));
    CrewParticipant hostParticipant =
        crewParticipantRepository.save(CrewParticipant.create(crew, host, AMOUNT, endAt));
    CrewParticipant memberParticipant =
        crewParticipantRepository.save(CrewParticipant.create(crew, member, AMOUNT, endAt));
    saveLockedAccount(host, fundHost ? AMOUNT : 0L);
    saveLockedAccount(member, AMOUNT);
    DailySettlementSnapshot snapshot =
        dailySettlementSnapshotRepository.save(
            DailySettlementSnapshot.finalized(
                crew,
                endAt.toLocalDate(),
                DailySettlementType.A,
                MissionFrequencyType.DAILY,
                "load-test-fixture",
                endAt,
                2,
                0,
                AMOUNT * 2));
    dailySettlementParticipantSnapshotRepository.save(
        DailySettlementParticipantSnapshot.create(
            snapshot, hostParticipant, 0, BigDecimal.ZERO, 0L));
    dailySettlementParticipantSnapshotRepository.save(
        DailySettlementParticipantSnapshot.create(
            snapshot, memberParticipant, 0, BigDecimal.ZERO, 0L));
    return settlementRepository.save(
        Settlement.createPending(
            crew,
            "load-test-fixture",
            endAt,
            new SettlementRuleContextSnapshot(DailySettlementType.A, MissionFrequencyType.DAILY)));
  }

  private void saveLockedAccount(Member member, long lockedBalance) {
    PointAccount account = PointAccount.create(member);
    if (lockedBalance > 0) {
      account.increaseAvailable(lockedBalance);
      account.lockFromAvailable(lockedBalance);
    }
    pointAccountRepository.save(account);
  }

  private void repairLockedBalance(Long settlementId, Long memberId) {
    settlementRepository
        .findById(settlementId)
        .filter(settlement -> settlement.getStatus() == SettlementStatus.RETRY_WAIT)
        .ifPresent(
            settlement ->
                pointAccountRepository
                    .findByMemberId(memberId)
                    .ifPresent(
                        account -> {
                          long missingBalance = AMOUNT - account.getLockedBalance();
                          if (missingBalance > 0) {
                            account.increaseAvailable(missingBalance);
                            account.lockFromAvailable(missingBalance);
                            pointAccountRepository.save(account);
                          }
                        }));
  }

  private boolean isNoLongerRetryable(Long settlementId) {
    return settlementRepository
        .findById(settlementId)
        .map(settlement -> settlement.getStatus() != SettlementStatus.RETRY_WAIT)
        .orElse(true);
  }
}
