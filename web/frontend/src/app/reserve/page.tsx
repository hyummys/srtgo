'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { STATIONS, SEAT_TYPES, PASSENGER_TYPES, formatTime, formatDate } from '@/lib/constants';
import type { RailType, TrainResult, Passengers, SeatType } from '@/lib/types';

export default function ReservePage() {
  const router = useRouter();
  const [railType, setRailType] = useState<RailType>('SRT');
  const [departure, setDeparture] = useState('');
  const [arrival, setArrival] = useState('');
  const [date, setDate] = useState('');
  const [time, setTime] = useState('120000');
  const [passengers, setPassengers] = useState<Passengers>({
    adult: 1, child: 0, senior: 0, disability1to3: 0, disability4to6: 0,
  });

  const [trains, setTrains] = useState<TrainResult[]>([]);
  const [selectedTrains, setSelectedTrains] = useState<number[]>([]);
  const [seatType, setSeatType] = useState<SeatType>('GENERAL_FIRST');
  const [autoPay, setAutoPay] = useState(false);

  const [loading, setLoading] = useState(false);
  const [macroLoading, setMacroLoading] = useState(false);
  const [error, setError] = useState('');
  const [step, setStep] = useState<'search' | 'select'>('search');
  const [stations, setStations] = useState<string[]>([]);

  useEffect(() => {
    loadDefaults();
  }, [railType]);

  async function loadDefaults() {
    try {
      const [stationData, defaults] = await Promise.all([
        api.settings.getStations(railType),
        api.settings.getDefaults(railType),
      ]);
      setStations(stationData.selected.length > 0 ? stationData.selected : stationData.available);
      setDeparture(defaults.departure || (railType === 'SRT' ? '수서' : '서울'));
      setArrival(defaults.arrival || '동대구');
      if (defaults.date) setDate(defaults.date);
      if (defaults.time) setTime(defaults.time);
      setPassengers({
        adult: defaults.adult || 1,
        child: defaults.child || 0,
        senior: defaults.senior || 0,
        disability1to3: defaults.disability1to3 || 0,
        disability4to6: defaults.disability4to6 || 0,
      });
    } catch {
      setStations(STATIONS[railType]);
    }
  }

  async function handleSearch() {
    setError('');
    setLoading(true);
    try {
      const searchDate = date || new Date().toISOString().slice(0, 10).replace(/-/g, '');
      const result = await api.trains.search({
        rail_type: railType,
        departure,
        arrival,
        date: searchDate,
        time,
        passengers,
      });
      setTrains(result.trains);
      setSelectedTrains([]);
      setStep('select');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  function toggleTrain(index: number) {
    setSelectedTrains((prev) =>
      prev.includes(index) ? prev.filter((i) => i !== index) : [...prev, index]
    );
  }

  async function handleMacroStart() {
    if (selectedTrains.length === 0) {
      setError('열차를 선택해주세요');
      return;
    }
    setMacroLoading(true);
    setError('');
    try {
      const searchDate = date || new Date().toISOString().slice(0, 10).replace(/-/g, '');
      const result = await api.macro.start({
        rail_type: railType,
        departure,
        arrival,
        date: searchDate,
        time,
        passengers,
        train_indices: selectedTrains,
        seat_type: seatType,
        auto_pay: autoPay,
      });
      router.push(`/macro?task=${result.task_id}`);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setMacroLoading(false);
    }
  }

  // Generate date options (today + 30 days)
  const dateOptions: { value: string; label: string }[] = [];
  for (let i = 0; i <= 30; i++) {
    const d = new Date();
    d.setDate(d.getDate() + i);
    const val = d.toISOString().slice(0, 10).replace(/-/g, '');
    const label = `${d.getMonth() + 1}/${d.getDate()} (${['일', '월', '화', '수', '목', '금', '토'][d.getDay()]})`;
    dateOptions.push({ value: val, label });
  }

  const timeOptions: { value: string; label: string }[] = [];
  for (let h = 0; h < 24; h++) {
    const val = `${String(h).padStart(2, '0')}0000`;
    timeOptions.push({ value: val, label: `${String(h).padStart(2, '0')}:00` });
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">열차 예매</h1>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm">
          {error}
          <button onClick={() => setError('')} className="float-right text-red-400">X</button>
        </div>
      )}

      {step === 'search' && (
        <div className="space-y-4">
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

          {/* Stations */}
          <div className="bg-white rounded-2xl shadow-sm p-4 space-y-3">
            <div>
              <label className="block text-xs font-semibold text-gray-500 mb-1">출발역</label>
              <select
                value={departure}
                onChange={(e) => setDeparture(e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                {stations.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div className="flex justify-center">
              <button
                onClick={() => { const tmp = departure; setDeparture(arrival); setArrival(tmp); }}
                className="p-2 bg-gray-100 rounded-full hover:bg-gray-200"
              >
                <svg className="w-4 h-4 text-gray-500 rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                </svg>
              </button>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-500 mb-1">도착역</label>
              <select
                value={arrival}
                onChange={(e) => setArrival(e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                {stations.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
          </div>

          {/* Date & Time */}
          <div className="bg-white rounded-2xl shadow-sm p-4 grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-500 mb-1">날짜</label>
              <select
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                {dateOptions.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-500 mb-1">시간</label>
              <select
                value={time}
                onChange={(e) => setTime(e.target.value)}
                className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                {timeOptions.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
          </div>

          {/* Passengers */}
          <div className="bg-white rounded-2xl shadow-sm p-4">
            <h3 className="text-xs font-semibold text-gray-500 mb-3">인원</h3>
            <div className="space-y-2">
              {PASSENGER_TYPES.map(({ key, label }) => (
                <div key={key} className="flex items-center justify-between">
                  <span className="text-sm">{label}</span>
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => setPassengers((p) => ({ ...p, [key]: Math.max(0, (p as any)[key] - 1) }))}
                      className="w-8 h-8 rounded-full bg-gray-100 text-gray-600 font-bold flex items-center justify-center"
                    >
                      -
                    </button>
                    <span className="w-6 text-center font-semibold text-sm">{(passengers as any)[key]}</span>
                    <button
                      onClick={() => setPassengers((p) => ({ ...p, [key]: Math.min(9, (p as any)[key] + 1) }))}
                      className="w-8 h-8 rounded-full bg-primary-100 text-primary-700 font-bold flex items-center justify-center"
                    >
                      +
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <button
            onClick={handleSearch}
            disabled={loading}
            className="w-full py-3.5 bg-primary-600 text-white rounded-2xl font-semibold hover:bg-primary-700 transition-colors disabled:opacity-50"
          >
            {loading ? '조회 중...' : '열차 조회'}
          </button>
        </div>
      )}

      {step === 'select' && (
        <div className="space-y-4">
          <button
            onClick={() => setStep('search')}
            className="text-sm text-primary-600 flex items-center gap-1"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            검색 조건 변경
          </button>

          {/* Train List */}
          <div className="space-y-2">
            {trains.length === 0 ? (
              <div className="bg-white rounded-2xl shadow-sm p-8 text-center text-gray-400">
                조회 결과가 없습니다
              </div>
            ) : (
              trains.map((train, idx) => {
                const isSelected = selectedTrains.includes(idx);
                const hasSeats = train.general_available || train.special_available;
                return (
                  <button
                    key={idx}
                    onClick={() => toggleTrain(idx)}
                    className={`w-full text-left rounded-2xl shadow-sm p-4 transition-all ${
                      isSelected
                        ? 'bg-primary-50 border-2 border-primary-500'
                        : 'bg-white border-2 border-transparent'
                    }`}
                  >
                    <div className="flex justify-between items-start">
                      <div>
                        <span className="font-bold text-sm">
                          [{train.train_name} {train.train_number}]
                        </span>
                        <span className="text-xs text-gray-500 ml-2">{train.duration_minutes}분</span>
                      </div>
                      <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center ${
                        isSelected ? 'bg-primary-600 border-primary-600' : 'border-gray-300'
                      }`}>
                        {isSelected && (
                          <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                          </svg>
                        )}
                      </div>
                    </div>
                    <div className="text-sm mt-1">
                      <span className="font-mono">
                        {formatDate(train.dep_date)} {formatTime(train.dep_time)}
                      </span>
                      <span className="mx-2 text-gray-400">→</span>
                      <span className="font-mono">{formatTime(train.arr_time)}</span>
                    </div>
                    <div className="text-xs text-gray-500 mt-0.5">
                      {train.dep_station} → {train.arr_station}
                    </div>
                    <div className="flex gap-2 mt-2">
                      <span className={`text-xs px-2 py-0.5 rounded-full ${
                        train.general_available ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                      }`}>
                        일반 {train.general_available ? '가능' : '매진'}
                      </span>
                      <span className={`text-xs px-2 py-0.5 rounded-full ${
                        train.special_available ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                      }`}>
                        특실 {train.special_available ? '가능' : '매진'}
                      </span>
                      {train.standby_available && (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-yellow-100 text-yellow-700">
                          대기 가능
                        </span>
                      )}
                    </div>
                  </button>
                );
              })
            )}
          </div>

          {trains.length > 0 && (
            <>
              {/* Seat Type & Options */}
              <div className="bg-white rounded-2xl shadow-sm p-4 space-y-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">좌석 유형</label>
                  <select
                    value={seatType}
                    onChange={(e) => setSeatType(e.target.value as SeatType)}
                    className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                  >
                    {SEAT_TYPES.map((st) => (
                      <option key={st.value} value={st.value}>{st.label}</option>
                    ))}
                  </select>
                </div>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={autoPay}
                    onChange={(e) => setAutoPay(e.target.checked)}
                    className="w-4 h-4 rounded border-gray-300 text-primary-600"
                  />
                  <span className="text-sm">자동 결제</span>
                </label>
              </div>

              {/* Action Buttons */}
              <button
                onClick={handleMacroStart}
                disabled={macroLoading || selectedTrains.length === 0}
                className="w-full py-3.5 bg-primary-600 text-white rounded-2xl font-semibold hover:bg-primary-700 transition-colors disabled:opacity-50"
              >
                {macroLoading ? '시작 중...' : `매크로 시작 (${selectedTrains.length}개 열차)`}
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
