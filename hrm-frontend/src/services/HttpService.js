import { toast } from 'sonner';
import useUiStore from '../store/uiStore';
import useAuthStore from '../store/useAuthStore';
import ConstantList from '../appConfig';
import { navigateTo } from '../navigation';
import * as AuthService from './AuthService';
import { createHttpClient } from './createHttpClient.js';

const HttpService = createHttpClient({
    baseURL: ConstantList.API_ENPOINT,
    getSession: () => useAuthStore.getState(),
    setTokens: (accessToken, refreshToken) => useAuthStore.setState({ accessToken, refreshToken }),
    clearSession: () => useAuthStore.getState().clearAuth(),
    refreshTokens: (token) => AuthService.refreshToken(token),
    startLoading: () => useUiStore.getState().startLoading(),
    stopLoading: () => useUiStore.getState().stopLoading(),
    onSessionExpired: () => {
        if (window.location.pathname === '/login') return;
        toast.error('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại!');
        const { pathname, search, hash } = window.location;
        navigateTo('/login', { replace: true, state: { from: { pathname, search, hash } } });
    },
});

export default HttpService;
