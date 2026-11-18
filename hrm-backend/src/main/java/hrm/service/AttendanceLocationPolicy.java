package hrm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Shared acquisition settings and server-side acceptance limit, measured in meters. */
@Service
public class AttendanceLocationPolicy {
    private static final String PREFIX = "app.attendance.location.";
    private final Snapshot settings;

    public AttendanceLocationPolicy(
            @Value("${app.attendance.location.max-accuracy-meters:300}") String maxAccuracy,
            @Value("${app.attendance.location.target-accuracy-meters:20}") String targetAccuracy,
            @Value("${app.attendance.location.acquisition-timeout-ms:25000}") String timeout) {
        double maximum = positiveNumber(maxAccuracy, "max-accuracy-meters");
        double target = positiveNumber(targetAccuracy, "target-accuracy-meters");
        if (target > maximum) {
            throw new IllegalArgumentException("Cấu hình " + PREFIX + "target-accuracy-meters không được lớn hơn max-accuracy-meters.");
        }
        int timeoutMillis;
        try {
            timeoutMillis = Integer.parseInt(timeout.trim());
            if (timeoutMillis < 1000 || timeoutMillis > 60000) throw new NumberFormatException();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Cấu hình " + PREFIX + "acquisition-timeout-ms phải là số nguyên từ 1000 đến 60000 mili giây.");
        }
        settings = new Snapshot(maximum, target, timeoutMillis);
    }

    public record Snapshot(double maxAccuracyMeters, double targetAccuracyMeters, int acquisitionTimeoutMs) {}

    public Snapshot snapshot() {
        return settings;
    }

    public void requireAcceptableAccuracy(double accuracy) {
        if (accuracy > settings.maxAccuracyMeters()) {
            throw new IllegalArgumentException("Sai số GPS vượt quá " + format(settings.maxAccuracyMeters())
                    + " m cho phép. Vui lòng di chuyển tới nơi thoáng hoặc gần cửa sổ và lấy lại vị trí.");
        }
    }

    private static String format(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static double positiveNumber(String value, String property) {
        try {
            double number = Double.parseDouble(value.trim());
            if (!Double.isFinite(number) || number <= 0) throw new NumberFormatException();
            return number;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Cấu hình " + PREFIX + property + " phải là số hữu hạn lớn hơn 0.");
        }
    }
}
