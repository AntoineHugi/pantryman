import { useState, useEffect, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '../api/auth';
import type { SignupRequest, LoginRequest, SignupResponse } from '../api/types';
import { AuthContext } from './AuthContextValue';

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState<string | null>(
    localStorage.getItem('authToken')
  );
  const [userId, setUserId] = useState<string | null>(
    localStorage.getItem('userId')
  );
  const [isAuthenticated, setIsAuthenticated] = useState(!!token);

  useEffect(() => {
    setIsAuthenticated(!!token);
  }, [token]);

  const signup = async (data: SignupRequest): Promise<SignupResponse> => {
    return await authApi.signup(data);
  };

  const login = async (data: LoginRequest) => {
    const response = await authApi.login(data);
    localStorage.setItem('authToken', response.token);
    localStorage.setItem('userId', response.userId);
    setToken(response.token);
    setUserId(response.userId);
  };

  const logout = () => {
    authApi.logout();
    // Don't show the previous user's lists to whoever logs in next
    queryClient.clear();
    setToken(null);
    setUserId(null);
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        token,
        userId,
        signup,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
