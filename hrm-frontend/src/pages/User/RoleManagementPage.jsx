import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, IconButton, InputAdornment, LinearProgress, Paper, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import { Add, DeleteOutline, Refresh, Search, TuneOutlined } from '@mui/icons-material';
import { toast } from 'sonner';
import { getUsers } from '../../services/UserService';
import {
  createPermissionRole, deletePermissionRole, getPermissionGroups,
} from '../../services/accessControlService';
import { ROLES } from '../../constants/roles.js';
import { canDeletePermissionRole, roleLabel } from '../../utils/permissions.js';

const errorText = (error) => error.response?.data?.message || error.message || 'Không thể xử lý vai trò. Vui lòng thử lại.';
const normalize = (value) => String(value || '').normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
const isProtected = (role) => role?.name === ROLES.ADMIN;

export default function RoleManagementPage() {
  const [roles, setRoles] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [newRole, setNewRole] = useState({ name: '', description: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [createError, setCreateError] = useState('');
  const [roleToDelete, setRoleToDelete] = useState(null);
  const [confirmation, setConfirmation] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [pending, setPending] = useState('');
  const mutationRef = useRef(false);
  const loadSequence = useRef(0);

  const load = useCallback(async () => {
    const sequence = ++loadSequence.current;
    setLoading(true);
    setLoadError('');
    try {
      const [groups, userResponse] = await Promise.all([getPermissionGroups(), getUsers()]);
      if (userResponse?.status >= 400) throw new Error(userResponse.message || 'Không thể tải danh sách tài khoản.');
      if (!Array.isArray(groups?.roles) || !Array.isArray(userResponse?.data)) {
        throw new Error('Chưa tải được đầy đủ vai trò và tài khoản. Vui lòng thử lại.');
      }
      if (sequence !== loadSequence.current) return;
      setRoles(groups.roles.filter((role) => role.isDeleted !== true));
      setUsers(userResponse.data.filter((user) => user.isDeleted !== true));
      setHasLoaded(true);
    } catch (error) {
      if (sequence === loadSequence.current) setLoadError(errorText(error));
    } finally {
      if (sequence === loadSequence.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    return () => { loadSequence.current += 1; };
  }, [load]);

  const assignedCounts = useMemo(() => {
    const counts = new Map();
    for (const user of users) {
      const ids = new Set((user.roles || []).filter((role) => role.isDeleted !== true).map((role) => role.id));
      for (const id of ids) counts.set(id, (counts.get(id) || 0) + 1);
    }
    return counts;
  }, [users]);

  const visibleRoles = useMemo(() => {
    const keyword = normalize(search.trim());
    return roles.filter((role) => !keyword || normalize(`${roleLabel(role)} ${role.name} ${role.description || ''}`).includes(keyword));
  }, [roles, search]);

  const busy = Boolean(pending);
  const actionsDisabled = loading || busy || Boolean(loadError) || !hasLoaded;
  const currentDeleteRole = roles.find((role) => role.id === roleToDelete?.id);
  const canDeleteSelected = canDeletePermissionRole(currentDeleteRole, users);
  const deleteReason = (role) => {
    if (isProtected(role)) return `Vai trò ${roleLabel(role)} được bảo vệ, không thể xóa.`;
    const count = assignedCounts.get(role.id) || 0;
    if (count) return `Đang được gán cho ${count} tài khoản (kể cả tài khoản bị khóa). Hãy gỡ vai trò khỏi các tài khoản trước.`;
    if (loading) return 'Đang tải danh sách vai trò và tài khoản.';
    if (loadError) return 'Cần tải lại danh sách trước khi xóa.';
    return 'Xóa vai trò';
  };

  const openCreate = () => {
    if (actionsDisabled) return;
    setNewRole({ name: '', description: '' });
    setFieldErrors({});
    setCreateError('');
    setCreateOpen(true);
  };

  const createRole = async (event) => {
    event.preventDefault();
    if (mutationRef.current || actionsDisabled) return;
    const name = newRole.name.trim().toUpperCase();
    const description = newRole.description.trim();
    const errors = {};
    if (!/^[A-Z][A-Z0-9_]{2,49}$/.test(name)) {
      errors.name = 'Nhập 3–50 ký tự in hoa, số hoặc dấu gạch dưới; bắt đầu bằng chữ.';
    }
    if (!description) errors.description = 'Vui lòng nhập tên vai trò.';
    else if (description.length > 255) errors.description = 'Tên vai trò không quá 255 ký tự.';
    setFieldErrors(errors);
    setCreateError('');
    if (Object.keys(errors).length) return;
    mutationRef.current = true;
    setPending('create');
    try {
      await createPermissionRole({ name, description, permissions: [] });
      setCreateOpen(false);
      setSearch('');
      toast.success('Đã tạo vai trò');
      await load();
    } catch (error) {
      setCreateError(errorText(error));
    } finally {
      mutationRef.current = false;
      setPending('');
    }
  };

  const openDelete = (role) => {
    if (actionsDisabled || !canDeletePermissionRole(role, users)) return;
    setRoleToDelete(role);
    setConfirmation('');
    setDeleteError('');
  };

  const deleteRole = async (event) => {
    event.preventDefault();
    if (mutationRef.current || actionsDisabled || !canDeleteSelected || confirmation !== roleToDelete?.name) return;
    mutationRef.current = true;
    setPending('delete');
    setDeleteError('');
    try {
      await deletePermissionRole(roleToDelete.id);
      setRoles((current) => current.filter((role) => role.id !== roleToDelete.id));
      setRoleToDelete(null);
      setConfirmation('');
      toast.success('Đã xóa vai trò');
      await load();
    } catch (error) {
      setDeleteError(errorText(error));
      // Refresh assignments if another administrator assigned the role meanwhile.
      await load();
    } finally {
      mutationRef.current = false;
      setPending('');
    }
  };

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1280, mx: 'auto' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, mb: 3 }}>
        <Typography variant="h5" component="h1" fontWeight={700}>Vai trò</Typography>
        <Button variant="contained" startIcon={<Add />} onClick={openCreate} disabled={actionsDisabled}>
          Tạo vai trò
        </Button>
      </Box>

      {loadError && (
        <Alert severity="error" sx={{ mb: 2 }} action={(
          <Button color="inherit" size="small" onClick={load} disabled={loading || busy}>Thử lại</Button>
        )}>
          {loadError}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden' }}>
        <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <TextField
            label="Tìm vai trò"
            placeholder="Tên hoặc mã vai trò"
            size="small"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            sx={{ flex: 1, maxWidth: 420 }}
            InputProps={{ startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment> }}
          />
          <Tooltip title="Tải lại danh sách">
            <span>
              <IconButton aria-label="Tải lại danh sách vai trò" onClick={load} disabled={loading || busy}>
                <Refresh />
              </IconButton>
            </span>
          </Tooltip>
        </Box>
        {loading && hasLoaded && <LinearProgress aria-label="Đang tải lại danh sách vai trò" />}
        {!hasLoaded ? (
          <Box sx={{ p: 6, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 1.5 }} role="status">
            {loading && <CircularProgress size={24} />}
            <Typography color="text.secondary">
              {loading ? 'Đang tải vai trò…' : 'Chưa tải được danh sách vai trò.'}
            </Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Danh sách vai trò" sx={{ minWidth: 760 }}>
              <TableHead>
                <TableRow sx={{ bgcolor: 'action.hover' }}>
                  <TableCell sx={{ py: 1.5 }}>Tên vai trò</TableCell>
                  <TableCell>Mã vai trò</TableCell>
                  <TableCell align="center">Số quyền</TableCell>
                  <TableCell align="center">Tài khoản</TableCell>
                  <TableCell align="right">Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {visibleRoles.map((role) => (
                  <TableRow key={role.id} hover>
                    <TableCell sx={{ py: 2 }}>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}>
                        <Typography variant="body2" fontWeight={600}>{roleLabel(role)}</Typography>
                        {isProtected(role) && <Chip label="Được bảo vệ" size="small" variant="outlined" />}
                      </Box>
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 13 }}>{role.name}</TableCell>
                    <TableCell align="center">{new Set(role.permissions || []).size}</TableCell>
                    <TableCell align="center">{assignedCounts.get(role.id) || 0}</TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      {!isProtected(role) && (
                        <Button
                          size="small"
                          startIcon={<TuneOutlined />}
                          component={RouterLink}
                          to={`/administration/permissions?roleId=${encodeURIComponent(role.id)}`}
                          disabled={loading || busy}
                          aria-label={`Phân quyền vai trò ${roleLabel(role)}`}
                          sx={{ mr: 0.5 }}
                        >
                          Phân quyền
                        </Button>
                      )}
                      <Tooltip title={deleteReason(role)}>
                        <span>
                          <IconButton
                            size="small"
                            color="error"
                            aria-label={`Xóa vai trò ${roleLabel(role)}`}
                            disabled={actionsDisabled || !canDeletePermissionRole(role, users)}
                            onClick={() => openDelete(role)}
                          >
                            <DeleteOutline fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
                {!visibleRoles.length && (
                  <TableRow>
                    <TableCell colSpan={5} align="center" sx={{ py: 5, color: 'text.secondary' }}>
                      {search.trim() ? 'Không tìm thấy vai trò phù hợp.' : 'Chưa có vai trò.'}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <Dialog open={createOpen} onClose={() => { if (!mutationRef.current) setCreateOpen(false); }} fullWidth maxWidth="sm" aria-labelledby="create-role-title">
        <Box component="form" onSubmit={createRole} noValidate>
          <DialogTitle id="create-role-title">Tạo vai trò</DialogTitle>
          <DialogContent sx={{ display: 'grid', gap: 2, pt: '12px !important' }}>
            {createError && <Alert severity="error">{createError}</Alert>}
            <TextField
              autoFocus required fullWidth label="Tên vai trò" value={newRole.description}
              disabled={busy} inputProps={{ maxLength: 255 }}
              error={Boolean(fieldErrors.description)} helperText={fieldErrors.description}
              onChange={(event) => setNewRole((current) => ({ ...current, description: event.target.value }))}
            />
            <TextField
              required fullWidth label="Mã vai trò" value={newRole.name}
              placeholder="VD: HR_PAYROLL" disabled={busy} inputProps={{ maxLength: 50 }}
              error={Boolean(fieldErrors.name)}
              helperText={fieldErrors.name || '3–50 ký tự: chữ in hoa, số, dấu gạch dưới; bắt đầu bằng chữ.'}
              onChange={(event) => setNewRole((current) => ({ ...current, name: event.target.value.toUpperCase() }))}
            />
            <Typography variant="body2" color="text.secondary">Vai trò mới chưa có quyền. Chọn Phân quyền sau khi tạo.</Typography>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setCreateOpen(false)} disabled={busy}>Hủy</Button>
            <Button type="submit" variant="contained" disabled={actionsDisabled} startIcon={pending === 'create' ? <CircularProgress size={16} color="inherit" /> : null}>
              {pending === 'create' ? 'Đang tạo…' : 'Tạo vai trò'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      <Dialog open={Boolean(roleToDelete)} onClose={() => { if (!mutationRef.current) setRoleToDelete(null); }} fullWidth maxWidth="sm" aria-labelledby="delete-role-title">
        <Box component="form" onSubmit={deleteRole} noValidate>
          <DialogTitle id="delete-role-title">Xóa vai trò</DialogTitle>
          <DialogContent sx={{ display: 'grid', gap: 2 }}>
            <Typography>
              Xóa vai trò <strong>{roleLabel(roleToDelete)}</strong>? Nhập đúng mã <strong>{roleToDelete?.name}</strong> để xác nhận.
            </Typography>
            {deleteError && <Alert severity="error">{deleteError}</Alert>}
            {!loading && currentDeleteRole && !canDeleteSelected && <Alert severity="warning">{deleteReason(currentDeleteRole)}</Alert>}
            {!loading && roleToDelete && !currentDeleteRole && <Alert severity="info">Vai trò này không còn trong danh sách.</Alert>}
            <TextField
              autoFocus fullWidth label="Nhập mã vai trò để xác nhận" value={confirmation}
              disabled={busy} autoComplete="off"
              onChange={(event) => setConfirmation(event.target.value)}
            />
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setRoleToDelete(null)} disabled={busy}>Hủy</Button>
            <Button
              type="submit" color="error" variant="contained"
              disabled={actionsDisabled || !canDeleteSelected || confirmation !== roleToDelete?.name}
              startIcon={pending === 'delete' ? <CircularProgress size={16} color="inherit" /> : null}
            >
              {pending === 'delete' ? 'Đang xóa…' : 'Xác nhận xóa'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Box>
  );
}
