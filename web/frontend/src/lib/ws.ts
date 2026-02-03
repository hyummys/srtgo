'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import type { MacroEvent } from './types';

const WS_BASE = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000').replace('http', 'ws');

export function useMacroWebSocket(taskId: string | null) {
  const [events, setEvents] = useState<MacroEvent[]>([]);
  const [latestEvent, setLatestEvent] = useState<MacroEvent | null>(null);
  const [wsStatus, setWsStatus] = useState<string>('disconnected');
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!taskId) return;

    const wsUrl = `${WS_BASE}/ws/macro/${taskId}`;
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => setWsStatus('connected');

    ws.onmessage = (e) => {
      try {
        const event: MacroEvent = JSON.parse(e.data);
        setLatestEvent(event);
        setEvents((prev) => [...prev.slice(-200), event]);
        if (['success', 'cancelled', 'failed'].includes(event.type)) {
          setWsStatus(event.type);
        }
      } catch {
        // ignore parse errors
      }
    };

    ws.onerror = () => setWsStatus('error');
    ws.onclose = () => setWsStatus('disconnected');

    // Ping every 30s to keep alive
    const pingInterval = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send('ping');
      }
    }, 30000);

    return () => {
      clearInterval(pingInterval);
      ws.close();
    };
  }, [taskId]);

  const cancel = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send('cancel');
    }
  }, []);

  return { events, latestEvent, wsStatus, cancel };
}
