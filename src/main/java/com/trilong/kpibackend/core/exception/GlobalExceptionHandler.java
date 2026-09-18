package com.trilong.kpibackend.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler — bắt tất cả exception chưa được xử lý
 * và trả về JSON chuẩn thay vì HTML error page.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bắt lỗi validation (@NotNull, @NotBlank,...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    // File tải lên vượt trần multipart (15 MB). Không bắt riêng thì rơi vào
    // "Lỗi hệ thống" chung chung, người dùng không biết là do ảnh quá to.
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileQuaTo(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("FILE_TOO_LARGE",
                        "Ảnh quá lớn (tối đa 15 MB). Chụp lại ảnh nhỏ hơn hoặc nén trước khi tải."));
    }

    // Bắt RuntimeException (bao gồm lỗi login sai mật khẩu,...)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERROR", ex.getMessage()));
    }

    // Bắt tất cả exception không xác định
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SERVER_ERROR",
                        "Lỗi hệ thống. Vui lòng thử lại sau."));
    }
}
