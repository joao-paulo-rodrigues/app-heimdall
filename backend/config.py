from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # MQTT Configuration
    mqtt_host: str = "177.87.122.5"
    mqtt_port: int = 1883
    mqtt_username: str = "mosquitto_broker_user_ue"
    mqtt_password: str = "tiue@Mosquitto2025#"
    mqtt_client_id: str = "heimdall_backend"
    
    # Database Configuration
    database_url: str = "postgresql://heimdall:heimdall@localhost:5432/heimdall"
    
    # Redis Configuration
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0
    
    # API Configuration
    api_host: str = "0.0.0.0"
    api_port: int = 8000
    
    class Config:
        env_file = ".env"
        case_sensitive = False


settings = Settings()


