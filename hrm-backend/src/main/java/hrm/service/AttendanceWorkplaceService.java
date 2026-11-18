package hrm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Optional workplace reference; distance is informational and never enforces a geofence. */
@Service
public class AttendanceWorkplaceService {
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;
    private final String workplaceName;
    private final Double workplaceLatitude;
    private final Double workplaceLongitude;

    public AttendanceWorkplaceService(
            @Value("${app.attendance.workplace.name:}") String name,
            @Value("${app.attendance.workplace.latitude:}") String latitude,
            @Value("${app.attendance.workplace.longitude:}") String longitude) {
        boolean latitudeMissing = latitude == null || latitude.isBlank();
        boolean longitudeMissing = longitude == null || longitude.isBlank();
        if (latitudeMissing != longitudeMissing) {
            throw new IllegalArgumentException("Cấu hình địa điểm chấm công phải có cả app.attendance.workplace.latitude và longitude, hoặc để trống cả hai.");
        }
        if (latitudeMissing) {
            workplaceName = null;
            workplaceLatitude = null;
            workplaceLongitude = null;
            return;
        }
        workplaceLatitude = parseCoordinate(latitude, 90, "latitude");
        workplaceLongitude = parseCoordinate(longitude, 180, "longitude");
        workplaceName = name == null || name.isBlank() ? "Địa điểm làm việc" : name.trim();
        if (workplaceName.length() > 255) {
            throw new IllegalArgumentException("Tên địa điểm chấm công tối đa 255 ký tự.");
        }
    }

    public record DistanceSnapshot(String workplaceName, Double distanceMeters) {}

    public DistanceSnapshot getDistance(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Tọa độ chấm công không hợp lệ.");
        }
        if (workplaceLatitude == null) {
            return new DistanceSnapshot(null, null);
        }
        double latitudeDelta = Math.toRadians(latitude - workplaceLatitude);
        double longitudeDelta = Math.toRadians(longitude - workplaceLongitude);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(workplaceLatitude)) * Math.cos(Math.toRadians(latitude))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        haversine = Math.max(0, Math.min(1, haversine));
        double meters = 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return new DistanceSnapshot(workplaceName, Math.round(meters * 10) / 10.0);
    }

    private static double parseCoordinate(String value, double limit, String property) {
        try {
            double coordinate = Double.parseDouble(value.trim());
            if (!Double.isFinite(coordinate) || Math.abs(coordinate) > limit) {
                throw new NumberFormatException();
            }
            return coordinate;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Cấu hình app.attendance.workplace." + property + " phải là số hữu hạn trong khoảng [-" + limit + ", " + limit + "].");
        }
    }
}
