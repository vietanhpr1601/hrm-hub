import React from 'react';
import { Card, CardContent, Typography, Box, Avatar, useTheme, Zoom, Skeleton } from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import EventNoteIcon from '@mui/icons-material/EventNote';
import WorkOutlineIcon from '@mui/icons-material/WorkOutline';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

const RecentActivities = ({ recentActivities, loading }) => {
    const { t } = useTranslation();
    const theme = useTheme();
    const isLight = theme.palette.mode === 'light';

    if (loading) {
        return (
            <Card
                sx={{
                    borderRadius: '16px',
                    boxShadow: isLight ? '0 2px 12px rgba(0,0,0,0.03)' : '0 4px 20px rgba(0,0,0,0.15)',
                    border: `1px solid ${isLight ? '#e5eaf2' : 'rgba(255, 255, 255, 0.08)'}`,
                    height: '100%'
                }}
            >
                <CardContent sx={{ p: '24px !important' }}>
                    <Skeleton variant="text" width="30%" height={24} className="mb-6" />
                    <div className="space-y-4">
                        {[...Array(3)].map((_, i) => (
                            <div key={i} className="flex gap-4 items-center">
                                <Skeleton variant="circular" width={40} height={40} className="shrink-0" />
                                <div className="flex-1 space-y-2">
                                    <Skeleton variant="text" width="70%" />
                                    <Skeleton variant="text" width="30%" />
                                </div>
                            </div>
                        ))}
                    </div>
                </CardContent>
            </Card>
        );
    }

    // Get color & icon based on activity type
    const getActivityConfig = (type) => {
        switch (type) {
            case 'STAFF':
                return {
                    color: '#2E7D32',
                    bgColor: isLight ? 'rgba(46, 125, 50, 0.1)' : 'rgba(102, 187, 106, 0.2)',
                    icon: <PersonAddIcon sx={{ fontSize: '18px', color: '#2E7D32' }} />
                };
            case 'LEAVE':
                return {
                    color: '#ED6C02',
                    bgColor: isLight ? 'rgba(237, 108, 2, 0.1)' : 'rgba(255, 183, 77, 0.2)',
                    icon: <EventNoteIcon sx={{ fontSize: '18px', color: '#ED6C02' }} />
                };
            default:
                return {
                    color: '#1976D2',
                    bgColor: isLight ? 'rgba(25, 118, 210, 0.1)' : 'rgba(102, 179, 255, 0.2)',
                    icon: <WorkOutlineIcon sx={{ fontSize: '18px', color: '#1976D2' }} />
                };
        }
    };

    return (
        <Zoom in={true}>
            <Card
                sx={{
                    borderRadius: '16px',
                    boxShadow: isLight ? '0 2px 12px rgba(0,0,0,0.03)' : '0 4px 20px rgba(0,0,0,0.15)',
                    border: `1px solid ${isLight ? '#e5eaf2' : 'rgba(255, 255, 255, 0.08)'}`,
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column'
                }}
            >
                <CardContent sx={{ p: '24px !important', display: 'flex', flexDirection: 'column', height: '100%' }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'text.primary', mb: 3 }}>
                        {t('dashboard.activity.title', 'Hoạt động gần đây')}
                    </Typography>

                    {recentActivities && recentActivities.length > 0 ? (
                        <Box 
                            sx={{ 
                                position: 'relative', 
                                pl: 3, 
                                py: 1, 
                                maxHeight: '350px', 
                                overflowY: 'auto',
                                flexGrow: 1,
                                '&::before': {
                                    content: '""',
                                    position: 'absolute',
                                    top: 10,
                                    bottom: 10,
                                    left: '11px',
                                    width: '2px',
                                    backgroundColor: isLight ? '#f1f5f9' : '#334155'
                                }
                            }}
                        >
                            {recentActivities.map((act, index) => {
                                const cfg = getActivityConfig(act.type);
                                return (
                                    <Box 
                                        key={index} 
                                        sx={{ 
                                            position: 'relative', 
                                            mb: 3, 
                                            '&:last-child': { mb: 0 },
                                            display: 'flex',
                                            alignItems: 'flex-start',
                                            gap: 2
                                        }}
                                    >
                                        {/* Dot / Icon container */}
                                        <Box 
                                            sx={{ 
                                                position: 'absolute', 
                                                left: '-24px', 
                                                top: '4px',
                                                zIndex: 2,
                                                width: '24px',
                                                height: '24px',
                                                borderRadius: '50%',
                                                backgroundColor: isLight ? '#ffffff' : '#0F1214',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                border: `2px solid ${cfg.color}`,
                                                boxShadow: isLight ? '0 2px 6px rgba(0,0,0,0.05)' : 'none'
                                            }}
                                        >
                                            <Box sx={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: cfg.color }} />
                                        </Box>

                                        {/* Avatar / Thumbnail */}
                                        <Avatar 
                                            src={act.staffAvatar} 
                                            alt={act.staffName}
                                            sx={{ 
                                                width: 36, 
                                                height: 36, 
                                                fontSize: '0.85rem',
                                                fontWeight: 600,
                                                backgroundColor: cfg.bgColor,
                                                color: cfg.color
                                            }}
                                        >
                                            {act.staffName ? act.staffName.substring(0, 1).toUpperCase() : '?'}
                                        </Avatar>

                                        {/* Text content */}
                                        <Box sx={{ flexGrow: 1 }}>
                                            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 0.5 }}>
                                                <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.primary', fontSize: '0.875rem' }}>
                                                    {act.title}
                                                </Typography>
                                                <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '0.75rem', ml: 1, whiteSpace: 'nowrap' }}>
                                                    {dayjs(act.timestamp).fromNow()}
                                                </Typography>
                                            </Box>
                                            <Typography variant="body2" sx={{ color: 'text.secondary', fontSize: '0.825rem', lineHeight: 1.4 }}>
                                                {act.description}
                                            </Typography>
                                        </Box>
                                    </Box>
                                );
                            })}
                        </Box>
                    ) : (
                        <Box sx={{ display: 'flex', height: '100%', flexGrow: 1, alignItems: 'center', justifyContent: 'center', minHeight: 200 }}>
                            <Typography variant="body2" color="text.secondary">
                                {t('dashboard.activity.no_data', 'Không có hoạt động nào gần đây')}
                            </Typography>
                        </Box>
                    )}
                </CardContent>
            </Card>
        </Zoom>
    );
};

export default RecentActivities;
