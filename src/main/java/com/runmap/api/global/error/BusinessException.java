package com.runmap.api.global.error;

/**
 * 의도적으로 던지는 비즈니스 예외.
 * 의미는 {@link ErrorCode}가 담고, 운반은 이 한 클래스가 맡는다.
 * 도메인마다 예외 클래스를 새로 만들지 않는다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
