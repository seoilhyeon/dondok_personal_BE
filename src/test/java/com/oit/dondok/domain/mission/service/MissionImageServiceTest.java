package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.mission.dto.response.ImageVerifyResponse;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.exception.MissionErrorCode;
import com.oit.dondok.domain.mission.port.ImageMetadata;
import com.oit.dondok.domain.mission.port.ImageMetadataPort;
import com.oit.dondok.domain.mission.repository.MissionLogRepository;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionImageServiceTest {

  private static final UUID MEMBER_UUID = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final Long CREW_ID = 42L;
  private static final Long PARTICIPANT_ID = 101L;
  private static final String MISSION_IMAGE_OBJECT_KEY_FIXTURE =
      "mission/42/101/test-image-object-key";
  private static final ZoneOffset KST = ZoneOffset.ofHours(9);

  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private ImageMetadataPort imageMetadataPort;
  @Mock private MissionLogRepository missionLogRepository;
  @Mock private MissionRuleRepository missionRuleRepository;

  @InjectMocks private MissionImageService missionImageService;

  // 소유권: 회원이 해당 크루의 participant를 소유하면 그대로 반환한다.
  @Test
  void getOwnedParticipantReturnsWhenMemberOwnsParticipantInCrew() {
    CrewParticipant participant = participantOf(MEMBER_UUID, CREW_ID);
    given(crewParticipantRepository.findById(PARTICIPANT_ID)).willReturn(Optional.of(participant));

    CrewParticipant result =
        missionImageService.getOwnedParticipant(MEMBER_UUID, CREW_ID, PARTICIPANT_ID);

    assertThat(result).isSameAs(participant);
  }

  // 소유권: participant가 없으면 PARTICIPANT_NOT_FOUND.
  @Test
  void getOwnedParticipantThrowsWhenParticipantNotFound() {
    given(crewParticipantRepository.findById(PARTICIPANT_ID)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> missionImageService.getOwnedParticipant(MEMBER_UUID, CREW_ID, PARTICIPANT_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  // 소유권: 다른 회원이 요청하면 존재를 숨겨 PARTICIPANT_NOT_FOUND.
  @Test
  void getOwnedParticipantThrowsWhenRequestedByDifferentMember() {
    CrewParticipant participant = participantOf(UUID.randomUUID(), CREW_ID);
    given(crewParticipantRepository.findById(PARTICIPANT_ID)).willReturn(Optional.of(participant));

    assertThatThrownBy(
            () -> missionImageService.getOwnedParticipant(MEMBER_UUID, CREW_ID, PARTICIPANT_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  // 소유권: participant가 다른 크루 소속이면 PARTICIPANT_NOT_FOUND.
  @Test
  void getOwnedParticipantThrowsWhenParticipantBelongsToDifferentCrew() {
    CrewParticipant participant = participantOf(MEMBER_UUID, 999L);
    given(crewParticipantRepository.findById(PARTICIPANT_ID)).willReturn(Optional.of(participant));

    assertThatThrownBy(
            () -> missionImageService.getOwnedParticipant(MEMBER_UUID, CREW_ID, PARTICIPANT_ID))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  // EXIF 시각이 없으면 ExifRisk.MISSING.
  @Test
  void getImageVerifyResponseReturnsMissingWhenNoExif() {
    givenExtracted(null, "hash-1");
    givenMissionRule(DailySettlementType.A);
    givenDuplicate(false);

    ImageVerifyResponse result =
        missionImageService.getImageVerifyResponse(
            CREW_ID, MISSION_IMAGE_OBJECT_KEY_FIXTURE, kst(6, 2, 8, 30));

    assertThat(result.exifRisk()).isEqualTo(ExifRisk.MISSING);
    assertThat(result.takenAt()).isNull();
    assertThat(result.imageHash()).isEqualTo("hash-1");
    assertThat(result.duplicate()).isFalse();
  }

  // 촬영 시각이 [당일 00:00, 마감] 안이고 server_time 이전이면 ExifRisk.NORMAL.
  @Test
  void getImageVerifyResponseReturnsNormalWithinWindow() {
    givenExtracted(kst(6, 2, 8, 0), "hash-1");
    givenMissionRule(DailySettlementType.A); // 인증마감 09:00
    givenDuplicate(false);

    ImageVerifyResponse result =
        missionImageService.getImageVerifyResponse(
            CREW_ID, MISSION_IMAGE_OBJECT_KEY_FIXTURE, kst(6, 2, 8, 30));

    assertThat(result.exifRisk()).isEqualTo(ExifRisk.NORMAL);
  }

  // 촬영 시각이 인증 당일 00:00 이전(전날)이면 ExifRisk.TIME_INVALID.
  @Test
  void getImageVerifyResponseReturnsTimeInvalidWhenTakenBeforeMissionDay() {
    givenExtracted(kst(6, 1, 23, 0), "hash-1");
    givenMissionRule(DailySettlementType.A);
    givenDuplicate(false);

    ImageVerifyResponse result =
        missionImageService.getImageVerifyResponse(
            CREW_ID, MISSION_IMAGE_OBJECT_KEY_FIXTURE, kst(6, 2, 8, 30));

    assertThat(result.exifRisk()).isEqualTo(ExifRisk.TIME_INVALID);
  }

  // 촬영 시각이 인증마감 이후면 ExifRisk.TIME_INVALID (지각 제출 케이스).
  @Test
  void getImageVerifyResponseReturnsTimeInvalidWhenTakenAfterDeadline() {
    givenExtracted(kst(6, 2, 9, 30), "hash-1"); // 마감 09:00 이후
    givenMissionRule(DailySettlementType.A);
    givenDuplicate(false);

    ImageVerifyResponse result =
        missionImageService.getImageVerifyResponse(
            CREW_ID, MISSION_IMAGE_OBJECT_KEY_FIXTURE, kst(6, 2, 10, 0));

    assertThat(result.exifRisk()).isEqualTo(ExifRisk.TIME_INVALID);
  }

  // 촬영 시각이 server_time(제출 시각) 이후면 ExifRisk.TIME_INVALID (물리적으로 불가능).
  @Test
  void getImageVerifyResponseReturnsTimeInvalidWhenTakenAfterServerTime() {
    givenExtracted(kst(6, 2, 8, 30), "hash-1"); // server_time 이후, 마감 이전
    givenMissionRule(DailySettlementType.A);
    givenDuplicate(false);

    ImageVerifyResponse result =
        missionImageService.getImageVerifyResponse(
            CREW_ID, MISSION_IMAGE_OBJECT_KEY_FIXTURE, kst(6, 2, 8, 0));

    assertThat(result.exifRisk()).isEqualTo(ExifRisk.TIME_INVALID);
  }

  // 같은 크루에 동일 해시가 있으면 duplicate=true.
  @Test
  void getImageVerifyResponseFlagsDuplicateWhenHashExistsInCrew() {
    givenExtracted(kst(6, 2, 8, 0), "hash-1");
    givenMissionRule(DailySettlementType.A);
    givenDuplicate(true);

    ImageVerifyResponse result =
        missionImageService.getImageVerifyResponse(
            CREW_ID, MISSION_IMAGE_OBJECT_KEY_FIXTURE, kst(6, 2, 8, 30));

    assertThat(result.duplicate()).isTrue();
  }

  // 크루의 미션 규칙이 없으면 MISSION_RULE_NOT_FOUND.
  @Test
  void getImageVerifyResponseThrowsWhenMissionRuleNotFound() {
    givenExtracted(kst(6, 2, 8, 0), "hash-1");
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                missionImageService.getImageVerifyResponse(
                    CREW_ID, MISSION_IMAGE_OBJECT_KEY_FIXTURE, kst(6, 2, 8, 30)))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MissionErrorCode.MISSION_RULE_NOT_FOUND);
  }

  private void givenExtracted(OffsetDateTime takenAt, String hash) {
    given(imageMetadataPort.extract(anyString())).willReturn(new ImageMetadata(takenAt, hash));
  }

  private void givenMissionRule(DailySettlementType type) {
    MissionRule rule = mock(MissionRule.class);
    given(rule.getDailySettlementType()).willReturn(type);
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(rule));
  }

  private void givenDuplicate(boolean duplicate) {
    given(missionLogRepository.existsByCrewParticipantCrewIdAndImageHash(anyLong(), anyString()))
        .willReturn(duplicate);
  }

  private static OffsetDateTime kst(int month, int day, int hour, int minute) {
    return OffsetDateTime.of(2026, month, day, hour, minute, 0, 0, KST);
  }

  private static CrewParticipant participantOf(UUID memberUuid, Long crewId) {
    CrewParticipant participant = mock(CrewParticipant.class);
    Member member = mock(Member.class);
    Crew crew = mock(Crew.class);
    given(participant.getMember()).willReturn(member);
    given(member.getUuid()).willReturn(memberUuid);
    given(participant.getCrew()).willReturn(crew);
    given(crew.getId()).willReturn(crewId);
    return participant;
  }
}
