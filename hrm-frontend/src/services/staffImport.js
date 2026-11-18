import { readStaffExcelResponse, STAFF_EXCEL_ACCEPT } from './staffExport.js';

export const STAFF_IMPORT_MAX_BYTES = 1024 * 1024;
export const STAFF_IMPORT_TEMPLATE_NAME = 'Mau_nhap_nhan_vien_moi.xlsx';

export const requestStaffImportTemplate = async (client, apiPath) => {
    const response = await client.get(`${apiPath}/import-template`, {
        responseType: 'blob',
        headers: { Accept: STAFF_EXCEL_ACCEPT },
    });
    return readStaffExcelResponse(response, STAFF_IMPORT_TEMPLATE_NAME);
};

export const requestStaffImport = async (client, apiPath, file, password) => {
    if (!(file instanceof File) || !/\.xlsx$/i.test(file.name)) {
        throw new Error('Vui lòng chọn tệp Excel .xlsx theo mẫu nhập nhân viên mới.');
    }
    if (file.size === 0) {
        throw new Error('Tệp Excel đang rỗng. Vui lòng điền dữ liệu vào mẫu trước khi nhập.');
    }
    if (file.size > STAFF_IMPORT_MAX_BYTES) {
        throw new Error('Tệp Excel không được vượt quá 1 MB.');
    }
    const signature = new Uint8Array(await file.slice(0, 4).arrayBuffer());
    if (signature[0] !== 0x50 || signature[1] !== 0x4B || signature[2] !== 0x03 || signature[3] !== 0x04) {
        throw new Error('Nội dung tệp không đúng định dạng XLSX. Vui lòng dùng mẫu nhập của hệ thống.');
    }

    const formData = new FormData();
    if (typeof password !== 'string' || !password.length) throw new Error('Vui lòng nhập mật khẩu xác nhận.');
    formData.append('file', file);
    formData.append('password', password);
    // The browser adds the multipart boundary; do not set Content-Type manually.
    const response = await client.post(`${apiPath}/import-excel`, formData, { timeout: 120000 });
    const payload = response.data;
    const result = payload?.data;
    if (payload?.status !== 200
        || !Number.isInteger(result?.importedCount) || result.importedCount < 1
        || result.totalRows !== result.importedCount
        || !Array.isArray(result.errors) || result.errors.length > 0) {
        const error = new Error(payload?.message || 'Chưa xác nhận được kết quả nhập. Vui lòng kiểm tra danh sách nhân viên trước khi thử lại.');
        error.response = response;
        throw error;
    }
    return result;
};
