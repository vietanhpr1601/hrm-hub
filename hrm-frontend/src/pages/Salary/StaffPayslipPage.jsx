import React, { useEffect, useMemo, useState } from 'react';
import { 
    Paper, 
    Box, 
    Typography,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Grid,
    Divider,
    List,
    ListItem,
    ListItemText,
    CircularProgress,
    Card,
    CardContent,
    Alert
} from '@mui/material';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import InfoIcon from '@mui/icons-material/Info';
import { toast } from 'sonner';

import usePayrollStore from '../../store/usePayrollStore';
import usePeriodStore from '../../store/usePeriodStore';
import { SalaryItemType } from '../../constants';
import { describeSalaryFormula, formatPayrollMoney, formatWorkDays, getSalaryLineType } from '../../utils/payroll';

const StaffPayslipPage = () => {
    const {
        allPeriods,
        loading: fetchingPeriods,
        loadAllPeriods
    } = usePeriodStore();

    const {
        myPayslip: payslip,
        loading,
        loadMyPayslip
    } = usePayrollStore();

    const [selectedPeriodId, setSelectedPeriodId] = useState('');

    const periods = useMemo(() => {
        if (!allPeriods) return [];
        return [...allPeriods].sort((a, b) => (b.year * 100 + b.month) - (a.year * 100 + a.month));
    }, [allPeriods]);

    // Fetch periods for the dropdown
    useEffect(() => {
        loadAllPeriods();
    }, [loadAllPeriods]);

    // Set default selectedPeriodId when periods are loaded
    useEffect(() => {
        if (periods.length > 0 && !selectedPeriodId) {
            setSelectedPeriodId(periods[0].id);
        }
    }, [periods, selectedPeriodId]);

    // Fetch payslip when period changes
    useEffect(() => {
        const loadPayslip = async () => {
            if (!selectedPeriodId) return;
            try {
                await loadMyPayslip(selectedPeriodId);
            } catch (error) {
                console.error('Failed to load payslip:', error);
                toast.info('Chưa có dữ liệu phiếu lương cho kỳ này');
            }
        };
        loadPayslip();
    }, [selectedPeriodId, loadMyPayslip]);

    const handlePeriodChange = (e) => {
        setSelectedPeriodId(e.target.value);
    };

    const formatMoney = formatPayrollMoney;

    const activePeriod = periods.find(p => p.id === selectedPeriodId);
    const hasLegacyFormula = payslip && (payslip.standardWorkDays == null || payslip.items?.some(item => !item.calculationType || !item.type));
    const renderItems = (type, title) => {
        const lines = (payslip?.items || []).filter(item => getSalaryLineType(item) === type);
        return <Box className="space-y-2">
            <Typography variant="subtitle2" fontWeight="bold">{title}</Typography>
            <List dense disablePadding className="border rounded-lg p-2">
                {lines.map((item, index) => <ListItem key={item.id || index} sx={{ gap: 2 }}>
                    <ListItemText primary={item.name || item.salaryItem?.name}
                        secondary={hasLegacyFormula ? 'Số tiền đã lưu; phiếu cũ chưa lưu đủ dữ liệu công thức.' : describeSalaryFormula(item, payslip.totalWorkDays, payslip.standardWorkDays)} />
                    <Typography variant="body2" fontWeight="bold" sx={{ whiteSpace: 'nowrap' }}>{formatMoney(item.calculatedValue)}</Typography>
                </ListItem>)}
                {!lines.length && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', p: 1 }}>Không có khoản lương.</Typography>}
            </List>
        </Box>;
    };

    return (
        <div className="space-y-6">
            {/* Header selection card */}
            <Paper elevation={0} className="p-4 border border-border">
                <Grid container spacing={3} alignItems="center" justifyContent="space-between">
                    <Grid item xs={12} md={6}>
                        <Box display="flex" alignItems="center" gap={2}>
                            <AccountBalanceWalletIcon color="primary" fontSize="large" />
                            <Box>
                                <Typography variant="h6" fontWeight="bold" className="text-text-primary">
                                    Phiếu lương cá nhân
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    Tra cứu chi tiết thu nhập, khấu trừ và thực nhận hàng tháng của bạn.
                                </Typography>
                            </Box>
                        </Box>
                    </Grid>
                    <Grid item xs={12} md={4}>
                        <FormControl variant="outlined" size="small" fullWidth>
                            <InputLabel id="select-payslip-period-label">Chọn kỳ lương</InputLabel>
                            <Select
                                labelId="select-payslip-period-label"
                                value={selectedPeriodId}
                                onChange={handlePeriodChange}
                                label="Chọn kỳ lương"
                                disabled={fetchingPeriods}
                            >
                                {periods.map((p) => (
                                    <MenuItem key={p.id} value={p.id}>
                                        {p.name}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Grid>
                </Grid>
            </Paper>

            {loading ? (
                <Box display="flex" justifyContent="center" py={10}>
                    <CircularProgress />
                </Box>
            ) : payslip ? (
                <Paper elevation={0} className="p-6 border border-border max-w-2xl mx-auto space-y-6">
                    {/* Payslip Header */}
                    <Box textAlign="center" className="space-y-1">
                        <Typography variant="h5" fontWeight="bold" className="text-text-primary uppercase tracking-wide">
                            Phiếu thanh toán lương
                        </Typography>
                        <Typography variant="subtitle1" fontWeight="semibold">
                            {activePeriod?.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" display="block">
                            Nhân viên: <strong>{payslip.staff?.displayName}</strong> ({payslip.staff?.staffCode})
                        </Typography>
                        <Typography variant="caption" color="text.secondary" display="block">
                            Phòng ban: <strong>{payslip.staff?.departmentName || payslip.staff?.department?.name || 'N/A'}</strong> | Chức vụ: <strong>{payslip.staff?.positionName || payslip.staff?.position?.name || 'N/A'}</strong>
                        </Typography>
                        <Box sx={{ mt: 1.5, display: 'flex', justifyContent: 'center' }}>
                            {payslip.paidStatus === 'PAID' ? (
                                <Box 
                                    display="inline-flex" 
                                    alignItems="center" 
                                    gap={0.5} 
                                    sx={{ 
                                        backgroundColor: (theme) => theme.palette.mode === 'light' ? '#e8f5e9' : 'rgba(46, 125, 50, 0.2)',
                                        color: (theme) => theme.palette.mode === 'light' ? '#2e7d32' : '#81c784',
                                        px: 2, 
                                        py: 0.5, 
                                        borderRadius: '16px',
                                        fontSize: '13px',
                                        fontWeight: 'bold'
                                    }}
                                >
                                    <CheckCircleIcon sx={{ fontSize: '16px' }} />
                                    Đã chi trả
                                </Box>
                            ) : (
                                <Box 
                                    display="inline-flex" 
                                    alignItems="center" 
                                    gap={0.5} 
                                    sx={{ 
                                        backgroundColor: (theme) => theme.palette.mode === 'light' ? '#ffeec2' : 'rgba(245, 124, 0, 0.2)',
                                        color: (theme) => theme.palette.mode === 'light' ? '#b56d00' : '#ffb74d',
                                        px: 2, 
                                        py: 0.5, 
                                        borderRadius: '16px',
                                        fontSize: '13px',
                                        fontWeight: 'bold'
                                    }}
                                >
                                    <InfoIcon sx={{ fontSize: '16px' }} />
                                    Chưa chi trả
                                </Box>
                            )}
                        </Box>
                    </Box>

                    <Divider />

                    {/* Summary Card */}
                    <Grid container spacing={3}>
                        <Grid item xs={6}>
                            <Card variant="outlined">
                                <CardContent className="p-3 text-center">
                                    <Typography variant="caption" color="text.secondary">Ngày công thực tế</Typography>
                                    <Typography variant="h6" fontWeight="bold" className="text-text-primary">
                                        {formatWorkDays(payslip.totalWorkDays)} công
                                    </Typography>
                                </CardContent>
                            </Card>
                        </Grid>
                        <Grid item xs={6}>
                            <Card variant="outlined">
                                <CardContent className="p-3 text-center">
                                    <Typography variant="caption" color="text.secondary">Công chuẩn đã lưu</Typography>
                                    <Typography variant="h6" fontWeight="bold">
                                        {payslip.standardWorkDays != null ? `${formatWorkDays(payslip.standardWorkDays)} công` : 'Chưa lưu'}
                                    </Typography>
                                </CardContent>
                            </Card>
                        </Grid>
                    </Grid>

                    {hasLegacyFormula && <Alert severity="info">
                        Phiếu cũ chưa lưu đầy đủ công thức và công chuẩn. Hiển thị số tiền đã lưu; loại khoản tham khảo cấu hình hiện tại.
                    </Alert>}
                    {renderItems(SalaryItemType.INCOME, '1. Khoản cộng (Thu nhập)')}
                    {renderItems(SalaryItemType.DEDUCTION, '2. Khoản trừ (Khấu trừ)')}
                    <Box display="flex" justifyContent="space-between">
                        <Typography variant="body2">Tổng thu nhập: {formatMoney(payslip.totalIncome)}</Typography>
                        <Typography variant="body2">Tổng khấu trừ: {formatMoney(payslip.totalDeduction)}</Typography>
                    </Box>
                    <Divider />

                    {/* Net Salary Display */}
                    <Box display="flex" justifyContent="space-between" alignItems="center" py={2} className="flex justify-between">
                        <Box>
                            <Typography variant="subtitle1" fontWeight="bold" className="text-text-primary">Thực lĩnh</Typography>
                        </Box>
                        <Typography variant="h4" fontWeight="bold">
                            {formatMoney(payslip.netSalary)}
                        </Typography>
                    </Box>

                    {payslip.paidStatus === 'PAID' && <Box sx={{ p: 2, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                        <Typography variant="subtitle2" fontWeight="bold" sx={{ mb: 1 }}>Thông tin thanh toán</Typography>
                        <Typography variant="body2">Số tiền: {payslip.paidAmount != null ? formatMoney(payslip.paidAmount) : 'Chưa lưu'}</Typography>
                        <Typography variant="body2">Ngày thanh toán: {payslip.paidDate || 'Chưa lưu'}</Typography>
                        <Typography variant="body2">Hình thức: {payslip.paymentMethod === 'BANK_TRANSFER' ? 'Chuyển khoản' : payslip.paymentMethod === 'CASH' ? 'Tiền mặt' : 'Chưa lưu'}</Typography>
                        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>Mã giao dịch / Chứng từ: {payslip.paymentReference || '—'}</Typography>
                        <Typography variant="body2">Người ghi nhận: {payslip.paidBy || 'Chưa lưu'}</Typography>
                    </Box>}

                    {payslip.note && (
                        <>
                            <Divider />
                            <Box sx={{ mt: 2, p: 1.5, borderRadius: '4px', border: '1px solid', borderColor: 'divider', backgroundColor: (theme) => theme.palette.mode === 'light' ? '#fafafa' : 'rgba(255, 255, 255, 0.02)' }}>
                                <Typography variant="caption" color="text.secondary" fontWeight="medium" display="block" mb={0.5}>Ghi chú từ bộ phận nhân sự:</Typography>
                                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{payslip.note}</Typography>
                            </Box>
                        </>
                    )}
                </Paper>
            ) : (
                <Paper elevation={0} className="p-8 border border-border text-center text-text-secondary">
                    <Typography variant="body1" className="italic">
                        Chưa có dữ liệu phiếu lương của bạn cho kỳ lương "{activePeriod?.name || 'được chọn'}".
                    </Typography>
                    <Typography variant="caption" color="text.secondary" className="block mt-2">
                        Phiếu lương sẽ hiển thị khi bộ phận phụ trách lập và duyệt bảng lương của kỳ này.
                    </Typography>
                </Paper>
            )}
        </div>
    );
};

export default StaffPayslipPage;

