package com.oit.dondok.domain.member.service;

import com.oit.dondok.domain.member.dto.response.HostOperationSummaryResponse;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.HostOperationQueryRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HostOperationSummaryService {

  private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final HostOperationQueryRepository hostOperationQueryRepository;

  public HostOperationSummaryResponse findHostOperationSummaryByMemberUuid(UUID memberUuid) {
    if (!hostOperationQueryRepository.existsByMemberUuid(memberUuid)) {
      throw new CustomException(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    long totalPendingCount =
        hostOperationQueryRepository.countTotalPendingOperationsByHost(memberUuid);
    Long hostCrewId = hostOperationQueryRepository.findDefaultHostCrewId(memberUuid).orElse(null);
    return new HostOperationSummaryResponse(
        memberUuid, totalPendingCount, hostCrewId, OffsetDateTime.now(SEOUL_ZONE_ID));
  }
}
