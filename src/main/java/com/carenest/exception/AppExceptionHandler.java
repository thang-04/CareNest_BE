package com.carenest.exception;

import com.carenest.controller.AuthController;
import com.carenest.controller.ManagementController;
import com.carenest.controller.ParentController;
import com.carenest.controller.TeacherController;
import com.carenest.controller.UserController;
import com.carenest.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.carenest.security.AccountLockedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Format lỗi {code, message, data} cho các API auth/quản lý tài khoản/trẻ/ảnh.
 * Chỉ áp dụng cho các controller dưới đây (ưu tiên cao nhất); controller khác vẫn dùng GlobalExceptionHandler.
 * Lỗi trong security filter chain (chưa tới controller) được ghi trực tiếp qua handleSecurityException.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AuthController.class, ManagementController.class, ParentController.class,
        TeacherController.class, UserController.class})
public class AppExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        return build(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.MALFORMED_REQUEST);
    }

    // For example a form-urlencoded body sent to an endpoint that expects multipart/form-data.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.IMAGE_TOO_LARGE);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleMultipart(Exception ex) {
        return build(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.DATA_CONFLICT);
    }

    // ---- Security ----

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return build(ErrorCode.INVALID_CREDENTIALS);
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ApiResponse<Void>> handleDisabled(AuthenticationException ex) {
        return build(ErrorCode.ACCOUNT_DISABLED);
    }

    @ExceptionHandler(InvalidBearerTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidBearerTokenException ex) {
        if (ex.getCause() instanceof AccountLockedException) {
            return build(ErrorCode.ACCOUNT_DISABLED);
        }
        return build(ErrorCode.INVALID_TOKEN);
    }

    // Thrown when loading the user fails for a technical reason (e.g. database down), not a login error.
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInternalAuth(InternalAuthenticationServiceException ex) {
        log.error("Authentication service error", ex);
        return build(ErrorCode.UNCATEGORIZED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.ACCESS_DENIED);
    }

    /**
     * Dùng cho lỗi xảy ra trong security filter chain, khi chưa xác định được controller.
     */
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(RuntimeException ex) {
        if (ex instanceof InvalidBearerTokenException invalidToken) {
            return handleInvalidToken(invalidToken);
        }
        if (ex instanceof AccessDeniedException accessDenied) {
            return handleAccessDenied(accessDenied);
        }
        return handleAuthentication((AuthenticationException) ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUncategorized(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.UNCATEGORIZED);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode) {
        return build(errorCode, errorCode.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, null));
    }
}
