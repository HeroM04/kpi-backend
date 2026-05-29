package com.trilong.kpibackend.modules.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "checkin_logs")
@Data
@NoArgsConstructor
public class CheckinLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tạm thời để Long, sau này làm module User xong ta map @ManyToOne sau cho khỏi lỗi
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "checkin_time")
    private ZonedDateTime checkinTime;

    @Column(name = "checkin_type")
    private String checkinType; // 'OFFICE' hoặc 'FIELD'

    private Double latitude;
    private Double longitude;

    @Column(name = "distance_to_office")
    private Double distanceToOffice;

    @Column(name = "photo_url")
    private String photoUrl;

    private String note;

    private String status; // 'APPROVED' (Tự động) hoặc 'PENDING' (Chờ duyệt)

    @Column(name = "action_type", length = 20)
    private String actionType; // 'CHECK_IN' hoặc 'CHECK_OUT'

    @PrePersist
    public void prePersist() {
        if(this.checkinTime == null) {
            this.checkinTime = ZonedDateTime.now();
        }
    }
}