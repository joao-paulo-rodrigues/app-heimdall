import json
import logging
import sys
from datetime import datetime, timezone
from typing import Any, Dict, Optional


class StructuredLogger:
    def __init__(self):
        self.logger = logging.getLogger("heimdall")
        self.logger.setLevel(logging.DEBUG)
        
        if not self.logger.handlers:
            handler = logging.StreamHandler(sys.stdout)
            handler.setFormatter(StructuredFormatter())
            self.logger.addHandler(handler)
    
    def _log(
        self,
        level: str,
        component: str,
        message: str,
        metadata: Optional[Dict[str, Any]] = None,
        trace_id: Optional[str] = None
    ):
        log_entry = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": level,
            "component": component,
            "message": message
        }
        
        if trace_id:
            log_entry["trace_id"] = trace_id
        
        if metadata:
            log_entry["metadata"] = metadata
        
        log_line = json.dumps(log_entry, ensure_ascii=False)
        
        log_level = getattr(logging, level.upper(), logging.INFO)
        self.logger.log(log_level, log_line)
    
    def debug(self, component: str, message: str, metadata: Optional[Dict[str, Any]] = None, trace_id: Optional[str] = None):
        self._log("DEBUG", component, message, metadata, trace_id)
    
    def info(self, component: str, message: str, metadata: Optional[Dict[str, Any]] = None, trace_id: Optional[str] = None):
        self._log("INFO", component, message, metadata, trace_id)
    
    def warning(self, component: str, message: str, metadata: Optional[Dict[str, Any]] = None, trace_id: Optional[str] = None):
        self._log("WARNING", component, message, metadata, trace_id)
    
    def error(self, component: str, message: str, metadata: Optional[Dict[str, Any]] = None, trace_id: Optional[str] = None, exc_info=None):
        metadata = metadata or {}
        if exc_info:
            import traceback
            metadata["exception"] = str(exc_info)
            metadata["traceback"] = traceback.format_exc()
        self._log("ERROR", component, message, metadata, trace_id)
    
    def critical(self, component: str, message: str, metadata: Optional[Dict[str, Any]] = None, trace_id: Optional[str] = None, exc_info=None):
        metadata = metadata or {}
        if exc_info:
            import traceback
            metadata["exception"] = str(exc_info)
            metadata["traceback"] = traceback.format_exc()
        self._log("CRITICAL", component, message, metadata, trace_id)


class StructuredFormatter(logging.Formatter):
    def format(self, record):
        return record.getMessage()


