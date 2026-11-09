package com.runmap.api.global.web;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;

/**
 * IP당 분당 한도 (API 명세 §2.9). IP는 메모리 카운터에만 있고 로그에 남기지 않는다.
 *
 * <p>ponytail: 1분 고정 창 + 메모리. 서버를 2대 이상으로 늘리면 공유 저장소로 옮긴다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(name = "runveal.rate-limit.enabled", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

  static final Map<String, Integer> LIMITS =
      Map.of("/api/v1/matches", 30, "/api/v1/routes", 10, "/api/v1/meta", 60);

  private final HandlerExceptionResolver resolver;
  private volatile long minute = -1;
  private volatile Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

  public RateLimitFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !LIMITS.containsKey(pathOf(request));
  }

  // 디코딩된 경로 (/api/v1/%72outes 도 /api/v1/routes 로 센다)
  private static String pathOf(HttpServletRequest request) {
    return UrlPathHelper.defaultInstance.getLookupPathForRequest(request);
  }

  /**
   * 내부 프록시(Caddy, 같은 Docker 네트워크)에서 온 요청만 X-Forwarded-For 의 마지막 값(프록시가 붙인 실제 IP)을 믿는다. Forwarded ·
   * X-Forwarded-Prefix 는 보지 않는다.
   */
  static String clientIp(HttpServletRequest request) {
    String remote = request.getRemoteAddr();
    String xff = request.getHeader("X-Forwarded-For");
    if (xff == null || !isInternal(remote)) {
      return remote;
    }
    String[] hops = xff.split(",");
    return hops[hops.length - 1].trim();
  }

  private static boolean isInternal(String ip) {
    try {
      InetAddress a = InetAddress.getByName(ip); // remoteAddr 는 IP 리터럴이라 DNS 조회 없음
      return a.isLoopbackAddress() || a.isSiteLocalAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long now = System.currentTimeMillis();
    String path = pathOf(request);
    int count =
        window(now / 60_000)
            .computeIfAbsent(clientIp(request) + " " + path, k -> new AtomicInteger())
            .incrementAndGet();
    if (count > LIMITS.get(path)) {
      int retryAfter = (int) (60 - (now / 1000) % 60);
      resolver.resolveException(
          request,
          response,
          null,
          new BusinessException(ErrorCode.RATE_LIMITED).retryAfter(retryAfter));
      return;
    }
    chain.doFilter(request, response);
  }

  // 분이 바뀌면 카운터를 통째로 버린다 (메모리가 쌓이지 않음)
  private Map<String, AtomicInteger> window(long current) {
    if (current != minute) {
      synchronized (this) {
        if (current != minute) {
          counts = new ConcurrentHashMap<>();
          minute = current;
        }
      }
    }
    return counts;
  }
}
