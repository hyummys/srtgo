'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, getStoredToken, setToken, clearToken, setUser, getStoredUser } from '@/lib/api';
import type { AuthStatus, User } from '@/lib/types';

export default function HomePage() {
  const [authStatus, setAuthStatus] = useState<AuthStatus | null>(null);
  const [activeMacros, setActiveMacros] = useState<any[]>([]);
  const [error, setError] = useState('');
  const [needsLogin, setNeedsLogin] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [currentUser, setCurrentUser] = useState<User | null>(null);

  // Login form
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const token = getStoredToken();
    const user = getStoredUser();
    if (!token) {
      setNeedsLogin(true);
    } else {
      setCurrentUser(user);
      loadData();
    }
  }, []);

  async function loadData() {
    try {
      const [status, macros] = await Promise.all([
        api.auth.status(),
        api.macro.active(),
      ]);
      setAuthStatus(status as unknown as AuthStatus);
      setActiveMacros(macros.tasks);
      setError('');
    } catch (e: any) {
      const msg = e.message || '';
      if (msg === 'AUTH_REQUIRED') {
        setLoginError('세션이 만료되었습니다. 다시 로그인해주세요.');
        setNeedsLogin(true);
        setCurrentUser(null);
      } else if (msg.includes('연결할 수 없습니다')) {
        setLoginError('서버에 연결할 수 없습니다. 백엔드 서버가 실행 중인지 확인하세요.');
        setNeedsLogin(true);
      } else {
        setError(msg);
      }
    }
  }

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;
    setIsLoading(true);
    setLoginError('');

    try {
      const res = await api.user.login({ username: username.trim(), password: password.trim() });
      setToken(res.token);
      setUser(res.user);
      setCurrentUser(res.user);
      setNeedsLogin(false);
      loadData();
    } catch (e: any) {
      setLoginError(e.message || '로그인에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  }

  function handleLogout() {
    clearToken();
    setCurrentUser(null);
    setAuthStatus(null);
    setActiveMacros([]);
    setNeedsLogin(true);
    setUsername('');
    setPassword('');
  }

  if (needsLogin) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh]">
        <div className="bg-white rounded-2xl shadow-sm p-8 w-full max-w-sm">
          <h1 className="text-xl font-bold text-center mb-2">SRTgo Web</h1>
          <p className="text-gray-500 text-sm text-center mb-6">
            로그인하여 시작하세요
          </p>
          {loginError && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-2 mb-4 text-red-600 text-xs text-center">
              {loginError}
            </div>
          )}
          <form onSubmit={handleLogin}>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="아이디"
              className="w-full px-4 py-3 border border-gray-300 rounded-xl mb-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              autoFocus
              autoComplete="username"
            />
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호"
              className="w-full px-4 py-3 border border-gray-300 rounded-xl mb-4 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              autoComplete="current-password"
            />
            <button
              type="submit"
              disabled={isLoading}
              className="w-full py-3 bg-primary-600 text-white rounded-xl font-semibold hover:bg-primary-700 transition-colors disabled:opacity-50"
            >
              {isLoading ? '로그인 중...' : '로그인'}
            </button>
          </form>
          <div className="mt-4 text-center">
            <Link href="/register" className="text-primary-600 text-sm hover:underline">
              회원가입
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">SRTgo</h1>
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-600">
            {currentUser?.nickname || currentUser?.username}
            {currentUser?.role === 'admin' && (
              <span className="ml-1 text-xs bg-primary-100 text-primary-700 px-1.5 py-0.5 rounded">관리자</span>
            )}
          </span>
          <button
            onClick={handleLogout}
            className="text-xs text-gray-400 hover:text-gray-600"
          >
            로그아웃
          </button>
        </div>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          {error}
        </div>
      )}

      {/* Login Status */}
      <div className="bg-white rounded-2xl shadow-sm p-4">
        <h2 className="text-sm font-semibold text-gray-500 mb-3">로그인 상태</h2>
        <div className="flex gap-3">
          {(['SRT', 'KTX'] as const).map((rail) => {
            const info = authStatus?.[rail];
            return (
              <div
                key={rail}
                className={`flex-1 rounded-xl p-3 text-center ${
                  info?.logged_in
                    ? 'bg-green-50 border border-green-200'
                    : 'bg-gray-50 border border-gray-200'
                }`}
              >
                <div className="font-bold text-sm">{rail}</div>
                <div className={`text-xs mt-1 ${info?.logged_in ? 'text-green-600' : 'text-gray-400'}`}>
                  {info?.logged_in ? `${info.id}` : '미연결'}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Active Macros */}
      {activeMacros.length > 0 && (
        <div className="bg-white rounded-2xl shadow-sm p-4">
          <h2 className="text-sm font-semibold text-gray-500 mb-3">실행 중인 매크로</h2>
          {activeMacros.map((task) => (
            <Link
              key={task.task_id}
              href="/macro"
              className="block bg-blue-50 border border-blue-200 rounded-xl p-3 mb-2"
            >
              <div className="flex justify-between items-center">
                <span className="font-semibold text-sm">
                  {task.departure} → {task.arrival}
                </span>
                <span className="text-xs text-blue-600 font-mono">
                  {task.attempts}회 시도중
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}

      {/* Quick Actions */}
      <div className="grid grid-cols-2 gap-3">
        <Link
          href="/reserve"
          className="bg-primary-600 text-white rounded-2xl p-6 text-center shadow-sm hover:bg-primary-700 transition-colors"
        >
          <svg className="w-8 h-8 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <div className="font-semibold">열차 조회</div>
        </Link>
        <Link
          href="/reservations"
          className="bg-white text-gray-700 rounded-2xl p-6 text-center shadow-sm border border-gray-200 hover:bg-gray-50 transition-colors"
        >
          <svg className="w-8 h-8 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          <div className="font-semibold">예약 확인</div>
        </Link>
        <Link
          href="/macro"
          className="bg-white text-gray-700 rounded-2xl p-6 text-center shadow-sm border border-gray-200 hover:bg-gray-50 transition-colors"
        >
          <svg className="w-8 h-8 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          <div className="font-semibold">매크로 모니터</div>
        </Link>
        <Link
          href="/settings"
          className="bg-white text-gray-700 rounded-2xl p-6 text-center shadow-sm border border-gray-200 hover:bg-gray-50 transition-colors"
        >
          <svg className="w-8 h-8 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
          <div className="font-semibold">설정</div>
        </Link>
        {currentUser?.role === 'admin' && (
          <Link
            href="/admin"
            className="bg-amber-50 text-amber-700 rounded-2xl p-6 text-center shadow-sm border border-amber-200 hover:bg-amber-100 transition-colors col-span-2"
          >
            <svg className="w-8 h-8 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
            <div className="font-semibold">사용자 관리</div>
          </Link>
        )}
      </div>
    </div>
  );
}
