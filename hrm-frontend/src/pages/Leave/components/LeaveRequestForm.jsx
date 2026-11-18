import React, { useEffect, useMemo, useState } from 'react';
import { useFormik, FormikProvider } from 'formik';
import * as Yup from 'yup';
import { Grid, Button, FormControlLabel, Checkbox, Typography, Box, TextField as MuiTextField, Chip, Alert } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { toast } from 'sonner';

import useLeaveStore from '../../../store/useLeaveStore';
import useAuthStore from '../../../store/useAuthStore';
import TextField from '../../../components/ui/TextField';
import SelectInput from '../../../components/ui/SelectInput';
import DateTimePicker from '../../../components/ui/DateTimePicker';
import AsyncAutocomplete from '../../../components/ui/AsyncAutocomplete';
import Popup from '../../../components/ui/Popup';
import { pagingStaffLookup } from '../../../services/StaffService';
import { getAllShifts } from '../../../services/shiftWorkService';
import usePermission from '../../../hooks/usePermission';

const LeaveRequestForm = ({ open, onClose, requestData, onSaveSuccess }) => {
    const { t } = useTranslation();
    const { addLeaveRequest, modifyLeaveRequest, balance, loadLeaveBalance } = useLeaveStore();
    const { user } = useAuthStore();
    const [shifts, setShifts] = useState([]);
    const [makeupDate, setMakeupDate] = useState('');

    const isAdminOrHR = usePermission('LEAVE_MANAGE');

    // Load shifts for half day selection
    useEffect(() => {
        const fetchShifts = async () => {
            try {
                const response = await getAllShifts();
                const shiftList = response?.data || response || [];
                setShifts(shiftList.filter(s => s.code === 'CA_SANG' || s.code === 'CA_CHIEU'));
            } catch (error) {
                console.error("Failed to fetch shifts", error);
            }
        };
        fetchShifts();
    }, []);

    const initialValues = useMemo(() => ({
        id: requestData?.id || null,
        requestStaff: requestData?.requestStaffId ? {
            id: requestData.requestStaffId,
            displayName: requestData.requestStaffName,
            staffCode: requestData.requestStaffCode
        } : (user?.staffId ? {
            id: user.staffId,
            displayName: user.staffName || user.username || '',
            staffCode: user.staffCode || ''
        } : null),
        leaveType: requestData?.leaveType || 'ANNUAL',
        fromDate: requestData?.fromDate ? new Date(requestData.fromDate) : new Date(),
        toDate: requestData?.toDate ? new Date(requestData.toDate) : new Date(),
        requestReason: requestData?.requestReason || '',
        halfDayLeave: requestData?.halfDayLeave ?? false,
        shiftWorkId: requestData?.shiftWorkId || '',
        makeupDates: requestData?.makeupDates || [],
    }), [requestData, user, isAdminOrHR]);

    const validationSchema = Yup.object({
        requestStaff: Yup.object().nullable().required(t('leave.validation.staffRequired', 'Nhân viên là bắt buộc')),
        leaveType: Yup.string().required(t('leave.validation.typeRequired', 'Loại phép là bắt buộc')),
        fromDate: Yup.date().required(t('leave.validation.fromDateRequired', 'Ngày bắt đầu là bắt buộc')),
        toDate: Yup.date().required(t('leave.validation.toDateRequired', 'Ngày kết thúc là bắt buộc'))
            .min(Yup.ref('fromDate'), t('leave.validation.dateOrder', 'Ngày kết thúc phải sau hoặc bằng ngày bắt đầu')),
        makeupDates: Yup.array().when('leaveType', {
            is: 'MAKEUP', then: schema => schema.min(1, 'Vui lòng chọn ngày làm bù'),
        }),
    });

    const formik = useFormik({
        initialValues: initialValues,
        enableReinitialize: true,
        validationSchema: validationSchema,
        onSubmit: async (values) => {
            try {
                const submitValues = {
                    id: values.id,
                    requestStaffId: values.requestStaff?.id,
                    leaveType: values.leaveType,
                    fromDate: format(new Date(values.fromDate), 'yyyy-MM-dd'),
                    toDate: format(new Date(values.toDate), 'yyyy-MM-dd'),
                    requestReason: values.requestReason,
                    halfDayLeave: values.halfDayLeave,
                    shiftWorkId: values.shiftWorkId || null,
                    makeupDates: values.leaveType === 'MAKEUP' ? values.makeupDates : [],
                };

                if (values.id) {
                    await modifyLeaveRequest(values.id, submitValues);
                    toast.success(t('leave.message.updateSuccess', 'Cập nhật đơn nghỉ phép thành công'));
                } else {
                    await addLeaveRequest(submitValues);
                    toast.success(t('leave.message.createSuccess', 'Tạo đơn nghỉ phép thành công'));
                }
                if (onSaveSuccess) onSaveSuccess();
            } catch (error) {
                toast.error(error.response?.data?.message || t('leave.message.error', 'Đã xảy ra lỗi khi lưu đơn nghỉ phép'));
            }
        },
    });

    // Load balance dynamically when requestStaff or leaveType changes
    const staffIdForBalance = formik.values.requestStaff?.id;
    const leaveTypeForBalance = formik.values.leaveType;
    const fromDateForBalance = formik.values.fromDate;

    useEffect(() => {
        if (staffIdForBalance && leaveTypeForBalance === 'ANNUAL' && fromDateForBalance) {
            const year = new Date(fromDateForBalance).getFullYear();
            loadLeaveBalance(staffIdForBalance, year);
        }
    }, [staffIdForBalance, leaveTypeForBalance, fromDateForBalance, loadLeaveBalance]);

    const isSingleDay = useMemo(() => {
        if (!formik.values.fromDate || !formik.values.toDate) return true;
        const d1 = format(new Date(formik.values.fromDate), 'yyyy-MM-dd');
        const d2 = format(new Date(formik.values.toDate), 'yyyy-MM-dd');
        return d1 === d2;
    }, [formik.values.fromDate, formik.values.toDate]);

    useEffect(() => {
        if (!isSingleDay) {
            formik.setFieldValue('halfDayLeave', false);
            formik.setFieldValue('shiftWorkId', '');
        }
    }, [isSingleDay]);

    const action = (
        <>
            <Button onClick={onClose} variant="outlined" color="inherit" sx={{ color: 'text.secondary', textTransform: 'none' }}>
                {t('general.cancel', 'Hủy bỏ')}
            </Button>
            <Button onClick={formik.handleSubmit} color="primary" variant="contained" sx={{ textTransform: 'none', px: 4, ml: 1 }}>
                {t('general.save', 'Lưu lại')}
            </Button>
        </>
    );

    const leaveTypeOptions = [
        { value: 'ANNUAL', name: t('leave.type.annual', 'Phép năm') },
        { value: 'UNPAID', name: t('leave.type.unpaid', 'Phép không lương') },
        { value: 'MAKEUP', name: 'Nghỉ có làm bù' }
    ];

    return (
        <Popup
            open={open}
            onClosePopup={onClose}
            title={requestData ? t('leave.title.edit', 'Chỉnh sửa đơn nghỉ phép') : t('leave.title.create', 'Thêm mới đơn nghỉ phép')}
            size="md"
            action={action}
        >
            <FormikProvider value={formik}>
                <Grid container spacing={2} className='pt-3 pb-0'>

                    {/* Lựa chọn nhân viên (chỉ hiển thị cho HR/Admin khi tạo mới hoặc sửa) */}
                    {isAdminOrHR ? (
                        <Grid item xs={12} sm={6}>
                            <AsyncAutocomplete
                                name="requestStaff"
                                label={t('leave.field.staff', 'Nhân viên')}
                                api={pagingStaffLookup}
                                searchObject={{ pageIndex: 1, pageSize: 50 }}
                                displayName="displayName"
                                formik={formik}
                                required
                            />
                        </Grid>
                    ) : (
                        <Grid item xs={12} sm={6}>
                            <TextField
                                label={t('leave.field.staff', 'Nhân viên')}
                                name="requestStaff.displayName"
                                value={user?.staffName || user?.username || ''}
                                disabled
                            />
                        </Grid>
                    )}

                    {/* Loại nghỉ phép */}
                    <Grid item xs={12} sm={6}>
                        <SelectInput
                            name="leaveType"
                            label={t('leave.field.leaveType', 'Loại nghỉ phép')}
                            options={leaveTypeOptions}
                            keyValue="value"
                            displayvalue="name"
                            hideNullOption={true}
                            required
                        />
                    </Grid>

                    {/* Thời gian */}
                    <Grid item xs={12} sm={6}>
                        <DateTimePicker
                            label={t('leave.field.fromDate', 'Từ ngày')}
                            name="fromDate"
                            notValueMillisecond={true}
                            required
                        />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                        <DateTimePicker
                            label={t('leave.field.toDate', 'Đến ngày')}
                            name="toDate"
                            notValueMillisecond={true}
                            required
                        />
                    </Grid>

                    {/* Checkbox nghỉ nửa ngày */}
                    {isSingleDay && (
                        <Grid item xs={12}>
                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={formik.values.halfDayLeave}
                                        onChange={(e) => {
                                            const checked = e.target.checked;
                                            formik.setFieldValue('halfDayLeave', checked);
                                            if (!checked) {
                                                formik.setFieldValue('shiftWorkId', '');
                                            } else {
                                                // Mặc định chọn ca đầu tiên cho nửa ngày
                                                if (shifts.length > 0) {
                                                    formik.setFieldValue('shiftWorkId', shifts[0].id);
                                                }
                                            }
                                        }}
                                    />
                                }
                                label={t('leave.field.halfDayLeave', 'Nghỉ nửa ngày')}
                            />
                        </Grid>
                    )}

                    {/* Các cấu hình chi tiết cho nghỉ nửa ngày */}
                    {formik.values.halfDayLeave && (
                        <Grid item xs={12} sm={6}>
                            <SelectInput
                                name="shiftWorkId"
                                label={t('leave.field.shiftOff', 'Ca nghỉ phép')}
                                options={shifts}
                                keyValue="id"
                                displayvalue="name"
                            />
                        </Grid>
                    )}

                    {formik.values.leaveType === 'MAKEUP' && <Grid item xs={12}>
                        <Box sx={{ p: 2, border: 1, borderColor: 'divider', borderRadius: 2 }}>
                            <Typography variant="subtitle2" sx={{ mb: 1 }}>Ngày làm bù</Typography>
                            <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
                                <MuiTextField type="date" size="small" label="Chọn ngày làm bù" value={makeupDate}
                                    InputLabelProps={{ shrink: true }} onChange={event => setMakeupDate(event.target.value)} />
                                <Button variant="outlined" disabled={!makeupDate} onClick={() => {
                                    const from = format(new Date(formik.values.fromDate), 'yyyy-MM-dd');
                                    const to = format(new Date(formik.values.toDate), 'yyyy-MM-dd');
                                    if (makeupDate >= from && makeupDate <= to) { toast.error('Ngày làm bù phải ngoài khoảng ngày nghỉ'); return; }
                                    if (formik.values.makeupDates.includes(makeupDate)) { toast.error('Ngày làm bù đã được chọn'); return; }
                                    formik.setFieldValue('makeupDates', [...formik.values.makeupDates, makeupDate].sort());
                                    setMakeupDate('');
                                }}>Thêm ngày</Button>
                            </Box>
                            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 1 }}>
                                {formik.values.makeupDates.map(date => <Chip key={date} label={format(new Date(date), 'dd/MM/yyyy')}
                                    onDelete={() => formik.setFieldValue('makeupDates', formik.values.makeupDates.filter(item => item !== date))} />)}
                            </Box>
                            {formik.touched.makeupDates && formik.errors.makeupDates && <Typography color="error" variant="caption">{formik.errors.makeupDates}</Typography>}
                            <Alert severity="info">Mỗi ngày nghỉ chọn một ngày làm bù; nghỉ nửa ngày chọn một ngày. Công được tính theo chấm công thực tế đã duyệt vào ngày làm bù.</Alert>
                        </Box>
                    </Grid>}

                    {/* Lý do nghỉ */}
                    <Grid item xs={12}>
                        <TextField
                            label={t('leave.field.reason', 'Lý do nghỉ phép')}
                            name="requestReason"
                            multiline
                            rows={3}
                        />
                    </Grid>
                </Grid>
            </FormikProvider>
        </Popup>
    );
};

export default LeaveRequestForm;
