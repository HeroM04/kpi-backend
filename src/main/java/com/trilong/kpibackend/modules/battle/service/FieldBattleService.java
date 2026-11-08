package com.trilong.kpibackend.modules.battle.service;

import com.trilong.kpibackend.core.utils.LyDoTuChoi;
import com.trilong.kpibackend.modules.battle.dto.SubmitFieldBattleDTO;
import com.trilong.kpibackend.modules.battle.dto.FieldBattleResponseDTO;
import com.trilong.kpibackend.modules.battle.dto.UpdateFieldBattleDTO;
import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.battle.repository.FieldBattleRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FieldBattleService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private final FieldBattleRepository fieldBattleRepository;
    private final UserRepository userRepository;
    private final KpiCalculationService kpiCalculationService;

    /** Trực tiếp gặp khách — +10đ mỗi báo cáo được duyệt. */
    private static final int KPI_POINTS_MEETING = 10;
    /** Hỗ trợ khách của người khác — +5đ mỗi báo cáo được duyệt. */
    private static final int KPI_POINTS_SUPPORT = 5;

    /** Chuẩn hóa loại thực chiến; giá trị lạ hoặc trống thì coi là gặp khách. */
    private String chuanHoaLoai(String raw) {
        return "SUPPORT".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? "SUPPORT" : "MEETING";
    }

    /** Số điểm của một báo cáo theo loại. */
    private int diemCua(FieldBattle battle) {
        return "SUPPORT".equals(chuanHoaLoai(battle.getBattleType()))
                ? KPI_POINTS_SUPPORT : KPI_POINTS_MEETING;
    }

    /** Tên loại để ghép vào nhật ký điểm. */
    private String tenLoai(FieldBattle battle) {
        return "SUPPORT".equals(chuanHoaLoai(battle.getBattleType()))
                ? "hỗ trợ khách" : "gặp khách";
    }

    /** Cùng người, cùng loại, cùng nội dung trong khoảng này thì coi là bấm Gửi lặp. */
    static final Duration CUA_SO_NOP_TRUNG = Duration.ofMinutes(2);

    @Transactional
    public FieldBattle submitBattle(Long userId, SubmitFieldBattleDTO dto) {
        // Khóa người gửi tới hết giao dịch. App chờ lấy GPS vài giây sau khi bấm
        // Gửi, người dùng tưởng chưa ăn nên bấm thêm 3–4 lần → các lượt tới cùng
        // một giây. Có khóa thì chúng xếp hàng, lượt sau thấy lượt trước đã lưu.
        User user = userRepository.khoaTheoId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        FieldBattle daGui = timBanVuaGui(userId, chuanHoaLoai(dto.getBattleType()), dto.getContent());
        if (daGui != null) {
            // Trả lại đúng bản đã có (app cũ vẫn báo "thành công"), không tạo bản trùng
            log.info("[ThucChien] Bỏ lượt gửi lặp của userId={} — trùng báo cáo #{}", userId, daGui.getId());
            return daGui;
        }

        FieldBattle battle = FieldBattle.builder()
                .user(user)
                .customerName(dto.getCustomerName() != null ? dto.getCustomerName() : "")
                .customerPhone(dto.getCustomerPhone())
                .project(dto.getProject() != null ? dto.getProject() : "")
                .content(dto.getContent())
                .photoUrl(dto.getPhotoUrl())
                .location(dto.getLocation())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .battleType(chuanHoaLoai(dto.getBattleType()))
                .status("PENDING")
                .build();

        FieldBattle savedData = fieldBattleRepository.save(battle);
        try {
            messagingTemplate.convertAndSend("/topic/thuc-chien/admin", FieldBattleResponseDTO.from(savedData));
        } catch(Exception e) {
            e.printStackTrace();
        }
        return savedData;
    }

    /** Báo cáo cùng loại, cùng nội dung (bỏ khác biệt hoa thường, dấu cách thừa) vừa gửi gần đây. */
    private FieldBattle timBanVuaGui(Long userId, String loai, String noiDung) {
        String k = chuanNoiDung(noiDung);
        return fieldBattleRepository
                .findByUserIdAndSubmittedAtAfter(userId, ZonedDateTime.now().minus(CUA_SO_NOP_TRUNG))
                .stream()
                .filter(b -> loai.equals(chuanHoaLoai(b.getBattleType())) && k.equals(chuanNoiDung(b.getContent())))
                .findFirst().orElse(null);
    }

    private static String chuanNoiDung(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public List<FieldBattle> getMyBattles(Long userId) {
        return fieldBattleRepository.findByUserIdOrderBySubmittedAtDesc(userId);
    }

    public List<FieldBattle> getBattlesByStatus(String status) {
        return fieldBattleRepository.findByStatusOrderBySubmittedAtDesc(status);
    }

    public List<FieldBattle> getAllBattles() {
        return fieldBattleRepository.findAllByOrderBySubmittedAtDesc();
    }

    public FieldBattle getBattleById(Long battleId) {
        return fieldBattleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ¡o cÃ¡o thá»±c chiáº¿n cÃ³ ID: " + battleId));
    }


    @Transactional
    public FieldBattle approveBattle(Long battleId, Long approvedById) {
        FieldBattle battle = fieldBattleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ¡o cÃ¡o thá»±c chiáº¿n cÃ³ ID: " + battleId));

        User approver = userRepository.findById(approvedById)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i duyá»‡t"));

        if ("APPROVED".equals(battle.getStatus())) {
            return battle;
        }

        battle.setStatus("APPROVED");
        battle.setRejectReason(null);
        battle.setApprovedBy(approver);
        battle.setApprovedAt(ZonedDateTime.now());
        FieldBattle savedBattle = fieldBattleRepository.save(battle);

        // Cá»™ng Ä‘iá»ƒm KPI thÃ¡ng gá»­i yÃªu cáº§u
        kpiCalculationService.updateKpiPoints(battle.getUser().getId(), "meeting", diemCua(battle),
                battle.getSubmittedAt(),
                "Admin duyệt thực chiến — " + tenLoai(battle) + " " + battle.getCustomerName());

        return savedBattle;
    }

    @Transactional
    public FieldBattle rejectBattle(Long battleId, Long approvedById, String lyDoTuChoi) {
        String lyDo = com.trilong.kpibackend.core.utils.LyDoTuChoi.batBuoc(lyDoTuChoi);
        FieldBattle battle = fieldBattleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ¡o cÃ¡o thá»±c chiáº¿n cÃ³ ID: " + battleId));

        User approver = userRepository.findById(approvedById)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i duyá»‡t"));

        // Đang APPROVED thì thu hồi đúng số điểm báo cáo này thực đã cộng
        thuHoiDiem(battle, "Admin từ chối thực chiến — ");

        battle.setStatus("REJECTED");
        battle.setRejectReason(lyDo);
        battle.setApprovedBy(approver);
        battle.setApprovedAt(ZonedDateTime.now());

        return fieldBattleRepository.save(battle);
    }

    /**
     * Admin sửa báo cáo trong modal "Sửa" trên web. Đổi trạng thái ở đây cộng /
     * thu hồi điểm đúng như bấm Duyệt / Từ chối, để hai đường sửa không lệch điểm.
     * Đổi loại (gặp khách ↔ hỗ trợ) khi đang duyệt thì gỡ điểm theo loại cũ rồi
     * cộng lại theo loại mới. Người nộp và giờ nộp không sửa được: điểm neo vào
     * đúng người, đúng tuần nộp.
     */
    @Transactional
    public FieldBattle updateBattle(Long battleId, UpdateFieldBattleDTO dto, Long editorId) {
        FieldBattle battle = fieldBattleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy báo cáo thực chiến có ID: " + battleId));

        String cu = battle.getStatus();
        String moi = chuanHoaTrangThai(dto.getStatus(), cu);
        boolean doiTrangThai = !moi.equals(cu);
        // Kiểm lý do trước mọi thay đổi: thiếu lý do thì báo lỗi, bản ghi và điểm giữ nguyên
        String lyDo = null;
        if ("REJECTED".equals(moi) && (doiTrangThai || !trong(dto.getRejectReason()))) {
            lyDo = LyDoTuChoi.batBuoc(dto.getRejectReason());
        }

        boolean dangDuyet = "APPROVED".equals(cu);
        boolean seDuyet = "APPROVED".equals(moi);
        String loaiMoi = trong(dto.getBattleType()) ? chuanHoaLoai(battle.getBattleType()) : chuanHoaLoai(dto.getBattleType());
        boolean doiLoai = !loaiMoi.equals(chuanHoaLoai(battle.getBattleType()));

        // Gỡ điểm theo loại CŨ, trước khi đổi loại
        if (dangDuyet && (!seDuyet || doiLoai)) {
            thuHoiDiem(battle, seDuyet ? "Admin đổi loại thực chiến — " : "Admin gỡ duyệt thực chiến — ");
        }

        if (dto.getCustomerName() != null) battle.setCustomerName(dto.getCustomerName().trim());
        if (dto.getCustomerPhone() != null) battle.setCustomerPhone(dto.getCustomerPhone().trim());
        if (dto.getProject() != null) battle.setProject(dto.getProject().trim());
        if (!trong(dto.getContent())) battle.setContent(dto.getContent().trim());   // cột NOT NULL, không cho xóa trắng
        if (dto.getLocation() != null) battle.setLocation(dto.getLocation());
        battle.setBattleType(loaiMoi);

        if (seDuyet && (!dangDuyet || doiLoai)) {
            kpiCalculationService.updateKpiPoints(battle.getUser().getId(), "meeting", diemCua(battle),
                    battle.getSubmittedAt(),
                    "Admin duyệt thực chiến — " + tenLoai(battle) + " " + battle.getCustomerName());
        }

        if (doiTrangThai) {
            battle.setStatus(moi);
            if (seDuyet || "REJECTED".equals(moi)) {
                userRepository.findById(editorId).ifPresent(battle::setApprovedBy);
                battle.setApprovedAt(ZonedDateTime.now());
            } else {
                battle.setApprovedBy(null);
                battle.setApprovedAt(null);
            }
        }
        if (!"REJECTED".equals(moi)) battle.setRejectReason(null);
        else if (lyDo != null) battle.setRejectReason(lyDo);

        return fieldBattleRepository.save(battle);
    }

    /** PENDING / APPROVED / REJECTED; trống thì giữ trạng thái cũ, giá trị lạ thì báo lỗi. */
    private static String chuanHoaTrangThai(String raw, String cu) {
        if (trong(raw)) return cu;
        String s = raw.trim().toUpperCase();
        if (!s.equals("PENDING") && !s.equals("APPROVED") && !s.equals("REJECTED")) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + raw);
        }
        return s;
    }

    private static boolean trong(String s) {
        return s == null || s.isBlank();
    }

    @Transactional
    public void deleteBattle(Long battleId) {
        FieldBattle battle = fieldBattleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ¡o cÃ¡o thá»±c chiáº¿n cÃ³ ID: " + battleId));

        // Đang APPROVED thì thu hồi đúng số điểm báo cáo này thực đã cộng
        thuHoiDiem(battle, "Admin xóa báo cáo thực chiến — ");

        fieldBattleRepository.delete(battle);
    }

    /**
     * Gỡ điểm của một báo cáo đã duyệt — đúng số THỰC đã cộng, không phải số quy
     * định. Nhóm Thực chiến trần 40đ/tuần: báo cáo duyệt lúc nhóm đã đầy thì vào
     * 0đ; trừ đại 10đ là ăn vào điểm của những lần gặp khách khác.
     */
    private void thuHoiDiem(FieldBattle battle, String dauCau) {
        if (!"APPROVED".equals(battle.getStatus())) return;
        Long uid = battle.getUser().getId();
        int dangGiu = kpiCalculationService.diemThucDaCong(uid, "meeting", "Admin duyệt thực chiến",
                battle.getSubmittedAt(), diemCua(battle));
        if (dangGiu <= 0) return;
        kpiCalculationService.updateKpiPoints(uid, "meeting", -dangGiu, battle.getSubmittedAt(),
                dauCau + tenLoai(battle) + " " + battle.getCustomerName());
    }
}
