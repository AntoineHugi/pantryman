import axios from 'axios';
import API_BASE_URL from '../config';
import { clearQueryCache } from '../queryClient';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

// Add auth token to requests automatically
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 errors (expired/invalid token).
// /auth/* endpoints also return 401 for wrong credentials (login, current-password
// checks); those should show an error on the page, not log the user out.
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthEndpoint = error.config?.url?.startsWith('/auth/');
    if (error.response?.status === 401 && !isAuthEndpoint) {
      localStorage.removeItem('authToken');
      localStorage.removeItem('userId');
      clearQueryCache();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Extracts the backend's `{ error: "..." }` message from a failed request.
export function getErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError<{ error?: string }>(err)) {
    if (!err.response && err.code === axios.AxiosError.ERR_NETWORK) {
      return `${fallback}: no connection. Try again when you're online.`;
    }
    return err.response?.data?.error || fallback;
  }
  return fallback;
}

export default apiClient;