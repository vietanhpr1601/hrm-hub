package hrm.model;

import hrm.enums.CheckInOutType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.Instant;

@Entity
@Table(name = "tbl_check_in_out_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckInOutRecord extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "record_time", nullable = false)
    private LocalDateTime recordTime;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "location_accuracy")
    private Double locationAccuracy;

    @Column(name = "location_captured_at")
    private Instant locationCapturedAt;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(name = "workplace_name")
    private String workplaceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false)
    private CheckInOutType recordType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shift_id")
    private ShiftWork shift;
}
