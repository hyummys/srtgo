import type { User, LoginResponse, RegisterResponse, AdminUser, Passengers } from './types';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || '';

function getToken(): string {
  if (typeof window === 'undefined') return '';
  return localStorage.getItem('srtgo_jwt') || '';
}

export function setToken(token: string) {
  localStorage.setItem('srtgo_jwt', token);
}

export function getStoredToken(): string {
  return getToken();
}

export function clearToken() {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('srtgo_jwt');
    localStorage.removeItem('srtgo_user');
  }
}

export function setUser(user: User) {
  localStorage.setItem('srtgo_user', JSON.stringify(user));
}

export function getStoredUser(): User | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem('srtgo_user');
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      headers,
      ...options,
    });
  } catch {
    // Network error (server down, CORS blocked, etc.)
    throw new Error('서버에 연결할 수 없습니다. 백엔드 서버가 실행 중인지 확인하세요.');
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({ detail: res.statusText }));
    // Token-level auth error from middleware
    if (body.code === 'TOKEN_REQUIRED' || body.code === 'TOKEN_INVALID') {
      clearToken();
      if (typeof window !== 'undefined' && window.location.pathname !== '/') {
        window.location.href = '/';
      }
      throw new Error('AUTH_REQUIRED');
    }
    throw new Error(body.detail || `API Error: ${res.status}`);
  }

  return res.json();
}

export const api = {
  health: () => apiFetch<{ status: string }>('/api/health'),

  user: {
    register: (data: { username: string; password: string; nickname: string }) =>
      apiFetch<RegisterResponse>('/api/user/register', {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    login: (data: { username: string; password: string }) =>
      apiFetch<LoginResponse>('/api/user/login', {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    me: () => apiFetch<User>('/api/user/me'),
  },

  admin: {
    listUsers: () => apiFetch<{ users: AdminUser[] }>('/api/admin/users'),
    approveUser: (userId: number) =>
      apiFetch<{ status: string }>(`/api/admin/users/${userId}/approve`, { method: 'PUT' }),
    rejectUser: (userId: number) =>
      apiFetch<{ status: string }>(`/api/admin/users/${userId}/reject`, { method: 'PUT' }),
    deleteUser: (userId: number) =>
      apiFetch<{ status: string }>(`/api/admin/users/${userId}`, { method: 'DELETE' }),
  },

  auth: {
    login: (data: { rail_type: string; id: string; password: string }) =>
      apiFetch<{ status: string; user: Record<string, string> }>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    status: () =>
      apiFetch<Record<string, { logged_in: boolean; id: string | null }>>('/api/auth/status'),
  },

  trains: {
    stations: () => apiFetch<Record<string, string[]>>('/api/trains/stations'),
    search: (data: {
      rail_type: string;
      departure: string;
      arrival: string;
      date: string;
      time: string;
      passengers: Passengers;
    }) =>
      apiFetch<{ trains: any[] }>('/api/trains/search', {
        method: 'POST',
        body: JSON.stringify(data),
      }),
  },

  reservations: {
    list: (railType: string) =>
      apiFetch<{ reservations: any[] }>(`/api/reservations/${railType}`),
    pay: (railType: string, reservationNumber: string) =>
      apiFetch<{ status: string }>(`/api/reservations/${railType}/pay`, {
        method: 'POST',
        body: JSON.stringify({ reservation_number: reservationNumber }),
      }),
    cancel: (railType: string, reservationNumber: string) =>
      apiFetch<{ status: string }>(`/api/reservations/${railType}/${reservationNumber}`, {
        method: 'DELETE',
      }),
  },

  settings: {
    getStations: (railType: string) =>
      apiFetch<{ available: string[]; selected: string[] }>(`/api/settings/stations/${railType}`),
    saveStations: (railType: string, stations: string[]) =>
      apiFetch(`/api/settings/stations/${railType}`, {
        method: 'PUT',
        body: JSON.stringify({ stations }),
      }),
    getTelegram: () => apiFetch<any>('/api/settings/telegram'),
    saveTelegram: (data: { token: string; chat_id: string }) =>
      apiFetch('/api/settings/telegram', { method: 'PUT', body: JSON.stringify(data) }),
    getCard: () => apiFetch<any>('/api/settings/card'),
    saveCard: (data: { number: string; password: string; birthday: string; expire: string }) =>
      apiFetch('/api/settings/card', { method: 'PUT', body: JSON.stringify(data) }),
    getOptions: () => apiFetch<{ options: string[] }>('/api/settings/options'),
    saveOptions: (options: string[]) =>
      apiFetch('/api/settings/options', { method: 'PUT', body: JSON.stringify({ options }) }),
    getDefaults: (railType: string) => apiFetch<any>(`/api/settings/defaults/${railType}`),
    saveDefaults: (railType: string, data: any) =>
      apiFetch(`/api/settings/defaults/${railType}`, { method: 'PUT', body: JSON.stringify(data) }),
  },

  macro: {
    start: (data: {
      rail_type: string;
      departure: string;
      arrival: string;
      date: string;
      time: string;
      passengers: Passengers;
      train_indices: number[];
      seat_type: string;
      auto_pay: boolean;
    }) =>
      apiFetch<{ task_id: string }>('/api/macro/start', {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    active: () => apiFetch<{ tasks: any[] }>('/api/macro/active'),
    status: (taskId: string) => apiFetch<any>(`/api/macro/${taskId}`),
    cancel: (taskId: string) =>
      apiFetch<{ status: string }>(`/api/macro/${taskId}`, { method: 'DELETE' }),
  },
};
