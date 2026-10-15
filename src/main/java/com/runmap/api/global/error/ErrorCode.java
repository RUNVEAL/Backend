package com.runmap.api.global.error;

import org.springframework.http.HttpStatus;

/**
 * 에러의 의미(코드) · 사용자 메시지 · HTTP 상태를 한 곳에서 관리한다.
 * 코드는 "도메인 접두어(영문 1~2자) + 3자리 숫자" 형식이며,
 * 한 번 배포된 코드값의 의미는 바꾸지 않는다(API 계약).
 */
public enum ErrorCode {

    // ===== 공통 (C) =====
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST,             "C001", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED,       "C002", "허용되지 않은 요청입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND,                  "C003", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 오류가 발생했습니다."),

    // ===== 인증/인가 (A) =====
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,                   "A001", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN,                     "A002", "접근 권한이 없습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED,                  "A003", "만료된 토큰입니다."),

    // ===== 사용자 (U) =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND,                    "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT,                    "U002", "이미 사용 중인 이메일입니다."),

    // ===== 러닝코스 (CO) =====
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND,                  "CO001", "러닝코스를 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode()       { return code; }
    public String getMessage()    { return message; }
}
