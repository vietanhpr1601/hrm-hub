import test from 'node:test';
import assert from 'node:assert/strict';
import { batchPreviewRows } from '../src/components/ui/batchApprovalData.js';
test('batch preview accepts only a complete explicit list of pending records', () => {
  const rows = [{ id: 'a', status: 'SUBMITTED' }, { id: 'b', status: 'SUBMITTED' }];
  assert.deepEqual(batchPreviewRows({ totalElements: 2, content: rows }, 'status', 'SUBMITTED'), rows);
  assert.deepEqual(batchPreviewRows({ totalElements: 0, content: [] }, 'status', 'SUBMITTED'), []);
});
test('batch preview refuses truncation, duplicates, wrong states and oversized batches', () => {
  const row = { id: 'a', approvalStatus: 'PENDING' };
  for (const page of [null, { totalElements: 501, content: [] }, { totalElements: 2, content: [row] },
    { totalElements: 2, content: [row, row] }, { totalElements: 1, content: [{ ...row, approvalStatus: 'APPROVED' }] }])
    assert.throws(() => batchPreviewRows(page, 'approvalStatus', 'PENDING'));
});
