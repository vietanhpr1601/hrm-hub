import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material';

const cancelled = () => Object.assign(new Error('Đã hủy thao tác'), { name: 'ActionCancelled' });

async function errorMessage(error) {
  let data = error?.response?.data;
  if (data instanceof Blob) {
    try { data = JSON.parse(await data.text()); } catch { data = null; }
  }
  return data?.message || error?.message || 'Không thể thực hiện thao tác. Vui lòng thử lại.';
}

function PasswordActionDialog({ title, description, username, onConfirm, onCancel }) {
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const inputRef = useRef(null);
  const submit = async event => {
    event.preventDefault();
    if (inFlight.current || !password.length) return;
    inFlight.current = true;
    setBusy(true);
    setError('');
    try { await onConfirm(password); }
    catch (reason) { setError(await errorMessage(reason)); }
    finally {
      setPassword('');
      setBusy(false);
      inFlight.current = false;
      inputRef.current?.focus();
    }
  };
  return <Dialog open onClose={() => !inFlight.current && onCancel()} maxWidth="xs" fullWidth>
    <form onSubmit={submit}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {description && <Typography variant="body2" color="text.secondary">{description}</Typography>}
        <Typography variant="body2">Xác nhận mật khẩu đăng nhập của <strong>{username}</strong>.</Typography>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField autoFocus inputRef={inputRef} required fullWidth type="password" label="Mật khẩu xác nhận"
          autoComplete="current-password" value={password} disabled={busy}
          onChange={event => { setPassword(event.target.value); setError(''); }} />
      </DialogContent>
      <DialogActions>
        <Button disabled={busy} onClick={onCancel}>Hủy</Button>
        <Button type="submit" variant="contained" disabled={busy || !password.length}
          startIcon={busy ? <CircularProgress size={16} color="inherit" /> : undefined}>Xác nhận</Button>
      </DialogActions>
    </form>
  </Dialog>;
}

/** Executes the operation inside its password prompt; credentials never enter a store or localStorage. */
export default function usePasswordAction(username) {
  const pending = useRef(null);
  const [request, setRequest] = useState(null);
  const runWithPassword = useCallback(options => new Promise((resolve, reject) => {
    if (pending.current) { reject(cancelled()); return; }
    const next = { ...options, username, resolve, reject };
    pending.current = next;
    setRequest(next);
  }), [username]);
  const cancel = useCallback(() => {
    const current = pending.current;
    pending.current = null;
    setRequest(null);
    current?.reject(cancelled());
  }, []);
  useEffect(() => { cancel(); }, [username, cancel]);
  useEffect(() => () => {
    pending.current?.reject(cancelled());
    pending.current = null;
  }, []);
  const confirm = async password => {
    const current = pending.current;
    if (!current) return;
    if (current.username !== username) { cancel(); return; }
    try {
      const result = await current.action(password);
      if (pending.current !== current) return;
      pending.current = null;
      setRequest(null);
      current.resolve(result);
    } catch (error) {
      if (Array.isArray(error?.response?.data?.data?.errors) && error.response.data.data.errors.length) {
        pending.current = null;
        setRequest(null);
        current.reject(error);
        return;
      }
      throw error;
    }
  };
  return { runWithPassword, passwordDialog: request && request.username === username
    ? <PasswordActionDialog title={request.title} description={request.description} username={username}
        onConfirm={confirm} onCancel={cancel} /> : null };
}
