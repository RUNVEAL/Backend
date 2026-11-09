package com.runmap.api.global.error;

import com.runmap.api.global.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * 예외를 한 곳에서 ProblemDetail(RFC 9457)로 변환한다. 비즈니스 code 와 message · requestId 를 확장 필드로 싣는다 (API 명세
 * §2.8).
 *
 * <p>좌표 무로그: 예외 메시지와 스택트레이스 전체는 로그에도 응답에도 남기지 않는다. GraphHopper · Jackson 예외 메시지에는 입력 좌표가 들어간다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // Boot 기본 ProblemDetail 핸들러보다 먼저 (명세 형식 + 좌표 무로그)
public class GlobalExceptionHandler {

  private static final Set<String> NON_FINITE = Set.of("NaN", "Infinity", "-Infinity");

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusiness(BusinessException e, HttpServletRequest req) {
    log.warn("[BusinessException] {}", e.getErrorCode().getCode());
    ProblemDetail problem = toProblem(e.getErrorCode(), req);
    e.getProperties().forEach(problem::setProperty);
    ResponseEntity.BodyBuilder res = ResponseEntity.status(problem.getStatus());
    if (e.getRetryAfterSeconds() != null) {
      res.header("Retry-After", e.getRetryAfterSeconds().toString());
    }
    return res.body(problem);
  }

  // 깨진 JSON · 타입 불일치 · 모르는 enum. 원인 메시지에는 입력값이 들어 있으므로 버린다.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleUnreadable(
      HttpMessageNotReadableException e, HttpServletRequest req) {
    String reason = "MALFORMED_JSON";
    String field = null;
    if (e.getCause() instanceof MismatchedInputException mie) {
      field = pathOf(mie.getPath());
      reason = "INVALID_TYPE";
      if (mie instanceof InvalidFormatException ife) {
        if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
          reason = "UNKNOWN_ENUM";
        } else if (NON_FINITE.contains(String.valueOf(ife.getValue()))) {
          reason = "NOT_FINITE";
        }
      }
    }
    BusinessException be = BusinessException.invalid(field, reason);
    return handleBusiness(be, req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest req) {
    // 프레임워크 예외(404 · 405 · 415 …)는 상태를 그대로 둔다
    if (e instanceof ErrorResponse er) {
      ProblemDetail body = er.getBody();
      body.setProperty("requestId", RequestIdFilter.of(req));
      return ResponseEntity.status(er.getStatusCode()).headers(er.getHeaders()).body(body);
    }
    StackTraceElement at = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
    log.error("[UnexpectedException] {} at {}", e.getClass().getName(), at);
    return ResponseEntity.internalServerError().body(toProblem(ErrorCode.INTERNAL_ERROR, req));
  }

  private ProblemDetail toProblem(ErrorCode code, HttpServletRequest req) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), code.getMessage());
    problem.setProperty("code", code.getCode()); // 앱은 이것으로만 분기한다
    problem.setProperty("message", code.getMessage());
    problem.setProperty("requestId", RequestIdFilter.of(req));
    return problem;
  }

  // [{start},{lat}] → "start.lat" · [{points},{3}] → "points[3]"
  private static String pathOf(List<JacksonException.Reference> path) {
    StringBuilder sb = new StringBuilder();
    for (JacksonException.Reference ref : path) {
      if (ref.getPropertyName() != null) {
        if (!sb.isEmpty()) {
          sb.append('.');
        }
        sb.append(ref.getPropertyName());
      } else if (ref.getIndex() >= 0) {
        sb.append('[').append(ref.getIndex()).append(']');
      }
    }
    return sb.isEmpty() ? null : sb.toString();
  }
}
