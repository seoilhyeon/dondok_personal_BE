package com.oit.dondok.domain.member.controller;

import com.oit.dondok.domain.member.dto.response.ActivitySummaryResponse;
import com.oit.dondok.domain.member.dto.response.HostOperationSummaryResponse;
import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.service.HostOperationSummaryService;
import com.oit.dondok.domain.member.service.MemberActivitySummaryService;
import com.oit.dondok.domain.member.service.MemberProfileService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class MemberProfileController {

  private final MemberProfileService memberProfileService;
  private final MemberActivitySummaryService memberActivitySummaryService;
  private final HostOperationSummaryService hostOperationSummaryService;

  @GetMapping
  public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UUID memberUuid) {
    ProfileResponse response = memberProfileService.findProfileByMemberUuid(memberUuid);

    return ResponseEntity.ok(response);
  }

  @GetMapping("/activity-summary")
  public ResponseEntity<ActivitySummaryResponse> getActivitySummary(
      @AuthenticationPrincipal UUID memberUuid) {
    ActivitySummaryResponse response =
        memberActivitySummaryService.findActivitySummaryByMemberUuid(memberUuid);

    return ResponseEntity.ok(response);
  }

  @GetMapping("/host-operation-summary")
  public ResponseEntity<HostOperationSummaryResponse> getHostOperationSummary(
      @AuthenticationPrincipal UUID memberUuid) {
    HostOperationSummaryResponse response =
        hostOperationSummaryService.findHostOperationSummaryByMemberUuid(memberUuid);

    return ResponseEntity.ok(response);
  }
}
