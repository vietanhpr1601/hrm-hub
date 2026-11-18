package hrm.service.impl;

import hrm.dto.request.StaffDto;
import hrm.dto.response.StaffProfileDto;
import hrm.dto.search.SearchDto;
import hrm.exception.ResourceNotFoundException;
import hrm.model.Staff;
import hrm.model.Department;
import hrm.model.User;
import hrm.repository.DepartmentRepository;
import hrm.repository.PositionRepository;
import hrm.repository.StaffRepository;
import hrm.security.PermissionPolicy;
import hrm.security.SecurityUtils;
import hrm.service.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StaffServiceImpl implements StaffService {

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public StaffProfileDto getCurrentStaffProfile() {
        User currentUser = securityUtils.getCurrentUser();
        if (!PermissionPolicy.active(currentUser) || !Boolean.TRUE.equals(currentUser.getActive())) {
            throw new AuthenticationCredentialsNotFoundException("Vui lòng đăng nhập lại");
        }
        Staff staff = currentUser.getStaff();
        if (!PermissionPolicy.active(staff)) {
            throw new ResourceNotFoundException("Tài khoản chưa được liên kết với hồ sơ nhân viên đang hoạt động");
        }
        return StaffProfileDto.from(staff);
    }

    @Override
    public Page<StaffDto> getAllStaffs(SearchDto searchDto) {
        List<Staff> filteredList = staffRepository.findAll().stream()
                .filter(staff -> staff.getIsDeleted() == null || !staff.getIsDeleted())
                .filter(staff -> {
                    if (searchDto != null) {
                        // 1. Keyword search
                        if (searchDto.getKeyword() != null && !searchDto.getKeyword().isEmpty()) {
                            String keyword = searchDto.getKeyword().toLowerCase();
                            boolean matches = (staff.getStaffCode() != null && staff.getStaffCode().toLowerCase().contains(keyword))
                                || (staff.getDisplayName() != null && staff.getDisplayName().toLowerCase().contains(keyword))
                                || (staff.getEmail() != null && staff.getEmail().toLowerCase().contains(keyword))
                                || (staff.getPhoneNumber() != null && staff.getPhoneNumber().contains(keyword));
                            if (!matches) return false;
                        }
                        // 2. Department filter
                        if (searchDto.getDepartmentId() != null) {
                            if (staff.getDepartment() == null || !staff.getDepartment().getId().equals(searchDto.getDepartmentId())) {
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        int total = filteredList.size();
        int pageNum = 0;
        int size = 10;

        if (searchDto != null) {
            pageNum = searchDto.getPageIndex() >= 1 ? searchDto.getPageIndex() - 1 : 0;
            size = searchDto.getPageSize() > 0 ? searchDto.getPageSize() : 10;
        }

        int fromIndex = pageNum * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<StaffDto> pageContent = new java.util.ArrayList<>();
        if (fromIndex < total) {
            pageContent = filteredList.subList(fromIndex, toIndex).stream()
                    .map(StaffDto::new)
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(pageContent, PageRequest.of(pageNum, size), total);
    }

    @Override
    public List<StaffDto> getAllStaffsUnpaginated() {
        return staffRepository.findAll().stream()
                .filter(staff -> staff.getIsDeleted() == null || !staff.getIsDeleted())
                .map(StaffDto::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<StaffDto> getStaffById(UUID id) {
        return staffRepository.findById(id)
                .filter(staff -> staff.getIsDeleted() == null || !staff.getIsDeleted())
                .map(StaffDto::new);
    }

    @Override
    @Transactional
    public StaffDto saveStaff(StaffDto dto) {
        if (dto.getResignationDate() != null) {
            if (dto.getStartDate() != null && dto.getResignationDate().isBefore(dto.getStartDate())) {
                throw new IllegalArgumentException("Ngày nghỉ việc không được trước ngày vào làm.");
            }
            if (dto.getResignationDate().isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))) {
                throw new IllegalArgumentException("Ngày nghỉ việc thực tế không được ở tương lai.");
            }
        }
        // Validate email uniqueness if email is provided
        if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty()) {
            String email = dto.getEmail().trim();
            boolean emailExists;
            if (dto.getId() != null) {
                emailExists = staffRepository.existsByEmailAndIdNotAndActive(email, dto.getId());
            } else {
                emailExists = staffRepository.existsByEmailAndActive(email);
            }
            if (emailExists) {
                throw new hrm.exception.BadRequestException("Email đã tồn tại trong hệ thống");
            }
        }

        Staff staff;
        if (dto.getId() != null) {
            staff = staffRepository.findById(dto.getId()).filter(PermissionPolicy::active)
                    .orElseThrow(() -> new hrm.exception.ResourceNotFoundException("Nhân viên không tồn tại hoặc đã bị xóa"));
        } else {
            staff = new Staff();
        }

        Department department = dto.getDepartmentId() == null ? null
                : departmentRepository.findById(dto.getDepartmentId()).filter(PermissionPolicy::active)
                        .orElseThrow(() -> new IllegalArgumentException("Phòng ban không tồn tại hoặc đã bị xóa"));
        
        staff.setStaffCode(dto.getStaffCode());
        staff.setDisplayName(dto.getDisplayName());
        staff.setBirthDate(dto.getBirthDate());
        staff.setGender(dto.getGender());
        staff.setPhoneNumber(dto.getPhoneNumber());
        staff.setEmail(dto.getEmail());
        staff.setWorkingStatus(dto.getWorkingStatus());
        staff.setIdNumber(dto.getIdNumber());
        staff.setStartDate(dto.getStartDate());
        staff.setResignationDate(dto.getResignationDate());
        staff.setCurrentAddress(dto.getCurrentAddress());
        staff.setSocialInsuranceCode(dto.getSocialInsuranceCode());
        
        staff.setDepartment(department);
        
        if (dto.getPositionId() != null) {
            positionRepository.findById(dto.getPositionId()).ifPresent(staff::setPosition);
        } else {
            staff.setPosition(null);
        }

        // Map expanded fields
        staff.setAvatarUrl(dto.getAvatarUrl());
        staff.setBirthPlace(dto.getBirthPlace());
        staff.setNationality(dto.getNationality());
        staff.setEthnics(dto.getEthnics());
        staff.setReligion(dto.getReligion());
        staff.setEducationDegree(dto.getEducationDegree());
        staff.setProvince(dto.getProvince());
        staff.setCommune(dto.getCommune());
        staff.setPermanentResidence(dto.getPermanentResidence());
        staff.setCurrentResidence(dto.getCurrentResidence());
        staff.setIdNumberIssueDate(dto.getIdNumberIssueDate());
        staff.setIdNumberIssueBy(dto.getIdNumberIssueBy());
        staff.setCompanyEmail(dto.getCompanyEmail());
        staff.setTaxCode(dto.getTaxCode());
        staff.setHealthInsuranceNumber(dto.getHealthInsuranceNumber());
        staff.setBankName(dto.getBankName());
        staff.setBankAccountNumber(dto.getBankAccountNumber());
        staff.setBankAccountName(dto.getBankAccountName());
        staff.setBankBin(dto.getBankBin());

        Staff savedStaff = staffRepository.save(staff);
        return new StaffDto(savedStaff);
    }

    @Override
    @Transactional
    public void deleteStaff(UUID id) {
        staffRepository.findById(id).ifPresent(staff -> {
            staff.setIsDeleted(true);
            staffRepository.save(staff);
        });
    }

    @Override
    public boolean existsById(UUID id) {
        return staffRepository.findById(id)
                .map(staff -> staff.getIsDeleted() == null || !staff.getIsDeleted())
                .orElse(false);
    }

    @Override
    public String generateStaffCode() {
        // 1. Tạo tiền tố dựa trên năm và tháng hiện tại (Ví dụ: NV2605_)
        java.time.LocalDate now = java.time.LocalDate.now();
        String year = String.format("%02d", now.getYear() % 100);
        String month = String.format("%02d", now.getMonthValue());
        String prefix = "NV" + year + month + "_";

        // 2. Lấy tất cả mã nhân viên có định dạng NV..._... từ Repository
        java.util.List<String> codes = staffRepository.findMaxValidStaffCode();

        // 3. Tìm mã có số thứ tự (phần sau dấu _) lớn nhất trong toàn hệ thống
        String maxCode = codes.stream()
                .filter(code -> code.matches("^NV[0-9]{4}_[0-9]{3}$"))
                .max(java.util.Comparator.comparingInt(code -> 
                    Integer.parseInt(code.substring(code.indexOf("_") + 1))
                ))
                .orElse(null);

        int nextNumber = 1;
        if (maxCode != null) {
            try {
                String[] parts = maxCode.split("_");
                if (parts.length == 2) {
                    nextNumber = Integer.parseInt(parts[1]) + 1;
                }
            } catch (NumberFormatException ignored) {
                // Nếu parse lỗi thì mặc định bắt đầu từ 1
            }
        }

        // 4. Kết hợp tiền tố hiện tại với số thứ tự mới (Ví dụ: NV2605_005)
        return prefix + String.format("%03d", nextNumber);
    }
}
