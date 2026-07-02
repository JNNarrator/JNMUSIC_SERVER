package com.jn.music.common.exception;

import com.jn.music.common.ApiError;
import com.jn.music.common.ApiResponse;
import com.jn.music.common.TraceIdContext;
import com.jn.music.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(HttpServletRequest request, Exception ex) {
        String message = "参数校验失败";
        if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            message = formatFieldErrors(methodArgumentNotValidException.getBindingResult().getFieldErrors());
        } else if (ex instanceof BindException bindException) {
            message = formatFieldErrors(bindException.getFieldErrors());
        }
        log.warn("参数校验失败 traceId={} path={} message={}", TraceIdContext.getTraceId(), request.getRequestURI(), message);
        return buildResponse(ErrorCode.INVALID_PARAMETER, message);
    }

    @ExceptionHandler({IllegalArgumentException.class, BusinessException.class})
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(HttpServletRequest request, Exception ex) {
        if (ex instanceof BusinessException businessException) {
            log.warn("业务异常 traceId={} path={} message={}", TraceIdContext.getTraceId(), request.getRequestURI(), ex.getMessage());
            return buildResponse(businessException.getErrorCode(), ex.getMessage());
        }
        log.warn("参数异常 traceId={} path={} message={}", TraceIdContext.getTraceId(), request.getRequestURI(), ex.getMessage());
        return buildResponse(ErrorCode.INVALID_PARAMETER, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(HttpServletRequest request, Exception ex) {
        log.error("未预期异常 traceId={} path={}", TraceIdContext.getTraceId(), request.getRequestURI(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR, "服务内部错误");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(HttpServletRequest request, ResponseStatusException ex) {
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;
        if (ex.getStatusCode() != null) {
            errorCode = mapStatusCode(ex.getStatusCode().value());
        }
        log.warn("状态异常 traceId={} path={} status={} message={}", TraceIdContext.getTraceId(), request.getRequestURI(), ex.getStatusCode(), ex.getReason());
        return buildResponse(errorCode, ex.getReason() != null ? ex.getReason() : errorCode.getMessage());
    }

    private ErrorCode mapStatusCode(int statusCode) {
        if (statusCode == 401) {
            return ErrorCode.INVALID_PARAMETER;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(ErrorCode errorCode, String message) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .error(ApiError.builder().code(errorCode.name()).message(message).build())
                .traceId(TraceIdContext.getTraceId())
                .build();
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    private String formatFieldErrors(List<FieldError> fieldErrors) {
        if (fieldErrors.isEmpty()) {
            return "参数校验失败";
        }
        return fieldErrors.stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }
}
