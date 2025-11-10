import json
import threading
from typing import Optional

import paho.mqtt.client as mqtt

from backend.config import settings
from backend.logger import StructuredLogger

logger = StructuredLogger()


class MqttBridge:
    def __init__(self, ws_manager):
        self.ws_manager = ws_manager
        self.client: Optional[mqtt.Client] = None
        self.is_connected = False
        self._lock = threading.Lock()
    
    def connect(self):
        try:
            self.client = mqtt.Client(
                client_id=settings.mqtt_client_id,
                protocol=mqtt.MQTTv311
            )
            
            self.client.username_pw_set(
                settings.mqtt_username,
                settings.mqtt_password
            )
            
            self.client.on_connect = self._on_connect
            self.client.on_disconnect = self._on_disconnect
            self.client.on_message = self._on_message
            self.client.on_publish = self._on_publish
            
            logger.info(
                component="heimdall.backend.mqtt",
                message="Connecting to MQTT broker",
                metadata={
                    "host": settings.mqtt_host,
                    "port": settings.mqtt_port,
                    "client_id": settings.mqtt_client_id
                }
            )
            
            self.client.connect(
                settings.mqtt_host,
                settings.mqtt_port,
                keepalive=60
            )
            
            self.client.loop_start()
            
        except Exception as e:
            logger.error(
                component="heimdall.backend.mqtt",
                message="Failed to connect to MQTT broker",
                exc_info=e
            )
    
    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self.is_connected = True
            logger.info(
                component="heimdall.backend.mqtt",
                message="Connected to MQTT broker",
                metadata={"return_code": rc}
            )
            
            # Subscribe to all device topics
            topics = [
                "v1/heimdall/tenants/+/devices/+/cmd",
                "v1/heimdall/tenants/+/devices/+/ack",
                "v1/heimdall/tenants/+/devices/+/status",
                "v1/heimdall/telemetry/logs",
                "v1/heimdall/telemetry/status"
            ]
            
            for topic in topics:
                client.subscribe(topic, qos=1)
                logger.info(
                    component="heimdall.backend.mqtt",
                    message="Subscribed to topic",
                    metadata={"topic": topic}
                )
        else:
            self.is_connected = False
            logger.error(
                component="heimdall.backend.mqtt",
                message="Failed to connect to MQTT broker",
                metadata={"return_code": rc}
            )
    
    def _on_disconnect(self, client, userdata, rc):
        self.is_connected = False
        logger.warning(
            component="heimdall.backend.mqtt",
            message="Disconnected from MQTT broker",
            metadata={"return_code": rc}
        )
    
    def _on_message(self, client, userdata, msg):
        try:
            topic = msg.topic
            payload = msg.payload.decode('utf-8')
            
            logger.info(
                component="heimdall.backend.mqtt",
                message="Received MQTT message",
                metadata={
                    "topic": topic,
                    "qos": msg.qos,
                    "payload_size": len(payload)
                }
            )
            
            # Parse payload if JSON
            try:
                data = json.loads(payload)
            except json.JSONDecodeError:
                data = {"raw": payload}
            
            # Forward to WebSocket clients
            if self.ws_manager:
                self.ws_manager.broadcast({
                    "type": "mqtt_message",
                    "topic": topic,
                    "data": data,
                    "timestamp": data.get("timestamp")
                })
            
        except Exception as e:
            logger.error(
                component="heimdall.backend.mqtt",
                message="Error processing MQTT message",
                exc_info=e
            )
    
    def _on_publish(self, client, userdata, mid):
        logger.debug(
            component="heimdall.backend.mqtt",
            message="Message published",
            metadata={"message_id": mid}
        )
    
    def publish(self, topic: str, payload: str, qos: int = 1) -> bool:
        if not self.is_connected or not self.client:
            logger.warning(
                component="heimdall.backend.mqtt",
                message="Cannot publish: not connected",
                metadata={"topic": topic}
            )
            return False
        
        try:
            result = self.client.publish(topic, payload, qos=qos)
            
            logger.info(
                component="heimdall.backend.mqtt",
                message="Published message",
                metadata={
                    "topic": topic,
                    "qos": qos,
                    "message_id": result.mid
                }
            )
            
            return result.rc == mqtt.MQTT_ERR_SUCCESS
        except Exception as e:
            logger.error(
                component="heimdall.backend.mqtt",
                message="Failed to publish message",
                metadata={"topic": topic},
                exc_info=e
            )
            return False
    
    def disconnect(self):
        if self.client:
            self.client.loop_stop()
            self.client.disconnect()
            logger.info(
                component="heimdall.backend.mqtt",
                message="MQTT client disconnected"
            )


