package com.oit.dondok.domain.point.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointLedgerService {

  private final PointAccountRepository pointAccountRepository;
  private final PointHistoryRepository pointHistoryRepository;

  @Transactional
  public PointHistory charge(Member member, Long amount, String paymentId) {
    PointCommand command = PointCommand.charge(member, amount, paymentId);

    return appendOrReuse(command, account -> account.increaseAvailable(command.amount()));
  }

  @Transactional
  public PointHistory lockHostDeposit(CrewParticipant participant) {
    PointCommand command = reserveCommand(participant);

    return appendOrReuse(command, account -> account.lockFromAvailable(command.depositAmount()));
  }

  @Transactional
  public void lockPendingReserve(CrewParticipant participant) {
    PointCommand command = PointCommand.lock(participant, reserveLockKey(participant));
    appendOrReuse(command, account -> account.lockFromReserved(command.depositAmount()));
  }

  @Transactional
  public PointHistory reservePendingDeposit(CrewParticipant participant) {
    PointCommand command = reserveCommand(participant);

    return appendOrReuse(command, account -> account.reserve(command.depositAmount()));
  }

  @Transactional
  public PointHistory releasePendingReserve(CrewParticipant participant) {
    if (participant.getReleasedPointHistory() != null) {
      return participant.getReleasedPointHistory();
    }

    PointCommand command = PointCommand.release(participant, reserveReleaseKey(participant));

    PointHistory history =
        appendOrReuse(command, account -> account.releaseReserved(command.depositAmount()));
    if (participant.getReleasedPointHistory() == null) {
      participant.linkReleasedPointHistory(history);
    }
    return history;
  }

  @Transactional
  public PointHistory releaseLockedDeposit(CrewParticipant participant) {
    PointCommand command =
        PointCommand.releaseLocked(participant, crewCancelRefundKey(participant));
    return appendOrReuse(
        command, account -> account.releaseLockedToAvailable(command.depositAmount()));
  }

  @Transactional
  public PointHistory refundSettlement(SettlementItem settlementItem) {
    if (settlementItem.getPointHistory() != null) {
      return settlementItem.getPointHistory();
    }

    PointCommand command = PointCommand.settlementRefund(settlementItem);
    PointHistory history =
        appendOrReuse(
            command,
            account -> account.settleLockedDeposit(command.depositAmount(), command.amount()));
    if (settlementItem.getPointHistory() == null) {
      settlementItem.linkPointHistory(history);
    }
    return history;
  }

  private PointCommand reserveCommand(CrewParticipant participant) {
    return PointCommand.reserve(participant, reserveKey(participant));
  }

  private String reserveKey(CrewParticipant participant) {
    return "crew:%d:participant:%d:reserve:%d"
        .formatted(crewId(participant), participantId(participant), reserveCycle(participant));
  }

  private String reserveLockKey(CrewParticipant participant) {
    return "crew:%d:participant:%d:reserve-lock:%d"
        .formatted(crewId(participant), participantId(participant), reserveCycle(participant));
  }

  private String reserveReleaseKey(CrewParticipant participant) {
    return "crew:%d:participant:%d:reserve-release:%d"
        .formatted(crewId(participant), participantId(participant), reserveCycle(participant));
  }

  private String crewCancelRefundKey(CrewParticipant participant) {
    return "crew:%d:participant:%d:crew-cancel-refund"
        .formatted(crewId(participant), participantId(participant));
  }

  private PointHistory appendOrReuse(PointCommand command, BalanceMutation balanceMutation) {
    return pointHistoryRepository
        .findByIdempotencyKey(command.idempotencyKey())
        .map(existing -> validateSameCanonicalInput(existing, command))
        .orElseGet(() -> append(command, balanceMutation));
  }

  private PointHistory append(PointCommand command, BalanceMutation balanceMutation) {
    PointAccount account = findAccountForUpdate(command.memberId());

    try {
      balanceMutation.apply(account);
      return pointHistoryRepository.save(
          PointHistory.create(
              account.getMember(),
              command.amount(),
              account.getAvailableBalance(),
              account.getReservedBalance(),
              account.getLockedBalance(),
              command.transactionType(),
              command.referenceType(),
              command.referenceId(),
              command.idempotencyKey()));
    } catch (DataIntegrityViolationException e) {
      return pointHistoryRepository
          .findByIdempotencyKey(command.idempotencyKey())
          .map(existing -> validateSameCanonicalInput(existing, command))
          .orElseThrow(() -> new CustomException(PointErrorCode.IDEMPOTENCY_CONFLICT, e));
    } catch (IllegalArgumentException e) {
      throw new CustomException(resolvePointMutationError(e), e);
    }
  }

  private PointAccount findAccountForUpdate(Long memberId) {
    return pointAccountRepository
        .findByMemberIdForUpdate(memberId)
        .orElseThrow(() -> new CustomException(PointErrorCode.POINT_ACCOUNT_NOT_FOUND));
  }

  private PointHistory validateSameCanonicalInput(PointHistory existing, PointCommand command) {
    Long existingMemberId = existing.getMember().getId();
    if (!Objects.equals(existingMemberId, command.memberId())
        || !Objects.equals(existing.getAmount(), command.amount())
        || existing.getTransactionType() != command.transactionType()
        || existing.getReferenceType() != command.referenceType()
        || !Objects.equals(existing.getReferenceId(), command.referenceId())) {
      throw new CustomException(PointErrorCode.IDEMPOTENCY_CONFLICT);
    }
    return existing;
  }

  private PointErrorCode resolvePointMutationError(IllegalArgumentException exception) {
    if (exception.getMessage() != null && exception.getMessage().contains("부족")) {
      return PointErrorCode.INSUFFICIENT_BALANCE;
    }
    return PointErrorCode.INVALID_AMOUNT;
  }

  private long reserveCycle(CrewParticipant participant) {
    return pointHistoryRepository.countByReferenceTypeAndReferenceIdAndTransactionType(
            PointReferenceType.CREW_PARTICIPANT,
            participantId(participant),
            PointTransactionType.CREW_RESERVE_RELEASE)
        + 1;
  }

  private static Long crewId(CrewParticipant participant) {
    if (participant.getCrew() == null || participant.getCrew().getId() == null) {
      throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
    }
    return participant.getCrew().getId();
  }

  private static Long participantId(CrewParticipant participant) {
    if (participant.getId() == null) {
      throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
    }
    return participant.getId();
  }

  private static Long memberId(CrewParticipant participant) {
    if (participant.getMember() == null || participant.getMember().getId() == null) {
      throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
    }
    return participant.getMember().getId();
  }

  private static Long memberId(Member member) {
    if (member == null || member.getId() == null) {
      throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
    }
    return member.getId();
  }

  private static Long memberId(SettlementItem settlementItem) {
    if (settlementItem.getMember() == null || settlementItem.getMember().getId() == null) {
      throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
    }
    return settlementItem.getMember().getId();
  }

  private static Long depositAmount(CrewParticipant participant) {
    if (participant.getDepositAmount() == null || participant.getDepositAmount() <= 0) {
      throw new CustomException(PointErrorCode.INVALID_AMOUNT);
    }
    return participant.getDepositAmount();
  }

  private static Long depositAmount(SettlementItem settlementItem) {
    if (settlementItem.getDepositAmount() == null || settlementItem.getDepositAmount() <= 0) {
      throw new CustomException(PointErrorCode.INVALID_AMOUNT);
    }
    return settlementItem.getDepositAmount();
  }

  private static Long refundAmount(SettlementItem settlementItem) {
    if (settlementItem.getRefundAmount() == null || settlementItem.getRefundAmount() < 0) {
      throw new CustomException(PointErrorCode.INVALID_AMOUNT);
    }
    if (settlementItem.getDepositAmount() != null
        && settlementItem.getRefundAmount() > settlementItem.getDepositAmount()) {
      throw new CustomException(PointErrorCode.INVALID_AMOUNT);
    }
    return settlementItem.getRefundAmount();
  }

  private static Long settlementItemId(SettlementItem settlementItem) {
    if (settlementItem.getId() == null) {
      throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
    }
    return settlementItem.getId();
  }

  private static Long crewId(SettlementItem settlementItem) {
    if (settlementItem.getCrewParticipant() == null
        || settlementItem.getCrewParticipant().getCrew() == null
        || settlementItem.getCrewParticipant().getCrew().getId() == null) {
      throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
    }
    return settlementItem.getCrewParticipant().getCrew().getId();
  }

  private static Long participantId(SettlementItem settlementItem) {
    if (settlementItem.getCrewParticipant() == null
        || settlementItem.getCrewParticipant().getId() == null) {
      throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
    }
    return settlementItem.getCrewParticipant().getId();
  }

  @FunctionalInterface
  private interface BalanceMutation {
    void apply(PointAccount account);
  }

  private record PointCommand(
      Long memberId,
      Long depositAmount,
      Long amount,
      PointTransactionType transactionType,
      PointReferenceType referenceType,
      Long referenceId,
      String idempotencyKey) {

    private static PointCommand reserve(CrewParticipant participant, String idempotencyKey) {
      Long depositAmount = PointLedgerService.depositAmount(participant);
      return new PointCommand(
          PointLedgerService.memberId(participant),
          depositAmount,
          -depositAmount,
          PointTransactionType.CREW_DEPOSIT_RESERVE,
          PointReferenceType.CREW_PARTICIPANT,
          participantId(participant),
          idempotencyKey);
    }

    private static PointCommand charge(Member member, Long amount, String paymentId) {
      if (amount == null || amount <= 0) {
        throw new CustomException(PointErrorCode.INVALID_AMOUNT);
      }
      if (paymentId == null || paymentId.isBlank()) {
        throw new CustomException(PointErrorCode.INVALID_POINT_REFERENCE);
      }
      return new PointCommand(
          PointLedgerService.memberId(member),
          amount,
          amount,
          PointTransactionType.POINT_CHARGE,
          PointReferenceType.POINT_CHARGE,
          0L,
          "charge:%s".formatted(paymentId));
    }

    private static PointCommand release(CrewParticipant participant, String idempotencyKey) {
      Long depositAmount = PointLedgerService.depositAmount(participant);
      return new PointCommand(
          PointLedgerService.memberId(participant),
          depositAmount,
          depositAmount,
          PointTransactionType.CREW_RESERVE_RELEASE,
          PointReferenceType.CREW_PARTICIPANT,
          participantId(participant),
          idempotencyKey);
    }

    private static PointCommand releaseLocked(CrewParticipant participant, String idempotencyKey) {
      Long depositAmount = PointLedgerService.depositAmount(participant);
      return new PointCommand(
          PointLedgerService.memberId(participant),
          depositAmount,
          depositAmount,
          PointTransactionType.CREW_CANCEL_REFUND,
          PointReferenceType.CREW_PARTICIPANT,
          participantId(participant),
          idempotencyKey);
    }

    private static PointCommand lock(CrewParticipant participant, String idempotencyKey) {
      Long depositAmount = PointLedgerService.depositAmount(participant);
      return new PointCommand(
          PointLedgerService.memberId(participant),
          depositAmount,
          -depositAmount,
          PointTransactionType.CREW_DEPOSIT_LOCK,
          PointReferenceType.CREW_PARTICIPANT,
          participantId(participant),
          idempotencyKey);
    }

    private static PointCommand settlementRefund(SettlementItem settlementItem) {
      Long depositAmount = PointLedgerService.depositAmount(settlementItem);
      Long refundAmount = PointLedgerService.refundAmount(settlementItem);
      return new PointCommand(
          PointLedgerService.memberId(settlementItem),
          depositAmount,
          refundAmount,
          PointTransactionType.CREW_SETTLEMENT_REFUND,
          PointReferenceType.SETTLEMENT_ITEM,
          settlementItemId(settlementItem),
          "crew:%d:participant:%d:settlement-refund:final"
              .formatted(crewId(settlementItem), participantId(settlementItem)));
    }
  }
}
