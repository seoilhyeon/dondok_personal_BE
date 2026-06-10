package com.oit.dondok.domain.mission.controller;

import com.oit.dondok.domain.mission.dto.response.FeedResponse;
import com.oit.dondok.domain.mission.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "피드", description = "인증 피드 조회 API")
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

  private final FeedService feedService;

  @Operation(
      summary = "인증 피드 조회",
      description =
          "내가 참여 중인 크루들의 인증 피드를 조회합니다. crew_id로 특정 "
              + "크루만, from/to로 특정 날짜 및 기간을 필터링할 수 있으며, cursor로 페이지네이션합니다.")
  @GetMapping
  public ResponseEntity<FeedResponse> getFeed(
      @AuthenticationPrincipal UUID memberUuid,
      @RequestParam(name = "crew_id", required = false) Long crewId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(feedService.getFeed(memberUuid, crewId, from, to, cursor, limit));
  }
}
