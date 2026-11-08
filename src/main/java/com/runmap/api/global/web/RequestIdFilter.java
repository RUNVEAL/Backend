package com.runmap.api.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 무작위 UUIDv4 를 X-Request-Id 로 붙인다(사용자·기기 ID 아님, 클라이언트 값은 무시). POST 응답은 Cache-Control: no-store
 * (API 명세 §2.4).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  static final String ATTRIBUTE = RequestIdFilter.class.getName();

  public static String of(HttpServletRequest request) {
    Object id = request.getAttribute(ATTRIBUTE);
    return id == null ? null : id.toString();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader(HEADER, id);
    if ("POST".equals(request.getMethod())) {
      response.setHeader("Cache-Control", "no-store");
    }
    chain.doFilter(request, response);
  }
}
