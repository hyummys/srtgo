'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { api, setToken, setUser } from '@/lib/api';

export default function RegisterPage() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!username.trim() || !password.trim() || !nickname.trim()) {
      setError('모든 항목을 입력해주세요.');
      return;
    }
    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }
    if (password.length < 6) {
      setError('비밀번호는 6자 이상이어야 합니다.');
      return;
    }
    if (username.length < 3) {
      setError('아이디는 3자 이상이어야 합니다.');
      return;
    }

    setIsLoading(true);
    try {
      const res = await api.user.register({
        username: username.trim(),
        password: password.trim(),
        nickname: nickname.trim(),
      });

      if (res.status === 'approved' && res.token && res.user) {
        // First user (admin) — auto-login
        setToken(res.token);
        setUser(res.user);
        router.push('/');
      } else {
        // Pending approval
        setSuccess(res.message || '가입 완료! 관리자 승인을 기다려주세요.');
      }
    } catch (e: any) {
      setError(e.message || '회원가입에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh]">
      <div className="bg-white rounded-2xl shadow-sm p-8 w-full max-w-sm">
        <h1 className="text-xl font-bold text-center mb-2">회원가입</h1>
        <p className="text-gray-500 text-sm text-center mb-6">
          SRTgo Web 계정을 만드세요
        </p>

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-2 mb-4 text-red-600 text-xs text-center">
            {error}
          </div>
        )}
        {success && (
          <div className="bg-green-50 border border-green-200 rounded-lg p-3 mb-4 text-green-700 text-sm text-center">
            {success}
            <div className="mt-2">
              <Link href="/" className="text-primary-600 text-sm hover:underline font-semibold">
                로그인 페이지로 돌아가기
              </Link>
            </div>
          </div>
        )}

        {!success && (
          <form onSubmit={handleSubmit}>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="아이디 (3자 이상)"
              className="w-full px-4 py-3 border border-gray-300 rounded-xl mb-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              autoFocus
              autoComplete="username"
            />
            <input
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="닉네임"
              className="w-full px-4 py-3 border border-gray-300 rounded-xl mb-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호 (6자 이상)"
              className="w-full px-4 py-3 border border-gray-300 rounded-xl mb-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              autoComplete="new-password"
            />
            <input
              type="password"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              placeholder="비밀번호 확인"
              className="w-full px-4 py-3 border border-gray-300 rounded-xl mb-4 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              autoComplete="new-password"
            />
            <button
              type="submit"
              disabled={isLoading}
              className="w-full py-3 bg-primary-600 text-white rounded-xl font-semibold hover:bg-primary-700 transition-colors disabled:opacity-50"
            >
              {isLoading ? '가입 중...' : '회원가입'}
            </button>
          </form>
        )}

        <div className="mt-4 text-center">
          <Link href="/" className="text-gray-500 text-sm hover:underline">
            로그인으로 돌아가기
          </Link>
        </div>
      </div>
    </div>
  );
}
