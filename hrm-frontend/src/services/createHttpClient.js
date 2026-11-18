import axios from 'axios';

const authEndpoint = (url = '') => /\/auth\/(login|refresh|logout)(?:[?#]|$)/.exec(url)?.[1];

// Dependencies are injected so refresh and session races can be tested without a server.
export const createHttpClient = ({
  baseURL,
  getSession,
  setTokens,
  clearSession,
  refreshTokens,
  onSessionExpired,
  startLoading = () => {},
  stopLoading = () => {},
}) => {
  const client = axios.create({ baseURL, timeout: 30000 });
  let refreshInFlight = null;

  const expireSession = (session) => {
    const current = getSession();
    if (current.refreshToken !== session.refreshToken || current.accessToken !== session.accessToken) return;
    const wasAuthenticated = Boolean(current.user || current.accessToken || current.refreshToken);
    clearSession();
    if (wasAuthenticated) onSessionExpired();
  };

  client.interceptors.request.use((config) => {
    // Services may already include the relative API prefix in their URL.
    const prefix = config.baseURL?.replace(/\/+$/, '');
    if (prefix?.startsWith('/') && !prefix.startsWith('//')
      && (config.url === prefix || config.url?.startsWith(`${prefix}/`))) {
      config.baseURL = '';
    }
    if (!config.skipGlobalLoading) startLoading();
    const { accessToken, refreshToken } = getSession();
    const endpoint = authEndpoint(config.url);
    config._sessionRefreshToken = refreshToken;
    if (accessToken && endpoint !== 'login' && endpoint !== 'refresh') {
      config.headers.set('Authorization', `Bearer ${accessToken}`);
    } else {
      config.headers.delete('Authorization');
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => {
      if (!response.config.skipGlobalLoading) stopLoading();
      return response;
    },
    async (error) => {
      if (!error.config?.skipGlobalLoading) stopLoading();
      const request = error.config;
      if (error.response?.status !== 401 || !request || authEndpoint(request.url)) throw error;

      const session = getSession();
      // An old request must never refresh, retry, or clear a newly signed-in account.
      if (request._sessionRefreshToken !== session.refreshToken) throw error;
      if (request._retry || !session.refreshToken) {
        expireSession(session);
        throw error;
      }
      request._retry = true;

      // Another request may already have refreshed this token before this 401 arrived.
      if (session.accessToken && request.headers.get('Authorization') !== `Bearer ${session.accessToken}`) {
        return client(request);
      }

      if (!refreshInFlight || refreshInFlight.token !== session.refreshToken) {
        const pending = { token: session.refreshToken };
        pending.promise = (async () => {
          try {
            const response = await Promise.resolve().then(() => refreshTokens(session.refreshToken));
            const tokens = response.data?.data;
            if (response.data?.status !== 200 || !tokens?.accessToken) {
              throw new Error(response.data?.message || 'Phản hồi làm mới phiên không hợp lệ');
            }
            const current = getSession();
            if (current.refreshToken !== session.refreshToken || current.accessToken !== session.accessToken) {
              throw new Error('Phiên đăng nhập đã thay đổi');
            }
            setTokens(tokens.accessToken, tokens.refreshToken || session.refreshToken);
          } catch (refreshError) {
            expireSession(session);
            throw refreshError;
          } finally {
            if (refreshInFlight === pending) refreshInFlight = null;
          }
        })();
        refreshInFlight = pending;
      }

      await refreshInFlight.promise;
      return client(request);
    },
  );

  return client;
};
