package com.oit.dondok.domain.crew.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record UpdateNoticeRequest(
    @Size(max = 255, message = "title은 255자 이하여야 합니다.") String title,
    @Size(max = 5000, message = "content는 5000자 이하여야 합니다.") String content) {

  @AssertTrue(message = "title은 공백일 수 없습니다.")
  public boolean isTitleValid() {
    return title == null || !title.isBlank();
  }

  @AssertTrue(message = "content는 공백일 수 없습니다.")
  public boolean isContentValid() {
    return content == null || !content.isBlank();
  }
}
