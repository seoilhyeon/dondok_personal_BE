package com.oit.dondok.domain.crew.controller;

import com.oit.dondok.domain.crew.dto.request.CrewCreateRequest;
import com.oit.dondok.domain.crew.dto.response.CrewCreateResponse;
import com.oit.dondok.domain.crew.dto.response.CrewDetailResponse;
import com.oit.dondok.domain.crew.dto.response.CrewListResponse;
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

  @Operation(summary = "크루 생성", description = "새로운 크루를 생성합니다.")
  @PostMapping
  public ResponseEntity<CrewCreateResponse> createCrew(
      @AuthenticationPrincipal UUID memberUuid, @Valid @RequestBody CrewCreateRequest request) {
    CrewCreateResponse response = crewService.createCrew(memberUuid, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
