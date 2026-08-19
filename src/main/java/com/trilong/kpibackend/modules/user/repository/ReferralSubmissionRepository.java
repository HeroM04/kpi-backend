package com.trilong.kpibackend.modules.user.repository;

import com.trilong.kpibackend.modules.user.entity.ReferralSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralSubmissionRepository extends JpaRepository<ReferralSubmission, Long> {

    List<ReferralSubmission> findByReferrerIdOrderBySubmittedAtDesc(Long referrerId);

    List<ReferralSubmission> findByStatusOrderBySubmittedAtAsc(String status);

    List<ReferralSubmission> findAllByOrderBySubmittedAtDesc();

    /** Chặn hai người cùng giới thiệu một số điện thoại đang chờ duyệt. */
    Optional<ReferralSubmission> findByCandidatePhoneAndStatus(String candidatePhone, String status);
}
