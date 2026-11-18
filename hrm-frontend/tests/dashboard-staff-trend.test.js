import test from 'node:test';
import assert from 'node:assert/strict';
import { prepareStaffTrend, staffTrendMissingDateNote } from '../src/pages/Dashboard/staffTrendData.js';

test('staff movement keeps separate exact monthly counts and orders months across years', () => {
    const input = [
        { period: '2026-01', month: '01/2026', newHires: 1, resignations: 0 },
        { period: '2025-12', month: '12/2025', newHires: 0, resignations: 2 },
    ];
    assert.deepEqual(prepareStaffTrend(input), [input[1], input[0]]);
    assert.equal(input[0].period, '2026-01');
});

test('missing or malformed movement data is not fabricated into zero employees', () => {
    assert.deepEqual(prepareStaffTrend(null), []);
    assert.deepEqual(prepareStaffTrend([null, {}, { month: '10/2026', newHires: 0, resignations: null }]), [
        { period: undefined, month: '10/2026', newHires: 0, resignations: null },
    ]);
    assert.equal(prepareStaffTrend([{ month: '10/2026', newHires: -1, resignations: 0.5 }])[0].newHires, null);
    assert.equal(prepareStaffTrend([{ month: '10/2026', newHires: -1, resignations: 0.5 }])[0].resignations, null);
});

test('missing employment dates are explained without assigning an invented month', () => {
    assert.equal(staffTrendMissingDateNote({ unknownStartDates: 0, unknownResignationDates: 0 }), '');
    assert.equal(staffTrendMissingDateNote(undefined), '');
    const note = staffTrendMissingDateNote({ unknownStartDates: 2, unknownResignationDates: 1 });
    assert.match(note, /2 hồ sơ thiếu ngày vào làm/);
    assert.match(note, /1 hồ sơ nghỉ việc thiếu ngày nghỉ/);
});
