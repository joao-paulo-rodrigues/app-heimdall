# Quick Start - Heimdall Android

## Pré-requisitos

1. Android Studio instalado
2. Emulador Android configurado (API 28+)
3. ADB configurado no PATH

## Passos Rápidos

### Opção 1: Script Automatizado (Mais Fácil)

```bash
cd android
./run.sh
```

Este script faz tudo automaticamente:
- Verifica se há emulador rodando
- Se não houver, inicia um automaticamente
- Compila o projeto
- Instala no emulador
- Abre o app

### Opção 2: Manual

#### 1. Iniciar o Emulador

```bash
# Listar emuladores disponíveis
emulator -list-avds

# Iniciar emulador
emulator -avd <nome_do_avd>
```

Ou via Android Studio: Tools > Device Manager > Play

#### 2. Compilar e Instalar

```bash
cd android
./build.sh
```

Ou manualmente:

```bash
cd android
./gradlew clean assembleDebug
./gradlew installDebug
```

### 3. Verificar Logs

```bash
# Ver logs em tempo real
adb logcat | grep Heimdall

# Ver logs do arquivo local
adb shell run-as com.heimdall.device cat files/heimdall_logs/heimdall.log
```

### 4. Verificar Conexão MQTT

Os logs devem mostrar:
- Tentativa de conexão ao broker MQTT
- Conexão estabelecida
- Inscrição no tópico de comandos
- Publicação de status "online"

## Estrutura de Tópicos MQTT

- **Comandos recebidos**: `v1/heimdall/tenants/{tenant_id}/devices/{device_id}/cmd`
- **Status publicado**: `v1/heimdall/tenants/{tenant_id}/devices/{device_id}/status`
- **Logs publicados**: `v1/heimdall/telemetry/logs`

## Configuração MQTT

As credenciais estão em: `app/src/main/java/com/heimdall/device/config/MqttConfig.kt`

- Host: 177.87.122.5
- Porta: 1883
- Usuário: mosquitto_broker_user_ue

## Troubleshooting

### Emulador não conecta à internet

```bash
# Verificar conectividade
adb shell ping -c 3 177.87.122.5
```

### App não inicia

```bash
# Verificar se está instalado
adb shell pm list packages | grep heimdall

# Reinstalar
adb uninstall com.heimdall.device
./gradlew installDebug
```

### Logs não aparecem

- Verifique se o Logger foi inicializado
- Verifique permissões de armazenamento
- Verifique o Logcat completo: `adb logcat`

