package com.carenest.exception;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Nơi duy nhất chuyển exception thành ResponseJson. Exception mới phải được map ở đây,
 * nếu không sẽ rơi vào 500; desc trả client không chứa thông tin nội bộ.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST_DATA = "Invalid request data";

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<ResponseJson<Void>> handleGlobalException(GlobalException ex) {
        if (ex.getApiCode().getHttpStatus().is5xxServerError()) {
            log.error("[handleGlobalException]|code={}|desc={}", ex.getApiCode().getCode(), ex.getMessage(), ex);
        } else {
            log.warn("[handleGlobalException]|code={}|desc={}", ex.getApiCode().getCode(), ex.getMessage());
        }
        return ResponseJson.toJson(ex.getApiCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseJson<List<FieldErrorResponse>>> handleBodyValidation(
            MethodArgumentNotValidException ex) {
        List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseJson.toJsonWithData(ApiCode.BAD_REQUEST, INVALID_REQUEST_DATA, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseJson<List<FieldErrorResponse>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        List<FieldErrorResponse> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return ResponseJson.toJsonWithData(ApiCode.BAD_REQUEST, INVALID_REQUEST_DATA, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ResponseJson<List<FieldErrorResponse>>> handleMethodValidation(
            HandlerMethodValidationException ex) {
        List<FieldErrorResponse> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return ResponseJson.toJsonWithData(ApiCode.BAD_REQUEST, INVALID_REQUEST_DATA, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ResponseJson<Void>> handleMalformedRequest(Exception ex) {
        log.warn("[handleMalformedRequest]|type={}", ex.getClass().getSimpleName());
        return ResponseJson.toJson(ApiCode.BAD_REQUEST, "Malformed request");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ResponseJson<Void>> handleNotFound(Exception ex) {
        return ResponseJson.toJson(ApiCode.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseJson<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseJson.toJson(ApiCode.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResponseJson<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseJson.toJson(ApiCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseJson<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseJson.toJson(ApiCode.PAYLOAD_TOO_LARGE, "File too large");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseJson<Void>> handleUnexpected(Exception ex) {
        log.error("[handleUnexpected]|type={}", ex.getClass().getSimpleName(), ex);
        return ResponseJson.toJson(ApiCode.INTERNAL_ERROR, "Internal server error");
    }
}
