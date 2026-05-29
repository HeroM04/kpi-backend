package com.trilong.kpibackend.modules.battle.dto;

import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldBattleResponseDTO {
    private Long id;
    private Long userId;
    private String userFullName;
    private String customerName;
    private String customerPhone;
    private String project;
    private String content;
    private String photoUrl;
    private String status;
    private ZonedDateTime submittedAt;
    private ZonedDateTime approvedAt;
    private Long approvedById;
    private String approvedByFullName;

    public static FieldBattleResponseDTO from(FieldBattle battle) {
        if (battle == null) return null;
        return FieldBattleResponseDTO.builder()
                .id(battle.getId())
                .userId(battle.getUser().getId())
                .userFullName(battle.getUser().getFullName())
                .customerName(battle.getCustomerName())
                .customerPhone(battle.getCustomerPhone())
                .project(battle.getProject())
                .content(battle.getContent())
                .photoUrl(battle.getPhotoUrl())
                .status(battle.getStatus())
                .submittedAt(battle.getSubmittedAt())
                .approvedAt(battle.getApprovedAt())
                .approvedById(battle.getApprovedBy() != null ? battle.getApprovedBy().getId() : null)
                .approvedByFullName(battle.getApprovedBy() != null ? battle.getApprovedBy().getFullName() : null)
                .build();
    }
}
