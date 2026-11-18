export const XLSX_CONTENT_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
// Successful downloads are XLSX; validation and authorization errors are JSON.
export const STAFF_EXCEL_ACCEPT = `${XLSX_CONTENT_TYPE}, application/json`;
const DEFAULT_FILE_NAME = 'Danh_sach_nhan_vien.xlsx';

const getExportFileName = (contentDisposition = '', defaultFileName = DEFAULT_FILE_NAME) => {
    const encodedName = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(contentDisposition)?.[1];
    const plainName = /filename\s*=\s*(?:"([^"]+)"|([^;]+))/i.exec(contentDisposition);
    let name = plainName?.[1] || plainName?.[2] || defaultFileName;
    if (encodedName) {
        try {
            name = decodeURIComponent(encodedName);
        } catch {
            // Fall back to the plain filename when the encoded header is malformed.
        }
    }
    name = name.trim().split(/[\\/]/).pop();
    if (!name || /[<>:"|?*]/.test(name) || [...name].some(char => char.charCodeAt(0) < 32) || !/\.xlsx$/i.test(name)) {
        return defaultFileName;
    }
    return name;
};

export const requestStaffExport = async (client, apiPath, searchDto = {}, password) => {
    if (typeof password !== 'string' || !password.length) throw new Error('Vui lòng nhập mật khẩu xác nhận.');
    const response = await client.post(`${apiPath}/export-excel`, searchDto, {
        responseType: 'blob',
        headers: { Accept: STAFF_EXCEL_ACCEPT, 'X-Confirmation-Password': encodeURIComponent(password) },
    });
    return readStaffExcelResponse(response);
};

export const readStaffExcelResponse = async (response, defaultFileName = DEFAULT_FILE_NAME) => {
    const blob = response.data;
    const contentType = (response.headers?.['content-type'] || blob?.type || '')
        .split(';')[0].trim().toLowerCase();
    if (!(blob instanceof Blob) || blob.size === 0 || contentType !== XLSX_CONTENT_TYPE) {
        throw new Error('Phản hồi xuất danh sách nhân viên không phải tệp Excel hợp lệ.');
    }
    // XLSX is a ZIP package. Reject JSON/HTML even if a server labels it as Excel.
    const signature = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
    if (signature[0] !== 0x50 || signature[1] !== 0x4B || signature[2] !== 0x03 || signature[3] !== 0x04) {
        throw new Error('Dữ liệu xuất danh sách nhân viên không đúng định dạng XLSX.');
    }
    const fileName = getExportFileName(response.headers?.['content-disposition'], defaultFileName);
    return new File([blob], fileName, { type: XLSX_CONTENT_TYPE });
};
