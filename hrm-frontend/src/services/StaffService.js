import axios from './HttpService';
import ConstantList from '../appConfig';
import { requestStaffExport } from './staffExport.js';
import { requestStaffImport, requestStaffImportTemplate } from './staffImport.js';

const API_PATH = ConstantList.API_ENPOINT + '/staffs';

export const exportStaffs = (searchDto, password) => requestStaffExport(axios, API_PATH, searchDto, password);
export const importNewStaffs = (file, password) => requestStaffImport(axios, API_PATH, file, password);
export const downloadStaffImportTemplate = () => requestStaffImportTemplate(axios, API_PATH);

export const getStaffs = async () => {
    const response = await axios.get(API_PATH);
    return response.data;
};

export const getStaffLookup = async () => {
    const response = await axios.get(`${API_PATH}/lookup`);
    return response.data;
};

// Selectors only need employee identity; they must not load private staff profiles.
export const pagingStaffLookup = async (search = {}) => {
    const response = await getStaffLookup();
    const keyword = (search.keyword || '').trim().toLocaleLowerCase('vi');
    const rows = (response.data || []).filter((staff) => !keyword ||
        `${staff.staffCode || ''} ${staff.displayName || ''}`.toLocaleLowerCase('vi').includes(keyword));
    const pageSize = Math.max(1, Number(search.pageSize) || 10);
    const pageIndex = Math.max(1, Number(search.pageIndex) || 1);
    return { ...response, data: {
        content: rows.slice((pageIndex - 1) * pageSize, pageIndex * pageSize),
        totalElements: rows.length, totalPages: Math.ceil(rows.length / pageSize),
    } };
};

export const pagingStaffs = async (searchDto) => {
    const response = await axios.post(`${API_PATH}/paging`, searchDto || {});
    return response.data;
};

export const getStaffById = async (id) => {
    const response = await axios.get(`${API_PATH}/${id}`);
    return response.data;
};

export const getMyStaffProfile = async (config = {}) => {
    const response = await axios.get(`${API_PATH}/me`, { skipGlobalLoading: true, ...config });
    return response.data;
};

export const saveStaff = async (staff) => {
    if (staff.id) {
        const response = await axios.put(`${API_PATH}/${staff.id}`, staff);
        return response.data;
    } else {
        const response = await axios.post(API_PATH, staff);
        return response.data;
    }
};

export const deleteStaff = async (id) => {
    const response = await axios.delete(`${API_PATH}/${id}`);
    return response.data;
};

export const generateStaffCode = async () => {
    const response = await axios.get(`${API_PATH}/generate-staff-code`);
    return response.data;
};

export const getDepartments = async () => {
    const response = await axios.get(`${API_PATH}/departments`);
    return response.data;
};

export const getPositions = async () => {
    const response = await axios.get(`${API_PATH}/positions`);
    return response.data;
};
