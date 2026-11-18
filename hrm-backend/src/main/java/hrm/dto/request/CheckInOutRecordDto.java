package hrm.dto.request;

import hrm.enums.CheckInOutType;
import hrm.model.CheckInOutRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckInOutRecordDto {
    private UUID id;
    private UUID staffId;
    private String staffName;
    private LocalDateTime recordTime;
    private String ipAddress;
    private Double latitude;
    private Double longitude;
    private Double locationAccuracy;
    private Instant locationCapturedAt;
    private String deviceType;
    private Double distanceMeters;
    private String workplaceName;
    private CheckInOutType recordType;
    private UUID shiftId;
    private String shiftName;

    public CheckInOutRecordDto(CheckInOutRecord entity) {
        if (entity != null) {
            this.id = entity.getId();
            if (entity.getStaff() != null) {
                this.staffId = entity.getStaff().getId();
                this.staffName = entity.getStaff().getDisplayName();
            }
            this.recordTime = entity.getRecordTime();
            this.ipAddress = entity.getIpAddress();
            this.latitude = entity.getLatitude();
            this.longitude = entity.getLongitude();
            this.locationAccuracy = entity.getLocationAccuracy();
            this.locationCapturedAt = entity.getLocationCapturedAt();
            this.deviceType = entity.getDeviceType();
            this.distanceMeters = entity.getDistanceMeters();
            this.workplaceName = entity.getWorkplaceName();
            this.recordType = entity.getRecordType();
            if (entity.getShift() != null) {
                this.shiftId = entity.getShift().getId();
                this.shiftName = entity.getShift().getName();
            }
        }
    }
}
