import { createContext } from 'react';
import type { SignupRequest, LoginRequest, SignupResponse } from '../api/types';

export interface AuthContextType {
  isAuthenticated: boolean;
  token: string | null;
  userId: string | null;
  signup: (data: SignupRequest) => Promise<SignupResponse>;
  login: (data: LoginRequest) => Promise<void>;
  logout: () => void;
}

// Kept apart from AuthProvider so that file only exports a component (react-refresh)
export const AuthContext = createContext<AuthContextType | undefined>(undefined);
