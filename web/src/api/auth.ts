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

  resendVerification: async (email: string): Promise<void> => {
    await apiClient.post('/auth/resend-verification', { email });
  },

  logout: () => {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userId');
  },
};