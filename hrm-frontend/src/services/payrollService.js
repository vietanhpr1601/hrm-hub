import HttpService from './HttpService';

export const previewAutoPayroll = periodId => HttpService.get(`/payrolls/auto-draft/${periodId}`);
export const createAutoPayroll = (periodId, staffIds) => HttpService.post(`/payrolls/auto-draft/${periodId}`, { staffIds });
export const updatePayrollDetails = (id, details) => HttpService.put(`/payrolls/${id}/details`, details);



export const createPayroll = (periodId, name, code, description, staffIds) => {
    return HttpService.post(`/payrolls?periodId=${periodId}&name=${encodeURIComponent(name)}&code=${encodeURIComponent(code || '')}&description=${encodeURIComponent(description || '')}`,
        staffIds === undefined ? undefined : { staffIds });
};

export const getAllPayrolls = () => {
    return HttpService.get('/payrolls');
};

export const getPayrollsByPeriod = (periodId) => {
    return HttpService.get(`/payrolls/by-period/${periodId}`);
};

export const calculatePayroll = (payrollId) => {
    return HttpService.post(`/payrolls/${payrollId}/calculate`);
};

export const addPayrollStaffs = (payrollId, staffIds) =>
    HttpService.post(`/payrolls/${payrollId}/staffs`, { staffIds });

export const refreshStaffPayroll = (payslipId) =>
    HttpService.post(`/payrolls/payslip/${payslipId}/calculate`);

export const removePayrollStaff = (payslipId) =>
    HttpService.delete(`/payrolls/payslip/${payslipId}`);

export const recordPayslipPayment = (payslipId, payload) =>
    HttpService.post(`/payrolls/payslip/${payslipId}/payment`, payload);

export const getPayslipEmailStatus = (payslipId, options) =>
    HttpService.get(`/payrolls/payslip/${payslipId}/email`, { skipGlobalLoading: true, ...options });

export const getPayslipEmailStatuses = (payslipId, options) =>
    HttpService.get(`/payrolls/payslip/${payslipId}/emails`, { skipGlobalLoading: true, ...options });

export const retryPayslipEmail = (payslipId, eventType = 'PAYMENT') =>
    HttpService.post(`/payrolls/payslip/${payslipId}/email/retry`, null, { params: { eventType } });

export const getPayrollDetails = (payrollId) => {
    return HttpService.get(`/payrolls/${payrollId}`);
};

export const confirmPayroll = (payrollId) => {
    return HttpService.put(`/payrolls/${payrollId}/confirm`);
};

export const deletePayroll = (payrollId) => {
    return HttpService.delete(`/payrolls/${payrollId}`);
};

export const getMyPayslip = (periodId) => {
    return HttpService.get(`/payrolls/my-payslip?periodId=${periodId}`);
};

export const generateBankTransferQr = (payload) => {
    return HttpService.post('/bank-transfer/qr/generate', payload);
};
