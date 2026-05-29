package com.trilong.kpibackend.core.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cấu trúc chuẩn cho mọi response lỗi trả về client.
 * { "status": "ERROR", "message": "..." }
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
    private String status;
    private String message;
}
