package com.oit.dondok.global.exception;

import java.util.Objects;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

  private final ErrorCode errorCode;

  public CustomException(ErrorCode errorCode) {
    super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage());
    this.errorCode = errorCode;
  }

  public CustomException(ErrorCode errorCode, Throwable cause) {
    super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage(), cause);
    this.errorCode = errorCode;
  }

  public boolean isServerError() {
    return errorCode.getStatus().is5xxServerError();
  }
}
