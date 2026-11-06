package com.trilong.kpibackend.modules.auth.repository;

import com.trilong.kpibackend.modules.auth.entity.KhoaKhoiPhuc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KhoaKhoiPhucRepository extends JpaRepository<KhoaKhoiPhuc, Long> {

    @Query("SELECT k FROM KhoaKhoiPhuc k JOIN FETCH k.user WHERE k.maBam = :maBam")
    Optional<KhoaKhoiPhuc> timTheoMaBam(@Param("maBam") String maBam);

    /** Mới nhất trước — để giữ vài khóa gần nhất, xóa phần cũ. */
    @Query("SELECT k FROM KhoaKhoiPhuc k WHERE k.user.id = :userId ORDER BY k.createdAt DESC, k.id DESC")
    List<KhoaKhoiPhuc> cuaNhanSu(@Param("userId") Long userId);

    /** Xóa mọi khóa của một người — khi đá mọi thiết bị hay gỡ một thiết bị. */
    @Modifying
    @Query("DELETE FROM KhoaKhoiPhuc k WHERE k.user.id = :userId")
    int xoaHet(@Param("userId") Long userId);

    /** Xóa mọi khóa của một người trừ một khóa (máy vừa tự đặt lại mật khẩu). */
    @Modifying
    @Query("DELETE FROM KhoaKhoiPhuc k WHERE k.user.id = :userId AND k.id <> :giuId")
    int xoaHetTru(@Param("userId") Long userId, @Param("giuId") Long giuId);
}
