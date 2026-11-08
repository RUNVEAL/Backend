package com.runmap.api.global.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 의도적으로 던지는 비즈니스 예외. 의미는 {@link ErrorCode}가 담고, 운반은 이 한 클래스가 맡는다. 도메인마다 예외 클래스를 새로 만들지 않는다.
 *
 * <p>응답에 더할 속성(field · reason · supportedMapVersions 등)은 {@link #with}로 싣는다. 값에 사용자 입력을 넣지 않는다.
 */
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, Object> properties = new LinkedHashMap<>();
  private Integer retryAfterSeconds;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  /** 400 INVALID_REQUEST. field = JSON 경로(예: start.lat, points[3]), reason = API 명세 §2.8 reason. */
  public static BusinessException invalid(String field, String reason) {
    return new BusinessException(ErrorCode.INVALID_REQUEST)
        .with("field", field)
        .with("reason", reason);
  }

  public BusinessException with(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public BusinessException retryAfter(int seconds) {
    this.retryAfterSeconds = seconds;
    return this;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }

  public Integer getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
