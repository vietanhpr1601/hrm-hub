package hrm;

import hrm.service.AttendanceWorkplaceService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttendanceWorkplaceTests {
    @Test
    void noCoordinatesMeansNoDistanceOrPresetOffice() {
        var result = new AttendanceWorkplaceService("Office", "", "").getDistance(10.77, 106.69);
        assertNull(result.distanceMeters());
        assertNull(result.workplaceName());
    }

    @Test
    void knownCoordinatesProduceExpectedHaversineDistance() {
        var service = new AttendanceWorkplaceService(" Office ", "0", "0");
        assertEquals(0.0, service.getDistance(0, 0).distanceMeters());
        assertEquals(111195.1, service.getDistance(0, 1).distanceMeters(), 0.1);
        assertEquals(111195.1, service.getDistance(1, 0).distanceMeters(), 0.1);
        assertEquals("Office", service.getDistance(0, 0).workplaceName());
        assertTrue(Double.isFinite(service.getDistance(0, 180).distanceMeters()));
    }

    @Test
    void incompleteInvalidOrNonFiniteConfigurationFailsClearly() {
        for (String[] pair : new String[][]{{"", "0"}, {"0", ""}, {"91", "0"}, {"0", "181"},
                {"not-a-number", "0"}, {"NaN", "0"}, {"0", "Infinity"}}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new AttendanceWorkplaceService("Office", pair[0], pair[1]));
            assertTrue(error.getMessage().contains("app.attendance.workplace"));
        }
    }
}
