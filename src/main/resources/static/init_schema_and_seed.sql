-- ============================================================
-- SQL DDL Script for KPI System (PostgreSQL)
-- This script defines new tables and modifies existing tables
-- ============================================================

-- 1. Modify Feedbacks Table (If already exists, add columns)
ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS rating INT DEFAULT 5;
ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS admin_reply TEXT;
ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS resolved_by BIGINT REFERENCES users(id);

-- 2. Create Deals Table (Chốt Căn)
CREATE TABLE IF NOT EXISTS deals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    project_name VARCHAR(255) NOT NULL,
    unit VARCHAR(100) NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    commission DOUBLE PRECISION,
    customer_name VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by BIGINT REFERENCES users(id),
    kpi_triggered INT DEFAULT 100,
    contract_photo_url VARCHAR(500)
);

-- 3. Create Social Posts Table (Bài Đăng MXH)
CREATE TABLE IF NOT EXISTS social_posts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    link VARCHAR(500) NOT NULL,
    caption TEXT,
    screenshot_url VARCHAR(500),
    status VARCHAR(50) DEFAULT 'PENDING',
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by BIGINT REFERENCES users(id)
);

-- 4. Create Field Battles Table (Thực Chiến / Gặp Khách)
CREATE TABLE IF NOT EXISTS field_battles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    customer_name VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(20),
    project VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    photo_url VARCHAR(500),
    status VARCHAR(50) DEFAULT 'PENDING',
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by BIGINT REFERENCES users(id)
);

-- 5. Create Training Sessions Table (Buổi Đào Tạo)
CREATE TABLE IF NOT EXISTS training_sessions (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    presenter VARCHAR(255),
    room_code VARCHAR(100) UNIQUE,
    start_time TIMESTAMP WITH TIME ZONE,
    location VARCHAR(255),
    max_slots INT DEFAULT 20,
    status VARCHAR(50) DEFAULT 'UPCOMING',
    photo_url VARCHAR(500)
);

-- 6. Create Training Attendees Table (Điểm Danh Đào Tạo)
CREATE TABLE IF NOT EXISTS training_attendees (
    session_id BIGINT REFERENCES training_sessions(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    attended_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, user_id)
);

-- 7. Create KPI Scores Table (Điểm KPI Tháng)
CREATE TABLE IF NOT EXISTS kpi_scores (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    month VARCHAR(7) NOT NULL,
    attendance INT DEFAULT 0,
    meeting INT DEFAULT 0,
    post INT DEFAULT 0,
    deal INT DEFAULT 0,
    total INT DEFAULT 0,
    is_flagged BOOLEAN DEFAULT FALSE,
    UNIQUE (user_id, month)
);
