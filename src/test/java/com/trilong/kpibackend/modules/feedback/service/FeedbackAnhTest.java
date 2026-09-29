package com.trilong.kpibackend.modules.feedback.service;

import com.trilong.kpibackend.core.storage.AnhRiengTuService;
import com.trilong.kpibackend.modules.feedback.entity.Feedback;
import com.trilong.kpibackend.modules.feedback.repository.FeedbackRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Góp ý kèm ảnh chụp màn hình: ảnh lên S3, DB chỉ giữ khóa. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackAnhTest {

    @Mock FeedbackRepository feedbackRepository;
    @Mock UserRepository userRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock AnhRiengTuService anhRiengTu;
    @InjectMocks FeedbackService service;

    private int dem = 0;

    @BeforeEach
    void chuanBi() throws Exception {
        when(feedbackRepository.saveAndFlush(any())).thenAnswer(i -> {
            Feedback f = i.getArgument(0);
            f.setId(90L);
            return f;
        });
        when(anhRiengTu.taiLen(any(), eq("feedback"))).thenAnswer(i -> "feedback/2026/09/anh-" + (++dem) + ".jpg");
        when(anhRiengTu.linkXem(any())).thenAnswer(i -> "https://s3.example/" + i.getArgument(0) + "?ky=tam");
        when(userRepository.findById(any())).thenReturn(Optional.empty());
    }

    private MultipartFile anh() {
        return new MockMultipartFile("images", "loi.png", "image/png", new byte[]{1, 2, 3});
    }

    private Map<String, Object> noiDung() {
        return new java.util.HashMap<>(Map.of("title", "App treo khi chấm công", "content", "Ảnh bên dưới", "category", "Lỗi app"));
    }

    @Test
    @DisplayName("Gửi kèm 2 ảnh → DB lưu 2 khóa S3, trả về 2 link xem ký tạm")
    void guiKemAnh() throws Exception {
        var kq = service.createWithImages(7L, noiDung(), List.of(anh(), anh()));

        ArgumentCaptor<Feedback> luu = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).saveAndFlush(luu.capture());
        assertThat(luu.getValue().getImageKeys()).isEqualTo("feedback/2026/09/anh-1.jpg,feedback/2026/09/anh-2.jpg");
        assertThat(kq.getImageUrls()).hasSize(2).allMatch(u -> u.startsWith("https://s3.example/feedback/"));
    }

    @Test
    @DisplayName("Quá 5 ảnh → từ chối trước khi đưa ảnh nào lên S3")
    void quaNamAnh() throws Exception {
        List<MultipartFile> sauAnh = new ArrayList<>();
        for (int i = 0; i < 6; i++) sauAnh.add(anh());

        assertThatThrownBy(() -> service.createWithImages(7L, noiDung(), sauAnh))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5 ảnh");
        verify(anhRiengTu, never()).taiLen(any(), any());
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Ảnh thứ hai lỗi → xóa ảnh thứ nhất đã lên S3, không lưu góp ý dở dang")
    void loiGiuaChungDonAnh() throws Exception {
        when(anhRiengTu.taiLen(any(), eq("feedback")))
                .thenReturn("feedback/2026/09/dau.jpg")
                .thenThrow(new IllegalArgumentException("Chỉ nhận ảnh JPG, PNG hoặc WebP."));

        assertThatThrownBy(() -> service.createWithImages(7L, noiDung(), List.of(anh(), anh())))
                .hasMessageContaining("JPG");
        verify(anhRiengTu).xoa("feedback/2026/09/dau.jpg");
        verify(feedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Chỉ gửi ảnh không ghi chữ → vẫn lưu được, nội dung ghi 'xem ảnh đính kèm'")
    void chiCoAnh() throws Exception {
        Map<String, Object> rong = new java.util.HashMap<>();
        rong.put("title", "Lỗi");
        var kq = service.createWithImages(7L, rong, List.of(anh()));
        assertThat(kq.getContent()).contains("ảnh đính kèm");
    }

    @Test
    @DisplayName("Góp ý không ảnh (app cũ gửi JSON) vẫn chạy, danh sách ảnh rỗng")
    void khongAnh() {
        var kq = service.createAndBroadcastFeedback(7L, noiDung());
        assertThat(kq.getImageUrls()).isEmpty();
        verifyNoInteractions(anhRiengTu);
    }

    @Test
    @DisplayName("Ẩn danh gửi dạng chữ 'true' từ form multipart vẫn được hiểu là ẩn danh")
    void anDanhDangChu() throws Exception {
        Map<String, Object> m = noiDung();
        m.put("isAnonymous", "true");
        var kq = service.createWithImages(7L, m, List.of());
        assertThat(kq.isAnonymous()).isTrue();
        assertThat(kq.getSenderId()).isNull();
        assertThat(FeedbackService.laDung(false)).isFalse();
        assertThat(FeedbackService.laDung(null)).isFalse();
    }

    @Test
    @DisplayName("Tách/ghép chuỗi khóa ảnh lưu trong DB")
    void tachGhepKhoa() {
        assertThat(FeedbackService.tachKhoa(null)).isEmpty();
        assertThat(FeedbackService.tachKhoa(" a.jpg , ,b.png")).containsExactly("a.jpg", "b.png");
        assertThat(FeedbackService.ghepKhoa(List.of())).isNull();
        assertThat(FeedbackService.ghepKhoa(List.of("a.jpg", "b.png"))).isEqualTo("a.jpg,b.png");
    }

    @Test
    @DisplayName("Xóa góp ý → xóa luôn ảnh trên S3")
    void xoaGopYXoaAnh() {
        Feedback f = Feedback.builder().id(90L).content("x").targetType("COMPANY")
                .imageKeys("feedback/2026/09/a.jpg,feedback/2026/09/b.jpg").build();
        when(feedbackRepository.findById(90L)).thenReturn(Optional.of(f));

        service.deleteFeedback(90L);

        verify(feedbackRepository).delete(f);
        verify(anhRiengTu).xoa("feedback/2026/09/a.jpg");
        verify(anhRiengTu).xoa("feedback/2026/09/b.jpg");
    }
}
