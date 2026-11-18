import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Box, Button, Chip, CircularProgress, Paper, Tab, Tabs, TablePagination, Typography } from '@mui/material';
import { DoneAll, EventAvailable, NotificationsNone, Refresh, TaskAlt } from '@mui/icons-material';
import dayjs from 'dayjs';
import useNotificationStore from '../../store/useNotificationStore';
import useAuthStore from '../../store/useAuthStore';
import { hasPermission } from '../../utils/permissions.js';
import { canOpenNotification } from '../../utils/notificationAccess.js';
import { notificationSessionIdentity } from '../../store/createNotificationStore.js';
import { getLeaveRequestById } from '../../services/leaveService';
import Popup from '../../components/ui/Popup';

const formatDate = value => value ? dayjs(value).format('DD/MM/YYYY') : '—';
const leaveTypes = { ANNUAL: 'Phép năm', UNPAID: 'Nghỉ không lương', MAKEUP: 'Nghỉ có làm bù' };
const statuses = { APPROVED: 'Đã duyệt', PENDING: 'Chờ duyệt', REJECTED: 'Từ chối' };

function LeaveDetail({ id, onClose, staffId }) {
  const [state, setState] = useState({ loading: true, record: null, error: '' });
  useEffect(() => {
    let active = true;
    setState({ loading: true, record: null, error: '' });
    getLeaveRequestById(id).then(response => {
      if (!active) return;
      const record = response?.data;
      if (!staffId || record?.requestStaffId !== staffId) throw new Error('Đơn nghỉ này không thuộc hồ sơ nhân viên đang liên kết với tài khoản.');
      setState({ loading: false, record, error: '' });
    }).catch(error => {
      if (active) setState({ loading: false, record: null, error: error.response?.data?.message || error.message || 'Không tải được đơn nghỉ phép.' });
    });
    return () => { active = false; };
  }, [id, staffId]);
  const record = state.record;
  return <Popup open title="Chi tiết đơn nghỉ phép" size="sm" onClosePopup={onClose} action={<Button onClick={onClose}>Đóng</Button>}>
    <Box sx={{ py: 2 }}>
      {state.loading ? <CircularProgress size={24} aria-label="Đang tải đơn nghỉ phép" /> : state.error ? <Alert severity="error">{state.error}</Alert> : record && <Box className="space-y-3">
        <Box className="flex justify-between items-center gap-2"><Typography variant="h6">{leaveTypes[record.leaveType] || record.leaveType}</Typography><Chip size="small" color={record.approvalStatus === 'APPROVED' ? 'success' : 'default'} label={statuses[record.approvalStatus] || record.approvalStatus} /></Box>
        <Typography>{formatDate(record.fromDate)} – {formatDate(record.toDate)} · {record.totalDays} ngày</Typography>
        {record.halfDayLeave && <Typography variant="body2">Nghỉ nửa ngày{record.shiftWorkName ? ` · ${record.shiftWorkName}` : ''}</Typography>}
        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>Lý do: {record.requestReason || '—'}</Typography>
        {!!record.makeupDates?.length && <Typography variant="body2">Ngày làm bù: {record.makeupDates.map(formatDate).join(', ')}</Typography>}
        {record.approvalStaffName && <Typography variant="body2">Người duyệt: {record.approvalStaffName}</Typography>}
        {record.rejectReason && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>Ý kiến phản hồi: {record.rejectReason}</Typography>}
      </Box>}
    </Box>
  </Popup>;
}

function Inbox() {
  const user = useAuthStore(state => state.user);
  const state = useNotificationStore(value => value);
  const { identity, items, unreadCount, loading, error, actionError, busyId, unreadOnly,
    pageIndex, pageSize, totalElements, loadPage, refreshCount, markRead, markAllRead } = state;
  const navigate = useNavigate();
  const [leaveId, setLeaveId] = useState(null);
  useEffect(() => {
    if (!identity) return undefined;
    loadPage();
    refreshCount();
    const refresh = () => {
      if (document.visibilityState !== 'hidden') loadPage({ background: true });
    };
    const timer = window.setInterval(refresh, 30000);
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refresh);
    };
  }, [identity, loadPage, refreshCount]);

  const openDetail = async item => {
    if (!canOpenNotification(useAuthStore.getState().user, item)) return;
    if (!item.readAt && !(await markRead(item.id))) return;
    const session = useAuthStore.getState();
    if (identity !== notificationSessionIdentity(session) || !canOpenNotification(session.user, item)) return;
    if (item.type === 'ATTENDANCE_APPROVED') {
      navigate(`/time-sheet-detail?${new URLSearchParams({ date: item.referenceDate, timesheetId: item.referenceId })}`);
    } else if (item.type === 'LEAVE_APPROVED') setLeaveId(item.referenceId);
  };

  return <Paper elevation={0} sx={{ border: 1, borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
    <Box sx={{ p: { xs: 2, sm: 3 } }} className="flex flex-wrap items-center justify-between gap-3">
      <Box><Typography component="h1" variant="h5" fontWeight={700}>Thông báo</Typography><Typography variant="body2" color="text.secondary">{unreadCount ? `${unreadCount} thông báo chưa đọc` : 'Kết quả phê duyệt dành cho bạn'}</Typography></Box>
      <Box className="flex flex-wrap gap-2">
        <Button startIcon={<Refresh />} aria-label="Tải lại thông báo" onClick={() => { loadPage(); refreshCount(); }} disabled={loading || !!busyId}>Tải lại</Button>
        <Button variant="outlined" startIcon={<DoneAll />} aria-label="Đánh dấu tất cả đã đọc" onClick={markAllRead} disabled={!unreadCount || !!busyId || loading}>Đánh dấu tất cả đã đọc</Button>
      </Box>
    </Box>
    <Tabs value={unreadOnly ? 'unread' : 'all'} onChange={(_, value) => loadPage({ pageIndex: 1, unreadOnly: value === 'unread' })} aria-label="Lọc thông báo" sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}>
      <Tab value="all" label="Tất cả" disabled={!!busyId} /><Tab value="unread" label="Chưa đọc" disabled={!!busyId} />
    </Tabs>
    {error && <Alert severity="error" sx={{ m: 2 }} action={<Button onClick={() => loadPage()} color="inherit">Thử lại</Button>}>{error}</Alert>}
    {actionError && <Alert severity="error" sx={{ m: 2 }}>{actionError}</Alert>}
    {loading ? <Box sx={{ p: 5, textAlign: 'center' }}><CircularProgress size={28} aria-label="Đang tải thông báo" /></Box> : !items.length ? !error && <Box sx={{ p: 6, textAlign: 'center', color: 'text.secondary' }}>
      <NotificationsNone sx={{ fontSize: 42, mb: 1 }} /><Typography>{unreadOnly ? 'Bạn đã đọc hết thông báo.' : 'Chưa có thông báo.'}</Typography><Typography variant="body2" sx={{ mt: 1 }}>Khi chấm công hoặc đơn nghỉ của bạn được phê duyệt, kết quả sẽ xuất hiện tại đây.</Typography>
    </Box> : <Box component="ul" sx={{ listStyle: 'none', p: 0, m: 0 }}>
      {items.map(item => {
        const isAttendance = item.type === 'ATTENDANCE_APPROVED';
        const canOpen = canOpenNotification(user, item);
        return <Box component="li" key={item.id} data-notification-id={item.id} sx={{ p: { xs: 2, sm: 3 }, borderBottom: 1, borderColor: 'divider', bgcolor: item.readAt ? 'background.paper' : 'action.hover', display: 'flex', gap: 2 }}>
          <Box sx={{ color: 'success.main', mt: 0.25 }}>{isAttendance ? <TaskAlt /> : <EventAvailable />}</Box>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Box className="flex flex-wrap items-center justify-between gap-2"><Typography component="h2" variant="subtitle1" fontWeight={item.readAt ? 500 : 700}>{item.title}</Typography><Chip label={item.readAt ? 'Đã đọc' : 'Chưa đọc'} size="small" color={item.readAt ? 'default' : 'primary'} variant="outlined" /></Box>
            <Typography variant="body2" sx={{ my: 1, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{item.message}</Typography>
            <Box className="flex flex-wrap justify-between items-center gap-2"><Typography component="time" dateTime={item.createdAt} variant="caption" color="text.secondary">{item.createdAt ? dayjs(item.createdAt).format('HH:mm · DD/MM/YYYY') : ''}</Typography>
              <Box className="flex gap-2">
                {!item.readAt && <Button size="small" aria-label={`Đánh dấu đã đọc: ${item.title}`} disabled={!!busyId} onClick={() => markRead(item.id)}>Đánh dấu đã đọc</Button>}
                {canOpen && <Button size="small" aria-label={`Xem chi tiết: ${item.title}`} disabled={!!busyId} onClick={() => openDetail(item)}>Xem chi tiết</Button>}
              </Box>
            </Box>
          </Box>
        </Box>;
      })}
    </Box>}
    <TablePagination component="div" count={totalElements} page={pageIndex - 1} rowsPerPage={pageSize} rowsPerPageOptions={[10]} onPageChange={(_, page) => loadPage({ pageIndex: page + 1 })} labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`} backIconButtonProps={{ 'aria-label': 'Trang thông báo trước', disabled: loading || !!busyId || pageIndex <= 1 }} nextIconButtonProps={{ 'aria-label': 'Trang thông báo sau', disabled: loading || !!busyId || pageIndex * pageSize >= totalElements }} />
    {leaveId && hasPermission(user, 'LEAVE_SELF') && <LeaveDetail key={`${identity}:${leaveId}`} id={leaveId} staffId={user?.staffId} onClose={() => setLeaveId(null)} />}
  </Paper>;
}

export default function NotificationPage() {
  const identity = useNotificationStore(state => state.identity);
  const allowed = useAuthStore(state => hasPermission(state.user, 'NOTIFICATION_VIEW'));
  return identity && allowed ? <Inbox key={identity} /> : null;
}
