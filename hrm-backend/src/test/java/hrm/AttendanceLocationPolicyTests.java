package hrm;

import hrm.service.AttendanceLocationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceLocationPolicyTests {
    @Test
    void defaultSpringPolicyHasThreeHundredMeterLimitAndTwentyFiveSecondAcquisition() {
        try (var context = context(Map.of())) {
            var policy = context.getBean(AttendanceLocationPolicy.class).snapshot();
            assertEquals(300.0, policy.maxAccuracyMeters());
            assertEquals(20.0, policy.targetAccuracyMeters());
            assertEquals(25000, policy.acquisitionTimeoutMs());
        }
    }

    @Test
    void springPropertyOverridesAreSharedWithoutChangingTheirValues() {
        try (var context = context(Map.of(
                "app.attendance.location.max-accuracy-meters", "12.5",
                "app.attendance.location.target-accuracy-meters", "8",
                "app.attendance.location.acquisition-timeout-ms", "30000"))) {
            AttendanceLocationPolicy service = context.getBean(AttendanceLocationPolicy.class);
            var settings = service.snapshot();
            assertEquals(12.5, settings.maxAccuracyMeters());
            assertEquals(8.0, settings.targetAccuracyMeters());
            assertEquals(30000, settings.acquisitionTimeoutMs());
            assertDoesNotThrow(() -> service.requireAcceptableAccuracy(12.5));
            assertThrows(IllegalArgumentException.class, () -> service.requireAcceptableAccuracy(12.51));
        }
    }

    @Test
    void malformedOrInconsistentConfigurationFailsDuringStartupWithPropertyName() {
        for (String[] values : new String[][]{
                {"NaN", "20", "25000"}, {"Infinity", "20", "25000"}, {"0", "20", "25000"},
                {"-1", "20", "25000"}, {"abc", "20", "25000"}, {"", "20", "25000"},
                {"50", "NaN", "25000"}, {"50", "Infinity", "25000"}, {"50", "0", "25000"},
                {"50", "-1", "25000"}, {"50", "51", "25000"}, {"50", "", "25000"},
                {"50", "20", "999"}, {"50", "20", "60001"}, {"50", "20", "25000.5"},
                {"50", "20", "NaN"}, {"50", "20", ""}}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("invalid", Map.of(
                        "app.attendance.location.max-accuracy-meters", values[0],
                        "app.attendance.location.target-accuracy-meters", values[1],
                        "app.attendance.location.acquisition-timeout-ms", values[2])));
                context.register(AttendanceLocationPolicy.class);
                Throwable failure = assertThrows(RuntimeException.class, context::refresh);
                while (failure.getCause() != null) failure = failure.getCause();
                assertInstanceOf(IllegalArgumentException.class, failure);
                assertTrue(failure.getMessage().startsWith("Cấu hình app.attendance.location."));
            }
        }
    }

    @Test
    void timeoutBoundariesAndEqualTargetAreAllowed() {
        assertEquals(1000, new AttendanceLocationPolicy("50", "50", "1000").snapshot().acquisitionTimeoutMs());
        assertEquals(60000, new AttendanceLocationPolicy("50", "20", "60000").snapshot().acquisitionTimeoutMs());
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("location-test", properties));
        context.register(AttendanceLocationPolicy.class);
        context.refresh();
        return context;
    }
}
