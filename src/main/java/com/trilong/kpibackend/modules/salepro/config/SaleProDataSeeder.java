package com.trilong.kpibackend.modules.salepro.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SaleProDataSeeder implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Chỉ chạy nếu bảng projects tồn tại và trống
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM salepro.projects", Integer.class);
            if (count != null && count == 0) {
                System.out.println("Seeding SalePro data...");
                seedData();
                System.out.println("SalePro data seeded successfully.");
            }
        } catch (Exception e) {
            // Bảng chưa tồn tại hoặc lỗi khác, bỏ qua
            System.out.println("SalePro schema/tables might not exist yet or error checking count: " + e.getMessage());
        }
    }

    private void seedData() {
        String sql = """
            -- Reset lại các chuỗi ID tự tăng
            ALTER SEQUENCE salepro.projects_id_seq RESTART WITH 1;
            ALTER SEQUENCE salepro.buildings_id_seq RESTART WITH 1;
            ALTER SEQUENCE salepro.apartments_id_seq RESTART WITH 1;
            
            INSERT INTO salepro.projects (name, project_type, status, details) 
            VALUES (
                'LUMIÈRE HANOI SEASONS GARDEN', 
                'CAO_TANG', 
                'DANG_BAN', 
                '{"overview": "Theo dõi thông tin chi tiết và bảng giá, quỹ căn, mặt bằng dự án LUMIÈRE HANOI SEASONS GARDEN.", "locationMap": "Trục đường Nguyễn Trãi - Hướng đi Vành Đai 3", "trainingMaterials": ["link-slide-dao-tao.pdf", "video-training-lumiere.mp4"], "images360": ["link-anh-360-view-1.jpg"], "documents": ["hop-dong-mau.pdf", "chinh-sach-thang-6.pdf"]}'::jsonb
            );

            INSERT INTO salepro.buildings (project_id, building_name, subdivision_name, total_floors) 
            VALUES 
                (1, 'L1', 'THE BLOOM', 46),
                (1, 'L2', 'THE BLOOM', 43);

            -- Tầng 05
            INSERT INTO salepro.apartments (building_id, apartment_code, thumbnail_url, apartment_type, direction, floor, axis, view_description, status, clearance_area, built_up_area, listed_price, loan_price, early_payment_price, progress_payment_price, supported_banks, sales_policy_applied, gifts_promotions, handover_standard, fund_type) VALUES
            (1, 'L1-05-01', 'thumb_01.jpg', '2BR+', 'TAY_BAC', '05', '01', 'View Đường Nguyễn Trãi', 'DA_BAN', 99.8, 110.7, 14.87, 14.87, 13.52, 14.66, 'TCB, BIDV', 'CSBH Tháng 6', 'Miễn phí quản lý 36 tháng', 'Cao cấp', 'HĐMB'),
            (1, 'L1-05-02', 'thumb_02.jpg', '2BR', 'TAY_BAC', '05', '02', 'View Đường Nguyễn Trãi', 'QUY_DOC_QUYEN', 76.8, 83.3, 11.20, 11.20, 10.50, 11.00, 'TCB', 'CSBH Tháng 6', 'Voucher VinFast', 'Cao cấp', 'HĐMB'),
            (1, 'L1-05-03', 'thumb_03.jpg', '2BR', 'TAY_BAC', '05', '03', 'View Đường Nguyễn Trãi', 'CON_HANG', 86.9, 95.8, 12.50, 12.50, 11.80, 12.30, 'TCB, VCB', 'CSBH Tháng 6', 'Gói nội thất 100tr', 'Cao cấp', 'Sơ cấp'),
            (1, 'L1-05-05A', 'thumb_05A.jpg', '2BR', 'TAY_BAC', '05', '05A', 'View Nội Khu', 'CON_HANG', 84.6, 91.9, 12.10, 12.10, 11.50, 11.90, 'TCB', 'CSBH Tháng 6', 'Miễn phí quản lý 36 tháng', 'Cao cấp', 'Sơ cấp'),
            (1, 'L1-05-08A', 'thumb_08A.jpg', '2BR+', 'DONG_BAC', '05', '08A', 'View Nội Khu', 'CON_HANG', 113.3, 125.4, 16.50, 16.50, 15.20, 16.10, 'TCB, BIDV', 'CSBH Tháng 6', 'Gói smart home', 'Cao cấp', 'Sơ cấp');

            -- Tầng 06
            INSERT INTO salepro.apartments (building_id, apartment_code, thumbnail_url, apartment_type, direction, floor, axis, view_description, status, clearance_area, built_up_area, listed_price, loan_price, early_payment_price, progress_payment_price, supported_banks, sales_policy_applied, gifts_promotions, handover_standard, fund_type) VALUES
            (1, 'L1-06-01', 'thumb_01.jpg', '2BR+', 'TAY_BAC', '06', '01', 'View Đường Nguyễn Trãi', 'CON_HANG', 99.8, 110.7, 14.95, 14.95, 13.60, 14.75, 'TCB, BIDV', 'CSBH Tháng 6', 'Miễn phí quản lý 36 tháng', 'Cao cấp', 'HĐMB'),
            (1, 'L1-06-02', 'thumb_02.jpg', '2BR', 'TAY_BAC', '06', '02', 'View Đường Nguyễn Trãi', 'CON_HANG', 76.8, 83.3, 11.35, 11.35, 10.65, 11.15, 'TCB', 'CSBH Tháng 6', 'Voucher VinFast', 'Cao cấp', 'HĐMB'),
            (1, 'L1-06-03', 'thumb_03.jpg', '2BR', 'TAY_BAC', '06', '03', 'View Đường Nguyễn Trãi', 'DA_BAN', 86.9, 95.8, 12.65, 12.65, 11.95, 12.45, 'TCB, VCB', 'CSBH Tháng 6', 'Gói nội thất 100tr', 'Cao cấp', 'Sơ cấp'),
            (1, 'L1-06-05A', 'thumb_05A.jpg', '2BR', 'TAY_BAC', '06', '05A', 'View Nội Khu', 'CON_HANG', 84.6, 91.9, 12.25, 12.25, 11.65, 12.05, 'TCB', 'CSBH Tháng 6', 'Miễn phí quản lý 36 tháng', 'Cao cấp', 'Sơ cấp'),
            (1, 'L1-06-08A', 'thumb_08A.jpg', '2BR+', 'DONG_BAC', '06', '08A', 'View Nội Khu', 'CON_HANG', 113.3, 125.4, 16.70, 16.70, 15.40, 16.30, 'TCB, BIDV', 'CSBH Tháng 6', 'Gói smart home', 'Cao cấp', 'Sơ cấp');

            -- Tầng 08
            INSERT INTO salepro.apartments (building_id, apartment_code, thumbnail_url, apartment_type, direction, floor, axis, view_description, status, clearance_area, built_up_area, listed_price, loan_price, early_payment_price, progress_payment_price, supported_banks, sales_policy_applied, gifts_promotions, handover_standard, fund_type) VALUES
            (1, 'L1-08-01', 'thumb_01.jpg', '2BR+', 'TAY_BAC', '08', '01', 'View Đường Nguyễn Trãi', 'CON_HANG', 99.8, 110.7, 15.10, 15.10, 13.75, 14.90, 'TCB, BIDV', 'CSBH Tháng 6', 'Miễn phí quản lý 36 tháng', 'Cao cấp', 'HĐMB'),
            (1, 'L1-08-02', 'thumb_02.jpg', '2BR', 'TAY_BAC', '08', '02', 'View Đường Nguyễn Trãi', 'QUY_DOC_QUYEN', 76.8, 83.3, 11.50, 11.50, 10.80, 11.30, 'TCB', 'CSBH Tháng 6', 'Voucher VinFast', 'Cao cấp', 'HĐMB'),
            (1, 'L1-08-03', 'thumb_03.jpg', '2BR', 'TAY_BAC', '08', '03', 'View Đường Nguyễn Trãi', 'CON_HANG', 86.9, 95.8, 12.80, 12.80, 12.10, 12.60, 'TCB, VCB', 'CSBH Tháng 6', 'Gói nội thất 100tr', 'Cao cấp', 'Sơ cấp'),
            (1, 'L1-08-05A', 'thumb_05A.jpg', '2BR', 'TAY_BAC', '08', '05A', 'View Nội Khu', 'DA_BAN', 84.6, 91.9, 12.40, 12.40, 11.80, 12.20, 'TCB', 'CSBH Tháng 6', 'Miễn phí quản lý 36 tháng', 'Cao cấp', 'Sơ cấp'),
            (1, 'L1-08-08A', 'thumb_08A.jpg', '2BR+', 'DONG_BAC', '08', '08A', 'View Nội Khu', 'CON_HANG', 113.3, 125.4, 16.90, 16.90, 15.60, 16.50, 'TCB, BIDV', 'CSBH Tháng 6', 'Gói smart home', 'Cao cấp', 'Sơ cấp');

            -- L2
            INSERT INTO salepro.apartments (building_id, apartment_code, thumbnail_url, apartment_type, direction, floor, axis, view_description, status, clearance_area, built_up_area, listed_price, loan_price, early_payment_price, progress_payment_price, supported_banks, sales_policy_applied, gifts_promotions, handover_standard, fund_type) VALUES
            (2, 'HH-B-ff-02', 'thumb_l2.jpg', '1PN', 'DONG_BAC', '02', '02', 'View Nội Khu', 'CON_HANG', 49.2, 54.1, 7.88, 7.88, 7.10, 7.70, 'TCB', 'CSBH Mới', 'Tặng thẻ gym', 'Hoàn thiện', 'HĐMB'),
            (2, 'HH-B-16-05', 'thumb_l2.jpg', '1PN', 'DONG_BAC', '16', '05', 'View Nội Khu', 'CON_HANG', 47.7, 52.5, 8.01, 8.01, 7.25, 7.85, 'TCB', 'CSBH Mới', 'Tặng thẻ gym', 'Hoàn thiện', 'HĐMB'),
            (2, 'HH-B-pp-06', 'thumb_l2.jpg', '1PN', 'DONG_BAC', '06', '06', 'View Hồ bơi', 'DA_BAN', 48.0, 52.8, 8.34, 8.34, 7.50, 8.10, 'TCB', 'CSBH Mới', 'Tặng thẻ gym', 'Hoàn thiện', 'Sơ cấp'),
            (2, 'HH-B-mm-08A', 'thumb_l2.jpg', '1PN', 'DONG_BAC', '08', '08A', 'View Nội Khu', 'CON_HANG', 47.9, 52.7, 7.93, 7.93, 7.15, 7.80, 'TCB', 'CSBH Mới', 'Tặng thẻ gym', 'Hoàn thiện', 'HĐMB'),
            (2, 'HH-B-aa-21', 'thumb_l2.jpg', '1PN', 'TAY_NAM', '21', '21', 'View Cảnh quan', 'QUY_DOC_QUYEN', 48.4, 53.1, 7.66, 8.34, 6.66, 7.66, 'TCB, ACB', 'Hỗ trợ phí quản lý', 'Thẻ sử dụng Clubhouse 24 tháng', 'Cao cấp', 'Sơ cấp'),
            (2, 'HH-B-hh-23', 'thumb_l2.jpg', '1PN', 'TAY_NAM', '23', '23', 'View Cảnh quan', 'CON_HANG', 47.9, 52.7, 8.29, 8.29, 7.45, 8.10, 'TCB, ACB', 'Hỗ trợ phí quản lý', 'Thẻ sử dụng Clubhouse', 'Cao cấp', 'Sơ cấp'),
            (2, 'HH-B-jj-25', 'thumb_l2.jpg', '1PN', 'TAY_NAM', '25', '25', 'View Cảnh quan', 'CON_HANG', 48.5, 53.3, 8.05, 8.05, 7.20, 7.85, 'TCB, ACB', 'Hỗ trợ phí quản lý', 'Thẻ sử dụng Clubhouse', 'Cao cấp', 'Sơ cấp');
        """;
        jdbcTemplate.execute(sql);
    }
}
