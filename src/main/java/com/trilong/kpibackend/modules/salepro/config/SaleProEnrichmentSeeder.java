package com.trilong.kpibackend.modules.salepro.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeder bổ sung (chạy SAU SaleProDataSeeder) — đổ dữ liệu cho các bảng/cột mới và phủ 100% case:
 * chuyên viên, mặt bằng tầng, hỏi đáp, tiến độ, tài liệu, sự kiện, tin tức, và 1 dự án thấp tầng.
 * Mỗi block guard riêng (chỉ chạy khi trống / cột NULL) nên an toàn idempotent trên DB đã có data (Neon).
 */
@Component
@Order(100)
public class SaleProEnrichmentSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public SaleProEnrichmentSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // KHÔNG dùng @Transactional: mỗi block chạy autocommit độc lập, 1 block lỗi không kéo đổ block khác.
    @Override
    public void run(String... args) {
        runQuietly("sales_agents", this::seedAgents);
        runQuietly("link managing agent", this::linkManagingAgent);
        runQuietly("backfill buildings", this::backfillBuildings);
        runQuietly("backfill apartments", this::backfillApartments);
        runQuietly("enrich project details", this::enrichProjectDetails);
        runQuietly("overview landing", this::seedOverviewLanding);
        runQuietly("hot flags + rich content", this::seedHotAndRichContent);
        runQuietly("building_floor_plans", this::seedFloorPlans);
        runQuietly("apartment_questions", this::seedQuestions);
        runQuietly("project_progress", this::seedProgress);
        runQuietly("project_documents", this::seedDocuments);
        runQuietly("events", this::seedEvents);
        runQuietly("news", this::seedNews);
        runQuietly("low-rise project", this::seedLowRiseProject);
    }

    private void runQuietly(String name, Runnable block) {
        try {
            block.run();
        } catch (Exception e) {
            System.out.println("[SaleProEnrichmentSeeder] bỏ qua block '" + name + "': " + e.getMessage());
        }
    }

    private boolean isEmpty(String table) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return c == null || c == 0;
    }

    // ============ 1. Chuyên viên ============
    private void seedAgents() {
        if (!isEmpty("salepro.sales_agents")) return;
        jdbc.execute("""
            INSERT INTO salepro.sales_agents (full_name, title, phone, email, avatar_url, zalo_link, created_at, updated_at) VALUES
            ('Dương Hồng Hạnh', 'MC - Quản lý quỹ căn', '0901234567', 'hanh.duong@trilongland.vn', 'https://i.pravatar.cc/150?img=47', 'https://zalo.me/0901234567', now(), now()),
            ('Nguyễn Minh Tuấn', 'Chuyên viên tư vấn cao cấp', '0907654321', 'tuan.nguyen@trilongland.vn', 'https://i.pravatar.cc/150?img=12', 'https://zalo.me/0907654321', now(), now());
        """);
    }

    private void linkManagingAgent() {
        jdbc.execute("""
            UPDATE salepro.projects
            SET managing_agent_id = (SELECT id FROM salepro.sales_agents ORDER BY id LIMIT 1)
            WHERE managing_agent_id IS NULL
              AND EXISTS (SELECT 1 FROM salepro.sales_agents);
        """);
    }

    // ============ 2. Backfill cột mới của buildings ============
    private void backfillBuildings() {
        jdbc.execute("""
            UPDATE salepro.buildings SET
                ownership_type        = COALESCE(ownership_type, 'Lâu dài'),
                handover_standard     = COALESCE(handover_standard, 'Cao cấp'),
                total_area            = COALESCE(total_area, 82820.00),
                total_apartments      = COALESCE(total_apartments, CASE building_name WHEN 'L1' THEN 1000 ELSE 850 END),
                elevator_count        = COALESCE(elevator_count, 8),
                construction_progress = COALESCE(construction_progress, 33),
                image_url             = COALESCE(image_url, 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?q=80&w=1200'),
                description           = COALESCE(description, 'Tòa căn hộ cao cấp thuộc phân khu THE BLOOM, thiết kế tinh tế tận hưởng ánh sáng và không gian xanh.'),
                sales_policy          = COALESCE(sales_policy, 'Miễn phí phí quản lý 24 tháng. Chiết khấu thanh toán sớm tới 9%. Hỗ trợ vay 70% trong 35 năm.'),
                marker_lat            = COALESCE(marker_lat, CASE building_name WHEN 'L1' THEN 20.99320 ELSE 20.99250 END),
                marker_lng            = COALESCE(marker_lng, CASE building_name WHEN 'L1' THEN 105.80120 ELSE 105.80250 END)
            WHERE project_id = (SELECT id FROM salepro.projects WHERE project_type = 'CAO_TANG' ORDER BY id LIMIT 1);
        """);
    }

    // ============ 3. Backfill apartments ============
    private void backfillApartments() {
        jdbc.execute("""
            UPDATE salepro.apartments
            SET sales_policy_date = COALESCE(sales_policy_date, DATE '2026-05-20')
            WHERE sales_policy_date IS NULL;
        """);
    }

    // ============ 4. Enrich JSONB details của dự án cao tầng ============
    private void enrichProjectDetails() {
        jdbc.execute("""
            UPDATE salepro.projects SET details = COALESCE(details, '{}'::jsonb) || '{
                "developer": "Masterise Homes",
                "address": "Nguyễn Trãi, Thanh Xuân, Hà Nội",
                "totalProjectArea": "82.820 m²",
                "scaleDescription": "10 tòa | 35-46 tầng",
                "constructionDensity": "28.8%",
                "apartmentTypes": "Studio, 1BR, 1BR+, 2BR, 2BR+1MK, 3BR, 3BR+, Duplex, Penthouse",
                "scale": "1080 ha",
                "capital": "2,3 tỷ USD",
                "residents": "135.000 cư dân",
                "overviewBullets": [
                    "Tên dự án: Lumière Hanoi Seasons Garden",
                    "Chủ đầu tư: Masterise Homes",
                    "Vị trí: 233 - 235 Nguyễn Trãi, Thanh Xuân, Hà Nội",
                    "Vốn đầu tư: ~2.3 tỷ USD",
                    "Sản phẩm: Căn hộ cao cấp hạng sang",
                    "Kết nối: Metro Cát Linh - Hà Đông, Vành Đai 3, Vành Đai 2.5"
                ],
                "bannerImageUrl": "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?q=80&w=1600",
                "overviewImageUrl": "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?q=80&w=800",
                "locationDescription": "Tọa lạc tại 233 - 235 Nguyễn Trãi, dự án sở hữu vị trí kim cương ngay nội đô Hà Nội, trên trục huyết mạch kết nối Thanh Xuân, Đống Đa và Hà Đông.",
                "connectionPoints": [
                    {"time": "01", "label": "Ga Cát Linh - Thượng Đình"},
                    {"time": "02", "label": "Vinhomes Royal City"},
                    {"time": "03", "label": "Ngã Tư Sở, Vành Đai 2, 2.5, 3"},
                    {"time": "08", "label": "Hồ Tây"},
                    {"time": "10", "label": "Hồ Hoàn Kiếm"},
                    {"time": "30", "label": "Sân bay Nội Bài"}
                ],
                "mapImageUrl": "https://images.unsplash.com/photo-1524661135-423995f22d0b?q=80&w=1600",
                "mapEmbedUrl": "https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1m2!1s0x3135ac90c642fc01%3A0x6aab5a22f55b8220!2zMjMzIE5ndXnhu4VuIFRyw6Ni!5e0!3m2!1sen!2s!4v1700000000000",
                "latitude": 20.9925,
                "longitude": 105.8012,
                "masterplanImageUrl": "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?q=80&w=1600",
                "trainingVideoUrl": "https://www.youtube.com/embed/dQw4w9WgXcQ",
                "trainingThumbnail": "https://images.unsplash.com/photo-1611162617474-5b21e879e113?q=80&w=1000",
                "salesPolicy": "MIỄN PHÍ PHÍ QUẢN LÝ 24 tháng (khách mới) / 48 tháng (cư dân cũ). Thanh toán tiến độ chuẩn, hỗ trợ vay tới 70%."
            }'::jsonb
            WHERE project_type = 'CAO_TANG' AND (details->>'developer') IS NULL;
        """);
    }

    // ============ 4b. Trang Tổng quan (landing) ============
    private void seedOverviewLanding() {
        jdbc.execute("""
            UPDATE salepro.projects SET details = COALESCE(details, '{}'::jsonb) || '{
                "heroImages": [
                    "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?q=80&w=1600",
                    "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?q=80&w=1600",
                    "https://images.unsplash.com/photo-1582268611958-ebfd161ef9cf?q=80&w=1600"
                ],
                "productCount": "4500 căn",
                "ownership": "Lâu dài",
                "products": [
                    {"name": "Căn hộ 1BR/1BR+1", "areaRange": "50 - 102 m²", "images": ["https://images.unsplash.com/photo-1503387762-592deb58ef4e?q=80&w=600", "https://images.unsplash.com/photo-1497366216548-37526070297c?q=80&w=600"]},
                    {"name": "Căn hộ 2BR/2BR+1", "areaRange": "86 - 152 m²", "images": ["https://images.unsplash.com/photo-1497366811353-6870744d04b2?q=80&w=600", "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?q=80&w=600"]},
                    {"name": "Căn hộ 3BR/3BR+1", "areaRange": "107 - 152 m²", "images": ["https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?q=80&w=600", "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?q=80&w=600"]}
                ],
                "amenities": [
                    {"label": "KHU VUI CHƠI TRẺ EM", "image": "https://images.unsplash.com/photo-1571260899304-425eee4c7efc?q=80&w=600"},
                    {"label": "THƯ VIỆN", "image": "https://images.unsplash.com/photo-1521587760476-6c12a4b040da?q=80&w=600"},
                    {"label": "CẢNH QUAN THÁC NƯỚC", "image": "https://images.unsplash.com/photo-1432405972618-c60b0225b8f9?q=80&w=600"},
                    {"label": "CLUBHOUSE", "image": "https://images.unsplash.com/photo-1540541338287-41700207dee6?q=80&w=600"}
                ],
                "featureTitle": "LUMIÈRE HANOI SEASONS GARDEN: KHI ÁNH SÁNG CHẠM NGÕ, KHI CẢM XÚC NỞ HOA",
                "featureDescription": "Tọa lạc tại vị trí kim cương 233 - 235 Nguyễn Trãi, Hanoi Seasons Garden mang đến chuẩn sống tinh hoa với thiết kế hiện đại, ngập tràn ánh sáng và kết nối thuận tiện tới trung tâm Thủ Đô.",
                "featureVideoUrl": "https://www.youtube.com/embed/dQw4w9WgXcQ",
                "featureImage": "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?q=80&w=1200",
                "masterplanTabs": [
                    {"label": "Mặt bằng tổng thể", "image": "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?q=80&w=1600"},
                    {"label": "MB Điển hình Tòa L1", "image": "https://images.unsplash.com/photo-1503387762-592deb58ef4e?q=80&w=1200"},
                    {"label": "MB Điển hình Tòa L2", "image": "https://images.unsplash.com/photo-1497366216548-37526070297c?q=80&w=1200"}
                ]
            }'::jsonb
            WHERE project_type = 'CAO_TANG' AND (details->>'productCount') IS NULL;
        """);
    }

    // ============ 4c. Cờ HOT + nội dung dài xen ảnh cho tin tức/sự kiện seed ============
    private void seedHotAndRichContent() {
        // Gắn HOT cho mọi dự án chưa có cờ (admin chỉnh lại trong Quản lý Dự án)
        jdbc.execute("""
            UPDATE salepro.projects
            SET details = COALESCE(details, '{}'::jsonb) || '{"isHot": true}'::jsonb
            WHERE (details->>'isHot') IS NULL;
        """);

        // Nội dung dài xen ảnh cho các bài tin tức SEED (không đụng bài admin tự tạo)
        jdbc.execute("""
            UPDATE salepro.news_articles SET content =
              '<p>' || COALESCE(summary, title) || '</p>'
              || '<figure><img src="' || COALESCE(thumbnail, 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?q=80&w=1200') || '" alt=""/><figcaption>' || title || '</figcaption></figure>'
              || '<p>Trong bối cảnh thị trường bất động sản đang có nhiều chuyển biến tích cực, dòng tiền đầu tư ngày càng chọn lọc và hướng tới các dự án sở hữu pháp lý minh bạch, tiến độ đảm bảo cùng tiềm năng khai thác thực.</p>'
              || '<h3>Vị thế và tiềm năng tăng giá</h3>'
              || '<p>Hạ tầng giao thông đồng bộ, tiện ích nội khu hoàn chỉnh và cộng đồng cư dân văn minh là những yếu tố then chốt tạo nên giá trị bền vững của dự án trong dài hạn.</p>'
              || '<figure><img src="https://images.unsplash.com/photo-1503387762-592deb58ef4e?q=80&w=1200" alt=""/><figcaption>Tiến độ xây dựng được đảm bảo từng giai đoạn</figcaption></figure>'
              || '<h3>Chính sách bán hàng hấp dẫn</h3>'
              || '<p>Chủ đầu tư đưa ra nhiều chính sách ưu đãi: hỗ trợ lãi suất, ân hạn nợ gốc, miễn phí quản lý cùng quà tặng giá trị dành cho khách hàng tiên phong.</p>'
              || '<figure><img src="https://images.unsplash.com/photo-1560518883-ce09059eeffa?q=80&w=1200" alt=""/><figcaption>Khách hàng tham quan thực tế dự án</figcaption></figure>'
              || '<p>Giới chuyên gia nhận định đây là thời điểm phù hợp để nhà đầu tư trung và dài hạn cân nhắc xuống tiền, đón đầu chu kỳ tăng trưởng mới của thị trường.</p>'
            WHERE content NOT LIKE '%<figure%';
        """);

        // Nội dung dài xen ảnh cho các sự kiện SEED
        jdbc.execute("""
            UPDATE salepro.events SET description =
              '<p>' || REGEXP_REPLACE(COALESCE(description, title), '<[^>]+>', '', 'g') || '</p>'
              || '<figure><img src="' || COALESCE(banner_image, 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?q=80&w=1200') || '" alt=""/><figcaption>' || title || '</figcaption></figure>'
              || '<p>Sự kiện quy tụ đội ngũ chuyên gia hàng đầu cùng các chuyên viên tư vấn giàu kinh nghiệm, mang đến góc nhìn toàn cảnh về dự án và cơ hội đầu tư.</p>'
              || '<h3>Tại sự kiện, Quý khách hàng sẽ được</h3>'
              || '<p>Khám phá tổng thể dự án qua sa bàn và khu nhà mẫu. Cập nhật chính sách bán hàng và ưu đãi mới nhất. Trực tiếp tư vấn 1-1 cùng chuyên gia. Tham gia bốc thăm trúng thưởng với nhiều phần quà giá trị.</p>'
              || '<figure><img src="https://images.unsplash.com/photo-1511578314322-379afb476865?q=80&w=1200" alt=""/><figcaption>Không gian sự kiện được đầu tư chỉn chu</figcaption></figure>'
              || '<p>Số lượng chỗ ngồi có hạn — Quý khách vui lòng đăng ký sớm với chuyên viên tư vấn để giữ chỗ và nhận bộ tài liệu dự án đầy đủ.</p>'
            WHERE description NOT LIKE '%<figure%';
        """);
    }

    // ============ 5. Mặt bằng tầng (layout tòa) ============
    private void seedFloorPlans() {
        if (!isEmpty("salepro.building_floor_plans")) return;
        jdbc.execute("""
            INSERT INTO salepro.building_floor_plans (building_id, floor_label, image_url, note, sort_order)
            SELECT b.id, v.floor_label, v.image_url, v.note, v.sort_order
            FROM salepro.buildings b
            CROSS JOIN (VALUES
                ('MẶT BẰNG TẦNG ĐIỂN HÌNH (LEVEL 5-8)', 'https://images.unsplash.com/photo-1503387762-592deb58ef4e?q=80&w=1200', 'Tầng điển hình', 1),
                ('MẶT BẰNG TẦNG TRUNG (LEVEL 9-20)', 'https://images.unsplash.com/photo-1497366216548-37526070297c?q=80&w=1200', 'Tầng trung', 2),
                ('MẶT BẰNG TẦNG CAO (LEVEL 21+)', 'https://images.unsplash.com/photo-1497366811353-6870744d04b2?q=80&w=1200', 'Tầng cao', 3)
            ) AS v(floor_label, image_url, note, sort_order)
            WHERE b.project_id = (SELECT id FROM salepro.projects WHERE project_type = 'CAO_TANG' ORDER BY id LIMIT 1);
        """);
    }

    // ============ 6. Hỏi đáp ============
    private void seedQuestions() {
        if (!isEmpty("salepro.apartment_questions")) return;
        jdbc.execute("""
            INSERT INTO salepro.apartment_questions (apartment_id, full_name, phone, content, answer, answered_by, status, created_at)
            SELECT a.id, 'Nguyễn Văn An', '0912000111', 'Căn này còn không và có hỗ trợ vay ngân hàng nào ạ?', 'Dạ căn còn hàng, hỗ trợ vay TCB/BIDV tới 70% giá trị.', 'Dương Hồng Hạnh', 'ANSWERED', now() - INTERVAL '2 day'
            FROM salepro.apartments a WHERE a.apartment_code = 'L1-05-03' LIMIT 1;

            INSERT INTO salepro.apartment_questions (apartment_id, full_name, phone, content, status, created_at)
            SELECT a.id, 'Trần Thị Bình', '0922333444', 'Cho mình xin layout chi tiết và chính sách thanh toán sớm với ạ.', 'PENDING', now() - INTERVAL '5 hour'
            FROM salepro.apartments a WHERE a.apartment_code = 'L1-05-03' LIMIT 1;

            INSERT INTO salepro.apartment_questions (apartment_id, full_name, phone, content, answer, answered_by, status, created_at)
            SELECT a.id, 'Lê Hoàng', '0935666777', 'Quỹ độc quyền này giá có thương lượng được không?', 'Giá niêm yết đã gồm VAT & KPBT, anh để lại SĐT em tư vấn thêm ạ.', 'Nguyễn Minh Tuấn', 'ANSWERED', now() - INTERVAL '1 day'
            FROM salepro.apartments a WHERE a.apartment_code = 'L1-05-02' LIMIT 1;
        """);
    }

    // ============ 7. Tiến độ ============
    private void seedProgress() {
        if (!isEmpty("salepro.project_progress")) return;
        jdbc.execute("""
            INSERT INTO salepro.project_progress (project_id, title, progress_date, external_url, images, sort_order, created_at)
            SELECT p.id, v.title, v.pdate::date, v.url, v.imgs::jsonb, v.so, now()
            FROM salepro.projects p
            CROSS JOIN (VALUES
                ('Tháng 6/2026', '2026-06-01', 'https://drive.google.com/drive/folders/progress-06-2026', '["https://images.unsplash.com/photo-1541888946425-d81bb19240f5?q=80&w=800","https://images.unsplash.com/photo-1503387762-592deb58ef4e?q=80&w=800","https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?q=80&w=800"]', 1),
                ('14/4/2026', '2026-04-14', 'https://drive.google.com/drive/folders/progress-14-04-2026', '["https://images.unsplash.com/photo-1581094794329-c8112a89af12?q=80&w=800","https://images.unsplash.com/photo-1504307651254-35680f356dfd?q=80&w=800"]', 2),
                ('7/4/2026', '2026-04-07', 'https://drive.google.com/drive/folders/progress-07-04-2026', '["https://images.unsplash.com/photo-1590644365607-1c5a0a8c8b5a?q=80&w=800"]', 3)
            ) AS v(title, pdate, url, imgs, so)
            WHERE p.project_type = 'CAO_TANG' ORDER BY p.id LIMIT 3;
        """);
    }

    // ============ 8. Tài liệu (link Drive) ============
    private void seedDocuments() {
        if (!isEmpty("salepro.project_documents")) return;
        jdbc.execute("""
            INSERT INTO salepro.project_documents (project_id, label, drive_url, doc_type, sort_order)
            SELECT p.id, v.label, v.url, v.dtype, v.so
            FROM salepro.projects p
            CROSS JOIN (VALUES
                ('TỔNG MẶT BẰNG', 'https://drive.google.com/file/d/tong-mat-bang/view', 'PDF', 1),
                ('MẶT BẰNG TẦNG', 'https://drive.google.com/file/d/mat-bang-tang/view', 'PDF', 2),
                ('PHỐI CẢNH', 'https://drive.google.com/file/d/phoi-canh/view', 'IMAGE', 3),
                ('LAYOUT CĂN HỘ', 'https://drive.google.com/file/d/layout-can-ho/view', 'PDF', 4),
                ('VIDEO', 'https://drive.google.com/file/d/video-du-an/view', 'VIDEO', 5),
                ('SLIDE ĐÀO TẠO', 'https://drive.google.com/file/d/slide-dao-tao/view', 'SLIDE', 6),
                ('PHÁP LÝ DỰ ÁN', 'https://drive.google.com/file/d/phap-ly/view', 'PDF', 7),
                ('CSBH', 'https://drive.google.com/file/d/csbh/view', 'PDF', 8),
                ('TRỤC CĂN', 'https://drive.google.com/file/d/truc-can/view', 'PDF', 9)
            ) AS v(label, url, dtype, so)
            WHERE p.project_type = 'CAO_TANG' ORDER BY p.id LIMIT 9;
        """);
    }

    // ============ 9. Sự kiện ============
    private void seedEvents() {
        if (!isEmpty("salepro.events")) return;
        jdbc.execute("""
            INSERT INTO salepro.events (title, slug, event_type, status, banner_image, description, location, start_time, end_time, project_id, gallery_images, participant_count, checkin_count, created_at, updated_at) VALUES
            ('SỰ KIỆN MỞ BÁN DỰ ÁN VINHOMES HẢI VÂN BAY "HEIR TO THE GEMS - NGƯỜI KẾ THỪA NGỌC BẢO"', 'mo-ban-vinhomes-hai-van-bay', 'GENERAL', 'ENDED', 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?q=80&w=1600', '<p>Sự kiện mở bán đặc biệt mở ra cơ hội sở hữu những giá trị xứng tầm cho các chủ nhân tiên phong.</p>', 'Trống Đồng Palace, Lãng Yên, Hà Nội', TIMESTAMPTZ '2026-05-31 08:00:00+07', TIMESTAMPTZ '2026-05-31 12:00:00+07', NULL, '["https://images.unsplash.com/photo-1492684223066-81342ee5ff30?q=80&w=800","https://images.unsplash.com/photo-1511578314322-379afb476865?q=80&w=800"]'::jsonb, 10, 0, now(), now()),
            ('SỰ KIỆN TRẢI NGHIỆM: HÀNH TRÌNH DIỆU KỲ - WONDER PLANET', 'trai-nghiem-wonder-planet', 'GENERAL', 'ENDED', 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?q=80&w=1600', '<p>Hành trình trải nghiệm diệu kỳ tại Wonderland.</p>', 'Công viên Wonderland, Vinhomes Global Gate, Đông Anh, Hà Nội', TIMESTAMPTZ '2026-05-30 14:30:00+07', TIMESTAMPTZ '2026-05-30 21:30:00+07', NULL, '[]'::jsonb, 25, 18, now(), now()),
            ('TALKSHOW GIẢI MÃ VĨ MÔ & CHIẾN LƯỢC ĐẦU TƯ BĐS 2026', 'talkshow-vi-mo-2026', 'GENERAL', 'ENDED', 'https://images.unsplash.com/photo-1475721027785-f74eccf877e2?q=80&w=1600', '<p>Talkshow chuyên sâu về thị trường bất động sản 2026.</p>', 'Trung tâm Hội nghị Quốc gia, Hà Nội', TIMESTAMPTZ '2026-05-20 09:00:00+07', TIMESTAMPTZ '2026-05-20 12:00:00+07', NULL, '[]'::jsonb, 120, 95, now(), now()),
            ('ĐÀO TẠO CHUYÊN SÂU DỰ ÁN LUMIÈRE HANOI SEASONS GARDEN', 'dao-tao-lumiere-seasons-garden', 'TRAINING', 'UPCOMING', 'https://images.unsplash.com/photo-1524178232363-1fb2b075b655?q=80&w=1600', '<p>Đào tạo chuyên sâu dành cho chuyên viên tư vấn.</p>', 'Hội trường Tầng 3, Tòa Symphony, Hà Nội', TIMESTAMPTZ '2026-06-15 14:00:00+07', TIMESTAMPTZ '2026-06-15 17:00:00+07', (SELECT id FROM salepro.projects WHERE project_type='CAO_TANG' ORDER BY id LIMIT 1), '[]'::jsonb, 40, 0, now(), now()),
            ('OPEN HOUSE: THAM QUAN NHÀ MẪU LUMIÈRE SEASONS GARDEN', 'open-house-lumiere', 'GENERAL', 'UPCOMING', 'https://images.unsplash.com/photo-1505691938895-1758d7feb511?q=80&w=1600', '<p>Mời quý khách tham quan nhà mẫu thực tế.</p>', 'Nhà mẫu dự án, 233 Nguyễn Trãi, Hà Nội', TIMESTAMPTZ '2026-06-20 08:30:00+07', TIMESTAMPTZ '2026-06-20 17:30:00+07', (SELECT id FROM salepro.projects WHERE project_type='CAO_TANG' ORDER BY id LIMIT 1), '[]'::jsonb, 60, 0, now(), now());
        """);
    }

    // ============ 10. Tin tức ============
    private void seedNews() {
        if (isEmpty("salepro.news_categories")) {
            jdbc.execute("""
                INSERT INTO salepro.news_categories (name, slug, sort_order) VALUES
                ('Phân Tích - Nhận định', 'phan-tich-nhan-dinh', 1),
                ('Tin Tức Dự Án', 'tin-tuc-du-an', 2),
                ('Thị trường', 'thi-truong', 3),
                ('VINHOMES GOLDEN CITY', 'vinhomes-golden-city', 4),
                ('Vinhomes Royal Island', 'vinhomes-royal-island', 5),
                ('Pháp Lý - Chính Sách', 'phap-ly-chinh-sach', 6),
                ('Vinhomes Wonder City', 'vinhomes-wonder-city', 7),
                ('LUMIÈRE Prime Hills', 'lumiere-prime-hills', 8),
                ('Vinhomes The Gallery', 'vinhomes-the-gallery', 9),
                ('Masteri Grand Avenue', 'masteri-grand-avenue', 10);
            """);
        }
        if (isEmpty("salepro.news_articles")) {
            jdbc.execute("""
                INSERT INTO salepro.news_articles (title, slug, thumbnail, summary, content, author, category_id, tags, project_id, published_at, view_count, status)
                SELECT v.title, v.slug, v.thumbnail, v.summary, v.content, 'Mayhomes',
                       (SELECT id FROM salepro.news_categories WHERE slug = v.cat_slug LIMIT 1),
                       '["Chung cư cao cấp","Đầu tư bất động sản","BĐS Hà Nội"]'::jsonb,
                       CASE WHEN v.ptype = 'CAO_TANG' THEN (SELECT id FROM salepro.projects WHERE project_type='CAO_TANG' ORDER BY id LIMIT 1) ELSE NULL END,
                       v.published::timestamptz, v.views, 'PUBLISHED'
                FROM (VALUES
                    ('Diện mạo mới của "đất vàng" Cao Xà Lá: Từ tổ hợp nhà máy đến đại đô thị cao cấp', 'dien-mao-moi-cao-xa-la', 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?q=80&w=600', 'Khu công nghiệp Cao Xà Lá cũ tại trục Nguyễn Trãi đang được giải phóng mặt bằng để nhường chỗ cho siêu dự án phức hợp cao cấp Hanoi Seasons Garden.', '<p>Khu đất vàng Cao Xà Lá đang chuyển mình mạnh mẽ thành đại đô thị cao cấp.</p><h3>Vị trí kim cương</h3><p>Nằm trên trục Nguyễn Trãi huyết mạch.</p>', 'tin-tuc-du-an', '2026-05-11 09:00:00+07', 320, 'CAO_TANG'),
                    ('Giải mã sức hút của Vinhomes Sài Gòn Park: Tài sản lõi được trợ lực từ tiến độ thần tốc', 'giai-ma-suc-hut-vinhomes-sai-gon-park', 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?q=80&w=600', 'Dòng tiền đầu tư đang dịch chuyển mạnh vào nhóm tài sản lõi với pháp lý minh bạch và tiến độ xây dựng vượt trội.', '<p>Vinhomes Sài Gòn Park ghi nhận 7.778 lượt đăng ký giữ chỗ chỉ sau 3 phiên phát sóng.</p>', 'phan-tich-nhan-dinh', '2026-06-09 09:00:00+07', 540, NULL),
                    ('Cầu Thượng Cát khởi công - Giá trị Vinhomes Wonder City bứt tốc', 'cau-thuong-cat-khoi-cong', 'https://images.unsplash.com/photo-1449844908441-8829872d2607?q=80&w=600', 'Cầu Thượng Cát chính thức khởi công là bước ngoặt hạ tầng quan trọng của Hà Nội giai đoạn 2025-2030.', '<p>Cây cầu bắc qua sông Hồng nối Bắc Từ Liêm - Đông Anh.</p>', 'vinhomes-wonder-city', '2026-05-23 09:00:00+07', 210, NULL),
                    ('Pháp lý minh bạch - Bệ phóng cho bất động sản 2026', 'phap-ly-minh-bach-2026', 'https://images.unsplash.com/photo-1450101499163-c8848c66ca85?q=80&w=600', 'Các dự án có pháp lý hoàn chỉnh đang trở thành lựa chọn an toàn hàng đầu của nhà đầu tư.', '<p>Pháp lý minh bạch là yếu tố sống còn.</p>', 'phap-ly-chinh-sach', '2026-05-18 09:00:00+07', 150, NULL),
                    ('Thị trường căn hộ nội đô Hà Nội tiếp tục lập đỉnh giá mới', 'thi-truong-can-ho-noi-do', 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?q=80&w=600', 'Nguồn cung khan hiếm đẩy giá căn hộ khu vực trung tâm tăng mạnh trong nửa đầu 2026.', '<p>Giá căn hộ nội đô tiếp tục neo cao.</p>', 'thi-truong', '2026-06-02 09:00:00+07', 188, NULL),
                    ('LUMIÈRE Hanoi Seasons Garden cất nóc tòa L1 vượt tiến độ 30 ngày', 'lumiere-cat-noc-l1', 'https://images.unsplash.com/photo-1503387762-592deb58ef4e?q=80&w=600', 'Nhà thầu chính thức làm lễ cất nóc tháp L1, đánh dấu cột mốc quan trọng của dự án.', '<p>Cất nóc tòa L1 vượt tiến độ cam kết.</p>', 'tin-tuc-du-an', '2026-06-05 09:00:00+07', 275, 'CAO_TANG')
                ) AS v(title, slug, thumbnail, summary, content, cat_slug, published, views, ptype);
            """);
        }
    }

    // ============ 11. Dự án thấp tầng (case 11 tab, không có Tòa nhà) ============
    private void seedLowRiseProject() {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM salepro.projects WHERE project_type = 'THAP_TANG'", Integer.class);
        if (c != null && c > 0) return;

        jdbc.execute("""
            INSERT INTO salepro.projects (name, project_type, status, details, managing_agent_id, created_at, updated_at)
            VALUES (
                'VINHOMES SÀI GÒN PARK',
                'THAP_TANG',
                'DANG_BAN',
                '{"developer":"Vinhomes","address":"TP. Thủ Đức, TP.HCM","totalProjectArea":"267 ha","scaleDescription":"Khu thấp tầng liền kề - biệt thự","constructionDensity":"24%","apartmentTypes":"Liền kề, Biệt thự, Shophouse","scale":"267 ha","capital":"4 tỷ USD","residents":"Cộng đồng tinh hoa","overviewBullets":["Tên dự án: Vinhomes Sài Gòn Park","Chủ đầu tư: Vinhomes","Sản phẩm: Liền kề, biệt thự, shophouse"],"bannerImageUrl":"https://images.unsplash.com/photo-1570129477492-45c003edd2be?q=80&w=1600","salesPolicy":"Chiết khấu tới 10%, hỗ trợ vay 70% trong 35 năm."}'::jsonb,
                (SELECT id FROM salepro.sales_agents ORDER BY id LIMIT 1),
                now(), now()
            );

            INSERT INTO salepro.buildings (project_id, building_name, subdivision_name, total_floors, ownership_type, handover_standard, total_apartments, description, image_url, construction_progress)
            VALUES (
                (SELECT id FROM salepro.projects WHERE project_type='THAP_TANG' ORDER BY id LIMIT 1),
                'IVY PARK', 'IVY PARK', 5, 'Lâu dài', 'Giãn xây', 200,
                'Phân khu liền kề - biệt thự cao cấp IVY PARK.',
                'https://images.unsplash.com/photo-1570129477492-45c003edd2be?q=80&w=1200', 100
            );

            INSERT INTO salepro.apartments (building_id, apartment_code, thumbnail_url, apartment_type, direction, floor, axis, view_description, status, clearance_area, built_up_area, land_area, construction_area, listed_price, loan_price, early_payment_price, progress_payment_price, supported_banks, sales_policy_applied, sales_policy_date, gifts_promotions, handover_standard, fund_type)
            SELECT b.id, v.code, 'https://images.unsplash.com/photo-1576941089067-2de3c901e126?q=80&w=600', 'LIEN_KE', v.dir, '1', v.axis, 'View nội khu', v.st, v.land, v.build, v.land, v.build, v.listed, v.loan, v.tts, v.tttd, 'TECHCOMBANK', 'CSBH Tháng 6', DATE '2026-06-06', 'Quà tặng đồng hành', 'Giãn xây', 'Sơ cấp'
            FROM salepro.buildings b
            CROSS JOIN (VALUES
                ('AS84-06', 'DONG_BAC', '06', 'CON_HANG', 50.0, 144.2, 5.95, 5.55, 5.10, 5.83),
                ('AS85-24', 'DONG_BAC', '24', 'CON_HANG', 50.0, 144.2, 6.21, 5.78, 5.30, 6.05),
                ('AS84-36', 'DONG_BAC', '36', 'DA_BAN', 50.0, 144.2, 6.25, 5.81, 5.33, 6.08),
                ('AS82-18', 'DONG_BAC', '18', 'CON_HANG', 50.0, 150.1, 6.35, 5.90, 5.42, 6.18),
                ('AS83-05', 'TAY_NAM', '05', 'QUY_DOC_QUYEN', 50.0, 184.1, 7.13, 6.63, 6.10, 6.95),
                ('AS81-31', 'TAY_NAM', '31', 'CON_HANG', 60.0, 185.4, 8.40, 7.81, 7.18, 8.18)
            ) AS v(code, dir, axis, st, land, build, listed, loan, tts, tttd)
            WHERE b.project_id = (SELECT id FROM salepro.projects WHERE project_type='THAP_TANG' ORDER BY id LIMIT 1);
        """);
    }
}
