import { useEffect, useState } from 'react';
import { Alert, Box, Button, Chip, CircularProgress, Paper, Typography } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import dayjs from 'dayjs';
import Avatar from '../../components/ui/Avatar';
import { getMyStaffProfile } from '../../services/StaffService';
import useAuthStore from '../../store/useAuthStore';
import { GenderOptions, WorkingStatusOptions } from '../../constants/staff';
import { getLabelFromOptions } from '../../LocalFunction';
import { roleLabel } from '../../utils/permissions';

const emptyValue = 'Chưa cập nhật';
const displayValue = value => value === null || value === undefined || String(value).trim() === '' ? emptyValue : value;
const displayDate = value => value && dayjs(value).isValid() ? dayjs(value).format('DD/MM/YYYY') : emptyValue;
const initialResult = () => ({ token: null, loading: true, profile: null, notLinked: false, error: '' });

function ProfileSection({ title, fields }) {
  return <Paper component="section" elevation={0} sx={{ p: { xs: 2, sm: 3 }, border: 1, borderColor: 'divider', borderRadius: 2 }}>
    <Typography component="h2" variant="subtitle1" fontWeight={700} sx={{ mb: 2 }}>{title}</Typography>
    <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2.5 }}>
      {fields.map(([label, value]) => <Box key={label} sx={{ minWidth: 0 }}>
        <Typography component="dt" variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>{label}</Typography>
        <Typography component="dd" variant="body2" sx={{ m: 0, fontWeight: 500, overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>{displayValue(value)}</Typography>
      </Box>)}
    </Box>
  </Paper>;
}

function ProfileContent({ user, accessToken }) {
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState(initialResult);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const isCurrentSession = () => {
      const current = useAuthStore.getState();
      return active && current.accessToken === accessToken && current.user?.id === user.id
        && current.user?.staffId === user.staffId;
    };
    setResult({ ...initialResult(), token: accessToken });
    getMyStaffProfile({ signal: controller.signal }).then(response => {
      if (!isCurrentSession()) return;
      if (!response?.data) throw new Error('Không tải được hồ sơ cá nhân. Vui lòng thử lại.');
      setResult({ token: accessToken, loading: false, profile: response.data, notLinked: false, error: '' });
    }).catch(error => {
      if (!isCurrentSession() || error.code === 'ERR_CANCELED') return;
      const notLinked = error.response?.status === 404;
      setResult({ token: accessToken, loading: false, profile: null, notLinked,
        error: notLinked ? '' : error.response?.data?.message || error.message || 'Không tải được hồ sơ cá nhân. Vui lòng thử lại.' });
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [accessToken, user.id, user.staffId, revision]);

  // Hide the previous response immediately while a new session is being loaded.
  const { profile, loading, notLinked, error } = result.token === accessToken ? result : initialResult();
  const name = profile?.displayName || user.staffName || user.username;
  const roles = (user.roles || []).filter(role => role.isDeleted !== true).map(roleLabel).join(', ');

  return <Box className="space-y-4" sx={{ maxWidth: 1100, mx: 'auto' }}>
    <Box className="flex flex-wrap items-center justify-between gap-3">
      <Box>
        <Typography component="h1" variant="h5" fontWeight={700}>Trang cá nhân</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>Thông tin tài khoản và hồ sơ nhân viên của bạn.</Typography>
      </Box>
      <Button startIcon={<RefreshIcon />} onClick={() => setRevision(value => value + 1)} disabled={loading}>Tải lại</Button>
    </Box>

    <Paper elevation={0} sx={{ p: { xs: 2, sm: 3 }, border: 1, borderColor: 'divider', borderRadius: 2 }}>
      <Box className="flex items-center gap-4">
        <Avatar name={name} imgPath={profile?.avatarUrl || user.avatarUrl} className="w-16 h-16 text-2xl shrink-0" />
        <Box sx={{ minWidth: 0 }}>
          <Typography component="h2" variant="h6" fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>{name}</Typography>
          {(profile?.staffCode || user.staffCode) && <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{profile?.staffCode || user.staffCode}</Typography>}
          <Chip size="small" label="Tài khoản đang hoạt động" color="success" variant="outlined" />
        </Box>
      </Box>
    </Paper>

    <ProfileSection title="Thông tin tài khoản" fields={[
      ['Tên đăng nhập', user.username], ['Nhóm quyền', roles || 'Chưa được gán nhóm quyền'],
    ]} />

    {loading ? <Box role="status" sx={{ p: 4, textAlign: 'center' }}>
      <CircularProgress size={28} aria-label="Đang tải hồ sơ cá nhân" />
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Đang tải hồ sơ cá nhân…</Typography>
    </Box> : error ? <Alert severity="error" action={<Button color="inherit" onClick={() => setRevision(value => value + 1)}>Thử lại</Button>}>{error}</Alert>
      : notLinked ? <Alert severity="info">Tài khoản của bạn chưa được liên kết với hồ sơ nhân viên đang hoạt động. Vui lòng liên hệ quản trị viên để cập nhật thông tin.</Alert>
        : profile && <>
          <ProfileSection title="Thông tin cá nhân" fields={[
            ['Họ và tên', profile.displayName], ['Mã nhân viên', profile.staffCode],
            ['Ngày sinh', displayDate(profile.birthDate)], ['Giới tính', getLabelFromOptions(GenderOptions, profile.gender)],
            ['Số điện thoại', profile.phoneNumber], ['Email cá nhân', profile.email],
            ['Địa chỉ hiện tại', profile.currentAddress], ['Thường trú', profile.permanentResidence],
            ['Tạm trú', profile.currentResidence],
          ]} />
          <ProfileSection title="Thông tin công việc" fields={[
            ['Phòng ban', profile.departmentName], ['Chức danh', profile.positionName],
            ['Trạng thái làm việc', getLabelFromOptions(WorkingStatusOptions, profile.workingStatus)],
            ['Ngày vào làm', displayDate(profile.startDate)], ['Email công ty', profile.companyEmail],
            ...(profile.resignationDate ? [['Ngày nghỉ việc', displayDate(profile.resignationDate)]] : []),
          ]} />
        </>}
  </Box>;
}

export default function MyProfilePage() {
  const user = useAuthStore(state => state.user);
  const accessToken = useAuthStore(state => state.accessToken);
  return user && accessToken ? <ProfileContent key={`${user.id}:${user.staffId || ''}`} user={user} accessToken={accessToken} /> : null;
}
