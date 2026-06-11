package com.oit.dondok.domain.point.service;

import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.point.dto.request.PointChargeRequest;
import com.oit.dondok.domain.point.dto.response.PointChargeResponse;
import com.oit.dondok.domain.point.entity.PointCharge;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.exception.PointErrorCode;
import com.oit.dondok.domain.point.port.PaymentConfirmClient;
import com.oit.dondok.domain.point.port.PaymentConfirmRequest;
import com.oit.dondok.domain.point.port.PaymentConfirmResult;
import com.oit.dondok.domain.point.repository.PointChargeRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.global.util.SeoulDateTimeUtils;
import com.oit.dondok.global.validation.ChargeAmountPolicy;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class PointChargeService {

  private static final String KRW = "KRW";
  private static final String DONE = "DONE";

  private final MemberRepository memberRepository;
  private final PointChargeRepository pointChargeRepository;
  private final PaymentConfirmClient paymentConfirmClient;
  private final PointLedgerService pointLedgerService;
  private final TransactionTemplate transactionTemplate;

  public PointChargeService(
      MemberRepository memberRepository,
      PointChargeRepository pointChargeRepository,
      PaymentConfirmClient paymentConfirmClient,
      PointLedgerService pointLedgerService,
      PlatformTransactionManager transactionManager) {
    this.memberRepository = memberRepository;
    this.pointChargeRepository = pointChargeRepository;
    this.paymentConfirmClient = paymentConfirmClient;
    this.pointLedgerService = pointLedgerService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Transactional(propagation = Propagation.NEVER)
  public PointChargeResult charge(UUID memberUuid, PointChargeRequest request) {
    validateRequest(request);
    Member member =
        memberRepository
            .findByUuid(memberUuid)
            .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    PrepareResult prepared =
        mapIntegrityConflict(() -> inTransaction(() -> prepareCharge(member, request)));
    if (prepared.response() != null) {
      return new PointChargeResult(false, prepared.response());
    }

    PaymentConfirmResult confirmResult = confirmPayment(request);
    if (!matchesConfirmResult(confirmResult, request)) {
      cancelMismatchedPayment(request);
      recordFailure(request.paymentId(), "PAYMENT_CONFIRM_MISMATCH", "Toss confirmation mismatch");
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_MISMATCH);
    }

    try {
      return mapIntegrityConflict(() -> inTransaction(() -> completeCharge(member, request)));
    } catch (CustomException e) {
      compensateConfirmedPaymentAfterCompletionFailure(request, e);
      throw e;
    } catch (RuntimeException e) {
      log.error(
          "Point charge completion failed after Toss confirmation succeeded. payment_id={}, order_id={}, amount={}",
          request.paymentId(),
          request.orderId(),
          request.amount(),
          e);
      throw e;
    }
  }

  private PrepareResult prepareCharge(Member member, PointChargeRequest request) {
    return pointChargeRepository
        .findByPaymentIdForUpdate(request.paymentId())
        .map(charge -> prepareExisting(member, request, charge))
        .orElseGet(
            () -> {
              PointCharge charge =
                  PointCharge.createPending(
                      member, request.paymentId(), request.orderId(), request.amount());
              try {
                pointChargeRepository.save(charge);
              } catch (DataIntegrityViolationException e) {
                return pointChargeRepository
                    .findByPaymentIdForUpdate(request.paymentId())
                    .map(existing -> prepareExisting(member, request, existing))
                    .orElseThrow(() -> new CustomException(PointErrorCode.IDEMPOTENCY_CONFLICT, e));
              }
              return new PrepareResult(null);
            });
  }

  private PrepareResult prepareExisting(
      Member member, PointChargeRequest request, PointCharge charge) {
    if (charge.isLinked()) {
      ensureSameCanonicalInput(member, request, charge);
      return new PrepareResult(toResponse(charge.getPointHistory()));
    }
    if (!charge.belongsTo(member)) {
      throw new CustomException(PointErrorCode.IDEMPOTENCY_CONFLICT);
    }
    if (!charge.matches(member, request.orderId(), request.amount())) {
      throw new CustomException(PointErrorCode.IDEMPOTENCY_CONFLICT);
    }
    return new PrepareResult(null);
  }

  private PaymentConfirmResult confirmPayment(PointChargeRequest request) {
    try {
      return paymentConfirmClient.confirm(
          new PaymentConfirmRequest(request.paymentId(), request.orderId(), request.amount()));
    } catch (CustomException e) {
      recordFailure(request.paymentId(), e.getErrorCode().getCode(), e.getMessage());
      throw e;
    } catch (RuntimeException e) {
      recordFailure(request.paymentId(), "PAYMENT_CONFIRM_FAILED", e.getMessage());
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_FAILED, e);
    }
  }

  private PointChargeResult completeCharge(Member member, PointChargeRequest request) {
    PointCharge charge =
        pointChargeRepository
            .findByPaymentIdForUpdate(request.paymentId())
            .orElseThrow(() -> new CustomException(PointErrorCode.PAYMENT_CONFIRM_STALE));
    if (charge.isLinked()) {
      ensureSameCanonicalInput(member, request, charge);
      return new PointChargeResult(false, toResponse(charge.getPointHistory()));
    }
    if (!charge.matches(member, request.orderId(), request.amount())) {
      throw new CustomException(PointErrorCode.PAYMENT_CONFIRM_STALE);
    }

    PointHistory history = pointLedgerService.charge(member, request.amount(), request.paymentId());
    charge.complete(history);
    return new PointChargeResult(true, toResponse(history));
  }

  private void recordFailure(String paymentId, String failureCode, String failureMessage) {
    inTransaction(
        () -> {
          pointChargeRepository
              .findByPaymentIdForUpdate(paymentId)
              .ifPresent(charge -> charge.fail(failureCode, failureMessage));
          return null;
        });
  }

  private void cancelMismatchedPayment(PointChargeRequest request) {
    try {
      paymentConfirmClient.cancel(request.paymentId(), "Point charge confirmation mismatch");
    } catch (RuntimeException e) {
      log.error(
          "Failed to cancel mismatched Toss payment. payment_id={}, order_id={}, amount={}",
          request.paymentId(),
          request.orderId(),
          request.amount(),
          e);
    }
  }

  private void compensateConfirmedPaymentAfterCompletionFailure(
      PointChargeRequest request, CustomException failure) {
    String failureCode = failure.getErrorCode().getCode();
    String cancelReason = "Point charge ledger completion failed: " + failureCode;
    try {
      paymentConfirmClient.cancel(request.paymentId(), cancelReason);
    } catch (RuntimeException cancelFailure) {
      log.error(
          "Failed to cancel confirmed Toss payment after point charge completion failure. payment_id={}, order_id={}, amount={}, failure_code={}",
          request.paymentId(),
          request.orderId(),
          request.amount(),
          failureCode,
          cancelFailure);
    }

    try {
      recordFailure(request.paymentId(), failureCode, failure.getMessage());
    } catch (RuntimeException recordFailure) {
      log.error(
          "Failed to record point charge completion failure after Toss confirmation. payment_id={}, order_id={}, amount={}, failure_code={}",
          request.paymentId(),
          request.orderId(),
          request.amount(),
          failureCode,
          recordFailure);
    }
  }

  private void ensureSameCanonicalInput(
      Member member, PointChargeRequest request, PointCharge charge) {
    if (!charge.matches(member, request.orderId(), request.amount())) {
      throw new CustomException(PointErrorCode.IDEMPOTENCY_CONFLICT);
    }
  }

  private boolean matchesConfirmResult(PaymentConfirmResult result, PointChargeRequest request) {
    return result != null
        && Objects.equals(result.paymentId(), request.paymentId())
        && Objects.equals(result.orderId(), request.orderId())
        && Objects.equals(result.totalAmount(), request.amount())
        && KRW.equals(result.currency())
        && DONE.equals(result.status());
  }

  private PointChargeResponse toResponse(PointHistory history) {
    return new PointChargeResponse(
        history.getId(),
        history.getMember().getUuid(),
        history.getAmount(),
        history.getAvailableAfter(),
        history.getTransactionType(),
        SeoulDateTimeUtils.toSeoulOffset(history.getCreatedAt()));
  }

  private void validateRequest(PointChargeRequest request) {
    if (request == null || isBlank(request.paymentId()) || isBlank(request.orderId())) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT);
    }
    if (!ChargeAmountPolicy.isValid(request.amount())) {
      throw new CustomException(PointErrorCode.INVALID_AMOUNT);
    }
  }

  private <T> T inTransaction(Supplier<T> supplier) {
    return transactionTemplate.execute(status -> supplier.get());
  }

  private <T> T mapIntegrityConflict(Supplier<T> supplier) {
    try {
      return supplier.get();
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(PointErrorCode.IDEMPOTENCY_CONFLICT, e);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record PrepareResult(PointChargeResponse response) {}
}
