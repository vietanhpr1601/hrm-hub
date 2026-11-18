import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Badge, IconButton, Tooltip } from '@mui/material';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import useNotificationStore from '../../store/useNotificationStore';
import usePermission from '../../hooks/usePermission';

export default function NotificationBell() {
  const identity = useNotificationStore(state => state.identity);
  const count = useNotificationStore(state => state.unreadCount);
  const refresh = useNotificationStore(state => state.refreshCount);
  const allowed = usePermission('NOTIFICATION_VIEW');
  useEffect(() => {
    if (!identity || !allowed) return undefined;
    const update = () => { if (document.visibilityState !== 'hidden') refresh(); };
    update();
    const timer = window.setInterval(update, 30000);
    window.addEventListener('focus', update);
    document.addEventListener('visibilitychange', update);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', update);
      document.removeEventListener('visibilitychange', update);
    };
  }, [identity, allowed, refresh]);
  if (!identity || !allowed) return null;
  return <Tooltip title={count ? `${count} thông báo chưa đọc` : 'Thông báo'}>
    <IconButton component={Link} to="/announcement/announcement" color="primary" aria-label={`Thông báo, ${count} chưa đọc`}>
      <Badge badgeContent={count} color="error" max={99}><NotificationsNoneIcon /></Badge>
    </IconButton>
  </Tooltip>;
}
