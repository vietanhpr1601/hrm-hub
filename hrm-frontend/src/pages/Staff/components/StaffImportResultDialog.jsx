import React from 'react';
import {
    Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle,
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
} from '@mui/material';

const StaffImportResultDialog = ({ result, onClose }) => (
    <Dialog open={Boolean(result)} onClose={onClose} fullWidth maxWidth="md" aria-labelledby="staff-import-result-title">
        <DialogTitle id="staff-import-result-title">
            {result?.success ? 'Nhập nhân viên thành công' : 'Cần sửa dữ liệu trong tệp Excel'}
        </DialogTitle>
        <DialogContent>
            {result?.success ? (
                <Alert severity="success">Đã thêm {result.importedCount} nhân viên mới. Danh sách nhân viên đã được tải lại.</Alert>
            ) : (
                <>
                    <Alert severity="error" sx={{ mb: 2 }}>
                        Chưa thêm nhân viên nào. Hãy sửa các lỗi dưới đây, lưu tệp rồi nhập lại.
                    </Alert>
                    <TableContainer sx={{ maxHeight: 420 }}>
                        <Table size="small" stickyHeader aria-label="Các lỗi nhập nhân viên từ Excel">
                            <TableHead>
                                <TableRow>
                                    <TableCell>Dòng Excel</TableCell>
                                    <TableCell>Trường dữ liệu</TableCell>
                                    <TableCell>Nội dung lỗi</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {(result?.errors || []).map((error, index) => (
                                    <TableRow key={`${error.row}-${error.field}-${index}`}>
                                        <TableCell>{error.row || '—'}</TableCell>
                                        <TableCell>{error.field || 'Tệp Excel'}</TableCell>
                                        <TableCell>{error.message}</TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </>
            )}
        </DialogContent>
        <DialogActions><Button onClick={onClose}>Đóng</Button></DialogActions>
    </Dialog>
);

export default StaffImportResultDialog;
