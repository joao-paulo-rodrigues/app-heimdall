# Sistema de Comandos Robusto - Heimdall MDM

## Visão Geral

Sistema completo de comandos com ACK robusto, reprocessamento automático e store-and-forward para garantir entrega e processamento confiável de comandos remotos via MQTT.

## Arquitetura

### Componentes Principais

1. **Command** - Modelo de comando recebido
2. **CommandResult** - Resultado do processamento
3. **CommandAck** - Confirmação de recebimento/processamento
4. **CommandHandler** - Coordenador principal de processamento
5. **CommandAckManager** - Gerencia envio de ACKs
6. **CommandRetryManager** - Gerencia reprocessamento
7. **StoreAndForwardManager** - Armazena mensagens pendentes

## Fluxo de Processamento

```
1. Comando recebido via MQTT
   ↓
2. CommandHandler.processCommand()
   ↓
3. ACK RECEIVED enviado imediatamente
   ↓
4. Verificação de idempotência (comando já processado?)
   ↓
5. ACK PROCESSING enviado
   ↓
6. Processador de comando executado
   ↓
7. CommandResult gerado
   ↓
8. Resultado enviado via MQTT (ACK final)
   ↓
9. Se falhar: retry automático (até 3 tentativas)
   ↓
10. Se MQTT offline: armazenado para envio posterior
```

## Tipos de ACK

### RECEIVED
Enviado imediatamente após receber o comando, confirma que o dispositivo recebeu a mensagem.

```json
{
  "command_id": "cmd-123",
  "command": "ping",
  "ack_type": "received",
  "timestamp": 1234567890,
  "trace_id": "trace-abc"
}
```

### PROCESSING
Enviado antes de iniciar o processamento, indica que o comando está sendo executado.

```json
{
  "command_id": "cmd-123",
  "command": "ping",
  "ack_type": "processing",
  "message": "Command is being processed",
  "timestamp": 1234567890,
  "trace_id": "trace-abc"
}
```

### REJECTED
Enviado quando o comando é rejeitado (formato inválido, comando desconhecido, etc).

```json
{
  "command_id": "cmd-123",
  "command": "unknown_command",
  "ack_type": "rejected",
  "message": "Unknown command type",
  "timestamp": 1234567890,
  "trace_id": "trace-abc"
}
```

## Resultado Final

Após processamento, um resultado completo é enviado:

```json
{
  "command_id": "cmd-123",
  "command": "ping",
  "status": "success",
  "message": "Pong",
  "data": {
    "timestamp": 1234567890
  },
  "timestamp": 1234567891,
  "trace_id": "trace-abc"
}
```

## Store-and-Forward

### Quando Armazena

- Comandos recebidos quando MQTT está offline
- Resultados que falharam ao enviar
- ACKs que falharam ao enviar

### Localização

Arquivos JSON em:
- `/data/data/com.heimdall.device/files/heimdall_store/pending_commands.json`
- `/data/data/com.heimdall.device/files/heimdall_store/pending_results.json`

### Limite

Máximo de 1000 itens armazenados (FIFO - primeiro a entrar, primeiro a sair quando limite excedido).

## Reprocessamento (Retry)

### Configuração

- **Máximo de retries**: 3 tentativas
- **Delay inicial**: 5 segundos
- **Backoff exponencial**: 5s, 10s, 20s
- **Delay máximo**: 60 segundos

### Quando Reprocessa

- Exceção durante processamento
- Timeout de processamento
- Erro de validação recuperável

## Idempotência

Comandos são marcados como processados usando SharedPreferences. Comandos duplicados são ignorados automaticamente.

## Registro de Processadores

### Processadores Padrão

- `ping` - Comando de teste
- `get_device_status` - Retorna status do dispositivo

### Registrar Novo Processador

```kotlin
val mqttManager = MqttServiceManager.getInstance(context)

mqttManager.registerCommandProcessor("meu_comando") { command ->
    // Processar comando
    CommandResult(
        commandId = command.commandId,
        command = command.command,
        status = CommandResult.CommandStatus.SUCCESS,
        message = "Comando processado",
        data = mapOf("resultado" to "ok"),
        traceId = command.traceId
    )
}
```

## Formato de Comando

### Enviado via MQTT

```json
{
  "command_id": "cmd-unique-id",
  "command": "ping",
  "params": {
    "param1": "value1",
    "param2": 123
  },
  "timestamp": 1234567890,
  "trace_id": "trace-abc",
  "tenant_id": "ORG001"
}
```

### Campos Obrigatórios

- `command_id`: ID único do comando
- `command`: Tipo do comando

### Campos Opcionais

- `params`: Parâmetros do comando
- `timestamp`: Timestamp do comando
- `trace_id`: ID de rastreamento
- `tenant_id`: ID do tenant

## Tópicos MQTT

### Recebimento de Comandos
```
v1/heimdall/tenants/{tenant_id}/devices/{device_id}/cmd
```

### Envio de ACKs/Resultados
```
v1/heimdall/tenants/{tenant_id}/devices/{device_id}/ack
```

## Logs

Todos os eventos são logados usando o sistema de logging estruturado:

- Recebimento de comando
- Envio de ACK
- Início de processamento
- Resultado do processamento
- Erros e retries
- Store-and-forward

### Exemplo de Log

```json
{
  "timestamp": "2025-01-15T10:30:00.000Z",
  "component": "heimdall.device.command_handler",
  "level": "INFO",
  "device_id": "A1B2C3",
  "tenant_id": "ORG001",
  "trace_id": "trace-abc",
  "message": "Command processed successfully",
  "metadata": {
    "command_id": "cmd-123",
    "status": "SUCCESS"
  }
}
```

## Exemplo de Uso Completo

### 1. Enviar Comando (Backend)

```python
import paho.mqtt.client as mqtt

command = {
    "command_id": "cmd-123",
    "command": "ping",
    "trace_id": "trace-abc",
    "tenant_id": "ORG001"
}

client.publish(
    "v1/heimdall/tenants/ORG001/devices/A1B2C3/cmd",
    json.dumps(command),
    qos=1
)
```

### 2. Receber ACKs (Backend)

```python
def on_message(client, userdata, msg):
    ack = json.loads(msg.payload)
    print(f"ACK recebido: {ack['ack_type']} para comando {ack['command_id']}")
    
    if ack['ack_type'] == 'received':
        print("Comando recebido pelo dispositivo")
    elif ack['ack_type'] == 'processing':
        print("Comando em processamento")
    elif 'status' in ack:  # Resultado final
        print(f"Resultado: {ack['status']}")

client.subscribe("v1/heimdall/tenants/ORG001/devices/A1B2C3/ack")
client.on_message = on_message
```

## Boas Práticas

1. **Sempre use command_id único**: Evita processamento duplicado
2. **Use trace_id**: Facilita rastreamento end-to-end
3. **Valide parâmetros**: Retorne REJECTED se parâmetros inválidos
4. **Logs estruturados**: Use metadata para informações adicionais
5. **Timeout adequado**: Comandos longos devem ter timeout configurável
6. **Idempotência**: Comandos devem ser idempotentes quando possível

## Troubleshooting

### Comando não recebe ACK

1. Verificar se MQTT está conectado
2. Verificar logs do dispositivo
3. Verificar tópico correto
4. Verificar formato JSON do comando

### Comando processado mas resultado não chega

1. Verificar store-and-forward (arquivos pendentes)
2. Verificar logs de erro
3. Verificar reconexão MQTT
4. Verificar retry automático

### Comando duplicado

1. Verificar idempotência (comando já processado)
2. Verificar command_id único
3. Limpar histórico se necessário


