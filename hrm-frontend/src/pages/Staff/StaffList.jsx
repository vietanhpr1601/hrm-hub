import { usePermissions } from '../../hooks/usePermission';
import PermissionGuard from '../../components/auth/PermissionGuard';
import React, { useEffect, useState, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Grid, IconButton, TextField, Paper } from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';
import AddIcon from '@mui/icons-material/Add';
import FileUploadIcon from '@mui/icons-material/FileUpload';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';

import Table from '../../components/ui/Table';
import StaffForm from './components/StaffForm';
import StaffImportResultDialog from './components/StaffImportResultDialog';
import usePasswordAction from '../../hooks/usePasswordAction';
import useAuthStore from '../../store/useAuthStore';
import ConfirmationDialog from '../../components/ui/ConfirmationDialog';
import useStaffStore from '../../store/staffStore';
import { WorkingStatusOptions, GenderOptions } from '../../constants';
import { getLabelFromOptions, getActiveFilterCount, formatDate } from '../../LocalFunction';
import { downloadStaffImportTemplate, exportStaffs, getDepartments, getPositions, importNewStaffs } from '../../services/StaffService';
import { STAFF_IMPORT_TEMPLATE_NAME } from '../../services/staffImport.js';
import ListToolbar from '../../components/ui/ListToolbar';
import FilterPanel from '../../components/ui/FilterPanel';
import { Formik } from 'formik';
import Autocomplete from '../../components/ui/Autocomplete';

const FAKE_DEPARTMENTS = [
  { id: '1', name: "Phòng Hành chính" },
  { id: '2', name: "Phòng Kỹ thuật" },
  { id: '3', name: "Phòng Kinh doanh" },
  { id: '4', name: "Phòng Nhân sự" },
];

const FAKE_POSITIONS = [
  { id: '1', name: "Giám đốc" },
  { id: '2', name: "Trưởng phòng" },
  { id: '3', name: "Trưởng nhóm" },
  { id: '4', name: "Nhân viên" },
];

const StaffList = () => {
    const can = usePermissions();
    const username = useAuthStore(state => state.user?.username);
    const { runWithPassword, passwordDialog } = usePasswordAction(username);
    const navigate = useNavigate();
    const [openConfirm, setOpenConfirm] = useState(false);
    const [departments, setDepartments] = useState([]);
    const [positions, setPositions] = useState([]);
    const [filterOpen, setFilterOpen] = useState(false);
    const [importResult, setImportResult] = useState(null);

    const { 
        staffs, 
        loadStaffs, 
        removeStaff, 
        page, 
        setPage, 
        pageSize, 
        setPageSize, 
        totalElements, 
        setOpenForm, 
        openForm, 
        setSelectedStaff, 
        selectedStaff,
        keyword,
        setKeyword,
        filters,
        setFilters,
    } = useStaffStore();

    const formikRef = useRef();
    const [searchDraft, setSearchDraft] = useState(keyword || '');

    useEffect(() => {
        const fetchRefs = async () => {
            try {
                const depRes = await getDepartments();
                setDepartments(depRes?.data?.length ? depRes.data : FAKE_DEPARTMENTS);
                const posRes = await getPositions();
                setPositions(posRes?.data?.length ? posRes.data : FAKE_POSITIONS);
            } catch (err) {
                console.error("Failed to load filter metadata:", err);
                setDepartments(FAKE_DEPARTMENTS);
                setPositions(FAKE_POSITIONS);
            }
        };
        fetchRefs();
    }, []);

    useEffect(() => {
        setSearchDraft(keyword || '');
    }, [keyword]);

    useEffect(() => {
        loadStaffs();
    }, [page, pageSize, keyword, filters]);

    const handleAdd = () => {
        setSelectedStaff(null);
        setOpenForm(true);
    };

    const handleView = (staff) => {
        navigate(`/staff/${staff.id}`, { state: { isView: true } });
    };

    const handleEdit = (staff) => {
        navigate(`/staff/${staff.id}`, { state: { isEdit: true } });
    };

    const handleDelete = (staff) => {
        setSelectedStaff(staff);
        setOpenConfirm(true);
    };

    const confirmDelete = async () => {
        if (selectedStaff?.id) {
            await removeStaff(selectedStaff.id);
            setSelectedStaff(null);
            setOpenConfirm(false);
        }
    };

    const handleSearch = () => {
        setKeyword(searchDraft);
        if (filterOpen) {
            formikRef.current?.handleSubmit();
        }
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

    const initialDepartment = useMemo(() => {
        return departments.find(d => d.id === filters.departmentId) || null;
    }, [departments, filters.departmentId]);

    const initialPosition = useMemo(() => {
        return positions.find(p => p.id === filters.positionId) || null;
    }, [positions, filters.positionId]);

    const activeFilterCount = useMemo(() => {
        return getActiveFilterCount(filters);
    }, [filters]);

    const importOpts = useMemo(() => [
        {
            label: 'Nhập Excel nhân viên mới',
            onImport: async (file) => {
                setImportResult(null);
                try {
                    const result = await runWithPassword({ title: 'Xác nhận nhập Excel', description: file.name,
                        action: password => importNewStaffs(file, password) });
                    await loadStaffs();
                    setImportResult({ ...result, success: true });
                } catch (error) {
                    const result = error?.response?.data?.data;
                    if (Array.isArray(result?.errors) && result.errors.length > 0) {
                        setImportResult({ ...result, success: false });
                    }
                    throw error;
                }
            }
        }
    ], [loadStaffs, runWithPassword]);

    const templateOpts = useMemo(() => [
        {
            label: 'Tải mẫu nhập',
            fileName: STAFF_IMPORT_TEMPLATE_NAME,
            onDownload: downloadStaffImportTemplate,
        }
    ], []);

    const handleExport = async () => {
        return runWithPassword({ title: 'Xác nhận xuất Excel', description: 'Xuất danh sách nhân viên theo bộ lọc hiện tại.',
            action: password => exportStaffs({ keyword, ...filters }, password) });
    };

    const columns = [
        { 
            title: 'Thao tác', 
            field: 'actions',
            width: 140,
            render: (rowData) => (
                <div className="flex items-center space-x-0">
                    <IconButton size="small" sx={{ color: '#1976d2' }} onClick={() => handleView(rowData)}><VisibilityIcon fontSize="small" /></IconButton>
                    <PermissionGuard permissions="STAFF_UPDATE"><IconButton size="small" sx={{ color: '#1976d2' }} onClick={() => handleEdit(rowData)}><EditIcon fontSize="small" /></IconButton></PermissionGuard>
                    <PermissionGuard permissions="STAFF_DELETE"><IconButton size="small" sx={{ color: '#d32f2f' }} onClick={() => handleDelete(rowData)}><DeleteIcon fontSize="small" /></IconButton></PermissionGuard>
                    {/* <IconButton size="small"><MoreHorizIcon fontSize="small" /></IconButton> */}
                </div>
            )
        },
        { 
            title: 'Mã nhân viên', 
            field: 'staffCode', 
            width: 130,
        },
        { 
            title: 'Nhân viên', 
            field: 'displayName',
            width: 250,
            render: (rowData) => (
                <div className="py-1">
                    <div className="font-bold leading-tight whitespace-nowrap">{rowData.displayName}</div>
                    <div className="mt-1 whitespace-nowrap">Ngày sinh: {formatDate(rowData.birthDate) || '---'}</div>
                    <div className="whitespace-nowrap">Ngày vào làm: {formatDate(rowData.startDate) || '---'}</div>
                    <div>Giới tính: {getLabelFromOptions(GenderOptions, rowData.gender) || '---'}</div>
                </div>
            )
        },
        { 
            title: 'Thông tin liên hệ', 
            width: 250,
            render: (rowData) => (
                <div className="py-1">
                    <div>SĐT: {rowData.phoneNumber || '---'}</div>
                    <div className="truncate max-w-[230px]">Email: {rowData.email || '---'}</div>
                </div>
            )
        },
        { 
            title: 'Trạng thái nhân viên', 
            field: 'workingStatus',
            align: 'center',
            width: 150,
            render: (rowData) => (
                <span>
                    {getLabelFromOptions(WorkingStatusOptions, rowData.workingStatus)}
                </span>
            )
        },
        // { 
        //     title: 'Đơn vị', 
        //     width: 150,
        //     render: () => <span className="uppercase whitespace-nowrap">Thẩm mỹ Linh Anh</span> 
        // },
        { 
            title: 'Phòng ban', 
            align: 'center',
            width: 180,
            render: (rowData) => <span className="whitespace-nowrap">{rowData.departmentName || '---'}</span> 
        },
        { 
            title: 'Vị trí', 
            width: 180,
            align: 'center',
            render: (rowData) => <span className="whitespace-nowrap">{rowData.positionName || '---'}</span> 
        },
        { 
            title: 'Quản lý trực tiếp', 
            width: 200,
            render: (rowData) => (
                <div className="leading-tight">
                    - {rowData.managerName || 'Chưa xác định'}
                </div>
            )
        },
        { 
            title: 'Nơi ở hiện tại', 
            width: 250,
            render: (rowData) => <span className="leading-tight">{rowData.currentAddress || '---'}</span> 
        },
        { 
            title: 'Mã số BHXH', 
            field: 'socialInsuranceCode',
            width: 150,
            render: (rowData) => <span>{rowData.socialInsuranceCode || '---'}</span>
        },
        { 
            title: 'Trạng thái hồ sơ', 
            width: 150,
            render: () => <span className="text-[12px] text-green-600">---</span>
        },
        { 
            title: 'Tên đăng nhập', 
            width: 180,
            render: (rowData) => (
                <div className="flex items-center space-x-2">
                    <span>{rowData.username || '---'}</span>
                    {rowData.username && <span>✔</span>}
                </div>
            )
        },
        { 
            title: 'Cấp bậc', 
            field: 'level',
            width: 100,
            render: (rowData) => <span className="text-[12px] font-bold">{rowData.level || '---'}</span> 
        },
    ];

    return (
        <>
            <Paper elevation={0} className="p-3 sm:p-4 border border-border">
                <Formik
                    innerRef={formikRef}
                    initialValues={{
                        department: initialDepartment,
                        position: initialPosition
                    }}
                    enableReinitialize={true}
                    onSubmit={(values) => {
                        setFilters({
                            departmentId: values.department?.id || '',
                            positionId: values.position?.id || ''
                        });
                    }}
                >
                    {({ values, setFieldValue }) => (
                        <>
                            {/* Toolbar & Advanced Filter */}
                            <ListToolbar
                                searchDraft={searchDraft}
                                onSearchDraftChange={setSearchDraft}
                                onSearch={handleSearch}
                                onReset={handleReset}
                                onAdd={can('STAFF_CREATE') ? handleAdd : undefined}
                                addLabel="Thêm mới"
                                filter={{
                                    open: filterOpen,
                                    onToggle: setFilterOpen,
                                    activeCount: activeFilterCount
                                }}
                                downloadTemplateOptions={can('STAFF_IMPORT') ? templateOpts : []}
                                importOptions={can('STAFF_IMPORT') ? importOpts : []}
                                importAccept=".xlsx"
                                onExport={can('STAFF_EXPORT') ? handleExport : undefined}
                                exportFileName="Danh_sach_nhan_vien.xlsx"
                            />

                            <FilterPanel
                                open={filterOpen}
                                onOpenChange={setFilterOpen}
                                onApply={handleApplyFilters}
                                onReset={handleReset}
                            >
                                <Grid container spacing={2}>
                                    <Grid item xs={12} sm={6}>
                                        <Autocomplete
                                            name="department"
                                            label="Phòng ban"
                                            options={departments}
                                            getOptionLabel={(option) => option?.name || ''}
                                            onChange={(event, val) => {
                                                setFieldValue('department', val);
                                                if (val && values.position) {
                                                    const posFull = positions.find(p => p.id === values.position.id);
                                                    if (posFull && posFull.department?.id !== val.id) {
                                                        setFieldValue('position', null);
                                                    }
                                                } else if (!val) {
                                                    setFieldValue('position', null);
                                                }
                                            }}
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={6}>
                                        <Autocomplete
                                            name="position"
                                            label="Vị trí"
                                            options={
                                                values.department?.id
                                                    ? positions.filter(pos => pos.department?.id === values.department.id)
                                                    : positions
                                            }
                                            getOptionLabel={(option) => option?.name || ''}
                                        />
                                    </Grid>
                                </Grid>
                            </FilterPanel>
                        </>
                    )}
                </Formik>

                {can('STAFF_IMPORT') && <Alert severity="info" sx={{ mb: 2 }}>
                    <strong>Nhập nhân viên mới từ Excel:</strong> chọn Tải mẫu, điền dữ liệu rồi chọn Nhập Excel.
                    Bắt buộc: Họ và tên, Email, Trạng thái nhân viên. Để trống Mã nhân viên để hệ thống tự tạo.
                    Ngày nhập theo dd/MM/yyyy hoặc yyyy-MM-dd; Phòng ban và Chức vụ dùng mã hoặc tên đang có trong hệ thống.
                    Mỗi tệp .xlsx tối đa 1 MB và 1.000 dòng dữ liệu. Nếu có lỗi, toàn bộ tệp sẽ chưa được lưu.
                </Alert>}

                {/* Table */}
                <Table 
                    columns={columns} 
                    data={staffs} 
                    totalElements={totalElements}
                    page={page}
                    pageSize={pageSize}
                    handleChangePage={(e, p) => setPage(p)}
                    setRowsPerPage={(e) => setPageSize(parseInt(e.target.value, 10))}
                />
            </Paper>

            {openForm && (
                <StaffForm 
                    open={openForm} 
                    onClose={() => setOpenForm(false)} 
                    staffData={selectedStaff}
                    onSaveSuccess={() => {
                        setOpenForm(false);
                    }}
                />
            )}

            <ConfirmationDialog
                open={openConfirm}
                onConfirmDialogClose={() => setOpenConfirm(false)}
                onYesClick={confirmDelete}
                title="Xác nhận xóa"
                text="Bạn có chắc chắn muốn xóa nhân viên này không? Hành động này không thể hoàn tác."
                agree="Xác nhận"
                cancel="Hủy bỏ"
            />
            <StaffImportResultDialog result={importResult} onClose={() => setImportResult(null)} />
            {passwordDialog}
        </>
    );
};

export default StaffList;
