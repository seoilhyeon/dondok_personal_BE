package com.oit.dondok.domain.mission.controller;

import com.oit.dondok.domain.mission.dto.request.AddReactionRequest;
import com.oit.dondok.domain.mission.dto.response.ReactionResponse;
import com.oit.dondok.domain.mission.service.MissionLogReactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "리액션", description = "미션 인증 로그 이모지 리액션 API")
@RestController
@RequestMapping("/api/mission-logs/{missionLogId}/reactions")
@RequiredArgsConstructor
public class MissionLogReactionController {

  private final MissionLogReactionService missionLogReactionService;

  @Operation(summary = "리액션 추가", description = "미션 인증 로그에 이모지 리액션을 멱등 추가합니다.")
  @PostMapping
  public ResponseEntity<ReactionResponse> addReaction(
      @AuthenticationPrincipal UUID memberUuid,
      @PathVariable Long missionLogId,
      @RequestBody AddReactionRequest request) {
    return ResponseEntity.ok(
        missionLogReactionService.addReaction(memberUuid, missionLogId, request.reactionType()));
  }

  @Operation(summary = "내 리액션 삭제", description = "내가 남긴 이모지 리액션을 멱등 삭제합니다.")
  @DeleteMapping("/me")
  public ResponseEntity<ReactionResponse> removeReaction(
      @AuthenticationPrincipal UUID memberUuid,
      @PathVariable Long missionLogId,
      @RequestParam("reaction_type") String reactionType) {
    return ResponseEntity.ok(
        missionLogReactionService.removeReaction(memberUuid, missionLogId, reactionType));
  }
}
