package com.runmap.api.global.web;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 요청 본문 상한 256KB (API 명세 §2.3). 길이를 모르는(chunked) 본문도 상한+1 바이트까지만 읽고 판정한다.
 *
 * <p>ponytail: gzip 요청은 1차 미지원(D-e) — 최악 본문(구간 ID 1만 개 ≈ 170KB)도 상한 안.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BodyLimitFilter extends OncePerRequestFilter {

  public static final int MAX_BYTES = 262_144;

  private final HandlerExceptionResolver resolver;

  public BodyLimitFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BYTES) {
      tooLarge(request, response);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
    if (body.length > MAX_BYTES) {
      tooLarge(request, response);
      return;
    }
    chain.doFilter(new CachedBody(request, body), response);
  }

  private void tooLarge(HttpServletRequest request, HttpServletResponse response) {
    resolver.resolveException(
        request, response, null, new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE));
  }

  /** 이미 읽은 본문을 다시 읽게 한다. */
  private static class CachedBody extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBody(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
          return in.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
          return in.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }
  }
}
