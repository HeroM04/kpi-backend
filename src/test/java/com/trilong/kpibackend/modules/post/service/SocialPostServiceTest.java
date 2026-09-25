package com.trilong.kpibackend.modules.post.service;

import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.post.dto.SubmitPostDTO;
import com.trilong.kpibackend.modules.post.entity.SocialPost;
import com.trilong.kpibackend.modules.post.repository.SocialPostRepository;
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Duyệt / gỡ duyệt / từ chối / xóa bài đăng lan tỏa và điểm đi kèm. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocialPostServiceTest {

    @Mock SocialPostRepository repo;
    @Mock UserRepository userRepository;
    @Mock KpiCalculationService kpi;
    @InjectMocks SocialPostService service;

    private final User nhanSu = User.builder().id(7L).fullName("Sale A").build();
    private final ZonedDateTime nop = ZonedDateTime.of(2026, 9, 23, 6, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"));

    private SocialPost bai(String trangThai) {
        SocialPost p = SocialPost.builder().id(9L).user(nhanSu).platform("Facebook").link("https://fb.com/x")
                .status(trangThai).submittedAt(nop).build();
        when(repo.findById(9L)).thenReturn(Optional.of(p));
        return p;
    }

    @BeforeEach
    void chuanBi() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Duyệt bài → +5đ Lan tỏa")
    void duyet() {
        bai("PENDING");
        service.approvePost(9L, 1L);
        verify(kpi).updateKpiPoints(eq(7L), eq("post"), eq(5), eq(nop), startsWith("Admin duyệt: "));
    }

    @Test
    @DisplayName("Từ chối bài đã duyệt lúc nhóm đã đầy (vào 0đ) → không trừ gì")
    void tuChoiKhiDaDay() {
        bai("APPROVED");
        when(kpi.diemThucDaCong(7L, "post", "Admin duyệt: ", nop, 5)).thenReturn(0);
        service.rejectPost(9L, 1L);
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Sửa bài từ Đã duyệt về Chờ duyệt → gỡ đúng số đã cộng")
    void goDuyetQuaSua() {
        SocialPost p = bai("APPROVED");
        when(kpi.diemThucDaCong(anyLong(), anyString(), anyString(), any(), anyInt())).thenReturn(5);

        service.updatePost(9L, new SubmitPostDTO(), "PENDING", 1L);

        assertThat(p.getStatus()).isEqualTo("PENDING");
        assertThat(p.getApprovedBy()).isNull();
        verify(kpi).updateKpiPoints(eq(7L), eq("post"), eq(-5), eq(nop), startsWith("Admin gỡ duyệt: "));
    }

    @Test
    @DisplayName("Sửa bài từ Chờ duyệt sang Đã duyệt → cộng 5đ")
    void duyetQuaSua() {
        bai("PENDING");
        service.updatePost(9L, new SubmitPostDTO(), "APPROVED", 1L);
        verify(kpi).updateKpiPoints(eq(7L), eq("post"), eq(5), eq(nop), startsWith("Admin duyệt: "));
    }

    @Test
    @DisplayName("Sửa nội dung mà không đổi trạng thái → không đụng điểm")
    void suaNoiDung() {
        bai("APPROVED");
        SubmitPostDTO dto = new SubmitPostDTO();
        dto.setCaption("Chú thích mới");
        service.updatePost(9L, dto, "APPROVED", 1L);
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Xóa bài đã duyệt → gỡ điểm rồi mới xóa")
    void xoa() {
        SocialPost p = bai("APPROVED");
        when(kpi.diemThucDaCong(anyLong(), anyString(), anyString(), any(), anyInt())).thenReturn(5);
        service.deletePost(9L);
        verify(kpi).updateKpiPoints(eq(7L), eq("post"), eq(-5), any(), startsWith("Admin xóa bản ghi: "));
        verify(repo).delete(p);
    }
}
