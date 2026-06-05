package com.oit.dondok.domain.mission.port;

// S3 원본 이미지를 JPG로 재인코딩해 같은 key에 덮어쓰는 포트(EXIF 제거).
// 재인코딩은 EXIF를 지우므로 반드시 ImageMetadataPort.extract 이후에 호출해야 한다.
public interface ImageProcessingPort {
  void reEncode(String s3Key);
}
