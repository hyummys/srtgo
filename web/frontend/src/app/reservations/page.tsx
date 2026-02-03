'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import { formatTime, formatDate } from '@/lib/constants';
import type { RailType, ReservationData } from '@/lib/types';

export default function ReservationsPage() {
  const [railType, setRailType] = useState<RailType>('SRT');
  const [reservations, setReservations] = useState<ReservationData[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  useEffect(() => {
    loadReservations();
  }, [railType]);

  async function loadReservations() {
    setLoading(true);
    setError('');
    try {
      const result = await api.reservations.list(railType);
      setReservations(result.reservations);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function handlePay(reservationNumber: string) {
    if (!confirm('결제하시겠습니까?')) return;
    setActionLoading(reservationNumber);
    try {
      await api.reservations.pay(railType, reservationNumber);
      loadReservations();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setActionLoading(null);
    }
  }

  async function handleCancel(reservationNumber: string) {
    if (!confirm('정말 취소하시겠습니까?')) return;
    setActionLoading(reservationNumber);
    try {
      await api.reservations.cancel(railType, reservationNumber);
      loadReservations();
    } catch (e: any) {
      setError(e.message);
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">예약 확인</h1>

      {/* Rail Type Toggle */}
      <div className="bg-white rounded-2xl shadow-sm p-4">
        <div className="flex bg-gray-100 rounded-xl p-1">
          {(['SRT', 'KTX'] as const).map((rt) => (
            <button
              key={rt}
              onClick={() => setRailType(rt)}
              className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-colors ${
                railType === rt ? 'bg-white shadow-sm text-primary-700' : 'text-gray-500'
              }`}
            >
              {rt}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm">
          {error}
        </div>
      )}

      {loading ? (
        <div className="bg-white rounded-2xl shadow-sm p-8 text-center text-gray-400">
          로딩 중...
        </div>
      ) : reservations.length === 0 ? (
        <div className="bg-white rounded-2xl shadow-sm p-8 text-center text-gray-400">
          예약 내역이 없습니다
        </div>
      ) : (
        <div className="space-y-3">
          {reservations.map((rsv, idx) => (
            <div key={idx} className="bg-white rounded-2xl shadow-sm p-4">
              <div className="flex justify-between items-start">
                <div>
                  <span className="font-bold text-sm">
                    [{rsv.train_name} {rsv.train_number}]
                  </span>
                  {rsv.paid && (
                    <span className="ml-2 text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">
                      결제완료
                    </span>
                  )}
                  {rsv.is_waiting && (
                    <span className="ml-2 text-xs px-2 py-0.5 rounded-full bg-yellow-100 text-yellow-700">
                      예약대기
                    </span>
                  )}
                  {!rsv.paid && !rsv.is_waiting && (
                    <span className="ml-2 text-xs px-2 py-0.5 rounded-full bg-orange-100 text-orange-700">
                      미결제
                    </span>
                  )}
                </div>
                <div className="text-sm font-semibold text-primary-700">
                  {rsv.total_cost?.toLocaleString()}원
                </div>
              </div>

              <div className="text-sm mt-2">
                <span className="font-mono">
                  {formatDate(rsv.dep_date)} {formatTime(rsv.dep_time)}
                </span>
                <span className="mx-1 text-gray-400">→</span>
                <span className="font-mono">{formatTime(rsv.arr_time)}</span>
              </div>
              <div className="text-xs text-gray-500 mt-0.5">
                {rsv.dep_station} → {rsv.arr_station} ({rsv.seat_count}석)
              </div>

              {rsv.payment_date && rsv.payment_time && !rsv.paid && !rsv.is_waiting && (
                <div className="text-xs text-orange-600 mt-1">
                  결제기한: {formatDate(rsv.payment_date)} {formatTime(rsv.payment_time)}
                </div>
              )}

              {/* Ticket details */}
              {rsv.tickets && rsv.tickets.length > 0 && (
                <div className="mt-2 space-y-1">
                  {rsv.tickets.map((t, ti) => (
                    <div key={ti} className="text-xs text-gray-600 bg-gray-50 rounded-lg px-3 py-1.5">
                      {t.car}호차 {t.seat} ({t.seat_type}) {t.passenger_type}
                      {t.price > 0 && ` ${t.price.toLocaleString()}원`}
                    </div>
                  ))}
                </div>
              )}

              {/* Actions */}
              <div className="flex gap-2 mt-3">
                {!rsv.paid && !rsv.is_waiting && (
                  <button
                    onClick={() => handlePay(rsv.reservation_number)}
                    disabled={actionLoading === rsv.reservation_number}
                    className="flex-1 py-2 bg-green-500 text-white rounded-xl text-sm font-semibold hover:bg-green-600 disabled:opacity-50"
                  >
                    {actionLoading === rsv.reservation_number ? '처리중...' : '결제'}
                  </button>
                )}
                <button
                  onClick={() => handleCancel(rsv.reservation_number)}
                  disabled={actionLoading === rsv.reservation_number}
                  className={`${!rsv.paid && !rsv.is_waiting ? '' : 'flex-1'} py-2 px-4 bg-red-50 text-red-600 rounded-xl text-sm font-semibold hover:bg-red-100 disabled:opacity-50`}
                >
                  {actionLoading === rsv.reservation_number ? '처리중...' : rsv.paid ? '환불' : '취소'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <button
        onClick={loadReservations}
        className="w-full py-3 bg-white border border-gray-300 text-gray-700 rounded-2xl font-semibold hover:bg-gray-50"
      >
        새로고침
      </button>
    </div>
  );
}
