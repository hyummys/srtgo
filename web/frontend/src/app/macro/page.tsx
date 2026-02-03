'use client';

import { useState, useEffect, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { api } from '@/lib/api';
import { useMacroWebSocket } from '@/lib/ws';
import { formatElapsed } from '@/lib/constants';
import type { MacroTask, MacroEvent } from '@/lib/types';

function MacroContent() {
  const searchParams = useSearchParams();
  const taskIdParam = searchParams.get('task');

  const [activeTasks, setActiveTasks] = useState<MacroTask[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(taskIdParam);
  const [error, setError] = useState('');

  const { events, latestEvent, wsStatus, cancel } = useMacroWebSocket(selectedTaskId);

  useEffect(() => {
    loadTasks();
    const interval = setInterval(loadTasks, 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (taskIdParam) setSelectedTaskId(taskIdParam);
  }, [taskIdParam]);

  async function loadTasks() {
    try {
      const result = await api.macro.active();
      setActiveTasks(result.tasks);
      if (!selectedTaskId && result.tasks.length > 0) {
        setSelectedTaskId(result.tasks[0].task_id);
      }
    } catch (e: any) {
      setError(e.message);
    }
  }

  async function handleCancel(taskId: string) {
    try {
      await api.macro.cancel(taskId);
      loadTasks();
    } catch (e: any) {
      setError(e.message);
    }
  }

  const latestTick = events
    .filter((e) => e.type === 'tick')
    .slice(-1)[0];

  const successEvent = events.find((e) => e.type === 'success');
  const errorEvents = events.filter((e) => e.type === 'error').slice(-5);

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">매크로 모니터</h1>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm">
          {error}
        </div>
      )}

      {/* Active Tasks */}
      {activeTasks.length === 0 && !selectedTaskId && (
        <div className="bg-white rounded-2xl shadow-sm p-8 text-center text-gray-400">
          <svg className="w-12 h-12 mx-auto mb-3 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          실행 중인 매크로가 없습니다
        </div>
      )}

      {/* Task Selector */}
      {activeTasks.length > 1 && (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {activeTasks.map((task) => (
            <button
              key={task.task_id}
              onClick={() => setSelectedTaskId(task.task_id)}
              className={`flex-shrink-0 px-4 py-2 rounded-xl text-sm font-semibold transition-colors ${
                selectedTaskId === task.task_id
                  ? 'bg-primary-600 text-white'
                  : 'bg-white text-gray-600 border border-gray-200'
              }`}
            >
              {task.departure} → {task.arrival}
            </button>
          ))}
        </div>
      )}

      {/* Status Display */}
      {selectedTaskId && (
        <div className="space-y-4">
          {/* Success */}
          {successEvent && (
            <div className="bg-green-50 border-2 border-green-400 rounded-2xl p-6 text-center">
              <div className="text-3xl mb-2">🎉</div>
              <div className="text-lg font-bold text-green-700 mb-2">예약 성공!</div>
              {successEvent.reservation && (
                <div className="text-sm text-green-800 space-y-1">
                  <div>{successEvent.reservation.train_name} {successEvent.reservation.train_number}</div>
                  <div>{successEvent.reservation.dep_station} → {successEvent.reservation.arr_station}</div>
                  <div>{successEvent.reservation.total_cost?.toLocaleString()}원</div>
                </div>
              )}
              <div className="text-xs text-green-600 mt-3">
                {successEvent.attempts}회 시도 | {formatElapsed(successEvent.elapsed || 0)}
              </div>
            </div>
          )}

          {/* Running Status */}
          {!successEvent && wsStatus !== 'cancelled' && (
            <div className="bg-white rounded-2xl shadow-sm p-6">
              <div className="text-center">
                {/* Animated indicator */}
                <div className="relative w-20 h-20 mx-auto mb-4">
                  <div className="absolute inset-0 rounded-full border-4 border-primary-100"></div>
                  <div className="absolute inset-0 rounded-full border-4 border-primary-500 border-t-transparent animate-spin"></div>
                  <div className="absolute inset-0 flex items-center justify-center">
                    <svg className="w-8 h-8 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                  </div>
                </div>

                <div className="text-lg font-bold text-gray-800">좌석 검색 중...</div>

                <div className="flex justify-center gap-8 mt-4">
                  <div className="text-center">
                    <div className="text-2xl font-bold font-mono text-primary-600">
                      {latestTick?.attempts || 0}
                    </div>
                    <div className="text-xs text-gray-500">시도</div>
                  </div>
                  <div className="text-center">
                    <div className="text-2xl font-bold font-mono text-primary-600">
                      {formatElapsed(latestTick?.elapsed || 0)}
                    </div>
                    <div className="text-xs text-gray-500">경과</div>
                  </div>
                </div>

                <div className="mt-2">
                  <span className={`inline-block w-2 h-2 rounded-full mr-2 ${
                    wsStatus === 'connected' ? 'bg-green-500' : 'bg-red-500'
                  }`}></span>
                  <span className="text-xs text-gray-500">
                    {wsStatus === 'connected' ? '연결됨' : '연결 끊김'}
                  </span>
                </div>
              </div>

              <button
                onClick={() => {
                  cancel();
                  handleCancel(selectedTaskId);
                }}
                className="w-full mt-6 py-3 bg-red-500 text-white rounded-xl font-semibold hover:bg-red-600 transition-colors"
              >
                매크로 중지
              </button>
            </div>
          )}

          {/* Cancelled */}
          {wsStatus === 'cancelled' && !successEvent && (
            <div className="bg-gray-50 border border-gray-300 rounded-2xl p-6 text-center">
              <div className="text-gray-500 font-semibold">매크로가 중지되었습니다</div>
            </div>
          )}

          {/* Recent Errors */}
          {errorEvents.length > 0 && (
            <div className="bg-white rounded-2xl shadow-sm p-4">
              <h3 className="text-xs font-semibold text-gray-500 mb-2">최근 이벤트</h3>
              <div className="space-y-1 max-h-40 overflow-y-auto">
                {errorEvents.map((e, i) => (
                  <div key={i} className="text-xs text-gray-600 bg-gray-50 rounded-lg p-2">
                    <span className="text-yellow-600 font-semibold">[{e.error_type}]</span> {e.message}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function MacroPage() {
  return (
    <Suspense fallback={<div className="p-4 text-center text-gray-400">로딩 중...</div>}>
      <MacroContent />
    </Suspense>
  );
}
