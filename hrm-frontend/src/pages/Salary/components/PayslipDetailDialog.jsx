import React, { useEffect, useState } from 'react';
import {
    Alert, Box, Button, Chip, CircularProgress, Divider, Grid, MenuItem,
    TextField, Typography,
} from '@mui/material';
import QrCodeIcon from '@mui/icons-material/QrCode';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { toast } from 'sonner';
import Popup from '../../../components/ui/Popup';
import BankTransferQrCard from './BankTransferQrCard';
import PayrollEmailStatus from './PayrollEmailStatus';
import { generateBankTransferQr, recordPayslipPayment } from '../../../services/payrollService';
import {
    canRecordPayslipPayment, describeSalaryFormula, formatPayrollMoney, formatWorkDays,
    getSalaryLineType, getVietnamToday, validatePayslipPayment,
} from '../../../utils/payroll';

const normalizeBankName = (value) => (value || '').normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toUpperCase().trim();

const Metadata = ({ label, children }) => (
    <Box>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Typography variant="body2" sx={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>{children || '—'}</Typography>
    </Box>
);

const PayslipDetailDialog = ({ open, onClose, detail, activePayroll, isAdmin = false, onUpdateSuccess }) => {
    const [payment, setPayment] = useState({});
    const [errors, setErrors] = useState({});
    const [saving, setSaving] = useState(false);
    const [recordedDetail, setRecordedDetail] = useState(null);
    const [qrOpen, setQrOpen] = useState(false);
    const [qrImage, setQrImage] = useState(null);
    const [qrLoading, setQrLoading] = useState(false);

    useEffect(() => {
        if (!open) return;
        setPayment({ paidDate: getVietnamToday(), paymentMethod: 'BANK_TRANSFER', paymentReference: '', note: detail?.note || '' });
        setErrors({});
        setRecordedDetail(null);
        setQrOpen(false);
        setQrImage(null);
    }, [open, detail]);

    const payslip = recordedDetail || detail;
    if (!payslip) return null;
    const canPay = isAdmin && canRecordPayslipPayment(payslip, activePayroll);
    const hasBankAccount = Boolean(payslip.staff?.bankBin && payslip.staff?.bankAccountNumber);
    const canUseQr = canPay && hasBankAccount && Number.isInteger(Number(payslip.netSalary));
    const periodLabel = activePayroll?.period?.name || payslip.payroll?.period?.name || '—';
    const transferContent = normalizeBankName(`THANH TOAN LUONG ${periodLabel} CHO ${payslip.staff?.displayName || ''}`);
    const hasLegacyFormula = payslip.standardWorkDays == null || payslip.items?.some((item) => !item.type || !item.calculationType);
    const changePayment = (key) => (event) => {
        setPayment((value) => ({ ...value, [key]: event.target.value }));
        setErrors((value) => ({ ...value, [key]: undefined }));
    };

    const handlePayment = async () => {
        if (!canPay || saving) return;
        const validation = validatePayslipPayment(payment);
        setErrors(validation);
        if (Object.keys(validation).length) return;
        if (!window.confirm(`Ghi nhận đã trả đủ ${formatPayrollMoney(payslip.netSalary)} cho ${payslip.staff?.displayName || 'nhân viên'} vào ngày ${payment.paidDate}?`)) return;
        setSaving(true);
        try {
            const response = await recordPayslipPayment(payslip.id, {
                ...payment, paymentReference: payment.paymentReference.trim(), note: payment.note.trim(),
            });
            const updated = response?.data?.data || response?.data;
            if (!updated?.id || updated.paidStatus !== 'PAID') throw new Error('Kết quả ghi nhận thanh toán không hợp lệ.');
            setRecordedDetail(updated);
            setQrOpen(false);
            onUpdateSuccess?.(updated);
            toast.success('Đã ghi nhận thanh toán lương.');
        } catch (error) {
            toast.error(error.response?.data?.message || error.message || 'Không thể ghi nhận thanh toán.');
        } finally {
            setSaving(false);
        }
    };

    const openQr = async () => {
        if (!canUseQr) return;
        setQrOpen(true);
        setQrLoading(true);
        setQrImage(null);
        try {
            const response = await generateBankTransferQr({
                bankCode: payslip.staff.bankBin,
                accountNumber: payslip.staff.bankAccountNumber,
                accountHolderName: normalizeBankName(payslip.staff.bankAccountName || payslip.staff.displayName),
                amount: Math.round(payslip.netSalary), transferContent,
            });
            const result = response?.data?.data || response?.data;
            if (!result?.qrImageBase64) throw new Error('Không thể tạo mã QR.');
            setQrImage(result.qrImageBase64);
        } catch (error) {
            toast.error(error.response?.data?.message || 'Không thể tạo mã QR.');
        } finally {
            setQrLoading(false);
        }
    };

    return (
        <>
            <Popup open={open} onClosePopup={() => !saving && onClose()} title="Chi tiết lương nhân viên" size="lg"
                action={<Box display="flex" justifyContent="flex-end" gap={1}>
                    <Button variant="outlined" color="inherit" onClick={onClose} disabled={saving}>Đóng</Button>
                    {canPay && <Button variant="contained" onClick={handlePayment} disabled={saving}
                        startIcon={saving ? <CircularProgress size={18} color="inherit" /> : <CheckCircleIcon />}>
                        Ghi nhận đã thanh toán
                    </Button>}
                </Box>}>
                <Grid container spacing={3} sx={{ pb: 2 }}>
                    <Grid item xs={12} md={7}>
                        <Typography variant="subtitle1" fontWeight="bold" sx={{ mb: 2 }}>Chi tiết tính lương</Typography>
                        <Box display="flex" gap={2} sx={{ mb: 2, p: 2, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                            <Metadata label="Công thực tế đã duyệt">{`${formatWorkDays(payslip.totalWorkDays)} công`}</Metadata>
                            <Metadata label="Công chuẩn đã lưu">{payslip.standardWorkDays != null ? `${formatWorkDays(payslip.standardWorkDays)} công` : 'Chưa lưu công chuẩn'}</Metadata>
                        </Box>
                        {hasLegacyFormula && <Alert severity="info" sx={{ mb: 2 }}>
                            Phiếu cũ chưa lưu đầy đủ công thức và công chuẩn. Hiển thị số tiền đã lưu; loại khoản tham khảo cấu hình hiện tại.
                        </Alert>}
                        {payslip.items?.map((item) => (
                            <Box key={item.id || item.salaryItem?.id} sx={{ p: 1.5, mb: 1, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                                <Box display="flex" justifyContent="space-between" gap={2}>
                                    <Typography variant="body2" fontWeight="bold">{item.name || item.salaryItem?.name}</Typography>
                                    <Typography variant="body2" fontWeight="bold">
                                        {getSalaryLineType(item) === 'DEDUCTION' ? '− ' : '+ '}{formatPayrollMoney(item.calculatedValue)}
                                    </Typography>
                                </Box>
                                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                                    {hasLegacyFormula ? 'Số tiền đã lưu; phiếu cũ chưa lưu đủ dữ liệu công thức.' : describeSalaryFormula(item, payslip.totalWorkDays, payslip.standardWorkDays)}
                                </Typography>
                            </Box>
                        ))}
                        {!payslip.items?.length && <Alert severity="info">Bảng chưa có dữ liệu khoản lương của nhân viên này. Cập nhật từ hồ sơ nhân viên hoặc bổ sung cấu hình tại mục “Khoản lương”.</Alert>}
                        <Divider sx={{ my: 2 }} />
                        <Box display="flex" justifyContent="space-between" mb={1}><Typography variant="body2">Tổng thu nhập</Typography><Typography variant="body2">{formatPayrollMoney(payslip.totalIncome)}</Typography></Box>
                        <Box display="flex" justifyContent="space-between" mb={1}><Typography variant="body2">Tổng khấu trừ</Typography><Typography variant="body2">{formatPayrollMoney(payslip.totalDeduction)}</Typography></Box>
                        <Box display="flex" justifyContent="space-between" color="primary.main"><Typography fontWeight="bold">Thực lĩnh</Typography><Typography fontWeight="bold">{formatPayrollMoney(payslip.netSalary)}</Typography></Box>
                    </Grid>
                    <Grid item xs={12} md={5}>
                        <Box display="flex" flexDirection="column" gap={1.5}>
                            <Metadata label="Nhân viên">{`${payslip.staff?.displayName || ''} (${payslip.staff?.staffCode || ''})`}</Metadata>
                            <Metadata label="Bảng lương / Kỳ lương">{`${activePayroll?.name || payslip.payroll?.name || ''} / ${periodLabel}`}</Metadata>
                            <Box><Chip size="small" label={payslip.paidStatus === 'PAID' ? 'Đã thanh toán' : 'Chưa thanh toán'} color={payslip.paidStatus === 'PAID' ? 'success' : 'warning'} /></Box>
                            <Metadata label="Ghi chú">{payslip.note}</Metadata>
                            <PayrollEmailStatus payslip={payslip} open={open} />
                            {payslip.paidStatus === 'PAID' && <>
                                <Divider />
                                <Typography variant="subtitle2" fontWeight="bold">Thông tin thanh toán đã ghi nhận</Typography>
                                <Metadata label="Số tiền đã thanh toán">{payslip.paidAmount != null ? formatPayrollMoney(payslip.paidAmount) : 'Chưa lưu'}</Metadata>
                                <Metadata label="Ngày thanh toán">{payslip.paidDate || 'Chưa lưu'}</Metadata>
                                <Metadata label="Hình thức">{payslip.paymentMethod === 'BANK_TRANSFER' ? 'Chuyển khoản' : payslip.paymentMethod === 'CASH' ? 'Tiền mặt' : 'Chưa lưu'}</Metadata>
                                <Metadata label="Mã giao dịch / Chứng từ">{payslip.paymentReference}</Metadata>
                                <Metadata label="Người ghi nhận">{payslip.paidBy || 'Chưa lưu'}</Metadata>
                            </>}
                            {canPay && <>
                                <Divider />
                                <Typography variant="subtitle2" fontWeight="bold">Ghi nhận thanh toán</Typography>
                                <Alert severity="info">Ghi nhận sau khi đã chi trả đủ {formatPayrollMoney(payslip.netSalary)} cho nhân viên.</Alert>
                                <TextField label="Ngày thanh toán" type="date" size="small" fullWidth value={payment.paidDate || ''}
                                    InputLabelProps={{ shrink: true }} inputProps={{ max: getVietnamToday() }} required disabled={saving}
                                    onChange={changePayment('paidDate')} error={Boolean(errors.paidDate)} helperText={errors.paidDate} />
                                <TextField label="Hình thức thanh toán" select size="small" fullWidth value={payment.paymentMethod || 'BANK_TRANSFER'} required disabled={saving}
                                    onChange={changePayment('paymentMethod')} error={Boolean(errors.paymentMethod)} helperText={errors.paymentMethod}>
                                    <MenuItem value="BANK_TRANSFER">Chuyển khoản</MenuItem><MenuItem value="CASH">Tiền mặt</MenuItem>
                                </TextField>
                                <TextField label="Mã giao dịch / Số chứng từ" size="small" fullWidth value={payment.paymentReference || ''}
                                    onChange={changePayment('paymentReference')} required={payment.paymentMethod === 'BANK_TRANSFER'} disabled={saving}
                                    inputProps={{ maxLength: 255 }} error={Boolean(errors.paymentReference)} helperText={errors.paymentReference} />
                                <TextField label="Ghi chú" size="small" fullWidth multiline rows={2} value={payment.note || ''}
                                    onChange={changePayment('note')} disabled={saving} inputProps={{ maxLength: 255 }} error={Boolean(errors.note)} helperText={errors.note} />
                                {open && canUseQr && payment.paymentMethod === 'BANK_TRANSFER' && <>
                                    <BankTransferQrCard bankName={payslip.staff?.bankName} bankAccountNumber={payslip.staff?.bankAccountNumber}
                                        bankAccountName={normalizeBankName(payslip.staff?.bankAccountName || payslip.staff?.displayName)}
                                        bankBin={payslip.staff?.bankBin} amount={payslip.netSalary} note={transferContent} onClick={openQr} />
                                    <Button variant="outlined" startIcon={<QrCodeIcon />} onClick={openQr}>Xem QR chuyển khoản</Button>
                                    <Typography variant="caption" color="text.secondary">QR hỗ trợ chuyển khoản. Chỉ thao tác ghi nhận thanh toán mới cập nhật trạng thái phiếu.</Typography>
                                </>}
                                {!Number.isInteger(Number(payslip.netSalary)) && payment.paymentMethod === 'BANK_TRANSFER' && <Alert severity="info">
                                    Phiếu cũ có số tiền lẻ; QR chỉ hỗ trợ số tiền nguyên đồng.
                                </Alert>}
                            </>}
                            {isAdmin && payslip.paidStatus !== 'PAID' && !canPay && <Alert severity="info">
                                {activePayroll?.status !== 'CONFIRMED' ? 'Duyệt bảng lương trước khi ghi nhận thanh toán.' : 'Chỉ ghi nhận thanh toán khi số tiền thực lĩnh lớn hơn 0.'}
                            </Alert>}
                        </Box>
                    </Grid>
                </Grid>
            </Popup>
            <Popup open={qrOpen && canUseQr} onClosePopup={() => setQrOpen(false)} title="QR chuyển khoản lương" size="sm"
                action={<Button onClick={() => setQrOpen(false)}>Đóng</Button>}>
                <Box display="flex" flexDirection="column" alignItems="center" gap={2} sx={{ pb: 2 }}>
                    <Typography fontWeight="bold">{formatPayrollMoney(payslip.netSalary)}</Typography>
                    <Typography variant="body2">{payslip.staff?.bankName} · {payslip.staff?.bankAccountNumber}</Typography>
                    <Box sx={{ width: 250, height: 250, bgcolor: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        {qrLoading ? <CircularProgress /> : qrImage ? <img src={qrImage} alt="QR chuyển khoản lương" style={{ width: '100%', height: '100%', objectFit: 'contain' }} /> : <Typography color="text.secondary">Không tải được QR</Typography>}
                    </Box>
                    <Typography variant="body2">{transferContent}</Typography>
                    <Alert severity="info">Sau khi chuyển khoản thành công, nhập mã giao dịch và ghi nhận thanh toán trên phiếu lương.</Alert>
                </Box>
            </Popup>
        </>
    );
};

export default PayslipDetailDialog;
