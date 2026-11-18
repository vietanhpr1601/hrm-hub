import test from 'node:test';
import assert from 'node:assert/strict';
import * as Yup from 'yup';
import { resignationDateValidation } from '../src/utils/staffEmploymentValidation.js';

const schema = Yup.object({ startDate: Yup.date().nullable(), resignationDate: resignationDateValidation() });

test('resignation date is optional for legacy records and validates actual employment date order', async () => {
    assert.equal(await schema.isValid({ startDate: '2026-01-01', resignationDate: null }), true);
    assert.equal(await schema.isValid({ startDate: '2026-01-01', resignationDate: '2026-01-31' }), true);
    assert.equal(await schema.isValid({ startDate: '2026-01-01', resignationDate: '2026-01-01' }), true);
    assert.equal(await schema.isValid({ startDate: new Date(2026, 0, 1, 14), resignationDate: new Date(2026, 0, 1) }), true);
    assert.equal(await schema.isValid({ startDate: '2026-01-01', resignationDate: '2025-12-31' }), false);
    assert.equal(await schema.isValid({ startDate: null, resignationDate: '2026-01-31' }), true);
    await assert.rejects(schema.validate({ resignationDate: 'not-a-date' }), /Ngày nghỉ việc không hợp lệ/);
});
