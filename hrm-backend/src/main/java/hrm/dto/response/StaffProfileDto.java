package hrm.dto.response;

import hrm.model.Staff;
import hrm.security.PermissionPolicy;

import java.time.LocalDate;
import java.util.UUID;

/** A read-only personal profile; deliberately excludes payroll and account administration data. */
public record StaffProfileDto(
        UUID id,
        String staffCode,
        String displayName,
        String avatarUrl,
        LocalDate birthDate,
        String gender,
        String email,
        String phoneNumber,
        String companyEmail,
        String currentAddress,
        String permanentResidence,
        String currentResidence,
        String departmentName,
        String positionName,
        String workingStatus,
        LocalDate startDate,
        LocalDate resignationDate) {

    public static StaffProfileDto from(Staff staff) {
        return new StaffProfileDto(staff.getId(), staff.getStaffCode(), staff.getDisplayName(),
                staff.getAvatarUrl(), staff.getBirthDate(), staff.getGender(), staff.getEmail(),
                staff.getPhoneNumber(), staff.getCompanyEmail(), staff.getCurrentAddress(),
                staff.getPermanentResidence(), staff.getCurrentResidence(),
                PermissionPolicy.active(staff.getDepartment()) ? staff.getDepartment().getName() : null,
                PermissionPolicy.active(staff.getPosition()) ? staff.getPosition().getName() : null,
                staff.getWorkingStatus(), staff.getStartDate(), staff.getResignationDate());
    }
}
