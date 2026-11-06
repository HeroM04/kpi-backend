package com.trilong.kpibackend.modules.auth.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Khóa khôi phục mật khẩu của MỘT điện thoại đã đăng nhập thành công.
 *
 * <p>Nút "Quên mật khẩu" trên app đặt lại mật khẩu về mặc định mà không cần hỏi
 * Admin. Chỉ biết số điện thoại thì không đủ: số của đồng nghiệp ai cũng có,
 * nên ai cũng reset được tài khoản người khác rồi đăng nhập bằng 123456. Vì vậy
 * mỗi lần đăng nhập trên app, máy chủ cấp cho điện thoại đó một khóa ngẫu nhiên
 * (giữ trong kho bảo mật của máy, đăng xuất không xóa). Đặt lại mật khẩu phải
 * đưa ra đúng khóa này — tức là phải cầm đúng chiếc điện thoại từng đăng nhập
 * tài khoản đó.
 *
 * <p>Chỉ lưu mã băm SHA-256, không lưu khóa thật: lộ cơ sở dữ liệu cũng không
 * dùng được để reset.
 */
@Entity
@Table(name = "khoa_khoi_phuc", indexes = {
        @Index(name = "idx_khoa_khoi_phuc_ma_bam", columnList = "ma_bam", unique = true),
        @Index(name = "idx_khoa_khoi_phuc_user", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KhoaKhoiPhuc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /** SHA-256 (hex) của khóa đã cấp cho máy. */
    @Column(name = "ma_bam", nullable = false, length = 64)
    private String maBam;

    /** User-Agent lúc cấp — để tra khi cần. */
    @Column(name = "thiet_bi", length = 200)
    private String thietBi;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    /** Lần gần nhất khóa này được dùng để đặt lại mật khẩu. */
    @Column(name = "lan_dung_cuoi")
    private ZonedDateTime lanDungCuoi;
}
