import React from 'react';
import { Skeleton } from '@mui/material';
import useAuthStore from '../../../store/useAuthStore';
import EventNoteIcon from '@mui/icons-material/EventNote';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import 'dayjs/locale/vi';

const WelcomeBanner = ({ kpiSummary, loading }) => {
    const { t } = useTranslation();
    const user = useAuthStore(state => state.user);

    if (loading) {
        return (
            <Skeleton variant="rectangular" height={160} sx={{ borderRadius: '16px' }} width="100%" />
        );
    }

    // Format current date in Vietnamese
    dayjs.locale('vi');
    const formattedDate = dayjs().format('dddd, [ngày] D [tháng] M, YYYY');
    const capitalizedDate = formattedDate.charAt(0).toUpperCase() + formattedDate.slice(1);

    // Get time-based greeting
    const getGreeting = () => {
        const hour = dayjs().hour();
        if (hour < 12) return t('dashboard.greeting.morning', 'Chào buổi sáng');
        if (hour < 18) return t('dashboard.greeting.afternoon', 'Chào buổi chiều');
        return t('dashboard.greeting.evening', 'Chào buổi tối');
    };

    return (
        <div className="relative overflow-hidden rounded-2xl p-4 md:px-8 text-white bg-gradient-to-br from-[#1976D2] to-[#7B1FA2] dark:from-[#0d47a1] dark:to-[#4a148c] shadow-[0_8px_32px_rgba(25,118,210,0.15)] dark:shadow-[0_8px_32px_rgba(0,0,0,0.3)]">
            {/* Background Blob 1 */}
            <div className="absolute -top-1/2 -right-1/4 w-[300px] h-[300px] rounded-full bg-white/5 blur-[30px] pointer-events-none" />

            {/* Background Blob 2 */}
            <div className="absolute -bottom-1/3 -left-10 w-[250px] h-[250px] rounded-full bg-white/3 blur-[20px] pointer-events-none" />

            {/* Content */}
            <h2 className="text-xl md:text-2xl font-bold mb-1 drop-shadow-sm">
                {getGreeting()}, {user?.displayName || 'Admin'} 👋
            </h2>
            <p className="text-sm md:text-base opacity-85 mb-6 font-medium">
                {capitalizedDate}
            </p>

            <div className="flex flex-wrap gap-3 relative z-10">
                {kpiSummary?.pendingLeaveRequests > 0 && (
                    <div className="flex items-center gap-2 px-4 py-2 rounded-full bg-white/20 hover:bg-white/30 text-white text-sm font-medium backdrop-blur-sm border border-white/10 transition-colors duration-200 cursor-pointer">
                        <EventNoteIcon className="w-[18px] h-[18px] text-white" />
                        <span>
                            {kpiSummary.pendingLeaveRequests} {t('dashboard.alerts.pending_leaves', 'đơn nghỉ phép chờ duyệt')}
                        </span>
                    </div>
                )}

                {kpiSummary?.pendingTimesheetsToday > 0 && (
                    <div className="flex items-center gap-2 px-4 py-2 rounded-full bg-white/20 hover:bg-white/30 text-white text-sm font-medium backdrop-blur-sm border border-white/10 transition-colors duration-200 cursor-pointer">
                        <FactCheckIcon className="w-[18px] h-[18px] text-white" />
                        <span>
                            {kpiSummary.pendingTimesheetsToday} {t('dashboard.alerts.pending_timesheets_today', 'chấm công chờ duyệt trong ngày')}
                        </span>
                    </div>
                )}
            </div>
        </div>
    );
};

export default WelcomeBanner;
