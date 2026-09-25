package com.trilong.kpibackend.modules.user.service;

import com.trilong.kpibackend.core.security.HoSoNongService;
import com.trilong.kpibackend.modules.user.dto.DepartmentDTO;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.DepartmentRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Phòng ban: tên không trùng, sửa từng phần tọa độ, xóa thì nhân sự về "chưa phân phòng". */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentServiceTest {

    @Mock DepartmentRepository repo;
    @Mock UserRepository userRepository;
    @Mock HoSoNongService hoSoNong;
    @InjectMocks DepartmentService service;

    private final Department kd8 = Department.builder().id(8L).name("Phòng Kinh Doanh 8")
            .officeLat(20.99).officeLng(105.78).allowedRadius(5000).build();

    @BeforeEach
    void chuanBi() {
        when(repo.findAll()).thenReturn(List.of(kd8));
        when(repo.findById(8L)).thenReturn(Optional.of(kd8));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private DepartmentDTO dto(String ten) {
        DepartmentDTO d = new DepartmentDTO();
        d.setName(ten);
        return d;
    }

    @Test
    @DisplayName("Tên trùng (khác hoa thường, thừa dấu cách) → từ chối")
    void trungTen() {
        assertThatThrownBy(() -> service.createDepartment(dto("  phòng kinh doanh 8 ")))
                .hasMessageContaining("Đã có phòng ban");
    }

    @Test
    @DisplayName("Lưu lại chính tên cũ của mình khi sửa → được")
    void suaGiuTen() {
        service.updateDepartment(8L, dto("Phòng Kinh Doanh 8"));
        assertThat(kd8.getName()).isEqualTo("Phòng Kinh Doanh 8");
    }

    @Test
    @DisplayName("Sửa từng phần: chỉ đổi bán kính thì tọa độ giữ nguyên")
    void suaTungPhan() {
        DepartmentDTO d = new DepartmentDTO();
        d.setAllowedRadius(300);
        service.updateDepartment(8L, d);
        assertThat(kd8.getAllowedRadius()).isEqualTo(300);
        assertThat(kd8.getOfficeLat()).isEqualTo(20.99);
        assertThat(kd8.getName()).isEqualTo("Phòng Kinh Doanh 8");
    }

    @Test
    @DisplayName("Xóa phòng → nhân sự về chưa phân phòng, quyền của họ cập nhật ngay")
    void xoaPhong() {
        User a = User.builder().id(1L).department(kd8).build();
        User b = User.builder().id(2L).department(kd8).build();
        when(repo.existsById(8L)).thenReturn(true);
        when(userRepository.findByFilters(8L, null, null)).thenReturn(List.of(a, b));

        service.deleteDepartment(8L);

        assertThat(a.getDepartment()).isNull();
        assertThat(b.getDepartment()).isNull();
        verify(hoSoNong).quen(1L);
        verify(hoSoNong).quen(2L);
        verify(repo).deleteById(8L);
    }
}
