package com.oit.dondok.domain.mission.port;

// S3 원본 이미지에서 EXIF 촬영 시각과 SHA-256 해시를 추출하는 포트.
// 추출(mechanism)은 infra 어댑터가 담당하고, 그 결과로 위험/중복을 판정(decision)하는 책임은 mission 도메인이 가진다.
// 재인코딩 시 EXIF가 제거되므로 반드시 reEncode 이전 원본 바이트 기준으로 호출해야 한다.
public interface ImageMetadataPort {

  ImageMetadata extract(String s3Key);
}
