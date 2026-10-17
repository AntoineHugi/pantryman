import { useState, useEffect, type ReactNode } from 'react';
import { authApi } from '../api/auth';
import type { SignupRequest, LoginRequest, SignupResponse } from '../api/types';
import { AuthContext } from './AuthContextValue';
import { clearQueryCache } from '../queryClient';

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
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
    clearQueryCache();
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
