import AttendanceDetails from '../Timekeeping/components/AttendanceDetails';
import BatchApproval from '../../components/ui/BatchApproval';
import { searchTimesheets, approveTimesheetBatch } from '../../services/timesheetService';
import PermissionGuard from '../../components/auth/PermissionGuard';
import usePermission from '../../hooks/usePermission';
import React, { useEffect, useState, useMemo, useRef } from 'react';
import { 
    Button, Grid, IconButton, Paper, Typography, Box, 
    Chip, Tooltip, Stack
} from '@mui/material';
import { 
    CheckCircle, Cancel, Visibility, Search, CheckCircleOutline,
    Close, FilterList
} from '@mui/icons-material';
import { Formik } from 'formik';
import dayjs from 'dayjs';
import { toast } from 'sonner';

import Table from '../../components/ui/Table';
import ListToolbar from '../../components/ui/ListToolbar';
import FilterPanel from '../../components/ui/FilterPanel';
import Autocomplete from '../../components/ui/Autocomplete';
import useTimesheetStore from '../../store/useTimesheetStore';
import { getDepartments, getStaffLookup } from '../../services/StaffService';
import { getAllPeriods } from '../../services/periodService';
import { getLabelFromOptions } from '../../LocalFunction';
import ConfirmationDialog from '../../components/ui/ConfirmationDialog';
import TextField from '../../components/ui/TextField';
import DateTimePicker from '../../components/ui/DateTimePicker';
import Popup from '../../components/ui/Popup';

const TIMESHEET_STATUS_OPTIONS = [
    { id: 'DRAFT', name: 'Nháp' },
    { id: 'SUBMITTED', name: 'Chờ duyệt' },
    { id: 'APPROVED', name: 'Đã duyệt' },
    { id: 'REJECTED', name: 'Từ chối' }
];

// Helper to format shift time (removing seconds, e.g. "08:00:00" -> "08:00")
const formatShiftTime = (timeStr) => {
    if (!timeStr) return '';
    return timeStr.length >= 5 ? timeStr.substring(0, 5) : timeStr;
};

const TimesheetApprovalList = () => {
    const {
        timesheets,
        loading,
        page,
        setPage,
        pageSize,
        setPageSize,
        totalElements,
        filters,
        setFilters,
        resetFilters,
        loadTimesheets,
        updateTimesheetStatus,
    } = useTimesheetStore();

    // Dialog state for Approval / Rejection
    const [approvalOpen, setApprovalOpen] = useState(false);
    const [selectedTimesheet, setSelectedTimesheet] = useState(null);
    const [approvalStatus, setApprovalStatus] = useState('APPROVED');
    const [submitting, setSubmitting] = useState(false);

    // Dialog state for raw logs details
    const [detailOpen, setDetailOpen] = useState(false);
    const [detailTimesheet, setDetailTimesheet] = useState(null);

    // Ticked rows (only pending ones can be ticked) for "Duyệt mục đã chọn"
    const canApprove = usePermission('ATTENDANCE_APPROVE');
    const [selectedRows, setSelectedRows] = useState([]);
    useEffect(() => { setSelectedRows([]); }, [timesheets]);



    // Filter metadata
    const [departments, setDepartments] = useState([]);
    const [staffList, setStaffList] = useState([]);
    const [periods, setPeriods] = useState([]);
    const [filterOpen, setFilterOpen] = useState(false);
    const [searchDraft, setSearchDraft] = useState('');

    const formikRef = useRef();
    const formikRefPopup = useRef();

    // Fetch filters metadata
    useEffect(() => {
        const fetchRefs = async () => {
            try {
                const depRes = await getDepartments();
                setDepartments(depRes?.data || []);
                const staffRes = await getStaffLookup();
                setStaffList(staffRes?.data || []);
                const periodRes = await getAllPeriods();
                if (periodRes && periodRes.data) {
                    const dataList = periodRes.data.data || periodRes.data || [];
                    setPeriods(Array.isArray(dataList) ? dataList : []);
                }
            } catch (err) {
                console.error("Failed to load filter metadata:", err);
            }
        };
        fetchRefs();
    }, []);

    // Load timesheets when parameters change
    useEffect(() => {
        loadTimesheets();
    }, [page, pageSize, filters]);

    // Handlers
    const handleOpenApproval = (timesheet, status) => {
        setSelectedTimesheet(timesheet);
        setApprovalStatus(status);
        setApprovalOpen(true);
    };

    const handleConfirmApproval = async (note) => {
        if (!selectedTimesheet) return;
        setSubmitting(true);
        try {
            await updateTimesheetStatus(selectedTimesheet.id, approvalStatus, note);
            toast.success(`Đã cập nhật trạng thái ngày công sang: ${approvalStatus === 'APPROVED' ? 'Đã duyệt' : 'Từ chối'}`);
            setApprovalOpen(false);
            setSelectedTimesheet(null);
        } catch (err) {
            console.error(err);
            toast.error("Có lỗi xảy ra khi cập nhật trạng thái ngày công");
        } finally {
            setSubmitting(false);
        }
    };

    const handleOpenDetails = (timesheet) => {
        setDetailTimesheet(timesheet);
        setDetailOpen(true);
    };

    const handleApplyFilters = () => {
        formikRef.current?.handleSubmit();
    };

    const handleResetFilters = () => {
        formikRef.current?.resetForm();
        resetFilters();
        setSearchDraft('');
    };

    // Auto-calculating active filters count
    const activeFilterCount = useMemo(() => {
        let count = 0;
        if (filters.departmentId) count++;
        if (filters.staffId) count++;
        if (filters.status) count++;
        if (filters.fromDate) count++;
        if (filters.toDate) count++;
        if (filters.periodId) count++;
        return count;
    }, [filters]);

    // Initial values for Autocompletes
    const initialDepartment = useMemo(() => {
        return departments.find(d => d.id === filters.departmentId) || null;
    }, [departments, filters.departmentId]);

    const initialStaff = useMemo(() => {
        return staffList.find(s => s.id === filters.staffId) || null;
    }, [staffList, filters.staffId]);

    const initialStatus = useMemo(() => {
        return TIMESHEET_STATUS_OPTIONS.find(s => s.id === filters.status) || null;
    }, [filters.status]);

    const initialPeriod = useMemo(() => {
        return (periods || []).find(p => p.id === filters.periodId) || null;
    }, [periods, filters.periodId]);

    // Table Columns
    const columns = [
        {
            title: 'Thao tác',
            field: 'actions',
            width: 150,
            render: (rowData) => (
                <div className="flex items-center space-x-1">
                    <Tooltip title="Xem chi tiết chấm công" arrow>
                        <IconButton 
                            size="small" 
                            color="info" 
                            onClick={() => handleOpenDetails(rowData)}
                        >
                            <Visibility fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    {rowData.status !== 'APPROVED' && (
                        <PermissionGuard permissions="ATTENDANCE_APPROVE"><Tooltip title="Duyệt công" arrow>
                            <IconButton 
                                size="small" 
                                color="success" 
                                onClick={() => handleOpenApproval(rowData, 'APPROVED')}
                            >
                                <CheckCircle fontSize="small" />
                            </IconButton>
                        </Tooltip></PermissionGuard>
                    )}
                    {rowData.status !== 'REJECTED' && (
                        <PermissionGuard permissions="ATTENDANCE_APPROVE"><Tooltip title="Từ chối công" arrow>
                            <IconButton 
                                size="small" 
                                color="error" 
                                onClick={() => handleOpenApproval(rowData, 'REJECTED')}
                            >
                                <Cancel fontSize="small" />
                            </IconButton>
                        </Tooltip></PermissionGuard>
                    )}
                </div>
            )
        },
        {
            title: 'Mã NV',
            field: 'staffCode',
            width: 100,
            render: (rowData) => <span>{rowData.staffCode || '---'}</span>
        },
        {
            title: 'Nhân viên',
            field: 'staffName',
            minWidth: 150,
            width: 150,
            align: 'center',
            render: (rowData) => (
                <Typography className="font-semibold text-sm">{rowData.staffName || '---'}</Typography>
            )
        },
        {
            title: 'Phòng ban',
            field: 'departmentName',
            width: 150,
            align: 'center',
            render: (rowData) => <span>{rowData.departmentName || '---'}</span>
        },
        {
            title: 'Vị trí',
            field: 'positionName',
            align: 'center',
            width: 130,
            render: (rowData) => <span>{rowData.positionName || '---'}</span>
        },
        {
            title: 'Ngày làm việc',
            field: 'workingDate',
            align: 'center',
            width: 120,
            render: (rowData) => <span>{dayjs(rowData.workingDate).format('DD/MM/YYYY')}</span>
        },
        {
            title: 'Ca áp dụng',
            width: 170,
            render: (rowData) => {
                const details = rowData.details || [];
                if (details.length === 0) return '---';
                return (
                    <div className="space-y-0.5 text-xs">
                        {details.map((d, idx) => d.shift && (
                            <div key={idx} className="whitespace-nowrap">
                                {d.shift.name} ({formatShiftTime(d.shift.startTime)} - {formatShiftTime(d.shift.endTime)})
                            </div>
                        ))}
                    </div>
                );
            }
        },
        {
            title: 'Giờ vào/ra',
            width: 140,
            align: 'center',
            render: (rowData) => {
                let minCheckIn = null;
                let maxCheckOut = null;
                const details = rowData.details || [];
                details.forEach(d => {
                    if (d.checkInTime) {
                        const ci = dayjs(d.checkInTime);
                        if (!minCheckIn || ci.isBefore(minCheckIn)) {
                            minCheckIn = ci;
                        }
                    }
                    if (d.checkOutTime) {
                        const co = dayjs(d.checkOutTime);
                        if (!maxCheckOut || co.isAfter(maxCheckOut)) {
                            maxCheckOut = co;
                        }
                    }
                });
                return (
                    <div className="text-xs">
                        <div>Vào: <span className="font-bold text-gray-700">{minCheckIn ? minCheckIn.format('HH:mm') : '--:--'}</span></div>
                        <div>Ra: <span className="font-bold text-gray-700">{maxCheckOut ? maxCheckOut.format('HH:mm') : '--:--'}</span></div>
                    </div>
                );
            }
        },
        {
            title: 'Công',
            field: 'totalWorkRatio',
            align: 'center',
            width: 80,
            render: (rowData) => <span className="font-bold text-emerald-600">{rowData.totalWorkRatio || 0}</span>
        },
        {
            title: 'Số giờ làm',
            width: 130,
            align: 'center',
            render: (rowData) => {
                let totalHours = 0;
                const details = rowData.details || [];
                details.forEach(d => {
                    if (d.checkInTime && d.checkOutTime) {
                        const diffHours = dayjs(d.checkOutTime).diff(dayjs(d.checkInTime), 'hour', true);
                        if (diffHours > 0) {
                            totalHours += diffHours;
                        }
                    }
                });

                const displayHours = (rowData.standardHours !== undefined && rowData.standardHours !== null && rowData.overtimeHours !== undefined && rowData.overtimeHours !== null)
                    ? (rowData.standardHours + rowData.overtimeHours)
                    : totalHours;

                return (
                    <span className="font-bold text-gray-700 dark:text-gray-300 text-sm">
                        {displayHours > 0 ? `${displayHours.toFixed(2)}` : '0'}
                    </span>
                );
            }
        },
        {
            title: 'Trạng thái',
            field: 'status',
            width: 120,
            render: (rowData) => {
                switch (rowData.status) {
                    case 'APPROVED':
                        return <Chip label="Đã duyệt" size="small" color="success" variant="outlined" />;
                    case 'SUBMITTED':
                        return <Chip label="Chờ duyệt" size="small" color="warning" variant="outlined" />;
                    case 'REJECTED':
                        return <Chip label="Từ chối" size="small" color="error" variant="outlined" />;
                    default:
                        return <Chip label="Nháp" size="small" color="default" variant="outlined" />;
                }
            }
        },
        {
            title: 'Ghi chú duyệt',
            field: 'note',
            align: 'center',
            width: 180,
            render: (rowData) => <span className="text-xs text-gray-500">{rowData.note || '---'}</span>
        }
    ];

    return (
        <Box className="space-y-6">
            <Paper elevation={0} className="p-4 border border-border rounded-xl shadow-sm">
                <Formik
                    innerRef={formikRef}
                    initialValues={{
                        department: initialDepartment,
                        staff: initialStaff,
                        status: initialStatus,
                        period: initialPeriod,
                        fromDate: filters.fromDate ? new Date(filters.fromDate) : null,
                        toDate: filters.toDate ? new Date(filters.toDate) : null
                    }}
                    enableReinitialize={true}
                    onSubmit={(values) => {
                        setFilters({
                            departmentId: values.department?.id || null,
                            staffId: values.staff?.id || null,
                            status: values.status?.id || null,
                            periodId: values.period?.id || null,
                            fromDate: values.fromDate ? dayjs(values.fromDate).format('YYYY-MM-DD') : null,
                            toDate: values.toDate ? dayjs(values.toDate).format('YYYY-MM-DD') : null
                        });
                    }}
                >
                    {({ values, setFieldValue }) => (
                        <>
                            {/* Toolbar and filter toggle */}
                            <ListToolbar
                                searchDraft={searchDraft}
                                onSearchDraftChange={setSearchDraft}
                                onSearch={() => setFilters({ keyword: searchDraft })}
                                onReset={handleResetFilters}
                                showAdd={false}
                                filter={{
                                    open: filterOpen,
                                    onToggle: setFilterOpen,
                                    activeCount: activeFilterCount
                                }}
                            />

                            {/* Filters drawer panel */}
                            <FilterPanel
                                open={filterOpen}
                                onOpenChange={setFilterOpen}
                                onApply={handleApplyFilters}
                                onReset={handleResetFilters}
                            >
                                <Grid container spacing={2}>
                                    <Grid item xs={12} sm={4}>
                                        <Autocomplete
                                            name="department"
                                            label="Phòng ban"
                                            options={departments}
                                            getOptionLabel={(option) => option?.name || ''}
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={4}>
                                        <Autocomplete
                                            name="staff"
                                            label="Nhân viên"
                                            options={staffList}
                                            getOptionLabel={(option) => option ? `${option.displayName} (${option.staffCode})` : ''}
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={4}>
                                        <Autocomplete
                                            name="status"
                                            label="Trạng thái duyệt"
                                            options={TIMESHEET_STATUS_OPTIONS}
                                            getOptionLabel={(option) => option?.name || ''}
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={4}>
                                        <Autocomplete
                                            name="period"
                                            label="Kỳ lương"
                                            options={periods}
                                            getOptionLabel={(option) => option?.name || ''}
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={4}>
                                        <DateTimePicker
                                            label="Từ ngày"
                                            name="fromDate"
                                            notValueMillisecond={true}
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={4}>
                                        <DateTimePicker
                                            label="Đến ngày"
                                            name="toDate"
                                            notValueMillisecond={true}
                                        />
                                    </Grid>
                                </Grid>
                            </FilterPanel>
                        </>
                    )}
                </Formik>

                {/* Table */}
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ my: 1 }}>
                    {[{ id: '', name: 'Tất cả' }, ...TIMESHEET_STATUS_OPTIONS].map(option => <Chip key={option.id} label={option.name}
                        color={(filters.status || '') === option.id ? 'primary' : 'default'}
                        onClick={() => setFilters({ status: option.id || null })} />)}
                </Stack>
                <PermissionGuard permissions="ATTENDANCE_APPROVE"><BatchApproval
                    search={searchTimesheets} approve={approveTimesheetBatch} filters={filters}
                    statusField="status" pendingStatus="SUBMITTED" label="ngày công"
                    describe={row => `${row.staffCode} · ${row.staffName} · ${dayjs(row.workingDate).format('DD/MM/YYYY')} · ${row.totalWorkRatio || 0} công`}
                    onComplete={loadTimesheets}
                    selectedRows={selectedRows} onClearSelection={() => setSelectedRows([])}
                /></PermissionGuard>
                <Table
                    columns={columns}
                    data={timesheets}
                    selection={canApprove}
                    handleSelectList={setSelectedRows}
                    options={{ selectionProps: row => ({ disabled: row.status !== 'SUBMITTED', inputProps: { 'aria-label': `Chọn ${row.staffName || ''} ngày ${row.workingDate ? dayjs(row.workingDate).format('DD/MM/YYYY') : ''}` } }) }}
                    totalElements={totalElements}
                    page={page}
                    pageSize={pageSize}
                    handleChangePage={(e, p) => setPage(p)}
                    setRowsPerPage={(e) => setPageSize(parseInt(e.target.value, 10))}
                    loading={loading}
                />
            </Paper>

            {/* Approval / Rejection Note Dialog using shared ConfirmationDialog component */}
            <ConfirmationDialog
                open={approvalOpen}
                onConfirmDialogClose={() => setApprovalOpen(false)}
                title={approvalStatus === 'APPROVED' ? 'Xác nhận duyệt chấm công' : 'Xác nhận từ chối chấm công'}
                agree="Xác nhận"
                cancel="Hủy bỏ"
                onYesClick={() => {
                    formikRefPopup.current?.handleSubmit();
                }}
                disabled={submitting}
                text={`Bạn đang ${approvalStatus === 'APPROVED' ? 'duyệt chấm công' : 'từ chối chấm công'} cho nhân viên ${selectedTimesheet?.staffName || ''} (${selectedTimesheet?.staffCode || ''}) ngày ${selectedTimesheet ? dayjs(selectedTimesheet.workingDate).format('DD/MM/YYYY') : ''}.`}
            >
                {approvalOpen && (
                    <Formik
                        innerRef={formikRefPopup}
                        initialValues={{
                            note: ''
                        }}
                        onSubmit={(values) => {
                            handleConfirmApproval(values.note);
                        }}
                    >
                        {() => (
                            <Box>
                                <TextField
                                    label="Ghi chú / Lý do (không bắt buộc)"
                                    name="note"
                                    multiline
                                    rows={3}
                                    fullWidth
                                    size="small"
                                />
                            </Box>
                        )}
                    </Formik>
                )}
            </ConfirmationDialog>

            {/* Detailed logs view */}
            <Popup
                open={detailOpen}
                onClosePopup={() => setDetailOpen(false)}
                title={detailTimesheet ? `Chi tiết chấm công: ${detailTimesheet.staffName || ''} (Ngày ${dayjs(detailTimesheet.workingDate).format('DD/MM/YYYY')})` : ''}
                size="sm"
                action={
                    <Button 
                        onClick={() => setDetailOpen(false)} 
                        variant="contained" 
                        className="bg-gray-700 text-white rounded-lg hover:bg-gray-800 font-bold px-5 py-1.5"
                    >
                        Đóng
                    </Button>
                }
            >
                {detailTimesheet && <AttendanceDetails timesheet={detailTimesheet}  />}
            </Popup>

        </Box>
    );
};

export default TimesheetApprovalList;
