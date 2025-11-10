# Sistema de Autoinstalação e Autoatualização

Sistema simples para instalação automática de apps via URL, integrado com o sistema de comandos MQTT.

## Arquitetura

### Componentes Principais

1. **UpdateManager**: Coordenador principal que orquestra todo o processo
2. **DownloadManager**: Gerencia download de APKs de qualquer URL
3. **InstallManager**: Gerencia instalação/desinstalação usando Device Owner

### Fluxo de Instalação

```
1. Comando MQTT recebido (install_app) com URL do APK
2. UpdateManager verifica Device Owner
3. DownloadManager baixa APK da URL fornecida
4. InstallManager instala APK silenciosamente
5. Verificação de instalação bem-sucedida
6. Resultado enviado via ACK MQTT
```

## Como Funciona

O sistema é **simples e direto**: você fornece uma URL para um arquivo APK e o sistema baixa e instala automaticamente. Não há necessidade de manifest, verificação de versão ou estrutura complexa de pastas.

### Requisitos

- **URL do APK**: Qualquer URL HTTP/HTTPS que aponte para um arquivo APK
- **Device Owner**: O app deve ter privilégios de Device Owner para instalação silenciosa

## Comandos MQTT

### install_app

Baixa e instala um APK de uma URL.

**Parâmetros**:
- `url` (obrigatório): URL do arquivo APK para download (ex: `https://example.com/app-debug.apk`)
- `package_name` (opcional): Nome do pacote esperado (para validação pós-instalação)
- `force` (opcional): Força reinstalação mesmo se já estiver instalado (padrão: `false`)

**Exemplo**:
```json
{
  "command_id": "cmd-123",
  "command": "install_app",
  "params": {
    "url": "https://example.com/app-debug.apk",
    "package_name": "com.uebrasil.panicbuttonapp",
    "force": false
  },
  "trace_id": "trace-456"
}
```

**Resposta de Sucesso**:
```json
{
  "command_id": "cmd-123",
  "command": "install_app",
  "status": "SUCCESS",
  "message": "App installed/updated successfully",
  "data": {
    "package_name": "com.uebrasil.panicbuttonapp",
    "version_name": "1.0.0",
    "version_code": 1,
    "download_size": 5242880,
    "download_time_ms": 2500,
    "install_time_ms": 3000
  }
}
```

### update_app

Alias para `install_app`. Funciona da mesma forma - baixa e instala o APK da URL fornecida.

**Parâmetros**: Mesmos de `install_app`

### uninstall_app

Desinstala um app.

**Parâmetros**:
- `package_name` (obrigatório): Nome do pacote

**Exemplo**:
```json
{
  "command_id": "cmd-125",
  "command": "uninstall_app",
  "params": {
    "package_name": "com.uebrasil.panicbuttonapp"
  }
}
```

### check_updates

Verifica a versão instalada de um app.

**Parâmetros**:
- `package_name` (obrigatório): Nome do pacote a verificar

**Exemplo**:
```json
{
  "command_id": "cmd-126",
  "command": "check_updates",
  "params": {
    "package_name": "com.uebrasil.panicbuttonapp"
  }
}
```

**Resposta**:
```json
{
  "command_id": "cmd-126",
  "command": "check_updates",
  "status": "SUCCESS",
  "message": "Version check completed",
  "data": {
    "package_name": "com.uebrasil.panicbuttonapp",
    "installed_version_name": "1.0.0",
    "installed_version_code": 1,
    "installed_at": 1234567890
  }
}
```

## Recursos

### Download Robusto

- Timeout configurável (padrão: 2 minutos)
- Retry automático com backoff exponencial (até 3 tentativas)
- Progress tracking via logs
- Suporta qualquer URL HTTP/HTTPS

### Instalação Silenciosa

- Usa Device Owner para instalação sem interação do usuário
- Validação de privilégios antes de instalar
- Verificação pós-instalação
- Limpeza automática de arquivos temporários

### Logs Estruturados

Todos os eventos são registrados com logs estruturados JSON:

```json
{
  "timestamp": "2025-01-15T10:30:00.000Z",
  "level": "INFO",
  "component": "heimdall.device.update.manager",
  "device_id": "A1B2C3",
  "tenant_id": "UEBRASIL",
  "trace_id": "trace-456",
  "message": "App installed successfully",
  "metadata": {
    "url": "https://example.com/app-debug.apk",
    "package_name": "com.uebrasil.panicbuttonapp",
    "version_name": "1.0.0",
    "version_code": 1,
    "total_time_ms": 5500
  }
}
```

## Timeouts e Retries

### Configurações Padrão

- **Download Timeout**: 120 segundos
- **Install Timeout**: 60 segundos
- **Connection Timeout**: 30 segundos
- **Max Retry Attempts**: 3
- **Initial Retry Delay**: 2 segundos
- **Max Retry Delay**: 30 segundos

### Backoff Exponencial

As tentativas de retry usam backoff exponencial:
- Tentativa 1: 2s
- Tentativa 2: 4s
- Tentativa 3: 8s

## Limpeza Automática

O sistema limpa automaticamente arquivos de download antigos (mais de 24 horas) para economizar espaço.

## Requisitos

- **Device Owner**: O app deve ser configurado como Device Owner para instalação silenciosa
- **Permissões**: `INSTALL_PACKAGES`, `DELETE_PACKAGES`, `QUERY_ALL_PACKAGES`
- **Conectividade**: Acesso à internet para download do CloudFront

## Segurança

- Verificação de checksum SHA-256 (quando disponível no manifest)
- Validação de Device Owner antes de instalar/desinstalar
- Logs de todas as operações para auditoria
- Validação de integridade do APK antes da instalação

## Troubleshooting

### Erro: "Device Owner privileges required"

O app não está configurado como Device Owner. Execute:
```bash
adb shell dpm set-device-owner com.heimdall.device/.receiver.HeimdallDeviceAdminReceiver
```

### Erro: "Download failed"

- Verifique conectividade com internet
- Verifique se a URL do CloudFront está correta
- Verifique se o arquivo APK existe no CloudFront
- Verifique logs para detalhes do erro HTTP

### Erro: "Installation failed"

- Verifique se há espaço suficiente no dispositivo
- Verifique se o APK é compatível com a versão do Android
- Verifique logs para detalhes do erro de instalação

## Exemplos de Uso

### Instalar App de uma URL

```bash
# Via MQTT
mosquitto_pub -h 177.87.122.5 -p 1883 \
  -u mosquitto_broker_user_ue \
  -P 'tiue@Mosquitto2025#' \
  -t "v1/heimdall/tenants/UEBRASIL/devices/{device_id}/cmd" \
  -m '{
    "command_id": "install-panic-001",
    "command": "install_app",
    "params": {
      "url": "https://example.com/app-debug.apk",
      "package_name": "com.uebrasil.panicbuttonapp"
    },
    "trace_id": "trace-001"
  }'
```

### Instalar sem especificar package_name

```json
{
  "command_id": "install-002",
  "command": "install_app",
  "params": {
    "url": "https://example.com/app-debug.apk"
  }
}
```

### Verificar versão instalada

```json
{
  "command_id": "check-version-003",
  "command": "check_updates",
  "params": {
    "package_name": "com.uebrasil.panicbuttonapp"
  }
}
```

