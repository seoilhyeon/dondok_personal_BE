package com.oit.dondok.domain.mission.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oit.dondok.domain.mission.entity.ExifRisk;
import java.time.OffsetDateTime;

// 서버가 원본 이미지에서 추출/판정한 risk signal 묶음
@JsonNaming(SnakeCaseStrategy.class)
public record ImageVerifyResponse(
    OffsetDateTime takenAt, String imageHash, ExifRisk exifRisk, boolean duplicate) {}
