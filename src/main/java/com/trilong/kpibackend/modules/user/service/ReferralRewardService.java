package com.trilong.kpibackend.modules.user.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiAutoGrant;
import com.trilong.kpibackend.modules.kpi.repository.KpiAutoGrantRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Gieo hạt nhân sự mới — +15đ cho người giới thiệu.
 *
 * <p>Quy định: giới thiệu được người vào công ty thì <b>một tháng sau</b>, nếu
 * người đó vẫn còn làm, người giới thiệu mới được cộng 15đ. Ví dụ giới thiệu
 * ngày 15/7, đến 15/8 người được giới thiệu vẫn đi làm thì mới cộng.
 *
 * <p>Điểm thuộc nhóm <b>Lan tỏa giá trị</b> (trần 30đ/tuần) — giới thiệu người
 * vào công ty cũng là lan tỏa giá trị ra bên ngoài.
 *
 * <p>Cách chạy: mỗi ngày quét lại toàn bộ nhân sự có người giới thiệu và đã qua
 * mốc tròn tháng, ai chưa được cộng thì cộng. Quét theo trạng thái hiện tại chứ
 * không bắt đúng một ngày duy nhất, nên máy chủ có nghỉ vài hôm cũng không mất
 * điểm của ai. Mỗi lần cộng ghi một {@link KpiAutoGrant} nên không cộng trùng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralRewardService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Điểm thưởng cho người giới thiệu khi nhân sự mới làm đủ một tháng. */
    public static final int KPI_REFERRAL = 15;

    /** Nhóm điểm — Lan tỏa giá trị. */
    private static final String CATEGORY = "post";

    private final UserRepository userRepository;
    private final KpiAutoGrantRepository kpiAutoGrantRepository;
    private final KpiCalculationService kpiCalculationService;

    /** Kết quả một lần chạy, để log và trả về cho Admin. */
    public record ReferralRunResult(int granted, List<String> details) {}

    @Transactional
    public ReferralRunResult grantMaturedReferrals() {
        LocalDate today = LocalDate.now(VN_ZONE);
        List<String> details = new ArrayList<>();
        int granted = 0;

        for (User newcomer : userRepository.findByReferrerIdIsNotNullAndStatus("ACTIVE")) {
            LocalDate joined = UserService.joinedDateOf(newcomer);
            if (joined == null) continue;

            LocalDate matureDate = joined.plusMonths(1);
            if (matureDate.isAfter(today)) continue; // chưa đủ một tháng

            Long referrerId = newcomer.getReferrerId();
            String grantType = grantTypeFor(newcomer.getId());
            String period = periodFor(matureDate, today);

            if (kpiAutoGrantRepository.existsByUserIdAndPeriodAndGrantType(referrerId, period, grantType)) {
                continue;
            }
            // Đã cộng ở một kỳ khác trước đây thì cũng không cộng lại
            if (alreadyGranted(referrerId, grantType)) continue;

            User referrer = userRepository.findById(referrerId).orElse(null);
            if (referrer == null || !"ACTIVE".equals(referrer.getStatus())) continue;

            ZonedDateTime creditAt = creditMoment(matureDate, today);
            kpiCalculationService.updateKpiPoints(referrerId, CATEGORY, KPI_REFERRAL, creditAt);

            KpiAutoGrant grant = new KpiAutoGrant();
            grant.setUserId(referrerId);
            grant.setPeriod(period);
            grant.setGrantType(grantType);
            grant.setCategory(CATEGORY);
            grant.setPoints(KPI_REFERRAL);
            grant.setReason("Giới thiệu " + newcomer.getFullName() + " vào công ty ngày "
                    + joined + ", đủ một tháng ngày " + matureDate);
            kpiAutoGrantRepository.save(grant);

            granted++;
            String line = referrer.getFullName() + " +" + KPI_REFERRAL + "đ (giới thiệu "
                    + newcomer.getFullName() + ", đủ tháng " + matureDate + ")";
            details.add(line);
            log.info("[Gieo hạt] {}", line);
        }

        return new ReferralRunResult(granted, details);
    }

    /** Điểm đã giới thiệu ai thì chỉ cộng đúng một lần, bất kể kỳ nào. */
    private boolean alreadyGranted(Long referrerId, String grantType) {
        return kpiAutoGrantRepository.findByUserIdOrderByGrantedAtDesc(referrerId).stream()
                .anyMatch(g -> grantType.equals(g.getGrantType()));
    }

    private String grantTypeFor(Long newcomerId) {
        return "REFERRAL_" + newcomerId;
    }

    /**
     * Cộng vào tuần chứa ngày tròn tháng. Nếu ngày đó đã thuộc tháng cũ (Admin
     * điền người giới thiệu muộn, hoặc máy chủ nghỉ dài) thì cộng vào tuần hiện
     * tại để không sửa lại bảng điểm tháng đã chốt.
     */
    private ZonedDateTime creditMoment(LocalDate matureDate, LocalDate today) {
        LocalDate credit = YearMonth.from(matureDate).equals(YearMonth.from(today)) ? matureDate : today;
        return credit.atTime(12, 0).atZone(VN_ZONE);
    }

    private String periodFor(LocalDate matureDate, LocalDate today) {
        return kpiCalculationService.getWeekString(creditMoment(matureDate, today));
    }
}
