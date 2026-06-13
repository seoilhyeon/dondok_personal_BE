package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.dto.request.CrewCreateRequest;
import com.oit.dondok.domain.crew.dto.request.HostAgreementRequest;
import com.oit.dondok.domain.crew.dto.response.ApplicationListResponse;
import com.oit.dondok.domain.crew.dto.response.CrewCreateResponse;
import com.oit.dondok.domain.crew.dto.response.CrewDetailResponse;
import com.oit.dondok.domain.crew.dto.response.CrewListResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationApplyResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationApproveResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationCancelResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationCountResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationRejectResponse;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.port.CrewPointPort;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewQueryRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.domain.mission.entity.DailySettlementType;
import com.oit.dondok.domain.mission.entity.MissionFrequencyType;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.MissionScheduleDay;
import com.oit.dondok.domain.mission.repository.MissionRuleRepository;
import com.oit.dondok.domain.mission.repository.MissionScheduleDayRepository;
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementStatus;
import com.oit.dondok.domain.settlement.repository.SettlementRepository;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import com.oit.dondok.infra.image.adapter.DefaultImageObjectKeyPolicy;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CrewServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final Long CREW_ID = 1L;
  private static final Long DEPOSIT = 10_000L;

  @Mock private CrewRepository crewRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MissionRuleRepository missionRuleRepository;
  @Mock private MissionScheduleDayRepository missionScheduleDayRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private CrewPointPort crewPointPort;
  @Mock private CrewQueryRepository crewQueryRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private ImageDeliveryPort imageDeliveryPort;
  @Spy private DefaultImageObjectKeyPolicy keyPolicy = new DefaultImageObjectKeyPolicy();
  @Mock private NotificationSender notificationSender;

  @InjectMocks private CrewService crewService;

  // ======================== createCrew ========================

  @Test
  void createCrewWithDailyFrequencyTypeCreatesCrewAndReturnsResponse() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    CrewCreateRequest request = buildCrewCreateRequest(MissionFrequencyType.DAILY, null);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);
    CrewParticipant participant = buildLockedParticipant(crew, member);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(objectMapper.writeValueAsString(any())).willReturn("{}");
    given(crewRepository.save(any())).willReturn(crew);
    given(missionRuleRepository.save(any())).willReturn(missionRule);
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    CrewCreateResponse response = crewService.createCrew(memberUuid, request);

    assertThat(response.frequencyType()).isEqualTo(MissionFrequencyType.DAILY);
    assertThat(response.missionScheduleDays()).isEmpty();
    then(crewPointPort).should().lockForHostParticipant(participant);
    then(missionScheduleDayRepository).shouldHaveNoInteractions();
  }

  @Test
  void createCrewWithSpecificDaysFrequencyTypeSavesScheduleDays() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    List<String> days = List.of("MONDAY", "WEDNESDAY");
    CrewCreateRequest request = buildCrewCreateRequest(MissionFrequencyType.SPECIFIC_DAYS, days);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.SPECIFIC_DAYS);
    CrewParticipant participant = buildLockedParticipant(crew, member);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(objectMapper.writeValueAsString(any())).willReturn("{}");
    given(crewRepository.save(any())).willReturn(crew);
    given(missionRuleRepository.save(any())).willReturn(missionRule);
    given(missionScheduleDayRepository.save(any())).willReturn(null);
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    CrewCreateResponse response = crewService.createCrew(memberUuid, request);

    assertThat(response.frequencyType()).isEqualTo(MissionFrequencyType.SPECIFIC_DAYS);
    assertThat(response.missionScheduleDays()).containsExactlyElementsOf(days);
    then(missionScheduleDayRepository).should(times(2)).save(any());
  }

  @Test
  void createCrewThrowsValidationErrorWhenMinParticipantsExceedsMax() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    CrewCreateRequest request =
        new CrewCreateRequest(
            "Test Crew",
            "설명",
            null,
            "EXERCISE",
            DEPOSIT,
            5, // min=5 > max=3
            3,
            MissionFrequencyType.DAILY,
            null,
            DailySettlementType.A,
            buildHostAgreementRequest(),
            OffsetDateTime.now(SEOUL_ZONE).plusDays(1),
            LocalDate.now(SEOUL_ZONE).plusDays(5),
            LocalDate.now(SEOUL_ZONE).plusDays(30));

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> crewService.createCrew(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.VALIDATION_ERROR);
  }

  @Test
  void createCrewThrowsValidationErrorWhenStartDateIsNotBeforeEndDate() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    LocalDate start = LocalDate.now(SEOUL_ZONE).plusDays(10);
    LocalDate end = LocalDate.now(SEOUL_ZONE).plusDays(5); // end before start
    CrewCreateRequest request =
        new CrewCreateRequest(
            "Test Crew",
            "설명",
            null,
            "EXERCISE",
            DEPOSIT,
            2,
            5,
            MissionFrequencyType.DAILY,
            null,
            DailySettlementType.A,
            buildHostAgreementRequest(),
            OffsetDateTime.now(SEOUL_ZONE).plusDays(1),
            start,
            end);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> crewService.createCrew(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.VALIDATION_ERROR);
  }

  @Test
  void createCrewThrowsInvalidCategoryWhenCategoryIsUnknown() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    CrewCreateRequest request =
        new CrewCreateRequest(
            "Test Crew",
            "설명",
            null,
            "INVALID_CAT",
            DEPOSIT,
            2,
            5,
            MissionFrequencyType.DAILY,
            null,
            DailySettlementType.A,
            buildHostAgreementRequest(),
            OffsetDateTime.now(SEOUL_ZONE).plusDays(1),
            LocalDate.now(SEOUL_ZONE).plusDays(5),
            LocalDate.now(SEOUL_ZONE).plusDays(30));

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> crewService.createCrew(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.INVALID_CATEGORY);
  }

  @Test
  void createCrewThrowsInvalidFrequencyRuleWhenSpecificDaysHasNoScheduleDays() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    CrewCreateRequest request =
        new CrewCreateRequest(
            "Test Crew",
            "설명",
            null,
            "EXERCISE",
            DEPOSIT,
            2,
            5,
            MissionFrequencyType.SPECIFIC_DAYS,
            null, // null schedule days
            DailySettlementType.A,
            buildHostAgreementRequest(),
            OffsetDateTime.now(SEOUL_ZONE).plusDays(1),
            LocalDate.now(SEOUL_ZONE).plusDays(5),
            LocalDate.now(SEOUL_ZONE).plusDays(30));

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> crewService.createCrew(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.INVALID_FREQUENCY_RULE);
  }

  @Test
  void createCrewResolvesImageUrlFromS3KeyViaDeliveryPort() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    CrewCreateRequest request = buildCrewCreateRequest(MissionFrequencyType.DAILY, null);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    String s3Key = "crew/" + memberUuid + "/file";
    ReflectionTestUtils.setField(crew, "imageS3Key", s3Key);
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);
    CrewParticipant participant = buildLockedParticipant(crew, member);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(objectMapper.writeValueAsString(any())).willReturn("{}");
    given(crewRepository.save(any())).willReturn(crew);
    given(missionRuleRepository.save(any())).willReturn(missionRule);
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willReturn(
            new ImageDeliveryUrl(
                "https://cdn.example.com/crew/img",
                OffsetDateTime.now(SEOUL_ZONE).plusMinutes(10)));

    CrewCreateResponse response = crewService.createCrew(memberUuid, request);

    // image_url은 저장값이 아니라 s3 key에서 ImageDeliveryPort로 파생된다.
    assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/crew/img");
    then(imageDeliveryPort)
        .should()
        .createDeliveryUrl(new ImageObjectKey(s3Key), Duration.ofMinutes(10));
  }

  @Test
  void createCrewReturnsNullImageUrlWhenNoS3Key() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    CrewCreateRequest request = buildCrewCreateRequest(MissionFrequencyType.DAILY, null);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3)); // imageS3Key=null
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);
    CrewParticipant participant = buildLockedParticipant(crew, member);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(objectMapper.writeValueAsString(any())).willReturn("{}");
    given(crewRepository.save(any())).willReturn(crew);
    given(missionRuleRepository.save(any())).willReturn(missionRule);
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    CrewCreateResponse response = crewService.createCrew(memberUuid, request);

    // key가 없으면 포트를 호출하지 않고 null을 반환한다.
    assertThat(response.imageUrl()).isNull();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void createCrewRejectsImageS3KeyOutsideOwnCrewNamespace() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    // 타 네임스페이스(profile) key 제출 → 거부
    CrewCreateRequest request =
        buildCrewCreateRequestWithImageKey("profile/" + memberUuid + "/" + UUID.randomUUID());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> crewService.createCrew(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
  }

  @Test
  void createCrewRejectsOtherMembersCrewImageKey() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    // 같은 crew 네임스페이스지만 타인 소유 → 거부
    CrewCreateRequest request =
        buildCrewCreateRequestWithImageKey("crew/" + UUID.randomUUID() + "/" + UUID.randomUUID());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> crewService.createCrew(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
  }

  @Test
  void createCrewRejectsMissionNamespaceImageKey() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    // mission 네임스페이스 key는 crew 이미지로 허용되지 않는다.
    CrewCreateRequest request =
        buildCrewCreateRequestWithImageKey("mission/1/2/" + UUID.randomUUID());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));

    assertThatThrownBy(() -> crewService.createCrew(memberUuid, request))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
  }

  @Test
  void createCrewAcceptsOwnedCrewImageKey() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    String s3Key = "crew/" + memberUuid + "/" + UUID.randomUUID();
    CrewCreateRequest request = buildCrewCreateRequestWithImageKey(s3Key);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    ReflectionTestUtils.setField(crew, "imageS3Key", s3Key);
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);
    CrewParticipant participant = buildLockedParticipant(crew, member);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(objectMapper.writeValueAsString(any())).willReturn("{}");
    given(crewRepository.save(any())).willReturn(crew);
    given(missionRuleRepository.save(any())).willReturn(missionRule);
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willReturn(
            new ImageDeliveryUrl(
                "https://cdn.example.com/crew/img",
                OffsetDateTime.now(SEOUL_ZONE).plusMinutes(10)));

    CrewCreateResponse response = crewService.createCrew(memberUuid, request);

    assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/crew/img");
  }

  // ======================== findCrewList ========================

  @Test
  void findCrewListWithNoFilterReturnsEmptyCrewListResponse() {
    given(crewQueryRepository.findCrewsWithRule(any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());
    given(crewQueryRepository.findScheduleDaysByRuleIds(any())).willReturn(Map.of());

    CrewListResponse response =
        crewService.findCrewList(CrewStatus.RECRUITING, null, null, null, 20);

    assertThat(response.items()).isEmpty();
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void findCrewListNormalizesLowercaseCategoryToUppercase() {
    given(crewQueryRepository.findCrewsWithRule(any(), eq("EXERCISE"), any(), any(), anyInt()))
        .willReturn(List.of());
    given(crewQueryRepository.findScheduleDaysByRuleIds(any())).willReturn(Map.of());

    crewService.findCrewList(CrewStatus.RECRUITING, "exercise", null, null, 20);

    then(crewQueryRepository)
        .should()
        .findCrewsWithRule(eq(CrewStatus.RECRUITING), eq("EXERCISE"), any(), any(), anyInt());
  }

  @Test
  void findCrewListThrowsInvalidCategoryWhenCategoryIsUnknown() {
    assertThatThrownBy(
            () -> crewService.findCrewList(CrewStatus.RECRUITING, "INVALID_CAT", null, null, 20))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.INVALID_CATEGORY);
  }

  @Test
  void findCrewListResolvesImageUrlFromS3KeyViaDeliveryPort() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    String s3Key = "crew/" + memberUuid + "/file";
    ReflectionTestUtils.setField(crew, "imageS3Key", s3Key);
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);

    given(crewQueryRepository.findCrewsWithRule(any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(new CrewQueryRepository.CrewWithRule(crew, missionRule)));
    given(crewQueryRepository.findScheduleDaysByRuleIds(any())).willReturn(Map.of());
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willReturn(
            new ImageDeliveryUrl(
                "https://cdn.example.com/crew/img",
                OffsetDateTime.now(SEOUL_ZONE).plusMinutes(10)));

    CrewListResponse response =
        crewService.findCrewList(CrewStatus.RECRUITING, null, null, null, 20);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).imageUrl()).isEqualTo("https://cdn.example.com/crew/img");
    then(imageDeliveryPort)
        .should()
        .createDeliveryUrl(new ImageObjectKey(s3Key), Duration.ofMinutes(10));
  }

  @Test
  void findCrewListReturnsNullImageUrlWhenNoS3Key() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3)); // imageS3Key=null
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);

    given(crewQueryRepository.findCrewsWithRule(any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(new CrewQueryRepository.CrewWithRule(crew, missionRule)));
    given(crewQueryRepository.findScheduleDaysByRuleIds(any())).willReturn(Map.of());

    CrewListResponse response =
        crewService.findCrewList(CrewStatus.RECRUITING, null, null, null, 20);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).imageUrl()).isNull();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  // ======================== findCrewDetail ========================

  @Test
  void findCrewDetailReturnsDetailResponseWithMyParticipation() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    Settlement settlement = mock(Settlement.class);
    given(settlement.getStatus()).willReturn(SettlementStatus.PENDING);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(settlement));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));

    CrewDetailResponse response = crewService.findCrewDetail(CREW_ID, memberUuid);

    assertThat(response.myParticipation()).isNotNull();
    assertThat(response.myParticipation().status()).isEqualTo(CrewParticipantStatus.LOCKED);
    assertThat(response.settlementStatus()).isEqualTo("PENDING");
  }

  @Test
  void findCrewDetailReturnsNullMyParticipationWhenNotParticipating() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    CrewDetailResponse response = crewService.findCrewDetail(CREW_ID, memberUuid);

    assertThat(response.myParticipation()).isNull();
  }

  @Test
  void findCrewDetailReturnsNoneSettlementStatusWhenSettlementDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    CrewDetailResponse response = crewService.findCrewDetail(CREW_ID, memberUuid);

    assertThat(response.settlementStatus()).isEqualTo("NONE");
  }

  @Test
  void findCrewDetailThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> crewService.findCrewDetail(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  @Test
  void findCrewDetailResolvesImageUrlFromS3KeyViaDeliveryPort() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    String s3Key = "crew/" + memberUuid + "/file";
    ReflectionTestUtils.setField(crew, "imageS3Key", s3Key);
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willReturn(
            new ImageDeliveryUrl(
                "https://cdn.example.com/crew/img",
                OffsetDateTime.now(SEOUL_ZONE).plusMinutes(10)));

    CrewDetailResponse response = crewService.findCrewDetail(CREW_ID, memberUuid);

    // image_url은 저장값이 아니라 s3 key에서 ImageDeliveryPort로 파생된다.
    assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/crew/img");
    then(imageDeliveryPort)
        .should()
        .createDeliveryUrl(new ImageObjectKey(s3Key), Duration.ofMinutes(10));
  }

  @Test
  void findCrewDetailReturnsNullImageUrlWhenNoS3Key() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3)); // imageS3Key=null
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    CrewDetailResponse response = crewService.findCrewDetail(CREW_ID, memberUuid);

    // key가 없으면 포트를 호출하지 않고 null을 반환한다.
    assertThat(response.imageUrl()).isNull();
    then(imageDeliveryPort).shouldHaveNoInteractions();
  }

  @Test
  void findCrewDetailPropagatesWhenImageDeliveryFails() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    ReflectionTestUtils.setField(crew, "imageS3Key", "crew/" + memberUuid + "/file");
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.DAILY);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(missionRuleRepository.findByCrewId(CREW_ID)).willReturn(Optional.of(missionRule));
    given(settlementRepository.findByCrewId(CREW_ID)).willReturn(Optional.empty());
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any(Duration.class)))
        .willThrow(new CustomException(GlobalErrorCode.SERVER_ERROR));

    // 표시 URL 발급 실패는 격리하지 않고 전파한다(profile 경로와 동일).
    assertThatThrownBy(() -> crewService.findCrewDetail(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.SERVER_ERROR);
  }

  // ======================== applyParticipation ========================

  @Test
  void applyParticipationCreatesNewPendingParticipantWhenNoExistingRecord() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, member, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));
    ReflectionTestUtils.setField(participant, "id", 1L);

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(crewParticipantRepository.countByCrewIdAndStatusIn(eq(CREW_ID), any())).willReturn(0L);
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    ParticipationApplyResponse response = crewService.applyParticipation(CREW_ID, memberUuid);

    assertThat(response.status()).isEqualTo(CrewParticipantStatus.PENDING);
    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.memberUuid()).isEqualTo(memberUuid);
    then(crewPointPort).should().reserveForPendingParticipant(participant);
  }

  @Test
  void applyParticipationThrowsAlreadyParticipatingWhenStatusIsPending() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant existing =
        CrewParticipant.createPending(crew, member, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(existing));

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.ALREADY_PARTICIPATING);
  }

  @Test
  void applyParticipationThrowsAlreadyParticipatingWhenStatusIsLocked() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant existing = buildLockedParticipant(crew, member);

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(existing));

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.ALREADY_PARTICIPATING);
  }

  @Test
  void applyParticipationThrowsApplicationNotAllowedWhenStatusIsRejected() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant existing =
        buildParticipantWithStatus(crew, member, CrewParticipantStatus.REJECTED);

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(existing));

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.APPLICATION_NOT_ALLOWED);
  }

  @Test
  void applyParticipationThrowsApplicationNotAllowedWhenStatusIsExpired() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant existing =
        buildParticipantWithStatus(crew, member, CrewParticipantStatus.EXPIRED);

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(existing));

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.APPLICATION_NOT_ALLOWED);
  }

  @Test
  void applyParticipationReopensCancelledParticipantAndPreservesCancelledAt() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    LocalDateTime originalCancelledAt = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
    CrewParticipant existing =
        CrewParticipant.createPending(
            crew, member, DEPOSIT, LocalDateTime.of(2026, 4, 30, 9, 0, 0));
    existing.cancel(originalCancelledAt);
    ReflectionTestUtils.setField(existing, "id", 1L);
    ReflectionTestUtils.setField(existing, "version", 0L);

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(existing));
    given(crewParticipantRepository.countByCrewIdAndStatusIn(eq(CREW_ID), any())).willReturn(0L);
    given(crewParticipantRepository.saveAndFlush(existing)).willReturn(existing);

    crewService.applyParticipation(CREW_ID, memberUuid);

    assertThat(existing.getStatus()).isEqualTo(CrewParticipantStatus.PENDING);
    assertThat(existing.getCancelledAt())
        .as("cancelledAt must be preserved as audit trail after reopen")
        .isEqualTo(originalCancelledAt);
    then(crewParticipantRepository).should().saveAndFlush(existing); // same row, not new
    then(crewPointPort).should().reserveForPendingParticipant(existing);
    then(memberRepository).shouldHaveNoInteractions(); // reopen does not need Member entity
  }

  @Test
  void applyParticipationThrowsCrewNotRecruitingWhenCrewStatusIsNotRecruiting() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    ReflectionTestUtils.setField(crew, "status", CrewStatus.ACTIVE);

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_RECRUITING);
  }

  @Test
  void applyParticipationThrowsCrewNotRecruitingWhenRecruitmentDeadlineHasPassed() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).minusDays(1));

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_RECRUITING);
  }

  @Test
  void applyParticipationThrowsCapacityFullWhenPendingAndLockedCountReachesMax() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 3, LocalDateTime.now(SEOUL_ZONE).plusDays(3)); // max=3

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(crewParticipantRepository.countByCrewIdAndStatusIn(eq(CREW_ID), any())).willReturn(3L);

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CAPACITY_FULL);
  }

  @Test
  void applyParticipationThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  // ======================== cancelParticipation ========================

  @Test
  void cancelParticipationCancelsParticipantAndSetsCancelledAt() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, member, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    ParticipationCancelResponse response = crewService.cancelParticipation(CREW_ID, memberUuid);

    assertThat(participant.getStatus()).isEqualTo(CrewParticipantStatus.CANCELLED);
    assertThat(participant.getCancelledAt()).isNotNull();
    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.status()).isEqualTo(CrewParticipantStatus.CANCELLED);
    assertThat(response.cancelledAt()).isNotNull();
  }

  @Test
  void cancelParticipationCallsReleasePendingReserve() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, member, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    crewService.cancelParticipation(CREW_ID, memberUuid);

    then(crewPointPort).should().releasePendingReserve(participant);
  }

  @Test
  void cancelParticipationThrowsParticipantNotFoundWhenNoParticipationRecord() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> crewService.cancelParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  @Test
  void cancelParticipationThrowsApplicationNotCancellableWhenStatusIsNotPending() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant = buildLockedParticipant(crew, member); // LOCKED

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));

    assertThatThrownBy(() -> crewService.cancelParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.APPLICATION_NOT_CANCELLABLE);
  }

  @Test
  void cancelParticipationThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> crewService.cancelParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  // ======================== applyParticipation 동시성 예외 매핑 ========================

  @Test
  void applyParticipationThrowsAlreadyParticipatingWhenUniqueConstraintViolated() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(crewParticipantRepository.countByCrewIdAndStatusIn(eq(CREW_ID), any())).willReturn(0L);
    given(crewParticipantRepository.saveAndFlush(any()))
        .willThrow(new DataIntegrityViolationException("uk violation"));

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.ALREADY_PARTICIPATING);
  }

  @Test
  void applyParticipationThrowsConcurrentPaymentErrorWhenOptimisticLockFails() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());
    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(crewParticipantRepository.countByCrewIdAndStatusIn(eq(CREW_ID), any())).willReturn(0L);
    given(crewParticipantRepository.saveAndFlush(any()))
        .willThrow(new OptimisticLockingFailureException("optimistic lock failure") {});

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CONCURRENT_PAYMENT_ERROR);
  }

  @Test
  void applyParticipationReopenThrowsConcurrentPaymentErrorWhenOptimisticLockFails() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant existing =
        CrewParticipant.createPending(
            crew, member, DEPOSIT, LocalDateTime.of(2026, 4, 30, 9, 0, 0));
    existing.cancel(LocalDateTime.of(2026, 5, 1, 10, 0, 0));
    ReflectionTestUtils.setField(existing, "id", 1L);
    ReflectionTestUtils.setField(existing, "version", 0L);

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(existing));
    given(crewParticipantRepository.countByCrewIdAndStatusIn(eq(CREW_ID), any())).willReturn(0L);
    given(crewParticipantRepository.saveAndFlush(any()))
        .willThrow(new OptimisticLockingFailureException("optimistic lock failure") {});

    assertThatThrownBy(() -> crewService.applyParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CONCURRENT_PAYMENT_ERROR);
  }

  // ======================== cancelParticipation 동시성 예외 매핑 ========================

  @Test
  void cancelParticipationThrowsConcurrentPaymentErrorWhenOptimisticLockFails() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, member, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any()))
        .willThrow(new OptimisticLockingFailureException("optimistic lock failure") {});

    assertThatThrownBy(() -> crewService.cancelParticipation(CREW_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CONCURRENT_PAYMENT_ERROR);
  }

  // ======================== approveParticipation 가입 신청 승인 ========================

  @Test
  void approveParticipationApprovesParticipantAndSetsLockedAt() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, host, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    ParticipationApproveResponse response = crewService.approveParticipation(CREW_ID, 1L, hostUuid);

    assertThat(participant.getStatus()).isEqualTo(CrewParticipantStatus.LOCKED);
    assertThat(participant.getLockedAt()).isNotNull();
    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.status()).isEqualTo(CrewParticipantStatus.LOCKED);
    then(crewPointPort).should().lockForApprovedParticipant(participant);
  }

  @Test
  void approveParticipationThrowsForbiddenNotHostWhenRequesterIsNotHost() {
    UUID hostUuid = UUID.randomUUID();
    UUID otherUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, otherUuid)).willReturn(false);
    given(crewRepository.existsById(CREW_ID)).willReturn(true);

    assertThatThrownBy(() -> crewService.approveParticipation(CREW_ID, 1L, otherUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.FORBIDDEN_NOT_HOST);
  }

  @Test
  void approveParticipationThrowsApplicationNotApprovableWhenStatusIsNotPending() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant = buildLockedParticipant(crew, host); // 이미 LOCKED

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.of(participant));

    assertThatThrownBy(() -> crewService.approveParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.APPLICATION_NOT_APPROVABLE);
  }

  @Test
  void approveParticipationThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID hostUuid = UUID.randomUUID();
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(false);
    given(crewRepository.existsById(CREW_ID)).willReturn(false);

    assertThatThrownBy(() -> crewService.approveParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  @Test
  void approveParticipationThrowsParticipantNotFoundWhenParticipantDoesNotExist() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> crewService.approveParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  @Test
  void approveParticipationThrowsConcurrentPaymentErrorWhenOptimisticLockFails() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, host, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any()))
        .willThrow(new OptimisticLockingFailureException("lock fail") {});

    assertThatThrownBy(() -> crewService.approveParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CONCURRENT_PAYMENT_ERROR);
  }

  // ======================== rejectParticipation 가입 신청 거절 ========================

  @Test
  void rejectParticipationRejectsParticipantAndSetsRejectedAt() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, host, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    ParticipationRejectResponse response = crewService.rejectParticipation(CREW_ID, 1L, hostUuid);

    assertThat(participant.getStatus()).isEqualTo(CrewParticipantStatus.REJECTED);
    assertThat(participant.getRejectedAt()).isNotNull();
    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.status()).isEqualTo(CrewParticipantStatus.REJECTED);
    then(crewPointPort).should().releasePendingReserve(participant);
  }

  @Test
  void rejectParticipationThrowsForbiddenNotHostWhenRequesterIsNotHost() {
    UUID hostUuid = UUID.randomUUID();
    UUID otherUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, otherUuid)).willReturn(false);
    given(crewRepository.existsById(CREW_ID)).willReturn(true);

    assertThatThrownBy(() -> crewService.rejectParticipation(CREW_ID, 1L, otherUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.FORBIDDEN_NOT_HOST);
  }

  @Test
  void rejectParticipationThrowsApplicationNotRejectableWhenStatusIsNotPending() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant = buildLockedParticipant(crew, host); // 이미 LOCKED

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.of(participant));

    assertThatThrownBy(() -> crewService.rejectParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.APPLICATION_NOT_REJECTABLE);
  }

  @Test
  void approveParticipationThrowsParticipantNotInCrewWhenParticipantBelongsToDifferentCrew() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    // participant belongs to a different crew (id=99)
    Crew otherCrew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    ReflectionTestUtils.setField(otherCrew, "id", 99L);
    CrewParticipant participant =
        CrewParticipant.createPending(
            otherCrew, host, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.of(participant));

    assertThatThrownBy(() -> crewService.approveParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_IN_CREW);
  }

  @Test
  void rejectParticipationThrowsParticipantNotInCrewWhenParticipantBelongsToDifferentCrew() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    Crew otherCrew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    ReflectionTestUtils.setField(otherCrew, "id", 99L);
    CrewParticipant participant =
        CrewParticipant.createPending(
            otherCrew, host, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.of(participant));

    assertThatThrownBy(() -> crewService.rejectParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_IN_CREW);
  }

  @Test
  void rejectParticipationThrowsParticipantNotFoundWhenParticipantDoesNotExist() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> crewService.rejectParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.PARTICIPANT_NOT_FOUND);
  }

  @Test
  void rejectParticipationThrowsConcurrentPaymentErrorWhenOptimisticLockFails() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, host, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(1L)).willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any()))
        .willThrow(new OptimisticLockingFailureException("lock fail") {});

    assertThatThrownBy(() -> crewService.rejectParticipation(CREW_ID, 1L, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CONCURRENT_PAYMENT_ERROR);
  }

  // ======================== getParticipationList 가입 신청 목록 조회 ========================

  @Test
  void getParticipationListReturnsPendingParticipants() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, host, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 1L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(CREW_ID), eq(CrewParticipantStatus.PENDING), eq(0L), any()))
        .willReturn(List.of(participant));

    ApplicationListResponse result =
        crewService.getParticipationList(
            CREW_ID, CrewParticipantStatus.PENDING, hostUuid, null, 50);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).status()).isEqualTo(CrewParticipantStatus.PENDING);
  }

  @Test
  void getParticipationListThrowsForbiddenNotHostWhenRequesterIsNotHost() {
    UUID hostUuid = UUID.randomUUID();
    UUID otherUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, otherUuid)).willReturn(false);
    given(crewRepository.existsById(CREW_ID)).willReturn(true);

    assertThatThrownBy(
            () ->
                crewService.getParticipationList(
                    CREW_ID, CrewParticipantStatus.PENDING, otherUuid, null, 50))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.FORBIDDEN_NOT_HOST);
  }

  // ======================== getParticipationCount 가입 신청 건수 조회 ========================

  @Test
  void getParticipationCountReturnsCorrectCounts() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.countByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.PENDING))
        .willReturn(3L);
    given(crewParticipantRepository.countByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.LOCKED))
        .willReturn(2L);
    given(crewParticipantRepository.countByCrewIdAndStatus(CREW_ID, CrewParticipantStatus.REJECTED))
        .willReturn(1L);

    ParticipationCountResponse result = crewService.getParticipationCount(CREW_ID, hostUuid);

    assertThat(result.pendingCount()).isEqualTo(3L);
    assertThat(result.lockedCount()).isEqualTo(2L);
    assertThat(result.rejectedCount()).isEqualTo(1L);
  }

  @Test
  void getParticipationCountThrowsForbiddenNotHostWhenRequesterIsNotHost() {
    UUID hostUuid = UUID.randomUUID();
    UUID otherUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, otherUuid)).willReturn(false);
    given(crewRepository.existsById(CREW_ID)).willReturn(true);

    assertThatThrownBy(() -> crewService.getParticipationCount(CREW_ID, otherUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.FORBIDDEN_NOT_HOST);
  }

  // ======================== NOTIFY-002: 크루 알림 발송 ========================

  @Test
  void applyParticipationSendsCrewApplicationReceivedNotificationToHost() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    UUID applicantUuid = UUID.randomUUID();
    Member applicant = buildMember(applicantUuid);
    ReflectionTestUtils.setField(applicant, "id", 2L);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(crew, applicant, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));
    ReflectionTestUtils.setField(participant, "id", 2L);

    given(crewRepository.findByIdWithOptimisticLock(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, applicantUuid))
        .willReturn(Optional.empty());
    given(memberRepository.findByUuid(applicantUuid)).willReturn(Optional.of(applicant));
    given(crewParticipantRepository.countByCrewIdAndStatusIn(eq(CREW_ID), any())).willReturn(0L);
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    crewService.applyParticipation(CREW_ID, applicantUuid);

    ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
    then(notificationSender).should().send(eq(host), captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo("CREW_APPLICATION_RECEIVED");
    assertThat(captor.getValue().resourceId()).isEqualTo(String.valueOf(CREW_ID));
  }

  @Test
  void cancelParticipationSendsCrewApplicationCancelledNotificationToHost() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    UUID applicantUuid = UUID.randomUUID();
    Member applicant = buildMember(applicantUuid);
    ReflectionTestUtils.setField(applicant, "id", 2L);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(
            crew, applicant, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 2L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, applicantUuid))
        .willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    crewService.cancelParticipation(CREW_ID, applicantUuid);

    ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
    then(notificationSender).should().send(eq(host), captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo("CREW_APPLICATION_CANCELLED");
    assertThat(captor.getValue().resourceId()).isEqualTo(String.valueOf(CREW_ID));
  }

  @Test
  void approveParticipationSendsCrewApplicationApprovedNotificationToApplicant() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    UUID applicantUuid = UUID.randomUUID();
    Member applicant = buildMember(applicantUuid);
    ReflectionTestUtils.setField(applicant, "id", 2L);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(
            crew, applicant, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 2L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(2L)).willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    crewService.approveParticipation(CREW_ID, 2L, hostUuid);

    ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
    then(notificationSender).should().send(eq(applicant), captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo("CREW_APPLICATION_APPROVED");
    assertThat(captor.getValue().resourceId()).isEqualTo(String.valueOf(CREW_ID));
  }

  @Test
  void rejectParticipationSendsCrewApplicationRejectedNotificationToApplicant() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    UUID applicantUuid = UUID.randomUUID();
    Member applicant = buildMember(applicantUuid);
    ReflectionTestUtils.setField(applicant, "id", 2L);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant =
        CrewParticipant.createPending(
            crew, applicant, DEPOSIT, LocalDateTime.of(2026, 5, 1, 9, 0, 0));
    ReflectionTestUtils.setField(participant, "id", 2L);
    ReflectionTestUtils.setField(participant, "version", 0L);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewParticipantRepository.findById(2L)).willReturn(Optional.of(participant));
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    crewService.rejectParticipation(CREW_ID, 2L, hostUuid);

    ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
    then(notificationSender).should().send(eq(applicant), captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo("CREW_APPLICATION_REJECTED");
    assertThat(captor.getValue().resourceId()).isEqualTo(String.valueOf(CREW_ID));
  }

  // ======================== createCrew SPECIFIC_DAYS 요일 매핑 ========================

  @Test
  void createCrewWithSpecificDaysFrequencyTypePersistsMappedDayOfWeek() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    List<String> days = List.of("MONDAY", "WEDNESDAY");
    CrewCreateRequest request = buildCrewCreateRequest(MissionFrequencyType.SPECIFIC_DAYS, days);
    Crew crew = buildCrew(member, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    MissionRule missionRule = buildMissionRule(crew, MissionFrequencyType.SPECIFIC_DAYS);
    CrewParticipant participant = buildLockedParticipant(crew, member);

    given(memberRepository.findByUuid(memberUuid)).willReturn(Optional.of(member));
    given(objectMapper.writeValueAsString(any())).willReturn("{}");
    given(crewRepository.save(any())).willReturn(crew);
    given(missionRuleRepository.save(any())).willReturn(missionRule);
    given(missionScheduleDayRepository.save(any())).willReturn(null);
    given(crewParticipantRepository.saveAndFlush(any())).willReturn(participant);

    ArgumentCaptor<MissionScheduleDay> captor = ArgumentCaptor.forClass(MissionScheduleDay.class);

    crewService.createCrew(memberUuid, request);

    then(missionScheduleDayRepository).should(times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(MissionScheduleDay::getDayOfWeek)
        .containsExactlyInAnyOrder(DayOfWeek.MONDAY.getValue(), DayOfWeek.WEDNESDAY.getValue());
  }

  // ======================== helpers ========================

  private Member buildMember(UUID uuid) {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", 1L);
    ReflectionTestUtils.setField(member, "uuid", uuid);
    return member;
  }

  private Crew buildCrew(
      Member hostMember, int maxParticipants, LocalDateTime recruitmentDeadline) {
    Crew crew =
        Crew.create(
            hostMember,
            "테스트 크루",
            "크루 설명",
            null,
            "EXERCISE",
            "{}",
            HostPolicyVersion.HOST_POLICY_V1,
            LocalDateTime.now(SEOUL_ZONE),
            DEPOSIT,
            2,
            maxParticipants,
            recruitmentDeadline,
            LocalDateTime.now(SEOUL_ZONE).plusDays(5),
            LocalDateTime.now(SEOUL_ZONE).plusDays(35));
    ReflectionTestUtils.setField(crew, "id", CREW_ID);
    ReflectionTestUtils.setField(crew, "version", 0L);
    return crew;
  }

  private MissionRule buildMissionRule(Crew crew, MissionFrequencyType frequencyType) {
    MissionRule rule = MissionRule.create(crew, frequencyType, DailySettlementType.A);
    ReflectionTestUtils.setField(rule, "id", 1L);
    return rule;
  }

  private CrewParticipant buildLockedParticipant(Crew crew, Member member) {
    CrewParticipant participant =
        CrewParticipant.create(crew, member, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);
    return participant;
  }

  private CrewParticipant buildParticipantWithStatus(
      Crew crew, Member member, CrewParticipantStatus status) {
    CrewParticipant participant =
        CrewParticipant.createPending(crew, member, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));
    ReflectionTestUtils.setField(participant, "id", 1L);
    ReflectionTestUtils.setField(participant, "version", 0L);
    ReflectionTestUtils.setField(participant, "status", status);
    return participant;
  }

  private CrewCreateRequest buildCrewCreateRequest(
      MissionFrequencyType frequencyType, List<String> scheduleDays) {
    return new CrewCreateRequest(
        "테스트 크루",
        "크루 설명입니다",
        null,
        "EXERCISE",
        DEPOSIT,
        2,
        5,
        frequencyType,
        scheduleDays,
        DailySettlementType.A,
        buildHostAgreementRequest(),
        OffsetDateTime.now(SEOUL_ZONE).plusDays(3),
        LocalDate.now(SEOUL_ZONE).plusDays(5),
        LocalDate.now(SEOUL_ZONE).plusDays(35));
  }

  private CrewCreateRequest buildCrewCreateRequestWithImageKey(String imageS3Key) {
    return new CrewCreateRequest(
        "테스트 크루",
        "크루 설명입니다",
        imageS3Key,
        "EXERCISE",
        DEPOSIT,
        2,
        5,
        MissionFrequencyType.DAILY,
        null,
        DailySettlementType.A,
        buildHostAgreementRequest(),
        OffsetDateTime.now(SEOUL_ZONE).plusDays(3),
        LocalDate.now(SEOUL_ZONE).plusDays(5),
        LocalDate.now(SEOUL_ZONE).plusDays(35));
  }

  private HostAgreementRequest buildHostAgreementRequest() {
    return new HostAgreementRequest(
        HostPolicyVersion.HOST_POLICY_V1, OffsetDateTime.now(SEOUL_ZONE));
  }

  // ======================== findCrewMembers ========================

  @Test
  void findCrewMembersReturnsItemsWhenCallerIsHost() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant participant = buildLockedParticipant(crew, host);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(CREW_ID), eq(CrewParticipantStatus.LOCKED), eq(0L), any()))
        .willReturn(List.of(participant));

    var response = crewService.findCrewMembers(CREW_ID, hostUuid, null, 50);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).role()).isEqualTo("HOST");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void findCrewMembersReturnsItemsWhenCallerIsLockedParticipant() {
    UUID hostUuid = UUID.randomUUID();
    UUID memberUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Member member = buildMember(memberUuid);
    ReflectionTestUtils.setField(member, "id", 2L);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant callerParticipant = buildLockedParticipant(crew, member);
    CrewParticipant hostParticipant = buildLockedParticipant(crew, host);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(callerParticipant));
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(CREW_ID), eq(CrewParticipantStatus.LOCKED), eq(0L), any()))
        .willReturn(List.of(hostParticipant, callerParticipant));

    var response = crewService.findCrewMembers(CREW_ID, memberUuid, null, 50);

    assertThat(response.items()).hasSize(2);
    assertThat(response.items().stream().filter(i -> i.role().equals("HOST")).count()).isEqualTo(1);
    assertThat(response.items().stream().filter(i -> i.role().equals("MEMBER")).count())
        .isEqualTo(1);
  }

  @Test
  void findCrewMembersThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> crewService.findCrewMembers(CREW_ID, memberUuid, null, 50))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  @Test
  void findCrewMembersThrowsCrewAccessDeniedWhenCallerIsNotParticipant() {
    UUID hostUuid = UUID.randomUUID();
    UUID outsiderUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, outsiderUuid))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> crewService.findCrewMembers(CREW_ID, outsiderUuid, null, 50))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);
  }

  @Test
  void findCrewMembersThrowsCrewAccessDeniedWhenCallerHasNonLockedStatus() {
    UUID hostUuid = UUID.randomUUID();
    UUID memberUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Member member = buildMember(memberUuid);
    ReflectionTestUtils.setField(member, "id", 2L);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));
    CrewParticipant pendingParticipant =
        buildParticipantWithStatus(crew, member, CrewParticipantStatus.PENDING);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(pendingParticipant));

    assertThatThrownBy(() -> crewService.findCrewMembers(CREW_ID, memberUuid, null, 50))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);
  }

  @Test
  void findCrewMembersReturnsPaginatedResultWithNextCursor() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host, 5, LocalDateTime.now(SEOUL_ZONE).plusDays(3));

    CrewParticipant p1 = buildLockedParticipant(crew, host);
    Member member2 = buildMember(UUID.randomUUID());
    ReflectionTestUtils.setField(member2, "id", 2L);
    CrewParticipant p2 = buildLockedParticipant(crew, member2);
    ReflectionTestUtils.setField(p2, "id", 2L);

    given(crewQueryRepository.findCrewWithHost(CREW_ID)).willReturn(Optional.of(crew));
    given(
            crewParticipantRepository.findByCrewIdAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(CREW_ID), eq(CrewParticipantStatus.LOCKED), eq(0L), any()))
        .willReturn(List.of(p1, p2));

    var response = crewService.findCrewMembers(CREW_ID, hostUuid, null, 1);

    assertThat(response.items()).hasSize(1);
    assertThat(response.nextCursor()).isNotNull();
  }
}
