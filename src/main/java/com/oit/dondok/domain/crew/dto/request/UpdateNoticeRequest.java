package com.oit.dondok.domain.crew.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record UpdateNoticeRequest(
    @Size(max = 100, message = "title must be 100 characters or less") String title,
    @Size(max = 5000, message = "content must be 5000 characters or less") String content) {

  @AssertTrue(message = "title must not be blank")
  public boolean isTitleValid() {
    return title == null || !title.isBlank();
  }

  @AssertTrue(message = "content must not be blank")
  public boolean isContentValid() {
    return content == null || !content.isBlank();
  }
}
