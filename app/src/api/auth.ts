import apiClient from './client';
import type { AuthResponse, SignupResponse, SignupRequest, LoginRequest } from './types';

export const authApi = {
  signup: async (data: SignupRequest): Promise<SignupResponse> => {
    const response = await apiClient.post<SignupResponse>('/auth/signup', data);
    return response.data;
  },

  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', data);
    return response.data;
  },

  verifyEmail: async (token: string): Promise<boolean> => {
    const response = await apiClient.post<{ verified: boolean }>('/auth/verify', { token });
    return response.data.verified;
  },

  resendVerification: async (email: string, redirectPath?: string): Promise<void> => {
    await apiClient.post('/auth/resend-verification', { email, redirectPath });
  },

  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await apiClient.put('/auth/password', { currentPassword, newPassword });
  },

  deleteAccount: async (password: string): Promise<void> => {
    await apiClient.delete('/auth/account', { data: { password } });
  },

  logout: () => {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userId');
  },
};