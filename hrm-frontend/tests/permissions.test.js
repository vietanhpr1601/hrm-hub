import test from 'node:test';
import assert from 'node:assert/strict';
import { hasPermission, isAdmin, filterNavigations, inheritedRolePermissions, previewPermissions, initialUserFormRoles, permissionModules, canDeletePermissionRole, removeRetiredOverrides, removeRetiredPermissions } from '../src/utils/permissions.js';
import { getAuthRedirect, getHomePath } from '../src/utils/auth.js';
import { ROLES } from '../src/constants/roles.js';
import { navigations } from '../src/navigationConfig.js';

const flatten = (items) => items.flatMap((item) => [item, ...flatten(item.children || [])]);

test('Excel import remains Admin-only even with old role grants or explicit account grants', () => {
  for (const user of [
    { role: ['HR_MANAGER'] },
    { role: ['HR_EMPLOYEE'], permissions: ['STAFF_IMPORT'] },
    { role: ['CUSTOM'], permissions: ['STAFF_IMPORT', 'STAFF_EXPORT'] },
  ]) assert.equal(hasPermission(user, 'STAFF_IMPORT'), false);
  assert.equal(hasPermission({ role: ['ROLE_ADMIN'], permissions: [] }, 'STAFF_IMPORT'), true);
  assert.equal(hasPermission({ role: ['ROLE_USER'], permissions: ['STAFF_EXPORT'] }, 'STAFF_EXPORT'), true);
  assert.deepEqual(previewPermissions(['STAFF_IMPORT'], { STAFF_IMPORT: 'ALLOW' }, []), []);
  assert.deepEqual(permissionModules([{ code: 'STAFF_IMPORT', label: 'Nhập Excel', module: 'Nhân viên', adminOnly: true }]), {});
});

test('delete-role control protects only Admin and allows unused User, legacy or custom roles', () => {
  const role = { id: 'custom', name: 'HR_CUSTOM', system: false };
  assert.equal(canDeletePermissionRole(role, []), true);
  assert.equal(canDeletePermissionRole(role, [{ id: 'account', roles: [role] }]), false);
  assert.equal(canDeletePermissionRole(role, [{ id: 'locked-account', active: false, roles: [role] }]), false);
  assert.equal(canDeletePermissionRole(role, [{ id: 'deleted-account', isDeleted: true, roles: [role] }]), true);
  assert.equal(canDeletePermissionRole({ ...role, system: true }, []), true);
  assert.equal(canDeletePermissionRole({ ...role, name: 'ROLE_ADMIN' }, []), false);
  const basic = { id: 'user-role', name: 'ROLE_USER', system: true };
  assert.equal(canDeletePermissionRole(basic, []), true);
  assert.equal(canDeletePermissionRole(basic, [{ id: 'account', roles: [basic] }]), false);
  assert.equal(canDeletePermissionRole(basic, [{ id: 'locked', active: false, roles: [basic] }]), false);
  assert.equal(canDeletePermissionRole(basic, [{ id: 'deleted', isDeleted: true, roles: [basic] }]), true);
  assert.equal(canDeletePermissionRole({ ...role, name: 'HR_MANAGER' }, []), true);
  assert.equal(canDeletePermissionRole(null), false);
});

test('new accounts default to Người dùng while editing preserves legacy roles and empty memberships', () => {
  const roles = [{ id: 'admin', name: 'ROLE_ADMIN' }, { id: 'user', name: 'ROLE_USER' }];
  const legacy = [{ id: 'old', name: 'HR_MANAGER' }, { id: 'custom', name: 'CUSTOM_PAYROLL' }];
  assert.deepEqual(initialUserFormRoles(null, roles), [roles[1]]);
  assert.deepEqual(initialUserFormRoles({ id: 'existing', roles: legacy }, roles), legacy);
  assert.deepEqual(initialUserFormRoles({ id: 'existing', roles: [] }, roles), []);
  assert.deepEqual(initialUserFormRoles(null, []), []);
  assert.deepEqual(initialUserFormRoles(null, [{ ...roles[1], isDeleted: true }]), []);
  assert.deepEqual(initialUserFormRoles(null, [roles[0], ...legacy]), []);
});

test('Người dùng has only personal screens under default grants', () => {
  const user = { role: ['ROLE_USER'], permissions: ['ATTENDANCE_SELF', 'LEAVE_SELF', 'PAYROLL_SELF'] };
  const paths = flatten(filterNavigations(navigations, user)).map((item) => item.path);
  assert.ok(paths.includes('/time-sheet-detail'));
  assert.ok(paths.includes('/my-leave'));
  assert.ok(paths.includes('/salary/salary-staff-payslip'));
  assert.ok(!paths.includes('/staff/all'));
  assert.ok(!paths.includes('/salary/payrolls'));
  assert.ok(!paths.includes('/administration/permissions'));
  assert.equal(getHomePath(user), '/my-leave');
});

test('permission search supports Vietnamese without accents, module names and permission codes', () => {
  const catalog = [
    { code: 'STAFF_VIEW', label: 'Xem nhân viên', module: 'Nhân viên' },
    { code: 'STAFF_CREATE', label: 'Thêm nhân viên', module: 'Nhân viên' },
    { code: 'PAYROLL_SELF', label: 'Xem phiếu lương', module: 'Bảng lương' },
  ];
  assert.deepEqual(permissionModules(catalog, 'xem nhan vien'), { 'Nhân viên': [catalog[0]] });
  assert.deepEqual(permissionModules(catalog, 'bang luong'), { 'Bảng lương': [catalog[2]] });
  assert.deepEqual(permissionModules(catalog, 'staff_create'), { 'Nhân viên': [catalog[1]] });
  assert.deepEqual(permissionModules(catalog, 'khong ton tai'), {});
  assert.equal(Object.keys(permissionModules(catalog)).length, 2);
});

test('explicit server permissions override legacy role defaults, including an empty set', () => {
  const user = { role: ['HR_MANAGER'], permissions: [] };
  assert.equal(hasPermission(user, 'STAFF_VIEW'), false);
  assert.equal(hasPermission({ ...user, permissions: ['STAFF_VIEW'] }, 'STAFF_VIEW'), true);
  assert.equal(hasPermission({ ...user, permissions: ['STAFF_VIEW'] }, 'STAFF_DELETE'), false);
  assert.equal(hasPermission({ role: ['HR_MANAGER'] }, 'STAFF_VIEW'), true);
  assert.equal(hasPermission({ ...user, permissions: null }, 'STAFF_VIEW'), false);
});

test('custom role grants expose the right menu without legacy role requirements', () => {
  const user = { role: ['CUSTOM_HR'], permissions: ['STAFF_VIEW', 'ATTENDANCE_VIEW'] };
  const routes = flatten(filterNavigations(navigations, user)).map((item) => item.path);
  assert.ok(routes.includes('/staff/all'));
  assert.ok(routes.includes('/time-sheet-summary'));
  assert.ok(!routes.includes('/salary/payrolls'));
  assert.ok(!routes.includes('/administration/permissions'));
  assert.equal(getHomePath(user), '/staff/all');
});

test('revoked permissions remove parent menu and avoid a forbidden landing page', () => {
  const user = { role: ['HR_MANAGER'], permissions: [] };
  const filtered = filterNavigations(navigations, user);
  assert.ok(!flatten(filtered).some((item) => item.path === '/staff/all'));
  assert.ok(!filtered.some((item) => item.name === 'Nhân viên' && item.children.some((child) => child.path === '/staff/all')));
  assert.equal(getHomePath(user), '/access-denied');
});

test('notification menu and direct route require explicit permission', () => {
  const cases = [['NOTIFICATION_VIEW', '/announcement/announcement']];
  const routes = flatten(navigations);
  for (const [permission, path] of cases) {
    const route = routes.find(item => item.path === path);
    assert.equal(route.permissions, permission);
    for (const granted of [true, false]) {
      const user = { role: ['CUSTOM'], permissions: granted ? [permission] : [] };
      const paths = flatten(filterNavigations(navigations, user)).map(item => item.path);
      assert.equal(paths.includes(path), granted);
      assert.equal(getAuthRedirect({ user, accessToken: 'session', roles: route.auth, permissions: route.permissions }), granted ? null : '/access-denied');
      assert.equal(getHomePath(user), granted ? path : '/access-denied');
      for (const [otherPermission, otherPath] of cases.filter(([code]) => code !== permission)) {
        assert.equal(hasPermission(user, otherPermission), false);
        assert.equal(paths.includes(otherPath), false);
      }
    }
  }
  const user = { role: ['ROLE_USER'], permissions: [] };
  assert.equal(getHomePath(user), '/access-denied');
  assert.equal(getAuthRedirect({ user, accessToken: 'session' }), null, 'the access-denied route itself remains reachable without a redirect loop');
  assert.ok(!filterNavigations(navigations, user).some(item => item.name === 'Trang chủ'));
  assert.equal(getAuthRedirect({ user, accessToken: null, permissions: 'NOTIFICATION_VIEW' }), '/login');
});

test('removed bulletin page is absent from navigation and landing destinations', () => {
  assert.ok(!flatten(navigations).some(item => item.path === '/profile-page/home'));
  assert.equal(getHomePath({ role: ['CUSTOM'], permissions: ['BULLETIN_VIEW'] }), '/access-denied');
  assert.equal(getHomePath({ role: ['CUSTOM'], permissions: ['BULLETIN_VIEW', 'NOTIFICATION_VIEW'] }), '/announcement/announcement');
});

test('new homepage grants retain legacy defaults while authoritative empty permissions deny access', () => {
  for (const code of ['NOTIFICATION_VIEW']) {
    for (const role of Object.values(ROLES)) {
      assert.equal(hasPermission({ role: [role] }, code), true);
      assert.equal(hasPermission({ role: [role], permissions: [] }, code), role === ROLES.ADMIN);
    }
    assert.equal(hasPermission({ role: ['CUSTOM'] }, code), false);
    assert.equal(hasPermission({ role: ['ROLE_USER'], permissions: null }, code), false);
    assert.equal(hasPermission({ role: ['ROLE_ADMIN'], active: false }, code), false);
    assert.equal(hasPermission({ role: ['ROLE_ADMIN'], isDeleted: true }, code), false);
  }
});

test('new grants can be inherited, explicitly denied or allowed and are searchable by module', () => {
  const catalog = [
    { code: 'BULLETIN_VIEW', label: 'Xem bảng tin', module: 'Bảng tin' },
    { code: 'NOTIFICATION_VIEW', label: 'Xem thông báo của mình', module: 'Thông báo' },
  ];
  assert.deepEqual(permissionModules(catalog, 'bang tin'), {});
  assert.deepEqual(permissionModules(catalog, 'thong bao'), { 'Thông báo': [catalog[1]] });
  assert.deepEqual(previewPermissions(['BULLETIN_VIEW', 'NOTIFICATION_VIEW'], { NOTIFICATION_VIEW: 'DENY' }, catalog), []);
  assert.deepEqual(previewPermissions([], { NOTIFICATION_VIEW: 'ALLOW' }, catalog), ['NOTIFICATION_VIEW']);
  assert.deepEqual(previewPermissions([], {}, catalog), [], 'opening the editor does not add newly introduced permissions');
  assert.deepEqual(previewPermissions([], { NOTIFICATION_VIEW: 'DENY' }, catalog, true), ['NOTIFICATION_VIEW']);
});

test('retired bulletin grants disappear from effective permissions and role/account editor payloads', () => {
  for (const user of [
    { role: ['ROLE_ADMIN'] }, { role: ['ROLE_USER'] },
    { role: ['CUSTOM'], permissions: ['BULLETIN_VIEW'] },
  ]) assert.equal(hasPermission(user, 'BULLETIN_VIEW'), false);
  assert.equal(hasPermission({ role: ['CUSTOM'], permissions: ['NOTIFICATION_VIEW'] }, ['BULLETIN_VIEW', 'NOTIFICATION_VIEW']), true);
  assert.deepEqual(removeRetiredPermissions(['BULLETIN_VIEW', 'LEAVE_SELF', 'NOTIFICATION_VIEW']), ['LEAVE_SELF', 'NOTIFICATION_VIEW']);
  assert.deepEqual(removeRetiredOverrides({ BULLETIN_VIEW: 'ALLOW', NOTIFICATION_VIEW: 'DENY', LEAVE_SELF: 'ALLOW' }), { NOTIFICATION_VIEW: 'DENY', LEAVE_SELF: 'ALLOW' });
  assert.deepEqual(inheritedRolePermissions({ roles: [{ id: 'old', permissions: ['BULLETIN_VIEW', 'NOTIFICATION_VIEW'] }] }, ['old']), ['NOTIFICATION_VIEW']);
});

test('active Admin keeps all permissions; deleted Admin role and locked users do not', () => {
  assert.equal(hasPermission({ role: ['ROLE_ADMIN'], permissions: [] }, 'STAFF_DELETE'), true);
  const deletedRole = { role: ['ROLE_ADMIN'], roles: [{ name: 'ROLE_ADMIN', isDeleted: true }], permissions: [] };
  assert.equal(isAdmin(deletedRole), false);
  assert.equal(hasPermission(deletedRole, 'STAFF_DELETE'), false);
  assert.equal(hasPermission({ role: ['ROLE_ADMIN'], active: false, permissions: ['STAFF_VIEW'] }, 'STAFF_VIEW'), false);
  assert.equal(getHomePath({ role: ['ROLE_ADMIN'], active: false }), '/access-denied');
});

test('administration stays Admin-only even for a user with all catalog business permissions', () => {
  const user = { role: ['CUSTOM'], permissions: ['STAFF_VIEW', 'STAFF_CREATE', 'DASHBOARD_VIEW', 'PAYROLL_VIEW'] };
  assert.ok(!flatten(filterNavigations(navigations, user)).some((item) => item.path?.startsWith('/administration/')));
  assert.ok(flatten(filterNavigations(navigations, { role: ['ROLE_ADMIN'], permissions: [] })).some((item) => item.path === '/administration/permissions'));
  assert.ok(flatten(filterNavigations(navigations, { role: ['ROLE_ADMIN'], permissions: [] })).some((item) => item.path === '/administration/roles'));
});

test('account DENY defeats inherited grants, ALLOW adds a permission and inheritance restores group choice', () => {
  const groups = ['STAFF_VIEW', 'STAFF_EXPORT'];
  const overrides = { STAFF_EXPORT: 'DENY', STAFF_CREATE: 'ALLOW' };
  const result = previewPermissions(groups, overrides, []);
  assert.deepEqual(result.sort(), ['STAFF_CREATE', 'STAFF_VIEW']);
  assert.deepEqual(groups, ['STAFF_VIEW', 'STAFF_EXPORT']);
  assert.deepEqual(overrides, { STAFF_EXPORT: 'DENY', STAFF_CREATE: 'ALLOW' });
  assert.deepEqual(previewPermissions(groups, {}, []).sort(), ['STAFF_EXPORT', 'STAFF_VIEW']);
});

test('role inheritance ignores old department grants and unions only assigned active roles', () => {
  const groups = {
    roles: [
      { id: 'view', permissions: ['STAFF_VIEW'] },
      { id: 'export', permissions: ['STAFF_VIEW', 'STAFF_EXPORT'] },
      { id: 'unassigned', permissions: ['STAFF_DELETE'] },
      { id: 'deleted', isDeleted: true, permissions: ['PAYROLL_VIEW'] },
    ],
    departments: [{ id: 'old-department', permissions: ['STAFF_DELETE', 'PAYROLL_VIEW'] }],
  };
  const inherited = inheritedRolePermissions(groups, ['view', 'export', 'deleted']);
  assert.deepEqual(inherited.sort(), ['STAFF_EXPORT', 'STAFF_VIEW']);
  assert.deepEqual(previewPermissions(inherited, { STAFF_EXPORT: 'DENY', STAFF_CREATE: 'ALLOW' }, []).sort(), ['STAFF_CREATE', 'STAFF_VIEW']);
  assert.deepEqual(inheritedRolePermissions(groups, []), []);
  assert.deepEqual(inheritedRolePermissions({ departments: groups.departments }, ['old-department']), []);
});

test('Admin account preview ignores DENY and returns the complete catalog', () => {
  const catalog = [{ code: 'STAFF_VIEW' }, { code: 'STAFF_DELETE' }];
  assert.deepEqual(previewPermissions([], { STAFF_DELETE: 'DENY' }, catalog, true), ['STAFF_VIEW', 'STAFF_DELETE']);
});

test('navigation filtering does not mutate shared menu configuration', () => {
  const before = JSON.stringify(navigations);
  filterNavigations(navigations, { permissions: ['STAFF_VIEW'] });
  assert.equal(JSON.stringify(navigations), before);
});

test('any-of permission guard permits only an explicitly granted option', () => {
  const user = { permissions: ['LEAVE_APPROVE'] };
  assert.equal(hasPermission(user, ['LEAVE_VIEW', 'LEAVE_APPROVE']), true);
  assert.equal(hasPermission(user, ['LEAVE_MANAGE', 'LEAVE_SELF']), false);
  assert.equal(hasPermission(null, 'LEAVE_APPROVE'), false);
});
