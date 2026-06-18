package com.oit.dondok.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementMeProjection;
import com.oit.dondok.domain.settlement.repository.SettlementQueryRepository;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementQueryGuardTest {

  private static final Long CREW_ID = 1L;
  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");

  @Mock private CrewRepository crewRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private SettlementQueryRepository settlementQueryRepository;

  @InjectMocks private SettlementQueryGuard settlementQueryGuard;

  @Test
  void validateSummaryQueryInputAcceptsValidInput() {
    assertThatCode(() -> settlementQueryGuard.validateSummaryQueryInput(CREW_ID, MEMBER_UUID))
        .doesNotThrowAnyException();
  }

  @Test
  void validateSummaryQueryInputRejectsInvalidInputs() {
    assertThatThrownBy(() -> settlementQueryGuard.validateSummaryQueryInput(0L, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));

    assertThatThrownBy(() -> settlementQueryGuard.validateSummaryQueryInput(null, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));

    assertThatThrownBy(() -> settlementQueryGuard.validateSummaryQueryInput(CREW_ID, null))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void validateSummaryQueryAccessAllowsHost() {
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, MEMBER_UUID)).willReturn(true);

    settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID);
  }

  @Test
  void validateSummaryQueryAccessAllowsLockedParticipant() {
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, MEMBER_UUID)).willReturn(false);
    given(
            crewParticipantRepository.existsByCrewIdAndMemberUuidAndStatus(
                CREW_ID, MEMBER_UUID, CrewParticipantStatus.LOCKED))
        .willReturn(true);

    settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID);
  }

  @Test
  void validateSummaryQueryAccessRejectsMissingCrew() {
    given(crewRepository.existsById(CREW_ID)).willReturn(false);

    assertThatThrownBy(() -> settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CrewErrorCode.CREW_NOT_FOUND));
  }

  @Test
  void validateSummaryQueryAccessRejectsUnauthorizedUser() {
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, MEMBER_UUID)).willReturn(false);
    given(
            crewParticipantRepository.existsByCrewIdAndMemberUuidAndStatus(
                CREW_ID, MEMBER_UUID, CrewParticipantStatus.LOCKED))
        .willReturn(false);

    assertThatThrownBy(() -> settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED));
  }

  @Test
  void validateDetailQueryInputAcceptsValidInputs() {
    assertThatCode(() -> settlementQueryGuard.validateDetailQueryInput(1L, MEMBER_UUID))
        .doesNotThrowAnyException();
  }

  @Test
  void validateDetailQueryInputRejectsInvalidInputs() {
    assertThatThrownBy(() -> settlementQueryGuard.validateDetailQueryInput(0L, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));

    assertThatThrownBy(() -> settlementQueryGuard.validateDetailQueryInput(null, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));

    assertThatThrownBy(() -> settlementQueryGuard.validateDetailQueryInput(1L, null))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT));
  }

  @Test
  void validateCrewAccessAllowsHost() {
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, MEMBER_UUID)).willReturn(true);

    settlementQueryGuard.validateCrewAccess(CREW_ID, MEMBER_UUID);
  }

  @Test
  void validateCrewAccessAllowsLockedParticipant() {
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, MEMBER_UUID)).willReturn(false);
    given(
            crewParticipantRepository.existsByCrewIdAndMemberUuidAndStatus(
                CREW_ID, MEMBER_UUID, CrewParticipantStatus.LOCKED))
        .willReturn(true);

    settlementQueryGuard.validateCrewAccess(CREW_ID, MEMBER_UUID);
  }

  @Test
  void validateCrewAccessRejectsUnauthorizedUser() {
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, MEMBER_UUID)).willReturn(false);
    given(
            crewParticipantRepository.existsByCrewIdAndMemberUuidAndStatus(
                CREW_ID, MEMBER_UUID, CrewParticipantStatus.LOCKED))
        .willReturn(false);

    assertThatThrownBy(() -> settlementQueryGuard.validateCrewAccess(CREW_ID, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED));
  }

  @Test
  void requireAccessibleSettlementReturnsAccessGuardedSettlement() {
    Settlement settlement = mock(Settlement.class);
    given(settlementQueryRepository.findAccessibleByIdAndMemberUuid(1L, MEMBER_UUID))
        .willReturn(Optional.of(settlement));

    Settlement result = settlementQueryGuard.requireAccessibleSettlement(1L, MEMBER_UUID);

    assertThat(result).isEqualTo(settlement);
    then(settlementQueryRepository).should().findAccessibleByIdAndMemberUuid(1L, MEMBER_UUID);
  }

  @Test
  void requireAccessibleSettlementRejectsMissingOrUnauthorizedSettlementAsAccessDenied() {
    given(settlementQueryRepository.findAccessibleByIdAndMemberUuid(1L, MEMBER_UUID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> settlementQueryGuard.requireAccessibleSettlement(1L, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED));
  }

  @Test
  void requireAccessibleSettlementMeReturnsAccessGuardedProjection() {
    SettlementMeProjection projection =
        new SettlementMeProjection(
            1L,
            2L,
            null,
            null,
            null,
            SettlementStatus.SUCCEEDED,
            1,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null,
            null);
    given(settlementQueryRepository.findSettlementMeByIdAndMemberUuid(1L, MEMBER_UUID))
        .willReturn(Optional.of(projection));

    var result = settlementQueryGuard.requireAccessibleSettlementMe(1L, MEMBER_UUID);

    assertThat(result).isEqualTo(projection);
    then(settlementQueryRepository).should().findSettlementMeByIdAndMemberUuid(1L, MEMBER_UUID);
  }

  @Test
  void requireAccessibleSettlementMeRejectsMissingOrUnauthorizedSettlementAsAccessDenied() {
    given(settlementQueryRepository.findSettlementMeByIdAndMemberUuid(1L, MEMBER_UUID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> settlementQueryGuard.requireAccessibleSettlementMe(1L, MEMBER_UUID))
        .isInstanceOfSatisfying(
            CustomException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED));
  }

  @Test
  void authorizeSummaryQueryReturnsOptionalEmptyWhenNoSettlement() {
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, MEMBER_UUID)).willReturn(true);
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());

    Optional<Settlement> settlement =
        settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID);

    assertThat(settlement).isEmpty();
  }

  @Test
  void authorizeSummaryQueryReturnsSettlementWhenExists() {
    Settlement settlement = mock(Settlement.class);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, MEMBER_UUID)).willReturn(true);
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(settlement));

    Optional<Settlement> result = settlementQueryGuard.authorizeSummaryQuery(CREW_ID, MEMBER_UUID);

    assertThat(result).hasValue(settlement);
  }
}
