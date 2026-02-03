from __future__ import annotations

import json
from fastapi import WebSocket


class WSManager:
    def __init__(self):
        self._connections: dict[str, list[WebSocket]] = {}

    async def connect(self, task_id: str, ws: WebSocket):
        await ws.accept()
        self._connections.setdefault(task_id, []).append(ws)

    async def disconnect(self, task_id: str, ws: WebSocket):
        if task_id in self._connections:
            try:
                self._connections[task_id].remove(ws)
            except ValueError:
                pass
            if not self._connections[task_id]:
                del self._connections[task_id]

    async def broadcast(self, task_id: str, data: dict):
        if task_id not in self._connections:
            return
        message = json.dumps(data, ensure_ascii=False)
        dead = []
        for ws in self._connections[task_id]:
            try:
                await ws.send_text(message)
            except Exception:
                dead.append(ws)
        for ws in dead:
            try:
                self._connections[task_id].remove(ws)
            except ValueError:
                pass

    def cleanup(self, task_id: str):
        self._connections.pop(task_id, None)
