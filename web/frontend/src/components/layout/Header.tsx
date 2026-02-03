'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { getStoredUser, clearToken } from '@/lib/api';

const navItems = [
  { href: '/', label: '홈' },
  { href: '/reserve', label: '예매' },
  { href: '/macro', label: '매크로' },
  { href: '/reservations', label: '예약확인' },
  { href: '/settings', label: '설정' },
];

export default function Header() {
  const pathname = usePathname();
  const user = typeof window !== 'undefined' ? getStoredUser() : null;

  function handleLogout() {
    clearToken();
    window.location.href = '/';
  }

  return (
    <header className="bg-white border-b border-gray-200 sticky top-0 z-40">
      <div className="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between">
        <Link href="/" className="text-lg font-bold text-primary-700">
          SRTgo
        </Link>
        <div className="hidden md:flex items-center gap-1">
          <nav className="flex gap-1">
            {navItems.map((item) => {
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`px-3 py-2 rounded-lg text-sm transition-colors ${
                    isActive
                      ? 'bg-primary-50 text-primary-700 font-semibold'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
            {user?.role === 'admin' && (
              <Link
                href="/admin"
                className={`px-3 py-2 rounded-lg text-sm transition-colors ${
                  pathname === '/admin'
                    ? 'bg-amber-50 text-amber-700 font-semibold'
                    : 'text-amber-600 hover:bg-amber-50'
                }`}
              >
                관리자
              </Link>
            )}
          </nav>
          {user && (
            <div className="flex items-center gap-2 ml-4 pl-4 border-l border-gray-200">
              <span className="text-xs text-gray-500">{user.nickname}</span>
              <button
                onClick={handleLogout}
                className="text-xs text-gray-400 hover:text-gray-600"
              >
                로그아웃
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
