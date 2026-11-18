import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Box, Chip, CircularProgress, Typography } from '@mui/material';
import { Login, Logout, AccessTime, Schedule, Timelapse, MoreTime, Place } from '@mui/icons-material';
import dayjs from 'dayjs';
import { getRawLogs } from '../../../services/timesheetService';
import { summarizeAttendance, workplaceDistanceLabel } from '../../../utils/attendanceSummary.js';
import AttendanceLocationDetails from './AttendanceLocationDetails';
import { getAttendanceRelatedLeaves } from '../../../services/leaveService';

const statuses = { APPROVED: ['Đã duyệt', 'success'], REJECTED: ['Từ chối', 'error'], SUBMITTED: ['Chờ duyệt', 'warning'], DRAFT: ['Nháp', 'default'] };
const showTime = value => value ? dayjs(value).format('HH:mm') : 'Chưa ghi nhận';
const showHours = value => value == null ? 'Chưa có dữ liệu' : `${value.toFixed(2)} giờ`;

export default function AttendanceDetails({ timesheet, staffId, relatedLeaves = null }) {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [leaves, setLeaves] = useState([]);
  const [leaveError, setLeaveError] = useState('');
  const employeeId = timesheet?.staffId || staffId;
  const date = timesheet?.workingDate;
  useEffect(() => {
    let active = true;
    setLogs([]);
    setError('');
    setLeaves([]);
    setLeaveError('');
    if (!employeeId || !date) { setLoading(false); return undefined; }
    setLoading(true);
    getAttendanceRelatedLeaves(employeeId, date).then(response => {
      if (active) setLeaves(response?.data || []);
    }).catch(() => { if (active) setLeaveError('Không tải được đơn nghỉ và ngày làm bù liên quan.'); });
    getRawLogs(employeeId, date).then(response => {
      if (active) setLogs(response?.data || []);
    }).catch(reason => {
      if (active) setError(reason.response?.data?.message || 'Không tải được lịch sử vị trí chấm công. Hãy đóng và mở lại chi tiết.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [employeeId, date]);
  const summary = useMemo(() => summarizeAttendance(timesheet, logs), [timesheet, logs]);
  const status = statuses[timesheet?.status];
  const cards = [
    ['Giờ vào', showTime(summary.checkIn), Login, 'success.main'],
    ['Giờ ra', showTime(summary.checkOut), Logout, 'info.main'],
    ['Tổng giờ', showHours(summary.totalHours), AccessTime, 'primary.main'],
    ['Đi muộn', summary.lateMinutes == null ? 'Chưa có dữ liệu' : `${summary.lateMinutes} phút`, Schedule, 'warning.main'],
    ['Về sớm', summary.earlyMinutes == null ? 'Chưa có dữ liệu' : `${summary.earlyMinutes} phút`, Timelapse, 'warning.main'],
    ['Tăng ca (OT)', showHours(summary.overtimeHours), MoreTime, 'secondary.main'],
  ];
  return <Box className="space-y-4 py-2">
    <Box className="flex flex-wrap justify-between items-center gap-2">
      <Box><Typography variant="subtitle1" className="font-semibold">{timesheet?.staffName || 'Thông tin trong ngày'}</Typography>
        <Typography variant="caption" color="text.secondary">{timesheet?.staffCode}{timesheet?.departmentName ? ` · ${timesheet.departmentName}` : ''}</Typography></Box>
      {status ? <Chip label={status[0]} color={status[1]} size="small" /> : <Chip label="Chưa có bảng công" size="small" variant="outlined" />}
    </Box>
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', sm: 'repeat(3, minmax(0, 1fr))' }, gap: 1.5 }}>
      {cards.map(([label, value, Icon, color]) => <Box key={label} sx={{ border: 1, borderColor: 'divider', borderRadius: 2, p: 1.5, bgcolor: 'background.paper' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, color }}><Icon sx={{ fontSize: 18 }} /><Typography variant="caption">{label}</Typography></Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mt: 0.75 }}>{value}</Typography>
      </Box>)}
    </Box>
    <Typography variant="caption" color="text.secondary">Giờ làm thường: {showHours(summary.standardHours)}{Number.isFinite(timesheet?.totalWorkRatio) ? ` · Tổng công: ${timesheet.totalWorkRatio}` : ''}</Typography>
    {!!timesheet?.details?.length && <Box className="space-y-2">
      <Typography variant="subtitle2">Ca làm việc</Typography>
      {timesheet.details.map((detail, index) => <Box key={detail.id || index} sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 2 }}>
        <Typography variant="body2" className="font-semibold">{detail.shiftName || detail.shift?.name || 'Ca chưa xác định'}</Typography>
        <Typography variant="caption" color="text.secondary">{(detail.shiftStartTime || detail.shift?.startTime || '').slice(0, 5)} – {(detail.shiftEndTime || detail.shift?.endTime || '').slice(0, 5)}</Typography>
        <Typography variant="body2">Vào: {showTime(detail.checkInTime)} · Ra: {showTime(detail.checkOutTime)}</Typography>
        <Typography variant="caption" color="text.secondary">Đi muộn: {detail.lateMinutes ?? 0} phút · Về sớm: {detail.earlyMinutes ?? 0} phút · Công: {detail.workRatio ?? 0}</Typography>
      </Box>)}
    </Box>}
    <Box className="space-y-2">
      <Typography variant="subtitle2">Nhật ký chấm công và vị trí</Typography>
      {loading ? <Box className="flex items-center gap-2 py-3"><CircularProgress size={18} /><Typography variant="body2">Đang tải nhật ký…</Typography></Box>
        : error ? <Alert severity="error">{error}</Alert>
          : !logs.length ? <Alert severity="info">Chưa ghi nhận lượt chấm công trong ngày này.</Alert>
            : logs.map(log => <Box key={log.id} sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 2 }}>
              <Box className="flex justify-between items-center gap-2"><Typography variant="body2" className="font-semibold">{dayjs(log.recordTime).format('HH:mm:ss')}</Typography><Chip size="small" color={log.recordType === 'CHECK_IN' ? 'success' : 'info'} variant="outlined" label={log.recordType === 'CHECK_IN' ? 'Vào ca' : 'Ra ca'} /></Box>
              {log.shiftName && <Typography variant="caption">Ca: {log.shiftName}</Typography>}
              <AttendanceLocationDetails location={log} />
              <Typography variant="caption" component="div" color="text.secondary"><Place sx={{ fontSize: 13, verticalAlign: 'middle' }} /> Khoảng cách{log.workplaceName ? ` đến ${log.workplaceName}` : ' đến nơi làm việc'}: {workplaceDistanceLabel(log)}</Typography>
              {log.locationCapturedAt && <Typography variant="caption" component="div" color="text.secondary">Thời điểm lấy vị trí: {dayjs(log.locationCapturedAt).format('HH:mm:ss DD/MM/YYYY')}</Typography>}
              {log.ipAddress && <Typography variant="caption" component="div" color="text.secondary">IP: {log.ipAddress}</Typography>}
            </Box>)}
    </Box>
    {timesheet?.note && <Alert severity="info"><strong>Ghi chú duyệt công:</strong> {timesheet.note}</Alert>}
    {relatedLeaves}
    <Box className="space-y-2">
      <Typography variant="subtitle2">Nghỉ phép / Làm bù</Typography>
      {leaveError ? <Alert severity="warning">{leaveError}</Alert> : !leaves.length ? <Typography variant="body2" color="text.secondary">Không có đơn liên quan.</Typography> : leaves.map(leave => <Box key={leave.id} sx={{ p: 1.5, border: 1, borderColor: 'divider', borderRadius: 2 }}>
        <Box className="flex flex-wrap items-center gap-2"><Typography variant="body2" className="font-semibold">{leave.makeupDates?.includes(date) ? 'Ngày làm bù' : ({ ANNUAL: 'Phép năm', UNPAID: 'Nghỉ không lương', MAKEUP: 'Nghỉ có làm bù' }[leave.leaveType] || leave.leaveType)}</Typography>
          <Chip size="small" label={{ PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối' }[leave.approvalStatus] || leave.approvalStatus} /></Box>
        <Typography variant="body2">Ngày nghỉ: {dayjs(leave.fromDate).format('DD/MM/YYYY')} – {dayjs(leave.toDate).format('DD/MM/YYYY')}{leave.halfDayLeave ? ` · Nửa ngày (${leave.shiftWorkName || 'Theo ca nghỉ'})` : ''}</Typography>
        {!!leave.makeupDates?.length && <Typography variant="body2">Làm bù: {leave.makeupDates.map(value => dayjs(value).format('DD/MM/YYYY')).join(', ')}</Typography>}
        {leave.requestReason && <Typography variant="caption" color="text.secondary">{leave.requestReason}</Typography>}
      </Box>)}
    </Box>
  </Box>;
}
