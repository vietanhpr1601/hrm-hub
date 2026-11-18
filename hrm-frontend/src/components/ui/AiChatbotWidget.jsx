import React, { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Alert, Box, Button, Chip, CircularProgress, Drawer, Fab, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { AutoAwesome, Close, DeleteOutline, Send, StopCircle, ContentCopy } from '@mui/icons-material';
import HttpService from '../../services/HttpService';
import ConstantList from '../../appConfig';
import useAuthStore from '../../store/useAuthStore';

const suggestions = ['Danh sách phòng ban', 'Tổng số nhân viên', 'Danh sách nhân viên'];
const modeLabel = { database: 'Tra cứu trực tiếp', ai: 'AI hỗ trợ', unavailable: 'Chưa thể xác minh' };

const AiChatbotWidget = () => {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(null);
  const pending = useRef(null);
  const end = useRef(null);
  const username = useAuthStore(state => state.user?.username);

  useEffect(() => {
    pending.current?.abort();
    pending.current = null;
    setMessages([]);
    setInput('');
    setError('');
    setLoading(false);
  }, [username]);
  useEffect(() => () => pending.current?.abort(), []);
  useEffect(() => { end.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages, loading, open]);

  const send = async (text, retry = false) => {
    if (!text.trim() || text.length > 6000 || pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    const history = retry ? messages : [...messages, { role: 'user', content: text.trim() }];
    setMessages(history);
    setInput('');
    setError('');
    setLoading(true);
    try {
      const response = await HttpService.post(`${ConstantList.API_ENPOINT}/chat`, {
        messages: history.slice(-23).map(({ role, content }) => ({ role, content })),
      }, { signal: controller.signal, timeout: 65000, skipGlobalLoading: true });
      if (pending.current !== controller) return;
      if (!response.data?.data?.content) throw new Error('Phản hồi từ trợ lý không hợp lệ. Hãy thử lại.');
      setMessages([...history, { role: 'assistant', ...response.data.data }]);
    } catch (failure) {
      if (pending.current !== controller || controller.signal.aborted) return;
      setError(failure.response?.status === 403 ? 'Tài khoản của bạn chưa được cấp quyền sử dụng trợ lý AI.'
        : failure.response?.status === 401 ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
          : failure.response?.data?.message || failure.message || 'Không kết nối được trợ lý. Hãy thử lại.');
    } finally {
      if (pending.current === controller) { pending.current = null; setLoading(false); }
    }
  };
  const cancel = () => { pending.current?.abort(); pending.current = null; setLoading(false); };
  const clear = () => { cancel(); setMessages([]); setError(''); setCopied(null); };
  const copy = async (content, index) => {
    try { await navigator.clipboard.writeText(content); setCopied(index); }
    catch { setError('Không sao chép được. Bạn có thể chọn nội dung để sao chép thủ công.'); }
  };
  const last = messages.at(-1);
  const needsReply = last?.role === 'user';

  return <>
    <Tooltip title="Trợ lý HRM" placement="left">
      <Fab aria-label="Mở trợ lý HRM" color="primary" onClick={() => setOpen(true)}
        sx={{ position: 'fixed', bottom: 24, right: 24, zIndex: 1000, background: 'var(--gradient-primary)' }}>
        <AutoAwesome />
      </Fab>
    </Tooltip>
    <Drawer anchor="right" open={open} onClose={() => setOpen(false)} PaperProps={{ sx: {
      width: { xs: '100%', sm: 460 }, maxWidth: '100vw', bgcolor: 'hsl(var(--background))',
      color: 'hsl(var(--foreground))', display: 'flex', overflow: 'hidden',
    } }}>
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ p: 2, background: 'var(--gradient-primary)', color: 'hsl(var(--primary-foreground))' }}>
        <AutoAwesome />
        <Box sx={{ flex: 1 }}>
          <Typography fontWeight={700}>Trợ lý HRM</Typography>
          <Typography variant="caption">Tra cứu dữ liệu · Hỗ trợ nghiệp vụ</Typography>
        </Box>
        <Tooltip title="Cuộc trò chuyện mới"><IconButton color="inherit" aria-label="Cuộc trò chuyện mới" onClick={clear}><DeleteOutline /></IconButton></Tooltip>
        <IconButton color="inherit" aria-label="Đóng trợ lý" onClick={() => setOpen(false)}><Close /></IconButton>
      </Stack>
      <Box sx={{ px: 2, py: 1, borderBottom: '1px solid hsl(var(--border))' }}>
        <Typography variant="caption" color="text.secondary">Dữ liệu hiển thị theo quyền của bạn. Hướng dẫn AI cần được đối chiếu trước khi áp dụng.</Typography>
      </Box>
      <Box role="log" aria-label="Hội thoại với trợ lý HRM" aria-live="polite" sx={{ flex: 1, minHeight: 0, overflowY: 'auto', p: 2 }}>
        {!messages.length && <Box sx={{ py: 4 }}>
          <Typography variant="h6" fontWeight={700} gutterBottom>Tôi có thể giúp gì cho bạn?</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>Tra cứu phòng ban, đếm hồ sơ nhân sự hoặc tìm nhân viên theo mã, tên và phòng ban. Ví dụ: “Tìm nhân viên NV001”.</Typography>
          <Stack spacing={1}>{suggestions.map(text => <Button key={text} variant="outlined" onClick={() => send(text)} sx={{ justifyContent: 'flex-start', textTransform: 'none' }}>{text}</Button>)}</Stack>
        </Box>}
        {messages.map((message, index) => <Box key={index} sx={{ mb: 2.5, ml: message.role === 'user' ? 4 : 0, mr: message.role === 'user' ? 0 : 1 }}>
          <Typography variant="caption" sx={{ display: 'block', mb: 0.5, color: 'text.secondary' }}>{message.role === 'user' ? 'Bạn' : 'Trợ lý HRM'}</Typography>
          <Paper elevation={0} sx={{ p: 1.75, borderRadius: 2.5, overflowWrap: 'anywhere',
            bgcolor: message.role === 'user' ? 'hsl(var(--primary) / 0.10)' : 'hsl(var(--card))',
            color: 'inherit', border: '1px solid hsl(var(--border))' }}>
            {message.role === 'user' ? <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{message.content}</Typography>
              : <>
                <Chip size="small" label={modeLabel[message.mode] || 'AI hỗ trợ'} sx={{ mb: 1 }} />
                <Box sx={{ fontSize: 14, lineHeight: 1.7, '& p': { mt: 0, mb: 1 }, '& ul, & ol': { pl: 2.5 }, '& pre': { overflowX: 'auto' } }}>
                  <ReactMarkdown components={{ a: ({ children }) => <span>{children}</span>, img: () => null }}>{message.content}</ReactMarkdown>
                </Box>
                {message.notice && <Alert severity="info" sx={{ mt: 1, fontSize: 12 }}>{message.notice}</Alert>}
                {!!message.sources?.length && <Box sx={{ mt: 1.5, pt: 1, borderTop: '1px solid hsl(var(--border))' }}>
                  <Typography variant="caption" fontWeight={600}>Dữ liệu đã tra cứu</Typography>
                  {message.sources.map(source => <Typography key={source} variant="caption" display="block">{source}</Typography>)}
                  <Typography variant="caption" color="text.secondary">{new Date(message.updatedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</Typography>
                </Box>}
                <Button size="small" startIcon={<ContentCopy sx={{ fontSize: 14 }} />} onClick={() => copy(message.content, index)} sx={{ mt: 1, textTransform: 'none' }}>{copied === index ? 'Đã sao chép' : 'Sao chép'}</Button>
              </>}
          </Paper>
        </Box>)}
        {loading && <Stack direction="row" spacing={1} alignItems="center"><CircularProgress size={16} /><Typography variant="body2">Đang xử lý câu hỏi…</Typography></Stack>}
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        {needsReply && !loading && <Button sx={{ mt: 1 }} onClick={() => send(last.content, true)}>Thử lại câu hỏi</Button>}
        <div ref={end} />
      </Box>
      {!!messages.length && <Stack direction="row" spacing={0.75} sx={{ px: 2, py: 1, overflowX: 'auto' }}>{suggestions.map(text => <Chip key={text} label={text} disabled={loading} onClick={() => send(text)} />)}</Stack>}
      <Box component="form" onSubmit={event => { event.preventDefault(); send(input); }} sx={{ p: 2, borderTop: '1px solid hsl(var(--border))', pb: 'max(16px, env(safe-area-inset-bottom))' }}>
        <Stack direction="row" spacing={1} alignItems="flex-end">
          <TextField fullWidth multiline maxRows={4} size="small" placeholder="Nhập câu hỏi về nhân sự…" value={input}
            onChange={event => setInput(event.target.value)} inputProps={{ maxLength: 6000, 'aria-label': 'Câu hỏi cho trợ lý' }}
            onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); if (!loading) send(input); } }} />
          {loading ? <IconButton aria-label="Dừng trả lời" onClick={cancel}><StopCircle /></IconButton>
            : <IconButton aria-label="Gửi câu hỏi" color="primary" disabled={!input.trim()} type="submit"><Send /></IconButton>}
        </Stack>
        <Typography variant="caption" color="text.secondary">Không gửi mật khẩu hoặc thông tin cá nhân nhạy cảm.</Typography>
      </Box>
    </Drawer>
  </>;
};

export default AiChatbotWidget;
