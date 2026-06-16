package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.oit.dondok.domain.crew.dto.request.CreateCommentRequest;
import com.oit.dondok.domain.crew.dto.request.UpdateCommentRequest;
import com.oit.dondok.domain.crew.dto.response.CommentListResponse;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewNoticeComment;
import com.oit.dondok.domain.crew.entity.CrewNoticeCommentStatus;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.crew.repository.CrewNoticeCommentRepository;
import com.oit.dondok.domain.crew.repository.CrewNoticeRepository;
import com.oit.dondok.domain.crew.repository.CrewParticipantRepository;
import com.oit.dondok.domain.crew.repository.CrewRepository;
import com.oit.dondok.domain.member.entity.Member;
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
class CrewNoticeCommentServiceTest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final Long CREW_ID = 1L;
  private static final Long NOTICE_ID = 10L;
  private static final Long COMMENT_ID = 100L;
  private static final Long MEMBER_ID = 1L;
  private static final Long DEPOSIT = 10_000L;

  @Mock private CrewRepository crewRepository;
  @Mock private CrewParticipantRepository crewParticipantRepository;
  @Mock private CrewNoticeRepository crewNoticeRepository;
  @Mock private CrewNoticeCommentRepository crewNoticeCommentRepository;

  @InjectMocks private CrewNoticeCommentService crewNoticeCommentService;

  // ======================== findCommentList ========================

  @Test
  void findCommentListReturnsItemsForLockedMember() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);
    CrewNoticeComment comment = buildComment(notice, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(
            crewNoticeCommentRepository.findByCrewNoticeIdAndStatusOrderByCreatedAtAscIdAsc(
                any(), any(), any()))
        .willReturn(List.of(comment));

    CommentListResponse response =
        crewNoticeCommentService.findCommentList(CREW_ID, NOTICE_ID, null, 20, memberUuid);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).commentId()).isEqualTo(COMMENT_ID);
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void findCommentListReturnsNextCursorWhenMoreItemsExist() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);
    CrewNoticeComment c1 = buildComment(notice, member, 100L);
    CrewNoticeComment c2 = buildComment(notice, member, 101L);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(
            crewNoticeCommentRepository.findByCrewNoticeIdAndStatusOrderByCreatedAtAscIdAsc(
                any(), any(), any()))
        .willReturn(List.of(c1, c2));

    CommentListResponse response =
        crewNoticeCommentService.findCommentList(CREW_ID, NOTICE_ID, null, 1, memberUuid);

    assertThat(response.items()).hasSize(1);
    assertThat(response.nextCursor()).isNotNull();
  }

  @Test
  void findCommentListThrowsCrewNotFoundWhenCrewDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsById(CREW_ID)).willReturn(false);

    assertThatThrownBy(
            () ->
                crewNoticeCommentService.findCommentList(CREW_ID, NOTICE_ID, null, 20, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_NOT_FOUND);
  }

  @Test
  void findCommentListThrowsCrewAccessDeniedWhenMemberIsNotLockedParticipant() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                crewNoticeCommentService.findCommentList(CREW_ID, NOTICE_ID, null, 20, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);
  }

  @Test
  void findCommentListThrowsNoticeNotFoundWhenNoticeDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                crewNoticeCommentService.findCommentList(CREW_ID, NOTICE_ID, null, 20, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.NOTICE_NOT_FOUND);
  }

  // ======================== createComment ========================

  @Test
  void createCommentSavesCommentWhenCallerIsLockedMember() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewParticipant participant = buildLockedParticipant(crew, member);
    CrewNotice notice = buildNotice(crew, member);

    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.of(participant));
    given(crewNoticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));
    given(crewNoticeCommentRepository.save(any(CrewNoticeComment.class)))
        .willAnswer(inv -> inv.getArgument(0));

    crewNoticeCommentService.createComment(
        CREW_ID, NOTICE_ID, memberUuid, new CreateCommentRequest("확인했습니다!"));

    then(crewNoticeCommentRepository).should().save(any(CrewNoticeComment.class));
  }

  @Test
  void createCommentThrowsCrewAccessDeniedWhenMemberIsNotLockedParticipant() {
    UUID memberUuid = UUID.randomUUID();
    given(crewRepository.existsById(CREW_ID)).willReturn(true);
    given(crewParticipantRepository.findByCrewIdAndMemberUuid(CREW_ID, memberUuid))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                crewNoticeCommentService.createComment(
                    CREW_ID, NOTICE_ID, memberUuid, new CreateCommentRequest("내용")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);
  }

  // ======================== updateComment ========================

  @Test
  void updateCommentModifiesContentWhenCallerIsAuthor() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewNotice notice = buildNotice(crew, member);
    CrewNoticeComment comment = buildComment(notice, member);

    given(crewNoticeCommentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

    crewNoticeCommentService.updateComment(
        CREW_ID, NOTICE_ID, COMMENT_ID, memberUuid, new UpdateCommentRequest("수정된 내용"));

    assertThat(comment.getContent()).isEqualTo("수정된 내용");
  }

  @Test
  void updateCommentThrowsCommentNotFoundWhenCommentDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewNoticeCommentRepository.findById(COMMENT_ID)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                crewNoticeCommentService.updateComment(
                    CREW_ID, NOTICE_ID, COMMENT_ID, memberUuid, new UpdateCommentRequest("내용")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.COMMENT_NOT_FOUND);
  }

  @Test
  void updateCommentThrowsForbiddenWhenCallerIsNotAuthor() {
    UUID memberUuid = UUID.randomUUID();
    UUID otherUuid = UUID.randomUUID();
    Member author = buildMember(otherUuid);
    Crew crew = buildCrew(author);
    CrewNotice notice = buildNotice(crew, author);
    CrewNoticeComment comment = buildComment(notice, author);

    given(crewNoticeCommentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                crewNoticeCommentService.updateComment(
                    CREW_ID, NOTICE_ID, COMMENT_ID, memberUuid, new UpdateCommentRequest("내용")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.COMMENT_FORBIDDEN);
  }

  // ======================== deleteComment ========================

  @Test
  void deleteCommentSetsStatusToDeletedWhenCallerIsAuthor() {
    UUID memberUuid = UUID.randomUUID();
    Member member = buildMember(memberUuid);
    Crew crew = buildCrew(member);
    CrewNotice notice = buildNotice(crew, member);
    CrewNoticeComment comment = buildComment(notice, member);

    given(crewNoticeCommentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

    crewNoticeCommentService.deleteComment(CREW_ID, NOTICE_ID, COMMENT_ID, memberUuid);

    assertThat(comment.getStatus()).isEqualTo(CrewNoticeCommentStatus.DELETED);
  }

  @Test
  void deleteCommentThrowsCommentNotFoundWhenCommentDoesNotExist() {
    UUID memberUuid = UUID.randomUUID();
    given(crewNoticeCommentRepository.findById(COMMENT_ID)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                crewNoticeCommentService.deleteComment(CREW_ID, NOTICE_ID, COMMENT_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.COMMENT_NOT_FOUND);
  }

  @Test
  void deleteCommentThrowsForbiddenWhenCallerIsNotAuthor() {
    UUID memberUuid = UUID.randomUUID();
    UUID otherUuid = UUID.randomUUID();
    Member author = buildMember(otherUuid);
    Crew crew = buildCrew(author);
    CrewNotice notice = buildNotice(crew, author);
    CrewNoticeComment comment = buildComment(notice, author);

    given(crewNoticeCommentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                crewNoticeCommentService.deleteComment(CREW_ID, NOTICE_ID, COMMENT_ID, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.COMMENT_FORBIDDEN);
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
    CrewNotice notice = CrewNotice.create(crew, author, "테스트 공지", "테스트 내용");
    ReflectionTestUtils.setField(notice, "id", NOTICE_ID);
    return notice;
  }

  private CrewNoticeComment buildComment(CrewNotice notice, Member author) {
    return buildComment(notice, author, COMMENT_ID);
  }

  private CrewNoticeComment buildComment(CrewNotice notice, Member author, Long id) {
    CrewNoticeComment comment = CrewNoticeComment.create(notice, author, "확인했습니다!");
    ReflectionTestUtils.setField(comment, "id", id);
    ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now(SEOUL_ZONE));
    return comment;
  }
}
