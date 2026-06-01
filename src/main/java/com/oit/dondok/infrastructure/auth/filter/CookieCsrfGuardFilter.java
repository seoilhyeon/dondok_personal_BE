package com.oit.dondok.infrastructure.auth.filter;

import com.oit.dondok.infrastructure.auth.handler.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

public class CookieCsrfGuardFilter extends OncePerRequestFilter {

  private static final Set<String> COOKIE_AUTH_PATHS =
      Set.of("/api/auth/refresh", "/api/auth/logout");

  private final List<String> allowedOrigins;
  private final SecurityErrorHandler securityErrorHandler;

  public CookieCsrfGuardFilter(
      List<String> allowedOrigins, SecurityErrorHandler securityErrorHandler) {
    this.allowedOrigins = allowedOrigins;
    this.securityErrorHandler = securityErrorHandler;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!requiresGuard(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (hasAllowedOriginOrReferer(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    securityErrorHandler.handle(request, response, new AccessDeniedException("invalid origin"));
  }

  private boolean requiresGuard(HttpServletRequest request) {
    return "POST".equalsIgnoreCase(request.getMethod())
        && COOKIE_AUTH_PATHS.contains(request.getServletPath());
  }

  private boolean hasAllowedOriginOrReferer(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin != null && !origin.isBlank()) {
      return allowedOrigins.contains(origin);
    }

    String referer = request.getHeader(HttpHeaders.REFERER);
    if (referer != null && !referer.isBlank()) {
      return allowedOrigins.contains(extractOrigin(referer));
    }

    return false;
  }

  private String extractOrigin(String referer) {
    try {
      URI uri = URI.create(referer);
      if (uri.getScheme() == null || uri.getHost() == null) {
        return "";
      }

      String origin = uri.getScheme() + "://" + uri.getHost();
      if (uri.getPort() != -1) {
        return origin + ":" + uri.getPort();
      }
      return origin;
    } catch (IllegalArgumentException exception) {
      return "";
    }
  }
}
