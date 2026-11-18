import React, { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Chip, CircularProgress, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';
import { NumericFormat } from 'react-number-format';
import { toast } from 'sonner';
import { getAllSalaryItems, getStaffSalaryItems, saveStaffSalaryItems } from '../../../../services/salaryItemService';
import { buildSalaryRows, currentFormula, hasSalaryAmount, salaryRowsPayload } from '../../salaryConfigRows';
import usePermission from '../../../../hooks/usePermission';

const money = value => new Intl.NumberFormat('vi-VN').format(value);

const StaffSalaryConfigForm = ({ staffId, isView }) => {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [dirty, setDirty] = useState(false);
  const [type, setType] = useState('');
  const identity = useRef(staffId);
  identity.current = staffId;
  const canManage = usePermission('SALARY_CONFIG_MANAGE');
  const readOnly = isView || !canManage;
  const validStaff = staffId && staffId !== 'new';

  useEffect(() => {
    let active = true;
    setRows([]); setDirty(false); setError('');
    if (!validStaff) return;
    setLoading(true);
    Promise.all([getAllSalaryItems(), getStaffSalaryItems(staffId)]).then(([definitions, configured]) => {
      if (active) setRows(buildSalaryRows(definitions.data.data || definitions.data || [], configured.data.data || configured.data || []));
    }).catch(failure => { if (active) setError(failure.response?.data?.message || 'Không tải được cấu hình lương. Hãy tải lại trang.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [staffId, validStaff]);

  const update = (id, changes) => {
    setRows(current => current.map(row => row.salaryItem.id === id ? { ...row, ...changes } : row));
    setDirty(true);
  };
  const save = async () => {
    if (saving || readOnly || loading || error || !validStaff) return;
    let payload;
    try { payload = salaryRowsPayload(rows); } catch (failure) { toast.error(failure.message); return; }
    const requestedStaff = staffId;
    setSaving(true);
    try {
      const response = await saveStaffSalaryItems(staffId, payload);
      if (identity.current !== requestedStaff) return;
      setRows(buildSalaryRows(rows.map(row => row.salaryItem), response.data.data || response.data || []));
      setDirty(false);
      toast.success('Đã lưu bảng lương & phụ cấp của nhân viên');
    } catch (failure) { if (identity.current === requestedStaff) toast.error(failure.response?.data?.message || 'Không lưu được cấu hình. Dữ liệu đang nhập được giữ lại.'); }
    finally { setSaving(false); }
  };
  if (!validStaff) return <Alert severity="info">Lưu thông tin nhân viên trước khi nhập lương & phụ cấp.</Alert>;
  const selected = rows.filter(hasSalaryAmount);
  const total = selected.reduce((sum, row) => sum + (row.salaryItem.type === 'INCOME' ? 1 : -1) * (Number(row.amount) || 0), 0);
  return <Box>
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" sx={{ mb: 2 }}>
      <Box><Typography variant="h6" fontWeight={700}>Bảng lương & phụ cấp</Typography>
        <Typography variant="body2" color="text.secondary">Mỗi hàng là một khoản. Chỉ cần nhập số tiền vào cột bên phải; để trống nếu nhân viên không có khoản đó.</Typography></Box>
      {!readOnly && <Button variant="contained" startIcon={saving ? <CircularProgress size={16} color="inherit" /> : <SaveIcon />}
        disabled={saving || loading || !!error || !dirty} onClick={save}>Lưu bảng khoản lương</Button>}
    </Stack>
    <Alert severity="info" sx={{ mb: 2 }}>Cố định: số tiền của kỳ. Theo công chuẩn: mức tiền khi đủ công, được nhân với công đã duyệt / công chuẩn khi tính lương. Xóa số tiền (để trống) để ngừng áp dụng khoản đó.</Alert>
    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
    <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
      {[['', 'Tất cả khoản'], ['INCOME', 'Thu nhập / phụ cấp'], ['DEDUCTION', 'Giảm trừ']].map(([value, label]) => <Chip key={value} label={label} color={type === value ? 'primary' : 'default'} onClick={() => setType(value)} />)}
    </Stack>
    {loading ? <CircularProgress size={24} /> : <TableContainer component={Paper} variant="outlined">
      <Table size="small" aria-label="Bảng nhập lương và phụ cấp" sx={{ minWidth: 700 }}>
        <TableHead><TableRow><TableCell>Khoản lương / phụ cấp</TableCell><TableCell>Loại</TableCell><TableCell>Cách tính</TableCell><TableCell align="right">Số tiền (VND)</TableCell></TableRow></TableHead>
        <TableBody>{rows.filter(row => !type || (type === 'INCOME' ? row.salaryItem.type === 'INCOME' : row.salaryItem.type !== 'INCOME')).map(row => <TableRow key={row.salaryItem.id}>
          <TableCell><Typography variant="body2" fontWeight={600}>{row.salaryItem.name}</Typography><Typography variant="caption" color="text.secondary">{row.salaryItem.code}</Typography></TableCell>
          <TableCell>{row.salaryItem.type === 'INCOME' ? 'Thu nhập' : 'Giảm trừ'}</TableCell>
          <TableCell>{currentFormula(row.salaryItem) ? row.salaryItem.calculationType === 'FIXED' ? 'Cố định' : 'Theo công chuẩn' : <Typography color="error" variant="caption">Cách tính cũ — cần cập nhật</Typography>}</TableCell>
          <TableCell align="right"><NumericFormat customInput={TextField} size="small" value={row.amount} thousandSeparator="." decimalSeparator="," decimalScale={0} allowNegative={false} valueIsNumericString
            disabled={readOnly || saving} placeholder="Nhập số tiền" sx={{ width: 200 }} inputProps={{ 'aria-label': `Số tiền ${row.salaryItem.name}`, inputMode: 'numeric', style: { textAlign: 'right' } }}
            onValueChange={({ value }) => { if (value !== row.amount) update(row.salaryItem.id, { amount: value }); }} /></TableCell>
        </TableRow>)}
          {!rows.length && <TableRow><TableCell colSpan={4}>Chưa có khoản lương. Thêm khoản tại mục Lương thưởng → Khoản lương trước.</TableCell></TableRow>}
        </TableBody>
      </Table>
    </TableContainer>}
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" sx={{ mt: 2 }}>
      <Typography variant="body2">Đang áp dụng: <strong>{selected.length} khoản</strong>{dirty ? ' · Có thay đổi chưa lưu' : ''}</Typography>
      <Typography variant="body2">Tổng mức cấu hình sau giảm trừ: <strong>{money(total)} đ</strong></Typography>
    </Stack>
    <Typography variant="caption" color="text.secondary">Đây là mức cấu hình, chưa phải tiền thực lĩnh sau tính công.</Typography>
  </Box>;
};
export default StaffSalaryConfigForm;
