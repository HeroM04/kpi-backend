package com.trilong.kpibackend.modules.attendance.repository;

import com.trilong.kpibackend.modules.attendance.entity.NgayNghiLe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NgayNghiLeRepository extends JpaRepository<NgayNghiLe, Long> {

    boolean existsByNgay(LocalDate ngay);

    Optional<NgayNghiLe> findByNgay(LocalDate ngay);

    List<NgayNghiLe> findAllByOrderByNgayAsc();

    List<NgayNghiLe> findByNgayBetween(LocalDate tu, LocalDate den);
}
