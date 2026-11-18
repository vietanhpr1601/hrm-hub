import test from 'node:test';
import assert from 'node:assert/strict';
import {
    requestStaffImport as unconfirmedImport, requestStaffImportTemplate, STAFF_IMPORT_MAX_BYTES, STAFF_IMPORT_TEMPLATE_NAME,
} from '../src/services/staffImport.js';
import { XLSX_CONTENT_TYPE } from '../src/services/staffExport.js';

const password = ' Mật-khẩu+%123 ';
const requestStaffImport = (client, path, file) => unconfirmedImport(client, path, file, password);

// Transport fixtures; the backend validates actual workbook content and employee rules.
const workbookBytes = new Uint8Array([0x50, 0x4B, 0x03, 0x04, 255, 128]);
const workbook = (name = 'Nhan_vien_moi.xlsx', bytes = workbookBytes) => new File([bytes], name, { type: XLSX_CONTENT_TYPE });
const successfulResult = { importedCount: 2, totalRows: 2, errors: [] };

test('employee import sends the original file as multipart without overriding the browser boundary', async () => {
    const file = workbook();
    let request;
    const result = await requestStaffImport({
        post: async (...args) => {
            request = args;
            return { data: { status: 200, data: successfulResult } };
        },
    }, '/api/staffs', file);
    assert.equal(request[0], '/api/staffs/import-excel');
    assert.ok(request[1] instanceof FormData);
    assert.deepEqual([...request[1].keys()], ['file', 'password']);
    assert.equal(request[1].get('password'), password);
    const uploaded = request[1].get('file');
    assert.equal(uploaded.name, file.name);
    assert.deepEqual(new Uint8Array(await uploaded.arrayBuffer()), workbookBytes);
    assert.equal(request[2].headers, undefined);
    assert.equal(request[2].timeout, 120000);
    assert.deepEqual(result, successfulResult);
});

test('employee import blocks non-xlsx, empty, oversized and text files before sending any request', async () => {
    let requests = 0;
    const client = { post: async () => { requests++; } };
    const rejected = [
        [null, /chọn tệp Excel/],
        [workbook('Nhan_vien.xls'), /chọn tệp Excel/],
        [workbook('Nhan_vien.csv'), /chọn tệp Excel/],
        [workbook('Nhan_vien.xlsx', ''), /đang rỗng/],
        [workbook('Nhan_vien.xlsx', new Uint8Array(STAFF_IMPORT_MAX_BYTES + 1)), /vượt quá 1 MB/],
        [workbook('Nhan_vien.xlsx', '<html>not excel</html>'), /không đúng định dạng XLSX/],
    ];
    for (const [file, message] of rejected) {
        await assert.rejects(requestStaffImport(client, '/api/staffs', file), message);
    }
    assert.equal(requests, 0);
});

test('employee import accepts uppercase extension and a file exactly at the size limit', async () => {
    const bytes = new Uint8Array(STAFF_IMPORT_MAX_BYTES);
    bytes.set(workbookBytes);
    const result = await requestStaffImport({
        post: async () => ({ data: { status: 200, data: successfulResult } }),
    }, '/api/staffs', workbook('Nhan_vien.XLSX', bytes));
    assert.equal(result.importedCount, 2);
});

test('employee import preserves row validation details and rejects instead of reporting success', async () => {
    const result = {
        importedCount: 0,
        totalRows: 2,
        errors: [{ row: 3, field: 'Email', message: 'Email đã tồn tại trong hệ thống' }],
    };
    const error = Object.assign(new Error('Request failed with status code 400'), {
        response: { status: 400, data: { status: 400, message: 'Tệp có dữ liệu không hợp lệ', data: result } },
    });
    await assert.rejects(requestStaffImport({ post: async () => { throw error; } }, '/api/staffs', workbook()), (actual) => {
        assert.equal(actual, error);
        assert.deepEqual(actual.response.data.data.errors, result.errors);
        return true;
    });
});

test('employee import never reports success for malformed, empty or partial results', async () => {
    for (const payload of [
        {},
        '<html>Login required</html>',
        { status: 400, message: 'Nhập thất bại', data: { importedCount: 0, totalRows: 2, errors: [] } },
        { status: 200, data: { importedCount: 0, totalRows: 0, errors: [] } },
        { status: 200, data: { importedCount: 1, totalRows: 2, errors: [] } },
        { status: 200, data: { importedCount: 2, totalRows: 2, errors: [{ row: 2, field: 'Email', message: 'Lỗi' }] } },
        { status: 200, data: { importedCount: 2, totalRows: 2 } },
    ]) {
        await assert.rejects(requestStaffImport({ post: async () => ({ data: payload }) }, '/api/staffs', workbook()));
    }
});

test('employee import propagates network failures without converting them to successful imports', async () => {
    const error = new Error('Network Error');
    await assert.rejects(requestStaffImport({ post: async () => { throw error; } }, '/api/staffs', workbook()), error);
});

test('employee import template downloads binary XLSX with the dedicated fallback name', async () => {
    let request;
    const template = await requestStaffImportTemplate({
        get: async (...args) => {
            request = args;
            return { data: new Blob([workbookBytes], { type: XLSX_CONTENT_TYPE }), headers: {} };
        },
    }, '/api/staffs');
    assert.deepEqual(request, ['/api/staffs/import-template', {
        responseType: 'blob', headers: { Accept: `${XLSX_CONTENT_TYPE}, application/json` },
    }]);
    assert.equal(template.name, STAFF_IMPORT_TEMPLATE_NAME);
    assert.deepEqual(new Uint8Array(await template.arrayBuffer()), workbookBytes);
});

test('employee import template rejects text disguised as XLSX and propagates forbidden responses', async () => {
    await assert.rejects(requestStaffImportTemplate({
        get: async () => ({ data: new Blob(['{"message":"Forbidden"}'], { type: XLSX_CONTENT_TYPE }), headers: {} }),
    }, '/api/staffs'), /không đúng định dạng XLSX/);
    const forbidden = new Error('Forbidden');
    await assert.rejects(requestStaffImportTemplate({ get: async () => { throw forbidden; } }, '/api/staffs'), forbidden);
});

test('employee import without password does not upload employee data', async () => {
    let sent = false;
    const client = { post: async () => { sent = true; } };
    for (const missing of [undefined, null, '']) {
        await assert.rejects(unconfirmedImport(client, '/api/staffs', workbook(), missing), /mật khẩu xác nhận/);
    }
    assert.equal(sent, false);
});
