package com.oit.dondok.global.util;

import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CursorCodec {

  private CursorCodec() {}

  public static Long decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      throw new CustomException(GlobalErrorCode.INVALID_CURSOR);
    }
  }

  public static String encode(Long id) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
  }
}
