import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Grid, MenuItem, Paper,
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography
} from '@mui/material';
import { toast } from 'sonner';
import { getMyTasks, updateTaskStatus } from '../../services/taskService';
import { getProjects, getProjectWorkingStatuses } from '../../services/projectService';
import useAuthStore from '../../store/useAuthStore';
import { formatDate } from '../../LocalFunction';
import { ProgressLine } from '../Project/ProjectWorkspace';

const STEPS = [{ code: '', label: 'Tất cả' }, { code: 'TODO', label: 'Chưa làm' }, { code: 'DOING', label: 'Đang làm' }, { code: 'DONE', label: 'Hoàn thành' }];

/** Trang của người dùng: việc được giao cho mình, dự án mình tham gia và tiến độ của mình. */
const MyTasks = () => {
    const navigate = useNavigate();
    const user = useAuthStore((state) => state.user);
    const [tasks, setTasks] = useState([]);
    const [projects, setProjects] = useState([]);
    const [statusesByProject, setStatusesByProject] = useState({});
    const [loading, setLoading] = useState(true);
    const [step, setStep] = useState('');

    const load = useCallback(async () => {
        try {
            const [taskRes, projectRes] = await Promise.all([getMyTasks({ pageIndex: 1, pageSize: 1000 }), getProjects()]);
            const mine = taskRes?.data?.content || [];
            setTasks(mine);
            setProjects((projectRes?.data || []).filter((p) => (p.staffs || []).some((s) => s.staffId === user?.staffId)));
            const ids = [...new Set(mine.map((t) => t.projectId).filter(Boolean))];
            const lists = await Promise.all(ids.map((pid) => getProjectWorkingStatuses(pid).then((r) => [pid, r?.data || []]).catch(() => [pid, []])));
            setStatusesByProject(Object.fromEntries(lists));
        } catch (failure) {
            toast.error(failure?.response?.data?.message || 'Không tải được công việc');
        } finally { setLoading(false); }
    }, [user?.staffId]);
    useEffect(() => { load(); }, [load]);

    const done = tasks.filter((t) => t.statusCode === 'DONE').length;
    const shown = useMemo(() => (step ? tasks.filter((t) => t.statusCode === step) : tasks), [tasks, step]);
    const changeStatus = async (task, statusId) => {
        try { await updateTaskStatus(task.id, statusId); await load(); }
        catch (failure) { toast.error(failure?.response?.data?.message || 'Không cập nhật được trạng thái'); }
    };

    if (loading) return <Box py={8} textAlign="center"><CircularProgress /></Box>;

    return (
        <div className="space-y-4 mt-2">
            <Paper elevation={0} className="p-4 border border-border">
                <Typography variant="h6" fontWeight="bold">Công việc của tôi</Typography>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                    Đổi trạng thái khi bắt đầu làm (“Đang làm”) và khi xong (“Hoàn thành”). Tiến độ của bạn và của dự án tự cập nhật theo đó.
                </Typography>
                <Box maxWidth={520} mt={1}><ProgressLine done={done} total={tasks.length} /></Box>
            </Paper>

            <Paper elevation={0} className="p-4 border border-border">
                <Box display="flex" gap={1} flexWrap="wrap" mb={2}>
                    {STEPS.map((s) => <Chip key={s.code} color={step === s.code ? 'primary' : 'default'} onClick={() => setStep(s.code)}
                        label={`${s.label} (${s.code ? tasks.filter((t) => t.statusCode === s.code).length : tasks.length})`} />)}
                </Box>
                {!tasks.length ? <Alert severity="info">Bạn chưa được giao công việc nào.</Alert> : (
                    <TableContainer>
                        <Table size="small">
                            <TableHead><TableRow>
                                <TableCell className="font-bold">Công việc</TableCell><TableCell className="font-bold">Dự án</TableCell>
                                <TableCell className="font-bold">Hạn hoàn thành</TableCell><TableCell className="font-bold" style={{ width: 170 }}>Trạng thái</TableCell>
                            </TableRow></TableHead>
                            <TableBody>{shown.map((t) => {
                                const late = t.endTime && t.statusCode !== 'DONE' && new Date(t.endTime) < new Date();
                                return (
                                    <TableRow key={t.id} hover>
                                        <TableCell><Typography variant="body2" fontWeight={600}>{t.name}</Typography>
                                            {t.description && <Typography variant="caption" color="text.secondary" className="line-clamp-2">{t.description.replace(/<[^>]+>/g, ' ')}</Typography>}</TableCell>
                                        <TableCell><Button size="small" sx={{ textTransform: 'none' }} onClick={() => navigate(`/projects/${t.projectId}/view`)}>{t.projectName}</Button></TableCell>
                                        <TableCell>{t.endTime ? <Typography variant="body2" color={late ? 'error' : 'inherit'}>{formatDate(t.endTime)}{late ? ' (quá hạn)' : ''}</Typography> : '—'}</TableCell>
                                        <TableCell>
                                            <TextField select size="small" fullWidth value={t.statusId || ''} onChange={(e) => changeStatus(t, e.target.value)}>
                                                {(statusesByProject[t.projectId] || []).map((s) => <MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>)}
                                            </TextField>
                                        </TableCell>
                                    </TableRow>
                                );
                            })}</TableBody>
                        </Table>
                    </TableContainer>
                )}
            </Paper>

            <Paper elevation={0} className="p-4 border border-border">
                <Typography variant="subtitle1" fontWeight="bold" gutterBottom>Dự án của tôi ({projects.length})</Typography>
                {!projects.length ? <Alert severity="info">Bạn chưa tham gia dự án nào.</Alert> : (
                    <Grid container spacing={2}>{projects.map((p) => {
                        const me = (p.staffs || []).find((s) => s.staffId === user?.staffId);
                        return (
                            <Grid item xs={12} md={6} lg={4} key={p.id}>
                                <Card variant="outlined"><CardActionArea onClick={() => navigate(`/projects/${p.id}/view`)}><CardContent>
                                    <Typography fontWeight="bold">{p.name}</Typography>
                                    <Typography variant="caption" color="text.secondary">{p.code}{p.companyName ? ` · ${p.companyName}` : ''}</Typography>
                                    <Typography variant="body2" sx={{ mt: 1 }}>Vai trò của bạn: <b>{me?.roleName || (me?.projectRole === 'MANAGER' ? 'Quản lý dự án' : 'Người thực hiện')}</b></Typography>
                                    <Typography variant="caption" color="text.secondary">Tiến độ của bạn</Typography>
                                    <ProgressLine done={me?.taskDone || 0} total={me?.taskTotal || 0} />
                                    <Typography variant="caption" color="text.secondary">Tiến độ dự án</Typography>
                                    <ProgressLine done={p.taskDone} total={p.taskTotal} value={p.progress} />
                                </CardContent></CardActionArea></Card>
                            </Grid>
                        );
                    })}</Grid>
                )}
            </Paper>
        </div>
    );
};

export default MyTasks;
