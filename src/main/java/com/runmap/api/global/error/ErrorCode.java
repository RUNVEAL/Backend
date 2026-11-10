package com.runmap.api.global.error;

import org.springframework.http.HttpStatus;

/**
 * 에러의 의미(코드) · 메시지 · HTTP 상태를 한 곳에서 관리한다. 한 번 배포된 코드값의 의미는 바꾸지 않는다(API 계약).
 *
 * <p>지도 API(/routes · /match · /meta)는 API 명세 §2.8의 문자열 코드를 쓴다. 메시지는 개발자용 영어 한 줄이며 입력값(좌표 등)을 절대 담지
 * 않는다.
 */
public enum ErrorCode {

  // ===== 지도 API (API 명세 §2.8) =====
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "invalid request"),
  MAP_VERSION_OUTDATED(HttpStatus.CONFLICT, "MAP_VERSION_OUTDATED", "mapVersion not supported"),
  PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "request body too large"),
  TOO_MANY_POINTS(HttpStatus.CONTENT_TOO_LARGE, "TOO_MANY_POINTS", "too many points"),
  TOO_MANY_SEGMENTS(HttpStatus.CONTENT_TOO_LARGE, "TOO_MANY_SEGMENTS", "too many segment ids"),
  OUT_OF_SERVICE_AREA(
      HttpStatus.UNPROCESSABLE_CONTENT, "OUT_OF_SERVICE_AREA", "outside of service area"),
  MATCH_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "MATCH_FAILED", "could not match to roads"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "rate limit exceeded"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal error"),
  SERVER_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "SERVER_BUSY", "no execution slot available"),
  ENGINE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_UNAVAILABLE", "engine unavailable"),

  // ===== 공통 (C) =====
  INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "허용되지 않은 요청입니다."),
  ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "요청한 리소스를 찾을 수 없습니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 오류가 발생했습니다."),

  // ===== 인증/인가 (A) =====
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),

  // ===== 사용자 (U) =====
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),

  // ===== 러닝코스 (CO) =====
  COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "CO001", "러닝코스를 찾을 수 없습니다."),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;

  ErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
