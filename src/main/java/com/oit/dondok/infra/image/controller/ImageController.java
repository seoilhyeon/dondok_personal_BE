package com.oit.dondok.infra.image.controller;

import com.oit.dondok.infra.image.dto.PresignedUrlRequest;
import com.oit.dondok.infra.image.dto.PresignedUrlResponse;
import com.oit.dondok.infra.image.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Tag(name = "이미지", description = "이미지 업로드 관련 API")
public class ImageController {

  private final ImageService imageService;

  @Operation(summary = "Presigned URL 발급", description = "이미지 업로드를 위한 Presigned URL을 발급합니다.")
  @PostMapping("/presigned-url")
  public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
      @AuthenticationPrincipal UUID memberUuid, @Valid @RequestBody PresignedUrlRequest request) {
    return ResponseEntity.ok(imageService.generatePresignedUrl(memberUuid, request));
  }
}
