package hrm.dto.response;

import java.util.List;

public record StaffImportResult(int totalRows, int importedCount, List<RowError> errors) {
    public record RowError(int row, String field, String message) {}
}
