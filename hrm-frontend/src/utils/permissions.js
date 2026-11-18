import { ROLES } from '../constants/roles.js';

const { ADMIN, USER, HR_MANAGER, HR_EMPLOYEE, HR_RECRUITMENT, HR_COMPENSATION_BENEFIT, HR_TIMEKEEPING_MANAGER } = ROLES;
const staffRoles = [ADMIN, HR_MANAGER, HR_EMPLOYEE];
const salaryRoles = [ADMIN, HR_COMPENSATION_BENEFIT];
const attendanceRoles = [ADMIN, HR_MANAGER, HR_TIMEKEEPING_MANAGER];
const retiredPermissions = new Set(['BULLETIN_VIEW']);

export const removeRetiredPermissions = (permissions = []) => permissions.filter(code => !retiredPermissions.has(code));
export const removeRetiredOverrides = (overrides = {}) => Object.fromEntries(
  Object.entries(overrides).filter(([code]) => !retiredPermissions.has(code)),
);

// Only used for legacy sessions that have not yet received permissions from the server.
const legacyRoles = {
  DASHBOARD_VIEW: [ADMIN],
  NOTIFICATION_VIEW: Object.values(ROLES),
  STAFF_VIEW: staffRoles, STAFF_CREATE: staffRoles, STAFF_UPDATE: staffRoles,
  STAFF_DELETE: staffRoles, STAFF_IMPORT: [ADMIN], STAFF_EXPORT: staffRoles,
  DEPARTMENT_VIEW: [ADMIN], DEPARTMENT_MANAGE: [ADMIN],
  POSITION_VIEW: [ADMIN], POSITION_MANAGE: [ADMIN],
  RECRUITMENT_VIEW: [ADMIN, HR_MANAGER, HR_RECRUITMENT], RECRUITMENT_MANAGE: [ADMIN, HR_MANAGER, HR_RECRUITMENT],
  CANDIDATE_VIEW: [ADMIN, HR_MANAGER, HR_RECRUITMENT], CANDIDATE_MANAGE: [ADMIN, HR_MANAGER, HR_RECRUITMENT],
  ATTENDANCE_VIEW: attendanceRoles, ATTENDANCE_MANAGE: attendanceRoles,
  ATTENDANCE_APPROVE: [ADMIN, HR_MANAGER], ATTENDANCE_SELF: [...staffRoles, USER],
  LEAVE_VIEW: [ADMIN, HR_MANAGER], LEAVE_MANAGE: [ADMIN, HR_MANAGER],
  LEAVE_APPROVE: [ADMIN, HR_MANAGER], LEAVE_SELF: Object.values(ROLES),
  PAYROLL_VIEW: salaryRoles, PAYROLL_MANAGE: salaryRoles, PAYROLL_CONFIRM: salaryRoles,
  PAYROLL_PAY: salaryRoles, PAYROLL_SELF: [ADMIN, HR_EMPLOYEE, USER],
  SALARY_CONFIG_VIEW: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT], SALARY_CONFIG_MANAGE: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT],
  PERIOD_VIEW: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT], PERIOD_MANAGE: [ADMIN, HR_MANAGER, HR_COMPENSATION_BENEFIT],
  SHIFT_VIEW: attendanceRoles, SHIFT_MANAGE: attendanceRoles,
  AI_CHAT: Object.values(ROLES).filter((role) => role !== USER),
  PROJECT_VIEW: [ADMIN, HR_MANAGER], PROJECT_MANAGE: [ADMIN, HR_MANAGER],
};

export const getRoleNames = (user) => Array.isArray(user?.roles)
  ? user.roles.filter((role) => role && role.isDeleted !== true).map((role) => role.name)
  : user?.role || [];

export const isAdmin = (user) => !!user && user.active !== false && user.isDeleted !== true && getRoleNames(user).includes(ADMIN);

export const hasPermission = (user, permissions, fallbackRoles) => {
  if (!user || user.active === false || user.isDeleted === true) return false;
  if (!permissions || (Array.isArray(permissions) && !permissions.length)) return true;
  const requested = removeRetiredPermissions(Array.isArray(permissions) ? permissions : [permissions]);
  if (!requested.length) return false;
  if (isAdmin(user)) return true;
  const required = requested.filter(code => code !== 'STAFF_IMPORT');
  if (Object.hasOwn(user, 'permissions')) return Array.isArray(user.permissions) && required.some((code) => user.permissions.includes(code));
  const roles = getRoleNames(user);
  return required.some((code) => (fallbackRoles || legacyRoles[code] || []).some((role) => roles.includes(role)));
};

export const canAccessNavigation = (user, item) => {
  if (item.permissions) return hasPermission(user, item.permissions, item.auth);
  return !!user && user.active !== false && user.isDeleted !== true && (!item.auth || item.auth.some((role) => getRoleNames(user).includes(role)));
};

export const filterNavigations = (items, user) => items.flatMap((item) => {
  if (item.children) {
    const children = filterNavigations(item.children, user);
    return children.length ? [{ ...item, children }] : [];
  }
  return canAccessNavigation(user, item) ? [item] : [];
});

export const inheritedRolePermissions = (groups, roleIds) => [...new Set(
  (groups?.roles || []).filter((role) => role.isDeleted !== true && roleIds.includes(role.id))
    .flatMap((role) => removeRetiredPermissions(role.permissions || [])),
)];

export const roleLabel = (role) => role?.name === ADMIN ? 'Admin'
  : role?.name === USER ? 'Người dùng' : role?.description || role?.name || '';

export const initialUserFormRoles = (user, roles) => {
  if (user?.id) return user.roles || [];
  const role = roles.find((item) => item.name === USER && item.isDeleted !== true);
  return role ? [role] : [];
};

export const canDeletePermissionRole = (role, users = []) => !!role?.id
  && role.name !== ADMIN
  && !users.some((user) => user.isDeleted !== true && (user.roles || []).some((assigned) => assigned.id === role.id && assigned.isDeleted !== true));

export const permissionModules = (catalog, search = '') => {
  const normalize = (value) => String(value).normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
  const keyword = normalize(search.trim());
  return catalog.filter((item) => !item.adminOnly && item.code !== 'STAFF_IMPORT' && !retiredPermissions.has(item.code))
    .filter((item) => !keyword || normalize(`${item.label} ${item.module} ${item.code}`).includes(keyword))
    .reduce((modules, item) => {
      (modules[item.module] ||= []).push(item);
      return modules;
    }, {});
};

export const previewPermissions = (groupPermissions, overrides, catalog, admin = false) => {
  if (admin) return removeRetiredPermissions(catalog.map((item) => item.code));
  const result = new Set(removeRetiredPermissions(groupPermissions));
  for (const [code, decision] of Object.entries(removeRetiredOverrides(overrides || {}))) {
    if (decision === 'ALLOW') result.add(code);
    if (decision === 'DENY') result.delete(code);
  }
  result.delete('STAFF_IMPORT');
  return [...result];
};
