package com.oit.dondok.domain.crew.controller;

import com.oit.dondok.domain.crew.dto.request.CreateCommentRequest;
import com.oit.dondok.domain.crew.dto.request.UpdateCommentRequest;
import com.oit.dondok.domain.crew.dto.response.CommentListResponse;
import com.oit.dondok.domain.crew.service.CrewNoticeCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "크루 공지 댓글", description = "크루 공지 댓글 CRUD API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/crews/{crewId}/notices/{noticeId}/comments")
public class CrewNoticeCommentController {

  private final CrewNoticeCommentService crewNoticeCommentService;

  @Operation(summary = "댓글 목록 조회")
  @GetMapping
  public ResponseEntity<CommentListResponse> listComments(
      @PathVariable Long crewId,
      @PathVariable Long noticeId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit,
      @AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(
        crewNoticeCommentService.findCommentList(crewId, noticeId, cursor, limit, memberUuid));
  }

  @Operation(summary = "댓글 작성")
  @PostMapping
  public ResponseEntity<Void> createComment(
      @PathVariable Long crewId,
      @PathVariable Long noticeId,
      @RequestBody @Valid CreateCommentRequest request,
      @AuthenticationPrincipal UUID memberUuid) {
    crewNoticeCommentService.createComment(crewId, noticeId, memberUuid, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @Operation(summary = "댓글 수정")
  @PatchMapping("/{commentId}")
  public ResponseEntity<Void> updateComment(
      @PathVariable Long crewId,
      @PathVariable Long noticeId,
      @PathVariable Long commentId,
      @RequestBody @Valid UpdateCommentRequest request,
      @AuthenticationPrincipal UUID memberUuid) {
    crewNoticeCommentService.updateComment(crewId, noticeId, commentId, memberUuid, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "댓글 삭제")
  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> deleteComment(
      @PathVariable Long crewId,
      @PathVariable Long noticeId,
      @PathVariable Long commentId,
      @AuthenticationPrincipal UUID memberUuid) {
    crewNoticeCommentService.deleteComment(crewId, noticeId, commentId, memberUuid);
    return ResponseEntity.ok().build();
  }
}
