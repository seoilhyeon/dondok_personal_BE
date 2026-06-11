package com.oit.dondok.infra.image.exception;

import com.oit.dondok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImageErrorCode implements ErrorCode {
  // 디코딩/헤더 파싱 실패: 클라이언트 이미지가 손상/미지원이라 재처리해도 동일 실패하는 영구 거절(422).
  IMAGE_DECODE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "이미지를 디코딩할 수 없습니다."),
  // 스토리지(open/read) IO 실패: S3 등 서버측 일시적 장애일 수 있어 재시도 대상(5xx). 영구 실패로 단정하지 않는다.
  IMAGE_STORAGE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장소 읽기에 실패했습니다."),
  IMAGE_ENCODE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "이미지 재인코딩에 실패했습니다."),
  IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
  UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),
  IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 크기가 허용 범위를 초과했습니다."),
  IMAGE_DIMENSIONS_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 해상도가 허용 범위를 초과했습니다."),
  EMPTY_IMAGE(HttpStatus.UNPROCESSABLE_ENTITY, "이미지에 내용이 없습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
