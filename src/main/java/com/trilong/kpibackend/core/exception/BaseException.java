package com.trilong.kpibackend.core.exception;

/**
 * Exception gốc của ứng dụng — extend từ RuntimeException để không cần try/catch bắt buộc.
 * Tất cả custom exception nên kế thừa từ class này.
 */
public class BaseException extends RuntimeException {

    public BaseException(String message) {
        super(message);
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
