import json
import logging
import time
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Optional

import paho.mqtt.client as mqtt
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from backend.config import settings
from backend.logger import StructuredLogger
from backend.mqtt_bridge import MqttBridge
from backend.websocket_manager import WebSocketManager

# Configure structured logging
logger = StructuredLogger()

# Global MQTT bridge
mqtt_bridge: Optional[MqttBridge] = None
ws_manager: Optional[WebSocketManager] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global mqtt_bridge, ws_manager
    
    logger.info(
        component="heimdall.backend.startup",
        message="Starting Heimdall backend services"
    )
    
    ws_manager = WebSocketManager()
    mqtt_bridge = MqttBridge(ws_manager)
    mqtt_bridge.connect()
    
    yield
    
    logger.info(
        component="heimdall.backend.shutdown",
        message="Shutting down Heimdall backend services"
    )
    
    if mqtt_bridge:
        mqtt_bridge.disconnect()


app = FastAPI(
    title="Heimdall MDM API",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    trace_id = str(uuid.uuid4())
    start_time = time.time()
    
    logger.info(
        component="heimdall.backend.http",
        message=f"{request.method} {request.url.path}",
        metadata={
            "method": request.method,
            "path": str(request.url.path),
            "query_params": str(request.query_params),
            "client_host": request.client.host if request.client else None
        },
        trace_id=trace_id
    )
    
    response = await call_next(request)
    
    process_time = time.time() - start_time
    
    logger.info(
        component="heimdall.backend.http",
        message=f"{request.method} {request.url.path} completed",
        metadata={
            "method": request.method,
            "path": str(request.url.path),
            "status_code": response.status_code,
            "process_time": f"{process_time:.3f}s"
        },
        trace_id=trace_id
    )
    
    return response


@app.get("/")
async def root():
    return {
        "service": "Heimdall MDM API",
        "version": "1.0.0",
        "status": "running"
    }


@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "timestamp": datetime.now(timezone.utc).isoformat()
    }


@app.websocket("/ws/logs")
async def websocket_logs(websocket: WebSocket):
    await ws_manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            logger.debug(
                component="heimdall.backend.websocket",
                message="Received WebSocket message",
                metadata={"message": data}
            )
    except WebSocketDisconnect:
        ws_manager.disconnect(websocket)
        logger.info(
            component="heimdall.backend.websocket",
            message="WebSocket client disconnected"
        )


class CommandRequest(BaseModel):
    tenant_id: str
    device_id: str
    command: str
    payload: dict = {}


@app.post("/api/v1/commands")
async def send_command(cmd: CommandRequest):
    trace_id = str(uuid.uuid4())
    
    logger.info(
        component="heimdall.backend.commands",
        message="Received command request",
        metadata={
            "tenant_id": cmd.tenant_id,
            "device_id": cmd.device_id,
            "command": cmd.command
        },
        trace_id=trace_id
    )
    
    if not mqtt_bridge:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"error": "MQTT bridge not initialized"}
        )
    
    topic = f"v1/heimdall/tenants/{cmd.tenant_id}/devices/{cmd.device_id}/cmd"
    message = {
        "cmd_id": trace_id,
        "command": cmd.command,
        "payload": cmd.payload,
        "timestamp": datetime.now(timezone.utc).isoformat()
    }
    
    success = mqtt_bridge.publish(topic, json.dumps(message))
    
    if success:
        return {
            "status": "sent",
            "cmd_id": trace_id,
            "topic": topic
        }
    else:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"error": "Failed to publish command"}
        )


@app.get("/api/v1/logs")
async def get_logs(
    device_id: Optional[str] = None,
    tenant_id: Optional[str] = None,
    level: Optional[str] = None,
    component: Optional[str] = None,
    since: Optional[str] = None,
    limit: int = 100
):
    logger.info(
        component="heimdall.backend.logs",
        message="Log query requested",
        metadata={
            "device_id": device_id,
            "tenant_id": tenant_id,
            "level": level,
            "component": component,
            "since": since,
            "limit": limit
        }
    )
    
    # TODO: Implement log retrieval from database
    return {
        "logs": [],
        "count": 0,
        "filters": {
            "device_id": device_id,
            "tenant_id": tenant_id,
            "level": level,
            "component": component,
            "since": since
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)


