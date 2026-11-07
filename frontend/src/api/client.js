import axios from 'axios';

export const TOKEN_KEY = 'healthcare_token';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// An expired or revoked token (e.g. after a password change elsewhere) sends the user back to login.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const hadToken = Boolean(error.config?.headers?.Authorization);
    if (error.response?.status === 401 && hadToken) {
      localStorage.removeItem(TOKEN_KEY);
      if (!['/login', '/register'].includes(window.location.pathname)) window.location.assign('/login');
    }
    return Promise.reject(error);
  }
);

export function getErrorMessage(error) {
  if (error?.response?.status === 429) {
    const retry = error.response.headers?.['retry-after'];
    return `Too many requests. Try again${retry ? ` in ${retry} seconds` : ' shortly'}.`;
  }
  return error?.response?.data?.message || error?.response?.data?.error || error.message || 'Something went wrong';
}
