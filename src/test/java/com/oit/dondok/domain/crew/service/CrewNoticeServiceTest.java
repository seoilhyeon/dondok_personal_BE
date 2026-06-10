package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.oit.dondok.domain.crew.dto.request.AddReactionRequest;
import com.oit.dondok.domain.crew.dto.request.CreateNoticeRequest;
import com.oit.dondok.domain.crew.dto.request.UpdateNoticeRequest;
import com.oit.dondok.domain.crew.dto.response.NoticeListResponse;
import com.oit.dondok.domain.crew.dto.response.ReactionResponse;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.entity.CrewNoticeStatus;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewNoticeReactionRepository;
import com.oit.dondok.domain.crew.repository.CrewNoticeRepository;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.repository.MemberRepository;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
class CrewNoticeServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final Long CREW_ID = 1L;
  private static final Long NOTICE_ID = 10L;
  private static final Long DEPOSIT = 10_000L;
  private static final Long MEMBER_ID = 1L;

  @Mock private CrewRepository crewRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private CrewNoticeRepository crewNoticeRepository;
  @Mock private CrewNoticeReactionRepository crewNoticeReactionRepository;

  @InjectMocks private CrewNoticeService crewNoticeService;

  // ======================== findNoticeList ========================

  @Test
  void findNoticeListReturnsItemsForLockedMember() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(
            crewNoticeRepository.findByCrewIdAndStatusAndIdLessThanOrderByIdDesc(
                any(), any(), any(), any()))
        .willReturn(List.of(notice));

    NoticeListResponse response = crewNoticeService.findNoticeList(CREW_ID, null, 20, memberUuid);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).noticeId()).isEqualTo(NOTICE_ID);
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void findNoticeListReturnsNextCursorWhenMoreItemsExist() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    List<CrewNotice> rows = List.of(buildNotice(crew, member, 10L), buildNotice(crew, member, 9L));

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(
            crewNoticeRepository.findByCrewIdAndStatusAndIdLessThanOrderByIdDesc(
                any(), any(), any(), any()))
        .willReturn(rows);

    NoticeListResponse response = crewNoticeService.findNoticeList(CREW_ID, null, 1, memberUuid);

    assertThat(response.items()).hasSize(1);
    assertThat(response.nextCursor()).isNotNull();
  }

  @Test
  void findNoticeListThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsById(CREW_ID)).willReturn(false);

    assertThatThrownBy(() -> crewNoticeService.findNoticeList(CREW_ID, null, 20, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  @Test
  void findNoticeListThrowsCrewAccessDeniedWhenMemberIsNotLockedParticipant() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> crewNoticeService.findNoticeList(CREW_ID, null, 20, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);
  }

  // ======================== createNotice ========================

  @Test
  void createNoticeSavesNoticeWhenCallerIsHost() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(memberRepository.findByUuid(hostUuid)).willReturn(Optional.of(host));

    crewNoticeService.createNotice(CREW_ID, hostUuid, new CreateNoticeRequest("공지 제목", "공지 내용"));

    then(crewNoticeRepository).should().save(any(CrewNotice.class));
  }

  @Test
  void createNoticeThrowsForbiddenNotHostWhenCallerIsNotHost() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, memberUuid)).willReturn(false);
    given(crewRepository.existsById(CREW_ID)).willReturn(true);

    assertThatThrownBy(
            () ->
                crewNoticeService.createNotice(
                    CREW_ID, memberUuid, new CreateNoticeRequest("t", "c")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.FORBIDDEN_NOT_HOST);
  }

  @Test
  void createNoticeThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, memberUuid)).willReturn(false);
    given(crewRepository.existsById(CREW_ID)).willReturn(false);

    assertThatThrownBy(
            () ->
                crewNoticeService.createNotice(
                    CREW_ID, memberUuid, new CreateNoticeRequest("t", "c")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  // ======================== updateNotice ========================

  @Test
  void updateNoticeModifiesTitleAndContentWhenCallerIsHost() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host);
    CrewNotice notice = buildNotice(crew, host);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

    crewNoticeService.updateNotice(
        CREW_ID, NOTICE_ID, hostUuid, new UpdateNoticeRequest("새 제목", "새 내용"));

    assertThat(notice.getTitle()).isEqualTo("새 제목");
    assertThat(notice.getContent()).isEqualTo("새 내용");
  }

  @Test
  void updateNoticeKeepsOriginalContentWhenOnlyTitleProvided() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host);
    CrewNotice notice = buildNotice(crew, host);
    String originalContent = notice.getContent();

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

    crewNoticeService.updateNotice(
        CREW_ID, NOTICE_ID, hostUuid, new UpdateNoticeRequest("새 제목", null));

    assertThat(notice.getTitle()).isEqualTo("새 제목");
    assertThat(notice.getContent()).isEqualTo(originalContent);
  }

  @Test
  void updateNoticeThrowsNoticeNotFoundWhenNoticeDoesNotExist() {
    UUID hostUuid = UUID.randomUUID();
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                crewNoticeService.updateNotice(
                    CREW_ID, NOTICE_ID, hostUuid, new UpdateNoticeRequest("t", "c")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.NOTICE_NOT_FOUND);
  }

  @Test
  void updateNoticeThrowsNoticeNotFoundWhenNoticeBelongsToDifferentCrew() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew otherCrew = buildCrew(host);
    ReflectionTestUtils.setField(otherCrew, "id", 99L);
    CrewNotice notice = buildNotice(otherCrew, host, NOTICE_ID);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

    assertThatThrownBy(
            () ->
                crewNoticeService.updateNotice(
                    CREW_ID, NOTICE_ID, hostUuid, new UpdateNoticeRequest("t", "c")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.NOTICE_NOT_FOUND);
  }

  // ======================== deleteNotice ========================

  @Test
  void deleteNoticeSetsStatusToDeleted() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host);
    CrewNotice notice = buildNotice(crew, host);

    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

    crewNoticeService.deleteNotice(CREW_ID, NOTICE_ID, hostUuid);

    assertThat(notice.getStatus()).isEqualTo(CrewNoticeStatus.DELETED);
  }

  @Test
  void deleteNoticeThrowsNoticeNotFoundWhenNoticeDoesNotExist() {
    UUID hostUuid = UUID.randomUUID();
    given(crewRepository.existsByIdAndHostMemberUuid(CREW_ID, hostUuid)).willReturn(true);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> crewNoticeService.deleteNotice(CREW_ID, NOTICE_ID, hostUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.NOTICE_NOT_FOUND);
  }

  // ======================== addReaction ========================

  @Test
  void addReactionSavesNewReactionWhenNotAlreadyExists() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(
            crewNoticeReactionRepository.existsByCrewNoticeIdAndMemberIdAndReactionType(
                NOTICE_ID, MEMBER_ID, "👍"))
        .willReturn(false);
    given(crewNoticeReactionRepository.findByCrewNoticeId(NOTICE_ID)).willReturn(List.of());

    crewNoticeService.addReaction(CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍"));

    then(crewNoticeReactionRepository).should().save(any(CrewNoticeReaction.class));
  }

  @Test
  void addReactionSkipsSaveWhenReactionAlreadyExists() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(
            crewNoticeReactionRepository.existsByCrewNoticeIdAndMemberIdAndReactionType(
                NOTICE_ID, MEMBER_ID, "👍"))
        .willReturn(true);
    given(crewNoticeReactionRepository.findByCrewNoticeId(NOTICE_ID)).willReturn(List.of());

    crewNoticeService.addReaction(CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍"));

    then(crewNoticeReactionRepository).should(never()).save(any());
  }

  @Test
  void addReactionReturnsResponseWithMyReactionsAndCounts() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);
    CrewNoticeReaction reaction = CrewNoticeReaction.create(notice, member, "👍");

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(
            crewNoticeReactionRepository.existsByCrewNoticeIdAndMemberIdAndReactionType(
                NOTICE_ID, MEMBER_ID, "👍"))
        .willReturn(false);
    given(crewNoticeReactionRepository.findByCrewNoticeId(NOTICE_ID)).willReturn(List.of(reaction));

    ReactionResponse response =
        crewNoticeService.addReaction(CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍"));

    assertThat(response.noticeId()).isEqualTo(NOTICE_ID);
    assertThat(response.myReactions()).containsExactly("👍");
    assertThat(response.reactionCounts()).containsEntry("👍", 1L);
  }

  @Test
  void addReactionThrowsCrewAccessDeniedWhenMemberIsNotLockedParticipant() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                crewNoticeService.addReaction(
                    CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);
  }

  // ======================== removeReaction ========================

  @Test
  void removeReactionDeletesReactionWhenItExists() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);
    CrewNoticeReaction reaction = CrewNoticeReaction.create(notice, member, "👍");

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(
            crewNoticeReactionRepository.findByCrewNoticeIdAndMemberIdAndReactionType(
                NOTICE_ID, MEMBER_ID, "👍"))
        .willReturn(Optional.of(reaction));
    given(crewNoticeReactionRepository.findByCrewNoticeId(NOTICE_ID)).willReturn(List.of());

    crewNoticeService.removeReaction(CREW_ID, NOTICE_ID, memberUuid, "👍");

    then(crewNoticeReactionRepository).should().delete(reaction);
  }

  @Test
  void removeReactionDoesNothingWhenReactionDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(
            crewNoticeReactionRepository.findByCrewNoticeIdAndMemberIdAndReactionType(
                NOTICE_ID, MEMBER_ID, "👎"))
        .willReturn(Optional.empty());
    given(crewNoticeReactionRepository.findByCrewNoticeId(NOTICE_ID)).willReturn(List.of());

    crewNoticeService.removeReaction(CREW_ID, NOTICE_ID, memberUuid, "👎");

    then(crewNoticeReactionRepository).should(never()).delete(any());
  }

  // ======================== helpers ========================

  private Member buildMember(UUID uuid) {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", MEMBER_ID);
    ReflectionTestUtils.setField(member, "uuid", uuid);
    return member;
  }

  private Crew buildCrew(Member hostMember) {
    Crew crew =
        Crew.create(
            hostMember,
            "테스트 크루",
            "설명",
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
    ReflectionTestUtils.setField(crew, "id", CREW_ID);
    return crew;
  }

  private CrewParticipant buildLockedParticipant(Crew crew, Member member) {
    CrewParticipant participant =
        CrewParticipant.create(crew, member, DEPOSIT, LocalDateTime.now(SEOUL_ZONE));
    ReflectionTestUtils.setField(participant, "id", 1L);
    return participant;
  }

  private CrewNotice buildNotice(Crew crew, Member author) {
    return buildNotice(crew, author, NOTICE_ID);
  }

  private CrewNotice buildNotice(Crew crew, Member author, Long id) {
    CrewNotice notice = CrewNotice.create(crew, author, "테스트 공지", "테스트 내용");
    ReflectionTestUtils.setField(notice, "id", id);
    return notice;
  }
}
