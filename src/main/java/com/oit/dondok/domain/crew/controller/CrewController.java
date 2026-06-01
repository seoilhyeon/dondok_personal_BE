package com.oit.dondok.domain.crew.controller;

import com.oit.dondok.domain.crew.dto.request.CrewCreateRequest;
import com.oit.dondok.domain.crew.dto.response.CrewCreateResponse;
import com.oit.dondok.domain.crew.service.CrewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "크루", description = "크루 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/crews")
public class CrewController {

  // TODO: JWT 연결 후 @RequestHeader 방식 제거
  public static final String DEV_MEMBER_ID_HEADER = "X-Member-Id";

  private final CrewService crewService;

  @Operation(summary = "크루 생성", description = "새로운 크루를 생성합니다.")
  @PostMapping
  public ResponseEntity<CrewCreateResponse> createCrew(
      @RequestHeader(DEV_MEMBER_ID_HEADER) Long memberId,
      @Valid @RequestBody CrewCreateRequest request) {
    CrewCreateResponse response = crewService.createCrew(memberId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
