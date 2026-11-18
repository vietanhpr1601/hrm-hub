export const SalaryItemType = {
    INCOME: 'INCOME',
    DEDUCTION: 'DEDUCTION'
};

export const SalaryCalculationType = {
    FIXED: 'FIXED',
    BY_STANDARD_DAYS: 'BY_STANDARD_DAYS',
    // Kept only to read historical payrolls; new forms support the two types above.
    DAILY_MULTIPLIED: 'DAILY_MULTIPLIED'
};

export const PaidStatus = {
    PAID: 'PAID',
    UNPAID: 'UNPAID'
};

export const SalaryItemTypeOptions = [
    { value: '', name: 'Tất cả' },
    { value: SalaryItemType.INCOME, name: 'Cộng (Thu nhập)' },
    { value: SalaryItemType.DEDUCTION, name: 'Trừ (Khấu trừ)' }
];

export const SalaryItemTypeFormOptions = [
    { value: SalaryItemType.INCOME, name: 'Cộng (Thu nhập)' },
    { value: SalaryItemType.DEDUCTION, name: 'Trừ (Khấu trừ)' }
];

export const SalaryCalculationTypeOptions = [
    { value: '', name: 'Tất cả' },
    { value: SalaryCalculationType.FIXED, name: 'Cố định' },
    { value: SalaryCalculationType.BY_STANDARD_DAYS, name: 'Theo công chuẩn' }
];

export const SalaryCalculationTypeFormOptions = [
    { value: SalaryCalculationType.FIXED, name: 'Cố định' },
    { value: SalaryCalculationType.BY_STANDARD_DAYS, name: 'Theo công chuẩn' }
];

export const PayrollStatus = {
    DRAFT: 'DRAFT',
    CONFIRMED: 'CONFIRMED'
};

export const PayrollStatusConfig = {
    [PayrollStatus.DRAFT]: {
        label: 'Chờ duyệt',
        bgcolor: '#fff3e0',
        color: '#e65100'
    },
    [PayrollStatus.CONFIRMED]: {
        label: 'Đã duyệt',
        bgcolor: '#e8f5e9',
        color: '#2e7d32'
    }
};

