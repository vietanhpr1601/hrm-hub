import { ROLES } from '../constants/roles.js';
import { getRoleNames, hasPermission } from './permissions.js';

export const hasRole = (user, roles) => {
  if (!user || user.active === false || user.isDeleted === true) return false;
  if (!roles || (Array.isArray(roles) && roles.length === 0)) return true;
  const requiredRoles = Array.isArray(roles) ? roles : [roles];
  return requiredRoles.some((role) => getRoleNames(user).includes(role));
};

export const getHomePath = (user) => {
  if (!user || user.active === false || user.isDeleted === true) return '/access-denied';
  if (hasRole(user, ROLES.ADMIN)) return '/dashboard';
  if (Object.hasOwn(user, 'permissions')) {
    const destinations = [
      ['DASHBOARD_VIEW', '/dashboard'], ['LEAVE_SELF', '/my-leave'],
      ['STAFF_VIEW', '/staff/all'], ['RECRUITMENT_VIEW', '/recruitments'],
      ['PAYROLL_VIEW', '/salary/payrolls'], ['ATTENDANCE_VIEW', '/time-sheet-summary'],
      ['ATTENDANCE_SELF', '/time-sheet-detail'], ['ATTENDANCE_APPROVE', '/check-inout-result'],
      ['LEAVE_VIEW', '/leave-requests'], ['LEAVE_APPROVE', '/leave-requests'],
      ['PAYROLL_SELF', '/salary/salary-staff-payslip'], ['DEPARTMENT_VIEW', '/department'],
      ['POSITION_VIEW', '/category/staff/position'], ['SALARY_CONFIG_VIEW', '/salary/salary-item'],
      ['PERIOD_VIEW', '/salary/salary-period'],
      ['NOTIFICATION_VIEW', '/announcement/announcement'],
    ];
    return destinations.find(([code]) => hasPermission(user, code))?.[1] || '/access-denied';
  }
  if (hasRole(user, [ROLES.HR_MANAGER, ROLES.HR_EMPLOYEE])) return '/my-leave';
  if (hasRole(user, ROLES.HR_RECRUITMENT)) return '/recruitments';
  if (hasRole(user, ROLES.HR_COMPENSATION_BENEFIT)) return '/salary/payrolls';
  if (hasRole(user, ROLES.HR_TIMEKEEPING_MANAGER)) return '/time-sheet-summary';
  if (hasPermission(user, 'NOTIFICATION_VIEW')) return '/announcement/announcement';
  return '/access-denied';
};

export const getAuthRedirect = ({ user, accessToken, roles, permissions }) => {
  if (!user || !accessToken) return '/login';
  return (permissions ? hasPermission(user, permissions, roles) : hasRole(user, roles)) ? null : '/access-denied';
};

export const getLoginDestination = (from, user) => {
  const pathname = from?.pathname;
  if (typeof pathname !== 'string' || !pathname.startsWith('/') || pathname.startsWith('//') || pathname.includes('\\') || pathname === '/login') {
    return getHomePath(user);
  }
  return `${pathname}${from.search || ''}${from.hash || ''}`;
};
