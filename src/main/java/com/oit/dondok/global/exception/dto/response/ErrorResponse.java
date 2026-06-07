package com.oit.dondok.global.exception.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import lombok.Builder;
import org.springframework.http.HttpStatus;

@Builder
public record ErrorResponse(
    @JsonIgnore HttpStatus status, String code, String message, String timestamp) {

  public static ErrorResponse error(CustomException exception) {
    ErrorCode errorCode = exception.getErrorCode();

    return ErrorResponse.builder()
        .status(errorCode.getStatus())
        .code(errorCode.getCode())
        .message(exception.getMessage())
        .timestamp(nowSeoul())
        .build();
  }

  public static ErrorResponse error(ErrorCode errorCode) {
    return ErrorResponse.builder()
        .status(errorCode.getStatus())
        .code(errorCode.getCode())
        .message(errorCode.getMessage())
        .timestamp(nowSeoul())
        .build();
  }

  public static ErrorResponse error(ErrorCode errorCode, String message) {
    return ErrorResponse.builder()
        .status(errorCode.getStatus())
        .code(errorCode.getCode())
        .message(message)
        .timestamp(nowSeoul())
        .build();
  }

  public static ErrorResponse of(HttpStatus status, String code, String message) {
    return ErrorResponse.builder()
        .status(status)
        .code(code)
        .message(message)
        .timestamp(nowSeoul())
        .build();
  }

  private static String nowSeoul() {
    return OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        .truncatedTo(ChronoUnit.SECONDS)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
