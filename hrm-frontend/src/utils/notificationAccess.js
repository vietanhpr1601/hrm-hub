import { hasPermission } from './permissions.js';

export const canOpenNotification = (user, item) => hasPermission(user, 'NOTIFICATION_VIEW')
  && !!user?.staffId && !!item?.referenceId
  && (item.type === 'ATTENDANCE_APPROVED'
    ? !!item.referenceDate && hasPermission(user, 'ATTENDANCE_SELF')
    : item.type === 'LEAVE_APPROVED' && hasPermission(user, 'LEAVE_SELF'));
