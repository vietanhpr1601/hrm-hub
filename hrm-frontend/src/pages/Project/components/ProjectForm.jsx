import React, { useMemo } from 'react';
import { useFormik, FormikProvider } from 'formik';
import * as Yup from 'yup';
import { Grid, Button, Alert, IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import useProjectStore from '../../../store/useProjectStore';
import TextField from '../../../components/ui/TextField';
import SelectInput from '../../../components/ui/SelectInput';
import DateTimePicker from '../../../components/ui/DateTimePicker';
import PagingAutocomplete from '../../../components/ui/PagingAutocomplete';
import Popup from '../../../components/ui/Popup';
import { pagingStaffs } from '../../../services/StaffService';
import { ProjectRoleOptions } from '../../../constants';
import { format } from 'date-fns';
import { toast } from 'sonner';

const ProjectForm = ({ open, onClose, projectData, onSaveSuccess }) => {
    const { addProject, modifyProject } = useProjectStore();
    const isNew = !projectData?.id;

    const initialValues = useMemo(() => ({
        id: projectData?.id || null,
        name: projectData?.name || '',
        code: projectData?.code || '',
        description: projectData?.description || '',
        isPublic: projectData?.isPublic ?? false,
        startDate: projectData?.startDate ? new Date(projectData.startDate) : new Date(),
        endDate: projectData?.endDate ? new Date(projectData.endDate) : null,
        companyName: projectData?.companyName || '',
        progress: projectData?.progress ?? 0,
        selectedStaffs: [],
        staffs: [],
    }), [projectData]);

    const validationSchema = Yup.object({
        name: Yup.string().required('Tên dự án là bắt buộc'),
    });

    const formik = useFormik({
        initialValues: initialValues,
        enableReinitialize: true,
        validationSchema: validationSchema,
        onSubmit: async (values) => {
            const { selectedStaffs, staffs, ...rest } = values;
            const submitValues = { ...rest, progress: Math.min(100, Math.max(0, Number(values.progress) || 0)) };
            if (values.startDate) {
                submitValues.startDate = format(new Date(values.startDate), 'yyyy-MM-dd');
            }
            if (values.endDate) {
                submitValues.endDate = format(new Date(values.endDate), 'yyyy-MM-dd');
            }

            let saved = null;
            if (values.id) {
                await modifyProject(values.id, submitValues);
            } else {
                // Phân công luôn khi tạo dự án: ai làm, quyền gì, vai trò gì.
                submitValues.staffs = staffs.map(member => ({
                    staffId: member.staffId,
                    projectRole: member.projectRole || 'MEMBER',
                    roleName: member.roleName || '',
                }));
                try {
                    saved = await addProject(submitValues);
                } catch (error) {
                    toast.error(error?.response?.data?.message || 'Không tạo được dự án. Kiểm tra lại mã dự án và thông tin đã nhập.');
                    return;
                }
            }
            if (onSaveSuccess) onSaveSuccess(saved);
        },
    });

    // Đồng bộ danh sách người được chọn với bảng phân công (giữ quyền/vai trò đã nhập).
    const handleSelectedStaffChange = (selected) => {
        const chosen = selected || [];
        const kept = formik.values.staffs.filter(member => chosen.some(staff => staff.id === member.staffId));
        chosen.forEach(staff => {
            if (!kept.some(member => member.staffId === staff.id)) {
                kept.push({ staffId: staff.id, staffCode: staff.staffCode, displayName: staff.displayName, projectRole: 'MEMBER', roleName: '' });
            }
        });
        formik.setFieldValue('staffs', kept);
        formik.setFieldValue('selectedStaffs', chosen);
    };
    const handleRemove = (index) => {
        const removed = formik.values.staffs[index];
        formik.setFieldValue('staffs', formik.values.staffs.filter((_, i) => i !== index));
        formik.setFieldValue('selectedStaffs', formik.values.selectedStaffs.filter(staff => staff.id !== removed.staffId));
    };

    const action = (
        <>
            <Button onClick={onClose} variant="outlined" color="inherit" sx={{ color: 'text.secondary', textTransform: 'none' }}>Hủy bỏ</Button>
            <Button onClick={formik.handleSubmit} color="primary" variant="contained" sx={{ textTransform: 'none', px: 4, ml: 1 }}>Lưu lại</Button>
        </>
    );

    return (
        <Popup
            open={open}
            onClosePopup={onClose}
            title={projectData ? 'Chỉnh sửa dự án' : 'Thêm mới dự án'}
            size={isNew ? 'md' : 'sm'}
            action={action}
        >
            <FormikProvider value={formik}>
                <Grid container spacing={2} className='pb-0'>
                    <Grid item xs={12} sm={8}>
                        <TextField
                            label="Tên dự án"
                            name="name"
                            required
                        />
                    </Grid>
                    <Grid item xs={12} sm={4}>
                        <TextField
                            label="Mã dự án"
                            name="code"
                            required
                        />
                    </Grid>
                    <Grid item xs={12} sm={8}>
                        <TextField
                            label="Công ty / Khách hàng"
                            name="companyName"
                            placeholder="VD: Công ty CP ABC"
                        />
                    </Grid>
                    <Grid item xs={12} sm={4}>
                        <TextField
                            label="Tiến độ (%)"
                            name="progress"
                            type="number"
                            inputProps={{ min: 0, max: 100 }}
                        />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                        <DateTimePicker
                            label="Ngày bắt đầu"
                            name="startDate"
                            notValueMillisecond={true}
                        />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                        <DateTimePicker
                            label="Ngày kết thúc"
                            name="endDate"
                            notValueMillisecond={true}
                        />
                    </Grid>
                    {isNew && (
                        <>
                            <Grid item xs={12}>
                                <PagingAutocomplete
                                    multiple
                                    name="selectedStaffs"
                                    label="Người tham gia dự án"
                                    api={pagingStaffs}
                                    getOptionLabel={(option) => option ? `${option.displayName || ''} (${option.staffCode || ''})` : ''}
                                    onChange={(event, selectedOptions) => handleSelectedStaffChange(selectedOptions)}
                                />
                            </Grid>
                            {formik.values.staffs.length > 0 && (
                                <Grid item xs={12}>
                                    <TableContainer component={Paper} className="border border-border shadow-none">
                                        <Table size="small">
                                            <TableHead>
                                                <TableRow>
                                                    <TableCell className="font-bold">Nhân viên</TableCell>
                                                    <TableCell className="font-bold" style={{ width: 170 }}>Quyền</TableCell>
                                                    <TableCell className="font-bold" style={{ width: 210 }}>Vai trò trong dự án</TableCell>
                                                    <TableCell className="font-bold" align="center" style={{ width: 60 }} />
                                                </TableRow>
                                            </TableHead>
                                            <TableBody>
                                                {formik.values.staffs.map((member, index) => (
                                                    <TableRow key={member.staffId}>
                                                        <TableCell>{member.displayName} ({member.staffCode})</TableCell>
                                                        <TableCell>
                                                            <SelectInput name={`staffs[${index}].projectRole`} options={ProjectRoleOptions}
                                                                keyValue="value" displayvalue="label" size="small" hideNullOption />
                                                        </TableCell>
                                                        <TableCell>
                                                            <TextField name={`staffs[${index}].roleName`} placeholder="VD: Lập trình viên" size="small" />
                                                        </TableCell>
                                                        <TableCell align="center">
                                                            <IconButton size="small" color="error" onClick={() => handleRemove(index)} title="Bỏ khỏi dự án"><DeleteIcon fontSize="small" /></IconButton>
                                                        </TableCell>
                                                    </TableRow>
                                                ))}
                                            </TableBody>
                                        </Table>
                                    </TableContainer>
                                </Grid>
                            )}
                            <Grid item xs={12}>
                                <Alert severity="info">
                                    <Typography variant="body2">Ví dụ: dự án “Website bán hàng” của “Công ty CP ABC” — Nguyễn Văn A: quyền <b>Quản lý dự án</b>, vai trò “Trưởng nhóm”; Trần Thị B: quyền <b>Người thực hiện</b>, vai trò “Lập trình viên”. Khi lưu, mỗi người được phân công sẽ nhận email thông báo về Gmail trong hồ sơ.</Typography>
                                </Alert>
                            </Grid>
                        </>
                    )}
                    <Grid item xs={12}>
                        <TextField
                            label="Mô tả"
                            name="description"
                            multiline
                            rows={3}
                        />
                    </Grid>
                </Grid>
            </FormikProvider>
        </Popup>
    );
};

export default ProjectForm;
