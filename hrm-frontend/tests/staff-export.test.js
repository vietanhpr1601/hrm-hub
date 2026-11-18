import test from 'node:test';
import assert from 'node:assert/strict';
import { requestStaffExport as unconfirmedExport, XLSX_CONTENT_TYPE } from '../src/services/staffExport.js';

const password = ' Mật-khẩu+%123 ';
const requestStaffExport = (client, path, filters) => unconfirmedExport(client, path, filters, password);

// Transport fixture only; backend tests validate a complete XLSX workbook using POI.
const workbookBytes = new Uint8Array([0x50, 0x4B, 0x03, 0x04, 0, 1, 255, 128]);
const response = (data = new Blob([workbookBytes], { type: XLSX_CONTENT_TYPE }), headers = {}) => ({
    data,
    headers: { 'content-type': XLSX_CONTENT_TYPE, ...headers },
});
const client = (result) => ({ post: async () => result });

test('employee export requests a blob with the applied filters and preserves binary bytes', async () => {
    const filters = { keyword: 'Nguyễn', departmentId: 'department-1' };
    let request;
    const file = await requestStaffExport({
        post: async (...args) => {
            request = args;
            return response(undefined, { 'content-disposition': 'attachment; filename="Nhan_vien.xlsx"' });
        },
    }, '/api/staffs', filters);
    assert.deepEqual(request, ['/api/staffs/export-excel', filters, {
        responseType: 'blob', headers: { Accept: `${XLSX_CONTENT_TYPE}, application/json`, 'X-Confirmation-Password': encodeURIComponent(password) },
    }]);
    assert.ok(file instanceof Blob);
    assert.equal(file.name, 'Nhan_vien.xlsx');
    assert.equal(file.type, XLSX_CONTENT_TYPE);
    assert.deepEqual(new Uint8Array(await file.arrayBuffer()), workbookBytes);
});

test('employee export accepts UTF-8 filenames and uses a valid fallback without exposed headers', async () => {
    const named = await requestStaffExport(client(response(undefined, {
        'content-disposition': "attachment; filename=fallback.xlsx; filename*=UTF-8''Danh%20s%C3%A1ch.xlsx",
    })), '/api/staffs');
    assert.equal(named.name, 'Danh sách.xlsx');
    const fallback = await requestStaffExport(client(response()), '/api/staffs');
    assert.equal(fallback.name, 'Danh_sach_nhan_vien.xlsx');
});

test('employee export avoids unsafe or non-xlsx filenames', async () => {
    for (const name of ['bad?.xlsx', 'staff.html', 'staff.xlsx.exe']) {
        const file = await requestStaffExport(client(response(undefined, {
            'content-disposition': `attachment; filename="${name}"`,
        })), '/api/staffs');
        assert.equal(file.name, 'Danh_sach_nhan_vien.xlsx');
    }
});

test('employee export rejects an empty file, JSON objects, JSON errors and HTML responses', async () => {
    for (const result of [
        response(new Blob([], { type: XLSX_CONTENT_TYPE })),
        response({ message: 'Access denied' }),
        response(new Blob(['{"message":"Access denied"}'], { type: 'application/json' }), { 'content-type': 'application/json' }),
        response(new Blob(['<html>Server error</html>'], { type: 'text/html' }), { 'content-type': 'text/html' }),
    ]) {
        await assert.rejects(requestStaffExport(client(result), '/api/staffs'), /không phải tệp Excel hợp lệ/);
    }
});

test('employee export rejects text masquerading as XLSX', async () => {
    for (const text of ['{"error":"Export failed"}', '<html>Login</html>']) {
        await assert.rejects(requestStaffExport(client(response(new Blob([text], { type: XLSX_CONTENT_TYPE }))), '/api/staffs'), /không đúng định dạng XLSX/);
    }
});

test('employee export propagates HTTP failures without creating a downloadable file', async () => {
    const error = new Error('Forbidden');
    await assert.rejects(requestStaffExport({ post: async () => { throw error; } }, '/api/staffs'), error);
});

test('employee export accepts JSON password errors while keeping the error blob for the confirmation dialog', async () => {
    const message = 'Mật khẩu xác nhận không đúng.';
    const failure = Object.assign(new Error('Request failed with status code 400'), {
        response: { status: 400, headers: { 'content-type': 'application/json' },
            data: new Blob([JSON.stringify({ status: 400, message })], { type: 'application/json' }) },
    });
    await assert.rejects(requestStaffExport({ post: async (url, body, config) => {
        assert.equal(config.headers.Accept, `${XLSX_CONTENT_TYPE}, application/json`);
        assert.equal(config.responseType, 'blob');
        throw failure;
    } }, '/api/staffs'), asyncError => {
        assert.equal(asyncError, failure);
        assert.equal(asyncError.response.status, 400);
        assert.ok(asyncError.response.data instanceof Blob);
        return true;
    });
    assert.equal(JSON.parse(await failure.response.data.text()).message, message);
});

test('employee export without password sends no request and puts no password in the URL or filters', async () => {
    let sent = false;
    const client = { post: async () => { sent = true; } };
    for (const missing of [undefined, null, '']) {
        await assert.rejects(unconfirmedExport(client, '/api/staffs', {}, missing), /mật khẩu xác nhận/);
    }
    assert.equal(sent, false);
});
