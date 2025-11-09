# Heimdall Android Device

Aplicação Android para gerenciamento remoto de dispositivos via MQTT.

## Requisitos

- Android Studio Hedgehog ou superior
- JDK 17
- Android SDK API 28+ (Android 9.0+)
- Gradle 8.2+

## Configuração

### 1. Configurar SDK do Android

Certifique-se de que o `local.properties` está configurado com o caminho do SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

### 2. Configuração MQTT

As credenciais MQTT estão configuradas em `app/src/main/java/com/heimdall/device/config/MqttConfig.kt`:

- Host: 177.87.122.5
- Porta: 1883
- Usuário: mosquitto_broker_user_ue
- Senha: tiue@Mosquitto2025#

## Compilação e Execução Rápida

### Script Automatizado (Recomendado)

O script `run.sh` faz tudo automaticamente: inicia o emulador, compila, instala e abre o app.

```bash
cd android
./run.sh
```

Ou especifique um AVD específico:

```bash
./run.sh Pixel_5_API_33
```

### Via Gradle (linha de comando)

```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```

### Via Android Studio

1. Abra o projeto em Android Studio
2. Aguarde a sincronização do Gradle
3. Selecione o dispositivo/emulador
4. Clique em Run (Shift+F10)

## Instalação Manual no Emulador

### 1. Criar um Emulador

```bash
# Listar imagens disponíveis
emulator -list-avds

# Iniciar emulador
emulator -avd <nome_do_avd>
```

### 2. Instalar APK

```bash
# Compilar e instalar
./gradlew installDebug

# Ou instalar APK já compilado
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Execução

Após a instalação, o app:
1. Inicia automaticamente ao abrir
2. Conecta ao servidor MQTT configurado
3. Publica status "online" no tópico de status
4. Subscreve ao tópico de comandos
5. Registra todos os eventos em logs estruturados (JSON)

## Logs

Os logs são armazenados em:
- **Logcat**: `adb logcat | grep Heimdall`
- **Arquivo local**: `/data/data/com.heimdall.device/files/heimdall_logs/heimdall.log`
- **MQTT**: Publicados no tópico `v1/heimdall/telemetry/logs`

### Visualizar logs locais

```bash
adb shell run-as com.heimdall.device cat files/heimdall_logs/heimdall.log
```

## Estrutura do Projeto

```
app/src/main/
├── java/com/heimdall/device/
│   ├── config/          # Configurações (MQTT, etc.)
│   ├── database/        # Room Database (logs, mensagens pendentes)
│   ├── receiver/        # BroadcastReceivers (boot, etc.)
│   ├── service/         # Serviços (MQTT, etc.)
│   └── util/            # Utilitários (Logger, etc.)
└── res/                 # Recursos Android
```

## Permissões

O app requer as seguintes permissões:
- `INTERNET`: Conexão MQTT
- `ACCESS_NETWORK_STATE`: Verificar conectividade
- `WAKE_LOCK`: Manter conexão ativa
- `RECEIVE_BOOT_COMPLETED`: Iniciar após boot
- `INSTALL_PACKAGES`: Instalar apps remotamente (requer Device Owner)
- `DELETE_PACKAGES`: Remover apps remotamente (requer Device Owner)

## Device Owner Mode

Para funcionalidades completas de MDM, o app precisa ser configurado como Device Owner. Isso requer:

1. Reset de fábrica do dispositivo
2. Configuração via ADB durante o setup inicial:

```bash
adb shell dpm set-device-owner com.heimdall.device/.receiver.DeviceAdminReceiver
```

**Nota**: Device Owner só pode ser configurado em dispositivos sem conta de usuário configurada.

## Troubleshooting

### Erro de conexão MQTT

- Verifique se o emulador tem acesso à internet
- Verifique as credenciais em `MqttConfig.kt`
- Verifique se o servidor MQTT está acessível

### Erro de compilação

- Limpe o projeto: `./gradlew clean`
- Sincronize o Gradle no Android Studio
- Verifique se todas as dependências estão resolvidas

### Logs não aparecem

- Verifique as permissões de armazenamento
- Verifique o Logcat para erros
- Verifique se o Logger foi inicializado corretamente

