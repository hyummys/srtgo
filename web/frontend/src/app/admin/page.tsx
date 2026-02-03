'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, getStoredUser } from '@/lib/api';
import type { AdminUser } from '@/lib/types';

export default function AdminPage() {
  const router = useRouter();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState<number | null>(null);

  useEffect(() => {
    const user = getStoredUser();
    if (!user || user.role !== 'admin') {
      router.push('/');
      return;
    }
    loadUsers();
  }, []);

  async function loadUsers() {
    try {
      const res = await api.admin.listUsers();
      setUsers(res.users);
      setError('');
    } catch (e: any) {
      setError(e.message || 'Failed to load users');
    } finally {
      setLoading(false);
    }
  }

  async function handleApprove(userId: number) {
    setActionLoading(userId);
    try {
      await api.admin.approveUser(userId);
      await loadUsers();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setActionLoading(null);
    }
  }

  async function handleReject(userId: number) {
    setActionLoading(userId);
    try {
      await api.admin.rejectUser(userId);
      await loadUsers();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setActionLoading(null);
    }
  }

  async function handleDelete(userId: number, username: string) {
    if (!confirm(`정말 "${username}" 사용자를 삭제하시겠습니까? 모든 설정과 매크로 기록이 삭제됩니다.`)) {
      return;
    }
    setActionLoading(userId);
    try {
      await api.admin.deleteUser(userId);
      await loadUsers();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setActionLoading(null);
    }
  }

  function getStatusBadge(status: string) {
    switch (status) {
      case 'approved':
        return <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full">승인됨</span>;
      case 'pending':
        return <span className="text-xs bg-yellow-100 text-yellow-700 px-2 py-0.5 rounded-full">대기중</span>;
      case 'rejected':
        return <span className="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded-full">거절됨</span>;
      default:
        return <span className="text-xs bg-gray-100 text-gray-700 px-2 py-0.5 rounded-full">{status}</span>;
    }
  }

  function getRoleBadge(role: string) {
    if (role === 'admin') {
      return <span className="text-xs bg-primary-100 text-primary-700 px-2 py-0.5 rounded-full">관리자</span>;
    }
    return null;
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[40vh]">
        <div className="text-gray-400 text-sm">로딩 중...</div>
      </div>
    );
  }

  const currentUser = getStoredUser();

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">사용자 관리</h1>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm">
          {error}
        </div>
      )}

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {users.length === 0 ? (
          <div className="p-8 text-center text-gray-400 text-sm">
            등록된 사용자가 없습니다.
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {users.map((user) => (
              <div key={user.id} className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-sm">{user.nickname}</span>
                    <span className="text-xs text-gray-400">@{user.username}</span>
                    {getRoleBadge(user.role)}
                    {getStatusBadge(user.status)}
                  </div>
                  <span className="text-xs text-gray-400">
                    {new Date(user.created_at).toLocaleDateString('ko-KR')}
                  </span>
                </div>

                <div className="flex gap-2 mt-2">
                  {user.status === 'pending' && (
                    <>
                      <button
                        onClick={() => handleApprove(user.id)}
                        disabled={actionLoading === user.id}
                        className="text-xs px-3 py-1.5 bg-green-500 text-white rounded-lg hover:bg-green-600 disabled:opacity-50"
                      >
                        승인
                      </button>
                      <button
                        onClick={() => handleReject(user.id)}
                        disabled={actionLoading === user.id}
                        className="text-xs px-3 py-1.5 bg-red-500 text-white rounded-lg hover:bg-red-600 disabled:opacity-50"
                      >
                        거절
                      </button>
                    </>
                  )}
                  {user.status === 'rejected' && (
                    <button
                      onClick={() => handleApprove(user.id)}
                      disabled={actionLoading === user.id}
                      className="text-xs px-3 py-1.5 bg-green-500 text-white rounded-lg hover:bg-green-600 disabled:opacity-50"
                    >
                      승인
                    </button>
                  )}
                  {user.id !== currentUser?.id && (
                    <button
                      onClick={() => handleDelete(user.id, user.username)}
                      disabled={actionLoading === user.id}
                      className="text-xs px-3 py-1.5 bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200 disabled:opacity-50"
                    >
                      삭제
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
