package com.oit.dondok.domain.member.controller;

import com.oit.dondok.domain.member.dto.request.SignupRequest;
import com.oit.dondok.domain.member.dto.response.SignupResponse;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "회원", description = "회원 관련 API")
public class MemberController {

  private final MemberService memberService;

  @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임으로 회원가입합니다.")
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
