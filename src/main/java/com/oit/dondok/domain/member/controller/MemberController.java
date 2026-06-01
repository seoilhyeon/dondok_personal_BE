package com.oit.dondok.domain.member.controller;

import com.oit.dondok.domain.member.dto.request.SignupRequest;
import com.oit.dondok.domain.member.dto.response.SignupResponse;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/member")
public class MemberController {

  private final MemberService memberService;

  @PostMapping("/signup")
  public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
    Member member = memberService.signup(request.email(), request.password(), request.nickname());

    SignupResponse response =
        SignupResponse.of(
            member.getUuid(),
            member.getEmail(),
            member.getNickname(),
            member.getStatus(),
            member.getCreatedAt());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
