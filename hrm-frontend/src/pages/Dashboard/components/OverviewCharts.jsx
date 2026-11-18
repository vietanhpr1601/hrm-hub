import React from 'react';
import { Card, CardContent, Typography, Box, useTheme, Zoom, Skeleton } from '@mui/material';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const OverviewCharts = ({ recruitmentPipeline, loading }) => {
    const { t } = useTranslation();
    const theme = useTheme();
    const navigate = useNavigate();
    const isLight = theme.palette.mode === 'light';

    if (loading) {
        return (
                    <Card sx={{ borderRadius: '16px', border: '1px solid', borderColor: 'divider' }}>
                        <CardContent sx={{ p: '24px !important' }}>
                           <Skeleton variant="text" width="35%" height={24} className="mb-6" />
                           <Skeleton variant="rectangular" height={220} sx={{ borderRadius: '8px' }} />
                        </CardContent>
                    </Card>
        );
    }

    // Recruitment data from the dashboard summary.
    const recruitmentData = [
        { name: t('dashboard.recruit.screening', 'Sàng lọc CV'), count: recruitmentPipeline?.screening || 0, color: '#1976D2' },
        { name: t('dashboard.recruit.interview', 'Phỏng vấn'), count: recruitmentPipeline?.interview || 0, color: '#42A5F5' },
        { name: t('dashboard.recruit.qualified', 'Đạt yêu cầu'), count: recruitmentPipeline?.qualified || 0, color: '#26A69A' },
        { name: t('dashboard.recruit.waiting', 'Chờ việc'), count: recruitmentPipeline?.waiting || 0, color: '#FFA726' },
        { name: t('dashboard.recruit.onboarded', 'Đã nhận việc'), count: recruitmentPipeline?.onboarded || 0, color: '#66BB6A' }
    ];

    const handleRecruitClick = () => {
        navigate('/recruitments');
    };

    return (
        <div>
            {/* Recruitment Pipeline Chart */}
            <Zoom in={true}>
                <Card
                    sx={{
                        borderRadius: '16px',
                        boxShadow: isLight ? '0 2px 12px rgba(0,0,0,0.03)' : '0 4px 20px rgba(0,0,0,0.15)',
                        border: `1px solid ${isLight ? '#e5eaf2' : 'rgba(255, 255, 255, 0.08)'}`,
                        height: '100%'
                    }}
                >
                    <CardContent sx={{ p: '24px !important' }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'text.primary', mb: 3 }}>
                            {t('dashboard.recruit.title', 'Phễu tuyển dụng hiện tại')}
                        </Typography>
                        
                        <Box sx={{ width: '100%', height: 220 }}>
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart
                                    data={recruitmentData}
                                    layout="vertical"
                                    margin={{ top: 5, right: 30, left: 30, bottom: 5 }}
                                    onClick={handleRecruitClick}
                                    cursor="pointer"
                                >
                                    <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke={isLight ? '#f1f5f9' : '#334155'} />
                                    <XAxis type="number" stroke={theme.palette.text.secondary} fontSize={12} tickLine={false} axisLine={false} />
                                    <YAxis dataKey="name" type="category" stroke={theme.palette.text.secondary} fontSize={12} tickLine={false} axisLine={false} />
                                    <Tooltip 
                                        formatter={(value) => [`${value} ứng viên`]}
                                        contentStyle={{ 
                                            backgroundColor: isLight ? '#ffffff' : '#1f2327',
                                            borderColor: isLight ? '#e5eaf2' : 'rgba(255,255,255,0.08)',
                                            borderRadius: '8px'
                                        }}
                                    />
                                    <Bar dataKey="count" radius={[0, 4, 4, 0]} barSize={16}>
                                        {recruitmentData.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={entry.color} />
                                        ))}
                                    </Bar>
                                </BarChart>
                            </ResponsiveContainer>
                        </Box>
                    </CardContent>
                </Card>
            </Zoom>
        </div>
    );
};

export default OverviewCharts;
