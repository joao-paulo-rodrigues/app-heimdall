from typing import List
from fastapi import WebSocket

from backend.logger import StructuredLogger

logger = StructuredLogger()


class WebSocketManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []
    
    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
        logger.info(
            component="heimdall.backend.websocket",
            message="WebSocket client connected",
            metadata={"total_connections": len(self.active_connections)}
        )
    
    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
        logger.info(
            component="heimdall.backend.websocket",
            message="WebSocket client disconnected",
            metadata={"total_connections": len(self.active_connections)}
        )
    
    async def send_personal_message(self, message: dict, websocket: WebSocket):
        try:
            import json
            await websocket.send_text(json.dumps(message))
        except Exception as e:
            logger.error(
                component="heimdall.backend.websocket",
                message="Failed to send personal message",
                exc_info=e
            )
            self.disconnect(websocket)
    
    async def broadcast(self, message: dict):
        import json
        message_str = json.dumps(message)
        disconnected = []
        
        for connection in self.active_connections:
            try:
                await connection.send_text(message_str)
            except Exception as e:
                logger.warning(
                    component="heimdall.backend.websocket",
                    message="Failed to send broadcast message",
                    metadata={"error": str(e)}
                )
                disconnected.append(connection)
        
        for conn in disconnected:
            self.disconnect(conn)


