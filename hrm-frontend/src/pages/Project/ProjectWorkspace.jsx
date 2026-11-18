import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
    Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Grid, IconButton,
    LinearProgress, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Tooltip, Typography
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { toast } from 'sonner';
import { getProjectById, getProjectWorkingStatuses } from '../../services/projectService';
import { deleteTask, pagingTasks, saveTask, updateTaskStatus } from '../../services/taskService';
import useAuthStore from '../../store/useAuthStore';
import useProjectStore from '../../store/useProjectStore';
import { ROLES } from '../../constants/roles';
import Popup from '../../components/ui/Popup';
import ProjectGeneralInfo from './components/ProjectTabs/ProjectGeneralInfo';
import { formatDate } from '../../LocalFunction';

const errorText = (error, fallback) => error?.response?.data?.message || fallback;
const percent = (done, total) => (total ? Math.round((done * 100) / total) : 0);
export const statusColor = (code) => (code === 'DONE' ? 'success' : code === 'DOING' ? 'warning' : 'default');

/** Thanh tiến độ nhỏ kèm "x/y việc". */
export const ProgressLine = ({ done = 0, total = 0, value }) => {
    const shown = value ?? percent(done, total);
    return (
        <Box display="flex" alignItems="center" gap={1} minWidth={160}>
            <LinearProgress variant="determinate" value={shown} color={shown >= 100 ? 'success' : 'primary'} sx={{ flex: 1, height: 8, borderRadius: 4 }} />
            <Typography variant="caption" fontWeight={600} sx={{ minWidth: 92 }}>{shown}%{total ? ` (${done}/${total} việc)` : ''}</Typography>
        </Box>
    );
};

const emptyTask = { id: null, name: '', assigneeId: '', endDate: '', description: '' };

const ProjectWorkspace = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const user = useAuthStore((state) => state.user);
    const hasRole = useAuthStore((state) => state.hasRole);
    const setSelectedProject = useProjectStore((state) => state.setSelectedProject);
    const [project, setProject] = useState(null);
    const [statuses, setStatuses] = useState([]);
    const [tasks, setTasks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [editOpen, setEditOpen] = useState(false);
    const [taskForm, setTaskForm] = useState(null);
    const [saving, setSaving] = useState(false);
    const [toDelete, setToDelete] = useState(null);

    const load = useCallback(async () => {
        setError('');
        try {
            const [projectRes, statusRes, taskRes] = await Promise.all([
                getProjectById(id), getProjectWorkingStatuses(id), pagingTasks({ projectId: id, pageIndex: 1, pageSize: 1000 }),
            ]);
            const data = projectRes?.data || null;
            setProject(data);
            setSelectedProject(data);
            setStatuses(statusRes?.data || []);
            setTasks(taskRes?.data?.content || []);
        } catch (failure) {
            setError(errorText(failure, 'Không tải được dự án. Có thể bạn không thuộc dự án này.'));
        } finally {
            setLoading(false);
        }
    }, [id, setSelectedProject]);
    useEffect(() => { setLoading(true); load(); return () => setSelectedProject(null); }, [load, setSelectedProject]);

    const members = useMemo(() => project?.staffs || [], [project]);
    const isManager = members.some((m) => m.staffId === user?.staffId && m.projectRole === 'MANAGER');
    const canManage = useAuthStore.getState().hasPermission('PROJECT_MANAGE') || isManager;
    const canChangeStatus = (task) => canManage || (task.assigneeId && task.assigneeId === user?.staffId);
    const counts = useMemo(() => statuses.map((s) => ({ ...s, count: tasks.filter((t) => t.statusId === s.id).length })), [statuses, tasks]);
    const shownTasks = statusFilter ? tasks.filter((t) => t.statusId === statusFilter) : tasks;

    const changeStatus = async (task, statusId) => {
        try {
            await updateTaskStatus(task.id, statusId);
            await load();
        } catch (failure) { toast.error(errorText(failure, 'Không cập nhật được trạng thái')); }
    };
    const openTask = (task) => setTaskForm(task ? {
        id: task.id, name: task.name || '', assigneeId: task.assigneeId || '', description: task.description || '',
        endDate: task.endTime ? String(task.endTime).slice(0, 10) : '', statusId: task.statusId,
    } : { ...emptyTask });
    const submitTask = async () => {
        if (!taskForm.name.trim()) { toast.error('Nhập tên công việc'); return; }
        setSaving(true);
        try {
            await saveTask({
                id: taskForm.id, projectId: id, name: taskForm.name.trim(), description: taskForm.description,
                assigneeId: taskForm.assigneeId || null, priority: 2, statusId: taskForm.statusId || null,
                endTime: taskForm.endDate ? `${taskForm.endDate}T23:59:00` : null,
            });
            toast.success(taskForm.id ? 'Đã cập nhật công việc' : `Đã thêm công việc${taskForm.assigneeId ? ' và gửi email cho người được giao' : ''}`);
            setTaskForm(null);
            await load();
        } catch (failure) { toast.error(errorText(failure, 'Không lưu được công việc')); }
        finally { setSaving(false); }
    };
    const confirmDelete = async () => {
        try { await deleteTask(toDelete.id); toast.success('Đã xóa công việc'); setToDelete(null); await load(); }
        catch (failure) { toast.error(errorText(failure, 'Không xóa được công việc')); }
    };

    if (loading) return <Box py={8} textAlign="center"><CircularProgress /></Box>;
    if (error || !project) return <Alert severity="error" action={<Button onClick={() => navigate('/projects')}>Về danh sách</Button>}>{error || 'Không tìm thấy dự án'}</Alert>;

    return (
        <div className="space-y-4 mt-2">
            {/* 1. Thông tin dự án */}
            <Paper elevation={0} className="p-4 border border-border">
                <Box display="flex" alignItems="flex-start" gap={1} flexWrap="wrap">
                    <IconButton onClick={() => navigate('/projects')} title="Về danh sách dự án"><ArrowBackIcon /></IconButton>
                    <Box flex={1} minWidth={240}>
                        <Typography variant="h6" fontWeight="bold">{project.name}</Typography>
                        <Typography variant="body2" color="text.secondary">
                            Mã dự án: <b>{project.code || '—'}</b>{project.companyName ? <> · Công ty / khách hàng: <b>{project.companyName}</b></> : null}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            Bắt đầu: <b>{formatDate(project.startDate) || '—'}</b> · Kết thúc: <b>{formatDate(project.endDate) || '—'}</b>
                        </Typography>
                    </Box>
                    <Chip label={project.isFinished ? 'Đã hoàn thành' : 'Đang thực hiện'} color={project.isFinished ? 'success' : 'warning'} size="small" />
                    {canManage && <Button size="small" variant="outlined" startIcon={<EditIcon />} onClick={() => setEditOpen(true)}>Sửa dự án & người tham gia</Button>}
                </Box>
                <Box mt={2}>
                    <Typography variant="subtitle2" gutterBottom>Tiến độ dự án</Typography>
                    <ProgressLine done={project.taskDone} total={project.taskTotal} value={project.progress} />
                    <Typography variant="caption" color="text.secondary">Tiến độ = số công việc đã “Hoàn thành” / tổng số công việc của dự án.</Typography>
                </Box>
            </Paper>

            {/* 2. Người tham gia */}
            <Paper elevation={0} className="p-4 border border-border">
                <Typography variant="subtitle1" fontWeight="bold" gutterBottom>Người tham gia ({members.length})</Typography>
                {!members.length ? <Alert severity="info">Chưa có ai trong dự án.{canManage ? ' Bấm “Sửa dự án & người tham gia” để thêm người.' : ''}</Alert> : (
                    <TableContainer>
                        <Table size="small">
                            <TableHead><TableRow>
                                <TableCell className="font-bold">Nhân viên</TableCell><TableCell className="font-bold">Vai trò trong dự án</TableCell>
                                <TableCell className="font-bold">Quyền</TableCell><TableCell className="font-bold">Tiến độ công việc được giao</TableCell>
                            </TableRow></TableHead>
                            <TableBody>{members.map((m) => (
                                <TableRow key={m.staffId} selected={m.staffId === user?.staffId}>
                                    <TableCell>{m.displayName} ({m.staffCode}){m.staffId === user?.staffId ? ' — bạn' : ''}</TableCell>
                                    <TableCell>{m.roleName || '—'}</TableCell>
                                    <TableCell>{m.projectRole === 'MANAGER' ? 'Quản lý dự án' : 'Người thực hiện'}</TableCell>
                                    <TableCell>{m.taskTotal ? <ProgressLine done={m.taskDone} total={m.taskTotal} /> : <Typography variant="caption" color="text.secondary">Chưa được giao việc</Typography>}</TableCell>
                                </TableRow>
                            ))}</TableBody>
                        </Table>
                    </TableContainer>
                )}
            </Paper>

            {/* 3. Công việc */}
            <Paper elevation={0} className="p-4 border border-border">
                <Box display="flex" alignItems="center" gap={1} flexWrap="wrap" mb={1}>
                    <Typography variant="subtitle1" fontWeight="bold" flex={1}>Công việc ({tasks.length})</Typography>
                    <Button size="small" variant="outlined" onClick={() => navigate(`/tasks/board?projectId=${id}`)}>Bảng Kanban</Button>
                    {canManage && <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={() => openTask(null)} disabled={!members.length}>Thêm công việc</Button>}
                </Box>
                <Box display="flex" gap={1} flexWrap="wrap" mb={2}>
                    <Chip label={`Tất cả (${tasks.length})`} color={!statusFilter ? 'primary' : 'default'} onClick={() => setStatusFilter('')} />
                    {counts.map((s) => <Chip key={s.id} label={`${s.name} (${s.count})`} color={statusFilter === s.id ? 'primary' : 'default'} onClick={() => setStatusFilter(s.id)} />)}
                </Box>
                {!tasks.length ? <Alert severity="info">Chưa có công việc.{canManage ? ' Bấm “Thêm công việc” để tạo và giao việc cho người tham gia.' : ''}</Alert> : (
                    <TableContainer>
                        <Table size="small">
                            <TableHead><TableRow>
                                <TableCell className="font-bold">Công việc</TableCell><TableCell className="font-bold">Người làm</TableCell>
                                <TableCell className="font-bold">Hạn hoàn thành</TableCell><TableCell className="font-bold" style={{ width: 170 }}>Trạng thái</TableCell>
                                {canManage && <TableCell className="font-bold" align="center" style={{ width: 100 }}>Thao tác</TableCell>}
                            </TableRow></TableHead>
                            <TableBody>{shownTasks.map((t) => (
                                <TableRow key={t.id} hover>
                                    <TableCell><Typography variant="body2" fontWeight={600}>{t.name}</Typography>
                                        {t.description && <Typography variant="caption" color="text.secondary" className="line-clamp-2">{t.description.replace(/<[^>]+>/g, ' ')}</Typography>}</TableCell>
                                    <TableCell>{t.assigneeName || <Typography variant="caption" color="warning.main">Chưa giao</Typography>}</TableCell>
                                    <TableCell>{t.endTime ? formatDate(t.endTime) : '—'}</TableCell>
                                    <TableCell>{canChangeStatus(t) ? (
                                        <TextField select size="small" value={t.statusId || ''} onChange={(e) => changeStatus(t, e.target.value)} fullWidth>
                                            {statuses.map((s) => <MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>)}
                                        </TextField>
                                    ) : <Chip size="small" label={t.statusName || '—'} color={statusColor(t.statusCode)} />}</TableCell>
                                    {canManage && <TableCell align="center">
                                        <Tooltip title="Sửa"><IconButton size="small" color="primary" onClick={() => openTask(t)}><EditIcon fontSize="small" /></IconButton></Tooltip>
                                        <Tooltip title="Xóa"><IconButton size="small" color="error" onClick={() => setToDelete(t)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
                                    </TableCell>}
                                </TableRow>
                            ))}</TableBody>
                        </Table>
                    </TableContainer>
                )}
            </Paper>

            {/* Thêm / sửa công việc */}
            <Dialog open={Boolean(taskForm)} onClose={() => !saving && setTaskForm(null)} fullWidth maxWidth="sm">
                <DialogTitle>{taskForm?.id ? 'Sửa công việc' : 'Thêm công việc'}</DialogTitle>
                <DialogContent>
                    {taskForm && <Grid container spacing={2} sx={{ mt: 0 }}>
                        <Grid item xs={12}><TextField label="Tên công việc" required fullWidth size="small" value={taskForm.name}
                            onChange={(e) => setTaskForm({ ...taskForm, name: e.target.value })} placeholder="VD: Làm trang giỏ hàng" /></Grid>
                        <Grid item xs={12} sm={7}><TextField select label="Giao cho" fullWidth size="small" value={taskForm.assigneeId}
                            onChange={(e) => setTaskForm({ ...taskForm, assigneeId: e.target.value })} helperText="Chỉ chọn được người tham gia dự án">
                            <MenuItem value="">— Chưa giao —</MenuItem>
                            {members.map((m) => <MenuItem key={m.staffId} value={m.staffId}>{m.displayName}{m.roleName ? ` — ${m.roleName}` : ''}</MenuItem>)}
                        </TextField></Grid>
                        <Grid item xs={12} sm={5}><TextField label="Hạn hoàn thành" type="date" fullWidth size="small" InputLabelProps={{ shrink: true }}
                            value={taskForm.endDate} onChange={(e) => setTaskForm({ ...taskForm, endDate: e.target.value })} /></Grid>
                        <Grid item xs={12}><TextField label="Mô tả" fullWidth multiline rows={3} size="small" value={taskForm.description}
                            onChange={(e) => setTaskForm({ ...taskForm, description: e.target.value })} /></Grid>
                        {!taskForm.id && <Grid item xs={12}><Alert severity="info">Công việc mới có trạng thái “Chưa làm”. Người được giao nhận email thông báo và tự cập nhật Đang làm / Hoàn thành.</Alert></Grid>}
                    </Grid>}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setTaskForm(null)} disabled={saving}>Hủy</Button>
                    <Button variant="contained" onClick={submitTask} disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</Button>
                </DialogActions>
            </Dialog>

            {/* Xác nhận xóa */}
            <Dialog open={Boolean(toDelete)} onClose={() => setToDelete(null)}>
                <DialogTitle>Xóa công việc?</DialogTitle>
                <DialogContent>Xóa “{toDelete?.name}” khỏi dự án.</DialogContent>
                <DialogActions><Button onClick={() => setToDelete(null)}>Hủy</Button><Button color="error" variant="contained" onClick={confirmDelete}>Xóa</Button></DialogActions>
            </Dialog>

            {/* Sửa thông tin & người tham gia (dùng lại form có sẵn) */}
            <Popup open={editOpen} onClosePopup={() => setEditOpen(false)} title="Sửa dự án & người tham gia" size="md">
                {editOpen && <ProjectGeneralInfo isViewMode={false} onSaved={async () => { setEditOpen(false); await load(); }} />}
            </Popup>
        </div>
    );
};

export default ProjectWorkspace;
