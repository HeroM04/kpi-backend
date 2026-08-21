package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.training.dto.OneOnOneTrainingDto;
import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import com.trilong.kpibackend.modules.training.repository.OneOnOneTrainingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OneOnOneTrainingService {

    private final OneOnOneTrainingRepository oneOnOneTrainingRepository;
    private final KpiCalculationService kpiCalculationService;

    // 5 points for 1-1 training auto-approved
    private static final int KPI_POINTS_ONE_ON_ONE = 5;

    @Transactional
    public OneOnOneTrainingDto submitOneOnOneTraining(Long userId, String content, String photoUrl) {
        OneOnOneTraining training = OneOnOneTraining.builder()
                .userId(userId)
                .content(content)
                .photoUrl(photoUrl)
                .status("APPROVED") // Auto-approve
                .build();

        training = oneOnOneTrainingRepository.save(training);

        // Auto add 5 KPI points
        kpiCalculationService.updateKpiPoints(userId, "meeting", KPI_POINTS_ONE_ON_ONE,
                java.time.ZonedDateTime.now(), "Báo cáo đào tạo 1-1");

        return mapToDto(training);
    }

    public List<OneOnOneTrainingDto> getAllOneOnOneTrainings() {
        return oneOnOneTrainingRepository.findAllWithUser().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private OneOnOneTrainingDto mapToDto(OneOnOneTraining entity) {
        OneOnOneTrainingDto dto = new OneOnOneTrainingDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        if (entity.getUser() != null) {
            dto.setUserName(entity.getUser().getFullName());
            dto.setUserAvatar(entity.getUser().getAvatarUrl());
        }
        dto.setContent(entity.getContent());
        dto.setPhotoUrl(entity.getPhotoUrl());
        dto.setStatus(entity.getStatus());
        dto.setSubmittedAt(entity.getSubmittedAt() != null ? entity.getSubmittedAt() : java.time.ZonedDateTime.now());
        return dto;
    }
}
