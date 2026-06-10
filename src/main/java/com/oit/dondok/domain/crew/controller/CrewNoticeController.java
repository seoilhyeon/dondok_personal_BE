package com.oit.dondok.domain.crew.controller;

import com.oit.dondok.domain.crew.dto.request.AddReactionRequest;
import com.oit.dondok.domain.crew.dto.request.CreateNoticeRequest;
import com.oit.dondok.domain.crew.dto.request.UpdateNoticeRequest;
import com.oit.dondok.domain.crew.dto.response.NoticeListResponse;
import com.oit.dondok.domain.crew.dto.response.ReactionResponse;
import com.oit.dondok.domain.crew.service.CrewNoticeService;
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

@Tag(name = "크루 공지", description = "크루 공지 및 리액션 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/crews/{crewId}/notices")
public class CrewNoticeController {

  private final CrewNoticeService crewNoticeService;

  @Operation(summary = "공지 목록 조회")
  @GetMapping
  public ResponseEntity<NoticeListResponse> listNotices(
      @PathVariable Long crewId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit,
      @AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(crewNoticeService.findNoticeList(crewId, cursor, limit, memberUuid));
  }

  @Operation(summary = "공지 생성")
  @PostMapping
  public ResponseEntity<Void> createNotice(
      @PathVariable Long crewId,
      @RequestBody @Valid CreateNoticeRequest request,
      @AuthenticationPrincipal UUID memberUuid) {
    crewNoticeService.createNotice(crewId, memberUuid, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @Operation(summary = "공지 수정")
  @PatchMapping("/{noticeId}")
  public ResponseEntity<Void> updateNotice(
      @PathVariable Long crewId,
      @PathVariable Long noticeId,
      @RequestBody UpdateNoticeRequest request,
      @AuthenticationPrincipal UUID memberUuid) {
    crewNoticeService.updateNotice(crewId, noticeId, memberUuid, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "공지 삭제")
  @DeleteMapping("/{noticeId}")
  public ResponseEntity<Void> deleteNotice(
      @PathVariable Long crewId,
      @PathVariable Long noticeId,
      @AuthenticationPrincipal UUID memberUuid) {
    crewNoticeService.deleteNotice(crewId, noticeId, memberUuid);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "리액션 추가")
  @PostMapping("/{noticeId}/reactions")
  public ResponseEntity<ReactionResponse> addReaction(
      @PathVariable Long crewId,
      @PathVariable Long noticeId,
      @RequestBody @Valid AddReactionRequest request,
      @AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(crewNoticeService.addReaction(crewId, noticeId, memberUuid, request));
  }

  @Operation(summary = "내 리액션 삭제")
  @DeleteMapping("/{noticeId}/reactions/me")
  public ResponseEntity<ReactionResponse> removeReaction(
      @PathVariable Long crewId,
      @PathVariable Long noticeId,
      @RequestParam("reaction_type") String reactionType,
      @AuthenticationPrincipal UUID memberUuid) {
    return ResponseEntity.ok(
        crewNoticeService.removeReaction(crewId, noticeId, memberUuid, reactionType));
  }
}
