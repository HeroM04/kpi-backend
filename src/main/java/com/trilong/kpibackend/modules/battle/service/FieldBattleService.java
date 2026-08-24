package com.trilong.kpibackend.modules.battle.service;

import com.trilong.kpibackend.modules.battle.dto.SubmitFieldBattleDTO;
import com.trilong.kpibackend.modules.battle.dto.FieldBattleResponseDTO;
import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.battle.repository.FieldBattleRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

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

    @Transactional
    public FieldBattle submitBattle(Long userId, SubmitFieldBattleDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i dÃ¹ng"));

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
    public FieldBattle rejectBattle(Long battleId, Long approvedById) {
        FieldBattle battle = fieldBattleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ¡o cÃ¡o thá»±c chiáº¿n cÃ³ ID: " + battleId));

        User approver = userRepository.findById(approvedById)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i duyá»‡t"));

        // Náº¿u Ä‘ang á»Ÿ tráº¡ng thÃ¡i APPROVED, pháº£i trá»« Ä‘iá»ƒm KPI trÆ°á»›c khi reject
        if ("APPROVED".equals(battle.getStatus())) {
            kpiCalculationService.updateKpiPoints(battle.getUser().getId(), "meeting", -diemCua(battle),
                    battle.getSubmittedAt(),
                    "Admin từ chối thực chiến — " + tenLoai(battle) + " " + battle.getCustomerName());
        }

        battle.setStatus("REJECTED");
        battle.setApprovedBy(approver);
        battle.setApprovedAt(ZonedDateTime.now());

        return fieldBattleRepository.save(battle);
    }

    @Transactional
    public void deleteBattle(Long battleId) {
        FieldBattle battle = fieldBattleRepository.findById(battleId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ¡o cÃ¡o thá»±c chiáº¿n cÃ³ ID: " + battleId));

        // Náº¿u Ä‘ang á»Ÿ tráº¡ng thÃ¡i APPROVED, pháº£i trá»« Ä‘iá»ƒm KPI trÆ°á»›c khi xÃ³a
        if ("APPROVED".equals(battle.getStatus())) {
            kpiCalculationService.updateKpiPoints(battle.getUser().getId(), "meeting", -diemCua(battle),
                    battle.getSubmittedAt(),
                    "Admin xóa báo cáo thực chiến — " + tenLoai(battle) + " " + battle.getCustomerName());
        }

        fieldBattleRepository.delete(battle);
    }
}
