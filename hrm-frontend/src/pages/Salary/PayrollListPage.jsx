import { usePermissions } from '../../hooks/usePermission';
import PermissionGuard from '../../components/auth/PermissionGuard';
import React, { useEffect, useState, useMemo, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
    Paper,
    Box,
    Typography,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Button,
    IconButton,
    Tooltip,
    Alert,
    Autocomplete,
    TextField as MuiTextField
} from '@mui/material';
import TextField from '../../components/ui/TextField';
import Popup from '../../components/ui/Popup';
import VisibilityIcon from '@mui/icons-material/Visibility';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckIcon from '@mui/icons-material/Check';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import RefreshIcon from '@mui/icons-material/Refresh';
import AutoPayrollDraft from './components/AutoPayrollDraft';
import { calculatePayroll, updatePayrollDetails } from '../../services/payrollService';
import { toast } from 'sonner';
import { Formik } from 'formik';

import Table from '../../components/ui/Table';
import ListToolbar from '../../components/ui/ListToolbar';
import SelectInput from '../../components/ui/SelectInput';
import { PayrollStatus, PayrollStatusConfig } from '../../constants';
import usePayrollStore from '../../store/usePayrollStore';
import usePeriodStore from '../../store/usePeriodStore';
import { getStaffLookup } from '../../services/StaffService';

const PayrollListPage = () => {
    const can = usePermissions();
    const location = useLocation();
    const navigate = useNavigate();

    // Store states and actions
    const {
        allPeriods,
        loadAllPeriods
    } = usePeriodStore();

    const {
        allPayrolls,
        loading,
        confirming,
        deleting,
        creating: savingPayroll,
        loadAllPayrolls,
        createPayrollAction,
        confirmPayroll,
        deletePayroll
    } = usePayrollStore();

    // Local states
    const [selectedPeriodId, setSelectedPeriodId] = useState('');
    const [openNewPayrollDialog, setOpenNewPayrollDialog] = useState(false);
    const createFormikRef = useRef();
    const createInProgress = useRef(false);
    const [staffs, setStaffs] = useState([]);
    const [staffLoading, setStaffLoading] = useState(false);
    const [staffError, setStaffError] = useState('');
    const [editing, setEditing] = useState(null);
    const [editValues, setEditValues] = useState({ name: '', code: '', description: '' });
    const [updating, setUpdating] = useState(false);

    const saveDetails = async () => {
        if (updating || !editing) return;
        setUpdating(true);
        try { await updatePayrollDetails(editing.id, editValues); await loadAllPayrolls(); setEditing(null); toast.success('Đã cập nhật thông tin bảng lương'); }
        catch (error) { toast.error(error.response?.data?.message || 'Không cập nhật được bảng lương'); }
        finally { setUpdating(false); }
    };
    const recalculate = async row => {
        if (!window.confirm(`Cập nhật khoản lương từ hồ sơ và công đã duyệt để tính lại “${row.name}”?`)) return;
        setUpdating(true);
        try { await calculatePayroll(row.id); await loadAllPayrolls(); toast.success('Đã cập nhật khoản lương và tính lại bảng nháp'); }
        catch (error) { toast.error(error.response?.data?.message || 'Không tính lại được bảng lương'); }
        finally { setUpdating(false); }
    };

    // Search
    const [searchKeyword, setSearchKeyword] = useState('');
    const [searchDraft, setSearchDraft] = useState('');

    const periods = useMemo(() => {
        if (!allPeriods) return [];
        return [...allPeriods].sort((a, b) => (b.year * 100 + b.month) - (a.year * 100 + a.month));
    }, [allPeriods]);

    useEffect(() => {
        loadAllPeriods();
        loadAllPayrolls();
    }, [loadAllPeriods, loadAllPayrolls]);

    useEffect(() => {
        if (!openNewPayrollDialog) return;
        let cancelled = false;
        setStaffLoading(true);
        setStaffError('');
        getStaffLookup().then(response => {
            if (!Array.isArray(response?.data)) throw new Error('Danh sách nhân viên không hợp lệ.');
            if (!cancelled) setStaffs(response.data);
        }).catch(error => {
            if (!cancelled) {
                setStaffs([]);
                setStaffError(error.response?.data?.message || error.message || 'Không thể tải nhân viên.');
            }
        }).finally(() => { if (!cancelled) setStaffLoading(false); });
        return () => { cancelled = true; };
    }, [openNewPayrollDialog]);

    // Listen to location state changes
    useEffect(() => {
        if (location.state?.periodId) setSelectedPeriodId(location.state.periodId);
    }, [location.state]);

    // ── Actions ───────────────────────────────────────────────────────────────

    const handleConfirmPayroll = async (payrollId) => {
        if (!payrollId) return;
        if (!window.confirm('Bạn có chắc chắn muốn xác nhận bảng lương này? Sau khi xác nhận sẽ không thể tính toán lại.')) return;
        try {
            await confirmPayroll(payrollId);
            toast.success('Đã xác nhận bảng lương thành công!');
        } catch (error) {
            console.error('Failed to confirm payroll:', error);
            toast.error(error.response?.data?.message || 'Không thể xác nhận bảng lương');
        }
    };

    const handleDeletePayroll = async (payrollId) => {
        if (!payrollId) return;
        const payrollObj = allPayrolls.find(p => p.id === payrollId);
        if (payrollObj && payrollObj.status !== 'DRAFT') {
            toast.warning('Không thể xóa bảng lương đã xác nhận!');
            return;
        }
        if (!window.confirm(`Xóa bảng nháp “${payrollObj?.name || ''}” và các phiếu thuộc bảng? Dữ liệu được đánh dấu đã xóa để giữ lịch sử.`)) return;
        try {
            await deletePayroll(payrollId);
            toast.success('Xóa bảng lương thành công!');
            await loadAllPayrolls();
        } catch (error) {
            console.error('Failed to delete payroll:', error);
            toast.error(error.response?.data?.message || 'Không thể xóa bảng lương');
        }
    };

    // ── Filtering ─────────────────────────────────────────────────────────────

    const filteredPayrolls = useMemo(() => {
        return allPayrolls.filter(p => {
            const matchesPeriod = !selectedPeriodId || p.period?.id === selectedPeriodId;
            const matchesKeyword = !searchKeyword ||
                p.name.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                (p.code && p.code.toLowerCase().includes(searchKeyword.toLowerCase()));
            return matchesPeriod && matchesKeyword;
        });
    }, [allPayrolls, selectedPeriodId, searchKeyword]);

    const periodFilterOptions = useMemo(() => [
        { value: '', name: 'Tất cả kỳ lương' },
        ...periods.map(p => ({ value: p.id, name: p.name }))
    ], [periods]);

    // ── Columns ───────────────────────────────────────────────────────────────

    const masterColumns = [
        {
            title: 'Thao tác',
            align: 'center',
            width: '1%',
            render: (row) => (
                <div style={{ display: 'flex', justifyContent: 'center', gap: '4px', flexWrap: 'wrap', minWidth: 220 }}>
                    <Tooltip title="Xem chi tiết phiếu lương" arrow>
                        <IconButton size="small" color="primary" onClick={() => navigate(`/salary/payrolls/${row.id}`)}>
                            <VisibilityIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    <PermissionGuard permissions="PAYROLL_MANAGE">
                        <Tooltip title={row.status === 'DRAFT' ? 'Sửa tên, mã và mô tả bảng lương' : 'Bảng đã duyệt được khóa'}><span>
                        <Button size="small" startIcon={<EditIcon />} disabled={row.status !== 'DRAFT' || updating} onClick={() => { setEditing(row); setEditValues({ name: row.name || '', code: row.code || '', description: row.description || '' }); }}>Cập nhật</Button>
                        </span></Tooltip>
                        <Button size="small" startIcon={<RefreshIcon />} disabled={row.status !== 'DRAFT' || updating} onClick={() => recalculate(row)}>Tính lại</Button>
                        <Tooltip title={row.status === 'DRAFT' ? 'Xóa bảng nháp và các phiếu thuộc bảng' : 'Không xóa bảng đã duyệt hoặc đã trả tiền'}><span>
                        <Button size="small" color="error" startIcon={<DeleteIcon />} disabled={row.status !== 'DRAFT' || deleting || updating} onClick={() => handleDeletePayroll(row.id)}>Xóa</Button>
                        </span></Tooltip>
                    </PermissionGuard>
                    {row.status === 'DRAFT' && (
                        <>
                            <PermissionGuard permissions="PAYROLL_CONFIRM"><Tooltip title="Xác nhận bảng lương" arrow>
                                <IconButton size="small" color="success" disabled={updating || confirming || deleting} onClick={() => handleConfirmPayroll(row.id)}>
                                    <CheckIcon fontSize="small" />
                                </IconButton>
                            </Tooltip></PermissionGuard>
                        </>
                    )}
                </div>
            ),
        },
        {
            title: 'Tên bảng lương',
            field: 'name',
            align: 'center',
            render: (row) => (
                <span
                    onClick={() => navigate(`/salary/payrolls/${row.id}`)}
                    className="cursor-pointer text-primary font-semibold hover:underline"
                >
                    {row.name}
                </span>
            )
        },
        {
            title: 'Mã bảng lương',
            field: 'code',
            align: 'center',
            render: (row) => <span>{row.code || '---'}</span>
        },
        {
            title: 'Mô tả',
            field: 'description',
            align: 'center',
            render: (row) => <span>{row.description || '---'}</span>
        },
        {
            title: 'Kỳ tính lương',
            align: 'center',
            render: (row) => <span>{row.period?.name || '---'}</span>
        },
        {
            title: 'Trạng thái',
            align: 'center',
            render: (row) => {
                const cfg = PayrollStatusConfig[row.status] ?? PayrollStatusConfig[PayrollStatus.DRAFT];
                return <span>{cfg.label}</span>;
            }
        }
    ];

    // ── Toolbar controls ──────────────────────────────────────────────────────

    const searchExtraControls = (
        <Box display="flex" alignItems="center" gap={2} flexWrap="wrap">
            <FormControl variant="outlined" size="small" style={{ minWidth: 200 }}>
                <InputLabel id="select-period-filter-label">Chọn kỳ lương</InputLabel>
                <Select
                    labelId="select-period-filter-label"
                    value={selectedPeriodId}
                    onChange={(e) => setSelectedPeriodId(e.target.value)}
                    label="Chọn kỳ lương"
                >
                    {periodFilterOptions.map((opt) => (
                        <MenuItem key={opt.value} value={opt.value}>{opt.name}</MenuItem>
                    ))}
                </Select>
            </FormControl>
        </Box>
    );

    return (
        <div className="space-y-4">
            <Paper elevation={0} className="py-4 px-2 md:px-4 border border-border">
                {can('PAYROLL_MANAGE') && <Box display="flex" justifyContent="flex-end" flexWrap="wrap" gap={1} mb={2}>
                    <AutoPayrollDraft periods={periods} initialPeriodId={selectedPeriodId} />
                    <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpenNewPayrollDialog(true)} disabled={savingPayroll}>
                        Tạo bảng lương cho nhân viên
                    </Button>
                </Box>}
                <ListToolbar
                    searchDraft={searchDraft}
                    onSearchDraftChange={setSearchDraft}
                    onSearch={() => setSearchKeyword(searchDraft)}
                    onReset={() => {
                        setSearchDraft('');
                        setSearchKeyword('');
                        setSelectedPeriodId('');
                    }}
                    searchPlaceholder="Tìm theo tên hoặc mã bảng lương..."
                    searchExtraControls={searchExtraControls}
                />

                <Table
                    columns={masterColumns}
                    data={filteredPayrolls}
                    totalElements={filteredPayrolls.length}
                    page={0}
                    pageSize={100}
                    handleChangePage={() => {}}
                    setRowsPerPage={() => {}}
                    nonePagination={true}
                    loading={loading || confirming || deleting}
                />
            </Paper>

            <Popup open={Boolean(editing)} onClosePopup={() => { if (!updating) setEditing(null); }} title="Cập nhật bảng lương" size="sm"
                action={<><Button disabled={updating} onClick={() => setEditing(null)}>Hủy</Button><Button variant="contained" disabled={updating || !editValues.name.trim()} onClick={saveDetails}>Lưu cập nhật</Button></>}>
                <Box display="flex" flexDirection="column" gap={2} pt={2}>
                    {[['name', 'Tên bảng lương'], ['code', 'Mã bảng lương'], ['description', 'Mô tả']].map(([key, label]) => <MuiTextField key={key} size="small" label={label} value={editValues[key]}
                        required={key === 'name'} disabled={updating} inputProps={{ maxLength: 255 }} onChange={event => setEditValues(current => ({ ...current, [key]: event.target.value }))} />)}
                    <Alert severity="info">Đổi khoản lương ở hồ sơ nhân viên, rồi bấm Tính lại. Bảng đã duyệt không được cập nhật hoặc xóa.</Alert>
                </Box>
            </Popup>
            {/* Create New Payroll Popup */}
            <Popup
                open={openNewPayrollDialog}
                onClosePopup={() => { if (!savingPayroll) setOpenNewPayrollDialog(false); }}
                title="Tạo bảng lương mới"
                size="sm"
                action={
                    <>
                        <Button onClick={() => setOpenNewPayrollDialog(false)} color="inherit" disabled={savingPayroll}>
                            Hủy
                        </Button>
                        <Button
                            onClick={() => createFormikRef.current?.handleSubmit()}
                            color="primary"
                            variant="contained"
                            disabled={savingPayroll || staffLoading || Boolean(staffError)}
                        >
                            {savingPayroll ? 'Đang tạo...' : 'Tạo bảng lương'}
                        </Button>
                    </>
                }
            >
                <Formik
                    innerRef={createFormikRef}
                    initialValues={{ periodId: '', name: '', code: '', description: '', staffIds: [] }}
                    onSubmit={async (values) => {
                        if (createInProgress.current) return;
                        if (!values.periodId) {
                            toast.warning('Vui lòng chọn kỳ tính lương');
                            return;
                        }
                        if (!values.name.trim()) {
                            toast.warning('Vui lòng nhập tên bảng lương');
                            return;
                        }
                        if (!values.staffIds.length) {
                            toast.warning('Vui lòng chọn nhân viên cần tạo phiếu lương');
                            return;
                        }
                        createInProgress.current = true;
                        try {
                            const response = await createPayrollAction(
                                values.periodId,
                                values.name.trim(),
                                values.code.trim(),
                                values.description.trim(),
                                values.staffIds
                            );
                            if (response?.data) {
                                toast.success(`Đã tạo và tính bảng lương cho ${values.staffIds.length} nhân viên`);
                                const newPayroll = response.data.data || response.data;
                                navigate(`/salary/payrolls/${newPayroll.id}`);
                                setOpenNewPayrollDialog(false);
                            }
                        } catch (error) {
                            console.error('Failed to create payroll:', error);
                            toast.error(error.response?.data?.message || 'Không thể tạo bảng lương mới');
                        } finally {
                            createInProgress.current = false;
                        }
                    }}
                >
                    {({ values, setFieldValue }) => (
                        <Box>
                            <Typography variant="body2" color="text.secondary" className="mb-3">
                                Chọn kỳ và nhân viên. Hệ thống tính lương từ các khoản đã lưu ở hồ sơ nhân viên, sau đó bạn kiểm tra và duyệt bảng lương.
                            </Typography>

                            <SelectInput
                                name="periodId"
                                label="Chọn kỳ lương"
                                options={periods}
                                keyValue="id"
                                displayvalue="name"
                                hideNullOption={true}
                                required
                                handleChange={(e, value) => {
                                    const selectedPer = periods.find(p => p.id === value);
                                    if (selectedPer) {
                                        const formattedMonth = String(selectedPer.month).padStart(2, '0');
                                        createFormikRef.current?.setFieldValue('name', `Bảng lương Kỳ lương Tháng ${formattedMonth}/${selectedPer.year}`);
                                    }
                                }}
                            />

                            {staffError && <Alert severity="error" sx={{ my: 2 }}>{staffError}</Alert>}
                            <Autocomplete multiple options={staffs} loading={staffLoading} disabled={savingPayroll}
                                value={staffs.filter(staff => values.staffIds.includes(staff.id))}
                                getOptionLabel={staff => `${staff.staffCode || ''} — ${staff.displayName || ''}`}
                                isOptionEqualToValue={(option, value) => option.id === value.id}
                                onChange={(_, selected) => setFieldValue('staffIds', selected.map(staff => staff.id))}
                                noOptionsText={staffLoading ? 'Đang tải nhân viên...' : 'Không có nhân viên để chọn'}
                                renderInput={params => <MuiTextField {...params} label="Nhân viên nhận lương" required
                                    placeholder="Tìm tên hoặc mã nhân viên" helperText="Nhân viên cần có khoản lương đã lưu trong hồ sơ. Có thể chọn một hoặc nhiều người." />}
                                sx={{ my: 2 }} />

                            <TextField
                                name="name"
                                label="Tên bảng lương"
                                placeholder="Nhập tên bảng lương..."
                                required
                            />

                            <TextField
                                name="code"
                                label="Mã bảng lương"
                                placeholder="Để trống để hệ thống tạo mã riêng"
                                helperText="Mã được tạo riêng cho từng bảng; mỗi kỳ chỉ có một bảng được duyệt."
                            />

                            <TextField
                                name="description"
                                label="Mô tả"
                                placeholder="Nhập mô tả bảng lương..."
                                multiline
                                rows={2}
                            />
                        </Box>
                    )}
                </Formik>
            </Popup>
        </div>
    );
};

export default PayrollListPage;
