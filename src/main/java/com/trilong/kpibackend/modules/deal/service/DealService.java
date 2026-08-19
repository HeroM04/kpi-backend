package com.trilong.kpibackend.modules.deal.service;

import com.trilong.kpibackend.modules.deal.dto.SubmitDealDTO;
import com.trilong.kpibackend.modules.deal.entity.Deal;
import com.trilong.kpibackend.modules.deal.repository.DealRepository;
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
public class DealService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final KpiCalculationService kpiCalculationService;

    @Transactional
    public Deal submitDeal(Long userId, SubmitDealDTO dto) {
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) java.util.Map.of("type", "DEAL", "message", "Co yeu cau chot deal moi!")); } catch(Exception e){}
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i dÃ¹ng"));

        Deal deal = Deal.builder()
                .user(user)
                .projectName(dto.getProjectName())
                .unit(dto.getUnit())
                .price(dto.getPrice())
                .commission(dto.getCommission())
                .customerName(dto.getCustomerName())
                .customerPhone(dto.getCustomerPhone())
                .contractPhotoUrl(dto.getContractPhotoUrl())
                .status("PENDING")
                .kpiTriggered(100) // Máº·c Ä‘á»‹nh cá»™ng 100 Ä‘iá»ƒm KPI
                .build();

        return dealRepository.save(deal);
    }

    public List<Deal> getMyDeals(Long userId) {
        return dealRepository.findByUserIdOrderBySubmittedAtDesc(userId);
    }

    public List<Deal> getDealsByStatus(String status) {
        return dealRepository.findByStatusOrderBySubmittedAtDesc(status);
    }

    public List<Deal> getAllDeals() {
        return dealRepository.findAllByOrderBySubmittedAtDesc();
    }

    public Deal getDealById(Long dealId) {
        return dealRepository.findById(dealId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y yÃªu cáº§u chá»‘t cÄƒn cÃ³ ID: " + dealId));
    }


    @Transactional
    public Deal approveDeal(Long dealId, Long approvedById) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y yÃªu cáº§u chá»‘t cÄƒn cÃ³ ID: " + dealId));

        User approver = userRepository.findById(approvedById)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i duyá»‡t"));

        // Náº¿u Ä‘Ã£ duyá»‡t rá»“i thÃ¬ khÃ´ng lÃ m gÃ¬
        if ("APPROVED".equals(deal.getStatus())) {
            return deal;
        }

        deal.setStatus("APPROVED");
        deal.setApprovedBy(approver);
        deal.setApprovedAt(ZonedDateTime.now());

        // Chốt căn KHÔNG cộng điểm vào bảng KPI.
        // Theo quy định: chốt được căn thì nghiễm nhiên đạt 100% KPI tháng đó,
        // nhưng ba nhóm tiêu chí (phát triển cá nhân / thực chiến / lan tỏa) vẫn
        // được chấm bình thường. Việc "đạt 100%" xét ở khâu xếp loại dựa trên
        // việc tháng đó có deal đã duyệt hay không, nên ở đây chỉ cần lưu bản ghi.
        deal.setKpiTriggered(0);
        return dealRepository.save(deal);
    }

    @Transactional
    public Deal rejectDeal(Long dealId, Long approvedById) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y yÃªu cáº§u chá»‘t cÄƒn cÃ³ ID: " + dealId));

        User approver = userRepository.findById(approvedById)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i duyá»‡t"));

        // Không cần hoàn điểm: chốt căn không cộng điểm vào bảng KPI.
        // Bỏ duyệt deal thì tháng đó mất tư cách "đạt 100% nhờ chốt căn",
        // việc này tự phản ánh khi xếp loại vì không còn deal APPROVED nào.
        deal.setStatus("REJECTED");
        deal.setApprovedBy(approver);
        deal.setApprovedAt(ZonedDateTime.now());

        return dealRepository.save(deal);
    }

    @Transactional
    public void deleteDeal(Long dealId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y yÃªu cáº§u chá»‘t cÄƒn cÃ³ ID: " + dealId));

        // Không cần hoàn điểm: chốt căn không cộng điểm vào bảng KPI.
        dealRepository.delete(deal);
    }
}
