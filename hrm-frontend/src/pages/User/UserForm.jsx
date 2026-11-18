import React, { useEffect, useMemo } from 'react';
import { Autocomplete, Button, Box, TextField as MuiTextField } from '@mui/material';
import { useFormik, FormikProvider } from 'formik';
import * as Yup from 'yup';
import TextField from '../../components/ui/TextField';
import PagingAutocomplete from '../../components/ui/PagingAutocomplete';
import Popup from '../../components/ui/Popup';
import { pagingStaffs } from '../../services/StaffService';
import useUserStore from '../../store/userStore';
import { ROLES } from '../../constants/roles';
import { initialUserFormRoles, roleLabel } from '../../utils/permissions.js';

const UserForm = ({ open, onClose, userData, onSaveSuccess, isView = false }) => {
    const { addUser, modifyUser, roles: allRoles, loadRoles } = useUserStore();

    useEffect(() => {
        loadRoles();
    }, [loadRoles]);

    const initialValues = useMemo(() => ({
        id: userData?.id || null,
        username: userData?.username || '',
        password: userData?.password || '',
        active: userData?.active !== undefined ? userData.active : true,
        roles: initialUserFormRoles(userData, allRoles),
        staff: userData?.staffId ? { id: userData.staffId, displayName: userData.staffName, staffCode: '' } : null,
    }), [userData, allRoles]);

    const adminAccount = !!userData?.id && (userData.roles || []).some((role) => role.name === ROLES.ADMIN && !role.isDeleted);

    const validationSchema = Yup.object({
        username: Yup.string().required('Tên đăng nhập không được để trống'),
        password: userData?.id ? Yup.string().nullable() : Yup.string().required('Mật khẩu không được để trống'),
    });

    const formik = useFormik({
        initialValues: initialValues,
        enableReinitialize: true,
        validationSchema: validationSchema,
        onSubmit: async (values) => {
            try {
                const payload = {
                    id: values.id,
                    username: values.username,
                    password: values.password || undefined,
                    active: values.active,
                    roles: values.roles,
                    staffId: values.staff?.id || null,
                };
                if (values.id) {
                    await modifyUser(values.id, payload);
                } else {
                    await addUser(payload);
                }
                if (onSaveSuccess) onSaveSuccess();
            } catch (error) {
                console.error('Error saving user', error);
            }
        },
    });

    const action = (
        <>
            <Button 
                onClick={onClose} 
                variant="outlined" 
                color="inherit"
                style={{ 
                    textTransform: 'none',
                    borderColor: '#d1d5db',
                    color: '#4b5563',
                    backgroundColor: '#f9fafb',
                    height: '32px',
                    fontSize: '0.8125rem'
                }}
                sx={{
                    '&:hover': {
                        backgroundColor: '#f3f4f6 !important',
                        borderColor: '#9ca3af !important',
                        color: '#1f2937 !important'
                    }
                }}
            >
                {isView ? 'Đóng' : 'Hủy bỏ'}
            </Button>
            {!isView && (
                <Button 
                    onClick={formik.handleSubmit} 
                    color="primary" 
                    variant="contained"
                    style={{ 
                        textTransform: 'none',
                        height: '32px',
                        fontSize: '0.8125rem',
                        paddingLeft: '24px',
                        paddingRight: '24px',
                        marginLeft: '8px'
                    }}
                >
                    Lưu lại
                </Button>
            )}
        </>
    );

    return (
        <Popup
            open={open}
            onClosePopup={onClose}
            title={isView ? 'Chi tiết tài khoản' : (userData ? 'Cập nhật tài khoản' : 'Thêm tài khoản')}
            size="sm"
            action={action}
        >
            <FormikProvider value={formik}>
                <Box>
                    <TextField 
                        label="Tên đăng nhập" 
                        name="username" 
                        required
                        fullWidth 
                        disabled={isView}
                        autoComplete="new-password"
                    />
                    {!userData && (
                        <TextField 
                            label="Mật khẩu" 
                            name="password" 
                            type="password"
                            required
                            fullWidth 
                            disabled={isView}
                            autoComplete="new-password"
                        />
                    )}
                    <PagingAutocomplete 
                        label="Nhân viên liên kết" 
                        name="staff"
                        api={pagingStaffs}
                        disabled={isView}
                        getOptionLabel={(option) => {
                            if (!option) return "";
                            const codePart = option.staffCode ? ` (${option.staffCode})` : "";
                            return `${option.displayName || option.staffName || ''}${codePart}`;
                        }}
                        onChange={(event, value) => {
                            formik.setFieldValue('staff', value);
                            
                            // Auto-populate username if selecting a staff for a new user
                            if (value && !userData) {
                                if (value.staffCode && !formik.values.username) {
                                    formik.setFieldValue('username', value.staffCode);
                                }
                            }
                        }}
                    />
                    <Box mt={2}>
                        <Autocomplete
                            multiple
                            size="small"
                            options={allRoles}
                            value={formik.values.roles}
                            getOptionLabel={roleLabel}
                            isOptionEqualToValue={(option, value) => option.id === value.id}
                            disableCloseOnSelect
                            disabled={isView || adminAccount}
                            onChange={(_, nextRoles, reason, details) => {
                                if (reason === 'selectOption' && details?.option?.name === ROLES.ADMIN) {
                                    formik.setFieldValue('roles', [details.option]);
                                } else if (reason === 'selectOption') {
                                    formik.setFieldValue('roles', nextRoles.filter((role) => role.name !== ROLES.ADMIN));
                                } else {
                                    formik.setFieldValue('roles', nextRoles);
                                }
                            }}
                            renderInput={(params) => <MuiTextField {...params} label="Vai trò" placeholder="Chọn vai trò..." />}
                        />
                    </Box>
                </Box>
            </FormikProvider>
        </Popup>
    );
};

export default UserForm;
