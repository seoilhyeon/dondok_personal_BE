package com.oit.dondok.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.member.dto.response.HostOperationSummaryResponse;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.HostOperationQueryRepository;
import com.oit.dondok.global.exception.CustomException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HostOperationSummaryServiceTest {

  @Mock private HostOperationQueryRepository hostOperationQueryRepository;

  @InjectMocks private HostOperationSummaryService hostOperationSummaryService;

  @Test
  void findHostOperationSummaryByMemberUuidReturnsTotalPendingCount() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
    given(hostOperationQueryRepository.existsByMemberUuid(memberUuid)).willReturn(true);
    given(hostOperationQueryRepository.countTotalPendingOperationsByHost(memberUuid))
        .willReturn(6L);

    HostOperationSummaryResponse response =
        hostOperationSummaryService.findHostOperationSummaryByMemberUuid(memberUuid);

    assertThat(response.memberUuid()).isEqualTo(memberUuid);
    assertThat(response.totalPendingCount()).isEqualTo(6L);
    assertThat(response.generatedAt().getOffset().getId()).isEqualTo("+09:00");
  }

  @Test
  void findHostOperationSummaryByMemberUuidAllowsZeroPendingCount() {
    UUID memberUuid = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c902");
    given(hostOperationQueryRepository.existsByMemberUuid(memberUuid)).willReturn(true);
    given(hostOperationQueryRepository.countTotalPendingOperationsByHost(memberUuid))
        .willReturn(0L);

    HostOperationSummaryResponse response =
        hostOperationSummaryService.findHostOperationSummaryByMemberUuid(memberUuid);

    assertThat(response.memberUuid()).isEqualTo(memberUuid);
    assertThat(response.totalPendingCount()).isZero();
    assertThat(response.generatedAt().getOffset().getId()).isEqualTo("+09:00");
  }

  @Test
  void findHostOperationSummaryByMemberUuidThrowsWhenMemberDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(hostOperationQueryRepository.existsByMemberUuid(memberUuid)).willReturn(false);

    assertThatThrownBy(
            () -> hostOperationSummaryService.findHostOperationSummaryByMemberUuid(memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);

    then(hostOperationQueryRepository)
        .should(never())
        .countTotalPendingOperationsByHost(memberUuid);
  }
}
