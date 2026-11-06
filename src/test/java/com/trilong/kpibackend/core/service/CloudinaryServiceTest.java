package com.trilong.kpibackend.core.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.trilong.kpibackend.core.utils.ThuNhoAnh;
import com.trilong.kpibackend.core.utils.ThuNhoAnhTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/** Ảnh gửi lên Cloudinary: ảnh trên 10 MB được nén trước, ảnh thường gửi nguyên. */
class CloudinaryServiceTest {

    Uploader uploader;
    CloudinaryService service;

    @BeforeEach
    void chuanBi() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://res.cloudinary.com/x/anh.jpg"));
        service = new CloudinaryService(cloudinary);
    }

    private byte[] daGui() throws Exception {
        ArgumentCaptor<Object> bat = ArgumentCaptor.forClass(Object.class);
        verify(uploader).upload(bat.capture(), anyMap());
        return (byte[]) bat.getValue();
    }

    @Test
    @DisplayName("ảnh chụp màn hình 11 MB → nén dưới 10 MB rồi mới gửi (trước đây Cloudinary báo 'File size too large')")
    void anhQuaTran() throws Exception {
        byte[] goc = ThuNhoAnhTest.png(ThuNhoAnhTest.nhieu(1290, 2796));
        assertThat(goc.length).isGreaterThan(ThuNhoAnh.TRAN_CLOUDINARY);

        String url = service.uploadImage(new MockMultipartFile("file", "chup.png", "image/png", goc));

        assertThat(url).isEqualTo("https://res.cloudinary.com/x/anh.jpg");
        byte[] gui = daGui();
        assertThat(gui.length).isLessThan(ThuNhoAnh.TRAN_CLOUDINARY);
        assertThat(ThuNhoAnhTest.laJpeg(gui)).isTrue();
    }

    @Test
    @DisplayName("ảnh nhỏ → gửi nguyên, không nén lại")
    void anhNho() throws Exception {
        byte[] goc = ThuNhoAnhTest.png(ThuNhoAnhTest.nhieu(300, 200));

        service.uploadImage(new MockMultipartFile("file", "nho.png", "image/png", goc));

        assertThat(daGui()).isEqualTo(goc);
    }
}
