package com.oit.dondok.domain.member.service;

import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantRole;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.member.dto.response.MeCrewItemResponse;
import com.oit.dondok.domain.member.dto.response.MeCrewListResponse;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.util.CursorCodec;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MeCrewService {

  private static final int MAX_LIMIT = 100;
  private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(10);

  private final MemberRepository memberRepository;
  private final CrewQueryRepository crewQueryRepository;
  private final ImageDeliveryPort imageDeliveryPort;

  @Transactional(readOnly = true)
  public MeCrewListResponse findMyCrews(
      UUID memberUuid, CrewParticipantRole role, String cursor, int limit) {
    int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    Long cursorId = CursorCodec.decode(cursor);

    memberRepository
        .findByUuid(memberUuid)
        .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

    List<CrewParticipant> rows =
        crewQueryRepository.findMyCrewParticipants(memberUuid, role, cursorId, effectiveLimit);

    boolean hasNext = rows.size() > effectiveLimit;
    List<CrewParticipant> pageRows = hasNext ? rows.subList(0, effectiveLimit) : rows;

    List<MeCrewItemResponse> items =
        pageRows.stream()
            .map(
                p ->
                    MeCrewItemResponse.of(
                        p, memberUuid, resolveImageUrl(p.getCrew().getImageS3Key())))
            .toList();

    String nextCursor =
        hasNext ? CursorCodec.encode(pageRows.get(pageRows.size() - 1).getId()) : null;
    return new MeCrewListResponse(items, nextCursor);
  }

  private String resolveImageUrl(String imageS3Key) {
    if (!StringUtils.hasText(imageS3Key)) {
      return null;
    }
    return imageDeliveryPort.createDeliveryUrl(new ImageObjectKey(imageS3Key), IMAGE_URL_TTL).url();
  }
}
