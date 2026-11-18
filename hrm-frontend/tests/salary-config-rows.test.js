import test from 'node:test';
import assert from 'node:assert/strict';
import { buildSalaryRows, hasSalaryAmount, salaryRowsPayload } from '../src/pages/Staff/salaryConfigRows.js';
const salary = { id: 'base', name: 'Lương cơ bản', calculationType: 'BY_STANDARD_DAYS' };
const allowance = { id: 'meal', name: 'Ăn trưa', calculationType: 'FIXED' };
test('salary table shows every definition and restores saved amounts without assigning new items', () => {
  const rows = buildSalaryRows([salary, allowance], [{ salaryItem: salary, amount: 13000000 }]);
  assert.equal(rows.length, 2);
  assert.equal(rows.find(row => row.salaryItem.id === 'meal').enabled, false);
  assert.deepEqual(salaryRowsPayload(rows), [{ salaryItem: { id: 'base' }, amount: 13000000 }]);
});
test('typing an amount applies the item; blank or zero means not applied; invalid values are rejected', () => {
  const row = { salaryItem: allowance, enabled: false, amount: '730000' };
  assert.deepEqual(salaryRowsPayload([row]), [{ salaryItem: { id: 'meal' }, amount: 730000 }]);
  assert.equal(hasSalaryAmount(row), true);
  for (const amount of ['', '0', '  ', undefined]) {
    assert.deepEqual(salaryRowsPayload([{ ...row, enabled: true, amount }]), []);
    assert.equal(hasSalaryAmount({ ...row, amount }), false);
  }
  for (const amount of ['-1', 'NaN', 'Infinity']) assert.throws(() => salaryRowsPayload([{ ...row, amount }]));
});
test('clearing the amount of an old-formula item stops applying it instead of blocking the save', () => {
  const legacy = { id: 'old', name: 'Cũ', calculationType: 'DAILY_MULTIPLIED' };
  const rows = buildSalaryRows([salary], [{ salaryItem: legacy, amount: 100 }, { salaryItem: salary, amount: 5000000 }]);
  const cleared = rows.map(row => row.salaryItem.id === 'old' ? { ...row, amount: '' } : row);
  assert.deepEqual(salaryRowsPayload(cleared), [{ salaryItem: { id: 'base' }, amount: 5000000 }]);
});
test('old configuration is shown and cannot be silently dropped or converted to a new formula', () => {
  const legacy = { id: 'old', name: 'Cũ', calculationType: 'DAILY_MULTIPLIED' };
  const rows = buildSalaryRows([salary], [{ salaryItem: legacy, amount: 100 }]);
  assert.equal(rows.length, 2);
  assert.throws(() => salaryRowsPayload(rows), /cách tính cũ/);
});
