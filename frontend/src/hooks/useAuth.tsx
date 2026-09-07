import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { client, getTokens, setTokens, type AuthTokens } from '../api/client';

interface AuthState {
  tokens: AuthTokens | null;
  login: (emailOrUsername: string, password: string) => Promise<void>;
  register: (email: string, username: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [tokens, setT] = useState<AuthTokens | null>(() => getTokens());

  const login = useCallback(async (emailOrUsername: string, password: string) => {
    const t = await client.login({ emailOrUsername, password });
    setTokens(t);
    setT(t);
  }, []);

  const register = useCallback(async (email: string, username: string, password: string, displayName: string) => {
    const t = await client.register({ email, username, password, displayName });
    setTokens(t);
    setT(t);
  }, []);

  const logout = useCallback(() => {
    setTokens(null);
    setT(null);
  }, []);

  const value = useMemo(() => ({ tokens, login, register, logout }), [tokens, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
