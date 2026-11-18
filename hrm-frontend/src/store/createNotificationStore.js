import { createStore } from 'zustand/vanilla';
import { hasPermission } from '../utils/permissions.js';

export const notificationSessionIdentity = ({ user, accessToken }) => accessToken && user?.id && hasPermission(user, 'NOTIFICATION_VIEW')
  ? `${user.id}:${user.staffId || ''}` : null;

const initial = () => ({ identity: null, items: [], unreadCount: 0, totalElements: 0, totalPages: 0,
  pageIndex: 1, pageSize: 10, unreadOnly: false, loading: false, error: '', actionError: '', busyId: null });
const message = (error) => error.response?.data?.message || error.message || 'Không thể tải thông báo. Vui lòng thử lại.';

// Epochs also isolate permission revocation/regrant, even for the same account and employee.
export const createNotificationStore = (api) => {
  let epoch = 0;
  let listVersion = 0;
  let countVersion = 0;
  return createStore((set, get) => ({
    ...initial(),
    setSession: (session) => get().setIdentity(notificationSessionIdentity(session)),
    setIdentity: (identity) => {
      if (get().identity === identity) return;
      epoch += 1;
      listVersion += 1;
      countVersion += 1;
      set({ ...initial(), identity });
    },
    refreshCount: async () => {
      if (!get().identity) return;
      const session = epoch;
      const version = ++countVersion;
      try {
        const result = await api.getUnreadCount();
        if (session === epoch && version === countVersion) set({ unreadCount: Number(result?.unreadCount) || 0 });
      } catch { /* Background failures leave the last verified count; inbox offers retry. */ }
    },
    loadPage: async ({ pageIndex = get().pageIndex, unreadOnly = get().unreadOnly, background = false } = {}) => {
      if (!get().identity) return;
      const session = epoch;
      const version = ++listVersion;
      const changed = pageIndex !== get().pageIndex || unreadOnly !== get().unreadOnly;
      set({ pageIndex, unreadOnly, error: '', ...(changed ? { items: [] } : {}), ...(!background ? { loading: true } : {}) });
      try {
        const result = await api.getNotifications({ pageIndex, pageSize: get().pageSize, unreadOnly });
        if (session !== epoch || version !== listVersion) return;
        if (pageIndex > 1 && pageIndex > (result?.totalPages || 0)) {
          await get().loadPage({ pageIndex: Math.max(1, result?.totalPages || 1), unreadOnly });
          return;
        }
        set({ items: result?.content || [], totalElements: result?.totalElements || 0,
          totalPages: result?.totalPages || 0, loading: false });
      } catch (error) {
        if (session === epoch && version === listVersion) set({ error: message(error), loading: false });
      }
    },
    markRead: async (id) => {
      if (!get().identity || get().busyId) return false;
      const session = epoch;
      set({ busyId: id, actionError: '' });
      try {
        await api.readNotification(id);
        if (session !== epoch) return false;
        await Promise.all([get().loadPage({ background: true }), get().refreshCount()]);
        return session === epoch;
      } catch (error) {
        if (session === epoch) set({ actionError: message(error) });
        return false;
      } finally { if (session === epoch) set({ busyId: null }); }
    },
    markAllRead: async () => {
      if (!get().identity || get().busyId) return false;
      const session = epoch;
      set({ busyId: 'all', actionError: '' });
      try {
        await api.readAllNotifications();
        if (session !== epoch) return false;
        await Promise.all([get().loadPage({ pageIndex: 1, background: true }), get().refreshCount()]);
        return session === epoch;
      } catch (error) {
        if (session === epoch) set({ actionError: message(error) });
        return false;
      } finally { if (session === epoch) set({ busyId: null }); }
    },
  }));
};
