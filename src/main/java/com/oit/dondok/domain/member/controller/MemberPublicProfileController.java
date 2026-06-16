package com.oit.dondok.domain.member.controller;

import com.oit.dondok.domain.member.dto.response.MemberPublicProfileResponse;
import com.oit.dondok.domain.member.service.MemberPublicProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
@Tag(name = "회원 프로필", description = "타인 회원 프로필 조회 API")
public class MemberPublicProfileController {

  private final MemberPublicProfileService memberPublicProfileService;

  @Operation(
      summary = "타인 프로필 조회",
      description = "memberUuid로 특정 회원의 공개 프로필 및 활동 정보를 조회합니다. 본인 조회도 가능합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "404", description = "회원을 찾을 수 없음")
  })
  @GetMapping("/{memberUuid}/profile")
  public ResponseEntity<MemberPublicProfileResponse> getMemberProfile(
      @AuthenticationPrincipal UUID callerMemberUuid, @PathVariable UUID memberUuid) {
    MemberPublicProfileResponse response =
        memberPublicProfileService.findPublicProfileByMemberUuid(memberUuid);
    return ResponseEntity.ok(response);
  }
}
