package com.runmap.api.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 예외를 한 곳에서 ProblemDetail(RFC 9457)로 변환한다.
 * 비즈니스 코드는 ProblemDetail의 "code" 확장 필드에 실어 앱 분기에 쓰게 한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1) 우리가 의도적으로 던진 비즈니스 예외
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("[BusinessException] {} - {}", code.getCode(), e.getMessage());
        return toProblem(code);
    }

    // 2) @Valid 검증 실패 (어떤 필드가 왜 틀렸는지 함께 내려줌)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldError(
                        err.getField(),
                        String.valueOf(err.getRejectedValue()),
                        err.getDefaultMessage()))
                .toList();
        ProblemDetail problem = toProblem(ErrorCode.INVALID_INPUT_VALUE);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    // 3) 예상 못한 모든 예외 (마지막 안전망)
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("[UnexpectedException]", e);   // 스택트레이스는 서버 로그에만 남긴다
        return toProblem(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // ErrorCode를 ProblemDetail로 변환 (status/message + 비즈니스 code 확장 필드)
    private ProblemDetail toProblem(ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), code.getMessage());
        problem.setProperty("code", code.getCode());   // 앱이 분기에 쓸 비즈니스 코드
        return problem;
    }

    // 검증 실패 응답의 fieldErrors에 담기는 항목
    public record FieldError(String field, String value, String reason) {}
}
