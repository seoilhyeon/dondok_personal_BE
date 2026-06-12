package com.oit.dondok.domain.member.controller;

import com.oit.dondok.domain.crew.entity.CrewParticipantRole;
import com.oit.dondok.domain.crew.entity.CrewParticipantStatus;
import com.oit.dondok.domain.member.dto.request.UpdateProfileRequest;
import com.oit.dondok.domain.member.dto.response.ActivitySummaryResponse;
import com.oit.dondok.domain.member.dto.response.HostOperationSummaryResponse;
import com.oit.dondok.domain.member.dto.response.MeCrewListResponse;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.dto.response.ProfileUpdateResponse;
import com.oit.dondok.domain.member.service.HostOperationSummaryService;
import com.oit.dondok.domain.member.service.MeCrewService;
import com.oit.dondok.domain.member.service.MemberActivitySummaryService;
import com.oit.dondok.domain.member.service.MemberProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
@Tag(name = "내 정보", description = "내 정보 관련 API")
public class MemberProfileController {

  private final MemberProfileService memberProfileService;
  private final MemberActivitySummaryService memberActivitySummaryService;
  private final HostOperationSummaryService hostOperationSummaryService;
  private final MeCrewService meCrewService;

  @Operation(summary = "내 프로필 조회", description = "로그인한 회원의 프로필 정보를 조회합니다.")
  @GetMapping
  public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UUID memberUuid) {
    ProfileResponse response = memberProfileService.findProfileByMemberUuid(memberUuid);

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "내 활동 요약 조회", description = "로그인한 회원의 크루 참여 활동 요약을 조회합니다.")
  @GetMapping("/activity-summary")
  public ResponseEntity<ActivitySummaryResponse> getActivitySummary(
      @AuthenticationPrincipal UUID memberUuid) {
    ActivitySummaryResponse response =
        memberActivitySummaryService.findActivitySummaryByMemberUuid(memberUuid);

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "크루 운영 요약 조회", description = "로그인한 회원이 관리 중인 크루의 대기 현황을 조회합니다.")
  @GetMapping("/host-operation-summary")
  public ResponseEntity<HostOperationSummaryResponse> getHostOperationSummary(
      @AuthenticationPrincipal UUID memberUuid) {
    HostOperationSummaryResponse response =
        hostOperationSummaryService.findHostOperationSummaryByMemberUuid(memberUuid);

    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "내 크루 목록 조회",
      description = "로그인한 회원이 참여 중인 크루(PENDING·LOCKED) 목록을 커서 페이지네이션으로 조회합니다.")
  @GetMapping("/crews")
  public ResponseEntity<MeCrewListResponse> getMyCrews(
      @AuthenticationPrincipal UUID memberUuid,
      @RequestParam(required = false) CrewParticipantRole role,
      @RequestParam(required = false) CrewParticipantStatus myStatus,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(meCrewService.findMyCrews(memberUuid, role, myStatus, cursor, limit));
  }

  @Operation(summary = "내 프로필 수정", description = "로그인한 회원의 프로필 정보를 수정합니다.")
  @PatchMapping("/profile")
  public ResponseEntity<ProfileUpdateResponse> updateProfile(
      @AuthenticationPrincipal UUID memberUuid,
      @Valid @RequestBody UpdateProfileRequest updateProfileRequest) {
    ProfileUpdateResponse response =
        memberProfileService.updateProfile(memberUuid, updateProfileRequest);

    return ResponseEntity.ok(response);
  }
}
