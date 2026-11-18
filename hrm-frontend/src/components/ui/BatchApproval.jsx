import React, { useState } from 'react';
import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Stack, Typography } from '@mui/material';
import { DoneAll, PlaylistAddCheck } from '@mui/icons-material';
import { toast } from 'sonner';
import { batchPreviewRows } from './batchApprovalData';

// A preview freezes explicit IDs across every matching page before any mutation.
// Every row starts ticked; the approver can untick anyone to leave them out ("chọn tất cả nhưng trừ người này ra").
const BatchApproval = ({ search, approve, filters, statusField, pendingStatus, label, describe, onComplete, selectedRows = [], onClearSelection }) => {
  const [rows, setRows] = useState(null);
  const [checked, setChecked] = useState(() => new Set());
  const [submitted, setSubmitted] = useState([]);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const incompatible = filters[statusField] && filters[statusField] !== pendingStatus;
  const pendingSelected = selectedRows.filter(row => row?.[statusField] === pendingStatus);
  const chosen = rows ? rows.filter(row => checked.has(row.id)) : [];

  const open = list => { setRows(list); setChecked(new Set(list.map(row => row.id))); setSubmitted([]); setResult(null); };
  const preview = async () => {
    setBusy(true); setResult(null);
    try {
      const response = await search({ ...filters, [statusField]: pendingStatus, pageIndex: 1, pageSize: 500 });
      const matchingRows = batchPreviewRows(response?.data, statusField, pendingStatus);
      if (!matchingRows.length) { toast.info('Không có bản ghi chờ duyệt theo bộ lọc hiện tại.'); return; }
      open(matchingRows);
    } catch (error) { toast.error(error.response?.data?.message || error.message); }
    finally { setBusy(false); }
  };
  const toggle = id => setChecked(current => {
    const next = new Set(current);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  const toggleAll = () => setChecked(current => (current.size === rows.length ? new Set() : new Set(rows.map(row => row.id))));
  const confirm = async () => {
    if (busy || !chosen.length) return;
    setBusy(true);
    try {
      setSubmitted(chosen);
      const response = await approve(chosen.map(row => row.id));
      setResult(response.data);
      onClearSelection?.();
      await onComplete();
    } catch (error) { toast.error(error.response?.data?.message || 'Chưa xác định được kết quả. Hãy tải lại danh sách trước khi thử lại.'); setRows(null); onClearSelection?.(); await onComplete(); }
    finally { setBusy(false); }
  };

  return <Box sx={{ my: 2 }}>
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
      <Button variant="contained" startIcon={<DoneAll />} disabled={busy || !!incompatible} onClick={preview}>
        {busy ? 'Đang xử lý…' : 'Duyệt tất cả theo bộ lọc'}
      </Button>
      {pendingSelected.length > 0 && <Button variant="outlined" startIcon={<PlaylistAddCheck />} disabled={busy} onClick={() => open(pendingSelected)}>
        Duyệt mục đã chọn ({pendingSelected.length})
      </Button>}
    </Stack>
    <Typography variant="caption" display="block" color="text.secondary" sx={{ mt: 0.5 }}>Chỉ duyệt {label} đang chờ duyệt, gồm tất cả trang phù hợp bộ lọc đã áp dụng. Trước khi xác nhận có thể bỏ tích người không muốn duyệt.</Typography>
    <Dialog open={rows !== null} onClose={() => { if (!busy) setRows(null); }} fullWidth maxWidth="sm">
      <DialogTitle>{result ? 'Kết quả duyệt hàng loạt' : `Duyệt ${chosen.length}/${rows?.length || 0} ${label}?`}</DialogTitle>
      <DialogContent>
        {result ? <Alert severity={result.failed ? 'warning' : 'success'}>Đã duyệt: {result.approved}. Không duyệt được: {result.failed}.</Alert>
          : <Alert severity="info">Tất cả đang được tích. Bỏ tích người không muốn duyệt rồi xác nhận. Mỗi bản ghi vẫn được kiểm tra quyền, trạng thái và điều kiện nghiệp vụ.</Alert>}
        {!result && rows?.length > 0 && <FormControlLabel sx={{ mt: 1, ml: 0 }} label={`Chọn tất cả (${chosen.length}/${rows.length})`}
          control={<Checkbox size="small" checked={chosen.length === rows.length} indeterminate={chosen.length > 0 && chosen.length < rows.length} disabled={busy} onChange={toggleAll} />} />}
        <List dense sx={{ maxHeight: 340, overflow: 'auto' }}>{result
          ? submitted.map(row => {
            const outcome = result?.items?.find(item => item.id === row.id);
            return <ListItem key={row.id}><ListItemText primary={describe(row)} secondary={outcome?.message} secondaryTypographyProps={{ color: outcome?.success === false ? 'error' : 'text.secondary' }} /></ListItem>;
          })
          : rows?.map(row => <ListItem key={row.id} disablePadding>
            <ListItemButton dense disabled={busy} onClick={() => toggle(row.id)}>
              <ListItemIcon sx={{ minWidth: 36 }}><Checkbox edge="start" size="small" checked={checked.has(row.id)} tabIndex={-1} disableRipple inputProps={{ 'aria-label': `Duyệt ${describe(row)}` }} /></ListItemIcon>
              <ListItemText primary={describe(row)} />
            </ListItemButton>
          </ListItem>)}</List>
      </DialogContent>
      <DialogActions>
        <Button disabled={busy} onClick={() => setRows(null)}>{result ? 'Đóng' : 'Hủy'}</Button>
        {!result && <Button variant="contained" disabled={busy || !chosen.length} onClick={confirm}>{busy ? 'Đang duyệt…' : `Xác nhận duyệt ${chosen.length} bản ghi`}</Button>}
      </DialogActions>
    </Dialog>
  </Box>;
};
export default BatchApproval;
