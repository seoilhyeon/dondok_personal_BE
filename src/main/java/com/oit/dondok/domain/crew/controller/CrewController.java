package com.oit.dondok.domain.crew.controller;

import com.oit.dondok.domain.crew.dto.request.CrewCreateRequest;
import com.oit.dondok.domain.crew.dto.response.ApplicationListResponse;
import com.oit.dondok.domain.crew.dto.response.CrewCreateResponse;
import com.oit.dondok.domain.crew.dto.response.CrewDetailResponse;
import com.oit.dondok.domain.crew.dto.response.CrewListResponse;
import com.oit.dondok.domain.crew.dto.response.CrewMembersResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationApplyResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationApproveResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationCancelResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationCountResponse;
import com.oit.dondok.domain.crew.dto.response.ParticipationRejectResponse;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import com.oit.dondok.domain.crew.service.CrewService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "크루", description = "크루 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/crews")
public class CrewController {

  private final CrewService crewService;

  @Operation(summary = "크루 목록 조회", description = "크루 목록을 커서 페이지네이션으로 조회합니다. 비로그인 접근 가능.")
  @GetMapping
  public ResponseEntity<CrewListResponse> listCrews(
      @RequestParam(defaultValue = "RECRUITING") CrewStatus status,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(crewService.findCrewList(status, category, keyword, cursor, limit));
  }

  @Operation(summary = "크루 상세 조회", description = "특정 크루의 상세 정보와 내 참여 현황을 조회합니다.")
  @GetMapping("/{crewId}")
  public ResponseEntity<CrewDetailResponse> getCrewDetail(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable Long crewId) {
    return ResponseEntity.ok(crewService.findCrewDetail(crewId, memberUuid));
  }

  @Operation(summary = "크루 입장 신청", description = "크루에 입장을 신청합니다.")
  @PostMapping("/{crewId}/participants")
  public ResponseEntity<ParticipationApplyResponse> applyParticipation(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable Long crewId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(crewService.applyParticipation(crewId, memberUuid));
  }

  @Operation(summary = "크루 입장 신청 철회", description = "크루 입장 신청을 철회합니다.")
  @DeleteMapping("/{crewId}/participants/me")
  public ResponseEntity<ParticipationCancelResponse> cancelParticipation(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable Long crewId) {
    return ResponseEntity.ok(crewService.cancelParticipation(crewId, memberUuid));
  }

  @Operation(summary = "크루 생성", description = "새로운 크루를 생성합니다.")
  @PostMapping
  public ResponseEntity<CrewCreateResponse> createCrew(
      @AuthenticationPrincipal UUID memberUuid, @Valid @RequestBody CrewCreateRequest request) {
    CrewCreateResponse response = crewService.createCrew(memberUuid, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "입장 신청 승인", description = "방장이 PENDING 상태의 입장 신청을 승인합니다.")
  @PostMapping("/{crewId}/applications/{participantId}/approve")
  public ResponseEntity<ParticipationApproveResponse> approveParticipation(
      @AuthenticationPrincipal UUID memberUuid,
      @PathVariable Long crewId,
      @PathVariable Long participantId) {
    return ResponseEntity.ok(crewService.approveParticipation(crewId, participantId, memberUuid));
  }

  @Operation(summary = "입장 신청 거절", description = "방장이 PENDING 상태의 입장 신청을 거절합니다.")
  @PostMapping("/{crewId}/applications/{participantId}/reject")
  public ResponseEntity<ParticipationRejectResponse> rejectParticipation(
      @AuthenticationPrincipal UUID memberUuid,
      @PathVariable Long crewId,
      @PathVariable Long participantId) {
    return ResponseEntity.ok(crewService.rejectParticipation(crewId, participantId, memberUuid));
  }

  @Operation(summary = "가입 신청 목록 조회", description = "방장이 특정 상태의 가입 신청 목록을 조회합니다.")
  @GetMapping("/{crewId}/applications")
  public ResponseEntity<ApplicationListResponse> getParticipationList(
      @AuthenticationPrincipal UUID memberUuid,
      @PathVariable Long crewId,
      @RequestParam(defaultValue = "PENDING") CrewParticipantStatus status,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(
        crewService.getParticipationList(crewId, status, memberUuid, cursor, limit));
  }

  @Operation(summary = "가입 신청 건수 조회", description = "방장이 대기/승인/거절 건수를 조회합니다.")
  @GetMapping("/{crewId}/applications/count")
  public ResponseEntity<ParticipationCountResponse> getParticipationCount(
      @AuthenticationPrincipal UUID memberUuid, @PathVariable Long crewId) {
    return ResponseEntity.ok(crewService.getParticipationCount(crewId, memberUuid));
  }

  @Operation(
      summary = "크루 멤버 목록 조회",
      description = "LOCKED 상태의 크루 멤버 목록을 커서 페이지네이션으로 조회합니다. 크루 참여자(LOCKED) 또는 호스트만 접근 가능합니다.")
  @GetMapping("/{crewId}/members")
  public ResponseEntity<CrewMembersResponse> getCrewMembers(
      @AuthenticationPrincipal UUID memberUuid,
      @PathVariable Long crewId,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(crewService.findCrewMembers(crewId, memberUuid, cursor, limit));
  }
}
