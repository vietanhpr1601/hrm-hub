import { Navigate, useLocation } from 'react-router-dom';
import useAuthStore from '@/store/useAuthStore';
import { getAuthRedirect } from '../../utils/auth.js';

const AuthGuard = ({ children, roles, permissions }) => {
  const user = useAuthStore((state) => state.user);
  const accessToken = useAuthStore((state) => state.accessToken);
  const location = useLocation();

  const redirect = getAuthRedirect({ user, accessToken, roles, permissions });
  if (redirect === '/login') {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (redirect) {
    return <Navigate to="/access-denied" replace />;
  }

  return children;
};

export default AuthGuard;
