package com.oit.dondok.infra.loadtest.service;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
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
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.domain.settlement.service.SettlementBatchService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Local smoke ingress. It is absent unless the explicit non-production profile is active. */
@Service
@Profile("load-test & !prod")
@RequiredArgsConstructor
public class LoadTestFixtureService {
  private static final String EMAIL = "load-test@local.invalid";
  private static final String NICKNAME = "load-test";
  private static final long AMOUNT = 10_000L;

  private final MemberRepository memberRepository;
  private final PointAccountRepository pointAccountRepository;
  private final CrewRepository crewRepository;
  private final CrewParticipantRepository crewParticipantRepository;
  private final MissionRuleRepository missionRuleRepository;
  private final DailySettlementSnapshotRepository dailySettlementSnapshotRepository;
  private final DailySettlementParticipantSnapshotRepository
      dailySettlementParticipantSnapshotRepository;
  private final SettlementRepository settlementRepository;
  private final PointChargeRepository pointChargeRepository;
  private final PointChargeService pointChargeService;
  private final PointChargeRecoveryService pointChargeRecoveryService;
  private final SettlementBatchService settlementBatchService;
  private final LoadTestFixtureResetService fixtureResetService;
  private final Map<Long, Long> retryFixtureMemberIds = new ConcurrentHashMap<>();

  public Map<String, String> seed() {
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
    return seed();
  }

  public HttpStatus pointCharge(String paymentId, String orderId) {
    Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
    pointChargeService.charge(member.getUuid(), new PointChargeRequest(paymentId, orderId, AMOUNT));
    return HttpStatus.NO_CONTENT;
  }

  public HttpStatus recovery() {
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
    pointChargeRecoveryService.runRecoveryBatch(LocalDateTime.now().plusMinutes(6));
    return HttpStatus.NO_CONTENT;
  }

  public HttpStatus finalBatch() {
    createSettlementFixture("success", true);
    Settlement retryFixture = createSettlementFixture("retry", false);
    retryFixtureMemberIds.put(retryFixture.getId(), retryFixture.getCrew().getHostMember().getId());
    settlementBatchService.runFinalSettlementBatch(DailySettlementType.A);
    return HttpStatus.NO_CONTENT;
  }

  public HttpStatus retryBatch() {
    // First preserve the deterministic insufficient-credit fixture for a retry failure,
    // then repair it and run once more to produce the retry success tuple.
    settlementBatchService.runRetrySettlementBatch();
    retryFixtureMemberIds.forEach(this::repairLockedBalance);
    settlementBatchService.runRetrySettlementBatch();
    retryFixtureMemberIds.keySet().removeIf(this::isNoLongerRetryable);
    return HttpStatus.NO_CONTENT;
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

  private Settlement createSettlementFixture(String outcome, boolean fundHost) {
    String suffix = UUID.randomUUID().toString();
    LocalDateTime endAt =
        LocalDateTime.now().minusDays(2).withHour(0).withMinute(0).withSecond(0).withNano(0);
    Member host =
        memberRepository.save(
            Member.create(
                "load-test-settlement-host-" + suffix + "@local.invalid",
                null,
                "load-host-" + suffix));
    Member member =
        memberRepository.save(
            Member.create(
                "load-test-settlement-member-" + suffix + "@local.invalid",
                null,
                "load-member-" + suffix));
    Crew crew =
        crewRepository.save(
            Crew.create(
                host,
                "load-test-settlement-" + outcome,
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
