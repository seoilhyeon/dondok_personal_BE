package com.oit.dondok.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantRole;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.member.dto.response.MeCrewListResponse;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.exception.MemberErrorCode;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.global.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeCrewServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final Long DEPOSIT = 10_000L;

  @Mock private MemberRepository memberRepository;
  @Mock private CrewQueryRepository crewQueryRepository;
  @Mock private ImageDeliveryPort imageDeliveryPort;

  @InjectMocks private MeCrewService meCrewService;

  @Test
  void findMyCrewsReturnsItemsWithCorrectFields() {
    UUID memberUuid = UUID.randomUUID();
    Member host = buildMember(memberUuid);
    Crew crew = buildCrew(host);
    CrewParticipant participant = buildLockedParticipant(crew, host, 1L);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(host));
    given(crewQueryRepository.findMyCrewParticipants(eq(memberUuid), isNull(), eq(null), eq(20)))
        .willReturn(List.of(participant));
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willReturn(new ImageDeliveryUrl("https://cdn.example.com/crew.jpg", null));

    MeCrewListResponse result = meCrewService.findMyCrews(memberUuid, null, null, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).crewId()).isEqualTo(crew.getId());
    assertThat(result.items().get(0).myRole()).isEqualTo("HOST");
    assertThat(result.items().get(0).myStatus()).isEqualTo("LOCKED");
    assertThat(result.items().get(0).depositAmount()).isEqualTo(DEPOSIT);
    assertThat(result.items().get(0).imageUrl()).isEqualTo("https://cdn.example.com/crew.jpg");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void findMyCrewsWithHostRoleDelegatesRoleToRepository() {
    UUID memberUuid = UUID.randomUUID();
    Member host = buildMember(memberUuid);
    Crew crew = buildCrew(host);
    CrewParticipant participant = buildLockedParticipant(crew, host, 1L);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(host));
    given(
            crewQueryRepository.findMyCrewParticipants(
                eq(memberUuid), eq(CrewParticipantRole.HOST), eq(null), eq(20)))
        .willReturn(List.of(participant));
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willReturn(new ImageDeliveryUrl("https://cdn.example.com/crew.jpg", null));

    MeCrewListResponse result =
        meCrewService.findMyCrews(memberUuid, CrewParticipantRole.HOST, null, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).myRole()).isEqualTo("HOST");
  }

  @Test
  void findMyCrewsMemberRoleShowsMemberWhenParticipantIsNotHost() {
    UUID hostUuid = UUID.randomUUID();
    UUID memberUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Member member = buildMember(memberUuid);
    ReflectionTestUtils.setField(member, "id", 2L);
    Crew crew = buildCrew(host);
    CrewParticipant participant = buildLockedParticipant(crew, member, 2L);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(
            crewQueryRepository.findMyCrewParticipants(
                eq(memberUuid), eq(CrewParticipantRole.MEMBER), eq(null), eq(20)))
        .willReturn(List.of(participant));
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willReturn(new ImageDeliveryUrl("https://cdn.example.com/crew.jpg", null));

    MeCrewListResponse result =
        meCrewService.findMyCrews(memberUuid, CrewParticipantRole.MEMBER, null, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).myRole()).isEqualTo("MEMBER");
  }

  @Test
  void findMyCrewsReturnsEmptyItemsAndNullNextCursorWhenNoResults() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(crewQueryRepository.findMyCrewParticipants(eq(memberUuid), isNull(), eq(null), eq(20)))
        .willReturn(List.of());

    MeCrewListResponse result = meCrewService.findMyCrews(memberUuid, null, null, 20);

    assertThat(result.items()).isEmpty();
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void findMyCrewsReturnsNextCursorWhenMoreThanLimitResults() {
    UUID memberUuid = UUID.randomUUID();
    Member host = buildMember(memberUuid);
    Crew crew = buildCrew(host);
    CrewParticipant p1 = buildLockedParticipant(crew, host, 1L);
    CrewParticipant p2 = buildLockedParticipant(crew, host, 2L);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(host));
    given(crewQueryRepository.findMyCrewParticipants(eq(memberUuid), isNull(), eq(null), eq(1)))
        .willReturn(List.of(p1, p2));
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willReturn(new ImageDeliveryUrl("https://cdn.example.com/crew.jpg", null));

    MeCrewListResponse result = meCrewService.findMyCrews(memberUuid, null, null, 1);

    assertThat(result.items()).hasSize(1);
    assertThat(result.nextCursor()).isNotNull();
    String expectedCursor =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("1".getBytes(StandardCharsets.UTF_8));
    assertThat(result.nextCursor()).isEqualTo(expectedCursor);
  }

  @Test
  void findMyCrewsDecodesValidCursorAndPassesToRepository() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    String cursor =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("5".getBytes(StandardCharsets.UTF_8));

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(crewQueryRepository.findMyCrewParticipants(eq(memberUuid), isNull(), eq(5L), eq(20)))
        .willReturn(List.of());

    MeCrewListResponse result = meCrewService.findMyCrews(memberUuid, null, cursor, 20);

    assertThat(result.items()).isEmpty();
  }

  @Test
  void findMyCrewsThrowsInvalidCursorWhenCursorIsNotBase64Long() {
    UUID memberUuid = UUID.randomUUID();

    assertThatThrownBy(() -> meCrewService.findMyCrews(memberUuid, null, "!!invalid!!", 20))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.INVALID_CURSOR);
  }

  @Test
  void findMyCrewsThrowsMemberNotFoundWhenMemberDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.empty());

    assertThatThrownBy(() -> meCrewService.findMyCrews(memberUuid, null, null, 20))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
  }

  @Test
  void findMyCrewsReturnsNullImageUrlWhenCrewHasNoImageS3Key() {
    UUID memberUuid = UUID.randomUUID();
    Member host = buildMember(memberUuid);
    Crew crew = buildCrewWithoutImage(host);
    CrewParticipant participant = buildLockedParticipant(crew, host, 1L);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(host));
    given(crewQueryRepository.findMyCrewParticipants(eq(memberUuid), isNull(), eq(null), eq(20)))
        .willReturn(List.of(participant));

    MeCrewListResponse result = meCrewService.findMyCrews(memberUuid, null, null, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).imageUrl()).isNull();
  }

  // ======================== helpers ========================

  private Member buildMember(UUID uuid) {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", 1L);
    ReflectionTestUtils.setField(member, "uuid", uuid);
    return member;
  }

  private Crew buildCrew(Member hostMember) {
    Crew crew =
        Crew.create(
            hostMember,
            "테스트 크루",
            "크루 설명",
            "crew/image/key",
            "EXERCISE",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(SEOUL_ZONE),
            DEPOSIT,
            2,
            5,
            LocalDateTime.now(SEOUL_ZONE).plusDays(3),
            LocalDateTime.now(SEOUL_ZONE).plusDays(5),
            LocalDateTime.now(SEOUL_ZONE).plusDays(35));
    ReflectionTestUtils.setField(crew, "id", 1L);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private Crew buildCrewWithoutImage(Member hostMember) {
    Crew crew =
        Crew.create(
            hostMember,
            "이미지 없는 크루",
            "크루 설명",
            null,
            "EXERCISE",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(SEOUL_ZONE),
            DEPOSIT,
            2,
            5,
            LocalDateTime.now(SEOUL_ZONE).plusDays(3),
            LocalDateTime.now(SEOUL_ZONE).plusDays(5),
            LocalDateTime.now(SEOUL_ZONE).plusDays(35));
    ReflectionTestUtils.setField(crew, "id", 2L);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private CrewParticipant buildLockedParticipant(Crew crew, Member member, Long id) {
    CrewParticipant participant =
        CrewParticipant.create(crew, member, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));
    ReflectionTestUtils.setField(participant, "id", id);
    ReflectionTestUtils.setField(participant, "version", 0L);
    return participant;
  }
}
