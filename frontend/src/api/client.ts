const API_BASE = import.meta.env.VITE_API_BASE ?? '';

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  userId: string;
  username: string;
  email: string;
}

let tokens: AuthTokens | null = null;

function readLS(key: string): string | null {
  try {
    if (typeof localStorage === 'undefined') return null;
    return localStorage.getItem(key);
  } catch {
    return null; // SSR / private-mode: storage unavailable
  }
}

function writeLS(key: string, value: string | null): void {
  try {
    if (typeof localStorage === 'undefined') return;
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // storage unavailable: tokens stay in memory only
  }
}

try {
  const raw = readLS('orbitflow.tokens');
  if (raw) tokens = JSON.parse(raw);
} catch {
  tokens = null;
}

export function getTokens(): AuthTokens | null {
  return tokens;
}

export function setTokens(t: AuthTokens | null) {
  tokens = t;
  writeLS('orbitflow.tokens', t ? JSON.stringify(t) : null);
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((init.headers as Record<string, string>) ?? {}),
  };
  if (tokens?.accessToken) headers['Authorization'] = `Bearer ${tokens.accessToken}`;
  const wsId = readLS('orbitflow.workspaceId');
  if (wsId) headers['X-Workspace-Id'] = wsId;
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? (JSON.parse(text) as T) : (undefined as T);
  if (!res.ok) {
    const err = data as unknown as { detail?: string; code?: string };
    throw new Error(err?.detail ?? `Request failed: ${res.status}`);
  }
  return data;
}

export const client = {
  register: (body: object) =>
    api<AuthTokens>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (body: object) =>
    api<AuthTokens>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify(body) }),
};
