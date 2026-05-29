package com.trilong.kpibackend.modules.training.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingAttendeeId implements Serializable {
    private Long sessionId;
    private Long userId;
}
