package com.oit.dondok.global.exception.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.ErrorCode;
import lombok.Builder;
import org.springframework.http.HttpStatus;

@Builder
public record ErrorResponse(@JsonIgnore HttpStatus status, String code, String message) {

  public static ErrorResponse error(CustomException exception) {
    ErrorCode errorCode = exception.getErrorCode();

    return ErrorResponse.builder()
        .status(errorCode.getStatus())
        .code(errorCode.getCode())
        .message(exception.getMessage())
        .build();
  }

  public static ErrorResponse error(ErrorCode errorCode) {
    return ErrorResponse.builder()
        .status(errorCode.getStatus())
        .code(errorCode.getCode())
        .message(errorCode.getMessage())
        .build();
  }

  public static ErrorResponse error(ErrorCode errorCode, String message) {
    return ErrorResponse.builder()
        .status(errorCode.getStatus())
        .code(errorCode.getCode())
        .message(message)
        .build();
  }

  public static ErrorResponse of(HttpStatus status, String code, String message) {
    return ErrorResponse.builder().status(status).code(code).message(message).build();
  }
}
