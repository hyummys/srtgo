from __future__ import annotations

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

router = APIRouter()

# These will be set by main.py
task_manager = None
ws_manager = None


def init(tm, wm):
    global task_manager, ws_manager
    task_manager = tm
    ws_manager = wm


@router.websocket("/ws/macro/{task_id}")
async def macro_websocket(websocket: WebSocket, task_id: str):
    await ws_manager.connect(task_id, websocket)

    try:
        # Send initial status
        status = task_manager.get_status(task_id)
        if status:
            await websocket.send_json(status)

        # Keep alive - read client messages
        while True:
            data = await websocket.receive_text()
            if data == "cancel":
                task_manager.cancel_task(task_id)
            elif data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        pass
    finally:
        await ws_manager.disconnect(task_id, websocket)
