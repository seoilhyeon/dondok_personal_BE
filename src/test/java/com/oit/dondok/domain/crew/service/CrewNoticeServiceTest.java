package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.oit.dondok.domain.crew.dto.request.AddReactionRequest;
import com.oit.dondok.domain.crew.dto.request.CreateNoticeRequest;
import com.oit.dondok.domain.crew.dto.request.UpdateNoticeRequest;
import com.oit.dondok.domain.crew.dto.response.NoticeDetailResponse;
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
import com.oit.dondok.domain.notification.port.NotificationPayload;
import com.oit.dondok.domain.notification.port.NotificationSender;
import com.oit.dondok.global.exception.CustomException;
import java.time.LocalDateTime;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CrewNoticeServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final Long CREW_ID = 1L;
  private static final Long NOTICE_ID = 10L;
  private static final Long DEPOSIT = 10_000L;
  private static final Long MEMBER_ID = 1L;
  private static final Long AUTHOR_MEMBER_ID = 2L;

  @Mock private CrewRepository crewRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private CrewNoticeRepository crewNoticeRepository;
  @Mock private CrewNoticeReactionRepository crewNoticeReactionRepository;
  @Mock private CrewNoticeReactionTxHelper crewNoticeReactionTxHelper;
  @Mock private NotificationSender notificationSender;

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

  // ======================== findNoticeDetail ========================

  @Test
  void findNoticeDetailReturnsDetailForLockedMember() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewNoticeReactionRepository.findByCrewNoticeIdIn(List.of(NOTICE_ID)))
        .willReturn(List.of());

    NoticeDetailResponse response =
        crewNoticeService.findNoticeDetail(CREW_ID, NOTICE_ID, memberUuid);

    assertThat(response.noticeId()).isEqualTo(NOTICE_ID);
    assertThat(response.crewId()).isEqualTo(CREW_ID);
    assertThat(response.authorMemberUuid()).isEqualTo(memberUuid);
    assertThat(response.title()).isEqualTo("테스트 공지");
    assertThat(response.content()).isEqualTo("테스트 내용");
    assertThat(response.myReactions()).isEmpty();
    assertThat(response.reactionCounts()).isEmpty();
  }

  @Test
  void findNoticeDetailFiltersMyReactionsAndCountsAllWhenMultipleMembersReacted() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    Member otherMember = buildMember(UUID.randomUUID());
    ReflectionTestUtils.setField(otherMember, "id", 2L);

    CrewNoticeReaction myThumbsUp = CrewNoticeReaction.create(notice, member, "👍");
    CrewNoticeReaction otherThumbsUp = CrewNoticeReaction.create(notice, otherMember, "👍");
    CrewNoticeReaction otherHeart = CrewNoticeReaction.create(notice, otherMember, "❤️");

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewNoticeReactionRepository.findByCrewNoticeIdIn(List.of(NOTICE_ID)))
        .willReturn(List.of(myThumbsUp, otherThumbsUp, otherHeart));

    NoticeDetailResponse response =
        crewNoticeService.findNoticeDetail(CREW_ID, NOTICE_ID, memberUuid);

    assertThat(response.myReactions()).containsExactly("👍");
    assertThat(response.reactionCounts()).containsEntry("👍", 2L);
    assertThat(response.reactionCounts()).containsEntry("❤️", 1L);
    assertThat(response.reactionCounts()).hasSize(2);
  }

  // reaction_counts 동률 정렬: 토큰순이 아니라 최초 등장 시각(createdAt) 오름차순.
  @Test
  void findNoticeDetailOrdersReactionCountsByCreatedAtOnTie() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);
    Member other = buildMember(UUID.randomUUID());
    ReflectionTestUtils.setField(other, "id", 2L);

    LocalDateTime base = LocalDateTime.of(2026, 6, 15, 10, 0);
    // 🔥가 👏보다 먼저 등장 → 🔥, 👏 순 (토큰 오름차순이면 👏가 먼저이므로 구분된다)
    CrewNoticeReaction fire = buildReaction(notice, other, "🔥", base);
    CrewNoticeReaction clap = buildReaction(notice, other, "👏", base.plusSeconds(5));

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewNoticeReactionRepository.findByCrewNoticeIdIn(List.of(NOTICE_ID)))
        .willReturn(List.of(fire, clap));

    NoticeDetailResponse response =
        crewNoticeService.findNoticeDetail(CREW_ID, NOTICE_ID, memberUuid);

    assertThat(response.reactionCounts()).containsExactly(Map.entry("🔥", 1L), Map.entry("👏", 1L));
  }

  @Test
  void findNoticeDetailThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsById(CREW_ID)).willReturn(false);

    assertThatThrownBy(() -> crewNoticeService.findNoticeDetail(CREW_ID, NOTICE_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  @Test
  void findNoticeDetailThrowsCrewAccessDeniedWhenMemberIsNotLockedParticipant() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> crewNoticeService.findNoticeDetail(CREW_ID, NOTICE_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);
  }

  @Test
  void findNoticeDetailThrowsNoticeNotFoundWhenNoticeDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> crewNoticeService.findNoticeDetail(CREW_ID, NOTICE_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.NOTICE_NOT_FOUND);
  }

  // ======================== createNotice ========================

  @Test
  void createNoticeSavesNoticeWhenCallerIsHost() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host);
    CrewNotice savedNotice = buildNotice(crew, host);

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(memberRepository.findByUuid(hostUuid)).willReturn(Optional.of(host));
    given(crewNoticeRepository.save(any(CrewNotice.class))).willReturn(savedNotice);
    given(crewParticipantRepository.findByCrewIdAndStatusIn(eq(CREW_ID), any()))
        .willReturn(List.of());

    crewNoticeService.createNotice(CREW_ID, hostUuid, new CreateNoticeRequest("공지 제목", "공지 내용"));

    then(crewNoticeRepository).should().save(any(CrewNotice.class));
  }

  @Test
  void createNoticeNotifiesAllLockedParticipants() {
    UUID hostUuid = UUID.randomUUID();
    Member host = buildMember(hostUuid);
    Crew crew = buildCrew(host);
    CrewNotice savedNotice = buildNotice(crew, host);

    Member member1 = buildMember(UUID.randomUUID());
    ReflectionTestUtils.setField(member1, "id", 2L);
    Member member2 = buildMember(UUID.randomUUID());
    ReflectionTestUtils.setField(member2, "id", 3L);
    CrewParticipant p1 = buildLockedParticipant(crew, member1);
    CrewParticipant p2 = buildLockedParticipant(crew, member2);

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(memberRepository.findByUuid(hostUuid)).willReturn(Optional.of(host));
    given(crewNoticeRepository.save(any(CrewNotice.class))).willReturn(savedNotice);
    given(crewParticipantRepository.findByCrewIdAndStatusIn(eq(CREW_ID), any()))
        .willReturn(List.of(p1, p2));

    crewNoticeService.createNotice(CREW_ID, hostUuid, new CreateNoticeRequest("공지 제목", "공지 내용"));

    ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
    then(notificationSender).should(times(2)).send(any(Member.class), captor.capture());
    captor.getAllValues().forEach(p -> assertThat(p.eventType()).isEqualTo("CREW_NOTICE_POSTED"));
  }

  @Test
  void createNoticeThrowsForbiddenNotHostWhenCallerIsNotHost() {
    UUID memberUuid = UUID.randomUUID();
    Crew crew = buildCrew(buildMember(UUID.randomUUID()));
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));

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
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.empty());

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

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
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

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

    crewNoticeService.updateNotice(
        CREW_ID, NOTICE_ID, hostUuid, new UpdateNoticeRequest("새 제목", null));

    assertThat(notice.getTitle()).isEqualTo("새 제목");
    assertThat(notice.getContent()).isEqualTo(originalContent);
  }

  @Test
  void updateNoticeThrowsNoticeNotFoundWhenNoticeDoesNotExist() {
    UUID hostUuid = UUID.randomUUID();
    Crew crew = buildCrew(buildMember(hostUuid));
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
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
    Crew crew = buildCrew(host);
    Crew otherCrew = buildCrew(host);
    ReflectionTestUtils.setField(otherCrew, "id", 99L);
    CrewNotice notice = buildNotice(otherCrew, host, NOTICE_ID);

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
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

    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

    crewNoticeService.deleteNotice(CREW_ID, NOTICE_ID, hostUuid);

    assertThat(notice.getStatus()).isEqualTo(CrewNoticeStatus.DELETED);
  }

  @Test
  void deleteNoticeThrowsNoticeNotFoundWhenNoticeDoesNotExist() {
    UUID hostUuid = UUID.randomUUID();
    Crew crew = buildCrew(buildMember(hostUuid));
    given(crewRepository.findById(CREW_ID)).willReturn(Optional.of(crew));
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

    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeReactionTxHelper.buildReactionResponse(NOTICE_ID, MEMBER_ID))
        .willReturn(new ReactionResponse(NOTICE_ID, List.of(), Map.of()));

    crewNoticeService.addReaction(CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍"));

    then(crewNoticeReactionRepository).should().upsert(NOTICE_ID, MEMBER_ID, "👍");
  }

  @Test
  void addReactionDelegatesResponseBuildingToTxHelper() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeReactionTxHelper.buildReactionResponse(NOTICE_ID, MEMBER_ID))
        .willReturn(new ReactionResponse(NOTICE_ID, List.of(), Map.of()));

    crewNoticeService.addReaction(CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍"));

    then(crewNoticeReactionTxHelper).should().buildReactionResponse(NOTICE_ID, MEMBER_ID);
  }

  @Test
  void addReactionReturnsResponseWithMyReactionsAndCounts() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeReactionTxHelper.buildReactionResponse(NOTICE_ID, MEMBER_ID))
        .willReturn(new ReactionResponse(NOTICE_ID, List.of("👍"), Map.of("👍", 1L)));

    ReactionResponse response =
        crewNoticeService.addReaction(CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍"));

    assertThat(response.noticeId()).isEqualTo(NOTICE_ID);
    assertThat(response.myReactions()).containsExactly("👍");
    assertThat(response.reactionCounts()).containsEntry("👍", 1L);
  }

  @Test
  void addReactionReturnsResponseWhenConcurrentDuplicateSaveOccurs() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeReactionTxHelper.buildReactionResponse(NOTICE_ID, MEMBER_ID))
        .willReturn(new ReactionResponse(NOTICE_ID, List.of("like"), Map.of("like", 1L)));

    ReactionResponse response =
        crewNoticeService.addReaction(
            CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("like"));

    assertThat(response.myReactions()).containsExactly("like");
    assertThat(response.reactionCounts()).containsEntry("like", 1L);
  }

  @Test
  void addReactionThrowsReactionNotAllowedWhenMemberIsNotLockedParticipant() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewNotice notice = buildNotice(crew, member);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                crewNoticeService.addReaction(
                    CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.REACTION_NOT_ALLOWED);
  }

  @Test
  void addReactionSendsNotificationToNoticeAuthorWhenReactorIsNotAuthor() {
    UUID authorUuid = UUID.randomUUID();
    UUID reactorUuid = UUID.randomUUID();
    Member noticeAuthor = buildMember(authorUuid, AUTHOR_MEMBER_ID);
    Member reactor = buildMember(reactorUuid, MEMBER_ID);
    Crew crew = buildCrew(noticeAuthor);
    CrewParticipant reactorParticipant = buildLockedParticipant(crew, reactor);
    CrewNotice notice = buildNotice(crew, noticeAuthor);

    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, reactorUuid))
        .willReturn(Optional.of(reactorParticipant));
    given(crewNoticeReactionTxHelper.buildReactionResponse(NOTICE_ID, MEMBER_ID))
        .willReturn(new ReactionResponse(NOTICE_ID, List.of("👍"), Map.of("👍", 1L)));

    crewNoticeService.addReaction(CREW_ID, NOTICE_ID, reactorUuid, new AddReactionRequest("👍"));

    then(notificationSender).should().send(eq(noticeAuthor), any(NotificationPayload.class));
  }

  @Test
  void addReactionDoesNotSendNotificationWhenReactorIsNoticeAuthor() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeReactionTxHelper.buildReactionResponse(NOTICE_ID, MEMBER_ID))
        .willReturn(new ReactionResponse(NOTICE_ID, List.of("👍"), Map.of("👍", 1L)));

    crewNoticeService.addReaction(CREW_ID, NOTICE_ID, memberUuid, new AddReactionRequest("👍"));

    then(notificationSender).shouldHaveNoInteractions();
  }

  // ======================== removeReaction ========================

  @Test
  void removeReactionDeletesReactionWhenItExists() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeReactionTxHelper.removeReaction(eq(NOTICE_ID), any(Member.class), eq("👍")))
        .willReturn(MEMBER_ID);
    given(crewNoticeReactionTxHelper.buildReactionResponse(NOTICE_ID, MEMBER_ID))
        .willReturn(new ReactionResponse(NOTICE_ID, List.of(), Map.of()));

    crewNoticeService.removeReaction(CREW_ID, NOTICE_ID, memberUuid, "👍");

    then(crewNoticeReactionTxHelper)
        .should()
        .removeReaction(eq(NOTICE_ID), any(Member.class), eq("👍"));
  }

  @Test
  void removeReactionDoesNothingWhenReactionDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeReactionTxHelper.removeReaction(eq(NOTICE_ID), any(Member.class), eq("👎")))
        .willReturn(MEMBER_ID);
    given(crewNoticeReactionTxHelper.buildReactionResponse(NOTICE_ID, MEMBER_ID))
        .willReturn(new ReactionResponse(NOTICE_ID, List.of(), Map.of()));

    crewNoticeService.removeReaction(CREW_ID, NOTICE_ID, memberUuid, "👎");

    then(crewNoticeReactionTxHelper)
        .should()
        .removeReaction(eq(NOTICE_ID), any(Member.class), eq("👎"));
  }

  // ======================== helpers ========================

  private Member buildMember(UUID uuid) {
    return buildMember(uuid, MEMBER_ID);
  }

  private Member buildMember(UUID uuid, Long id) {
    Member member = Member.create("test@example.com", "password-hash", "테스트닉네임");
    ReflectionTestUtils.setField(member, "id", id);
    ReflectionTestUtils.setField(member, "uuid", uuid);
    return member;
  }

  private CrewNoticeReaction buildReaction(
      CrewNotice notice, Member member, String reactionType, LocalDateTime createdAt) {
    CrewNoticeReaction reaction = CrewNoticeReaction.create(notice, member, reactionType);
    ReflectionTestUtils.setField(reaction, "createdAt", createdAt);
    return reaction;
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
