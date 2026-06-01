package com.oit.dondok.domain.member.controller;

import com.oit.dondok.domain.member.dto.response.ProfileResponse;
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

  @GetMapping
  public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UUID memberUuid) {
    ProfileResponse response = memberProfileService.findProfileByMemberUuid(memberUuid);

    return ResponseEntity.ok(response);
  }
}
