'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import { STATIONS } from '@/lib/constants';
import type { RailType } from '@/lib/types';

function Section({ title, children, defaultOpen = false }: { title: string; children: React.ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between p-4 text-left"
      >
        <span className="font-semibold text-sm">{title}</span>
        <svg className={`w-4 h-4 text-gray-400 transition-transform ${open ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {open && <div className="px-4 pb-4 border-t border-gray-100 pt-3">{children}</div>}
    </div>
  );
}

function StatusBadge({ ok }: { ok: boolean }) {
  return (
    <span className={`text-xs px-2 py-0.5 rounded-full ${ok ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-400'}`}>
      {ok ? '설정됨' : '미설정'}
    </span>
  );
}

export default function SettingsPage() {
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  // Login state
  const [loginRail, setLoginRail] = useState<RailType>('SRT');
  const [loginId, setLoginId] = useState('');
  const [loginPw, setLoginPw] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);
  const [authStatus, setAuthStatus] = useState<any>(null);

  // Telegram state
  const [tgToken, setTgToken] = useState('');
  const [tgChatId, setTgChatId] = useState('');
  const [tgEnabled, setTgEnabled] = useState(false);
  const [tgLoading, setTgLoading] = useState(false);
  const [savedTgToken, setSavedTgToken] = useState('');

  // Card state
  const [cardNumber, setCardNumber] = useState('');
  const [cardPw, setCardPw] = useState('');
  const [cardBirthday, setCardBirthday] = useState('');
  const [cardExpire, setCardExpire] = useState('');
  const [cardEnabled, setCardEnabled] = useState(false);
  const [cardLoading, setCardLoading] = useState(false);
  const [savedCardNumber, setSavedCardNumber] = useState('');
  const [savedCardBirthday, setSavedCardBirthday] = useState(false);
  const [savedCardExpire, setSavedCardExpire] = useState(false);

  // Station state
  const [stationRail, setStationRail] = useState<RailType>('SRT');
  const [availableStations, setAvailableStations] = useState<string[]>([]);
  const [selectedStations, setSelectedStations] = useState<string[]>([]);
  const [stationLoading, setStationLoading] = useState(false);

  // Options state
  const [options, setOptions] = useState<string[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(false);

  useEffect(() => { loadAll(); }, []);
  useEffect(() => { loadStations(); }, [stationRail]);

  async function loadAll() {
    try {
      const [auth, tg, card, opts] = await Promise.all([
        api.auth.status(),
        api.settings.getTelegram(),
        api.settings.getCard(),
        api.settings.getOptions(),
      ]);
      setAuthStatus(auth);
      setSavedTgToken(tg.token || '');
      setTgChatId(tg.chat_id || '');
      setTgEnabled(tg.enabled);
      setSavedCardNumber(card.number_masked || '');
      setSavedCardBirthday(card.has_birthday || false);
      setSavedCardExpire(card.has_expire || false);
      setCardEnabled(card.enabled);
      setOptions(opts.options);
    } catch (e: any) {
      setError(e.message);
    }
    loadStations();
  }

  async function loadStations() {
    try {
      const data = await api.settings.getStations(stationRail);
      setAvailableStations(data.available);
      setSelectedStations(data.selected);
    } catch {}
  }

  function showMessage(msg: string) {
    setMessage(msg);
    setTimeout(() => setMessage(''), 3000);
  }

  async function handleLogin() {
    setLoginLoading(true);
    setError('');
    try {
      await api.auth.login({ rail_type: loginRail, id: loginId, password: loginPw });
      showMessage(`${loginRail} 로그인 성공`);
      setLoginPw('');
      const auth = await api.auth.status();
      setAuthStatus(auth);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoginLoading(false);
    }
  }

  async function handleTelegram() {
    setTgLoading(true);
    setError('');
    try {
      await api.settings.saveTelegram({ token: tgToken, chat_id: tgChatId });
      showMessage('텔레그램 설정 완료 (테스트 메시지 전송됨)');
      setTgEnabled(true);
      setTgToken('');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setTgLoading(false);
    }
  }

  async function handleCard() {
    setCardLoading(true);
    setError('');
    try {
      await api.settings.saveCard({
        number: cardNumber,
        password: cardPw,
        birthday: cardBirthday,
        expire: cardExpire,
      });
      showMessage('카드 설정 완료');
      setCardEnabled(true);
      setCardNumber('');
      setCardPw('');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setCardLoading(false);
    }
  }

  async function handleStations() {
    setStationLoading(true);
    try {
      await api.settings.saveStations(stationRail, selectedStations);
      showMessage('역 설정 저장됨');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setStationLoading(false);
    }
  }

  async function handleOptions() {
    setOptionsLoading(true);
    try {
      await api.settings.saveOptions(options);
      showMessage('옵션 저장됨');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setOptionsLoading(false);
    }
  }

  function toggleStation(station: string) {
    setSelectedStations((prev) =>
      prev.includes(station) ? prev.filter((s) => s !== station) : [...prev, station]
    );
  }

  function toggleOption(opt: string) {
    setOptions((prev) =>
      prev.includes(opt) ? prev.filter((o) => o !== opt) : [...prev, opt]
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">설정</h1>

      {message && (
        <div className="bg-green-50 border border-green-200 rounded-xl p-3 text-green-700 text-sm">
          {message}
        </div>
      )}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm">
          {error}
          <button onClick={() => setError('')} className="float-right text-red-400">X</button>
        </div>
      )}

      {/* Login */}
      <Section title="로그인 설정" defaultOpen={true}>
        <div className="flex gap-2 mb-3">
          {(['SRT', 'KTX'] as const).map((rt) => (
            <button
              key={rt}
              onClick={() => setLoginRail(rt)}
              className={`flex-1 py-1.5 rounded-lg text-sm font-semibold ${
                loginRail === rt ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-600'
              }`}
            >
              {rt} {authStatus?.[rt]?.logged_in ? '(연결됨)' : ''}
            </button>
          ))}
        </div>
        {authStatus?.[loginRail]?.logged_in && authStatus[loginRail].id && (
          <div className="mb-3 p-2.5 bg-green-50 border border-green-200 rounded-xl text-sm text-green-700">
            현재 저장된 아이디: <span className="font-mono font-semibold">{authStatus[loginRail].id}</span>
          </div>
        )}
        <div className="space-y-2">
          <input
            type="text"
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            placeholder={authStatus?.[loginRail]?.id || "아이디 (멤버십번호, 이메일, 전화번호)"}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <input
            type="password"
            value={loginPw}
            onChange={(e) => setLoginPw(e.target.value)}
            placeholder="비밀번호"
            className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <button
            onClick={handleLogin}
            disabled={loginLoading || !loginId || !loginPw}
            className="w-full py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold disabled:opacity-50"
          >
            {loginLoading ? '로그인 중...' : '로그인'}
          </button>
        </div>
      </Section>

      {/* Telegram */}
      <Section title={`텔레그램 설정`}>
        <div className="flex items-center gap-2 mb-3">
          <StatusBadge ok={tgEnabled} />
          {tgEnabled && savedTgToken && (
            <span className="text-xs text-gray-500 font-mono">토큰: {savedTgToken}</span>
          )}
        </div>
        <div className="space-y-2">
          <input
            type="text"
            value={tgToken}
            onChange={(e) => setTgToken(e.target.value)}
            placeholder={savedTgToken || "Bot Token"}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm font-mono focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <input
            type="text"
            value={tgChatId}
            onChange={(e) => setTgChatId(e.target.value)}
            placeholder="Chat ID"
            className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm font-mono focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <button
            onClick={handleTelegram}
            disabled={tgLoading || !tgToken || !tgChatId}
            className="w-full py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold disabled:opacity-50"
          >
            {tgLoading ? '저장 중...' : '저장 및 테스트'}
          </button>
        </div>
      </Section>

      {/* Card */}
      <Section title="카드 설정">
        <div className="flex items-center gap-2 mb-3">
          <StatusBadge ok={cardEnabled} />
          {cardEnabled && savedCardNumber && (
            <span className="text-xs text-gray-500 font-mono">{savedCardNumber}</span>
          )}
        </div>
        {cardEnabled && (savedCardBirthday || savedCardExpire) && (
          <div className="mb-3 p-2.5 bg-gray-50 border border-gray-200 rounded-xl text-xs text-gray-500 flex gap-3">
            {savedCardBirthday && <span>생년월일 설정됨</span>}
            {savedCardExpire && <span>유효기간 설정됨</span>}
          </div>
        )}
        <div className="space-y-2">
          <input
            type="text"
            value={cardNumber}
            onChange={(e) => setCardNumber(e.target.value)}
            placeholder={savedCardNumber || "카드 번호 (16자리)"}
            maxLength={16}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm font-mono focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <input
            type="password"
            value={cardPw}
            onChange={(e) => setCardPw(e.target.value)}
            placeholder="카드 비밀번호 (앞 2자리)"
            maxLength={2}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <input
            type="text"
            value={cardBirthday}
            onChange={(e) => setCardBirthday(e.target.value)}
            placeholder={savedCardBirthday ? "생년월일 (설정됨)" : "생년월일 (YYMMDD) 또는 사업자번호"}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <input
            type="text"
            value={cardExpire}
            onChange={(e) => setCardExpire(e.target.value)}
            placeholder={savedCardExpire ? "유효기간 (설정됨)" : "유효기간 (YYMM)"}
            maxLength={4}
            className="w-full px-3 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <button
            onClick={handleCard}
            disabled={cardLoading || !cardNumber || !cardPw || !cardBirthday || !cardExpire}
            className="w-full py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold disabled:opacity-50"
          >
            {cardLoading ? '저장 중...' : '저장'}
          </button>
        </div>
      </Section>

      {/* Stations */}
      <Section title="즐겨찾기 역">
        <div className="flex gap-2 mb-3">
          {(['SRT', 'KTX'] as const).map((rt) => (
            <button
              key={rt}
              onClick={() => setStationRail(rt)}
              className={`flex-1 py-1.5 rounded-lg text-sm font-semibold ${
                stationRail === rt ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-600'
              }`}
            >
              {rt}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-1.5 mb-3">
          {availableStations.map((station) => {
            const isSelected = selectedStations.includes(station);
            return (
              <button
                key={station}
                onClick={() => toggleStation(station)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  isSelected
                    ? 'bg-primary-100 text-primary-700 border border-primary-300'
                    : 'bg-gray-50 text-gray-500 border border-gray-200'
                }`}
              >
                {station}
              </button>
            );
          })}
        </div>
        <button
          onClick={handleStations}
          disabled={stationLoading}
          className="w-full py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold disabled:opacity-50"
        >
          {stationLoading ? '저장 중...' : '저장'}
        </button>
      </Section>

      {/* Options */}
      <Section title="예매 옵션">
        <div className="space-y-2 mb-3">
          {[
            { key: 'child', label: '어린이 포함' },
            { key: 'senior', label: '경로 포함' },
            { key: 'disability1to3', label: '장애 1~3급 포함' },
            { key: 'disability4to6', label: '장애 4~6급 포함' },
            { key: 'ktx', label: 'KTX 포함' },
          ].map(({ key, label }) => (
            <label key={key} className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={options.includes(key)}
                onChange={() => toggleOption(key)}
                className="w-4 h-4 rounded border-gray-300 text-primary-600"
              />
              <span className="text-sm">{label}</span>
            </label>
          ))}
        </div>
        <button
          onClick={handleOptions}
          disabled={optionsLoading}
          className="w-full py-2.5 bg-primary-600 text-white rounded-xl text-sm font-semibold disabled:opacity-50"
        >
          {optionsLoading ? '저장 중...' : '저장'}
        </button>
      </Section>
    </div>
  );
}
