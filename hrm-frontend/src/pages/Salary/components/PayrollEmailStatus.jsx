import React, { useEffect, useState } from 'react';
import { Alert, Box, Button, Stack, Typography } from '@mui/material';
import { getPayslipEmailStatuses, retryPayslipEmail } from '../../../services/payrollService';
import { usePermissions } from '../../../hooks/usePermission';

const labels = { NOT_QUEUED: 'Chưa có email', PENDING: 'Đang chờ gửi', SENT: 'Máy chủ email đã tiếp nhận', FAILED: 'Chưa gửi thành công' };

const PayrollEmailStatus = ({ payslip, open }) => {
  const can = usePermissions();
  const canView = can('PAYROLL_VIEW');
  const [statuses, setStatuses] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    setStatuses(null); setError('');
    if (!open || !canView) return;
    const controller = new AbortController();
    let timer;
    const load = async () => {
      try {
        const response = await getPayslipEmailStatuses(payslip.id, { signal: controller.signal });
        if (controller.signal.aborted) return;
        const result = response.data.data;
        if (!Array.isArray(result)) throw new Error('Trạng thái email không hợp lệ.');
        setStatuses(result); setError('');
        if (result.some(row => row.status === 'PENDING' && row.configured)) timer = setTimeout(load, 10000);
      } catch (failure) { if (!controller.signal.aborted) setError(failure.response?.data?.message || 'Không lấy được trạng thái email.'); }
    };
    load();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [open, payslip.id, payslip.paidStatus, canView, version]);
  if (!canView) return null;
  return <Box sx={{ border: '1px solid', borderColor: 'divider', p: 1.5, borderRadius: 1 }}>
    <Typography variant="subtitle2" fontWeight={700}>Email thông báo lương</Typography>
    <Typography variant="caption" display="block" color="text.secondary">Gửi riêng khi duyệt bảng lương và khi ghi nhận đã thanh toán.</Typography>
    {error && <Alert severity="warning" sx={{ mt: 1 }}>{error}</Alert>}
    {!statuses && !error && <Typography variant="body2">Đang kiểm tra…</Typography>}
    {statuses?.map(status => <Box key={status.eventType} sx={{ mt: 1.5, pt: 1, borderTop: '1px solid', borderColor: 'divider' }}>
    <Typography variant="body2" fontWeight={600}>{status.eventType === 'CONFIRMATION' ? 'Duyệt bảng lương' : 'Xác nhận thanh toán'}</Typography>
    <Typography variant="body2">{status ? labels[status.status] || status.status : error ? 'Chưa xác định' : 'Đang kiểm tra…'}</Typography>
    {status?.recipient && <Typography variant="caption" display="block" sx={{ overflowWrap: 'anywhere' }}>Người nhận: {status.recipient}</Typography>}
    {status?.sentAt && <Typography variant="caption" display="block">{new Date(status.sentAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</Typography>}
    {!status.configured && ['PENDING', 'FAILED'].includes(status.status) && <Alert severity="warning" sx={{ mt: 1 }}>Chưa bật hoặc chưa cấu hình email trên máy chủ. Email chưa được gửi.</Alert>}
    {status?.message && <Typography variant="caption" color={status.status === 'FAILED' ? 'error' : 'text.secondary'} display="block" sx={{ mt: 1 }}>{status.message}</Typography>}
    <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
      {status?.status === 'FAILED' && can('PAYROLL_PAY') && <Button size="small" disabled={busy || !status.configured} onClick={async () => {
        if (!window.confirm('Kiểm tra hộp thư trước khi thử lại để tránh nhận trùng nếu máy chủ email đã tiếp nhận lần trước. Tiếp tục?')) return;
        setBusy(true); setError('');
        try { await retryPayslipEmail(payslip.id, status.eventType); setVersion(value => value + 1); }
        catch (failure) { setError(failure.response?.data?.message || 'Không thể đưa email vào hàng chờ.'); }
        finally { setBusy(false); }
      }}>Thử gửi lại</Button>}
    </Stack>
    </Box>)}
    <Button size="small" disabled={busy} onClick={() => setVersion(value => value + 1)}>Cập nhật trạng thái</Button>
  </Box>;
};
export default PayrollEmailStatus;
