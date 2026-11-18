package hrm.service.impl;

import hrm.model.SalaryItem;
import hrm.model.Staff;
import hrm.model.StaffSalaryItem;
import hrm.enums.SalaryCalculationType;
import hrm.repository.SalaryItemRepository;
import hrm.repository.StaffRepository;
import hrm.repository.StaffSalaryItemRepository;
import hrm.service.SalaryItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SalaryItemServiceImpl implements SalaryItemService {

    @Autowired
    private SalaryItemRepository salaryItemRepository;

    @Autowired
    private StaffSalaryItemRepository staffSalaryItemRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Override
    public List<SalaryItem> getAllSalaryItems() {
        return salaryItemRepository.findAll().stream()
                .filter(item -> item.getIsDeleted() == null || !item.getIsDeleted())
                .collect(Collectors.toList());
    }

    @Override
    public SalaryItem saveSalaryItem(SalaryItem item) {
        if (item == null) throw new IllegalArgumentException("Khoản lương là bắt buộc");
        requireCurrentFormula(item.getCalculationType());
        if (item.getCode() != null) {
            String finalCode = item.getCode().trim().toUpperCase();
            java.util.Optional<SalaryItem> existing = salaryItemRepository.findActiveByCode(finalCode);
            if (existing.isPresent() && (item.getId() == null || !existing.get().getId().equals(item.getId()))) {
                throw new IllegalArgumentException("Mã khoản lương '" + finalCode + "' đã tồn tại");
            }
            item.setCode(finalCode);
        }

        if (item.getId() != null) {
            SalaryItem existing = salaryItemRepository.findById(item.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Khoản lương không tồn tại"));
            existing.setName(item.getName());
            existing.setCode(item.getCode());
            existing.setType(item.getType());
            existing.setCalculationType(item.getCalculationType());
            existing.setDescription(item.getDescription());
            return salaryItemRepository.save(existing);
        }
        return salaryItemRepository.save(item);
    }

    @Override
    public void deleteSalaryItem(UUID id) {
        SalaryItem item = salaryItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Khoản lương không tồn tại"));
        item.setIsDeleted(true);
        salaryItemRepository.save(item);
    }

    @Override
    public List<StaffSalaryItem> getStaffSalaryItems(UUID staffId) {
        return staffSalaryItemRepository.findByStaffId(staffId).stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()) && item.getSalaryItem() != null
                        && !Boolean.TRUE.equals(item.getSalaryItem().getIsDeleted()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<StaffSalaryItem> saveStaffSalaryItems(UUID staffId, List<StaffSalaryItem> items) {
        Staff staff = staffRepository.findById(staffId)
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại"));

        if (items == null) throw new IllegalArgumentException("Danh sách cấu hình khoản lương là bắt buộc");
        List<StaffSalaryItem> toSave = new ArrayList<>();
        Set<UUID> selected = new HashSet<>();
        for (StaffSalaryItem item : items) {
            if (item == null) throw new IllegalArgumentException("Cấu hình khoản lương không hợp lệ");
            if (item.getSalaryItem() == null || item.getSalaryItem().getId() == null) {
                throw new IllegalArgumentException("Hãy chọn khoản lương cho nhân viên");
            }
            if (!selected.add(item.getSalaryItem().getId()))
                throw new IllegalArgumentException("Khoản lương của nhân viên bị trùng");
            if (item.getAmount() == null || !Double.isFinite(item.getAmount()) || item.getAmount() < 0)
                throw new IllegalArgumentException("Số tiền khoản lương phải là số không âm hợp lệ");
            StaffSalaryItem ssi = new StaffSalaryItem();
            ssi.setStaff(staff);

            SalaryItem salaryItem = salaryItemRepository.findById(item.getSalaryItem().getId())
                    .filter(definition -> !Boolean.TRUE.equals(definition.getIsDeleted()))
                    .orElseThrow(() -> new IllegalArgumentException("Khoản lương không hợp lệ"));
            requireCurrentFormula(salaryItem.getCalculationType());
            if (salaryItem.getType() == null)
                throw new IllegalArgumentException("Loại khoản lương là bắt buộc");
            ssi.setSalaryItem(salaryItem);
            ssi.setAmount(item.getAmount());
            toSave.add(ssi);
        }
        // Validate every replacement before retiring the current configuration.
        List<StaffSalaryItem> oldItems = staffSalaryItemRepository.findByStaffId(staffId).stream()
                .filter(item -> item.getIsDeleted() == null || !item.getIsDeleted())
                .collect(Collectors.toList());
        for (StaffSalaryItem oldItem : oldItems) oldItem.setIsDeleted(true);
        staffSalaryItemRepository.saveAll(oldItems);
        return staffSalaryItemRepository.saveAll(toSave);
    }

    private void requireCurrentFormula(SalaryCalculationType formula) {
        if (formula != SalaryCalculationType.FIXED && formula != SalaryCalculationType.BY_STANDARD_DAYS)
            throw new IllegalArgumentException("Chỉ hỗ trợ Cố định hoặc Theo công thực tế / công chuẩn. Hãy cập nhật cách tính khoản lương cũ trước khi lưu cấu hình");
    }
}
