package com.oit.dondok.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    controllers = {GlobalExceptionTestController.class, ValidatedExceptionTestController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  // CustomException을 ErrorCode의 status와 ErrorResponse {code, message}로 변환하는지 검증한다.
  @Test
  void customException() throws Exception {
    mockMvc
        .perform(get("/custom-exception"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("유효한 입력 형식이 아닙니다."));
  }

  // 처리하지 못한 일반 예외를 500 SERVER_ERROR 응답으로 감싸는지 검증한다.
  @Test
  void unexpectedException() throws Exception {
    mockMvc
        .perform(get("/unexpected-exception"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
        .andExpect(jsonPath("$.message").value("예상치 못한 문제가 발생했습니다."));
  }

  // 지원하지 않는 HTTP method 요청이 405와 Allow 헤더, METHOD_NOT_SUPPORTED 응답을 반환하는지 검증한다.
  @Test
  void methodNotSupported() throws Exception {
    mockMvc
        .perform(post("/method-only-get"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_SUPPORTED"))
        .andExpect(jsonPath("$.message").value("지원하지 않는 HTTP 메서드입니다."));
  }

  // malformed JSON 요청 body가 400 INVALID_INPUT 응답으로 변환되는지 검증한다.
  @Test
  void malformedJson() throws Exception {
    mockMvc
        .perform(
            post("/body-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name":
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("유효한 입력 형식이 아닙니다."));
  }

  // 필수 query parameter 누락이 400 INVALID_INPUT 응답으로 변환되는지 검증한다.
  @Test
  void missingServletRequestParameter() throws Exception {
    mockMvc
        .perform(get("/required-parameter"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("유효한 입력 형식이 아닙니다."));
  }

  // query parameter 타입 변환 실패가 400 INVALID_INPUT 응답과 파라미터명을 포함하는 메시지로 변환되는지 검증한다.
  @Test
  void typeMismatch() throws Exception {
    mockMvc
        .perform(get("/type-mismatch").param("id", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value(containsString("id")))
        .andExpect(jsonPath("$.message").value(containsString("파라미터 타입이 올바르지 않습니다.")));
  }

  // 지원하지 않는 Content-Type 요청이 415 UNSUPPORTED_MEDIA_TYPE 응답으로 변환되는지 검증한다.
  @Test
  void unsupportedMediaType() throws Exception {
    mockMvc
        .perform(post("/body-validation").contentType(MediaType.TEXT_PLAIN).content("name=test"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(
            header().string(HttpHeaders.ACCEPT, containsString(MediaType.APPLICATION_JSON_VALUE)))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.message").value("지원하지 않는 Content-Type 입니다."));
  }

  // Accept: application/xml 요청으로 406 negotiation 경로를 검증한다.
  // 요청한 Accept 타입에 맞춰 에러 응답도 XML로 직렬화되므로 xpath로 ErrorResponse를 확인한다.
  @Test
  void notAcceptableWithXmlAccept() throws Exception {
    mockMvc
        .perform(get("/json-only").accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable())
        .andExpect(xpath("/ErrorResponse/code").string("NOT_ACCEPTABLE"))
        .andExpect(xpath("/ErrorResponse/message").exists());
  }

  // 존재하지 않는 정적 리소스 요청이 404 NOT_FOUND 응답으로 변환되는지 검증한다.
  @Test
  void noResourceFound() throws Exception {
    mockMvc
        .perform(get("/not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
  }

  // @RequestBody DTO 필드 검증 실패가 400 INVALID_INPUT 응답과 field 메시지로 변환되는지 검증한다.
  @Test
  void bodyFieldValidation() throws Exception {
    mockMvc
        .perform(
            post("/body-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": ""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("name: 이름은 필수입니다."));
  }

  // @RequestBody DTO 객체 단위 검증 실패가 400 INVALID_INPUT 응답으로 변환되는지 검증한다.
  @Test
  void bodyObjectValidation() throws Exception {
    mockMvc
        .perform(
            post("/object-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate": "2026-05-10",
                      "endDate": "2026-05-01"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value(containsString("시작일은 종료일보다 이전이어야 합니다.")));
  }

  // 컨트롤러 요청 파라미터 검증 실패가 400 INVALID_INPUT 응답으로 변환되는지 검증한다.
  @Test
  void parameterValidation() throws Exception {
    mockMvc
        .perform(get("/parameter-validation").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value(containsString("페이지는 1 이상이어야 합니다.")));
  }

  // 컨트롤러 반환값 검증 실패가 서버 계약 위반으로 취급되어 500 SERVER_ERROR 응답으로 변환되는지 검증한다.
  @Test
  void returnValueValidation() throws Exception {
    mockMvc
        .perform(get("/return-value-validation"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
        .andExpect(jsonPath("$.message").value("예상치 못한 문제가 발생했습니다."));
  }

  // @Validated 기반 ConstraintViolationException이 400 INVALID_INPUT 응답으로 변환되는지 검증한다.
  @Test
  void constraintViolation() throws Exception {
    mockMvc
        .perform(get("/constraint-violation").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").exists());
  }
}

@RestController
class GlobalExceptionTestController {

  @GetMapping("/custom-exception")
  void customException() {
    throw new CustomException(GlobalErrorCode.INVALID_INPUT);
  }

  @GetMapping("/unexpected-exception")
  void unexpectedException() {
    throw new RuntimeException("boom");
  }

  @GetMapping("/method-only-get")
  void methodOnlyGet() {}

  @PostMapping("/body-validation")
  void bodyValidation(@RequestBody @Valid TestRequest request) {}

  @PostMapping("/object-validation")
  void objectValidation(@RequestBody @Valid DateRangeRequest request) {}

  @GetMapping("/parameter-validation")
  void parameterValidation(@RequestParam @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") int page) {}

  @GetMapping("/required-parameter")
  void requiredParameter(@RequestParam String keyword) {}

  @GetMapping("/type-mismatch")
  void typeMismatch(@RequestParam Long id) {}

  @GetMapping("/return-value-validation")
  @NotBlank(message = "반환값은 비어 있을 수 없습니다.")
  String returnValueValidation() {
    return "";
  }

  @GetMapping(value = "/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
  void jsonOnly() {}
}

@Validated
@RestController
class ValidatedExceptionTestController {

  @GetMapping("/constraint-violation")
  void constraintViolation(@RequestParam @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") int page) {}
}

class TestRequest {

  @NotBlank(message = "이름은 필수입니다.")
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

class DateRangeRequest {

  private LocalDate startDate;

  private LocalDate endDate;

  @AssertTrue(message = "시작일은 종료일보다 이전이어야 합니다.")
  public boolean isValidDateRange() {
    if (startDate == null || endDate == null) {
      return true;
    }

    return startDate.isBefore(endDate);
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }
}
