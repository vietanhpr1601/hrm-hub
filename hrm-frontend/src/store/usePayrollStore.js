import { create } from 'zustand';
import {
    getAllPayrolls,
    calculatePayroll,
    getPayrollDetails,
    confirmPayroll,
    deletePayroll,
    getMyPayslip,
    createPayroll
} from '../services/payrollService';

let detailRequestId = 0;
let myPayslipRequestId = 0;

const usePayrollStore = create((set, get) => ({
    allPayrolls: [],
    payrollStaffs: [],
    loadedPayrollId: null,
    myPayslip: null,
    loading: false,
    calculating: false,
    confirming: false,
    deleting: false,
    creating: false,

    createPayrollAction: async (periodId, name, code, description, staffIds) => {
        set({ creating: true });
        try {
            const response = await createPayroll(periodId, name, code, description, staffIds);
            await get().loadAllPayrolls();
            return response;
        } catch (error) {
            console.error('Error creating payroll:', error);
            throw error;
        } finally {
            set({ creating: false });
        }
    },

    loadAllPayrolls: async () => {
        set({ loading: true });
        try {
            const response = await getAllPayrolls();
            const dataList = Array.isArray(response?.data?.data)
                ? response.data.data
                : Array.isArray(response?.data) ? response.data : [];
            set({ allPayrolls: dataList, loading: false });
        } catch (error) {
            console.error('Error loading payrolls:', error);
            set({ loading: false });
        }
    },

    loadPayrollDetails: async (payrollId) => {
        if (!payrollId) return;
        const requestId = ++detailRequestId;
        set(state => ({ loading: true, loadedPayrollId: payrollId,
            payrollStaffs: state.loadedPayrollId === payrollId ? state.payrollStaffs : [] }));
        try {
            const response = await getPayrollDetails(payrollId);
            const dataList = Array.isArray(response?.data?.data)
                ? response.data.data
                : Array.isArray(response?.data) ? response.data : [];
            if (requestId === detailRequestId) set({ payrollStaffs: dataList, loading: false });
        } catch (error) {
            console.error('Error loading payroll details:', error);
            if (requestId === detailRequestId) set({ payrollStaffs: [], loading: false });
            throw error;
        }
    },

    recalculatePayroll: async (payrollId) => {
        if (!payrollId) return;
        set({ calculating: true });
        try {
            await calculatePayroll(payrollId);
            await get().loadPayrollDetails(payrollId);
        } catch (error) {
            console.error('Error recalculating payroll:', error);
            throw error;
        } finally {
            set({ calculating: false });
        }
    },

    confirmPayroll: async (payrollId) => {
        if (!payrollId) return;
        set({ confirming: true });
        try {
            await confirmPayroll(payrollId);
            await get().loadAllPayrolls();
            await get().loadPayrollDetails(payrollId);
        } catch (error) {
            console.error('Error confirming payroll:', error);
            throw error;
        } finally {
            set({ confirming: false });
        }
    },

    deletePayroll: async (payrollId) => {
        if (!payrollId) return;
        set({ deleting: true });
        try {
            await deletePayroll(payrollId);
        } catch (error) {
            console.error('Error deleting payroll:', error);
            throw error;
        } finally {
            set({ deleting: false });
        }
    },

    applyPayslip: (payslip) => set(state => ({
        payrollStaffs: state.payrollStaffs.map(row => row.id === payslip.id ? payslip : row)
    })),

    loadMyPayslip: async (periodId) => {
        if (!periodId) return;
        const requestId = ++myPayslipRequestId;
        set({ loading: true, myPayslip: null });
        try {
            const response = await getMyPayslip(periodId);
            const data = response?.data?.data || response?.data || null;
            if (requestId === myPayslipRequestId) set({ myPayslip: data, loading: false });
        } catch (error) {
            console.error('Error loading my payslip:', error);
            if (requestId === myPayslipRequestId) set({ loading: false });
            throw error;
        }
    }
}));

export default usePayrollStore;
