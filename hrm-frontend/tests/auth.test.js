import test from 'node:test';
import assert from 'node:assert/strict';

import { ROLES } from '../src/constants/roles.js';
import { getHomePath, getLoginDestination, hasRole } from '../src/utils/auth.js';
import { navigations } from '../src/navigationConfig.js';

const landingCases = [
  [ROLES.ADMIN, '/dashboard', 'DashboardController.java', 'ROLE_ADMIN'],
  [ROLES.HR_MANAGER, '/my-leave', 'LeaveRequestController.java', 'HR_MANAGER'],
  [ROLES.HR_EMPLOYEE, '/my-leave', 'LeaveRequestController.java', 'HR_EMPLOYEE'],
  [ROLES.HR_RECRUITMENT, '/recruitments', 'RecruitmentController.java', 'HR_RECRUITMENT'],
  [ROLES.HR_COMPENSATION_BENEFIT, '/salary/payrolls', 'PayrollController.java', 'HR_COMPENSATION_BENEFIT'],
  [ROLES.HR_TIMEKEEPING_MANAGER, '/time-sheet-summary', 'TimesheetController.java', 'HR_TIMEKEEPING_MANAGER'],
];

test('each role lands on a page allowed by frontend navigation', () => {
  const flatten = (items) => items.flatMap((item) => [item, ...flatten(item.children || [])]);
  const routes = flatten(navigations);
  for (const [role, expectedPath] of landingCases) {
    const user = { role: [role] };
    assert.equal(getHomePath(user), expectedPath);
    const route = routes.find((item) => item.path === expectedPath);
    assert.ok(route, `Landing route exists for ${role}`);
    assert.equal(hasRole(user, route.auth), true, `Landing route permits ${role}`);
  }
  assert.equal(getHomePath({ role: [] }), '/access-denied');
});

test('role checks support strings, arrays and routes without a role restriction', () => {
  const user = { role: [ROLES.HR_EMPLOYEE] };
  assert.equal(hasRole(user, ROLES.HR_EMPLOYEE), true);
  assert.equal(hasRole(user, [ROLES.ADMIN, ROLES.HR_EMPLOYEE]), true);
  assert.equal(hasRole(user, ROLES.ADMIN), false);
  assert.equal(hasRole(user, []), true);
  assert.equal(hasRole(user), true);
  assert.equal(hasRole(null), false);
});

test('login restores the requested path, query and fragment', () => {
  assert.equal(getLoginDestination({ pathname: '/staff/all', search: '?department=123', hash: '#open' }), '/staff/all?department=123#open');
});

test('login refuses a login loop and external redirect destinations', () => {
  const user = { role: [ROLES.HR_EMPLOYEE] };
  for (const pathname of ['/login', '//example.test', 'https://example.test', '/\\example.test', undefined]) {
    assert.equal(getLoginDestination({ pathname }, user), '/my-leave');
  }
});
