-- ============================================================
-- Migration: Thêm cột video_url vào bảng training_sessions
-- Mô tả: Admin cập nhật link YouTube sau khi buổi học kết thúc (COMPLETED)
-- API mới: GET /api/v1/training-sessions/completed
-- Ngày tạo: 2026-06-08
-- ============================================================

-- Thêm cột video_url vào bảng training_sessions
-- (IF NOT EXISTS để an toàn khi chạy lại nhiều lần)
ALTER TABLE training_sessions
    ADD COLUMN IF NOT EXISTS video_url TEXT;

-- Index tùy chọn: Tăng tốc query lấy các buổi đào tạo COMPLETED
-- (Đã có index trên 'status' nếu dùng findByStatusOrderByStartTimeDesc)
CREATE INDEX IF NOT EXISTS idx_training_sessions_status_start_time
    ON training_sessions (status, start_time DESC);

-- Xác nhận cột đã được thêm
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'training_sessions'
  AND column_name = 'video_url';
