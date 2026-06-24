package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.point.entity.PointReferenceType;
import com.oit.dondok.domain.point.entity.PointTransactionType;
import com.oit.dondok.domain.point.service.PointLedgerService;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementFailureCode;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import com.oit.dondok.domain.settlement.entity.SettlementRuleContextSnapshot;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementItemRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementBatchCommandServiceTest {

  private static final Long SETTLEMENT_ID = 10L;
  private static final Long SETTLEMENT_ITEM_ID = 100L;
  private static final String BATCH_RUN_KEY = "batch-001";
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 0, 0);

  @Mock private SettlementRepository settlementRepository;
  @Mock private SettlementItemRepository settlementItemRepository;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private SettlementNotificationService settlementNotificationService;

  @InjectMocks private SettlementBatchCommandService service;

  @Test
  void claimSettlementUsesConditionalClaimWithRetryLimit() {
    given(
            settlementRepository.claimRunnable(
                SETTLEMENT_ID,
                BATCH_RUN_KEY,
                NOW,
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(1);

    boolean claimed = service.claimSettlement(SETTLEMENT_ID, BATCH_RUN_KEY, NOW);

    org.assertj.core.api.Assertions.assertThat(claimed).isTrue();
  }

  @Test
  void claimSettlementReturnsFalseForRaceLoser() {
    given(
            settlementRepository.claimRunnable(
                SETTLEMENT_ID,
                BATCH_RUN_KEY,
                NOW,
                List.of(SettlementStatus.PENDING, SettlementStatus.RETRY_WAIT),
                Settlement.MAX_RETRY_COUNT))
        .willReturn(0);

    boolean claimed = service.claimSettlement(SETTLEMENT_ID, BATCH_RUN_KEY, NOW);

    org.assertj.core.api.Assertions.assertThat(claimed).isFalse();
  }

  @Test
  void refundOneSettlementItemThrowsInputLoadFailedWhenSettlementItemNotFound() {
    given(settlementItemRepository.findById(SETTLEMENT_ITEM_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.refundOneSettlementItem(SETTLEMENT_ITEM_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.INPUT_LOAD_FAILED);
  }

  @Test
  void refundOneSettlementItemMapsLedgerFailureToPointCreditFailure() {
    SettlementItem settlementItem = org.mockito.Mockito.mock(SettlementItem.class);
    given(settlementItemRepository.findById(SETTLEMENT_ITEM_ID))
        .willReturn(Optional.of(settlementItem));
    given(pointLedgerService.refundSettlement(settlementItem))
        .willThrow(new IllegalStateException("원장 반영 실패"));

    assertThatThrownBy(() -> service.refundOneSettlementItem(SETTLEMENT_ITEM_ID))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.POINT_CREDIT_FAILED);
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void refundOneSettlementItemPublishesRefundCreditedEventAfterNewPointCredit() {
    Member member = member();
    SettlementItem settlementItem = settlementItemWithNotificationPayload(member);
    given(settlementItemRepository.findById(SETTLEMENT_ITEM_ID))
        .willReturn(Optional.of(settlementItem));
    given(pointLedgerService.refundSettlement(settlementItem))
        .willReturn(settlementPointHistory(member));

    service.refundOneSettlementItem(SETTLEMENT_ITEM_ID);

    assertThat(settlementItem.getPointHistory()).isNotNull();
    ArgumentCaptor<SettlementRefundCreditedNotificationEvent> captor =
        ArgumentCaptor.forClass(SettlementRefundCreditedNotificationEvent.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    SettlementRefundCreditedNotificationEvent event = captor.getValue();
    assertThat(event.memberId()).isEqualTo(1L);
    assertThat(event.settlementId()).isEqualTo(SETTLEMENT_ID);
    assertThat(event.refundAmount()).isEqualTo(7_000L);
    assertThat(event.crewTitle()).isEqualTo("크루");
  }

  @Test
  void refundOneSettlementItemDoesNotPublishEventWhenAlreadyCredited() {
    Member member = member();
    SettlementItem settlementItem = settlementItem(member);
    PointHistory linked = settlementPointHistory(member);
    settlementItem.linkPointHistory(linked);
    given(settlementItemRepository.findById(SETTLEMENT_ITEM_ID))
        .willReturn(Optional.of(settlementItem));
    given(pointLedgerService.refundSettlement(settlementItem)).willReturn(linked);

    service.refundOneSettlementItem(SETTLEMENT_ITEM_ID);

    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void verifyAndMarkSucceededRequiresAllItemsLinked() {
    Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(settlementItemRepository.countBySettlementId(SETTLEMENT_ID)).willReturn(2L);
    given(settlementItemRepository.countBySettlementIdAndPointHistoryIsNotNull(SETTLEMENT_ID))
        .willReturn(1L);

    assertThatThrownBy(() -> service.verifyAndMarkSucceeded(SETTLEMENT_ID, NOW))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.POINT_CREDIT_FAILED);
  }

  @Test
  void verifyAndMarkSucceededThrowsInputLoadFailedWhenSettlementNotFound() {
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.verifyAndMarkSucceeded(SETTLEMENT_ID, NOW))
        .isInstanceOf(SettlementBatchRunFailure.class)
        .extracting("failureCode")
        .isEqualTo(SettlementFailureCode.INPUT_LOAD_FAILED);
  }

  @Test
  void verifyAndMarkSucceededMarksSettlementSucceededOnAllItemsLinked() {
    Settlement settlement =
        Settlement.createPending(
            crew(),
            "batch-001",
            NOW,
            new SettlementRuleContextSnapshot(DailySettlementType.B, MissionFrequencyType.DAILY));
    ReflectionTestUtils.setField(settlement, "status", SettlementStatus.RUNNING);

    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(settlementItemRepository.countBySettlementId(SETTLEMENT_ID)).willReturn(1L);
    given(settlementItemRepository.countBySettlementIdAndPointHistoryIsNotNull(SETTLEMENT_ID))
        .willReturn(1L);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of());

    service.verifyAndMarkSucceeded(SETTLEMENT_ID, NOW);

    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.SUCCEEDED);
    then(settlementNotificationService)
        .should()
        .sendSettlementCompletedNotifications(settlement, List.of());
  }

  @Test
  void refundOneSettlementItemLinksPointHistoryAndThenSettlementCanSucceed() {
    Member member = member();
    SettlementItem settlementItem = settlementItemWithNotificationPayload(member);
    Settlement settlement =
        Settlement.createPending(
            crew(),
            "batch-001",
            NOW,
            new SettlementRuleContextSnapshot(DailySettlementType.B, MissionFrequencyType.DAILY));
    ReflectionTestUtils.setField(settlement, "status", SettlementStatus.RUNNING);

    given(settlementItemRepository.findById(SETTLEMENT_ITEM_ID))
        .willReturn(Optional.of(settlementItem));
    given(pointLedgerService.refundSettlement(settlementItem))
        .willReturn(settlementPointHistory(member));
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));
    given(settlementItemRepository.countBySettlementId(SETTLEMENT_ID)).willReturn(1L);
    given(settlementItemRepository.countBySettlementIdAndPointHistoryIsNotNull(SETTLEMENT_ID))
        .willReturn(1L);
    given(settlementItemRepository.findBySettlementIdOrderByIdAsc(SETTLEMENT_ID))
        .willReturn(List.of(settlementItem));

    service.refundOneSettlementItem(SETTLEMENT_ITEM_ID);
    service.verifyAndMarkSucceeded(SETTLEMENT_ID, NOW);

    assertThat(settlementItem.getPointHistory()).isNotNull();
    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.SUCCEEDED);
    then(settlementNotificationService)
        .should()
        .sendSettlementCompletedNotifications(settlement, List.of(settlementItem));
  }

  @Test
  void markRunFailureDelegatesToSettlementTransition() {
    Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
    given(settlementRepository.findById(SETTLEMENT_ID)).willReturn(Optional.of(settlement));

    service.markRunFailure(
        SETTLEMENT_ID, SettlementFailureCode.CALCULATION_FAILED, "calculation failed", NOW);

    then(settlement)
        .should()
        .markFailedAttempt(SettlementFailureCode.CALCULATION_FAILED, "calculation failed", NOW);
  }

  private Crew crew() {
    return org.mockito.Mockito.mock(Crew.class);
  }

  private Member member() {
    Member member = org.mockito.Mockito.mock(Member.class);
    return member;
  }

  private SettlementItem settlementItem(Member member) {
    SettlementItem settlementItem = newSettlementItem();
    ReflectionTestUtils.setField(settlementItem, "id", SETTLEMENT_ITEM_ID);
    ReflectionTestUtils.setField(settlementItem, "member", member);
    ReflectionTestUtils.setField(settlementItem, "refundAmount", 7_000L);
    return settlementItem;
  }

  private SettlementItem settlementItemWithNotificationPayload(Member member) {
    SettlementItem settlementItem = settlementItem(member);
    Settlement settlement = org.mockito.Mockito.mock(Settlement.class);
    Crew crew = org.mockito.Mockito.mock(Crew.class);
    CrewParticipant participant = org.mockito.Mockito.mock(CrewParticipant.class);
    given(member.getId()).willReturn(1L);
    given(settlement.getId()).willReturn(SETTLEMENT_ID);
    given(crew.getTitle()).willReturn("크루");
    given(participant.getCrew()).willReturn(crew);
    ReflectionTestUtils.setField(settlementItem, "settlement", settlement);
    ReflectionTestUtils.setField(settlementItem, "crewParticipant", participant);
    return settlementItem;
  }

  private PointHistory settlementPointHistory(Member member) {
    return PointHistory.create(
        member,
        7_000L,
        10_000L,
        0L,
        0L,
        PointTransactionType.CREW_SETTLEMENT_REFUND,
        PointReferenceType.SETTLEMENT_ITEM,
        SETTLEMENT_ITEM_ID,
        "crew:10:participant:1:settlement-refund:final");
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
}
