-- ============================================================
-- Script tạo dữ liệu mẫu để test hệ thống KPI Trí Long
-- Chạy file này sau khi Spring Boot khởi động lần đầu
-- (Hibernate tự tạo bảng với ddl-auto=update)
-- ============================================================

-- 1. Tạo phòng ban mẫu (tọa độ văn phòng Hà Nội)
INSERT INTO departments (name, office_lat, office_lng, allowed_radius)
VALUES
    ('Phòng Kinh Doanh 1', 21.028511, 105.804817, 100),
    ('Phòng Kinh Doanh 2', 21.028511, 105.804817, 100),
    ('Phòng Hành Chính - HR', 21.028511, 105.804817, 100)
ON CONFLICT DO NOTHING;

-- 2. Tạo tài khoản mẫu (mật khẩu "123456" đã được hash bằng BCrypt)
-- BCrypt hash của "123456": $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
INSERT INTO users (department_id, full_name, phone_number, password_hash, role, status)
VALUES
    -- Admin / HR
    (3, 'Nguyễn Thị Admin', '0900000001',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'ADMIN', 'ACTIVE'),

    -- Trưởng phòng kinh doanh
    (1, 'Trần Văn Trưởng Phòng', '0900000002',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'TRUONG_PHONG', 'ACTIVE'),

    -- Nhân viên Sales 1
    (1, 'Lê Thị Sale A', '0900000003',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'SALE', 'ACTIVE'),

    -- Nhân viên Sales 2
    (2, 'Phạm Văn Sale B', '0900000004',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'SALE', 'ACTIVE'),

    -- Nhân viên văn phòng
    (3, 'Hoàng Thị Văn Phòng', '0900000005',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'VAN_PHONG', 'ACTIVE')
ON CONFLICT (phone_number) DO NOTHING;

-- Xem lại dữ liệu vừa tạo
SELECT u.id, u.full_name, u.phone_number, u.role, u.status, d.name AS department
FROM users u
LEFT JOIN departments d ON u.department_id = d.id
ORDER BY u.id;
