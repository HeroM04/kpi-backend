package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.utils.HaversineUtils;
import com.trilong.kpibackend.modules.attendance.dto.CheckinRequestDTO;
import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.trilong.kpibackend.core.service.FaceRecognitionService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CheckinService — Xử lý toàn bộ logic chấm công.
 *
 * <h3>Luồng nghiệp vụ:</h3>
 * <p><b>Luồng 1 — Tại văn phòng (GPS ≤ 50m):</b>
 * <ul>
 *   <li>Status: {@code APPROVED} ngay lập tức</li>
 *   <li>Loại: {@code OFFICE}</li>
 *   <li>KPI: Tự động cộng điểm attendance</li>
 *   <li>Mốc giờ quan trọng: 08:30 (đúng giờ) | 17:30 (tan làm)</li>
 * </ul>
 *
 * <p><b>Luồng 2 — Ngoại tuyến/Thị trường (GPS > 50m):</b>
 * <ul>
 *   <li>Status: {@code PENDING} — chờ Admin/Trưởng phòng duyệt</li>
 *   <li>Loại: {@code FIELD}</li>
 *   <li>Bắt buộc: {@code note} phải có nội dung lý do</li>
 *   <li>KPI: Chỉ cộng điểm SAU KHI được duyệt</li>
 * </ul>
 *
 * <h3>Bảo mật client-side (thực hiện trên Mobile App — không xử lý ở đây):</h3>
 * <ul>
 *   <li>Anti-mock location: Block nếu isMockLocation = true</li>
 *   <li>Camera-only: Không cho chọn ảnh từ gallery</li>
 *   <li>ML Kit face detection: Phải có ≥ 1 khuôn mặt trong ảnh</li>
 *   <li>Watermark: Vẽ timestamp + địa chỉ lên ảnh trước khi upload</li>
 * </ul>
 */
@Service
@Slf4j
public class CheckinService {

    // ── Ngưỡng khoảng cách (mét) ─────────────────────────────────────────────
    /** Bán kính mặc định khi phòng ban chưa cấu hình: trong vòng 2000m → APPROVED tự động */
    private static final double OFFICE_RADIUS_METERS = 2000.0;

    // ── Tọa độ văn phòng mặc định (fallback khi phòng ban chưa set) ──────────
    private static final double DEFAULT_OFFICE_LAT = 20.999042; // Hà Nội
    private static final double DEFAULT_OFFICE_LNG = 105.806702;

    /**
     * Cấu hình văn phòng áp dụng cho một nhân viên (tọa độ + bán kính cho phép).
     * Đọc từ phòng ban của nhân viên trong DB, để đổi địa điểm văn phòng chỉ cần
     * sửa trên Web Admin, KHÔNG phải sửa code và build lại app.
     */
    private record OfficeConfig(double lat, double lng, double radiusMeters, String source) {}

    /**
     * Lấy cấu hình văn phòng cho nhân viên.
     * Ưu tiên tọa độ/bán kính của phòng ban; thiếu thì dùng giá trị mặc định.
     */
    private OfficeConfig resolveOfficeConfig(User user) {
        Department dept = (user != null) ? user.getDepartment() : null;

        if (dept != null && dept.getOfficeLat() != null && dept.getOfficeLng() != null) {
            double radius = (dept.getAllowedRadius() != null && dept.getAllowedRadius() > 0)
                    ? dept.getAllowedRadius().doubleValue()
                    : OFFICE_RADIUS_METERS;
            return new OfficeConfig(dept.getOfficeLat(), dept.getOfficeLng(), radius,
                    "phòng ban '" + dept.getName() + "'");
        }

        return new OfficeConfig(DEFAULT_OFFICE_LAT, DEFAULT_OFFICE_LNG, OFFICE_RADIUS_METERS,
                "mặc định (phòng ban chưa cấu hình tọa độ)");
    }

    // ── Mốc giờ chấm công (theo quy định công ty) ───────────────────────────
    /** Điểm danh từ 08:30; đến 08:45 vẫn tính ĐÚNG GIỜ (15 phút châm chước). */
    private static final LocalTime ON_TIME_LIMIT = LocalTime.of(8, 45);
    /** Từ sau 08:45 đến 10:00 tính là ĐI MUỘN. */
    private static final LocalTime LATE_LIMIT    = LocalTime.of(10, 0);
    /** Kết thúc giờ làm. */
    private static final LocalTime CUTOFF_CHECKOUT = LocalTime.of(17, 30);

    // ── Điểm KPI chuyên cần ─────────────────────────────────────────────────
    /** Đi làm đúng giờ (≤ 08:45) */
    private static final int KPI_ON_TIME = 5;
    /** Đi làm muộn (08:45 – 10:00) */
    private static final int KPI_LATE = -5;
    /** Check-in sau 10:00 — theo quy định tính là vắng mặt (không phép) */
    private static final int KPI_ABSENT_UNEXCUSED = -15;

    // ── Tăng ca (thuộc nhóm Thực chiến) ─────────────────────────────────────
    /** Mốc giờ về tối thiểu để được tính một khung tăng ca. */
    private static final LocalTime OVERTIME_LIMIT = LocalTime.of(20, 0);
    /** Điểm cho mỗi khung tăng ca. */
    private static final int KPI_OVERTIME = 5;

    // ── Zone ─────────────────────────────────────────────────────────────────
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private CheckinLogRepository checkinLogRepository;

    @Autowired
    private UserRepository userRepository;

    /** Dùng cho truy vấn gộp theo ngày công — JPQL không diễn đạt được GROUP BY này. */
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Autowired
    private KpiCalculationService kpiCalculationService;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Value("${app.rekognition.enabled:false}")
    private boolean isRekognitionEnabled;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý yêu cầu chấm công — entry point chính.
     *
     * <p>Hàm này tự quyết định luồng dựa trên khoảng cách GPS:
     * <ul>
     *   <li>≤ 50m → gọi {@link #processOfficeCheckin(Long, CheckinRequestDTO, double)}</li>
     *   <li>> 50m → gọi {@link #processFieldCheckin(Long, CheckinRequestDTO, double)}</li>
     * </ul>
     *
     * @return {@link CheckinLog} đã lưu vào DB
     * @throws IllegalArgumentException nếu FIELD checkin thiếu note
     */
    @Transactional
    public CheckinLog submitCheckin(Long userId, CheckinRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên"));

        String actionType = resolveActionType(request.getActionType());
        // Xác thực khuôn mặt bằng AWS Rekognition (nếu được bật trong properties và không phải CHECK_OUT)
        if (isRekognitionEnabled && !"CHECK_OUT".equals(actionType)) {
            if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
                throw new IllegalArgumentException("Bạn chưa cập nhật ảnh đại diện (Avatar). Vui lòng cập nhật để sử dụng tính năng nhận diện khuôn mặt!");
            }
            log.info("[Checkin] Đang gọi AWS Rekognition để xác thực khuôn mặt user {}...", userId);
            boolean isMatched = faceRecognitionService.compareFacesUrls(user.getAvatarUrl(), request.getPhotoUrl());
            if (!isMatched) {
                throw new IllegalArgumentException("Xác thực khuôn mặt thất bại! Người trong ảnh không khớp với ảnh đại diện của bạn.");
            }
            log.info("[Checkin] Xác thực khuôn mặt thành công cho user {}!", userId);
        }

        // Tọa độ + bán kính lấy theo phòng ban của nhân viên (sửa được trên Web Admin)
        OfficeConfig office = resolveOfficeConfig(user);

        double distance = HaversineUtils.calculateDistanceInMeters(
                request.getLatitude(), request.getLongitude(), office.lat(), office.lng()
        );

        log.info("[Checkin] userId={}, distance={}m, lat={}, lng={} | văn phòng: {} ({}, {}) bán kính {}m",
                userId, String.format("%.1f", distance), request.getLatitude(), request.getLongitude(),
                office.source(), office.lat(), office.lng(), String.format("%.0f", office.radiusMeters()));

        if (distance <= office.radiusMeters()) {
            return processOfficeCheckin(userId, request, distance);
        } else {
            return processFieldCheckin(userId, request, distance);
        }
    }

    /**
     * Kiểm tra nhanh xem tọa độ có trong phạm vi văn phòng của nhân viên không.
     * Dùng cấu hình phòng ban; phòng ban chưa set thì dùng giá trị mặc định.
     */
    public boolean isWithinOfficeRange(Long userId, double lat, double lng) {
        User user = userRepository.findById(userId).orElse(null);
        OfficeConfig office = resolveOfficeConfig(user);
        double distance = HaversineUtils.calculateDistanceInMeters(lat, lng, office.lat(), office.lng());
        return distance <= office.radiusMeters();
    }

    /** Lấy lịch sử checkin của user trong một ngày cụ thể */
    public List<CheckinLog> getCheckinsByUserIdAndDate(Long userId, LocalDate date) {
        ZonedDateTime start = date.atStartOfDay(VN_ZONE);
        ZonedDateTime end   = date.plusDays(1).atStartOfDay(VN_ZONE);
        return checkinLogRepository.findByUserIdAndCheckinTimeBetween(userId, start, end);
    }

    /** Lấy toàn bộ lịch sử checkin của user */
    public List<CheckinLog> getCheckinsByUserId(Long userId) {
        return checkinLogRepository.findByUserIdOrderByCheckinTimeDesc(userId);
    }

    /** Lấy danh sách chờ duyệt (cho Admin/Trưởng phòng) */
    public List<CheckinLog> getPendingCheckins() {
        return checkinLogRepository.findByStatusOrderByCheckinTimeDesc("PENDING");
    }

    /** Lấy toàn bộ danh sách checkin (Admin) */
    /**
     * Lấy một trang bản ghi chấm công theo bộ lọc.
     *
     * <p>Lọc theo phòng ban hoặc theo tên được quy về danh sách userId trước
     * (bảng nhân sự nhỏ), rồi để cơ sở dữ liệu lọc và phân trang. Nhờ vậy dù
     * bảng chấm công có hàng trăm nghìn dòng, mỗi lần gọi chỉ đọc đúng số dòng
     * của trang đang xem.
     */
    public org.springframework.data.domain.Page<CheckinLog> timTheoTrang(
            Long userId, Long departmentId, String search,
            String month, String from, String to, String status,
            int page, int size) {

        int coSo = Math.min(Math.max(size, 1), 200);
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), coSo);

        // Khoảng thời gian: ưu tiên from/to, không có thì suy từ month
        ZonedDateTime tuNgay = null, denNgay = null;
        if (from != null && !from.isBlank()) tuNgay = LocalDate.parse(from).atStartOfDay(VN_ZONE);
        if (to != null && !to.isBlank()) denNgay = LocalDate.parse(to).plusDays(1).atStartOfDay(VN_ZONE);
        if (tuNgay == null && denNgay == null && month != null && !month.isBlank()) {
            var ym = java.time.YearMonth.parse(month);
            tuNgay = ym.atDay(1).atStartOfDay(VN_ZONE);
            denNgay = ym.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE);
        }

        String trangThai = (status != null && !status.isBlank()) ? status.toUpperCase() : null;

        // Quy phòng ban / tên / userId về một danh sách nhân sự cụ thể
        List<Long> danhSachNhanSu = null;
        if (userId != null) {
            danhSachNhanSu = List.of(userId);
        } else if (departmentId != null || (search != null && !search.isBlank())) {
            String tuKhoa = search == null ? null : search.trim().toLowerCase();
            danhSachNhanSu = userRepository.findAll().stream()
                    .filter(u -> departmentId == null
                            || (u.getDepartment() != null && departmentId.equals(u.getDepartment().getId())))
                    .filter(u -> tuKhoa == null || tuKhoa.isEmpty()
                            || (u.getFullName() != null && u.getFullName().toLowerCase().contains(tuKhoa)))
                    .map(User::getId)
                    .toList();
            // Không ai khớp thì khỏi truy vấn, trả về trang rỗng luôn
            if (danhSachNhanSu.isEmpty()) {
                return org.springframework.data.domain.Page.empty(pageable);
            }
        }

        // Dựng điều kiện lọc động: chỉ thêm điều kiện nào thực sự được chọn
        final List<Long> nhanSu = danhSachNhanSu;
        final ZonedDateTime tu = tuNgay, den = denNgay;
        final String tt = trangThai;

        org.springframework.data.jpa.domain.Specification<CheckinLog> dieuKien = (root, query, cb) -> {
            var ds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (nhanSu != null) ds.add(root.get("userId").in(nhanSu));
            if (tt != null) ds.add(cb.equal(root.get("status"), tt));
            if (tu != null) ds.add(cb.greaterThanOrEqualTo(root.get("checkinTime"), tu));
            if (den != null) ds.add(cb.lessThan(root.get("checkinTime"), den));
            return ds.isEmpty() ? cb.conjunction() : cb.and(ds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        var sapXep = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "checkinTime");
        return checkinLogRepository.findAll(dieuKien,
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), coSo, sapXep));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PHÂN TRANG THEO NGÀY CÔNG
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Một trang danh sách chấm công, đếm theo NGÀY CÔNG chứ không theo bản ghi.
     *
     * @param banGhi     mọi bản ghi thuộc các ngày công của trang này
     * @param tongNgayCong tổng số ngày công khớp bộ lọc (để dựng thanh phân trang)
     * @param soCho / soDuyet / soTuChoi  thống kê ngày công theo trạng thái,
     *        tính trên toàn bộ dữ liệu khớp bộ lọc trừ bộ lọc trạng thái
     */
    public record TrangNgayCong(List<CheckinLog> banGhi, long tongNgayCong,
                                long soCho, long soDuyet, long soTuChoi) {}

    /** Biểu thức quy giờ chấm công về NGÀY theo giờ Việt Nam. */
    private static final String COT_NGAY =
            "(c.checkin_time AT TIME ZONE 'Asia/Ho_Chi_Minh')::date";

    /**
     * Lấy một trang danh sách chấm công, mỗi dòng là MỘT NGÀY CÔNG của một người
     * (gộp bản ghi vào và bản ghi ra) — đúng cách màn hình quản trị hiển thị.
     *
     * <p>Phải phân trang theo ngày công chứ không theo từng bản ghi, nếu không
     * ranh giới trang sẽ cắt đôi một cặp vào/ra và bảng hiện thiếu giờ.
     *
     * <p>Việc chia trang do cơ sở dữ liệu làm bằng GROUP BY … LIMIT/OFFSET, nên
     * trang thứ mấy cũng đọc đúng phần dữ liệu của trang đó. Cách làm cũ là nạp
     * một cụm bản ghi đầu bảng rồi cắt trong bộ nhớ — trang 2 trở đi không bao
     * giờ với tới được dữ liệu cũ hơn, và tổng số trang cũng sai.
     *
     * <p>Trả về TẤT CẢ bản ghi của những ngày công trong trang, kể cả bản ghi có
     * trạng thái khác với bộ lọc: một ngày có giờ vào đã duyệt và giờ ra đang
     * chờ vẫn phải hiện đủ hai mốc giờ thì người duyệt mới nhìn ra vấn đề.
     */
    @Transactional(readOnly = true)
    public TrangNgayCong timTheoNgayCong(
            Long userId, Long departmentId, String search,
            String month, String from, String to, String status,
            int page, int size) {

        int coSo = Math.min(Math.max(size, 1), 100);
        int trang = Math.max(page, 0);

        // Khoảng thời gian: ưu tiên from/to, không có thì suy từ month
        ZonedDateTime tuNgay = null, denNgay = null;
        if (from != null && !from.isBlank()) tuNgay = LocalDate.parse(from).atStartOfDay(VN_ZONE);
        if (to != null && !to.isBlank()) denNgay = LocalDate.parse(to).plusDays(1).atStartOfDay(VN_ZONE);
        if (tuNgay == null && denNgay == null && month != null && !month.isBlank()) {
            var ym = java.time.YearMonth.parse(month);
            tuNgay = ym.atDay(1).atStartOfDay(VN_ZONE);
            denNgay = ym.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE);
        }

        String trangThai = (status != null && !status.isBlank()) ? status.toUpperCase() : null;
        List<Long> nhanSu = quyVeDanhSachNhanSu(userId, departmentId, search);
        if (nhanSu != null && nhanSu.isEmpty()) {
            return new TrangNgayCong(List.of(), 0, 0, 0, 0);
        }

        // Điều kiện lọc dùng chung; chỉ ghép mệnh đề nào thực sự được chọn nên
        // không bao giờ phải truyền tham số null vào SQL.
        StringBuilder loc = new StringBuilder(" WHERE 1=1");
        Map<String, Object> thamSo = new java.util.LinkedHashMap<>();
        if (nhanSu != null) { loc.append(" AND c.user_id IN (:nhanSu)"); thamSo.put("nhanSu", nhanSu); }
        if (tuNgay != null) { loc.append(" AND c.checkin_time >= :tu"); thamSo.put("tu", tuNgay); }
        if (denNgay != null) { loc.append(" AND c.checkin_time < :den"); thamSo.put("den", denNgay); }
        String locKhongTrangThai = loc.toString();
        if (trangThai != null) { loc.append(" AND c.status = :tt"); thamSo.put("tt", trangThai); }
        String locDayDu = loc.toString();

        // 1) Tổng số ngày công khớp bộ lọc — để biết có bao nhiêu trang
        var qTong = em.createNativeQuery(
                "SELECT COUNT(*) FROM (SELECT c.user_id, " + COT_NGAY
                        + " FROM checkin_logs c" + locDayDu + " GROUP BY 1, 2) t");
        ganThamSo(qTong, thamSo, locDayDu);
        long tongNgayCong = ((Number) qTong.getSingleResult()).longValue();

        // 2) Thống kê theo trạng thái của ngày công. Bỏ qua bộ lọc trạng thái để
        //    các ô đếm luôn hiện đủ bức tranh, không đổi theo tab đang chọn.
        //    Một ngày có bản ghi chờ thì tính là chờ; có bản ghi bị từ chối thì
        //    tính là từ chối; còn lại là đã duyệt — khớp cách web gộp dòng.
        var qTk = em.createNativeQuery(
                "SELECT COUNT(*) FILTER (WHERE co_cho),"
                        + " COUNT(*) FILTER (WHERE NOT co_cho AND co_tuchoi),"
                        + " COUNT(*) FILTER (WHERE NOT co_cho AND NOT co_tuchoi)"
                        + " FROM (SELECT c.user_id, " + COT_NGAY + " AS ngay,"
                        + " bool_or(c.status = 'PENDING') AS co_cho,"
                        + " bool_or(c.status = 'REJECTED') AS co_tuchoi"
                        + " FROM checkin_logs c" + locKhongTrangThai + " GROUP BY 1, 2) t");
        ganThamSo(qTk, thamSo, locKhongTrangThai);
        Object[] tk = (Object[]) qTk.getSingleResult();
        long soCho = ((Number) tk[0]).longValue();
        long soTuChoi = ((Number) tk[1]).longValue();
        long soDuyet = ((Number) tk[2]).longValue();

        if (tongNgayCong == 0) {
            return new TrangNgayCong(List.of(), 0, soCho, soDuyet, soTuChoi);
        }

        // 3) Đúng những ngày công thuộc trang đang xem
        var qNgay = em.createNativeQuery(
                "SELECT c.user_id, " + COT_NGAY + " AS ngay FROM checkin_logs c" + locDayDu
                        + " GROUP BY 1, 2 ORDER BY ngay DESC, c.user_id LIMIT :lim OFFSET :bo");
        ganThamSo(qNgay, thamSo, locDayDu);
        qNgay.setParameter("lim", coSo);
        qNgay.setParameter("bo", (long) trang * coSo);
        @SuppressWarnings("unchecked")
        List<Object[]> cacNgay = qNgay.getResultList();
        if (cacNgay.isEmpty()) {
            return new TrangNgayCong(List.of(), tongNgayCong, soCho, soDuyet, soTuChoi);
        }

        // 4) Mọi bản ghi của đúng những cặp (nhân sự, ngày) đó
        StringBuilder cap = new StringBuilder();
        for (int i = 0; i < cacNgay.size(); i++) {
            cap.append(i == 0 ? " (" : " OR (")
               .append("c.user_id = :u").append(i)
               .append(" AND ").append(COT_NGAY).append(" = CAST(:d").append(i).append(" AS date))");
        }
        var qBanGhi = em.createNativeQuery(
                "SELECT c.* FROM checkin_logs c WHERE" + cap + " ORDER BY c.checkin_time DESC",
                CheckinLog.class);
        for (int i = 0; i < cacNgay.size(); i++) {
            qBanGhi.setParameter("u" + i, ((Number) cacNgay.get(i)[0]).longValue());
            qBanGhi.setParameter("d" + i, cacNgay.get(i)[1]);
        }
        @SuppressWarnings("unchecked")
        List<CheckinLog> banGhi = qBanGhi.getResultList();

        return new TrangNgayCong(banGhi, tongNgayCong, soCho, soDuyet, soTuChoi);
    }

    /** Chỉ gán những tham số thực sự có mặt trong câu lệnh. */
    private void ganThamSo(jakarta.persistence.Query q, Map<String, Object> thamSo, String cauLenh) {
        thamSo.forEach((ten, gt) -> {
            if (cauLenh.contains(":" + ten)) q.setParameter(ten, gt);
        });
    }

    /**
     * Quy bộ lọc phòng ban / tên / một người về danh sách userId cụ thể.
     * Trả về null nghĩa là không lọc theo người nào cả.
     */
    private List<Long> quyVeDanhSachNhanSu(Long userId, Long departmentId, String search) {
        if (userId != null) return List.of(userId);
        boolean coTuKhoa = search != null && !search.isBlank();
        if (departmentId == null && !coTuKhoa) return null;

        String tuKhoa = coTuKhoa ? search.trim().toLowerCase() : null;
        return userRepository.findAll().stream()
                .filter(u -> departmentId == null
                        || (u.getDepartment() != null && departmentId.equals(u.getDepartment().getId())))
                .filter(u -> tuKhoa == null
                        || (u.getFullName() != null && u.getFullName().toLowerCase().contains(tuKhoa)))
                .map(User::getId)
                .toList();
    }


    /**
     * @deprecated Nạp cả bảng vào bộ nhớ — chỉ còn dùng cho báo cáo nội bộ với
     *     dữ liệu đã giới hạn. Màn hình danh sách phải dùng {@link #timTheoTrang}.
     */
    @Deprecated
    public List<CheckinLog> getAllCheckins() {
        return checkinLogRepository.findAll();
    }

    /** Lấy chi tiết bản ghi checkin theo ID */
    public CheckinLog getCheckinById(Long id) {
        return checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bản ghi chấm công có ID: " + id));
    }

    /**
     * Admin/Trưởng phòng duyệt hoặc từ chối yêu cầu chấm công ngoại tuyến.
     *
     * <p>Khi APPROVE: cộng điểm KPI attendance.
     * Khi REJECT: nếu trước đó đã APPROVED thì trừ điểm lại.
     */
    @Transactional
    public CheckinLog updateStatus(Long id, String newStatus, String reason) {
        CheckinLog checkinLog = checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bản ghi chấm công"));

        String oldStatus = checkinLog.getStatus();
        checkinLog.setStatus(newStatus.toUpperCase());

        if (reason != null && !reason.trim().isEmpty()) {
            checkinLog.setRejectReason(reason);
        }

        CheckinLog saved = checkinLogRepository.save(checkinLog);

        // Xử lý cộng/trừ KPI
        if (!"APPROVED".equals(oldStatus) && "APPROVED".equals(newStatus.toUpperCase())) {
            // Phải quy về giờ Việt Nam: giá trị đọc từ DB đang ở múi giờ UTC,
            // nếu lấy thẳng toLocalTime() thì 09:30 VN thành 02:30 và bị chấm
            // nhầm thành "đi đúng giờ".
            int kpiPoints = calculateAttendanceKpi(checkinLog.getActionType(),
                    checkinLog.getCheckinTime().withZoneSameInstant(VN_ZONE).toLocalTime());
            kpiCalculationService.updateKpiPoints(
                    checkinLog.getUserId(), "attendance", kpiPoints, checkinLog.getCheckinTime(),
                    "Admin duyệt chấm công ngoại tuyến — "
                            + dienGiaiChuyenCan(checkinLog.getActionType(), checkinLog.getCheckinTime())
            );
            // Duyệt bản ghi check-out muộn cũng được tính tăng ca như chấm công tại chỗ
            awardOvertimeIfEligible(checkinLog.getUserId(), checkinLog.getActionType(),
                    checkinLog.getCheckinTime().withZoneSameInstant(VN_ZONE));
            log.info("[Checkin]  Duyệt {} #{} → {} KPI cho userId={}", checkinLog.getActionType(), id, (kpiPoints > 0 ? "+" + kpiPoints : kpiPoints), checkinLog.getUserId());
        } else if ("APPROVED".equals(oldStatus) && !"APPROVED".equals(newStatus.toUpperCase())) {
            // Phải quy về giờ Việt Nam: giá trị đọc từ DB đang ở múi giờ UTC,
            // nếu lấy thẳng toLocalTime() thì 09:30 VN thành 02:30 và bị chấm
            // nhầm thành "đi đúng giờ".
            int kpiPoints = calculateAttendanceKpi(checkinLog.getActionType(),
                    checkinLog.getCheckinTime().withZoneSameInstant(VN_ZONE).toLocalTime());
            kpiCalculationService.updateKpiPoints(
                    checkinLog.getUserId(), "attendance", -kpiPoints, checkinLog.getCheckinTime(),
                    "Admin thu hồi duyệt chấm công lúc " + gio(checkinLog.getCheckinTime())
                            + (reason != null && !reason.isBlank() ? " — " + reason.trim() : "")
            );
            log.info("[Checkin]  Thu hồi {} #{} → {} KPI cho userId={}", checkinLog.getActionType(), id, (-kpiPoints > 0 ? "+" + (-kpiPoints) : (-kpiPoints)), checkinLog.getUserId());
        }

        return saved;
    }

    /**
     * Duyệt checkin theo request body dạng Map (backward compatible với API cũ).
     */
    @Transactional
    public void processApproval(Map<String, Object> request) {
        Long logId   = ((Number) request.get("logId")).longValue();
        String status  = (String) request.get("status");
        String reason  = (String) request.get("reason");
        updateStatus(logId, status, reason);
    }

    /** Xóa bản ghi checkin (Admin) */
    @Transactional
    public void deleteCheckin(Long id) {
        CheckinLog checkinLog = checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bản ghi chấm công có ID: " + id));
        checkinLogRepository.delete(checkinLog);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Luồng 1: GPS ≤ 50m — tự động APPROVED, cộng KPI ngay.
     */
    @Transactional
    private CheckinLog processOfficeCheckin(Long userId, CheckinRequestDTO req, double distance) {
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);

        CheckinLog checkinLog = new CheckinLog();
        checkinLog.setUserId(userId);
        checkinLog.setCheckinTime(now);
        checkinLog.setCheckinType("OFFICE");
        String finalActionType = resolveActionType(req.getActionType());
        checkinLog.setActionType(finalActionType);
        checkinLog.setLatitude(req.getLatitude());
        checkinLog.setLongitude(req.getLongitude());
        checkinLog.setDistanceToOffice(distance);
        checkinLog.setAddress(req.getAddress());
        checkinLog.setPhotoUrl(req.getPhotoUrl());
        checkinLog.setNote(req.getNote());
        checkinLog.setStatus("APPROVED");

        CheckinLog saved = checkinLogRepository.save(checkinLog);

        // Điểm chuyên cần theo giờ check-in
        int kpiPoints = calculateAttendanceKpi(finalActionType, now.toLocalTime());
        kpiCalculationService.updateKpiPoints(userId, "attendance", kpiPoints, now,
                dienGiaiChuyenCan(finalActionType, now));

        // Điểm tăng ca theo giờ check-out (tính vào nhóm Thực chiến)
        awardOvertimeIfEligible(userId, finalActionType, now);

        log.info("[Checkin]  OFFICE {} userId={} lúc {} → {} KPI | distance={}m",
                finalActionType, userId, now.toLocalTime(), (kpiPoints > 0 ? "+" + kpiPoints : kpiPoints), String.format("%.1f", distance));

        // Notify WebSocket
        notifyAdmin("CHECKIN_OFFICE");
        return saved;
    }

    /**
     * Luồng 2: GPS > 50m — PENDING, cần Admin duyệt.
     */
    @Transactional
    private CheckinLog processFieldCheckin(Long userId, CheckinRequestDTO req, double distance) {
        // Bắt buộc phải có lý do khi ngoại tuyến
        if (req.getNote() == null || req.getNote().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Bạn đang ở ngoài phạm vi văn phòng (" + String.format("%.0f", distance) + "m). " +
                    "Vui lòng nhập lý do chấm công ngoại tuyến."
            );
        }

        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);

        CheckinLog checkinLog = new CheckinLog();
        checkinLog.setUserId(userId);
        checkinLog.setCheckinTime(now);
        checkinLog.setCheckinType("FIELD");
        checkinLog.setActionType(resolveActionType(req.getActionType()));
        checkinLog.setLatitude(req.getLatitude());
        checkinLog.setLongitude(req.getLongitude());
        checkinLog.setDistanceToOffice(distance);
        checkinLog.setAddress(req.getAddress());
        checkinLog.setPhotoUrl(req.getPhotoUrl());
        checkinLog.setNote(req.getNote());
        checkinLog.setStatus("PENDING");

        CheckinLog saved = checkinLogRepository.save(checkinLog);

        log.info("[Checkin]  FIELD checkin userId={} lúc {} → PENDING | distance={}m | note={}",
                userId, now.toLocalTime(), String.format("%.1f", distance), req.getNote());

        // Notify WebSocket cho Admin biết có yêu cầu mới
        notifyAdmin("CHECKIN_PENDING_NEW");
        return saved;
    }

    /** Chuẩn hóa actionType — mặc định CHECK_IN */
    private String resolveActionType(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "CHECK_IN";
        return raw.trim().toUpperCase();
    }

    /** Gửi thông báo WebSocket realtime cho Admin */
    private void notifyAdmin(String eventType) {
        try {
            messagingTemplate.convertAndSend("/topic/admin/attendance", eventType);
        } catch (Exception e) {
            log.warn("[Checkin] Không gửi được WebSocket notify: {}", e.getMessage());
        }
    }

    /**
     * Logic tính điểm KPI mới:
     * - CHECK_IN: Đúng giờ (<= 08:30) -> +5 điểm. Đi trễ (> 08:30) -> 0 điểm.
     * - CHECK_OUT: Không cộng/trừ điểm KPI (chỉ dùng để record thời gian).
     */
    /**
     * Tính điểm chuyên cần theo giờ check-in, đúng quy định công ty:
     * <ul>
     *   <li>đến 08:45 → đúng giờ, <b>+5đ</b> (điểm danh từ 08:30, châm chước 15 phút)</li>
     *   <li>08:45 – 10:00 → đi muộn, <b>−5đ</b></li>
     *   <li>sau 10:00 → tính là vắng mặt không phép, <b>−15đ</b></li>
     * </ul>
     * Check-out không tính điểm (chỉ dùng để ghi nhận giờ về).
     */
    /**
     * Cộng điểm tăng ca nếu ca làm kéo dài tới mốc quy định.
     *
     * <p>Quy định: khung làm thêm 18h–20h, về từ <b>20:00</b> trở đi được tính
     * một khung (+5đ), áp dụng các ngày làm việc trong tuần. Riêng thứ Năm và
     * thứ Sáu công ty kỳ vọng có một buổi kéo tới 21h — chỉ cần một trong hai
     * ngày đó đạt là đủ, nên về mặt điểm số vẫn tính từ mốc 20:00.
     *
     * <p>Điểm tăng ca thuộc nhóm <b>Thực chiến</b> (trần 40đ/tuần).
     * Chỉ tính khi CHECK_OUT, và không tính vào cuối tuần.
     */
    private void awardOvertimeIfEligible(Long userId, String actionType, ZonedDateTime time) {
        if (!"CHECK_OUT".equals(actionType)) return;

        DayOfWeek day = time.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return;

        if (time.toLocalTime().isBefore(OVERTIME_LIMIT)) return;

        kpiCalculationService.updateKpiPoints(userId, "meeting", KPI_OVERTIME, time,
                "Tăng ca — về lúc " + gio(time));
        log.info("[Checkin]  Tăng ca userId={} về lúc {} → +{} KPI (nhóm Thực chiến)",
                userId, time.toLocalTime(), KPI_OVERTIME);
    }

    private int calculateAttendanceKpi(String actionType, LocalTime time) {
        if ("CHECK_OUT".equals(actionType)) {
            return 0;
        }
        if (!time.isAfter(ON_TIME_LIMIT)) {
            return KPI_ON_TIME;
        }
        if (!time.isAfter(LATE_LIMIT)) {
            return KPI_LATE;
        }
        return KPI_ABSENT_UNEXCUSED;
    }

    /** Giờ Việt Nam dạng HH:mm để ghép vào câu diễn giải điểm. */
    private String gio(ZonedDateTime t) {
        return t.withZoneSameInstant(VN_ZONE).toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * Câu giải thích khoản điểm chuyên cần, khớp với {@link #calculateAttendanceKpi}.
     * Nhân sự đọc là biết ngay vì sao được cộng hay bị trừ.
     */
    private String dienGiaiChuyenCan(String actionType, ZonedDateTime time) {
        String hhmm = gio(time);
        if ("CHECK_OUT".equals(actionType)) {
            return "Chấm công ra ca lúc " + hhmm;
        }
        LocalTime t = time.withZoneSameInstant(VN_ZONE).toLocalTime();
        if (!t.isAfter(ON_TIME_LIMIT)) {
            return "Đi làm đúng giờ — chấm công lúc " + hhmm;
        }
        if (!t.isAfter(LATE_LIMIT)) {
            return "Đi muộn — chấm công lúc " + hhmm + " (hạn " + ON_TIME_LIMIT + ")";
        }
        return "Chấm công lúc " + hhmm + ", sau " + LATE_LIMIT + " nên tính vắng không phép";
    }
}
