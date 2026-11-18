import React from 'react';
import useAuthStore from '@/store/useAuthStore';
import { hasPermission } from '../../utils/permissions.js';

/**
 * PermissionGuard - Component kiểm tra quyền hiển thị children
 * @param {Array|String} roles - Các vai trò được phép xem chức năng.
 * @param {ReactNode} children - Component con hiển thị nếu có quyền.
 * @param {ReactNode} fallback - Giao diện hiển thị thay thế nếu không có quyền (mặc định là null).
 */
const PermissionGuard = ({ roles, permissions, children, fallback = null }) => {
  const allowed = useAuthStore((state) => permissions ? hasPermission(state.user, permissions, roles) : state.hasRole(roles));

  if (allowed) {
    return <>{children}</>;
  }

  return fallback;
};

export default PermissionGuard;
