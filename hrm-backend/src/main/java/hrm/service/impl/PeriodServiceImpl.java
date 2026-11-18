package hrm.service.impl;

import hrm.model.Period;
import hrm.model.Payroll;
import hrm.model.Payslip;
import hrm.enums.PayrollStatus;
import hrm.enums.PaidStatus;
import jakarta.persistence.EntityManager;
import hrm.repository.PeriodRepository;
import hrm.repository.PayrollRepository;
import hrm.repository.PayslipRepository;
import hrm.service.PeriodService;
import hrm.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hrm.dto.request.PeriodDto;
import hrm.dto.search.PeriodSearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PeriodServiceImpl implements PeriodService {

    @Autowired
    private PeriodRepository periodRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private EntityManager entityManager;

    @Override
    public Period createPeriod(PeriodDto dto) {
        Period period = new Period();
        period.setName(dto.getName());
        String code = dto.getCode();
        String finalCode;
        if (code == null || code.trim().isEmpty()) {
            finalCode = "KY_LUONG_T" + dto.getMonth() + "_" + dto.getYear();
        } else {
            finalCode = code.trim().toUpperCase();
        }

        Optional<Period> existing = periodRepository.findActiveByCode(finalCode);
        if (existing.isPresent()) {
            throw new CustomException("Mã kỳ lương '" + finalCode + "' đã tồn tại", HttpStatus.BAD_REQUEST);
        }

        period.setCode(finalCode);
        period.setDescription(dto.getDescription());
        period.setMonth(dto.getMonth());
        period.setYear(dto.getYear());
        period.setFromDate(dto.getFromDate());
        period.setToDate(dto.getToDate());
        period.setStandardWorkDays(dto.getStandardWorkDays() != null ? dto.getStandardWorkDays() : 26.0);
        return periodRepository.save(period);
    }

    @Override
    public Period updatePeriod(UUID id, PeriodDto dto) {
        Period period = lockEditablePeriod(id);
        period.setName(dto.getName());
        String code = dto.getCode();
        String finalCode;
        if (code == null || code.trim().isEmpty()) {
            finalCode = "KY_LUONG_T" + dto.getMonth() + "_" + dto.getYear();
        } else {
            finalCode = code.trim().toUpperCase();
        }

        Optional<Period> existing = periodRepository.findActiveByCode(finalCode);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new CustomException("Mã kỳ lương '" + finalCode + "' đã tồn tại ở một kỳ lương khác", HttpStatus.BAD_REQUEST);
        }

        period.setCode(finalCode);
        period.setDescription(dto.getDescription());
        period.setMonth(dto.getMonth());
        period.setYear(dto.getYear());
        period.setFromDate(dto.getFromDate());
        period.setToDate(dto.getToDate());
        period.setStandardWorkDays(dto.getStandardWorkDays() != null ? dto.getStandardWorkDays() : 26.0);
        return periodRepository.save(period);
    }

    @Override
    public List<Period> getAllPeriods() {
        return periodRepository.findAll().stream()
                .filter(period -> period.getIsDeleted() == null || !period.getIsDeleted())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deletePeriod(UUID periodId) {
        Period period = lockEditablePeriod(periodId);
        period.setIsDeleted(true);
        periodRepository.save(period);

        List<Payroll> payrolls = payrollRepository.findByPeriodId(periodId);
        for (Payroll payroll : payrolls) {
            payroll.setIsDeleted(true);
            payrollRepository.save(payroll);

            List<Payslip> payslips = payslipRepository.findByPayrollId(payroll.getId());
            for (Payslip payslip : payslips) {
                payslip.setIsDeleted(true);
            }
            payslipRepository.saveAll(payslips);
        }
    }

    @Override
    public Page<Period> getPeriods(PeriodSearchRequest request) {
        Specification<Period> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + request.getCode().trim().toLowerCase() + "%"));
            }

            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + request.getName().trim().toLowerCase() + "%"));
            }

            if (request.getMonth() != null) {
                predicates.add(cb.equal(root.get("month"), request.getMonth()));
            }

            if (request.getYear() != null) {
                predicates.add(cb.equal(root.get("year"), request.getYear()));
            }

            if (request.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fromDate"), request.getFromDate()));
            }

            if (request.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("toDate"), request.getToDate()));
            }

            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                String kw = "%" + request.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("code")), kw),
                    cb.like(cb.lower(root.get("name")), kw)
                ));
            }

            predicates.add(cb.or(cb.isNull(root.get("isDeleted")), cb.equal(root.get("isDeleted"), false)));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        int pageNum = request.getPageIndex() >= 1 ? request.getPageIndex() - 1 : 0;
        int size = request.getPageSize() > 0 ? request.getPageSize() : 10;

        return periodRepository.findAll(spec, PageRequest.of(pageNum, size));
    }

    private Period lockEditablePeriod(UUID id) {
        Period period = periodRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Kỳ lương không tồn tại hoặc đã xóa"));
        entityManager.refresh(period);
        for (Payroll payroll : payrollRepository.findByPeriodId(id)) {
            if (Boolean.TRUE.equals(payroll.getIsDeleted())) continue;
            if (payroll.getStatus() == PayrollStatus.CONFIRMED || payroll.getStatus() == PayrollStatus.PAID
                    || payslipRepository.findByPayrollId(payroll.getId()).stream()
                    .anyMatch(slip -> !Boolean.TRUE.equals(slip.getIsDeleted()) && slip.getPaidStatus() == PaidStatus.PAID)) {
                throw new IllegalStateException("Không thể sửa hoặc xóa kỳ lương đã có bảng lương xác nhận hoặc đã thanh toán");
            }
        }
        return period;
    }
}
