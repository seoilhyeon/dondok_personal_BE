package com.oit.dondok.infrastructure.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.infrastructure.auth.handler.SecurityErrorHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CookieCsrfGuardFilterTest {

  private final CookieCsrfGuardFilter filter =
      new CookieCsrfGuardFilter(
          List.of("http://localhost:3000", "https://dondok-fe.vercel.app"),
          new SecurityErrorHandler(new ObjectMapper()));

  @Test
  void allowsCookieAuthRequestFromAllowedOrigin() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
    request.setServletPath("/api/auth/refresh");
    request.addHeader(HttpHeaders.ORIGIN, "http://localhost:3000");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void rejectsCookieAuthRequestFromDisallowedOrigin() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
    request.setServletPath("/api/auth/refresh");
    request.addHeader(HttpHeaders.ORIGIN, "https://evil.example.com");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void appliesGuardWhenContextPathExists() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dondok/api/auth/refresh");
    request.setContextPath("/dondok");
    request.setServletPath("/api/auth/refresh");
    request.addHeader(HttpHeaders.ORIGIN, "https://evil.example.com");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void allowsCookieAuthRequestFromAllowedRefererWhenOriginIsMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
    request.setServletPath("/api/auth/refresh");
    request.addHeader(HttpHeaders.REFERER, "https://dondok-fe.vercel.app/auth/callback");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void ignoresNonCookieAuthEndpoint() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setServletPath("/api/auth/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(200);
  }
}
