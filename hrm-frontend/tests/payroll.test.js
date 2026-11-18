import test from 'node:test';
import assert from 'node:assert/strict';
import {
    calculateSalaryLine, canRecordPayslipPayment, describeSalaryFormula,
    getSalaryLineCalculationType, getSalaryLineType, getVietnamToday,
    previewPayslipTotals, salaryCalculationLabels, validatePayslipPayment,
} from '../src/utils/payroll.js';

const fixed = { salaryItemId: 'salary', amount: 12000000, type: 'INCOME', calculationType: 'FIXED' };
const daily = { salaryItemId: 'meal', amount: 30000, type: 'INCOME', calculationType: 'DAILY_MULTIPLIED' };
const standard = { salaryItemId: 'monthly', amount: 12000000, type: 'INCOME', calculationType: 'BY_STANDARD_DAYS' };

test('current formulas use fixed money or actual divided by standard days while historic daily lines remain legible', () => {
    assert.equal(calculateSalaryLine(fixed, 0, 0), 12000000);
    assert.equal(calculateSalaryLine(fixed, undefined, undefined), 12000000);
    assert.equal(calculateSalaryLine(daily, 20.5, 26), 615000);
    assert.equal(calculateSalaryLine(standard, 20.5, 26), 9461538);
    assert.equal(calculateSalaryLine(standard, 0, 26), 0);
    assert.equal(calculateSalaryLine(daily, 0, 26), 0);
    assert.equal(calculateSalaryLine(standard, 27, 26), 12461538);
});

test('saved line snapshots take precedence over changed salary item configuration including OT code', () => {
    const historical = { ...standard, type: 'DEDUCTION', salaryItem: { code: 'OT', type: 'INCOME', calculationType: 'FIXED' } };
    assert.equal(getSalaryLineType(historical), 'DEDUCTION');
    assert.equal(getSalaryLineCalculationType(historical), 'BY_STANDARD_DAYS');
    assert.equal(calculateSalaryLine(historical, 13, 26), 6000000);
    assert.match(describeSalaryFormula(historical, 13, 26), /26 công chuẩn × 13 công thực tế/);
    assert.doesNotMatch(describeSalaryFormula(historical, 13, 26), /1\.5|8 giờ|OT/);
    assert.equal(getSalaryLineType({ salaryItem: { type: 'INCOME' } }), 'INCOME');
});

test('missing or invalid standard days cannot silently use an invented standard', () => {
    for (const days of [undefined, null, 0, -1, NaN, Infinity]) {
        assert.equal(calculateSalaryLine(standard, 20, days), null);
        assert.match(describeSalaryFormula(standard, 20, days), /chưa lưu số công chuẩn/);
    }
    for (const amount of ['', null, -1, 'abc', Infinity]) {
        assert.equal(calculateSalaryLine({ ...fixed, amount }, 20, 26), null);
    }
    assert.equal(calculateSalaryLine({ ...fixed, calculationType: 'OTHER' }, 20, 26), null);
});

test('preview rounds each line half up to whole VND before summing income and deductions', () => {
    const items = [
        { ...fixed, amount: 10.5 },
        { ...daily, amount: 1.5 },
        { ...fixed, amount: 2.5, type: 'DEDUCTION' },
    ];
    assert.deepEqual(previewPayslipTotals(items, 1, 26), { totalIncome: 13, totalDeduction: 3, netSalary: 10 });
    assert.equal(calculateSalaryLine({ ...fixed, amount: 0.49 }, 1, 26), 0);
    assert.equal(calculateSalaryLine({ ...fixed, amount: 0.5 }, 1, 26), 1);
    assert.deepEqual(previewPayslipTotals([{ ...fixed, amount: 1 }, { ...fixed, amount: 2, type: 'DEDUCTION' }], 1, 26), { totalIncome: 1, totalDeduction: 2, netSalary: -1 });
    assert.equal(previewPayslipTotals([standard], 20, undefined), null);
});

test('current salary configuration uses two formulas while saved daily history stays legible', () => {
    assert.deepEqual(Object.keys(salaryCalculationLabels), ['FIXED', 'BY_STANDARD_DAYS']);
    assert.equal(daily.calculationType, 'DAILY_MULTIPLIED');
    assert.equal(calculateSalaryLine(daily, 20.5, 26), 615000);
    assert.match(describeSalaryFormula(daily, 20.5, 26), /Theo công thực tế/);
});

test('payment eligibility requires a live confirmed unpaid slip with positive salary', () => {
    const detail = { paidStatus: 'UNPAID', netSalary: 100 };
    const payroll = { status: 'CONFIRMED' };
    assert.equal(canRecordPayslipPayment(detail, payroll), true);
    for (const modified of [{ ...detail, paidStatus: 'PAID' }, { ...detail, netSalary: 0 }, { ...detail, netSalary: -1 }, { ...detail, netSalary: Infinity }, { ...detail, netSalary: NaN }, { ...detail, isDeleted: true }]) {
        assert.equal(canRecordPayslipPayment(modified, payroll), false);
    }
    assert.equal(canRecordPayslipPayment(detail, { status: 'DRAFT' }), false);
    assert.equal(canRecordPayslipPayment(detail, { ...payroll, isDeleted: true }), false);
});

test('Vietnam payment day follows local midnight rather than browser or UTC dates', () => {
    assert.equal(getVietnamToday(new Date('2026-10-11T16:59:59Z')), '2026-10-11');
    assert.equal(getVietnamToday(new Date('2026-10-11T17:00:00Z')), '2026-10-12');
});

test('bank payment requires a transaction reference and rejects invalid or future dates', () => {
    const payment = { paidDate: '2026-10-12', paymentMethod: 'BANK_TRANSFER', paymentReference: ' TX123 ', note: '' };
    const today = '2026-10-12';
    assert.deepEqual(validatePayslipPayment(payment, today), {});
    assert.deepEqual(validatePayslipPayment({ ...payment, paymentMethod: 'CASH', paymentReference: '' }, today), {});
    assert.ok(validatePayslipPayment({ ...payment, paymentReference: '   ' }, today).paymentReference);
    assert.ok(validatePayslipPayment({ ...payment, paidDate: '2026-10-13' }, today).paidDate);
    for (const paidDate of ['', '12/10/2026', '2026-02-29', '2026-13-01']) {
        assert.ok(validatePayslipPayment({ ...payment, paidDate }, today).paidDate);
    }
    assert.ok(validatePayslipPayment({ ...payment, paymentMethod: 'OTHER' }, today).paymentMethod);
    assert.ok(validatePayslipPayment({ ...payment, paymentReference: 'x'.repeat(256) }, today).paymentReference);
});
