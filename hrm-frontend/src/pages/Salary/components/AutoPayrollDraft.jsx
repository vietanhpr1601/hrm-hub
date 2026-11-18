import React, { useRef, useState } from 'react';
import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItem, ListItemText, MenuItem, TextField, Typography } from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { createAutoPayroll, previewAutoPayroll } from '../../../services/payrollService';

const AutoPayrollDraft = ({ periods, initialPeriodId }) => {
  const [open, setOpen] = useState(false);
  const [periodId, setPeriodId] = useState('');
  const [preview, setPreview] = useState(null);
  const [selected, setSelected] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submitting = useRef(false);
  const navigate = useNavigate();
  const load = async () => {
    setBusy(true); setError(''); setPreview(null); setSelected([]);
    try {
      const response = await previewAutoPayroll(periodId);
      const result = response.data.data;
      setPreview(result); setSelected(result.employees.filter(row => row.eligible).map(row => row.id));
    } catch (failure) { setError(failure.response?.data?.message || 'Không xem trước được bảng lương.'); }
    finally { setBusy(false); }
  };
  const create = async () => {
    if (submitting.current || !preview || !selected.length) return;
    submitting.current = true; setBusy(true); setError('');
    try {
      const response = await createAutoPayroll(periodId, selected);
      toast.success('Đã lập và tính bảng lương nháp. Bạn kiểm tra rồi duyệt sau.');
      setOpen(false); navigate(`/salary/payrolls/${response.data.data.id}`);
    } catch (failure) { setError(failure.response?.data?.message || 'Không tạo được bảng nháp. Hãy tải lại danh sách trước khi thử lại.'); }
    finally { submitting.current = false; setBusy(false); }
  };
  return <>
    <Button variant="outlined" startIcon={<AutoAwesomeIcon />} onClick={() => { setPeriodId(initialPeriodId || ''); setPreview(null); setError(''); setOpen(true); }}>Tự lập bảng nháp</Button>
    <Dialog open={open} onClose={() => { if (!busy) setOpen(false); }} fullWidth maxWidth="sm">
      <DialogTitle>Tự động lập bảng lương chờ duyệt</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>Lấy khoản lương đã lưu trong hồ sơ và công đã duyệt. Chỉ tạo bản nháp; không tự duyệt, trả tiền hoặc gửi email.</Alert>
        <TextField fullWidth select size="small" label="Kỳ lương" value={periodId} disabled={busy} onChange={event => { setPeriodId(event.target.value); setPreview(null); setSelected([]); setError(''); }}>
          {periods.map(period => <MenuItem key={period.id} value={period.id}>{period.name}</MenuItem>)}
        </TextField>
        <Button sx={{ my: 1 }} disabled={busy || !periodId} onClick={load}>{busy ? 'Đang xử lý…' : 'Xem trước nhân viên'}</Button>
        {error && <Alert severity="error">{error}</Alert>}
        {preview && <Box>
          <Typography variant="body2" fontWeight={600}>Đã chọn {selected.length} / {preview.employees.length} nhân viên</Typography>
          <Typography variant="caption" color="text.secondary">Loại hồ sơ đã xóa, chưa vào làm hoặc nghỉ việc trước kỳ. Nhân viên thiếu cấu hình được báo rõ bên dưới.</Typography>
          <List dense sx={{ maxHeight: 320, overflowY: 'auto' }}>{preview.employees.map(row => <ListItem key={row.id} disablePadding>
            <Checkbox disabled={!row.eligible || busy} checked={selected.includes(row.id)} inputProps={{ 'aria-label': `Chọn ${row.code}` }}
              onChange={event => setSelected(current => event.target.checked ? [...current, row.id] : current.filter(id => id !== row.id))} />
            <ListItemText primary={`${row.code} · ${row.name}`} secondary={row.reason} secondaryTypographyProps={{ color: row.eligible ? 'text.secondary' : 'error' }} />
          </ListItem>)}</List>
        </Box>}
      </DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => setOpen(false)}>Hủy</Button>
        <Button variant="contained" disabled={busy || !preview || !selected.length} onClick={create}>Tạo bản nháp ({selected.length} nhân viên)</Button></DialogActions>
    </Dialog>
  </>;
};
export default AutoPayrollDraft;
