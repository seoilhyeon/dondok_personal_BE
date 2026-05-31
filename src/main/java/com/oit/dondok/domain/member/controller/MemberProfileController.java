package com.oit.dondok.domain.member.controller;

import com.oit.dondok.domain.member.dto.response.ProfileResponse;
import com.oit.dondok.domain.member.service.MemberProfileService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class MemberProfileController {

  public static final String DEV_MEMBER_UUID_HEADER = "X-Dev-Member-Uuid";

  private final MemberProfileService memberProfileService;

  // TODO: JWT 연결 후 bypass 제거
  @GetMapping
  public ResponseEntity<ProfileResponse> getProfile(
      @RequestHeader(DEV_MEMBER_UUID_HEADER) UUID memberUuid) {
    ProfileResponse response = memberProfileService.findProfileByMemberUuid(memberUuid);

    return ResponseEntity.ok(response);
  }
}
