package com.trilong.kpibackend.modules.deal.service;

import com.trilong.kpibackend.modules.deal.dto.UpdateDealDTO;
import com.trilong.kpibackend.modules.deal.entity.Deal;
import com.trilong.kpibackend.modules.deal.repository.DealRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Sửa yêu cầu chốt căn qua modal "Sửa" trên web (PUT /deals/{id}). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DealServiceTest {

    @Mock DealRepository dealRepository;
    @Mock UserRepository userRepository;
    @Mock KpiCalculationService kpi;
    @InjectMocks DealService service;

    private final User nhanSu = User.builder().id(7L).fullName("Sale A").build();
    private final User admin = User.builder().id(1L).fullName("Admin").build();

    private Deal deal(String trangThai) {
        Deal d = Deal.builder().id(30L).user(nhanSu).projectName("Vista").unit("S1.01-01").price(4.5e9)
                .commission(1.35e8).customerName("Anh Tuấn").customerPhone("0901234567")
                .status(trangThai).kpiTriggered(100).build();
        when(dealRepository.findById(30L)).thenReturn(Optional.of(d));
        return d;
    }

    private UpdateDealDTO sua(String trangThai, String lyDo) {
        UpdateDealDTO dto = new UpdateDealDTO();
        dto.setStatus(trangThai);
        dto.setRejectReason(lyDo);
        return dto;
    }

    @BeforeEach
    void chuanBi() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(dealRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Sửa thông tin giao dịch, giữ trạng thái → ghi giá trị mới; ô trống thì giữ cũ")
    void suaThongTin() {
        Deal d = deal("PENDING");
        UpdateDealDTO dto = sua(null, null);
        dto.setUnit(" S1.02-05 ");
        dto.setPrice(5e9);
        dto.setCustomerName("  ");

        service.updateDeal(30L, dto, 1L);

        assertThat(d.getStatus()).isEqualTo("PENDING");
        assertThat(d.getUnit()).isEqualTo("S1.02-05");
        assertThat(d.getPrice()).isEqualTo(5e9);
        assertThat(d.getCustomerName()).isEqualTo("Anh Tuấn");
        assertThat(d.getApprovedBy()).isNull();
    }

    @Test
    @DisplayName("Sửa sang Đã duyệt → ghi người duyệt, kpiTriggered = 0 như bấm Duyệt; không cộng điểm KPI")
    void suaSangDuyet() {
        Deal d = deal("PENDING");

        service.updateDeal(30L, sua("APPROVED", null), 1L);

        assertThat(d.getStatus()).isEqualTo("APPROVED");
        assertThat(d.getApprovedBy()).isEqualTo(admin);
        assertThat(d.getApprovedAt()).isNotNull();
        assertThat(d.getKpiTriggered()).isZero();
        verifyNoInteractions(kpi);
    }

    @Test
    @DisplayName("Sửa sang Từ chối mà thiếu lý do → báo lỗi, giao dịch giữ nguyên")
    void suaSangTuChoiThieuLyDo() {
        Deal d = deal("APPROVED");

        assertThatThrownBy(() -> service.updateDeal(30L, sua("REJECTED", null), 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lý do");

        assertThat(d.getStatus()).isEqualTo("APPROVED");
        verify(dealRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa sang Từ chối có lý do → lưu lý do; về lại Chờ duyệt thì gỡ lý do và người duyệt")
    void tuChoiRoiVeChoDuyet() {
        Deal d = deal("APPROVED");

        service.updateDeal(30L, sua("REJECTED", "  Sai mã căn  "), 1L);
        assertThat(d.getStatus()).isEqualTo("REJECTED");
        assertThat(d.getRejectReason()).isEqualTo("Sai mã căn");
        assertThat(d.getApprovedBy()).isEqualTo(admin);

        service.updateDeal(30L, sua("PENDING", "Sai mã căn"), 1L);
        assertThat(d.getStatus()).isEqualTo("PENDING");
        assertThat(d.getRejectReason()).isNull();
        assertThat(d.getApprovedBy()).isNull();
        assertThat(d.getApprovedAt()).isNull();
        verifyNoInteractions(kpi);
    }

    @Test
    @DisplayName("Trạng thái lạ → báo lỗi, không lưu")
    void trangThaiLa() {
        deal("PENDING");
        assertThatThrownBy(() -> service.updateDeal(30L, sua("XONG", null), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dealRepository, never()).save(any());
    }
}
