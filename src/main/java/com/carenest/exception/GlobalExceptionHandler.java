package com.carenest.exception;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<ResponseJson<List<FieldErrorResponse>>> handleGlobalException(GlobalException ex) {
        if (ex.getApiCode().getHttpStatus().is5xxServerError()) {
            log.error(
                    "[handleGlobalException]|code={}|errorCode={}|desc={}",
                    ex.getApiCode().getCode(),
                    ex.getApiCode().getErrorCode(),
                    ex.getMessage(),
                    ex);
        } else {
            // Chỉ log tên ô, không log câu lỗi theo ô (có thể nhắc lại dữ liệu người dùng nhập)
            log.warn(
                    "[handleGlobalException]|code={}|errorCode={}|desc={}|fields={}",
                    ex.getApiCode().getCode(),
                    ex.getApiCode().getErrorCode(),
                    ex.getMessage(),
                    ex.getFieldErrors().stream().map(FieldErrorResponse::field).toList());
        }
        // Lỗi theo ô (C-5): data = [{ field, message }]; lỗi khác data = null như trước
        List<FieldErrorResponse> fields = ex.getFieldErrors().isEmpty() ? null : ex.getFieldErrors();
        return ResponseJson.toJsonWithData(ex.getApiCode(), ex.getMessage(), fields);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseJson<List<FieldErrorResponse>>> handleBodyValidation(
            MethodArgumentNotValidException ex) {
        List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        return invalid(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseJson<List<FieldErrorResponse>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        List<FieldErrorResponse> errors = ex.getConstraintViolations().stream()
                .map(violation ->
                        new FieldErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return invalid(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ResponseJson<List<FieldErrorResponse>>> handleMethodValidation(
            HandlerMethodValidationException ex) {
        List<FieldErrorResponse> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return invalid(errors);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ResponseJson<Void>> handleMalformedRequest(Exception ex) {
        log.warn("[handleMalformedRequest]|type={}", ex.getClass().getSimpleName());
        return error(ApiCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ResponseJson<Void>> handleNotFound(Exception ex) {
        return error(ApiCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseJson<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return error(ApiCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResponseJson<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(ApiCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseJson<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return error(ApiCode.FILE_TOO_LARGE);
    }

    // @PreAuthorize từ chối bên trong DispatcherServlet nên tới đây, không qua RestAccessDeniedHandler
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseJson<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[handleAccessDenied]|type={}", ex.getClass().getSimpleName());
        return error(ApiCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseJson<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("[handleAuthentication]|type={}", ex.getClass().getSimpleName());
        return error(ApiCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseJson<Void>> handleUnexpected(Exception ex) {
        log.error("[handleUnexpected]|type={}", ex.getClass().getSimpleName(), ex);
        return error(ApiCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ResponseJson<Void>> error(ApiCode apiCode) {
        return ResponseJson.toJson(apiCode, apiCode.getDefaultDesc());
    }

    private static ResponseEntity<ResponseJson<List<FieldErrorResponse>>> invalid(List<FieldErrorResponse> errors) {
        return ResponseJson.toJsonWithData(
                ApiCode.INVALID_REQUEST_DATA, ApiCode.INVALID_REQUEST_DATA.getDefaultDesc(), errors);
    }
}
