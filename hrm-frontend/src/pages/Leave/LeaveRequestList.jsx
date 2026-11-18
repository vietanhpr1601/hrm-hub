import { usePermissions } from '../../hooks/usePermission';
import PermissionGuard from '../../components/auth/PermissionGuard';
import React, { useEffect, useState, useRef } from 'react';
import { Paper, Button, IconButton, Chip, Grid, Stack, TextField as MuiTextField, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';
import BatchApproval from '../../components/ui/BatchApproval';
import { searchLeaveRequests, approveLeaveBatch } from '../../services/leaveService';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import { useTranslation } from 'react-i18next';
import { Formik } from 'formik';
import { toast } from 'sonner';

import useLeaveStore from '../../store/useLeaveStore';
import Table from '../../components/ui/Table';
import ListToolbar from '../../components/ui/ListToolbar';
import FilterPanel from '../../components/ui/FilterPanel';
import SelectInput from '../../components/ui/SelectInput';
import AsyncAutocomplete from '../../components/ui/AsyncAutocomplete';
import LeaveRequestForm from './components/LeaveRequestForm';
import ConfirmationDialog from '../../components/ui/ConfirmationDialog';
import { formatDate } from '../../LocalFunction';
import { pagingStaffLookup } from '../../services/StaffService';

const LeaveRequestList = () => {
    const can = usePermissions();
    const { t } = useTranslation();
    const {
        requests,
        loading,
        page,
        pageSize,
        totalElements,
        setPage,
        setPageSize,
        setFilters,
        filters,
        loadRequests,
        approveRequest,
        rejectRequest,
        removeLeaveRequest,
        setSelectedRequest,
        selectedRequest,
        openForm,
        setOpenForm,
        keyword,
        setKeyword,
        resetStore
    } = useLeaveStore();

    const [filterOpen, setFilterOpen] = useState(false);
    const [searchDraft, setSearchDraft] = useState(keyword || '');
    const [openApproveConfirm, setOpenApproveConfirm] = useState(false);
    const [openRejectDialog, setOpenRejectDialog] = useState(false);
    const [openDeleteConfirm, setOpenDeleteConfirm] = useState(false);
    const [rejectReason, setRejectReason] = useState('');
    const formikRef = useRef();

    // Ticked rows (only pending requests can be ticked) for "Duyệt mục đã chọn"
    const [selectedRows, setSelectedRows] = useState([]);
    useEffect(() => { setSelectedRows([]); }, [requests]);

    useEffect(() => {
        return () => {
            resetStore();
        };
    }, [resetStore]);

    useEffect(() => {
        setSearchDraft(keyword || '');
    }, [keyword]);

    useEffect(() => {
        // If filters has staffId but no staff filter is selected in Formik (e.g. carried-over from MyLeaveRequests),
        // we reset the store state first to avoid loading incorrect data and calling the API twice.
        if (filters.staffId && !formikRef.current?.values?.staff?.id) {
            resetStore();
            return;
        }
        loadRequests();
    }, [page, pageSize, keyword, filters, loadRequests, resetStore]);

    const handleSearch = () => {
        setKeyword(searchDraft);
    };

    const handleApplyFilters = () => {
        setKeyword(searchDraft);
        formikRef.current?.handleSubmit();
    };

    const handleReset = () => {
        setSearchDraft('');
        formikRef.current?.resetForm();
        setKeyword('');
        setFilters({});
    };

    const handleAdd = () => {
        setSelectedRequest(null);
        setOpenForm(true);
    };

    const handleEdit = (row) => {
        setSelectedRequest(row);
        setOpenForm(true);
    };

    const handleApproveClick = (row) => {
        setSelectedRequest(row);
        setOpenApproveConfirm(true);
    };

    const handleRejectClick = (row) => {
        setSelectedRequest(row);
        setRejectReason('');
        setOpenRejectDialog(true);
    };

    const handleDeleteClick = (row) => {
        setSelectedRequest(row);
        setOpenDeleteConfirm(true);
    };

    const confirmApprove = async () => {
        if (selectedRequest?.id) {
            try {
                await approveRequest(selectedRequest.id, '');
                toast.success(t('leave.message.approveSuccess', 'Phê duyệt đơn nghỉ phép thành công'));
                setOpenApproveConfirm(false);
                setSelectedRequest(null);
            } catch (error) {
                toast.error(error.response?.data?.message || t('leave.message.approveError', 'Lỗi khi phê duyệt'));
            }
        }
    };

    const confirmReject = async () => {
        if (selectedRequest?.id) {
            try {
                await rejectRequest(selectedRequest.id, rejectReason);
                toast.success(t('leave.message.rejectSuccess', 'Từ chối đơn nghỉ phép thành công'));
                setOpenRejectDialog(false);
                setSelectedRequest(null);
            } catch (error) {
                toast.error(error.response?.data?.message || t('leave.message.rejectError', 'Lỗi khi từ chối'));
            }
        }
    };

    const confirmDelete = async () => {
        if (selectedRequest?.id) {
            try {
                await removeLeaveRequest(selectedRequest.id);
                toast.success(t('leave.message.deleteSuccess', 'Xóa đơn nghỉ phép thành công'));
                setOpenDeleteConfirm(false);
                setSelectedRequest(null);
            } catch (error) {
                toast.error(error.response?.data?.message || t('leave.message.deleteError', 'Lỗi khi xóa đơn'));
            }
        }
    };

    const getLeaveTypeName = (type) => {
        switch (type) {
            case 'MAKEUP': return 'Nghỉ có làm bù';
            case 'ANNUAL': return t('leave.type.annual', 'Phép năm');
            case 'UNPAID': return t('leave.type.unpaid', 'Phép không lương');
            default: return type;
        }
    };

    const getStatusChip = (status) => {
        switch (status) {
            case 'PENDING':
                return <Chip label={t('leave.status.pending', 'Chờ duyệt')} color="warning" size="small" />;
            case 'APPROVED':
                return <Chip label={t('leave.status.approved', 'Đã duyệt')} color="success" size="small" />;
            case 'REJECTED':
                return <Chip label={t('leave.status.rejected', 'Từ chối')} color="error" size="small" />;
            default:
                return <Chip label={status} size="small" />;
        }
    };

    const columns = [
        {
            title: t('general.actions', 'Thao tác'),
            field: 'actions',
            width: 150,
            align: 'center',
            render: (rowData) => {
                const isPending = rowData.approvalStatus === 'PENDING';
                return (
                    <div className="flex items-center justify-center">
                        {isPending ? (
                            <>
                                <PermissionGuard permissions="LEAVE_APPROVE"><IconButton size="small" sx={{ color: '#2e7d32' }} onClick={() => handleApproveClick(rowData)} title={t('leave.action.approve', 'Phê duyệt')}>
                                    <CheckIcon fontSize="small" />
                                </IconButton></PermissionGuard>
                                <PermissionGuard permissions="LEAVE_APPROVE"><IconButton size="small" sx={{ color: '#d32f2f' }} onClick={() => handleRejectClick(rowData)} title={t('leave.action.reject', 'Từ chối')}>
                                    <CloseIcon fontSize="small" />
                                </IconButton></PermissionGuard>
                                <PermissionGuard permissions="LEAVE_MANAGE"><IconButton size="small" sx={{ color: '#1976d2' }} onClick={() => handleEdit(rowData)} title={t('general.edit', 'Sửa')}>
                                    <EditIcon fontSize="small" />
                                </IconButton></PermissionGuard>
                            </>
                        ) : (
                            <PermissionGuard permissions="LEAVE_MANAGE"><IconButton size="small" sx={{ color: '#d32f2f' }} onClick={() => handleDeleteClick(rowData)} title={t('general.delete', 'Xóa')}>
                                <DeleteIcon fontSize="small" />
                            </IconButton></PermissionGuard>
                        )}
                    </div>
                );
            }
        },
        {
            title: t('leave.field.staffCode', 'Mã nhân viên'),
            field: 'requestStaffCode',
            width: 130,
            render: (rowData) => <span className="font-semibold">{rowData.requestStaffCode}</span>
        },
        {
            title: t('leave.field.staffName', 'Tên nhân viên'),
            field: 'requestStaffName',
            width: 180,
            render: (rowData) => <span className="font-semibold">{rowData.requestStaffName}</span>
        },
        {
            title: t('leave.field.department', 'Phòng ban'),
            field: 'departmentName',
            width: 150,
            render: (rowData) => <span>{rowData.departmentName || '---'}</span>
        },
        {
            title: t('leave.field.leaveType', 'Loại phép'),
            field: 'leaveType',
            width: 140,
            render: (rowData) => <span>{getLeaveTypeName(rowData.leaveType)}</span>
        },
        {
            title: t('leave.field.fromDate', 'Từ ngày'),
            field: 'fromDate',
            width: 120,
            align: 'center',
            render: (rowData) => <span>{formatDate(rowData.fromDate)}</span>
        },
        {
            title: t('leave.field.toDate', 'Đến ngày'),
            field: 'toDate',
            width: 120,
            align: 'center',
            render: (rowData) => <span>{formatDate(rowData.toDate)}</span>
        },
        {
            title: t('leave.field.totalDays', 'Số ngày'),
            field: 'totalDays',
            width: 90,
            align: 'center',
            render: (rowData) => <span className="font-semibold">{rowData.totalDays}</span>
        },
        {
            title: t('leave.field.remainingAnnualLeave', 'Phép năm còn lại'),
            field: 'remainingAnnualLeaveDays',
            width: 150,
            align: 'center',
            render: (rowData) => <span className="font-semibold">{rowData.remainingAnnualLeaveDays == null ? '—' : `${rowData.remainingAnnualLeaveDays} ngày`}</span>
        },
        {
            title: t('leave.field.status', 'Trạng thái'),
            field: 'approvalStatus',
            width: 120,
            align: 'center',
            render: (rowData) => getStatusChip(rowData.approvalStatus)
        },
        {
            title: t('leave.field.reason', 'Lý do nghỉ'),
            field: 'requestReason',
            width: 200,
            render: (rowData) => <div><span className="line-clamp-2">{rowData.requestReason || '---'}</span>{!!rowData.makeupDates?.length && <small>Làm bù: {rowData.makeupDates.map(date => dayjs(date).format('DD/MM/YYYY')).join(', ')}</small>}</div>
        },
        {
            title: t('leave.field.rejectReason', 'Ý kiến phản hồi'),
            field: 'rejectReason',
            width: 200,
            render: (rowData) => <span className="line-clamp-2">{rowData.rejectReason || '---'}</span>
        }
    ];

    const leaveTypeOptions = [
        { value: '', name: t('general.all', 'Tất cả') },
        { value: 'ANNUAL', name: t('leave.type.annual', 'Phép năm') },
        { value: 'UNPAID', name: t('leave.type.unpaid', 'Phép không lương') },
        { value: 'MAKEUP', name: 'Nghỉ có làm bù' }
    ];

    const approvalStatusOptions = [
        { value: '', name: t('general.all', 'Tất cả') },
        { value: 'PENDING', name: t('leave.status.pending', 'Chờ duyệt') },
        { value: 'APPROVED', name: t('leave.status.approved', 'Đã duyệt') },
        { value: 'REJECTED', name: t('leave.status.rejected', 'Từ chối') }
    ];

    return (
        <>
            <Paper elevation={0} className="p-4 border border-border">
                <Formik
                    innerRef={formikRef}
                    initialValues={{
                        staff: null,
                        leaveType: '',
                        approvalStatus: '',
                        fromDate: '',
                        toDate: '',
                    }}
                    onSubmit={(values) => {
                        const newFilters = {};
                        if (values.staff?.id) newFilters.staffId = values.staff.id;
                        if (values.leaveType) newFilters.leaveType = values.leaveType;
                        if (values.approvalStatus) newFilters.approvalStatus = values.approvalStatus;
                        if (values.fromDate && values.toDate && values.toDate < values.fromDate) { toast.error('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.'); return; }
                        if (values.fromDate) newFilters.fromDate = values.fromDate;
                        if (values.toDate) newFilters.toDate = values.toDate;
                        setFilters(newFilters);
                    }}
                >
                    {(formik) => (
                        <>
                            <ListToolbar
                                searchDraft={searchDraft}
                                onSearchDraftChange={setSearchDraft}
                                onSearch={handleSearch}
                                onReset={handleReset}
                                onAdd={can('LEAVE_MANAGE') ? handleAdd : undefined}
                                addLabel={t('leave.action.createHR', 'Tạo hộ đơn nghỉ')}
                                filter={{
                                    open: filterOpen,
                                    onToggle: setFilterOpen,
                                    activeCount: Object.keys(filters).length
                                }}
                            />

                            <FilterPanel
                                open={filterOpen}
                                onOpenChange={setFilterOpen}
                                onApply={handleApplyFilters}
                                onReset={handleReset}
                            >
                                <Grid container spacing={2}>
                                    <Grid item xs={12} sm={6}><MuiTextField type="date" label="Từ ngày nghỉ" fullWidth size="small" InputLabelProps={{ shrink: true }}
                                        value={formik.values.fromDate} onChange={event => formik.setFieldValue('fromDate', event.target.value)} /></Grid>
                                    <Grid item xs={12} sm={6}><MuiTextField type="date" label="Đến ngày nghỉ" fullWidth size="small" InputLabelProps={{ shrink: true }}
                                        value={formik.values.toDate} onChange={event => formik.setFieldValue('toDate', event.target.value)} /></Grid>
                                    <Grid item xs={12} sm={4}>
                                        <AsyncAutocomplete
                                            name="staff"
                                            label={t('leave.field.staff', 'Nhân viên')}
                                            api={pagingStaffLookup}
                                            searchObject={{ pageIndex: 1, pageSize: 50 }}
                                            displayName="displayName"
                                            formik={formik}
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={4}>
                                        <SelectInput
                                            name="leaveType"
                                            label={t('leave.field.leaveType', 'Loại phép')}
                                            options={leaveTypeOptions}
                                            keyValue="value"
                                            displayvalue="name"
                                            hideNullOption={true}
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={4}>
                                        <SelectInput
                                            name="approvalStatus"
                                            label={t('leave.field.status', 'Trạng thái')}
                                            options={approvalStatusOptions}
                                            keyValue="value"
                                            displayvalue="name"
                                            hideNullOption={true}
                                        />
                                    </Grid>
                                </Grid>
                            </FilterPanel>
                        </>
                    )}
                </Formik>

                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ my: 1 }}>
                    {approvalStatusOptions.map(option => <Chip key={option.value} label={option.name}
                        color={(filters.approvalStatus || '') === option.value ? 'primary' : 'default'}
                        onClick={() => { formikRef.current?.setFieldValue('approvalStatus', option.value); setFilters({ ...filters, approvalStatus: option.value || null }); }} />)}
                </Stack>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ my: 1 }}>
                    {leaveTypeOptions.map(option => <Chip key={option.value} label={option.value ? option.name : 'Tất cả loại phép'}
                        variant={(filters.leaveType || '') === option.value ? 'filled' : 'outlined'}
                        onClick={() => { formikRef.current?.setFieldValue('leaveType', option.value); setFilters({ ...filters, leaveType: option.value || null }); }} />)}
                </Stack>
                <PermissionGuard permissions="LEAVE_APPROVE"><BatchApproval
                    search={searchLeaveRequests} approve={approveLeaveBatch} filters={{ ...filters, keyword }}
                    statusField="approvalStatus" pendingStatus="PENDING" label="đơn nghỉ phép"
                    describe={row => `${row.requestStaffCode} · ${row.requestStaffName} · ${formatDate(row.fromDate)} – ${formatDate(row.toDate)} · ${row.totalDays} ngày`}
                    onComplete={loadRequests}
                    selectedRows={selectedRows} onClearSelection={() => setSelectedRows([])}
                /></PermissionGuard>
                <Table
                    columns={columns}
                    data={requests}
                    selection={can('LEAVE_APPROVE')}
                    handleSelectList={setSelectedRows}
                    options={{ selectionProps: row => ({ disabled: row.approvalStatus !== 'PENDING', inputProps: { 'aria-label': `Chọn đơn nghỉ của ${row.requestStaffName || ''}` } }) }}
                    totalElements={totalElements}
                    page={page}
                    pageSize={pageSize}
                    handleChangePage={(e, p) => setPage(p)}
                    setRowsPerPage={(e) => setPageSize(parseInt(e.target.value, 10))}
                    loading={loading}
                />
            </Paper>

            {openForm && (
                <LeaveRequestForm
                    open={openForm}
                    onClose={() => setOpenForm(false)}
                    requestData={selectedRequest}
                />
            )}

            {/* Hộp thoại Phê duyệt */}
            <ConfirmationDialog
                open={openApproveConfirm}
                onConfirmDialogClose={() => setOpenApproveConfirm(false)}
                onYesClick={confirmApprove}
                title={t('leave.title.approveConfirm', 'Phê duyệt đơn nghỉ phép')}
                text={t('leave.message.approveConfirmText', 'Bạn có chắc chắn muốn phê duyệt đơn nghỉ phép này không?')}
                agree={t('general.confirm', 'Xác nhận')}
                cancel={t('general.cancel', 'Hủy bỏ')}
            />

            {/* Hộp thoại Từ chối kèm lý do */}
            <Dialog open={openRejectDialog} onClose={() => setOpenRejectDialog(false)} fullWidth maxWidth="xs">
                <DialogTitle>{t('leave.title.rejectConfirm', 'Từ chối đơn nghỉ phép')}</DialogTitle>
                <DialogContent>
                    <div className="pt-2">
                        <MuiTextField
                            label={t('leave.field.rejectReason', 'Ý kiến phản hồi / Lý do từ chối')}
                            value={rejectReason}
                            onChange={(e) => setRejectReason(e.target.value)}
                            fullWidth
                            multiline
                            rows={3}
                            variant="outlined"
                            size="small"
                        />
                    </div>
                </DialogContent>
                <DialogActions sx={{ p: 2 }}>
                    <Button onClick={() => setOpenRejectDialog(false)} color="inherit" variant="outlined" sx={{ textTransform: 'none' }}>
                        {t('general.cancel', 'Hủy bỏ')}
                    </Button>
                    <Button onClick={confirmReject} color="primary" variant="contained" sx={{ textTransform: 'none', px: 3 }}>
                        {t('general.confirm', 'Từ chối')}
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Hộp thoại Xóa đơn */}
            <ConfirmationDialog
                open={openDeleteConfirm}
                onConfirmDialogClose={() => setOpenDeleteConfirm(false)}
                onYesClick={confirmDelete}
                title={t('general.deleteConfirm', 'Xác nhận xóa')}
                text={t('leave.message.deleteConfirmText', 'Bạn có chắc chắn muốn xóa đơn nghỉ phép này không? Thao tác này không thể phục hồi.')}
                agree={t('general.confirm', 'Xác nhận')}
                cancel={t('general.cancel', 'Hủy bỏ')}
            />
        </>
    );
};

export default LeaveRequestList;
import dayjs from 'dayjs';
