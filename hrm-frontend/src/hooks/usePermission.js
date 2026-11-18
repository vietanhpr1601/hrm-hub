import useAuthStore from '../store/useAuthStore';
import { hasPermission } from '../utils/permissions.js';

export default function usePermission(permission) {
  return useAuthStore((state) => hasPermission(state.user, permission));
}

export function usePermissions() {
  const user = useAuthStore((state) => state.user);
  return (permission) => hasPermission(user, permission);
}
