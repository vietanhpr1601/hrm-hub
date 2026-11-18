import AttendanceDetails from './components/AttendanceDetails';
import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { 
    Alert, Grid, Box, Card, CardContent, Typography, Button,
    Chip, CircularProgress, IconButton, Tooltip,
    Tabs, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper
} from '@mui/material';
import { 
    ChevronLeft, ChevronRight, CalendarToday, ListAlt
} from '@mui/icons-material';
import dayjs from 'dayjs';

import useAuthStore from '../../store/useAuthStore';
import useTimesheetStore from '../../store/useTimesheetStore';
import Popup from '../../components/ui/Popup';
import { getTimesheetById } from '../../services/timesheetService';

const TimekeepingCalendar = () => {
    const user = useAuthStore(state => state.user);
    const staffId = user?.staffId;
    const [searchParams] = useSearchParams();
    const linkedDate = searchParams.get('date');
    const linkedTimesheetId = searchParams.get('timesheetId');

    const { 
        myTimesheets: cachedTimesheets, loading: tsLoading, loadMyTimesheets
    } = useTimesheetStore();

    // Local states
    const [currentDate, setCurrentDate] = useState(dayjs());
    const [selectedDayRecord, setSelectedDayRecord] = useState(null);
    const [detailDialogOpen, setDetailDialogOpen] = useState(false);
    const [viewMode, setViewMode] = useState('calendar'); // 'calendar' or 'table'
    const [linkedError, setLinkedError] = useState('');
    const monthKey = currentDate.format('YYYY-MM');
    const myTimesheets = useMemo(() => cachedTimesheets.filter(record => staffId && record.staffId === staffId && record.workingDate?.startsWith(monthKey)), [cachedTimesheets, staffId, monthKey]);

    useEffect(() => {
        let active = true;
        setDetailDialogOpen(false);
        setSelectedDayRecord(null);
        setLinkedError('');
        if (!linkedTimesheetId || !staffId) return undefined;
        if (!/^\d{4}-\d{2}-\d{2}$/.test(linkedDate || '') || dayjs(linkedDate).format('YYYY-MM-DD') !== linkedDate) {
            setLinkedError('Ngày chấm công trong thông báo không hợp lệ.');
            return undefined;
        }
        setCurrentDate(dayjs(linkedDate));
        getTimesheetById(linkedTimesheetId).then(response => {
            const session = useAuthStore.getState().user;
            if (!active || session?.id !== user?.id || session?.staffId !== staffId) return;
            const record = response?.data;
            if (!record || record.staffId !== staffId || record.workingDate !== linkedDate) {
                throw new Error('Bảng chấm công này không thuộc hồ sơ nhân viên đang liên kết với tài khoản.');
            }
            setSelectedDayRecord(record);
            setDetailDialogOpen(true);
        }).catch(error => {
            if (active) setLinkedError(error.response?.data?.message || error.message || 'Không thể mở bảng chấm công từ thông báo.');
        });
        return () => { active = false; };
    }, [linkedDate, linkedTimesheetId, staffId, user?.id]);

    // Helper to format shift time (removing seconds, e.g. "08:00:00" -> "08:00")
    const formatShiftTime = (timeStr) => {
        if (!timeStr) return '';
        return timeStr.length >= 5 ? timeStr.substring(0, 5) : timeStr;
    };

    // Load monthly timesheets when month or staffId changes
    useEffect(() => {
        if (staffId) {
            const startOfMonth = currentDate.startOf('month').format('YYYY-MM-DD');
            const endOfMonth = currentDate.endOf('month').format('YYYY-MM-DD');
            loadMyTimesheets(staffId, startOfMonth, endOfMonth);
        }
    }, [staffId, currentDate, loadMyTimesheets]);

    // Calculate monthly summary statistics from myTimesheets
    const monthlySummary = useMemo(() => {
        let totalWorkRatio = 0;
        let totalStandardHours = 0;
        let totalOvertimeHours = 0;
        let totalLateMinutes = 0;
        let totalEarlyMinutes = 0;
        let daysPresent = 0;

        myTimesheets.forEach(ts => {
            totalWorkRatio += ts.totalWorkRatio || 0;
            totalStandardHours += ts.standardHours || 0;
            totalOvertimeHours += ts.overtimeHours || 0;
            
            if (ts.totalWorkRatio && ts.totalWorkRatio > 0) {
                daysPresent++;
            }

            const details = ts.details || [];
            details.forEach(d => {
                totalLateMinutes += d.lateMinutes || 0;
                totalEarlyMinutes += d.earlyMinutes || 0;
            });
        });

        return {
            totalWorkRatio,
            totalStandardHours,
            totalOvertimeHours,
            totalLateMinutes,
            totalEarlyMinutes,
            daysPresent
        };
    }, [myTimesheets]);

    // Generate rows for table view (every actual day of the month)
    const tableRows = useMemo(() => {
        const daysInMonth = currentDate.daysInMonth();
        const rows = [];
        for (let d = 1; d <= daysInMonth; d++) {
            const date = currentDate.date(d);
            const dateStr = date.format('YYYY-MM-DD');
            const record = myTimesheets.find(ts => ts.workingDate === dateStr);
            rows.push({
                day: d,
                dateStr,
                dateObj: date,
                record
            });
        }
        return rows;
    }, [currentDate, myTimesheets]);

    // Calendar Generation Helper
    const generateCalendarDays = () => {
        const startOfMonth = currentDate.startOf('month');
        const startDayOfWeek = startOfMonth.day() === 0 ? 6 : startOfMonth.day() - 1; // Align Mon-Sun (Mon = 0, Sun = 6)
        const daysInMonth = currentDate.daysInMonth();
        
        const calendarCells = [];
        
        // Blank spaces for previous month
        for (let i = 0; i < startDayOfWeek; i++) {
            calendarCells.push({ day: null, dateStr: null });
        }
        
        // Days of this month
        for (let d = 1; d <= daysInMonth; d++) {
            const date = currentDate.date(d);
            calendarCells.push({
                day: d,
                dateStr: date.format('YYYY-MM-DD'),
                dateObj: date
            });
        }
        
        return calendarCells;
    };

    // Find timesheet for a date
    const getTimesheetForDate = (dateStr) => {
        if (!dateStr) return null;
        return myTimesheets.find(ts => ts.workingDate === dateStr);
    };

    // Status Chip Colors
    const getStatusChip = (status) => {
        switch (status) {
            case 'APPROVED':
                return <Chip label="Đã duyệt" size="small" className="bg-emerald-50 text-emerald-700 border border-emerald-200" />;
            case 'SUBMITTED':
                return <Chip label="Chờ duyệt" size="small" className="bg-amber-50 text-amber-700 border border-amber-200" />;
            case 'REJECTED':
                return <Chip label="Từ chối" size="small" className="bg-rose-50 text-rose-700 border border-rose-200" />;
            default:
                return <Chip label="Nháp" size="small" className="bg-gray-100 text-gray-600 border border-gray-200" />;
        }
    };

    const handleDayClick = async (cell) => {
        if (!cell.dateStr) return;
        setLinkedError('');
        
        // Show current local cached record first so it opens instantly
        const initialRecord = getTimesheetForDate(cell.dateStr) || { 
            workingDate: cell.dateStr,
            isNew: true 
        };
        setSelectedDayRecord(initialRecord);
        setDetailDialogOpen(true);


    };

    const calendarCells = generateCalendarDays();
    const daysOfWeek = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

    return (
        <Box className="space-y-6">
            {linkedError && <Alert severity="error">{linkedError}</Alert>}
            {/* Timesheet Card View */}
            <Card className="shadow-xs rounded-xs border border-gray-100 dark:border-zinc-800 bg-white dark:bg-zinc-900">
                {/* Month navigation header */}
                <Box className="flex flex-col sm:flex-row justify-between items-start sm:items-center p-6 border-b border-gray-100 dark:border-zinc-800 gap-4">
                    <Box>
                        <Typography variant="h6" className="font-bold text-gray-800 dark:text-gray-100">
                            Chi tiết lịch sử chấm công
                        </Typography>
                        <Typography variant="caption" className="text-gray-400 dark:text-gray-500">
                            Theo dõi và đối chiếu chi tiết các lượt check-in/out trong tháng
                        </Typography>
                    </Box>
                    
                    <Box className="flex flex-wrap items-center gap-4 w-full sm:w-auto">
                        <Tabs 
                            value={viewMode} 
                            onChange={(e, val) => setViewMode(val)}
                            indicatorColor="primary"
                            textColor="primary"
                            className="border border-gray-200 dark:border-zinc-800 rounded-lg p-0.5 min-h-0 bg-gray-50 dark:bg-zinc-950/20"
                            sx={{
                                '& .MuiTabs-indicator': {
                                    height: '100%',
                                    borderRadius: '6px',
                                    zIndex: 0,
                                    backgroundColor: (theme) => theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.08)' : 'rgba(25, 118, 210, 0.08)'
                                },
                                '& .MuiTab-root': {
                                    minHeight: '32px',
                                    padding: '6px 16px',
                                    zIndex: 1,
                                    textTransform: 'none',
                                    fontWeight: 'bold',
                                    fontSize: '13px',
                                    borderRadius: '6px',
                                    transition: 'all 0.2s',
                                    color: 'hsl(var(--foreground) / 0.6)',
                                    '&.Mui-selected': {
                                        color: 'primary.main',
                                    }
                                }
                            }}
                        >
                            <Tab 
                                value="calendar" 
                                icon={<CalendarToday className="text-sm mr-1" sx={{ fontSize: '16px' }} />} 
                                iconPosition="start" 
                                label="Dạng Lịch" 
                            />
                            <Tab 
                                value="table" 
                                icon={<ListAlt className="text-sm mr-1" sx={{ fontSize: '16px' }} />} 
                                iconPosition="start" 
                                label="Dạng Bảng" 
                            />
                        </Tabs>

                        <Box className="flex items-center gap-2">
                            <IconButton 
                                onClick={() => setCurrentDate(currentDate.subtract(1, 'month'))}
                                className="border border-gray-200 dark:border-zinc-800 hover:bg-gray-50 dark:hover:bg-zinc-800 text-gray-600 dark:text-gray-400"
                                size="small"
                            >
                                <ChevronLeft />
                            </IconButton>
                            <Typography variant="subtitle1" className="font-bold text-gray-700 dark:text-gray-200 px-2 min-w-[120px] text-center">
                                Tháng {currentDate.format('MM / YYYY')}
                            </Typography>
                            <IconButton 
                                onClick={() => setCurrentDate(currentDate.add(1, 'month'))}
                                className="border border-gray-200 dark:border-zinc-800 hover:bg-gray-50 dark:hover:bg-zinc-800 text-gray-600 dark:text-gray-400"
                                size="small"
                            >
                                <ChevronRight />
                            </IconButton>
                        </Box>
                    </Box>
                </Box>

                <CardContent className="p-6">
                    {tsLoading ? (
                        <Box className="h-[400px] flex items-center justify-center">
                            <CircularProgress />
                        </Box>
                    ) : viewMode === 'calendar' ? (
                        <Box>
                            {/* Days of week header */}
                            <Box className="grid grid-cols-7 gap-2 mb-2 text-center">
                                {daysOfWeek.map((day, index) => (
                                    <Typography 
                                        key={index} 
                                        variant="subtitle2" 
                                        className={`font-semibold py-2 text-gray-500 dark:text-gray-400 ${day === 'CN' ? 'text-red-500 dark:text-red-400' : ''}`}
                                    >
                                        {day}
                                    </Typography>
                                ))}
                            </Box>

                            {/* Calendar Days grid */}
                            <Box className="grid grid-cols-7 gap-2">
                                {calendarCells.map((cell, index) => {
                                    const isToday = cell.dateStr === dayjs().format('YYYY-MM-DD');
                                    const record = getTimesheetForDate(cell.dateStr);
                                    const isWeekend = cell.dateObj && (cell.dateObj.day() === 0 || cell.dateObj.day() === 6);

                                    return (
                                        <Box
                                            key={index}
                                            onClick={() => cell.day && handleDayClick(cell)}
                                            className={`
                                                min-h-[90px] p-2 rounded-xl border flex flex-col justify-between transition-all duration-200
                                                ${cell.day ? 'cursor-pointer hover:border-primary hover:shadow-sm' : 'bg-gray-50/50 dark:bg-zinc-950/10 border-transparent'}
                                                ${isToday ? 'border-primary ring-1 ring-primary' : 'border-gray-100 dark:border-zinc-800'}
                                                ${isWeekend ? 'bg-gray-50/70 dark:bg-zinc-950/30' : 'bg-white dark:bg-zinc-900'}
                                            `}
                                        >
                                            {cell.day ? (
                                                <>
                                                    <Box className="flex justify-between items-start">
                                                        <Typography 
                                                            variant="body2" 
                                                            className={`font-bold ${isToday ? 'text-primary' : isWeekend ? 'text-red-400 dark:text-red-500' : 'text-gray-700 dark:text-gray-200'}`}
                                                        >
                                                            {cell.day}
                                                        </Typography>
                                                        {record && (
                                                            <Box className="w-1.5 h-1.5 rounded-full bg-primary" />
                                                        )}
                                                    </Box>
                                                    {record ? (() => {
                                                         let minCheckIn = null;
                                                         let maxCheckOut = null;
                                                         let cellHours = 0;
                                                         const details = record.details || [];
                                                         
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
                                                             if (d.checkInTime && d.checkOutTime) {
                                                                 const diff = dayjs(d.checkOutTime).diff(dayjs(d.checkInTime), 'hour', true);
                                                                 if (diff > 0) cellHours += diff;
                                                             }
                                                         });

                                                         const displayHours = (record.standardHours !== undefined && record.standardHours !== null && record.overtimeHours !== undefined && record.overtimeHours !== null)
                                                             ? (record.standardHours + record.overtimeHours)
                                                             : cellHours;

                                                         return (
                                                             <Box className="space-y-1">
                                                                 <Box className="flex flex-col text-[10px] text-gray-500 dark:text-gray-400">
                                                                     <span>{minCheckIn ? minCheckIn.format('HH:mm') : '--:--'}</span>
                                                                     <span>{maxCheckOut ? maxCheckOut.format('HH:mm') : '--:--'}</span>
                                                                 </Box>
                                                                 <Box className="flex justify-between items-center text-[9px]">
                                                                     <span className="font-semibold text-emerald-600 dark:text-emerald-500">
                                                                         {record.totalWorkRatio || 0}
                                                                     </span>
                                                                     {displayHours > 0 ? (
                                                                         <span className="text-primary font-semibold">
                                                                             {displayHours.toFixed(1)}
                                                                         </span>
                                                                     ) : null}
                                                                 </Box>
                                                             </Box>
                                                         );
                                                     })() : cell.day && (
                                                        <Box className="text-[10px] text-gray-300 dark:text-gray-600 italic">Vắng / Nghỉ</Box>
                                                     )}
                                                </>
                                            ) : null}
                                        </Box>
                                    );
                                })}
                            </Box>
                        </Box>
                    ) : (
                        <Box className="overflow-x-auto">
                            <TableContainer component={Paper} className="shadow-none border border-gray-100 dark:border-zinc-800 rounded-xl overflow-hidden bg-white dark:bg-zinc-900">
                                <Table size="small" aria-label="timesheet table" className="min-w-[800px]">
                                    <TableHead>
                                        <TableRow className="bg-gray-50 dark:bg-zinc-800/50">
                                            <TableCell align="center" className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Ngày</TableCell>
                                            <TableCell align="center" className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Giờ Vào/Ra</TableCell>
                                            <TableCell className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Ca áp dụng</TableCell>
                                            <TableCell align="center" className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Công</TableCell>
                                            <TableCell align="center" className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Giờ chuẩn</TableCell>
                                            <TableCell align="center" className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Giờ OT</TableCell>
                                            <TableCell align="center" className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Đi muộn</TableCell>
                                            <TableCell align="center" className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Về sớm</TableCell>
                                            <TableCell align="center" className="font-bold text-gray-700 dark:text-gray-300 py-3 border-b border-gray-100 dark:border-zinc-800">Trạng thái</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {tableRows.map((row) => {
                                            const { day, dateStr, dateObj, record } = row;
                                            const isWeekend = dateObj.day() === 0 || dateObj.day() === 6;
                                            const isToday = dateStr === dayjs().format('YYYY-MM-DD');
                                            
                                            let minCheckIn = null;
                                            let maxCheckOut = null;
                                            let displayStd = 0;
                                            let displayOt = 0;
                                            let lateMinutes = 0;
                                            let earlyMinutes = 0;
                                            
                                            if (record) {
                                                const details = record.details || [];
                                                details.forEach(d => {
                                                    if (d.checkInTime) {
                                                        const ci = dayjs(d.checkInTime);
                                                        if (!minCheckIn || ci.isBefore(minCheckIn)) minCheckIn = ci;
                                                    }
                                                    if (d.checkOutTime) {
                                                        const co = dayjs(d.checkOutTime);
                                                        if (!maxCheckOut || co.isAfter(maxCheckOut)) maxCheckOut = co;
                                                    }
                                                    lateMinutes += d.lateMinutes || 0;
                                                    earlyMinutes += d.earlyMinutes || 0;
                                                });
                                                displayStd = record.standardHours || 0;
                                                displayOt = record.overtimeHours || 0;
                                            }

                                            return (
                                                <TableRow 
                                                    key={day}
                                                    onClick={() => handleDayClick({ day, dateStr, dateObj })}
                                                    className={`
                                                        hover:bg-gray-50/50 dark:hover:bg-zinc-800/30 cursor-pointer transition-colors
                                                        ${isToday ? 'bg-primary/5 dark:bg-primary/5' : ''}
                                                        ${isWeekend ? 'bg-gray-50/20 dark:bg-zinc-950/10' : ''}
                                                    `}
                                                >
                                                    {/* Day */}
                                                    <TableCell align="center" className="py-2.5 border-b border-gray-100 dark:border-zinc-800">
                                                        <Box className="flex flex-col items-center">
                                                            <span className={`font-bold ${isToday ? 'text-primary' : isWeekend ? 'text-red-500' : 'text-gray-700 dark:text-gray-300'}`}>
                                                                {day}
                                                            </span>
                                                            <span className="text-[10px] text-gray-400 font-medium">
                                                                {dateObj.format('dddd') === 'Sunday' ? 'Chủ Nhật' : 
                                                                 dateObj.format('dddd') === 'Saturday' ? 'Thứ Bảy' : 
                                                                 dateObj.format('dddd') === 'Monday' ? 'Thứ Hai' : 
                                                                 dateObj.format('dddd') === 'Tuesday' ? 'Thứ Ba' : 
                                                                 dateObj.format('dddd') === 'Wednesday' ? 'Thứ Tư' : 
                                                                 dateObj.format('dddd') === 'Thursday' ? 'Thứ Năm' : 'Thứ Sáu'}
                                                            </span>
                                                        </Box>
                                                    </TableCell>

                                                    {/* Check In / Out */}
                                                    <TableCell align="center" className="py-2.5 border-b border-gray-100 dark:border-zinc-800 whitespace-nowrap">
                                                        {minCheckIn || maxCheckOut ? (
                                                            <Box className="text-xs space-y-0.5">
                                                                <div className="whitespace-nowrap">Vào: <span className="font-semibold text-emerald-600">{minCheckIn ? minCheckIn.format('HH:mm') : '--:--'}</span></div>
                                                                <div className="whitespace-nowrap">Ra: <span className="font-semibold text-indigo-600">{maxCheckOut ? maxCheckOut.format('HH:mm') : '--:--'}</span></div>
                                                            </Box>
                                                        ) : (
                                                            <span className="text-gray-300 dark:text-gray-600 text-xs">--:--</span>
                                                        )}
                                                    </TableCell>

                                                    {/* Applied Shifts */}
                                                    <TableCell className="py-2.5 border-b border-gray-100 dark:border-zinc-800">
                                                        {record && record.details && record.details.length > 0 ? (
                                                            <Box className="space-y-0.5 text-xs">
                                                                {record.details.map((d, idx) => d.shift && (
                                                                    <div key={idx} className="font-medium text-gray-600 dark:text-gray-400 whitespace-nowrap">
                                                                        {d.shift.name} ({formatShiftTime(d.shift.startTime)} - {formatShiftTime(d.shift.endTime)})
                                                                    </div>
                                                                ))}
                                                            </Box>
                                                        ) : (
                                                            <span className="text-gray-400 text-xs">Nghỉ / Vắng</span>
                                                        )}
                                                    </TableCell>

                                                    {/* Total Work Ratio */}
                                                    <TableCell align="center" className="py-2.5 border-b border-gray-100 dark:border-zinc-800 font-bold text-emerald-600 text-sm">
                                                        {record ? `${record.totalWorkRatio || 0}` : '0'}
                                                    </TableCell>

                                                    {/* Standard Hours */}
                                                    <TableCell align="center" className="py-2.5 border-b border-gray-100 dark:border-zinc-800 font-semibold text-gray-700 dark:text-gray-300">
                                                        {record ? `${displayStd.toFixed(2)}` : '0'}
                                                    </TableCell>

                                                    {/* OT Hours */}
                                                    <TableCell align="center" className="py-2.5 border-b border-gray-100 dark:border-zinc-800 font-bold text-amber-600">
                                                        {record && displayOt > 0 ? `${displayOt.toFixed(2)}` : '0'}
                                                    </TableCell>

                                                    {/* Late minutes */}
                                                    <TableCell align="center" className="py-2.5 border-b border-gray-100 dark:border-zinc-800">
                                                        {record && lateMinutes > 0 ? (
                                                            <Chip 
                                                                label={`${lateMinutes} phút`} 
                                                                size="small" 
                                                                className="bg-rose-50 text-rose-700 border border-rose-100 font-bold" 
                                                                sx={{ height: '20px', fontSize: '11px' }}
                                                            />
                                                        ) : (
                                                            <span className="text-gray-300 dark:text-gray-600">-</span>
                                                        )}
                                                    </TableCell>

                                                    {/* Early minutes */}
                                                    <TableCell align="center" className="py-2.5 border-b border-gray-100 dark:border-zinc-800">
                                                        {record && earlyMinutes > 0 ? (
                                                            <Chip 
                                                                label={`${earlyMinutes} phút`} 
                                                                size="small" 
                                                                className="bg-rose-50 text-rose-700 border border-rose-100 font-bold" 
                                                                sx={{ height: '20px', fontSize: '11px' }}
                                                            />
                                                        ) : (
                                                            <span className="text-gray-300 dark:text-gray-600">-</span>
                                                        )}
                                                    </TableCell>

                                                    {/* Status */}
                                                    <TableCell align="center" className="py-2.5 border-b border-gray-100 dark:border-zinc-800">
                                                        {record ? getStatusChip(record.status) : (
                                                            <span className="text-gray-300 dark:text-gray-600 italic text-xs">-</span>
                                                        )}
                                                    </TableCell>
                                                </TableRow>
                                            );
                                        })}

                                        {/* Total Row */}
                                        <TableRow className="bg-gray-50 dark:bg-zinc-800/80 border-t-2 border-gray-200 dark:border-zinc-700">
                                            <TableCell align="center" className="py-3 font-extrabold text-gray-800 dark:text-gray-200 border-b-0">TỔNG CỘNG</TableCell>
                                            <TableCell align="center" className="py-3 border-b-0"></TableCell>
                                            <TableCell className="py-3 font-semibold text-gray-500 border-b-0">Tổng các ngày trong tháng</TableCell>
                                            <TableCell align="center" className="py-3 font-extrabold text-emerald-600 text-sm border-b-0">
                                                {monthlySummary.totalWorkRatio.toFixed(2)}
                                            </TableCell>
                                            <TableCell align="center" className="py-3 font-extrabold text-blue-600 text-sm border-b-0">
                                                {monthlySummary.totalStandardHours.toFixed(2)}
                                            </TableCell>
                                            <TableCell align="center" className="py-3 font-extrabold text-amber-600 text-sm border-b-0">
                                                {monthlySummary.totalOvertimeHours.toFixed(2)}
                                            </TableCell>
                                            <TableCell align="center" className="py-3 font-extrabold text-rose-600 text-sm border-b-0">
                                                {monthlySummary.totalLateMinutes > 0 ? `${monthlySummary.totalLateMinutes}p` : '0p'}
                                            </TableCell>
                                            <TableCell align="center" className="py-3 font-extrabold text-rose-600 text-sm border-b-0">
                                                {monthlySummary.totalEarlyMinutes > 0 ? `${monthlySummary.totalEarlyMinutes}p` : '0p'}
                                            </TableCell>
                                            <TableCell align="center" className="py-3 border-b-0"></TableCell>
                                        </TableRow>
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Box>
                    )}
                </CardContent>
            </Card>

            {/* Daily Timesheet Detail Dialog using shared Popup component */}
            <Popup
                open={detailDialogOpen && (!selectedDayRecord?.staffId || selectedDayRecord.staffId === staffId)}
                onClosePopup={() => setDetailDialogOpen(false)}
                title={selectedDayRecord ? `Chi tiết chấm công ngày ${dayjs(selectedDayRecord.workingDate).format('DD/MM/YYYY')}` : ''}
                size="sm"
                action={
                    <Button 
                        onClick={() => setDetailDialogOpen(false)} 
                        variant="contained" 
                        color="primary"
                        className="font-bold px-5 py-1.5 rounded-lg shadow-sm"
                    >
                        Đóng
                    </Button>
                }
            >
                {selectedDayRecord && <AttendanceDetails timesheet={selectedDayRecord} staffId={staffId} />}
            </Popup>
        </Box>
    );
};

export default TimekeepingCalendar;
