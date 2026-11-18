import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Alert, Autocomplete, Box, Button, Checkbox, Chip, FormControlLabel, IconButton, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Paper, TextField, Tooltip, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import CheckIcon from '@mui/icons-material/Check';
import DeleteIcon from '@mui/icons-material/Delete';
import RefreshIcon from '@mui/icons-material/Refresh';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { toast } from 'sonner';
import { usePermissions } from '../../hooks/usePermission';
import Table from '../../components/ui/Table';
import ListToolbar from '../../components/ui/ListToolbar';
import Popup from '../../components/ui/Popup';
import usePayrollStore from '../../store/usePayrollStore';
import { getStaffLookup } from '../../services/StaffService';
import { addPayrollStaffs, refreshStaffPayroll, removePayrollStaff } from '../../services/payrollService';
import PayslipDetailDialog from './components/PayslipDetailDialog';
import { formatPayrollMoney } from '../../utils/payroll';

const money = formatPayrollMoney;
const errorMessage = (error, fallback) => error.response?.data?.message || fallback;

const PayrollDetailPage = () => {
    const can = usePermissions();
    const { id: payrollId } = useParams();
    const navigate = useNavigate();
    const { allPayrolls, payrollStaffs, loadedPayrollId, loading, calculating, confirming,
        loadAllPayrolls, loadPayrollDetails, recalculatePayroll, confirmPayroll, applyPayslip } = usePayrollStore();
    const [searchDraft, setSearchDraft] = useState('');
    const [keyword, setKeyword] = useState('');
    const [department, setDepartment] = useState('');
    const [selectedDetail, setSelectedDetail] = useState(null);
    const [addOpen, setAddOpen] = useState(false);
    const [staffs, setStaffs] = useState([]);
    const [selectedStaffs, setSelectedStaffs] = useState([]);
    const [staffLoading, setStaffLoading] = useState(false);
    const [staffError, setStaffError] = useState('');
    const [saving, setSaving] = useState(false);
    // Duyệt có tích chọn: mặc định tích tất cả, bỏ tích người không duyệt trong kỳ này.
    const [approveOpen, setApproveOpen] = useState(false);
    const [approveChecked, setApproveChecked] = useState(() => new Set());

    useEffect(() => { loadAllPayrolls(); }, [loadAllPayrolls]);
    useEffect(() => {
        setSelectedDetail(null);
        setAddOpen(false);
        setSelectedStaffs([]);
        loadPayrollDetails(payrollId).catch(error => toast.error(errorMessage(error, 'Không thể tải bảng lương')));
    }, [payrollId, loadPayrollDetails]);
    useEffect(() => {
        if (!addOpen) return;
        let cancelled = false;
        setStaffLoading(true);
        setStaffError('');
        getStaffLookup().then(response => {
            if (!cancelled) setStaffs(Array.isArray(response?.data) ? response.data : []);
        }).catch(error => {
            if (!cancelled) { setStaffs([]); setStaffError(errorMessage(error, 'Không thể tải danh sách nhân viên')); }
        }).finally(() => { if (!cancelled) setStaffLoading(false); });
        return () => { cancelled = true; };
    }, [addOpen]);

    const payroll = allPayrolls.find(row => row.id === payrollId);
    const rows = useMemo(() => loadedPayrollId === payrollId ? payrollStaffs : [], [loadedPayrollId, payrollId, payrollStaffs]);
    const isDraft = payroll?.status === 'DRAFT';
    const busy = loading || calculating || confirming || saving;
    const departments = [...new Set(rows.map(row => row.staff?.departmentName || row.staff?.department?.name).filter(Boolean))];
    const availableStaffs = staffs.filter(staff => !rows.some(row => row.staff?.id === staff.id));
    const filteredRows = rows.filter(row => {
        const name = `${row.staff?.staffCode || ''} ${row.staff?.displayName || ''}`.toLocaleLowerCase('vi');
        return name.includes(keyword.toLocaleLowerCase('vi')) && (!department ||
            (row.staff?.departmentName || row.staff?.department?.name) === department);
    });
    const totals = useMemo(() => rows.reduce((sum, row) => ({
        net: sum.net + (row.netSalary || 0),
        paid: sum.paid + (row.paidStatus === 'PAID' ? (row.paidAmount ?? row.netSalary ?? 0) : 0),
        unpaid: sum.unpaid + (row.paidStatus === 'PAID' ? 0 : (row.netSalary || 0)),
        paidCount: sum.paidCount + (row.paidStatus === 'PAID' ? 1 : 0)
    }), { net: 0, paid: 0, unpaid: 0, paidCount: 0 }), [rows]);

    const handleAdd = async () => {
        if (!selectedStaffs.length || busy) return;
        setSaving(true);
        try {
            await addPayrollStaffs(payrollId, selectedStaffs.map(staff => staff.id));
            await loadPayrollDetails(payrollId);
            setAddOpen(false);
            setSelectedStaffs([]);
            toast.success('Đã thêm và tính lương từ hồ sơ nhân viên');
        } catch (error) { toast.error(errorMessage(error, 'Không thể thêm nhân viên')); }
        finally { setSaving(false); }
    };
    const handleRecalculate = async () => {
        if (!window.confirm('Lấy các khoản lương mới nhất từ hồ sơ nhân viên và công đã duyệt để tính lại bảng lương?')) return;
        try {
            await recalculatePayroll(payrollId);
            toast.success('Đã cập nhật khoản lương từ hồ sơ nhân viên và tính lại bảng lương');
        } catch (error) { toast.error(errorMessage(error, 'Không thể tính lại bảng lương')); }
    };
    const handleConfirm = () => {
        setApproveChecked(new Set(rows.map(row => row.id)));
        setApproveOpen(true);
    };
    const toggleApprove = id => setApproveChecked(current => {
        const next = new Set(current);
        if (next.has(id)) next.delete(id); else next.add(id);
        return next;
    });
    const approvedCount = rows.filter(row => approveChecked.has(row.id)).length;
    const submitApprove = async () => {
        if (busy || !approvedCount) return;
        const excluded = rows.filter(row => !approveChecked.has(row.id));
        setSaving(true);
        try {
            for (const row of excluded) await removePayrollStaff(row.id);
            if (excluded.length) await loadPayrollDetails(payrollId);
        } catch (error) {
            toast.error(errorMessage(error, 'Không thể bỏ nhân viên khỏi bảng lương'));
            await loadPayrollDetails(payrollId).catch(() => {});
            setSaving(false);
            return;
        }
        setSaving(false);
        try {
            await confirmPayroll(payrollId);
            setApproveOpen(false);
            toast.success(`Đã duyệt bảng lương cho ${approvedCount} nhân viên. Email xác nhận phiếu lương đang được gửi.`);
        } catch (error) { toast.error(errorMessage(error, 'Không thể duyệt bảng lương')); }
    };
    const handleRemove = async row => {
        if (!window.confirm(`Bỏ ${row.staff?.displayName || 'nhân viên'} khỏi bảng lương này?`)) return;
        setSaving(true);
        try {
            await removePayrollStaff(row.id);
            await loadPayrollDetails(payrollId);
            toast.success('Đã bỏ nhân viên khỏi bảng lương');
        } catch (error) { toast.error(errorMessage(error, 'Không thể xóa phiếu lương')); }
        finally { setSaving(false); }
    };
    const handleUpdated = updated => {
        applyPayslip(updated);
        setSelectedDetail(current => current?.id === updated.id ? updated : current);
    };
    const handleRefreshStaff = async row => {
        if (busy) return;
        setSaving(true);
        try {
            const response = await refreshStaffPayroll(row.id);
            const updated = response?.data?.data || response?.data;
            if (!updated?.id) throw new Error('Dữ liệu lương cập nhật không hợp lệ');
            handleUpdated(updated);
            toast.success(`Đã cập nhật khoản lương của ${row.staff?.displayName || 'nhân viên'} từ hồ sơ`);
        } catch (error) { toast.error(errorMessage(error, error.message || 'Không thể cập nhật khoản lương')); }
        finally { setSaving(false); }
    };

    const columns = [
        { title: 'Thao tác', align: 'center', render: row => <Box display="flex" gap={0.5}>
            <Tooltip title="Chi tiết / thanh toán"><IconButton size="small" onClick={() => setSelectedDetail(row)} disabled={busy}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>
            {isDraft && can('PAYROLL_MANAGE') && <>
                <Tooltip title="Cập nhật khoản lương từ hồ sơ"><IconButton size="small" color="primary" onClick={() => handleRefreshStaff(row)} disabled={busy}><RefreshIcon fontSize="small" /></IconButton></Tooltip>
                <Tooltip title="Bỏ nhân viên khỏi bảng"><IconButton size="small" color="error" onClick={() => handleRemove(row)} disabled={busy}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
            </>}
        </Box> },
        { title: 'Mã NV', render: row => row.staff?.staffCode || '' },
        { title: 'Nhân viên', render: row => can('STAFF_VIEW')
            ? <Button size="small" onClick={() => navigate(`/staff/${row.staff?.id}`)} sx={{ textTransform: 'none', justifyContent: 'flex-start' }}>{row.staff?.displayName || ''}</Button>
            : row.staff?.displayName || '' },
        { title: 'Phòng ban', render: row => row.staff?.departmentName || row.staff?.department?.name || '—' },
        { title: 'Công thực tế đã duyệt', field: 'totalWorkDays', align: 'right' },
        { title: 'Khoản lương', render: row => row.items?.length ? `${row.items.length} khoản` : <Chip size="small" label="Cần cập nhật từ hồ sơ" color="warning" /> },
        { title: 'Tổng thu nhập', align: 'right', render: row => money(row.totalIncome) },
        { title: 'Tổng khấu trừ', align: 'right', render: row => money(row.totalDeduction) },
        { title: 'Thực nhận', align: 'right', render: row => money(row.netSalary) },
        { title: 'Thanh toán', render: row => <Chip size="small" label={row.paidStatus === 'PAID' ? 'Đã thanh toán' : 'Chưa thanh toán'} color={row.paidStatus === 'PAID' ? 'success' : 'default'} /> }
    ];

    return <div className="space-y-4 mt-2">
        <Paper elevation={0} className="py-4 px-2 md:px-4 border border-border">
            <Box display="flex" alignItems="center" gap={1} mb={2}>
                <IconButton onClick={() => navigate('/salary/payrolls')}><ArrowBackIcon /></IconButton>
                <Box flex={1}><Typography variant="h6" fontWeight="bold">{payroll?.name || 'Chi tiết bảng lương'}</Typography>
                    <Typography variant="body2" color="text.secondary">{payroll?.period?.name}</Typography></Box>
                {payroll && <Chip label={isDraft ? 'Chờ duyệt' : 'Đã duyệt'} color={isDraft ? 'default' : 'success'} />}
            </Box>
            {payroll && <Alert severity="info" sx={{ mb: 2 }}>{isDraft
                ? 'Khoản lương lấy từ hồ sơ nhân viên. Tính và kiểm tra bảng lương, sau đó duyệt để ghi nhận thanh toán.'
                : 'Bảng lương đã khóa. Mở từng phiếu để ghi nhận số tiền đã trả sau khi thanh toán.'}</Alert>}
            {isDraft && rows.some(row => row.items?.some(item => (item.calculationType ?? item.salaryItem?.calculationType) === 'DAILY_MULTIPLIED')) &&
                <Alert severity="warning" sx={{ mb: 2 }}>Có khoản dùng cách tính cũ. Cập nhật thành cố định hoặc theo công chuẩn tại “Khoản lương” trong hồ sơ nhân viên, rồi tính lại bảng lương.</Alert>}
            <Box display="flex" flexWrap="wrap" gap={3} mb={2}>
                <Typography variant="body2">{rows.length} nhân viên · Tổng thực nhận: <b>{money(totals.net)}</b></Typography>
                <Typography variant="body2">Đã thanh toán: <b>{money(totals.paid)}</b> ({totals.paidCount}/{rows.length})</Typography>
                <Typography variant="body2">Chưa thanh toán: <b>{money(totals.unpaid)}</b></Typography>
            </Box>
            <Box display="flex" flexWrap="wrap" gap={1} mb={2}>
                {isDraft && can('PAYROLL_MANAGE') && <>
                    <Button startIcon={<AddIcon />} variant="contained" size="small" disabled={busy} onClick={() => setAddOpen(true)}>Thêm nhân viên</Button>
                    <Button startIcon={<RefreshIcon />} size="small" disabled={busy || !rows.length} onClick={handleRecalculate}>Cập nhật khoản lương & tính lại</Button>
                </>}
                {isDraft && can('PAYROLL_CONFIRM') && <Button startIcon={<CheckIcon />} color="success" variant="contained" size="small" disabled={busy || !rows.length} onClick={handleConfirm}>Duyệt bảng lương</Button>}
            </Box>
            <ListToolbar searchDraft={searchDraft} onSearchDraftChange={setSearchDraft}
                onSearch={() => setKeyword(searchDraft.trim())}
                onReset={() => { setSearchDraft(''); setKeyword(''); setDepartment(''); }}
                searchPlaceholder="Tìm tên, mã nhân viên..."
                searchExtraControls={<TextField select size="small" label="Phòng ban" value={department}
                    onChange={event => setDepartment(event.target.value)} SelectProps={{ native: true }}
                    InputLabelProps={{ shrink: true }} sx={{ minWidth: 180 }}>
                    <option value="">Tất cả</option>{departments.map(name => <option key={name} value={name}>{name}</option>)}
                </TextField>} />
            {!loading && !rows.length && <Alert severity="info" sx={{ my: 2 }}
                action={isDraft && can('PAYROLL_MANAGE') ? <Button size="small" onClick={() => setAddOpen(true)} disabled={busy}>Thêm nhân viên</Button> : undefined}>
                Bảng lương chưa có nhân viên. Chọn “Thêm nhân viên” để tạo phiếu lương.
            </Alert>}
            <Table columns={columns} data={filteredRows} totalElements={filteredRows.length}
                page={0} pageSize={100} handleChangePage={() => {}} setRowsPerPage={() => {}} nonePagination loading={busy} />
        </Paper>
        <Popup open={addOpen} onClosePopup={() => { if (!saving) setAddOpen(false); }} title="Thêm nhân viên vào bảng lương" size="sm"
            action={<><Button onClick={() => setAddOpen(false)} disabled={saving}>Hủy</Button>
                <Button variant="contained" onClick={handleAdd} disabled={busy || staffLoading || !selectedStaffs.length}>{saving ? 'Đang thêm...' : 'Thêm & tính lương'}</Button></>}>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Tính lương cho nhân viên được chọn từ các khoản đã lưu trong hồ sơ. Nếu cần thay đổi tiền hoặc cách tính, sửa mục “Khoản lương” ở hồ sơ nhân viên.</Typography>
            {staffError && <Alert severity="error" sx={{ mb: 2 }}>{staffError}</Alert>}
            <Autocomplete multiple options={availableStaffs} value={selectedStaffs} loading={staffLoading} disabled={saving}
                getOptionLabel={staff => `${staff.staffCode || ''} — ${staff.displayName || ''}`}
                isOptionEqualToValue={(option, value) => option.id === value.id}
                onChange={(_, value) => setSelectedStaffs(value)} noOptionsText="Không còn nhân viên để thêm"
                renderInput={params => <TextField {...params} label="Chọn nhân viên" placeholder="Tìm tên hoặc mã" />} />
        </Popup>
        <Popup open={approveOpen} onClosePopup={() => { if (!busy) setApproveOpen(false); }} title="Duyệt bảng lương" size="sm"
            action={<><Button onClick={() => setApproveOpen(false)} disabled={busy}>Hủy</Button>
                <Button variant="contained" color="success" onClick={submitApprove} disabled={busy || !approvedCount}>{busy ? 'Đang duyệt...' : `Duyệt ${approvedCount} nhân viên`}</Button></>}>
            <Alert severity="info" sx={{ mb: 1 }}>Tất cả đang được tích. Bỏ tích người chưa duyệt trong kỳ này: người đó sẽ được bỏ khỏi bảng lương trước khi duyệt. Sau khi duyệt, số tiền được khóa và mỗi nhân viên nhận email xác nhận phiếu lương về địa chỉ email trong hồ sơ.</Alert>
            <FormControlLabel sx={{ ml: 0 }} label={`Chọn tất cả (${approvedCount}/${rows.length})`}
                control={<Checkbox size="small" checked={approvedCount === rows.length && rows.length > 0} indeterminate={approvedCount > 0 && approvedCount < rows.length} disabled={busy}
                    onChange={() => setApproveChecked(approvedCount === rows.length ? new Set() : new Set(rows.map(row => row.id)))} />} />
            <List dense sx={{ maxHeight: 360, overflow: 'auto' }}>{rows.map(row => {
                const addresses = [row.staff?.email, row.staff?.companyEmail].map(value => (value || '').trim()).filter(Boolean);
                const email = addresses.find(value => value.toLowerCase().endsWith('@gmail.com')) || row.staff?.companyEmail || row.staff?.email;
                return <ListItem key={row.id} disablePadding>
                    <ListItemButton dense disabled={busy} onClick={() => toggleApprove(row.id)}>
                        <ListItemIcon sx={{ minWidth: 36 }}><Checkbox edge="start" size="small" checked={approveChecked.has(row.id)} tabIndex={-1} disableRipple inputProps={{ 'aria-label': `Duyệt lương ${row.staff?.displayName || ''}` }} /></ListItemIcon>
                        <ListItemText primary={`${row.staff?.staffCode || ''} · ${row.staff?.displayName || ''} · ${money(row.netSalary)}`}
                            secondary={email ? `Email: ${email}` : 'Chưa có email trong hồ sơ — sẽ không gửi được email'}
                            secondaryTypographyProps={{ color: email ? 'text.secondary' : 'warning.main' }} />
                    </ListItemButton>
                </ListItem>;
            })}</List>
        </Popup>
        <PayslipDetailDialog open={Boolean(selectedDetail)} onClose={() => setSelectedDetail(null)} detail={selectedDetail}
            activePayroll={payroll} isAdmin={can('PAYROLL_PAY')} onUpdateSuccess={handleUpdated} />
    </div>;
};

export default PayrollDetailPage;
