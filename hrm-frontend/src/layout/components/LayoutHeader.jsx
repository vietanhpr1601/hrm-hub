import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react'
import { NavLink } from 'react-router-dom'
import { 
  IconButton, Button, Menu, MenuItem, Divider, Typography, Box, 
  CircularProgress, Alert
} from '@mui/material'
import { useFormik, FormikProvider } from 'formik'
import TextField from '@/components/ui/TextField'
import SelectInput from '@/components/ui/SelectInput'
import MenuOutlinedIcon from '@mui/icons-material/MenuOutlined'
import LightModeIcon from '@mui/icons-material/LightMode'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import PersonOutlineIcon from '@mui/icons-material/PersonOutline'
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined'
import LogoutIcon from '@mui/icons-material/Logout'
import AccessTimeIcon from '@mui/icons-material/AccessTime'
import InfoIcon from '@mui/icons-material/Info'
import LocationOnIcon from '@mui/icons-material/LocationOn'
import dayjs from 'dayjs'
import { toast } from 'sonner'

import useThemeStore from '@/store/themeStore'
import useSidebarStore from '@/store/sidebarStore'
import useAuthStore from '@/store/useAuthStore'
import useTimesheetStore from '@/store/useTimesheetStore'
import useShiftWorkStore from '@/store/useShiftWorkStore'
import { getTimesheetByStaffAndRange, getAttendanceLocationPolicy } from '@/services/timesheetService'
import { attendanceSessionKey, createLocationRequestManager, formatLocationAccuracy, isLocationCancelled, validateAttendanceLocation, getLocationMapUrl } from '@/utils/attendanceLocation'
import { usePermissions } from '@/hooks/usePermission'
import ConfirmationDialog from '@/components/ui/ConfirmationDialog'
import Avatar from '@/components/ui/Avatar'
import Popup from '@/components/ui/Popup'
import NotificationBell from './NotificationBell'
import LanguageIcon from '@mui/icons-material/Language'
import { useLanguage } from '@/i18n/LanguageProvider'

const LayoutHeader = () => {
  const { toggleCollapsed, toggleMobileOpen } = useSidebarStore();
  const { user, logout } = useAuthStore();
  const { mode, toggleTheme } = useThemeStore();
  const { checkInOut, loadMyTimesheets } = useTimesheetStore();
  const { allShifts, loadAllShifts } = useShiftWorkStore();
  const can = usePermissions();
  const canReadAttendance = can('ATTENDANCE_SELF') || can('ATTENDANCE_VIEW');
  const canCheckIn = can('ATTENDANCE_SELF') || can('ATTENDANCE_MANAGE');

  const { lang, setLang, t } = useLanguage();
  const [anchorEl, setAnchorEl] = useState(null);
  const open = Boolean(anchorEl);
  const [showConfirmLogout, setShowConfirmLogout] = useState(false);

  // Quick Timekeeping Dialog States
  const [timekeepingOpen, setTimekeepingOpen] = useState(false);
  const [time, setTime] = useState(dayjs());
  const [location, setLocation] = useState(null);
  const [locationError, setLocationError] = useState('');
  const [locationLoading, setLocationLoading] = useState(false);
  const [locationPolicy, setLocationPolicy] = useState(null);
  const [bestLocationSample, setBestLocationSample] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [savingAttendance, setSavingAttendance] = useState(false);
  const [showConfirmTimekeeping, setShowConfirmTimekeeping] = useState(false);
  const [todayTimesheet, setTodayTimesheet] = useState(null);
  const locationManager = useRef(createLocationRequestManager());
  const locationRequestId = useRef(0);
  const dialogOpen = useRef(false);
  const submitInProgress = useRef(false);
  const saveInProgress = useRef(false);
  const submissionId = useRef(0);

  const fetchLocation = useCallback(async () => {
    const requestId = ++locationRequestId.current;
    const sessionKey = attendanceSessionKey(useAuthStore.getState().user);
    const isCurrent = () => dialogOpen.current && requestId === locationRequestId.current
      && sessionKey === attendanceSessionKey(useAuthStore.getState().user);
    setLocation(null);
    setBestLocationSample(null);
    setLocationPolicy(null);
    setLocationError('');
    setLocationLoading(true);
    try {
      const position = await locationManager.current.request({
        loadPolicy: getAttendanceLocationPolicy,
        onPolicy: policy => { if (isCurrent()) setLocationPolicy(policy); },
        onSample: sample => { if (isCurrent()) setBestLocationSample(sample); },
      });
      if (!isCurrent()) return null;
      setLocation(position);
      return position;
    } catch (error) {
      if (isCurrent() && !isLocationCancelled(error)) {
        setLocation(null);
        setLocationError(error.message);
      }
      return null;
    } finally {
      if (isCurrent()) setLocationLoading(false);
    }
  }, []);

  // Invalidate immediately on logout, staff relinking or permission changes, including between renders.
  useEffect(() => useAuthStore.subscribe((state, previous) => {
    if (attendanceSessionKey(state.user) === attendanceSessionKey(previous.user)) return;
    dialogOpen.current = false;
    locationRequestId.current += 1;
    submissionId.current += 1;
    locationManager.current.cancel();
    submitInProgress.current = false;
    saveInProgress.current = false;
    setTimekeepingOpen(false);
    setShowConfirmTimekeeping(false);
    setLocation(null);
    setBestLocationSample(null);
    setLocationPolicy(null);
    setLocationError('');
    setLocationLoading(false);
    setSubmitting(false);
    setSavingAttendance(false);
    setTodayTimesheet(null);
  }), []);

  useEffect(() => {
    dialogOpen.current = timekeepingOpen;
    if (!timekeepingOpen) return undefined;
    setTime(dayjs());
    fetchLocation();
    loadAllShifts();
    const timer = setInterval(() => setTime(dayjs()), 1000);
    return () => {
      dialogOpen.current = false;
      locationRequestId.current += 1;
      locationManager.current.cancel();
      clearInterval(timer);
    };
  }, [timekeepingOpen, fetchLocation, loadAllShifts]);

  const defaultShiftId = useMemo(() => {
    if (allShifts.length > 0) {
      const defaultShift = allShifts.find(s => s.code?.toLowerCase().includes('hanh_chinh') || s.code?.toLowerCase().includes('ca_ngay')) || allShifts[0];
      return defaultShift?.id || '';
    }
    return '';
  }, [allShifts]);

  const shiftOptions = useMemo(() => {
    return allShifts.map(shift => ({
      value: shift.id,
      name: `${shift.name} (${shift.startTime} - ${shift.endTime})`
    }));
  }, [allShifts]);

  const initialValues = useMemo(() => ({
    shiftId: defaultShiftId,
    recordType: 'CHECK_IN',
    staffName: user?.staffName || user?.fullName || user?.username || '',
  }), [defaultShiftId, user?.staffName, user?.fullName, user?.username]);

  const formik = useFormik({
    initialValues,
    enableReinitialize: true,
    onSubmit: async (values) => {
      if (submitInProgress.current || !dialogOpen.current) return;
      if (!user?.staffId || !canCheckIn) {
        toast.error("Không tìm thấy thông tin nhân viên đăng nhập");
        return;
      }

      if (!values.shiftId) {
        toast.error('Vui lòng chọn ca làm việc.');
        return;
      }
      submitInProgress.current = true;
      const currentSubmission = ++submissionId.current;
      const sessionKey = attendanceSessionKey(user);
      const isCurrentSubmission = () => currentSubmission === submissionId.current
        && sessionKey === attendanceSessionKey(useAuthStore.getState().user);
      setSubmitting(true);

      try {
        const freshLocation = await fetchLocation();
        if (!freshLocation || !dialogOpen.current || !isCurrentSubmission()) return;
        validateAttendanceLocation(freshLocation);

        const checkInOutDto = {
          staffId: user.staffId,
          ...freshLocation,
          deviceType: 'Web Browser',
          recordType: values.recordType,
          shiftId: values.shiftId || null
        };

        saveInProgress.current = true;
        setSavingAttendance(true);
        const response = await checkInOut(checkInOutDto);
        if (!isCurrentSubmission()) return;
        toast.success(`Điểm danh ${values.recordType === 'CHECK_IN' ? 'Vào ca' : 'Ra ca'} thành công!`);
        
        // Reload current month timesheets to immediately update the calendar view if open
        if (user?.staffId && canReadAttendance) {
          const savedTime = dayjs(response?.data?.recordTime);
          const startOfMonth = savedTime.startOf('month').format('YYYY-MM-DD');
          const endOfMonth = savedTime.endOf('month').format('YYYY-MM-DD');
          loadMyTimesheets(user.staffId, startOfMonth, endOfMonth).catch(err => 
            console.error("Failed to refresh monthly timesheets after check-in/out:", err)
          );
        }
        
        // Reset states
        dialogOpen.current = false;
        setTimekeepingOpen(false);
        setLocation(null);
        setTodayTimesheet(null);
      } catch (err) {
        if (!isCurrentSubmission()) return;
        console.error('Quick check in out submit error:', err);
        toast.error(err.response?.data?.message || err.message || 'Có lỗi xảy ra khi chấm công');
      } finally {
        if (isCurrentSubmission()) {
          submitInProgress.current = false;
          saveInProgress.current = false;
          setSubmitting(false);
          setSavingAttendance(false);
        }
      }
    }
  });

  const { values, setFieldValue } = formik;

  const selectedDateStr = time.format('YYYY-MM-DD');

  // Fetch selected date timesheet status when modal opens or selectedDateStr changes
  useEffect(() => {
    let active = true;
    if (timekeepingOpen && canReadAttendance && user?.staffId && selectedDateStr) {
      getTimesheetByStaffAndRange(user.staffId, selectedDateStr, selectedDateStr)
        .then(res => {
          if (!active) return;
          if (res && res.data && res.data.length > 0) {
            setTodayTimesheet(res.data[0]);
          } else {
            setTodayTimesheet(null);
          }
        })
        .catch(err => {
          if (!active) return;
          console.error('Failed to load selected date timesheet:', err);
          setTodayTimesheet(null);
        });
    } else {
      setTodayTimesheet(null);
    }
    return () => { active = false; };
  }, [timekeepingOpen, canReadAttendance, user?.staffId, selectedDateStr]);

  const handleToggle = () => {
    if (window.innerWidth < 768) {
      toggleMobileOpen();
    } else {
      toggleCollapsed();
    }
  };

  const handleAvatarClick = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleLogoutClick = () => {
    handleClose();
    setShowConfirmLogout(true);
  };

  const handleConfirmLogout = () => {
    logout();
  };

  // handleCheckInOutSubmit logic refactored into formik onSubmit

  const handleCloseTimekeepingDialog = () => {
    if (saveInProgress.current) return;
    dialogOpen.current = false;
    locationRequestId.current += 1;
    locationManager.current.cancel();
    submissionId.current += 1;
    submitInProgress.current = false;
    setSubmitting(false);
    setTimekeepingOpen(false);
    setLocation(null);
    setBestLocationSample(null);
    setLocationPolicy(null);
    setLocationLoading(false);
    setLocationError('');
    setShowConfirmTimekeeping(false);
    setTodayTimesheet(null);
    formik.resetForm();
  };

  const selectedShiftApprovedOrRejected = useMemo(() => {
    if (!todayTimesheet || !values.shiftId) return false;
    const status = todayTimesheet.status;
    if (status !== 'APPROVED' && status !== 'REJECTED') return false;
    
    return true;
  }, [todayTimesheet, values.shiftId]);

  const confirmationText = useMemo(() => {
    const actionText = values.recordType === 'CHECK_IN' ? 'Vào ca' : 'Ra ca';
    const dateStr = dayjs(selectedDateStr).format('DD/MM/YYYY');
    const shift = allShifts.find(s => s.id === values.shiftId);
    
    const formatTime = (timeStr) => {
      if (!timeStr) return '';
      return timeStr.substring(0, 5);
    };
    const shiftText = shift 
      ? ` cho ca ${shift.name} (${formatTime(shift.startTime)} - ${formatTime(shift.endTime)})` 
      : '';
    
    // If it's a CHECK_IN, we don't warn about updating/resubmitting since backend always takes the earliest check-in anyway.
    if (values.recordType === 'CHECK_IN') {
      return `Bạn có chắc chắn muốn thực hiện chấm công ${actionText}${shiftText} ngày ${dateStr} không?`;
    }

    // If it's a CHECK_OUT, we warn if todayTimesheet exists and has a special status
    if (todayTimesheet) {
      const status = todayTimesheet.status;
      if (status === 'APPROVED' || status === 'REJECTED') {
        return `Công ngày ${dateStr} của bạn đang ở trạng thái ${status === 'APPROVED' ? 'ĐÃ DUYỆT' : 'TỪ CHỐI'}. Việc quẹt ${actionText}${shiftText} sẽ cập nhật giờ ra và gửi duyệt lại. Bạn có chắc chắn muốn tiếp tục không?`;
      }
    }
    
    return `Bạn có chắc chắn muốn thực hiện chấm công ${actionText}${shiftText} ngày ${dateStr} không?`;
  }, [todayTimesheet, values.recordType, selectedDateStr, values.shiftId, allShifts]);

  return (
    <div className='bg-background h-[48px] flex items-center justify-between px-3 md:px-4 text-foreground border-b border-border shadow-sm'>
      {/* Left side: nút thu gọn/mở menu */}
      <div className='flex items-center gap-2'>
        <IconButton size="small" className='text-primary' onClick={handleToggle} title={t('Thu gọn / mở rộng menu')}>
          <MenuOutlinedIcon fontSize="medium" />
        </IconButton>
        <NavLink to="/dashboard" className='md:hidden no-underline font-black italic text-primary text-[20px]'>HRM</NavLink>
      </div>

      {/* Right side: Actions */}
      <div className='flex items-center gap-2 md:gap-3'>
        {/* Nút Chấm công giống hr-v5 nhưng theo style hrm */}
        {user && canCheckIn && (
          <Button
            variant="contained"
            color="primary"
            size="small"
            startIcon={<AccessTimeIcon />}
            onClick={() => {
              if (!user?.staffId) {
                toast.error("Tài khoản của bạn chưa được liên kết với hồ sơ nhân sự nào để chấm công!");
                return;
              }
              setTimekeepingOpen(true);
            }}
          >
            <span className='hidden md:block'>Chấm công</span>
          </Button>
        )}

        {/* <div className='flex items-center bg-muted hover:bg-primary/10 dark:hover:bg-primary/20 text-primary px-2.5 py-1.5 rounded-md cursor-pointer border border-border'>
          <MailOutlineIcon sx={{ fontSize: '18px' }} />
        </div>
 
        <div className='flex items-center bg-muted hover:bg-primary/10 dark:hover:bg-primary/20 text-primary px-2.5 py-1.5 rounded-md cursor-pointer relative border border-border'>
          <NotificationsNoneIcon sx={{ fontSize: '18px' }} />
          <span className='absolute top-2 right-2.5 w-2 h-2 bg-red-500 rounded-full border border-background'></span>
        </div> */}
 
        <span className='hidden md:block w-px h-6 bg-border' />

        {/* Nút ngôn ngữ: bấm là chuyển ngay VI <-> EN */}
        <button
          type="button"
          data-no-translate
          onClick={() => setLang(lang === 'vi' ? 'en' : 'vi')}
          className='flex items-center gap-1 bg-muted hover:bg-primary/10 dark:hover:bg-primary/20 text-primary px-2 py-1 rounded-full cursor-pointer border border-border text-[12px] font-bold'
          title={lang === 'vi' ? 'Switch to English' : 'Chuyển sang Tiếng Việt'}
        >
          <LanguageIcon sx={{ fontSize: '16px' }} />
          {lang.toUpperCase()}
        </button>

        {/* Nút bật/tắt chế độ Sáng/Tối */}
        <div
          onClick={toggleTheme}
          className='flex items-center bg-muted hover:bg-primary/10 dark:hover:bg-primary/20 text-primary px-2 py-1.5 rounded-full cursor-pointer border border-border'
          title={t(mode === 'light' ? 'Chuyển sang chế độ tối' : 'Chuyển sang chế độ sáng')}
        >
          {mode === 'light' ? <DarkModeIcon sx={{ fontSize: '16px' }} /> : <LightModeIcon sx={{ fontSize: '16px' }} />}
        </div>

        <NotificationBell />

        <span className='hidden md:block w-px h-6 bg-border' />

        {/* Avatar Trigger */}
        <div
          onClick={handleAvatarClick}
          className='flex items-center gap-2 cursor-pointer rounded-md px-1 hover:bg-muted'
        >
          <div className='w-8 h-8 rounded-full flex items-center justify-center border border-border shadow-sm overflow-hidden flex-shrink-0'>
            <Avatar
              name={user?.fullName || user?.username}
              imgPath={user?.avatarUrl}
              className="w-full h-full text-[12px] font-bold"
            />
          </div>
          <div className='hidden sm:flex flex-col leading-tight max-w-[160px]' data-no-translate>
            <span className='text-[13px] font-bold text-foreground truncate'>{user?.username}</span>
            <span className='text-[11px] text-muted-foreground truncate'>{user?.staffName || user?.fullName || ''}</span>
          </div>
        </div>
 
        {/* User Profile Dropdown Menu */}
        <Menu
          anchorEl={anchorEl}
          id="user-menu"
          open={open}
          onClose={handleClose}
          onClick={handleClose}
          disablePortal
          PaperProps={{
            className: "shadow-lg mt-3 w-[220px] md:w-[260px] rounded-lg p-1 border border-border bg-popover text-popover-foreground",
            sx: {
              marginLeft: '-16px'
            }
          }}
          transformOrigin={{ horizontal: 'right', vertical: 'top' }}
          anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
        >
          <div className="flex items-center gap-3 px-4 py-2">
            <Avatar 
              name={user?.fullName || user?.username} 
              imgPath={user?.avatarUrl} 
              className="w-12 h-12 border-2 border-background shadow-sm flex-shrink-0 text-lg font-semibold" 
            />
            <div className="flex flex-col min-w-0">
              {user?.staffName && (
                <span className="font-semibold text-[15px] text-foreground truncate block leading-tight">
                  {user.staffName}
                </span>
              )}
              <span className="text-muted-foreground text-[12px] truncate mt-0.5 block">
                {user?.email || (user?.username ? `${user.username}@gmail.com` : 'user@domain.com')}
              </span>
              {user?.staffCode && (
                <span className="text-secondary text-[12px] font-semibold mt-0.5 block tracking-wide">
                  {user.staffCode}
                </span>
              )}
            </div>
          </div>
          <Divider className="my-1 border-border" />
          
          <MenuItem 
            component={NavLink}
            to="/profile"
            onClick={handleClose}
            className="mx-1 my-0.5 px-3 py-2.5 rounded-md text-sm text-foreground hover:bg-sidebar-accent/40 hover:text-sidebar-accent-foreground flex items-center gap-3 group transition-colors cursor-pointer"
          >
            <PersonOutlineIcon className="text-foreground group-hover:text-sidebar-accent-foreground w-[18px] h-[18px] min-w-[18px]" />
            Trang cá nhân
          </MenuItem>
          
          <MenuItem 
            onClick={handleClose}
            className="mx-1 my-0.5 px-3 py-2.5 rounded-md text-sm text-foreground hover:bg-sidebar-accent/40 hover:text-sidebar-accent-foreground flex items-center gap-3 group transition-colors cursor-pointer"
          >
            <SettingsOutlinedIcon className="text-foreground group-hover:text-sidebar-accent-foreground w-[18px] h-[18px] min-w-[18px]" />
            Cài đặt
          </MenuItem>
          
          <MenuItem 
            onClick={handleLogoutClick} 
            className="mx-1 my-0.5 px-3 py-2.5 rounded-md text-sm text-destructive hover:bg-destructive/10 flex items-center gap-3 group transition-colors cursor-pointer"
          >
            <LogoutIcon className="text-destructive w-[18px] h-[18px] min-w-[18px]" />
            Đăng xuất
          </MenuItem>
        </Menu>
 
        {/* Confirmation Dialog for Logging out */}
        <ConfirmationDialog
          open={showConfirmLogout}
          onConfirmDialogClose={() => setShowConfirmLogout(false)}
          title="Xác nhận đăng xuất"
          text="Bạn có chắc chắn muốn đăng xuất khỏi hệ thống không?"
          agree="Đăng xuất"
          cancel="Hủy"
          onYesClick={handleConfirmLogout}
          container={document.getElementById('root')}
        />

        {/* Confirmation Dialog for Timekeeping */}
        <ConfirmationDialog
          open={showConfirmTimekeeping}
          onConfirmDialogClose={() => setShowConfirmTimekeeping(false)}
          title="Xác nhận chấm công"
          text={confirmationText}
          agree="Xác nhận"
          cancel="Hủy"
          onYesClick={() => {
            setShowConfirmTimekeeping(false);
            formik.handleSubmit();
          }}
          container={document.getElementById('root')}
        />

        {/* Quick Timekeeping Dialog using shared Popup component */}
          <Popup
            open={timekeepingOpen}
            onClosePopup={handleCloseTimekeepingDialog}
            title="Chấm công"
            size="xs"
            action={
              <>
                <Button
                  onClick={handleCloseTimekeepingDialog}
                  disabled={savingAttendance}
                  variant="outlined"
                  color="inherit"
                  className="text-gray-600 hover:bg-gray-150 font-semibold normal-case px-4 py-1.5 rounded-lg"
                >
                  Hủy
                </Button>
                <Button
                  variant="contained"
                  color="primary"
                  disabled={submitting || locationLoading || !location || !user?.staffId || !values.shiftId || !canCheckIn}
                  onClick={() => {
                    setShowConfirmTimekeeping(true);
                  }}
                  startIcon={<AccessTimeIcon />}
                  className="font-bold px-5 py-1.5 rounded-lg shadow-sm"
                >
                  {submitting ? <CircularProgress size={20} color="inherit" /> : 'Chấm công'}
                </Button>
              </>
            }
          >
            <FormikProvider value={formik}>
              <Box className="space-y-4 pt-[-6px] pb-2">
                {selectedShiftApprovedOrRejected && (
                  <Box className="bg-amber-50 dark:bg-amber-950/20 p-3.5 rounded-xl border border-amber-200 dark:border-amber-900/50 text-xs text-amber-700 dark:text-amber-400 flex items-start gap-2.5">
                    <InfoIcon sx={{ fontSize: 16, color: 'warning.main', mt: 0.5 }} />
                    <Box className="font-semibold leading-relaxed">
                      Bảng công ngày này đã có kết quả duyệt.
                      <br />
                      <span className="font-bold text-amber-800 dark:text-amber-300">Khi thêm lượt chấm công, phần công thực tế sẽ được gửi duyệt lại.</span>
                    </Box>
                  </Box>
                )}

                <Alert severity="info">Bật vị trí chính xác trên thiết bị. Hệ thống chọn vị trí có sai số nhỏ nhất và lấy lại vị trí khi bạn xác nhận.</Alert>
                <Box className="space-y-2 bg-muted p-3.5 rounded-xl border border-border" aria-live="polite">
                  <Box className="flex items-center gap-2">
                    <LocationOnIcon color="primary" fontSize="small" />
                    <Typography variant="subtitle2">Vị trí hiện tại</Typography>
                  </Box>
                  {locationPolicy && <Typography variant="body2">Sai số tối đa cho phép: {formatLocationAccuracy(locationPolicy.maxAccuracyMeters)} m · Lấy vị trí tối đa {locationPolicy.acquisitionTimeoutMs / 1000} giây</Typography>}
                  {locationLoading && <Box className="space-y-1">
                    <Box className="flex items-center gap-2"><CircularProgress size={16} /><Typography variant="body2">{locationPolicy ? 'Đang cải thiện vị trí…' : 'Đang tải cấu hình vị trí…'}</Typography></Box>
                    {bestLocationSample && <Typography variant="body2" color={bestLocationSample.locationAccuracy > locationPolicy?.maxAccuracyMeters ? 'warning.main' : 'text.secondary'}>Sai số tốt nhất: ±{formatLocationAccuracy(bestLocationSample.locationAccuracy)} m. Đang chờ vị trí chính xác hơn.</Typography>}
                  </Box>}
                  {locationError && <Alert severity="error">{locationError}</Alert>}
                  {location && <Box className="space-y-1">
                    <Alert severity="success">Vị trí đạt yêu cầu chấm công.</Alert>
                    <Typography variant="body2">Vĩ độ: {location.latitude.toFixed(6)} · Kinh độ: {location.longitude.toFixed(6)}</Typography>
                    <Typography variant="body2">Sai số ước tính: ±{formatLocationAccuracy(location.locationAccuracy)} m</Typography>
                    <Typography variant="caption" component="div">Cập nhật: {dayjs(location.locationCapturedAt).format('HH:mm:ss DD/MM/YYYY')}</Typography>
                    <a href={getLocationMapUrl(location)} target="_blank" rel="noopener noreferrer" className="text-primary underline text-sm">Xem vị trí trên bản đồ</a>
                  </Box>}
                  {!location && !locationLoading && !locationError && <Typography variant="body2">Chưa có vị trí.</Typography>}
                  <Button size="small" variant="outlined" onClick={fetchLocation} disabled={submitting || locationLoading}>Lấy lại vị trí</Button>
                </Box>

                {/* Timekeeping Info Form */}
                <Box className="space-y-3 pt-2">
                  <Typography variant="caption" className="font-bold text-primary tracking-wider uppercase">
                    THÔNG TIN CHẤM CÔNG
                  </Typography>
                  
                  <SelectInput
                    label="Ca làm việc"
                    name="shiftId"
                    options={shiftOptions}
                    required
                    fullWidth
                    hideNullOption
                  />

                  {/* Segmented control for Check-in / Check-out with standard HRM style */}
                  <Box className="space-y-1 mb-4">
                    <Typography variant="caption" className="block text-sm font-semibold mb-1.5 text-muted-foreground">
                      Hình thức chấm công <span style={{ color: 'red' }} className="font-bold ml-1">*</span>
                    </Typography>
                    <Box className="flex bg-muted p-1 rounded-xl w-full border border-border">
                      <button
                        type="button"
                        onClick={() => setFieldValue('recordType', 'CHECK_IN')}
                        className={`flex-1 py-1.5 text-xs font-bold rounded-lg transition duration-150 ${
                          values.recordType === 'CHECK_IN' 
                            ? 'bg-primary text-primary-foreground shadow-sm' 
                            : 'text-muted-foreground hover:text-foreground'
                        }`}
                      >
                        Vào ca (CHECK IN)
                      </button>
                      <button
                        type="button"
                        onClick={() => setFieldValue('recordType', 'CHECK_OUT')}
                        className={`flex-1 py-1.5 text-xs font-bold rounded-lg transition duration-150 ${
                          values.recordType === 'CHECK_OUT' 
                            ? 'bg-primary text-primary-foreground shadow-sm' 
                            : 'text-muted-foreground hover:text-foreground'
                        }`}
                      >
                        Ra ca (CHECK OUT)
                      </button>
                    </Box>
                  </Box>

                  <TextField
                    label="Nhân viên chấm công"
                    name="staffName"
                    disabled
                    readOnly
                    fullWidth
                  />

                  <Box className="bg-muted border border-border rounded-lg p-3">
                    <Typography variant="caption">Thời gian hiện tại</Typography>
                    <Typography variant="h6" className="font-semibold">{time.format('HH:mm:ss · DD/MM/YYYY')}</Typography>
                    <Typography variant="caption" className="text-muted-foreground">Thời điểm chấm công được hệ thống ghi nhận khi lưu.</Typography>
                  </Box>
                </Box>
              </Box>
            </FormikProvider>
          </Popup>
      </div>
    </div>
  )
}

export default LayoutHeader

