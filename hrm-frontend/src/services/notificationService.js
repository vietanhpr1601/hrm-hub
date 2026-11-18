import http from './HttpService';

// Inbox refreshes must not interrupt the employee's current screen.
const options = { skipGlobalLoading: true };
const data = (response) => {
  if (response.data?.status !== 200) throw new Error(response.data?.message || 'Không thể tải thông báo.');
  return response.data.data;
};
export const getNotifications = async (params) => data(await http.get('/notifications', { ...options, params }));
export const getUnreadCount = async () => data(await http.get('/notifications/unread-count', options));
export const readNotification = async (id) => data(await http.put(`/notifications/${encodeURIComponent(id)}/read`, null, options));
export const readAllNotifications = async () => data(await http.put('/notifications/read-all', null, options));
