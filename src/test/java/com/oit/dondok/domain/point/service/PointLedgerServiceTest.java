package com.oit.dondok.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.point.entity.PointAccount;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.repository.PointAccountRepository;
import com.oit.dondok.domain.point.repository.PointHistoryRepository;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.global.exception.CustomException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PointLedgerServiceTest {

  private static final Long CREW_ID = 10L;
  private static final Long PARTICIPANT_ID = 1L;
  private static final Long MEMBER_ID = 100L;
  private static final Long DEPOSIT = 10_000L;
  private static final Long SETTLEMENT_ITEM_ID = 200L;

  @Mock private PointAccountRepository pointAccountRepository;
  @Mock private PointHistoryRepository pointHistoryRepository;

  @InjectMocks private PointLedgerService pointLedgerService;

  @Test
  void chargeIncreasesAvailableBalanceAndAppendsChargeLedger() {
    Member member = member(MEMBER_ID);
    PointAccount account = account(member, 5_000L);
    given(pointHistoryRepository.findByIdempotencyKey("charge:payment-id"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    PointHistory history = pointLedgerService.charge(member, 10_000L, "payment-id");

    assertThat(account.getAvailableBalance()).isEqualTo(15_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();
    assertThat(history.getAmount()).isEqualTo(10_000L);
    assertThat(history.getAvailableAfter()).isEqualTo(15_000L);
    assertThat(history.getTransactionType()).isEqualTo(PointTransactionType.POINT_CHARGE);
    assertThat(history.getReferenceType()).isEqualTo(PointReferenceType.POINT_CHARGE);
    assertThat(history.getReferenceId()).isZero();
    assertThat(history.getIdempotencyKey()).isEqualTo("charge:payment-id");
  }

  @Test
  void chargeFailsWhenAmountIsNotPositive() {
    Member member = member(MEMBER_ID);

    assertThatThrownBy(() -> pointLedgerService.charge(member, 0L, "payment-id"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_AMOUNT);
    assertThatThrownBy(() -> pointLedgerService.charge(member, -10_000L, "payment-id"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_AMOUNT);
  }

  @Test
  void chargeFailsWhenPaymentIdIsInvalid() {
    Member member = member(MEMBER_ID);

    assertThatThrownBy(() -> pointLedgerService.charge(member, 10_000L, ""))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_POINT_REFERENCE);
    assertThatThrownBy(() -> pointLedgerService.charge(member, 10_000L, "   "))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_POINT_REFERENCE);
  }

  @Test
  void lockHostDepositMovesAvailableToLockedAndAppendsReserveLedger() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = lockedParticipant(member);
    PointAccount account = account(member, 20_000L);
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:1"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    PointHistory history = pointLedgerService.lockHostDeposit(participant);

    assertThat(account.getAvailableBalance()).isEqualTo(10_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isEqualTo(10_000L);
    assertThat(history.getAmount()).isEqualTo(-10_000L);
    assertThat(history.getAvailableAfter()).isEqualTo(10_000L);
    assertThat(history.getReservedAfter()).isZero();
    assertThat(history.getLockedAfter()).isEqualTo(10_000L);
    assertThat(history.getTransactionType()).isEqualTo(PointTransactionType.CREW_DEPOSIT_RESERVE);
    assertThat(history.getReferenceType()).isEqualTo(PointReferenceType.CREW_PARTICIPANT);
    assertThat(history.getReferenceId()).isEqualTo(PARTICIPANT_ID);
    assertThat(history.getIdempotencyKey()).isEqualTo("crew:10:participant:1:reserve:1");
  }

  @Test
  void lockPendingReserveMovesReservedToLockedAndAppendsLedger() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointAccount account = account(member, 0L);
    account.increaseAvailable(DEPOSIT);
    account.reserve(DEPOSIT);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve-lock:1"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    pointLedgerService.lockPendingReserve(participant);

    assertThat(account.getAvailableBalance()).isZero();
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isEqualTo(DEPOSIT);
    then(pointHistoryRepository)
        .should()
        .findByIdempotencyKey("crew:10:participant:1:reserve-lock:1");
    then(pointHistoryRepository).should().save(any(PointHistory.class));
  }

  @Test
  void lockPendingReserveIsIdempotentByHistoryKey() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointAccount account = account(member, 0L);
    account.increaseAvailable(DEPOSIT);
    account.reserve(DEPOSIT);

    PointHistory existing =
        pointHistory(
            member,
            -DEPOSIT,
            PointTransactionType.CREW_DEPOSIT_RESERVE,
            "crew:10:participant:1:reserve-lock:1");
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve-lock:1"))
        .willReturn(Optional.empty(), Optional.of(existing));
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    pointLedgerService.lockPendingReserve(participant);
    pointLedgerService.lockPendingReserve(participant);

    assertThat(account.getAvailableBalance()).isZero();
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isEqualTo(DEPOSIT);
    then(pointAccountRepository).should(times(1)).findByMemberIdForUpdate(MEMBER_ID);
    then(pointHistoryRepository)
        .should(times(2))
        .findByIdempotencyKey("crew:10:participant:1:reserve-lock:1");
    then(pointHistoryRepository).should(times(1)).save(any(PointHistory.class));
  }

  @Test
  void reservePendingDepositMovesAvailableToReservedAndAppendsReserveLedger() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointAccount account = account(member, 20_000L);
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:1"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    PointHistory history = pointLedgerService.reservePendingDeposit(participant);

    assertThat(account.getAvailableBalance()).isEqualTo(10_000L);
    assertThat(account.getReservedBalance()).isEqualTo(10_000L);
    assertThat(account.getLockedBalance()).isZero();
    assertThat(history.getAmount()).isEqualTo(-10_000L);
    assertThat(history.getAvailableAfter()).isEqualTo(10_000L);
    assertThat(history.getReservedAfter()).isEqualTo(10_000L);
    assertThat(history.getLockedAfter()).isZero();
    assertThat(history.getIdempotencyKey()).isEqualTo("crew:10:participant:1:reserve:1");
  }

  @Test
  void releasePendingReserveMovesReservedToAvailableAppendsLedgerAndLinksParticipant() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointAccount account = account(member, 0L);
    account.increaseAvailable(DEPOSIT);
    account.reserve(DEPOSIT);
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve-release:1"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    PointHistory history = pointLedgerService.releasePendingReserve(participant);

    assertThat(account.getAvailableBalance()).isEqualTo(10_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();
    assertThat(history.getAmount()).isEqualTo(10_000L);
    assertThat(history.getTransactionType()).isEqualTo(PointTransactionType.CREW_RESERVE_RELEASE);
    assertThat(history.getIdempotencyKey()).isEqualTo("crew:10:participant:1:reserve-release:1");
    assertThat(participant.getReleasedPointHistory()).isEqualTo(history);
  }

  @Test
  void reservePendingDepositUsesNextCycleBasedOnExistingReleaseCount() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointAccount account = account(member, 20_000L);
    givenReserveCycle(PARTICIPANT_ID, 2L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:2"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    PointHistory history = pointLedgerService.reservePendingDeposit(participant);

    assertThat(history.getIdempotencyKey()).isEqualTo("crew:10:participant:1:reserve:2");
  }

  @Test
  void lifecycleSimulationReserveRejectReopenReserveApproveRefundIsConsistent() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointAccount account = account(member, 30_000L);

    // 1st reserve
    given(
            pointHistoryRepository.countByReferenceTypeAndReferenceIdAndTransactionType(
                PointReferenceType.CREW_PARTICIPANT,
                PARTICIPANT_ID,
                PointTransactionType.CREW_RESERVE_RELEASE))
        .willReturn(0L, 0L, 1L);

    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:1"))
        .willReturn(Optional.empty());
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve-release:1"))
        .willReturn(Optional.empty());
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:2"))
        .willReturn(Optional.empty());
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve-lock:2"))
        .willReturn(Optional.empty());
    given(
            pointHistoryRepository.findByIdempotencyKey(
                "crew:10:participant:1:settlement-refund:final"))
        .willReturn(Optional.empty());

    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    PointHistory firstReserve = pointLedgerService.reservePendingDeposit(participant);
    assertThat(firstReserve.getIdempotencyKey()).isEqualTo("crew:10:participant:1:reserve:1");
    assertThat(account.getAvailableBalance()).isEqualTo(20_000L);
    assertThat(account.getReservedBalance()).isEqualTo(10_000L);
    assertThat(account.getLockedBalance()).isZero();

    // reject path(동일 동작) => release
    participant.cancel(LocalDateTime.now());
    PointHistory firstRelease = pointLedgerService.releasePendingReserve(participant);
    assertThat(firstRelease.getIdempotencyKey())
        .isEqualTo("crew:10:participant:1:reserve-release:1");
    assertThat(account.getAvailableBalance()).isEqualTo(30_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();
    assertThat(participant.getReleasedPointHistory()).isEqualTo(firstRelease);

    // reopen from canceled
    participant.reopen(LocalDateTime.now());
    assertThat(participant.getStatus()).isEqualTo(CrewParticipantStatus.PENDING);
    assertThat(participant.getReleasedPointHistory()).isNull();

    // 2nd reserve with cycle increment
    PointHistory secondReserve = pointLedgerService.reservePendingDeposit(participant);
    assertThat(secondReserve.getIdempotencyKey()).isEqualTo("crew:10:participant:1:reserve:2");
    assertThat(account.getAvailableBalance()).isEqualTo(20_000L);
    assertThat(account.getReservedBalance()).isEqualTo(10_000L);
    assertThat(account.getLockedBalance()).isZero();

    // approve transition
    pointLedgerService.lockPendingReserve(participant);
    assertThat(account.getAvailableBalance()).isEqualTo(20_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isEqualTo(10_000L);

    // refund on settlement
    SettlementItem settlementItem = settlementItem(member, participant, DEPOSIT, 7_000L);
    ReflectionTestUtils.setField(settlementItem, "id", SETTLEMENT_ITEM_ID);

    PointHistory refund = pointLedgerService.refundSettlement(settlementItem);
    assertThat(refund.getIdempotencyKey())
        .isEqualTo("crew:10:participant:1:settlement-refund:final");
    assertThat(refund.getAmount()).isEqualTo(7_000L);
    assertThat(account.getAvailableBalance()).isEqualTo(27_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();
    assertThat(settlementItem.getPointHistory()).isEqualTo(refund);
  }

  @Test
  void commandReusesExistingLedgerWhenCanonicalInputMatches() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointHistory existing =
        pointHistory(
            member,
            -DEPOSIT,
            PointTransactionType.CREW_DEPOSIT_RESERVE,
            "crew:10:participant:1:reserve:1");
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:1"))
        .willReturn(Optional.of(existing));

    PointHistory history = pointLedgerService.reservePendingDeposit(participant);

    assertThat(history).isEqualTo(existing);
    then(pointAccountRepository).should(never()).findByMemberIdForUpdate(any());
    then(pointHistoryRepository).should(never()).save(any());
  }

  @Test
  void commandRejectsExistingLedgerWhenCanonicalInputDiffers() {
    Member otherMember = member(999L);
    CrewParticipant participant = pendingParticipant(member(MEMBER_ID));
    PointHistory existing =
        pointHistory(
            otherMember,
            -DEPOSIT,
            PointTransactionType.CREW_DEPOSIT_RESERVE,
            "crew:10:participant:1:reserve:1");
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:1"))
        .willReturn(Optional.of(existing));

    assertThatThrownBy(() -> pointLedgerService.reservePendingDeposit(participant))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.IDEMPOTENCY_CONFLICT);
  }

  @Test
  void releasePendingReserveReturnsAlreadyLinkedHistoryWithoutRepositoryMutation() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointHistory linked =
        pointHistory(
            member,
            DEPOSIT,
            PointTransactionType.CREW_RESERVE_RELEASE,
            "crew:10:participant:1:reserve-release:1");
    participant.linkReleasedPointHistory(linked);

    PointHistory history = pointLedgerService.releasePendingReserve(participant);

    assertThat(history).isEqualTo(linked);
    then(pointHistoryRepository).shouldHaveNoInteractions();
    then(pointAccountRepository).shouldHaveNoInteractions();
  }

  @Test
  void releasePendingReserveLinksExistingIdempotentLedgerWhenNotYetLinked() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointHistory existing =
        pointHistory(
            member,
            DEPOSIT,
            PointTransactionType.CREW_RESERVE_RELEASE,
            "crew:10:participant:1:reserve-release:1");
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve-release:1"))
        .willReturn(Optional.of(existing));

    PointHistory history = pointLedgerService.releasePendingReserve(participant);

    assertThat(history).isEqualTo(existing);
    assertThat(participant.getReleasedPointHistory()).isEqualTo(existing);
    then(pointAccountRepository).should(never()).findByMemberIdForUpdate(any());
    then(pointHistoryRepository).should(never()).save(any());
  }

  @Test
  void refundSettlementSettlesLockedDepositAppendsLedgerAndLinksSettlementItem() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = lockedParticipant(member);
    SettlementItem settlementItem = settlementItem(member, participant, DEPOSIT, 7_000L);
    PointAccount account = account(member, 0L);
    account.increaseAvailable(DEPOSIT);
    account.lockFromAvailable(DEPOSIT);
    given(
            pointHistoryRepository.findByIdempotencyKey(
                "crew:10:participant:1:settlement-refund:final"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    PointHistory history = pointLedgerService.refundSettlement(settlementItem);

    assertThat(account.getAvailableBalance()).isEqualTo(7_000L);
    assertThat(account.getReservedBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();
    assertThat(history.getAmount()).isEqualTo(7_000L);
    assertThat(history.getAvailableAfter()).isEqualTo(7_000L);
    assertThat(history.getTransactionType()).isEqualTo(PointTransactionType.CREW_SETTLEMENT_REFUND);
    assertThat(history.getReferenceType()).isEqualTo(PointReferenceType.SETTLEMENT_ITEM);
    assertThat(history.getReferenceId()).isEqualTo(SETTLEMENT_ITEM_ID);
    assertThat(history.getIdempotencyKey())
        .isEqualTo("crew:10:participant:1:settlement-refund:final");
    assertThat(settlementItem.getPointHistory()).isEqualTo(history);
  }

  @Test
  void refundSettlementAllowsZeroRefundWhileSettlingLockedDeposit() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = lockedParticipant(member);
    SettlementItem settlementItem = settlementItem(member, participant, DEPOSIT, 0L);
    PointAccount account = account(member, 0L);
    account.increaseAvailable(DEPOSIT);
    account.lockFromAvailable(DEPOSIT);
    given(
            pointHistoryRepository.findByIdempotencyKey(
                "crew:10:participant:1:settlement-refund:final"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));
    given(pointHistoryRepository.save(any(PointHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    PointHistory history = pointLedgerService.refundSettlement(settlementItem);

    assertThat(account.getAvailableBalance()).isZero();
    assertThat(account.getLockedBalance()).isZero();
    assertThat(history.getAmount()).isZero();
    assertThat(settlementItem.getPointHistory()).isEqualTo(history);
  }

  @Test
  void refundSettlementReturnsAlreadyLinkedHistoryWithoutRepositoryMutation() {
    Member member = member(MEMBER_ID);
    SettlementItem settlementItem =
        settlementItem(member, lockedParticipant(member), DEPOSIT, 7_000L);
    PointHistory linked =
        settlementPointHistory(member, 7_000L, "crew:10:participant:1:settlement-refund:final");
    settlementItem.linkPointHistory(linked);

    PointHistory history = pointLedgerService.refundSettlement(settlementItem);

    assertThat(history).isEqualTo(linked);
    then(pointHistoryRepository).shouldHaveNoInteractions();
    then(pointAccountRepository).shouldHaveNoInteractions();
  }

  @Test
  void refundSettlementLinksExistingIdempotentLedgerWhenNotYetLinked() {
    Member member = member(MEMBER_ID);
    SettlementItem settlementItem =
        settlementItem(member, lockedParticipant(member), DEPOSIT, 7_000L);
    PointHistory existing =
        settlementPointHistory(member, 7_000L, "crew:10:participant:1:settlement-refund:final");
    given(
            pointHistoryRepository.findByIdempotencyKey(
                "crew:10:participant:1:settlement-refund:final"))
        .willReturn(Optional.of(existing));

    PointHistory history = pointLedgerService.refundSettlement(settlementItem);

    assertThat(history).isEqualTo(existing);
    assertThat(settlementItem.getPointHistory()).isEqualTo(existing);
    then(pointAccountRepository).should(never()).findByMemberIdForUpdate(any());
    then(pointHistoryRepository).should(never()).save(any());
  }

  @Test
  void commandFailsWhenPointAccountDoesNotExist() {
    CrewParticipant participant = pendingParticipant(member(MEMBER_ID));
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:1"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> pointLedgerService.reservePendingDeposit(participant))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.POINT_ACCOUNT_NOT_FOUND);
  }

  @Test
  void commandFailsWhenBalanceIsInsufficient() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    PointAccount account = account(member, 0L);
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:1"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willReturn(Optional.of(account));

    assertThatThrownBy(() -> pointLedgerService.reservePendingDeposit(participant))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
  }

  @Test
  void reservePendingDepositPropagatesConcurrentUpdateFailure() {
    Member member = member(MEMBER_ID);
    CrewParticipant participant = pendingParticipant(member);
    givenReserveCycle(PARTICIPANT_ID, 1L);
    given(pointHistoryRepository.findByIdempotencyKey("crew:10:participant:1:reserve:1"))
        .willReturn(Optional.empty());
    given(pointAccountRepository.findByMemberIdForUpdate(MEMBER_ID))
        .willThrow(new OptimisticLockingFailureException("optimistic lock failure"));

    assertThatThrownBy(() -> pointLedgerService.reservePendingDeposit(participant))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void commandFailsWhenParticipantReferenceIsNotPersisted() {
    CrewParticipant participant = pendingParticipant(member(MEMBER_ID));
    ReflectionTestUtils.setField(participant, "id", null);

    assertThatThrownBy(() -> pointLedgerService.reservePendingDeposit(participant))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.INVALID_POINT_REFERENCE);
  }

  @Test
  void refundSettlementFailsWhenCanonicalInputDiffersForSameFinalKey() {
    Member member = member(MEMBER_ID);
    SettlementItem settlementItem =
        settlementItem(member, lockedParticipant(member), DEPOSIT, 7_000L);
    PointHistory existing =
        settlementPointHistory(member, 5_000L, "crew:10:participant:1:settlement-refund:final");
    given(
            pointHistoryRepository.findByIdempotencyKey(
                "crew:10:participant:1:settlement-refund:final"))
        .willReturn(Optional.of(existing));

    assertThatThrownBy(() -> pointLedgerService.refundSettlement(settlementItem))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(PointErrorCode.IDEMPOTENCY_CONFLICT);
  }

  private void givenReserveCycle(Long participantId, long cycle) {
    given(
            pointHistoryRepository.countByReferenceTypeAndReferenceIdAndTransactionType(
                PointReferenceType.CREW_PARTICIPANT,
                participantId,
                PointTransactionType.CREW_RESERVE_RELEASE))
        .willReturn(cycle - 1);
  }

  private static PointAccount account(Member member, long availableBalance) {
    PointAccount account = PointAccount.create(member);
    if (availableBalance > 0) {
      account.increaseAvailable(availableBalance);
    }
    return account;
  }

  private static PointHistory pointHistory(
      Member member, long amount, PointTransactionType transactionType, String idempotencyKey) {
    return PointHistory.create(
        member,
        amount,
        10_000L,
        0L,
        0L,
        transactionType,
        PointReferenceType.CREW_PARTICIPANT,
        PARTICIPANT_ID,
        idempotencyKey);
  }

  private static PointHistory settlementPointHistory(
      Member member, long amount, String idempotencyKey) {
    return PointHistory.create(
        member,
        amount,
        amount,
        0L,
        0L,
        PointTransactionType.CREW_SETTLEMENT_REFUND,
        PointReferenceType.SETTLEMENT_ITEM,
        SETTLEMENT_ITEM_ID,
        idempotencyKey);
  }

  private static SettlementItem settlementItem(
      Member member, CrewParticipant participant, long depositAmount, long refundAmount) {
    SettlementItem settlementItem = newSettlementItem();
    ReflectionTestUtils.setField(settlementItem, "id", SETTLEMENT_ITEM_ID);
    ReflectionTestUtils.setField(settlementItem, "member", member);
    ReflectionTestUtils.setField(settlementItem, "crewParticipant", participant);
    ReflectionTestUtils.setField(settlementItem, "depositAmount", depositAmount);
    ReflectionTestUtils.setField(settlementItem, "refundAmount", refundAmount);
    return settlementItem;
  }

  private static SettlementItem newSettlementItem() {
    try {
      Constructor<SettlementItem> constructor = SettlementItem.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  private static CrewParticipant lockedParticipant(Member member) {
    Crew crew = crew(member);
    CrewParticipant participant =
        CrewParticipant.create(crew, member, DEPOSIT, LocalDateTime.of(2026, 6, 5, 9, 0));
    ReflectionTestUtils.setField(participant, "id", PARTICIPANT_ID);
    return participant;
  }

  private static CrewParticipant pendingParticipant(Member member) {
    Crew crew = crew(member);
    CrewParticipant participant =
        CrewParticipant.createPending(crew, member, DEPOSIT, LocalDateTime.of(2026, 6, 5, 9, 0));
    ReflectionTestUtils.setField(participant, "id", PARTICIPANT_ID);
    return participant;
  }

  private static Crew crew(Member host) {
    Crew crew =
        Crew.create(
            host,
            "크루",
            "설명",
            null,
            "OTHER",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.of(2026, 6, 5, 9, 0),
            DEPOSIT,
            2,
            5,
            LocalDateTime.of(2026, 6, 6, 9, 0),
            LocalDateTime.of(2026, 6, 7, 0, 0),
            LocalDateTime.of(2026, 7, 7, 23, 59));
    ReflectionTestUtils.setField(crew, "id", CREW_ID);
    return crew;
  }

  private static Member member(Long id) {
    Member member = Member.create("member-" + id + "@example.com", "password-hash", "회원" + id);
    ReflectionTestUtils.setField(member, "id", id);
    return member;
  }
}
