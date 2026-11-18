import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Alert, Autocomplete, Box, Button, Chip, CircularProgress,
  InputAdornment, Paper, Tab, Tabs, TextField, Typography,
} from '@mui/material';
import { AdminPanelSettingsOutlined, LockOutlined, SaveOutlined, Search } from '@mui/icons-material';
import { toast } from 'sonner';
import { getUsers } from '../../services/UserService';
import {
  getPermissionCatalog, getPermissionGroups, saveRolePermissions,
  getAccountPermissions, saveAccountPermissions,
} from '../../services/accessControlService';
import useAuthStore from '../../store/useAuthStore';
import { inheritedRolePermissions, previewPermissions, roleLabel, removeRetiredPermissions, removeRetiredOverrides } from '../../utils/permissions.js';
import { ROLES } from '../../constants/roles.js';
import PermissionModules from './components/PermissionModules';

const errorText = (error) => error.response?.data?.message || error.message || 'Không thể xử lý phân quyền';
const sameValues = (left, right) => JSON.stringify([...left].sort()) === JSON.stringify([...right].sort());

export default function PermissionManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedRoleId = searchParams.get('roleId');
  const [tab, setTab] = useState(0);
  const [catalog, setCatalog] = useState([]);
  const [groups, setGroups] = useState({ roles: [] });
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [groupId, setGroupId] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState([]);
  const [search, setSearch] = useState('');
  const [account, setAccount] = useState(null);
  const [accountDetails, setAccountDetails] = useState(null);
  const [accountLoading, setAccountLoading] = useState(false);
  const [roleIds, setRoleIds] = useState([]);
  const [overrides, setOverrides] = useState({});
  const refreshCurrentUser = useAuthStore((state) => state.getCurrentUser);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [nextCatalog, nextGroups, nextUsers] = await Promise.all([
        getPermissionCatalog(), getPermissionGroups(), getUsers(),
      ]);
      setCatalog(nextCatalog || []);
      setGroups(nextGroups || { roles: [] });
      setUsers(nextUsers.data || []);
      // Admin mặc định có toàn quyền nên không đưa vào danh sách phân quyền theo vai trò.
      const configurable = (nextGroups?.roles || []).filter((role) => role.name !== ROLES.ADMIN);
      setGroupId((current) => (configurable.some((role) => role.id === current) ? current : null)
        || configurable.find((role) => role.name === ROLES.USER)?.id || configurable[0]?.id || '');
    } catch (err) { setError(errorText(err)); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const groupOptions = groups.roles.filter((role) => role.name !== ROLES.ADMIN);
  const selectedGroup = groupOptions.find((group) => group.id === groupId);
  const adminGroup = selectedGroup?.name === ROLES.ADMIN;

  useEffect(() => {
    if (requestedRoleId && groups.roles.some((role) => role.id === requestedRoleId && role.name !== ROLES.ADMIN)) {
      setGroupId(requestedRoleId);
      setTab(0);
      setSearch('');
    }
  }, [requestedRoleId, groups.roles]);

  useEffect(() => {
    setSelectedPermissions(removeRetiredPermissions(adminGroup ? catalog.map((item) => item.code) : selectedGroup?.permissions || []));
  }, [selectedGroup, adminGroup, catalog]);

  useEffect(() => {
    let cancelled = false;
    setAccountDetails(null);
    setError('');
    if (!account) { setAccountLoading(false); return; }
    setAccountLoading(true);
    getAccountPermissions(account.id).then((details) => {
      if (cancelled) return;
      setAccountDetails(details);
      setRoleIds((details.roles || []).map((role) => role.id));
      setOverrides(removeRetiredOverrides(details.permissionOverrides || {}));
    }).catch((err) => { if (!cancelled) setError(errorText(err)); })
      .finally(() => { if (!cancelled) setAccountLoading(false); });
    return () => { cancelled = true; };
  }, [account]);

  const inherited = useMemo(() => accountDetails ? inheritedRolePermissions(groups, roleIds) : [], [accountDetails, groups, roleIds]);
  const selectedAdmin = accountDetails?.admin || groups.roles.some((role) => roleIds.includes(role.id) && role.name === ROLES.ADMIN);
  const effective = previewPermissions(inherited, overrides, catalog, selectedAdmin);
  const groupDirty = selectedGroup && !sameValues(selectedPermissions, removeRetiredPermissions(selectedGroup.permissions || []));
  const accountDirty = accountDetails && (!sameValues(roleIds, (accountDetails.roles || []).map((role) => role.id))
    || !sameValues(Object.entries(overrides).map(([code, value]) => `${code}:${value}`),
      Object.entries(removeRetiredOverrides(accountDetails.permissionOverrides || {})).map(([code, value]) => `${code}:${value}`)));
  const selectAccountRoles = (_, roles) => {
    if (accountDetails?.admin) return;
    const admin = roles.find((role) => role.name === ROLES.ADMIN);
    setRoleIds(admin ? [admin.id] : roles.map((role) => role.id));
    if (admin) setOverrides({});
  };

  const saveGroup = async () => {
    setSaving(true);
    try {
      await saveRolePermissions(groupId, selectedPermissions);
      setGroups(await getPermissionGroups());
      await refreshCurrentUser();
      toast.success('Đã lưu quyền của nhóm');
    } catch (err) { toast.error(errorText(err)); }
    finally { setSaving(false); }
  };
  const saveAccount = async () => {
    setSaving(true);
    try {
      await saveAccountPermissions(account.id, { roleIds, permissionOverrides: selectedAdmin ? {} : overrides });
      const details = await getAccountPermissions(account.id);
      setAccountDetails(details);
      setRoleIds((details.roles || []).map((role) => role.id));
      setOverrides(details.permissionOverrides || {});
      await refreshCurrentUser();
      const userResponse = await getUsers();
      setUsers(userResponse.data || []);
      toast.success('Đã lưu phân quyền tài khoản');
    } catch (err) { toast.error(errorText(err)); }
    finally { setSaving(false); }
  };
  const changeTab = (_, value) => { setTab(value); setSearch(''); setError(''); };
  const showEditor = tab === 0 ? !!selectedGroup : !!accountDetails;
  const locked = tab === 0 ? adminGroup : selectedAdmin;
  const currentPermissions = tab === 0 ? selectedPermissions : effective;

  return <Box sx={{ maxWidth: 1040, mx: 'auto', display: 'flex', flexDirection: 'column', gap: 2.5 }}>
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
      <AdminPanelSettingsOutlined color="primary" /><Typography variant="h5" fontWeight={650}>Phân quyền</Typography>
    </Box>
    <Tabs value={tab} onChange={changeTab} aria-label="Phạm vi phân quyền" sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
      <Tab label="Vai trò" disabled={saving} /><Tab label="Tài khoản" disabled={saving} />
    </Tabs>
    {error && <Alert severity="error" action={<Button onClick={load} disabled={loading}>Thử lại</Button>}>{error}</Alert>}
    {loading ? <Box sx={{ p: 6, textAlign: 'center' }}><CircularProgress size={28} /></Box> : <>
      {tab === 0 ? <>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
            <Autocomplete sx={{ flex: 1, minWidth: 240 }} options={groupOptions}
              value={selectedGroup || null} disabled={saving} disableClearable
              isOptionEqualToValue={(option, value) => option.id === value.id} getOptionLabel={roleLabel}
              onChange={(_, role) => { if (role) {
                setGroupId(role.id); setSearch('');
                setSearchParams((current) => { const next = new URLSearchParams(current); next.set('roleId', role.id); return next; }, { replace: true });
              } }}
              renderInput={(params) => <TextField {...params} label="Chọn vai trò" size="small" />} />
            <Button component={Link} to="/administration/roles" variant="outlined" disabled={saving}>Quản lý vai trò</Button>
        </Box>
      </> : <>
        <Autocomplete options={users} value={account} disabled={saving}
          isOptionEqualToValue={(option, value) => option.id === value.id}
          getOptionLabel={(option) => `${option.username || ''}${option.staffName ? ` — ${option.staffName}` : ''}`}
          onChange={(_, value) => { setAccount(value); setSearch(''); }}
          renderInput={(params) => <TextField {...params} size="small" label="Chọn tài khoản" />} />
        {accountLoading && <CircularProgress size={24} />}
        {accountDetails && <>
          {account.active === false && <Alert severity="warning">Tài khoản đang bị khóa.</Alert>}
          <Autocomplete multiple options={groups.roles}
            value={groups.roles.filter((role) => roleIds.includes(role.id))}
            disabled={saving || accountDetails.admin} disableCloseOnSelect
            isOptionEqualToValue={(option, value) => option.id === value.id} getOptionLabel={roleLabel}
            getOptionDisabled={(role) => selectedAdmin && role.name !== ROLES.ADMIN}
            onChange={selectAccountRoles}
            renderInput={(params) => <TextField {...params} label="Vai trò" size="small" />} />
          {selectedAdmin && !accountDetails.admin && <Alert severity="warning">Cấp Admin sẽ thay thế vai trò hiện tại và xóa quyền riêng.</Alert>}
        </>}
      </>}
      {showEditor && <>
        <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5, flexWrap: 'wrap' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="subtitle2">{tab === 0 ? roleLabel(selectedGroup) : 'Quyền riêng'}</Typography>
            <Chip size="small" variant="outlined" label={`${currentPermissions.length}/${catalog.length} quyền`} />
          </Box>
          <Button variant="contained" startIcon={saving ? <CircularProgress size={16} color="inherit" /> : <SaveOutlined />}
            disabled={saving || (tab === 0 ? adminGroup || !groupDirty : accountDetails.admin || !accountDirty)}
            onClick={tab === 0 ? saveGroup : saveAccount}>{tab === 0 ? 'Lưu quyền' : 'Lưu tài khoản'}</Button>
        </Paper>
        {locked ? <Paper variant="outlined" sx={{ py: 4, px: 2, textAlign: 'center', borderRadius: 2 }}>
          <LockOutlined color="primary" sx={{ mb: 1 }} /><Typography fontWeight={600}>Admin có toàn bộ quyền</Typography>
        </Paper> : <>
          <TextField size="small" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm quyền..."
            inputProps={{ 'aria-label': 'Tìm quyền' }} InputProps={{ startAdornment: <InputAdornment position="start"><Search fontSize="small" /></InputAdornment> }} />
          <PermissionModules key={tab === 0 ? groupId : account.id} catalog={catalog} search={search} selected={currentPermissions}
            onChange={setSelectedPermissions} disabled={saving} overrides={overrides}
            onOverride={tab === 1 ? (code, value) => setOverrides((current) => {
              const next = { ...current };
              if (value === 'INHERIT') delete next[code]; else next[code] = value;
              return next;
            }) : undefined} />
        </>}
      </>}
      {tab === 1 && !account && <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>Chọn tài khoản để chỉnh quyền.</Typography>}
    </>}
  </Box>;
}
