import { SalaryCalculationType, SalaryItemType } from '../constants/payroll.js';

export const formatPayrollMoney = (value) => new Intl.NumberFormat('vi-VN', {
    style: 'currency', currency: 'VND', maximumFractionDigits: 2,
}).format(Number(value) || 0);

export const formatWorkDays = (value) => new Intl.NumberFormat('vi-VN', {
    maximumFractionDigits: 2,
}).format(Number(value) || 0);

export const getSalaryLineType = (item) => item?.type ?? item?.salaryItem?.type;
export const getSalaryLineCalculationType = (item) => item?.calculationType ?? item?.salaryItem?.calculationType;

export const salaryCalculationLabels = {
    [SalaryCalculationType.FIXED]: 'Cố định',
    [SalaryCalculationType.BY_STANDARD_DAYS]: 'Theo công chuẩn',
};

const roundMoney = (value) => Math.sign(value) * Math.round(
    Math.abs(value) + Number.EPSILON * Math.max(1, Math.abs(value)),
);

export function calculateSalaryLine(item, workDays, standardWorkDays) {
    if (item?.amount === '' || item?.amount == null) return null;
    const amount = Number(item.amount);
    const days = Number(workDays);
    const standard = Number(standardWorkDays);
    if (!Number.isFinite(amount) || amount < 0) return null;
    let result;
    switch (getSalaryLineCalculationType(item)) {
        case SalaryCalculationType.FIXED: result = amount; break;
        case SalaryCalculationType.DAILY_MULTIPLIED:
            if (!Number.isFinite(days) || days < 0) return null;
            result = amount * days;
            break;
        case SalaryCalculationType.BY_STANDARD_DAYS:
            if (!Number.isFinite(days) || days < 0 || !Number.isFinite(standard) || standard <= 0) return null;
            result = amount * days / standard;
            break;
        default: return null;
    }
    if (!Number.isFinite(result)) return null;
    return roundMoney(result);
}

export function describeSalaryFormula(item, workDays, standardWorkDays) {
    const amount = formatPayrollMoney(item?.amount);
    const days = formatWorkDays(workDays);
    switch (getSalaryLineCalculationType(item)) {
        case SalaryCalculationType.FIXED: return `Cố định: ${amount}`;
        case SalaryCalculationType.DAILY_MULTIPLIED: return `Theo công thực tế: ${amount} × ${days} công`;
        case SalaryCalculationType.BY_STANDARD_DAYS:
            if (!Number.isFinite(Number(standardWorkDays)) || Number(standardWorkDays) <= 0) {
                return 'Theo công chuẩn: phiếu chưa lưu số công chuẩn.';
            }
            return `Theo công chuẩn: ${amount} ÷ ${formatWorkDays(standardWorkDays)} công chuẩn × ${days} công thực tế`;
        default: return 'Phiếu chưa lưu công thức tính.';
    }
}

export function previewPayslipTotals(items, workDays, standardWorkDays) {
    let income = 0;
    let deduction = 0;
    for (const item of items) {
        const value = calculateSalaryLine(item, workDays, standardWorkDays);
        const type = getSalaryLineType(item);
        if (value == null || !Object.values(SalaryItemType).includes(type)) return null;
        if (type === SalaryItemType.INCOME) income += value;
        else deduction += value;
    }
    return { totalIncome: roundMoney(income), totalDeduction: roundMoney(deduction), netSalary: roundMoney(income - deduction) };
}

export function getVietnamToday(now = new Date()) {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
    }).formatToParts(now);
    const part = (type) => parts.find((value) => value.type === type)?.value;
    return `${part('year')}-${part('month')}-${part('day')}`;
}

export function canRecordPayslipPayment(detail, payroll) {
    const netSalary = Number(detail?.netSalary);
    return payroll?.status === 'CONFIRMED' && !payroll?.isDeleted && !detail?.isDeleted
        && detail?.paidStatus === 'UNPAID' && Number.isFinite(netSalary) && netSalary > 0;
}

export function validatePayslipPayment(payment, today = getVietnamToday()) {
    const errors = {};
    const date = payment?.paidDate || '';
    const parsed = new Date(`${date}T00:00:00Z`);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
        errors.paidDate = 'Chọn ngày thanh toán hợp lệ.';
    } else if (date > today) {
        errors.paidDate = 'Ngày thanh toán không được ở tương lai.';
    }
    if (!['BANK_TRANSFER', 'CASH'].includes(payment?.paymentMethod)) {
        errors.paymentMethod = 'Chọn hình thức thanh toán.';
    }
    if (payment?.paymentMethod === 'BANK_TRANSFER' && !payment?.paymentReference?.trim()) {
        errors.paymentReference = 'Nhập mã giao dịch hoặc số chứng từ chuyển khoản.';
    }
    if ((payment?.paymentReference || '').trim().length > 255) {
        errors.paymentReference = 'Mã giao dịch tối đa 255 ký tự.';
    }
    if ((payment?.note || '').length > 255) errors.note = 'Ghi chú tối đa 255 ký tự.';
    return errors;
}

