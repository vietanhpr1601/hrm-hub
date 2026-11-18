package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.dto.request.PeriodDto;
import hrm.dto.request.PayslipDraftRequest;
import hrm.dto.request.PayslipPaymentRequest;
import hrm.enums.PaidStatus;
import hrm.enums.PaymentMethod;
import hrm.enums.PayrollStatus;
import hrm.enums.SalaryCalculationType;
import hrm.enums.SalaryItemType;
import hrm.enums.TimesheetStatus;
import hrm.model.Payroll;
import hrm.model.Payslip;
import hrm.model.PayslipItem;
import hrm.model.Period;
import hrm.model.SalaryItem;
import hrm.model.Staff;
import hrm.model.StaffSalaryItem;
import hrm.model.Timesheet;
import hrm.model.User;
import hrm.repository.PayrollRepository;
import hrm.repository.PayslipRepository;
import hrm.repository.PeriodRepository;
import hrm.repository.SalaryItemRepository;
import hrm.repository.StaffRepository;
import hrm.repository.StaffSalaryItemRepository;
import hrm.repository.TimesheetRepository;
import hrm.service.PayrollService;
import hrm.service.PeriodService;
import hrm.service.SalaryItemService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
@WithMockUser(username = "payroll-accountant", authorities = "ROLE_ADMIN")
class ManualPayrollIntegrationTests {
    @Autowired private PayrollService payrollService;
    @Autowired private PeriodService periodService;
    @Autowired private SalaryItemService salaryItemService;
    @Autowired private PayrollRepository payrollRepository;
    @Autowired private PayslipRepository payslipRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private SalaryItemRepository salaryItemRepository;
    @Autowired private StaffSalaryItemRepository staffSalaryItemRepository;
    @Autowired private TimesheetRepository timesheetRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mvc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private hrm.repository.PayrollEmailRepository payrollEmails;
    @Autowired private hrm.service.PayrollDraftService automaticDrafts;
    @Autowired private hrm.service.PeriodCreationService periodCreation;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void creatingPeriodAutomaticallyCreatesCalculatedDraftAndReportsSkippedStaff() {
        Staff employee = configuredStaff(1000); Staff missing = staff();
        PeriodDto request = new PeriodDto(period()); request.setCode(UUID.randomUUID().toString());
        var result = periodCreation.create(request);
        Payroll payroll = payrollRepository.findById(result.getPayrollId()).orElseThrow();
        assertEquals(result.getId(), payroll.getPeriod().getId());
        assertEquals(PayrollStatus.DRAFT, payroll.getStatus()); assertEquals(1, result.getEmployeeCount());
        Payslip slip = onlyPayslip(payroll);
        assertEquals(employee.getId(), slip.getStaff().getId()); assertEquals(1000.0, slip.getNetSalary());
        assertEquals(PaidStatus.UNPAID, slip.getPaidStatus());
        assertTrue(result.getSkippedEmployees().stream().anyMatch(row -> missing.getId().equals(row.id())));
        assertTrue(payrollEmails.findByPayslipIdAndEventType(slip.getId(), hrm.model.PayrollEmail.EventType.CONFIRMATION).isEmpty());
    }

    @Test
    void creatingPeriodWithNoEligibleStaffStillCreatesEmptyDraftWithExplanation() {
        Staff missing = staff();
        PeriodDto request = new PeriodDto(period()); request.setCode(UUID.randomUUID().toString());
        var result = periodCreation.create(request);
        assertEquals(0, result.getEmployeeCount());
        assertTrue(payrollService.getPayrollDetails(result.getPayrollId()).isEmpty());
        assertTrue(result.getSkippedEmployees().stream().anyMatch(row -> missing.getId().equals(row.id())));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedAutoCalculationRollsBackBothPeriodAndPayroll() {
        String code = "AUTO_ROLLBACK_" + UUID.randomUUID();
        var transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status -> {
            Staff employee = configuredStaff(Double.MAX_VALUE);
            configure(employee, salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME), Double.MAX_VALUE);
            PeriodDto request = new PeriodDto(period()); request.setCode(code);
            periodCreation.create(request);
        }));
        assertTrue(periodRepository.findActiveByCode(code).isEmpty());
    }

    @Test
    @WithMockUser(authorities = {"PERMISSIONS_RESOLVED", "PERIOD_MANAGE"})
    void periodOnlyManagerCannotBypassPayrollPermissionThroughPeriodCreation() throws Exception {
        mvc.perform(post("/api/periods").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No payroll permission\",\"month\":9,\"year\":2026}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void automaticDraftPreviewsMissingConfigurationAndOnlyUsesApprovedAttendanceWithoutApprovingOrEmailing() {
        Period period = period();
        Staff employee = staff();
        configure(employee, salaryItem(SalaryCalculationType.BY_STANDARD_DAYS, SalaryItemType.INCOME), 13000000);
        Staff missing = staff();
        Staff future = configuredStaff(500);
        future.setStartDate(period.getToDate().plusDays(1)); staffRepository.saveAndFlush(future);
        timesheet(employee, period.getFromDate(), 1, 0, TimesheetStatus.APPROVED, false);
        timesheet(employee, period.getFromDate().plusDays(1), 1, 0, TimesheetStatus.SUBMITTED, false);
        var preview = automaticDrafts.preview(period.getId());
        assertTrue(preview.employees().stream().filter(row -> row.id().equals(employee.getId())).findFirst().orElseThrow().eligible());
        assertFalse(preview.employees().stream().filter(row -> row.id().equals(missing.getId())).findFirst().orElseThrow().eligible());
        assertFalse(preview.employees().stream().filter(row -> row.id().equals(future.getId())).findFirst().orElseThrow().eligible());
        Payroll draft = automaticDrafts.create(period.getId(), new hrm.service.PayrollDraftService.Selection(List.of(employee.getId())));
        assertEquals(PayrollStatus.DRAFT, draft.getStatus());
        Payslip slip = onlyPayslip(draft);
        assertEquals(PaidStatus.UNPAID, slip.getPaidStatus());
        assertEquals(1.0, slip.getTotalWorkDays()); assertEquals(500000.0, slip.getNetSalary());
        assertTrue(payrollEmails.findByPayslipId(slip.getId()).isEmpty());
        assertTrue(payrollEmails.findByPayslipIdAndEventType(slip.getId(), hrm.model.PayrollEmail.EventType.CONFIRMATION).isEmpty());
        assertThrows(IllegalStateException.class, () -> automaticDrafts.create(period.getId(), new hrm.service.PayrollDraftService.Selection(List.of(employee.getId()))));
        assertEquals(1, payrollRepository.findByPeriodId(period.getId()).size());
    }

    @Test
    void automaticDraftRejectsStaleOrUnconfiguredSelectionWithoutLeavingAnEmptyPayroll() {
        Period period = period(); Staff missing = staff();
        assertThrows(IllegalArgumentException.class, () -> automaticDrafts.create(period.getId(), new hrm.service.PayrollDraftService.Selection(List.of(missing.getId()))));
        assertTrue(payrollRepository.findByPeriodId(period.getId()).isEmpty());
    }

    @Test
    void draftDetailsCanBeEditedWithoutChangingMoneyButConfirmedPayrollCannotBeEdited() {
        Payslip slip = enrolledPayslip(1000);
        var request = new hrm.dto.request.PayrollDetailsRequest("Tên cập nhật", " UPDATED_CODE ", "Mô tả mới");
        Payroll updated = payrollService.updateDetails(slip.getPayroll().getId(), request);
        assertEquals("Tên cập nhật", updated.getName()); assertEquals("UPDATED_CODE", updated.getCode());
        assertEquals(1000, slip.getNetSalary());
        payrollService.confirmPayroll(updated.getId());
        assertThrows(IllegalStateException.class, () -> payrollService.updateDetails(updated.getId(), request));
    }

    @Test
    void approvalAndPaymentQueueSeparateEmailsOnce() {
        Payslip slip = enrolledPayslip(13000000);
        slip.getStaff().setCompanyEmail("payroll@example.test");
        staffRepository.saveAndFlush(slip.getStaff());
        assertTrue(payrollEmails.findByPayslipId(slip.getId()).isEmpty());
        payrollService.confirmPayroll(slip.getPayroll().getId());
        assertTrue(payrollEmails.findByPayslipId(slip.getId()).isEmpty());
        var approvalEmail = payrollEmails.findByPayslipIdAndEventType(slip.getId(), hrm.model.PayrollEmail.EventType.CONFIRMATION).orElseThrow();
        assertTrue(approvalEmail.getContent().contains("chưa xác nhận đã trả tiền"));
        payrollService.confirmPayroll(slip.getPayroll().getId());
        assertEquals(approvalEmail.getId(), payrollEmails.findByPayslipIdAndEventType(slip.getId(), hrm.model.PayrollEmail.EventType.CONFIRMATION).orElseThrow().getId());
        payrollService.recordPayment(slip.getId(), validPayment());
        entityManager.flush(); entityManager.clear();
        var job = payrollEmails.findByPayslipId(slip.getId()).orElseThrow();
        assertEquals(hrm.model.PayrollEmail.Status.PENDING, job.getStatus());
        assertEquals("payroll@example.test", job.getRecipient());
        assertNotEquals(approvalEmail.getId(), job.getId());
        assertTrue(job.getContent().contains("13.000.000"));
        assertThrows(IllegalStateException.class, () -> payrollService.recordPayment(slip.getId(), validPayment()));
        assertEquals(job.getId(), payrollEmails.findByPayslipId(slip.getId()).orElseThrow().getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rolledBackPaymentCannotLeaveAnEmailToSend() {
        var payslipId = new java.util.concurrent.atomic.AtomicReference<UUID>();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Payslip slip = enrolledPayslip(1000);
            slip.getStaff().setEmail("rollback@example.test");
            staffRepository.saveAndFlush(slip.getStaff());
            payrollService.confirmPayroll(slip.getPayroll().getId());
            payrollService.recordPayment(slip.getId(), validPayment());
            entityManager.flush();
            payslipId.set(slip.getId());
            assertTrue(payrollEmails.findByPayslipId(slip.getId()).isPresent());
            status.setRollbackOnly();
        });
        assertTrue(payrollEmails.findByPayslipId(payslipId.get()).isEmpty());
        assertTrue(payrollEmails.findByPayslipIdAndEventType(payslipId.get(), hrm.model.PayrollEmail.EventType.CONFIRMATION).isEmpty());
        assertTrue(payslipRepository.findById(payslipId.get()).isEmpty());
    }

    @Test
    void apiUsesEmployeeProfileItemsForCreationConfirmationAndPaymentWithPersistedMetadata() throws Exception {
        Period period = period();
        Staff staff = staff();
        SalaryItem income = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        SalaryItem deduction = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.DEDUCTION);
        configure(staff, income, 1550.0);
        configure(staff, deduction, 100.0);
        JsonNode payroll = api(post("/api/payrolls").param("periodId", period.getId().toString())
                .param("name", "Manual payroll HTTP flow"));
        String payrollId = payroll.path("id").asText();
        assertEquals("DRAFT", payroll.path("status").asText());
        assertTrue(api(get("/api/payrolls/" + payrollId)).isEmpty());

        JsonNode enrolled = api(post("/api/payrolls/" + payrollId + "/staffs"),
                Map.of("staffIds", List.of(staff.getId().toString())));
        String payslipId = enrolled.get(0).path("id").asText();
        assertEquals(2, enrolled.get(0).path("items").size());
        assertEquals(1450.0, enrolled.get(0).path("netSalary").asDouble());
        mvc.perform(put("/api/payrolls/payslip/" + payslipId + "/draft").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("note", "Retired manual editing",
                "items", List.of(Map.of("salaryItemId", income.getId().toString(), "amount", 1550,
                                "calculationType", "FIXED", "type", "INCOME"),
                        Map.of("salaryItemId", deduction.getId().toString(), "amount", 100,
                                "calculationType", "FIXED", "type", "DEDUCTION"))))))
                .andExpect(status().isConflict());
        assertEquals("CONFIRMED", api(put("/api/payrolls/" + payrollId + "/confirm")).path("status").asText());
        String date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        JsonNode paid = api(post("/api/payrolls/payslip/" + payslipId + "/payment"),
                Map.of("paidDate", date, "paymentMethod", "BANK_TRANSFER", "paymentReference", "HTTP-TRANSFER"));
        assertEquals("PAID", paid.path("paidStatus").asText());
        assertEquals(1450.0, paid.path("paidAmount").asDouble());
        assertEquals(date, paid.path("paidDate").asText());
        assertEquals("BANK_TRANSFER", paid.path("paymentMethod").asText());
        assertEquals("HTTP-TRANSFER", paid.path("paymentReference").asText());
        assertEquals("payroll-accountant", paid.path("paidBy").asText());
        assertEquals("PAID", api(get("/api/payrolls/" + payrollId)).get(0).path("paidStatus").asText());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_MANAGE")
    void apiCreatesPayrollForOnlySelectedConfiguredEmployees() throws Exception {
        Period period = period();
        Staff configured = configuredStaff(1000.0);
        Staff second = configuredStaff(2000.0);
        configuredStaff(9000.0);

        JsonNode created = api(post("/api/payrolls").param("periodId", period.getId().toString())
                .param("name", "Selected employees payroll"),
                Map.of("staffIds", List.of(configured.getId().toString(), second.getId().toString())));

        assertEquals("DRAFT", created.path("status").asText());
        List<Payslip> payslips = payrollService.getPayrollDetails(UUID.fromString(created.path("id").asText()));
        assertEquals(2, payslips.size());
        Payslip configuredSlip = payslips.stream().filter(p -> p.getStaff().getId().equals(configured.getId()))
                .findFirst().orElseThrow();
        Payslip secondSlip = payslips.stream().filter(p -> p.getStaff().getId().equals(second.getId()))
                .findFirst().orElseThrow();
        assertEquals(1000.0, configuredSlip.getNetSalary());
        assertEquals(1, configuredSlip.getItems().size());
        assertEquals(1, secondSlip.getItems().size());
        assertEquals(2000.0, secondSlip.getNetSalary());
    }

    @Test
    void apiRejectsEmptyOrDuplicateEmployeeSelectionBeforeCreatingPayroll() throws Exception {
        Period period = period();
        Staff staff = configuredStaff(1000.0);

        for (List<String> selection : List.of(List.<String>of(), List.of(staff.getId().toString(), staff.getId().toString()))) {
            mvc.perform(post("/api/payrolls").param("periodId", period.getId().toString()).param("name", "Invalid selection")
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("staffIds", selection))))
                    .andExpect(status().isBadRequest());
        }
        assertTrue(payrollRepository.findByPeriodId(period.getId()).isEmpty());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PAY")
    void paymentOnlyPermissionCannotCreatePayrollWithEmployees() throws Exception {
        Period period = period();
        Staff staff = configuredStaff(1000.0);

        mvc.perform(post("/api/payrolls").param("periodId", period.getId().toString()).param("name", "Denied creation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("staffIds", List.of(staff.getId().toString())))))
                .andExpect(status().isForbidden());
        assertTrue(payrollRepository.findByPeriodId(period.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedSelectedEmployeeCreationRollsBackBothPayrollAndPayslips() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CreationFixture fixture = tx.execute(status -> {
            Period period = period();
            Staff first = configuredStaff(1000.0);
            Staff invalid = configuredStaff(-1.0);
            Staff unconfigured = staff();
            List<StaffSalaryItem> configurations = List.of(
                    staffSalaryItemRepository.findByStaffId(first.getId()).get(0),
                    staffSalaryItemRepository.findByStaffId(invalid.getId()).get(0));
            return new CreationFixture(period.getId(), List.of(first.getId(), invalid.getId(), unconfigured.getId()),
                    configurations.stream().map(item -> item.getSalaryItem().getId()).toList(),
                    configurations.stream().map(StaffSalaryItem::getId).toList());
        });
        long payrollCount = payrollRepository.count();
        long payslipCount = payslipRepository.count();
        try {
            UUID validStaffId = fixture.staffIds().get(0);
            for (List<UUID> selection : List.of(List.of(validStaffId, UUID.randomUUID()),
                    List.of(validStaffId, fixture.staffIds().get(1)),
                    List.of(validStaffId, fixture.staffIds().get(2)), List.of(validStaffId, validStaffId))) {
                String code = "ATOMIC_" + UUID.randomUUID().toString().toUpperCase();
                assertThrows(IllegalArgumentException.class, () -> payrollService.createPayrollWithStaffs(
                        fixture.periodId(), "Atomic selected payroll", code, null, selection));
                tx.executeWithoutResult(status -> {
                    assertTrue(payrollRepository.findActiveByCode(code).isEmpty());
                    assertTrue(payrollRepository.findByPeriodId(fixture.periodId()).isEmpty());
                    assertEquals(payrollCount, payrollRepository.count());
                    assertEquals(payslipCount, payslipRepository.count());
                });
            }
        } finally {
            tx.executeWithoutResult(status -> {
                List<Payroll> payrolls = payrollRepository.findByPeriodId(fixture.periodId());
                for (Payroll payroll : payrolls) payslipRepository.deleteAll(payslipRepository.findByPayrollId(payroll.getId()));
                payrollRepository.deleteAll(payrolls);
                staffSalaryItemRepository.deleteAllById(fixture.configurationIds());
                salaryItemRepository.deleteAllById(fixture.salaryIds());
                staffRepository.deleteAllById(fixture.staffIds());
                periodRepository.deleteById(fixture.periodId());
            });
        }
    }

    private record CreationFixture(UUID periodId, List<UUID> staffIds, List<UUID> salaryIds, List<UUID> configurationIds) {}

    @Test
    void creationLeavesAnEmptyDraftEvenWhenEmployeesHaveSalaryConfigurations() {
        Staff staff = staff();
        configure(staff, salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME), 1000.0);

        Payroll payroll = createPayroll(period());

        assertEquals(PayrollStatus.DRAFT, payroll.getStatus());
        assertTrue(payrollService.getPayrollDetails(payroll.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> payrollService.calculatePayroll(payroll.getId()));
    }

    @Test
    void enrollmentAppendsOnlySelectedEmployeesAndDoesNotDuplicateExistingPayslips() {
        Payroll payroll = createPayroll(period());
        Staff first = configuredStaff(1000.0);
        Staff second = configuredStaff(2000.0);
        configuredStaff(3000.0);

        payrollService.enrollStaffs(payroll.getId(), List.of(first.getId()));
        UUID firstPayslipId = onlyPayslip(payroll).getId();
        assertThrows(IllegalArgumentException.class,
                () -> payrollService.enrollStaffs(payroll.getId(), List.of(first.getId())));
        assertThrows(IllegalArgumentException.class,
                () -> payrollService.enrollStaffs(payroll.getId(), List.of(second.getId(), second.getId())));
        payrollService.enrollStaffs(payroll.getId(), List.of(second.getId()));

        List<Payslip> payslips = payrollService.getPayrollDetails(payroll.getId());
        assertEquals(2, payslips.size());
        assertEquals(firstPayslipId, payslips.stream().filter(p -> p.getStaff().getId().equals(first.getId()))
                .findFirst().orElseThrow().getId());
        assertTrue(payslips.stream().anyMatch(p -> p.getStaff().getId().equals(second.getId())));
    }

    @Test
    void employeesWithoutAnActiveSalaryConfigurationCannotBeEnrolledAndErrorNamesEmployee() {
        Payroll payroll = createPayroll(period());
        Staff staff = staff();
        StaffSalaryItem configuration = configure(staff,
                salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME), 1000.0);
        configuration.setIsDeleted(true);
        staffSalaryItemRepository.saveAndFlush(configuration);
        timesheet(staff, LocalDate.of(2026, 9, 1), 1.0, 0.0, TimesheetStatus.APPROVED, false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId())));
        assertTrue(error.getMessage().contains(staff.getDisplayName()));
        assertTrue(payrollService.getPayrollDetails(payroll.getId()).isEmpty());
    }

    @Test
    void deletedEmployeesCannotBeEnrolled() {
        Payroll payroll = createPayroll(period());
        Staff staff = configuredStaff(1000.0);
        staff.setIsDeleted(true);
        staffRepository.saveAndFlush(staff);

        assertThrows(IllegalArgumentException.class,
                () -> payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId())));
        assertTrue(payrollService.getPayrollDetails(payroll.getId()).isEmpty());
    }

    @Test
    void recalculationPreservesPayslipIdentityAndMetadataWhileRefreshingCurrentProfileAndApprovedWork() {
        Payroll payroll = createPayroll(period());
        Staff staff = staff();
        SalaryItem salary = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        StaffSalaryItem configuration = configure(staff, salary, 1000.0);
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        Payslip original = onlyPayslip(payroll);
        UUID payslipId = original.getId();
        original.setNote("Review note");
        original.setPaidDate(LocalDate.of(2026, 9, 30));
        original.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        original.setPaymentReference("Preserved legacy reference");
        original.setPaidBy("Legacy accountant");
        payslipRepository.saveAndFlush(original);
        configuration.setAmount(2200.0);
        staffSalaryItemRepository.saveAndFlush(configuration);
        configuredStaff(9000.0);
        timesheet(staff, LocalDate.of(2026, 9, 1), 1.0, 0.0, TimesheetStatus.APPROVED, false);

        List<Payslip> recalculated = payrollService.calculatePayroll(payroll.getId());
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, recalculated.size());
        Payslip saved = onlyPayslip(payroll);
        assertEquals(payslipId, saved.getId());
        assertEquals(2200.0, saved.getNetSalary());
        assertEquals(1.0, saved.getTotalWorkDays());
        assertEquals("Review note", saved.getNote());
        assertEquals(PaidStatus.UNPAID, saved.getPaidStatus());
        assertEquals(LocalDate.of(2026, 9, 30), saved.getPaidDate());
        assertEquals(PaymentMethod.BANK_TRANSFER, saved.getPaymentMethod());
        assertEquals("Preserved legacy reference", saved.getPaymentReference());
        assertEquals("Legacy accountant", saved.getPaidBy());
        assertEquals(2200.0, saved.getItems().get(0).getAmount());
    }

    @Test
    void twoSimpleFormulasUseOnlyApprovedActiveAttendanceAndIgnoreOtCodeSpecialCases() {
        Period period = period();
        Payroll payroll = createPayroll(period);
        Staff staff = staff();
        SalaryItem fixed = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        SalaryItem standard = salaryItem(SalaryCalculationType.BY_STANDARD_DAYS, SalaryItemType.INCOME);
        SalaryItem deduction = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.DEDUCTION);
        SalaryItem overtimeCode = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        overtimeCode.setCode("OT");
        salaryItemRepository.saveAndFlush(overtimeCode);
        configure(staff, fixed, 1000.0);
        configure(staff, standard, 2600.0);
        configure(staff, deduction, 40.0);
        configure(staff, overtimeCode, 800.0);
        timesheet(staff, LocalDate.of(2026, 9, 1), 1.0, 2.0, TimesheetStatus.APPROVED, false);
        timesheet(staff, LocalDate.of(2026, 9, 2), 0.5, 2.0, TimesheetStatus.APPROVED, false);
        timesheet(staff, LocalDate.of(2026, 9, 3), 1.0, 0.0, TimesheetStatus.SUBMITTED, false);
        timesheet(staff, LocalDate.of(2026, 9, 4), 1.0, 0.0, TimesheetStatus.REJECTED, false);
        timesheet(staff, LocalDate.of(2026, 9, 5), 1.0, 0.0, TimesheetStatus.APPROVED, true);
        timesheet(staff, LocalDate.of(2026, 8, 31), 1.0, 0.0, TimesheetStatus.APPROVED, false);
        timesheet(staff(), LocalDate.of(2026, 9, 1), 1.0, 0.0, TimesheetStatus.APPROVED, false);

        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        Payslip payslip = onlyPayslip(payroll);

        assertEquals(1.5, payslip.getTotalWorkDays());
        assertEquals(4.0, payslip.getTotalOtHours());
        assertEquals(1000.0, line(payslip, fixed).getCalculatedValue());
        assertEquals(150.0, line(payslip, standard).getCalculatedValue());
        assertEquals(800.0, line(payslip, overtimeCode).getCalculatedValue());
        assertEquals(1950.0, payslip.getTotalIncome());
        assertEquals(40.0, payslip.getTotalDeduction());
        assertEquals(1910.0, payslip.getNetSalary());
    }

    @Test
    void confirmationRefreshesCurrentProfileAmountsFormulasClassificationAndApprovedAttendance() {
        Payroll payroll = createPayroll(period());
        Staff staff = configuredStaff(1000.0);
        SalaryItem salary = staffSalaryItemRepository.findByStaffId(staff.getId()).get(0).getSalaryItem();
        StaffSalaryItem configuration = staffSalaryItemRepository.findByStaffId(staff.getId()).get(0);
        SalaryItem allowance = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        configure(staff, allowance, 200.0);
        timesheet(staff, LocalDate.of(2026, 9, 1), 1.0, 0.0, TimesheetStatus.APPROVED, false);
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        UUID payslipId = onlyPayslip(payroll).getId();
        configuration.setAmount(2600.0);
        staffSalaryItemRepository.saveAndFlush(configuration);
        salary.setName("Current profile salary");
        salary.setCalculationType(SalaryCalculationType.BY_STANDARD_DAYS);
        salary.setType(SalaryItemType.DEDUCTION);
        salaryItemRepository.saveAndFlush(salary);
        allowance.setName("Current allowance");
        salaryItemRepository.saveAndFlush(allowance);
        timesheet(staff, LocalDate.of(2026, 9, 2), 0.5, 1.0, TimesheetStatus.APPROVED, false);
        timesheet(staff, LocalDate.of(2026, 9, 3), 1.0, 0.0, TimesheetStatus.SUBMITTED, false);
        payroll.getPeriod().setStandardWorkDays(30.0);
        periodRepository.saveAndFlush(payroll.getPeriod());

        payrollService.confirmPayroll(payroll.getId());
        entityManager.clear();

        Payslip saved = payslipRepository.findById(payslipId).orElseThrow();
        assertEquals(PayrollStatus.CONFIRMED, saved.getPayroll().getStatus());
        assertEquals(2, saved.getItems().size());
        assertEquals(SalaryCalculationType.BY_STANDARD_DAYS, line(saved, salary).getCalculationType());
        assertEquals(SalaryItemType.DEDUCTION, line(saved, salary).getType());
        assertEquals("Current profile salary", line(saved, salary).getName());
        assertEquals("Current allowance", line(saved, allowance).getName());
        assertEquals(2600.0, line(saved, salary).getAmount());
        assertEquals(1.5, saved.getTotalWorkDays());
        assertEquals(1.0, saved.getTotalOtHours());
        assertEquals(30.0, saved.getStandardWorkDays());
        assertEquals(200.0, saved.getTotalIncome());
        assertEquals(130.0, saved.getTotalDeduction());
        assertEquals(70.0, saved.getNetSalary());
    }

    @Test
    void manualDraftOverridesAreDisabledAndDirectEmployeesToSavedProfileConfiguration() {
        Payroll payroll = createPayroll(period());
        Staff staff = configuredStaff(1000.0);
        SalaryItem salary = staffSalaryItemRepository.findByStaffId(staff.getId()).get(0).getSalaryItem();
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        Payslip payslip = onlyPayslip(payroll);

        for (double amount : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> payrollService.updateDraftPayslip(payslip.getId(),
                    draft(null, item(salary, amount, SalaryCalculationType.FIXED, SalaryItemType.INCOME))));
            assertTrue(error.getMessage().contains("hồ sơ nhân viên"));
        }
        assertThrows(IllegalStateException.class, () -> payrollService.updateDraftPayslip(payslip.getId(),
                draft(null, item(salary, 1.0, SalaryCalculationType.FIXED, SalaryItemType.INCOME),
                        item(salary, 2.0, SalaryCalculationType.FIXED, SalaryItemType.INCOME))));
        assertEquals(1000.0, onlyPayslip(payroll).getNetSalary());
        assertEquals(1, onlyPayslip(payroll).getItems().size());
    }

    @Test
    void snapshotFieldsRemainStableWhenSalaryDefinitionsOrPeriodConfigurationChange() {
        Period period = period();
        Payroll payroll = createPayroll(period);
        Staff staff = staff();
        SalaryItem salary = salaryItem(SalaryCalculationType.BY_STANDARD_DAYS, SalaryItemType.INCOME);
        String originalName = salary.getName();
        StaffSalaryItem configuration = configure(staff, salary, 2600.0);
        timesheet(staff, LocalDate.of(2026, 9, 1), 1.0, 0.0, TimesheetStatus.APPROVED, false);
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        UUID payslipId = onlyPayslip(payroll).getId();
        payrollService.confirmPayroll(payroll.getId());
        salary.setName("Changed definition");
        salary.setCalculationType(SalaryCalculationType.FIXED);
        salary.setType(SalaryItemType.DEDUCTION);
        salaryItemRepository.saveAndFlush(salary);
        configuration.setAmount(9999.0);
        staffSalaryItemRepository.saveAndFlush(configuration);
        // Directly changing the entity simulates a historical definition change outside the period API.
        period.setStandardWorkDays(22.0);
        periodRepository.saveAndFlush(period);
        timesheet(staff, LocalDate.of(2026, 9, 2), 1.0, 0.0, TimesheetStatus.APPROVED, false);
        assertEquals(PayrollStatus.CONFIRMED, payrollService.confirmPayroll(payroll.getId()).getStatus());
        assertThrows(IllegalStateException.class, () -> payrollService.calculatePayroll(payroll.getId()));
        entityManager.clear();

        Payslip saved = payslipRepository.findById(payslipId).orElseThrow();
        assertEquals(26.0, saved.getStandardWorkDays());
        PayslipItem item = saved.getItems().get(0);
        assertEquals(originalName, item.getName());
        assertEquals(SalaryCalculationType.BY_STANDARD_DAYS, item.getCalculationType());
        assertEquals(SalaryItemType.INCOME, item.getType());
        assertEquals(2600.0, item.getAmount());
        assertEquals(1.0, saved.getTotalWorkDays());
        assertEquals(100.0, saved.getNetSalary());
    }

    @Test
    void removingDraftPayslipDoesNotBringEmployeeBackDuringRecalculation() {
        Payroll payroll = createPayroll(period());
        Staff staff = configuredStaff(1000.0);
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        UUID removedId = onlyPayslip(payroll).getId();

        payrollService.removePayslip(removedId);

        assertTrue(payrollService.getPayrollDetails(payroll.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> payrollService.calculatePayroll(payroll.getId()));
        assertTrue(payslipRepository.findById(removedId).isEmpty()
                || Boolean.TRUE.equals(payslipRepository.findById(removedId).orElseThrow().getIsDeleted()));
    }

    @Test
    void emptyAndNegativePayrollsCannotBeConfirmed() {
        Payroll empty = createPayroll(period());
        assertThrows(IllegalArgumentException.class, () -> payrollService.confirmPayroll(empty.getId()));
        assertEquals(PayrollStatus.DRAFT, empty.getStatus());

        Payroll negative = createPayroll(period());
        Staff staff = staff();
        configure(staff, salaryItem(SalaryCalculationType.FIXED, SalaryItemType.DEDUCTION), 100.0);
        payrollService.enrollStaffs(negative.getId(), List.of(staff.getId()));
        assertThrows(IllegalArgumentException.class, () -> payrollService.confirmPayroll(negative.getId()));
        assertEquals(PayrollStatus.DRAFT, negative.getStatus());
    }

    @Test
    void confirmationLocksEnrollmentRecalculationEditingRemovalAndPayrollDeletion() {
        Payslip payslip = enrolledPayslip(1000.0);
        Payroll payroll = payslip.getPayroll();
        SalaryItem salary = payslip.getItems().get(0).getSalaryItem();
        Staff extra = configuredStaff(500.0);
        payrollService.confirmPayroll(payroll.getId());

        assertThrows(IllegalStateException.class,
                () -> payrollService.enrollStaffs(payroll.getId(), List.of(extra.getId())));
        assertThrows(IllegalStateException.class, () -> payrollService.calculatePayroll(payroll.getId()));
        assertThrows(IllegalStateException.class, () -> payrollService.updateDraftPayslip(payslip.getId(),
                draft(null, item(salary, 1.0, SalaryCalculationType.FIXED, SalaryItemType.INCOME))));
        assertThrows(IllegalStateException.class, () -> payrollService.removePayslip(payslip.getId()));
        assertThrows(IllegalStateException.class, () -> payrollService.deletePayroll(payroll.getId()));
        assertEquals(1000.0, onlyPayslip(payroll).getNetSalary());
    }

    @Test
    void confirmedPayrollProtectsItsPeriodFromEditingAndDeletion() {
        Payslip payslip = enrolledPayslip(1000.0);
        Period period = payslip.getPayroll().getPeriod();
        payrollService.confirmPayroll(payslip.getPayroll().getId());
        PeriodDto changed = new PeriodDto(period);
        changed.setStandardWorkDays(22.0);

        assertThrows(IllegalStateException.class, () -> periodService.updatePeriod(period.getId(), changed));
        assertThrows(IllegalStateException.class, () -> periodService.deletePeriod(period.getId()));
        assertEquals(26.0, period.getStandardWorkDays());
        assertFalse(Boolean.TRUE.equals(period.getIsDeleted()));
        assertFalse(Boolean.TRUE.equals(payslip.getPayroll().getIsDeleted()));
        assertFalse(Boolean.TRUE.equals(payslip.getIsDeleted()));
    }

    @Test
    void bankPaymentRecordsFullNetAmountDateMethodReferenceAndAccountant() {
        Payslip payslip = enrolledPayslip(1234.56);
        payrollService.confirmPayroll(payslip.getPayroll().getId());
        LocalDate paidDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        Payslip paid = payrollService.recordPayment(payslip.getId(),
                payment(paidDate, PaymentMethod.BANK_TRANSFER, " BANK-12345 ", "Paid after review"));
        entityManager.flush();
        entityManager.clear();

        Payslip saved = payslipRepository.findById(paid.getId()).orElseThrow();
        assertEquals(PaidStatus.PAID, saved.getPaidStatus());
        assertEquals(1235.0, saved.getPaidAmount());
        assertEquals(paidDate, saved.getPaidDate());
        assertEquals(PaymentMethod.BANK_TRANSFER, saved.getPaymentMethod());
        assertEquals("BANK-12345", saved.getPaymentReference());
        assertEquals("payroll-accountant", saved.getPaidBy());
        assertEquals("Paid after review", saved.getNote());
    }

    @Test
    void cashPaymentDoesNotRequireBankReference() {
        Payslip payslip = enrolledPayslip(1000.0);
        payrollService.confirmPayroll(payslip.getPayroll().getId());

        Payslip paid = payrollService.recordPayment(payslip.getId(),
                payment(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), PaymentMethod.CASH, null, null));

        assertEquals(PaidStatus.PAID, paid.getPaidStatus());
        assertEquals(1000.0, paid.getPaidAmount());
        assertEquals(PaymentMethod.CASH, paid.getPaymentMethod());
    }

    @Test
    void draftPayslipsCannotBePaid() {
        Payslip payslip = enrolledPayslip(1000.0);

        assertThrows(IllegalStateException.class,
                () -> payrollService.recordPayment(payslip.getId(), validPayment()));
        assertEquals(PaidStatus.UNPAID, payslip.getPaidStatus());
        assertNull(payslip.getPaidAmount());
    }

    @Test
    void paymentRequiresValidDateMethodAndBankReference() {
        Payslip payslip = enrolledPayslip(1000.0);
        payrollService.confirmPayroll(payslip.getPayroll().getId());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        for (PayslipPaymentRequest invalid : List.of(
                payment(null, PaymentMethod.CASH, null, null),
                payment(today.plusDays(1), PaymentMethod.CASH, null, null),
                payment(today, null, null, null),
                payment(today, PaymentMethod.BANK_TRANSFER, null, null),
                payment(today, PaymentMethod.BANK_TRANSFER, "   ", null))) {
            assertThrows(IllegalArgumentException.class,
                    () -> payrollService.recordPayment(payslip.getId(), invalid));
        }
        assertEquals(PaidStatus.UNPAID, payslip.getPaidStatus());
        assertNull(payslip.getPaidAmount());
    }

    @Test
    void zeroNetPayslipCannotBeRecordedAsPayment() {
        Payslip payslip = enrolledPayslip(0.0);
        payrollService.confirmPayroll(payslip.getPayroll().getId());

        assertThrows(IllegalArgumentException.class,
                () -> payrollService.recordPayment(payslip.getId(), validPayment()));
        assertEquals(PaidStatus.UNPAID, payslip.getPaidStatus());
    }

    @Test
    void repeatedPaymentAndLegacyStatusTransitionsCannotChangeSettlement() {
        Payslip payslip = enrolledPayslip(1000.0);
        payrollService.confirmPayroll(payslip.getPayroll().getId());
        assertThrows(IllegalStateException.class,
                () -> payrollService.updatePayslip(payslip.getId(), PaidStatus.PAID, "Legacy shortcut"));
        payrollService.recordPayment(payslip.getId(), validPayment());

        assertThrows(IllegalStateException.class,
                () -> payrollService.recordPayment(payslip.getId(),
                        payment(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), PaymentMethod.CASH, null, "Second payment")));
        assertThrows(IllegalStateException.class,
                () -> payrollService.updatePayslip(payslip.getId(), PaidStatus.UNPAID, "Reopen payment"));
        assertEquals(PaidStatus.PAID, payslip.getPaidStatus());
        assertEquals(PaymentMethod.BANK_TRANSFER, payslip.getPaymentMethod());
        assertEquals("TEST-TRANSFER", payslip.getPaymentReference());
        assertEquals(1000.0, payslip.getPaidAmount());
    }

    @Test
    void deletedPayslipOrPayrollCannotBePaid() {
        Payslip deletedPayslip = enrolledPayslip(1000.0);
        payrollService.confirmPayroll(deletedPayslip.getPayroll().getId());
        deletedPayslip.setIsDeleted(true);
        payslipRepository.saveAndFlush(deletedPayslip);
        assertThrows(IllegalArgumentException.class,
                () -> payrollService.recordPayment(deletedPayslip.getId(), validPayment()));

        Payslip deletedPayroll = enrolledPayslip(1000.0);
        payrollService.confirmPayroll(deletedPayroll.getPayroll().getId());
        deletedPayroll.getPayroll().setIsDeleted(true);
        payrollRepository.saveAndFlush(deletedPayroll.getPayroll());
        assertThrows(IllegalArgumentException.class,
                () -> payrollService.recordPayment(deletedPayroll.getId(), validPayment()));
    }

    @Test
    void standardDayFormulaRejectsZeroDenominatorWhileFixedSalaryCanUseZeroStandardDays() {
        Period period = period();
        period.setStandardWorkDays(0.0);
        periodRepository.saveAndFlush(period);
        Payroll payroll = createPayroll(period);
        Staff staff = configuredStaff(1000.0);
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        Payslip payslip = onlyPayslip(payroll);
        SalaryItem salary = payslip.getItems().get(0).getSalaryItem();

        assertEquals(1000.0, payslip.getNetSalary());
        salary.setCalculationType(SalaryCalculationType.BY_STANDARD_DAYS);
        salaryItemRepository.saveAndFlush(salary);
        assertThrows(IllegalArgumentException.class, () -> payrollService.calculatePayroll(payroll.getId()));
        assertThrows(IllegalArgumentException.class, () -> payrollService.confirmPayroll(payroll.getId()));
        assertEquals(PayrollStatus.DRAFT, payroll.getStatus());
    }

    @Test
    void reenrollmentCreatesNewPayslipAndKeepsRemovedPayslipDeleted() {
        Payslip original = enrolledPayslip(1000.0);
        Payroll payroll = original.getPayroll();
        UUID oldId = original.getId();
        UUID staffId = original.getStaff().getId();
        payrollService.removePayslip(oldId);

        payrollService.enrollStaffs(payroll.getId(), List.of(staffId));

        Payslip replacement = onlyPayslip(payroll);
        assertNotEquals(oldId, replacement.getId());
        assertEquals(staffId, replacement.getStaff().getId());
        assertTrue(payslipRepository.findById(oldId).isEmpty()
                || Boolean.TRUE.equals(payslipRepository.findById(oldId).orElseThrow().getIsDeleted()));
    }

    @Test
    void historicalPaidPayslipWithNoNewMetadataRemainsReadableAndCannotBePaidAgain() {
        Payslip historical = enrolledPayslip(1000.0);
        payrollService.confirmPayroll(historical.getPayroll().getId());
        historical.setPaidStatus(PaidStatus.PAID);
        historical.setStandardWorkDays(null);
        historical.getItems().get(0).setCalculationType(null);
        historical.getItems().get(0).setType(null);
        payslipRepository.saveAndFlush(historical);
        User user = new User();
        user.setStaff(historical.getStaff());

        Payslip readable = payrollService.getMyPayslip(historical.getPayroll().getPeriod().getId(), user);

        assertEquals(historical.getId(), readable.getId());
        assertEquals(PaidStatus.PAID, readable.getPaidStatus());
        assertNull(readable.getStandardWorkDays());
        assertEquals(SalaryCalculationType.FIXED, readable.getItems().get(0).getCalculationType());
        assertEquals(SalaryItemType.INCOME, readable.getItems().get(0).getType());
        assertNull(readable.getPaidAmount());
        assertNull(readable.getPaidDate());
        assertThrows(IllegalStateException.class,
                () -> payrollService.recordPayment(historical.getId(), validPayment()));
        assertNull(readable.getPaidAmount());
    }

    @Test
    void deletedSalaryDefinitionCannotBeAssignedToEmployeeProfile() {
        Staff staff = configuredStaff(1000.0);
        SalaryItem deleted = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        deleted.setIsDeleted(true);
        salaryItemRepository.saveAndFlush(deleted);

        StaffSalaryItem assignment = assignment(deleted, 100.0);
        assertThrows(IllegalArgumentException.class,
                () -> salaryItemService.saveStaffSalaryItems(staff.getId(), List.of(assignment)));
        assertEquals(1000.0, salaryItemService.getStaffSalaryItems(staff.getId()).get(0).getAmount());
        assertEquals(1, salaryItemService.getStaffSalaryItems(staff.getId()).size());
    }

    @Test
    void eachFormulaRoundsHalfUpToWholeVndBeforeTotalsAreSummed() {
        Payroll payroll = createPayroll(period());
        Staff staff = staff();
        SalaryItem fixed = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        SalaryItem standard = salaryItem(SalaryCalculationType.BY_STANDARD_DAYS, SalaryItemType.INCOME);
        SalaryItem deduction = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.DEDUCTION);
        configure(staff, fixed, 10.5);
        configure(staff, standard, 26.0);
        configure(staff, deduction, 0.5);
        timesheet(staff, LocalDate.of(2026, 9, 1), 1.5, 0.0, TimesheetStatus.APPROVED, false);

        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        Payslip payslip = onlyPayslip(payroll);

        assertEquals(11.0, line(payslip, fixed).getCalculatedValue());
        assertEquals(2.0, line(payslip, standard).getCalculatedValue());
        assertEquals(1.0, line(payslip, deduction).getCalculatedValue());
        assertEquals(13.0, payslip.getTotalIncome());
        assertEquals(1.0, payslip.getTotalDeduction());
        assertEquals(12.0, payslip.getNetSalary());
    }

    @Test
    void standardDayFormulaRoundsDirectlyToWholeVndWithoutIntermediateCentRounding() {
        Period period = period();
        period.setStandardWorkDays(1.0);
        periodRepository.saveAndFlush(period);
        Payroll payroll = createPayroll(period);
        Staff staff = staff();
        configure(staff, salaryItem(SalaryCalculationType.BY_STANDARD_DAYS, SalaryItemType.INCOME), 0.99);
        timesheet(staff, LocalDate.of(2026, 9, 1), 0.5, 0.0, TimesheetStatus.APPROVED, false);

        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));

        assertEquals(0.0, onlyPayslip(payroll).getItems().get(0).getCalculatedValue());
        assertEquals(0.0, onlyPayslip(payroll).getNetSalary());
    }

    @Test
    void legacyDraftWithoutStandardWorkSnapshotRefreshesAutomaticallyDuringConfirmation() {
        Payslip legacy = enrolledPayslip(1000.0);
        Payroll payroll = legacy.getPayroll();
        legacy.setStandardWorkDays(null);
        legacy.getItems().get(0).setCalculationType(null);
        legacy.getItems().get(0).setType(null);
        payslipRepository.saveAndFlush(legacy);
        timesheet(legacy.getStaff(), LocalDate.of(2026, 9, 1), 1.0, 0.0, TimesheetStatus.APPROVED, false);

        assertNull(legacy.getStandardWorkDays());
        payrollService.confirmPayroll(payroll.getId());

        Payslip calculated = onlyPayslip(payroll);
        assertEquals(26.0, calculated.getStandardWorkDays());
        assertEquals(1.0, calculated.getTotalWorkDays());
        assertEquals(SalaryCalculationType.FIXED, calculated.getItems().get(0).getCalculationType());
        assertEquals(SalaryItemType.INCOME, calculated.getItems().get(0).getType());
        assertEquals(PayrollStatus.CONFIRMED, payrollService.confirmPayroll(payroll.getId()).getStatus());
    }

    @Test
    void manualDraftApiRejectsEvenLegacyFormulaOverrides() {
        Payslip payslip = enrolledPayslip(1000.0);
        SalaryItem salary = payslip.getItems().get(0).getSalaryItem();

        assertThrows(IllegalStateException.class, () -> payrollService.updateDraftPayslip(payslip.getId(),
                draft(null, item(salary, 1000.0, SalaryCalculationType.DAILY_MULTIPLIED, SalaryItemType.INCOME))));
        assertEquals(1000.0, payslip.getNetSalary());
        assertEquals(SalaryCalculationType.FIXED, payslip.getItems().get(0).getCalculationType());
    }

    @Test
    void legacyDailyProfileRequiresUpdatingEmployeeSalaryDefinitionBeforeCreatingPayroll() {
        Payroll payroll = createPayroll(period());
        Staff staff = staff();
        SalaryItem legacySalary = salaryItem(SalaryCalculationType.DAILY_MULTIPLIED, SalaryItemType.INCOME);
        StaffSalaryItem legacyConfiguration = configure(staff, legacySalary, 200.0);
        timesheet(staff, LocalDate.of(2026, 9, 1), 1.5, 0.0, TimesheetStatus.APPROVED, false);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId())));
        assertTrue(error.getMessage().contains("công chuẩn"));
        assertTrue(payrollService.getPayrollDetails(payroll.getId()).isEmpty());
        legacySalary.setCalculationType(SalaryCalculationType.BY_STANDARD_DAYS);
        salaryItemRepository.saveAndFlush(legacySalary);
        legacyConfiguration.setAmount(2600.0);
        staffSalaryItemRepository.saveAndFlush(legacyConfiguration);
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));

        assertEquals(150.0, onlyPayslip(payroll).getNetSalary());
        assertEquals(SalaryCalculationType.BY_STANDARD_DAYS, onlyPayslip(payroll).getItems().get(0).getCalculationType());
        assertEquals(PayrollStatus.CONFIRMED, payrollService.confirmPayroll(payroll.getId()).getStatus());
        assertEquals(SalaryCalculationType.BY_STANDARD_DAYS,
                salaryItemRepository.findById(legacySalary.getId()).orElseThrow().getCalculationType());
        assertEquals(2600.0, staffSalaryItemRepository.findById(legacyConfiguration.getId()).orElseThrow().getAmount());
    }

    @Test
    void historicalConfirmedDailyFormulaAndAmountsRemainReadableWithoutConversion() {
        Payslip historical = enrolledPayslip(1000.0);
        historical.getPayroll().setStatus(PayrollStatus.CONFIRMED);
        payrollRepository.saveAndFlush(historical.getPayroll());
        historical.getItems().get(0).setCalculationType(SalaryCalculationType.DAILY_MULTIPLIED);
        historical.getItems().get(0).setAmount(125.0);
        historical.getItems().get(0).setCalculatedValue(187.5);
        historical.setTotalWorkDays(1.5);
        historical.setTotalIncome(187.5);
        historical.setNetSalary(187.5);
        payslipRepository.saveAndFlush(historical);
        User user = new User();
        user.setStaff(historical.getStaff());
        UUID periodId = historical.getPayroll().getPeriod().getId();
        entityManager.clear();

        Payslip readable = payrollService.getMyPayslip(periodId, user);

        assertEquals(SalaryCalculationType.DAILY_MULTIPLIED, readable.getItems().get(0).getCalculationType());
        assertEquals(125.0, readable.getItems().get(0).getAmount());
        assertEquals(187.5, readable.getItems().get(0).getCalculatedValue());
        assertEquals(187.5, readable.getNetSalary());
    }

    @Test
    void salaryDefinitionsCannotBeCreatedOrUpdatedUsingLegacyDailyFormula() {
        SalaryItem creation = new SalaryItem();
        creation.setCode(UUID.randomUUID().toString());
        creation.setName("Unsupported daily salary");
        creation.setType(SalaryItemType.INCOME);
        creation.setCalculationType(SalaryCalculationType.DAILY_MULTIPLIED);
        assertThrows(IllegalArgumentException.class, () -> salaryItemService.saveSalaryItem(creation));
        assertTrue(salaryItemRepository.findActiveByCode(creation.getCode()).isEmpty());

        SalaryItem fixed = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        SalaryItem update = new SalaryItem();
        update.setId(fixed.getId());
        update.setCode(fixed.getCode());
        update.setName(fixed.getName());
        update.setType(fixed.getType());
        update.setCalculationType(SalaryCalculationType.DAILY_MULTIPLIED);
        assertThrows(IllegalArgumentException.class, () -> salaryItemService.saveSalaryItem(update));
        assertEquals(SalaryCalculationType.FIXED, salaryItemRepository.findById(fixed.getId()).orElseThrow().getCalculationType());
    }

    @Test
    void legacySalaryConfigurationRemainsReadableButCannotBeAssignedToEmployeeAgain() {
        SalaryItem legacy = salaryItem(SalaryCalculationType.DAILY_MULTIPLIED, SalaryItemType.INCOME);
        Staff oldStaff = staff();
        configure(oldStaff, legacy, 200.0);
        Staff targetStaff = configuredStaff(1000.0);
        StaffSalaryItem original = staffSalaryItemRepository.findByStaffId(targetStaff.getId()).get(0);
        assertTrue(salaryItemService.getAllSalaryItems().stream().anyMatch(item -> item.getId().equals(legacy.getId())
                && item.getCalculationType() == SalaryCalculationType.DAILY_MULTIPLIED));
        assertEquals(SalaryCalculationType.DAILY_MULTIPLIED,
                salaryItemService.getStaffSalaryItems(oldStaff.getId()).get(0).getSalaryItem().getCalculationType());
        StaffSalaryItem assignment = new StaffSalaryItem();
        assignment.setSalaryItem(legacy);
        assignment.setAmount(500.0);

        assertThrows(IllegalArgumentException.class,
                () -> salaryItemService.saveStaffSalaryItems(targetStaff.getId(), List.of(assignment)));
        assertFalse(Boolean.TRUE.equals(original.getIsDeleted()));
        assertEquals(1, salaryItemService.getStaffSalaryItems(targetStaff.getId()).size());
        assertEquals(1000.0, salaryItemService.getStaffSalaryItems(targetStaff.getId()).get(0).getAmount());
    }

    @Test
    void supportedSalaryDefinitionsAndEmployeeAssignmentsRemainWritable() {
        SalaryItem salary = new SalaryItem();
        salary.setCode(UUID.randomUUID().toString());
        salary.setName("Supported salary");
        salary.setType(SalaryItemType.INCOME);
        salary.setCalculationType(SalaryCalculationType.FIXED);
        SalaryItem saved = salaryItemService.saveSalaryItem(salary);
        assertEquals(SalaryCalculationType.FIXED, saved.getCalculationType());
        saved.setCalculationType(SalaryCalculationType.BY_STANDARD_DAYS);
        salaryItemService.saveSalaryItem(saved);
        entityManager.flush();
        assertEquals(SalaryCalculationType.BY_STANDARD_DAYS,
                salaryItemRepository.findById(saved.getId()).orElseThrow().getCalculationType());
        Staff staff = staff();
        StaffSalaryItem assignment = new StaffSalaryItem();
        assignment.setSalaryItem(saved);
        assignment.setAmount(2600.0);

        List<StaffSalaryItem> assigned = salaryItemService.saveStaffSalaryItems(staff.getId(), List.of(assignment));

        assertEquals(1, assigned.size());
        assertEquals(2600.0, assigned.get(0).getAmount());
        assertEquals(SalaryCalculationType.BY_STANDARD_DAYS, assigned.get(0).getSalaryItem().getCalculationType());
    }

    @Test
    void profileAddedAfterLegacyEmptyPayslipIsLoadedDuringRecalculation() {
        Payroll payroll = createPayroll(period());
        Staff staff = staff();
        Payslip legacy = legacyEmptyPayslip(payroll, staff);
        SalaryItem salary = salaryItem(SalaryCalculationType.BY_STANDARD_DAYS, SalaryItemType.INCOME);
        configure(staff, salary, 2600.0);
        timesheet(staff, LocalDate.of(2026, 9, 1), 1.5, 0.0, TimesheetStatus.APPROVED, false);

        payrollService.calculatePayroll(payroll.getId());
        entityManager.flush();
        entityManager.clear();

        Payslip refreshed = onlyPayslip(payroll);
        assertEquals(legacy.getId(), refreshed.getId());
        assertEquals(1, refreshed.getItems().size());
        assertEquals(2600.0, refreshed.getItems().get(0).getAmount());
        assertEquals(150.0, refreshed.getNetSalary());
        assertEquals(1.5, refreshed.getTotalWorkDays());
    }

    @Test
    void profileAddedAfterLegacyEmptyPayslipCanBeConfirmedWithoutAnyDraftEditing() {
        Payroll payroll = createPayroll(period());
        Staff staff = staff();
        Payslip legacy = legacyEmptyPayslip(payroll, staff);
        configure(staff, salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME), 1700.0);

        payrollService.confirmPayroll(payroll.getId());
        entityManager.clear();

        Payslip confirmed = onlyPayslip(payroll);
        assertEquals(legacy.getId(), confirmed.getId());
        assertEquals(PayrollStatus.CONFIRMED, confirmed.getPayroll().getStatus());
        assertEquals(1700.0, confirmed.getNetSalary());
        assertEquals(26.0, confirmed.getStandardWorkDays());
    }

    @Test
    void individualApiRefreshUsesProfileEvenWhenAnotherLegacyMemberIsMissingConfiguration() throws Exception {
        Payroll payroll = createPayroll(period());
        Staff configured = staff();
        Payslip selected = legacyEmptyPayslip(payroll, configured);
        Staff missing = staff();
        missing.setDisplayName("Missing salary employee");
        staffRepository.saveAndFlush(missing);
        Payslip missingSlip = legacyEmptyPayslip(payroll, missing);
        configure(configured, salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME), 1900.0);

        JsonNode refreshed = api(post("/api/payrolls/payslip/" + selected.getId() + "/calculate"));

        assertEquals(selected.getId().toString(), refreshed.path("id").asText());
        assertEquals(1900.0, refreshed.path("netSalary").asDouble());
        assertTrue(payslipRepository.findById(missingSlip.getId()).orElseThrow().getItems().isEmpty());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> payrollService.confirmPayroll(payroll.getId()));
        assertTrue(error.getMessage().contains(missing.getDisplayName()));
        assertEquals(PayrollStatus.DRAFT, payrollRepository.findById(payroll.getId()).orElseThrow().getStatus());
    }

    @Test
    void staleManualPayslipItemsAreReplacedByCurrentEmployeeProfile() {
        Payslip payslip = enrolledPayslip(1000.0);
        Staff staff = payslip.getStaff();
        PayslipItem stale = payslip.getItems().get(0);
        stale.setAmount(999999.0);
        stale.setCalculatedValue(999999.0);
        stale.setCalculationType(SalaryCalculationType.DAILY_MULTIPLIED);
        stale.setType(SalaryItemType.DEDUCTION);
        payslip.setNetSalary(999999.0);
        payslipRepository.saveAndFlush(payslip);
        SalaryItem current = salaryItem(SalaryCalculationType.BY_STANDARD_DAYS, SalaryItemType.INCOME);
        salaryItemService.saveStaffSalaryItems(staff.getId(), List.of(assignment(current, 2600.0)));
        timesheet(staff, LocalDate.of(2026, 9, 1), 2.0, 0.0, TimesheetStatus.APPROVED, false);

        payrollService.confirmPayroll(payslip.getPayroll().getId());
        entityManager.clear();

        Payslip confirmed = payslipRepository.findById(payslip.getId()).orElseThrow();
        assertEquals(1, confirmed.getItems().size());
        assertEquals(current.getId(), confirmed.getItems().get(0).getSalaryItem().getId());
        assertEquals(SalaryCalculationType.BY_STANDARD_DAYS, confirmed.getItems().get(0).getCalculationType());
        assertEquals(SalaryItemType.INCOME, confirmed.getItems().get(0).getType());
        assertEquals(200.0, confirmed.getNetSalary());
    }

    @Test
    void deletedProfileAssignmentsAndSalaryDefinitionsAreExcludedFromListingAndPayroll() {
        Staff staff = configuredStaff(1000.0);
        SalaryItem deletedDefinition = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        configure(staff, deletedDefinition, 2000.0);
        deletedDefinition.setIsDeleted(true);
        salaryItemRepository.saveAndFlush(deletedDefinition);
        StaffSalaryItem deletedAssignment = configure(staff,
                salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME), 3000.0);
        deletedAssignment.setIsDeleted(true);
        staffSalaryItemRepository.saveAndFlush(deletedAssignment);

        List<StaffSalaryItem> displayed = salaryItemService.getStaffSalaryItems(staff.getId());
        assertEquals(1, displayed.size());
        assertEquals(1000.0, displayed.get(0).getAmount());
        Payroll payroll = createPayroll(period());
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        assertEquals(1000.0, onlyPayslip(payroll).getNetSalary());
        assertEquals(1, onlyPayslip(payroll).getItems().size());

        displayed.get(0).getSalaryItem().setIsDeleted(true);
        salaryItemRepository.saveAndFlush(displayed.get(0).getSalaryItem());
        assertTrue(salaryItemService.getStaffSalaryItems(staff.getId()).isEmpty());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> payrollService.confirmPayroll(payroll.getId()));
        assertTrue(error.getMessage().contains(staff.getDisplayName()));
    }

    @Test
    void invalidProfileAmountsAndDuplicateItemsDoNotReplaceExistingConfiguration() {
        Staff staff = configuredStaff(1000.0);
        StaffSalaryItem original = salaryItemService.getStaffSalaryItems(staff.getId()).get(0);
        SalaryItem salary = original.getSalaryItem();
        for (Double amount : new Double[] {null, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> salaryItemService.saveStaffSalaryItems(staff.getId(), List.of(assignment(salary, amount))));
            assertFalse(Boolean.TRUE.equals(original.getIsDeleted()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> salaryItemService.saveStaffSalaryItems(staff.getId(),
                        List.of(assignment(salary, 1.0), assignment(salary, 2.0))));
        StaffSalaryItem missingSalary = new StaffSalaryItem();
        missingSalary.setAmount(1000.0);
        assertThrows(IllegalArgumentException.class,
                () -> salaryItemService.saveStaffSalaryItems(staff.getId(), List.of(missingSalary)));
        assertEquals(1, salaryItemService.getStaffSalaryItems(staff.getId()).size());
        assertEquals(1000.0, salaryItemService.getStaffSalaryItems(staff.getId()).get(0).getAmount());
    }

    @Test
    void deletedEmployeeAndSalaryWithMissingClassificationCannotReceiveProfileAssignment() {
        Staff deleted = staff();
        deleted.setIsDeleted(true);
        staffRepository.saveAndFlush(deleted);
        SalaryItem salary = salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME);
        assertThrows(IllegalArgumentException.class,
                () -> salaryItemService.saveStaffSalaryItems(deleted.getId(), List.of(assignment(salary, 1000.0))));
        Staff active = staff();
        salary.setType(null);
        assertThrows(IllegalArgumentException.class,
                () -> salaryItemService.saveStaffSalaryItems(active.getId(), List.of(assignment(salary, 1000.0))));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedConfirmationWithMissingProfileDoesNotPersistAnyRefreshedMember() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ConfirmationFixture fixture = tx.execute(status -> {
            Period period = period();
            Payroll payroll = createPayroll(period);
            Staff configured = configuredStaff(1000.0);
            payrollService.enrollStaffs(payroll.getId(), List.of(configured.getId()));
            Payslip existing = onlyPayslip(payroll);
            StaffSalaryItem configuration = staffSalaryItemRepository.findByStaffId(configured.getId()).get(0);
            configuration.setAmount(2000.0);
            staffSalaryItemRepository.saveAndFlush(configuration);
            Staff missing = staff();
            missing.setDisplayName("Unconfigured member for atomic approval");
            staffRepository.saveAndFlush(missing);
            legacyEmptyPayslip(payroll, missing);
            return new ConfirmationFixture(period.getId(), payroll.getId(), existing.getId(),
                    List.of(configured.getId(), missing.getId()), configuration.getSalaryItem().getId(),
                    configuration.getId(), missing.getDisplayName());
        });
        try {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> payrollService.confirmPayroll(fixture.payrollId()));
            assertTrue(error.getMessage().contains(fixture.missingStaffName()));
            tx.executeWithoutResult(status -> {
                Payslip unchanged = payslipRepository.findById(fixture.payslipId()).orElseThrow();
                assertEquals(1000.0, unchanged.getNetSalary());
                assertEquals(1000.0, unchanged.getItems().get(0).getAmount());
                assertEquals(PayrollStatus.DRAFT, unchanged.getPayroll().getStatus());
            });
        } finally {
            tx.executeWithoutResult(status -> {
                payslipRepository.deleteAll(payslipRepository.findByPayrollId(fixture.payrollId()));
                payrollRepository.deleteById(fixture.payrollId());
                staffSalaryItemRepository.deleteById(fixture.configurationId());
                salaryItemRepository.deleteById(fixture.salaryId());
                staffRepository.deleteAllById(fixture.staffIds());
                periodRepository.deleteById(fixture.periodId());
            });
        }
    }

    private record ConfirmationFixture(UUID periodId, UUID payrollId, UUID payslipId,
            List<UUID> staffIds, UUID salaryId, UUID configurationId, String missingStaffName) {}

    private Payslip legacyEmptyPayslip(Payroll payroll, Staff staff) {
        Payslip payslip = new Payslip();
        payslip.setPayroll(payroll);
        payslip.setStaff(staff);
        return payslipRepository.saveAndFlush(payslip);
    }

    private StaffSalaryItem assignment(SalaryItem salary, Double amount) {
        StaffSalaryItem assignment = new StaffSalaryItem();
        assignment.setSalaryItem(salary);
        assignment.setAmount(amount);
        return assignment;
    }

    private Payroll createPayroll(Period period) {
        return payrollService.createPayroll(period.getId(), "Manual payroll regression", UUID.randomUUID().toString(), null);
    }

    private Period period() {
        Period period = new Period();
        period.setName("Manual payroll period");
        period.setCode(UUID.randomUUID().toString());
        period.setMonth(9);
        period.setYear(2026);
        period.setFromDate(LocalDate.of(2026, 9, 1));
        period.setToDate(LocalDate.of(2026, 9, 30));
        period.setStandardWorkDays(26.0);
        return periodRepository.saveAndFlush(period);
    }

    private Staff staff() {
        Staff staff = new Staff();
        staff.setStaffCode(UUID.randomUUID().toString());
        staff.setDisplayName("Manual payroll employee");
        staff.setWorkingStatus("7");
        return staffRepository.saveAndFlush(staff);
    }

    private Staff configuredStaff(double amount) {
        Staff staff = staff();
        configure(staff, salaryItem(SalaryCalculationType.FIXED, SalaryItemType.INCOME), amount);
        return staff;
    }

    private SalaryItem salaryItem(SalaryCalculationType calculationType, SalaryItemType type) {
        SalaryItem salary = new SalaryItem();
        salary.setName("Salary line " + UUID.randomUUID());
        salary.setCode(UUID.randomUUID().toString());
        salary.setCalculationType(calculationType);
        salary.setType(type);
        return salaryItemRepository.saveAndFlush(salary);
    }

    private StaffSalaryItem configure(Staff staff, SalaryItem salary, double amount) {
        StaffSalaryItem configuration = new StaffSalaryItem();
        configuration.setStaff(staff);
        configuration.setSalaryItem(salary);
        configuration.setAmount(amount);
        return staffSalaryItemRepository.saveAndFlush(configuration);
    }

    private Timesheet timesheet(Staff staff, LocalDate date, double ratio, double overtime,
            TimesheetStatus status, boolean deleted) {
        Timesheet timesheet = new Timesheet();
        timesheet.setStaff(staff);
        timesheet.setWorkingDate(date);
        timesheet.setTotalWorkRatio(ratio);
        timesheet.setOvertimeHours(overtime);
        timesheet.setStatus(status);
        timesheet.setIsDeleted(deleted);
        return timesheetRepository.saveAndFlush(timesheet);
    }

    private Payslip enrolledPayslip(double amount) {
        Payroll payroll = createPayroll(period());
        Staff staff = configuredStaff(amount);
        payrollService.enrollStaffs(payroll.getId(), List.of(staff.getId()));
        return onlyPayslip(payroll);
    }

    private Payslip onlyPayslip(Payroll payroll) {
        List<Payslip> payslips = payrollService.getPayrollDetails(payroll.getId());
        assertEquals(1, payslips.size());
        return payslips.get(0);
    }

    private PayslipItem line(Payslip payslip, SalaryItem salary) {
        return payslip.getItems().stream().filter(i -> i.getSalaryItem().getId().equals(salary.getId()))
                .findFirst().orElseThrow();
    }

    private PayslipDraftRequest.Item item(SalaryItem salary, double amount,
            SalaryCalculationType formula, SalaryItemType type) {
        PayslipDraftRequest.Item item = new PayslipDraftRequest.Item();
        item.setSalaryItemId(salary.getId());
        item.setAmount(amount);
        item.setCalculationType(formula);
        item.setType(type);
        return item;
    }

    private PayslipDraftRequest draft(String note, PayslipDraftRequest.Item... items) {
        PayslipDraftRequest draft = new PayslipDraftRequest();
        draft.setItems(List.of(items));
        draft.setNote(note);
        return draft;
    }

    private PayslipPaymentRequest payment(LocalDate date, PaymentMethod method, String reference, String note) {
        PayslipPaymentRequest payment = new PayslipPaymentRequest();
        payment.setPaidDate(date);
        payment.setPaymentMethod(method);
        payment.setPaymentReference(reference);
        payment.setNote(note);
        return payment;
    }

    private PayslipPaymentRequest validPayment() {
        return payment(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), PaymentMethod.BANK_TRANSFER,
                "TEST-TRANSFER", "Payment regression");
    }

    private JsonNode api(MockHttpServletRequestBuilder request) throws Exception {
        byte[] result = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        entityManager.flush();
        entityManager.clear();
        return mapper.readTree(result).path("data");
    }

    private JsonNode api(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return api(request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
}
