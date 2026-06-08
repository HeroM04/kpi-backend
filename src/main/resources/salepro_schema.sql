-- 1. Tạo Schema riêng cho phân hệ SalePro
CREATE SCHEMA IF NOT EXISTS salepro;

-- 2. Bảng Quản lý Dự án (Sử dụng JSONB cho các thông tin động linh hoạt)
CREATE TABLE IF NOT EXISTS salepro.projects (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    project_type VARCHAR(50), -- CAO_TANG, THAP_TANG
    status VARCHAR(50),       -- SAP_MO_BAN, DANG_BAN, DA_HET_HANG
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    details JSONB             -- Lưu: Tổng quan, Vị trí (map), Đào tạo, Ảnh 360, Tiến độ, Tài liệu
);

-- 3. Bảng Quản lý Tòa nhà / Phân khu
CREATE TABLE IF NOT EXISTS salepro.buildings (
    id SERIAL PRIMARY KEY,
    project_id INT REFERENCES salepro.projects(id) ON DELETE CASCADE,
    building_name VARCHAR(100) NOT NULL, -- L1, L2...
    subdivision_name VARCHAR(100),       -- Phân khu: THE BLOOM, IVY PARK...
    total_floors INT
);

-- 4. Bảng Quản lý Căn hộ (Chi tiết phục vụ ma trận bảng hàng và so sánh)
CREATE TABLE IF NOT EXISTS salepro.apartments (
    id SERIAL PRIMARY KEY,
    building_id INT REFERENCES salepro.buildings(id) ON DELETE CASCADE,
    apartment_code VARCHAR(50) NOT NULL UNIQUE, -- VX4-MIN, HH-B-aa-21
    thumbnail_url VARCHAR(500),
    apartment_type VARCHAR(50),                 -- 1PN, 2PN, LIEN_KE...
    direction VARCHAR(50),                      -- TAY_BAC, DONG_BAC...
    floor VARCHAR(10),                          -- Tầng: aa, 05, 06...
    axis VARCHAR(10),                           -- Trục căn: 01, 02, 05A...
    view_description TEXT,                      -- View: Nội khu, Đường Nguyễn Trãi...
    status VARCHAR(50) DEFAULT 'CON_HANG',      -- CON_HANG, QUY_DOC_QUYEN, DA_BAN
    
    -- Nhóm Diện tích (đơn vị: m2)
    clearance_area NUMERIC(10, 2),              -- DT Thông thủy
    built_up_area NUMERIC(10, 2),               -- DT Tim tường / Xây dựng
    land_area NUMERIC(10, 2),                   -- DT Đất (thấp tầng)
    construction_area NUMERIC(10, 2),           -- DT Xây dựng chi tiết
    
    -- Nhóm Giá bán (đơn vị: tỷ VNĐ)
    listed_price NUMERIC(12, 2),                -- Giá niêm yết
    loan_price NUMERIC(12, 2),                  -- Giá vay
    early_payment_price NUMERIC(12, 2),         -- Giá thanh toán sớm
    progress_payment_price NUMERIC(12, 2),      -- Giá thanh toán tiến độ
    
    -- Nhóm Tài chính & Chính sách
    supported_banks VARCHAR(255),               -- BIDV, TCB, ACB...
    sales_policy_applied TEXT,                  -- Chính sách áp dụng
    gifts_promotions TEXT,                      -- Quà tặng đi kèm
    
    -- Nhóm Bàn giao & Pháp lý
    handover_standard VARCHAR(100),             -- Tiêu chuẩn bàn giao: Thô, Cao cấp...
    fund_type VARCHAR(100),                     -- Loại quỹ: HĐMB-VHM, Sơ cấp, Thứ cấp
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
