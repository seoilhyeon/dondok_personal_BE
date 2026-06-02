package com.oit.dondok.domain.mission.port;

import java.time.OffsetDateTime;

// S3 원본 이미지 바이트에서 추출한 메타데이터.
// @param takenAt: EXIF 촬영 시각(Asia/Seoul offset 포함 OffsetDateTime). EXIF가 없거나 파싱 불가하면 null.
// @param sha256: 원본 바이트의 SHA-256 hex(소문자 64자).
public record ImageMetadata(OffsetDateTime takenAt, String sha256) {}
