#!/bin/bash

# Script para compilar, instalar e abrir o Heimdall no emulador
# Uso: ./run.sh [nome_do_avd]

set -e

# Cores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Heimdall Android - Build & Run ===${NC}"
echo ""

# Diretório do script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Verificar se ADB está disponível
if ! command -v adb &> /dev/null; then
    echo -e "${RED}Erro: ADB não encontrado. Certifique-se de que o Android SDK está no PATH.${NC}"
    exit 1
fi

# Verificar se emulator está disponível
if ! command -v emulator &> /dev/null; then
    echo -e "${YELLOW}AVISO: comando 'emulator' não encontrado. Tentando usar $ANDROID_HOME/emulator/emulator${NC}"
    if [ -z "$ANDROID_HOME" ]; then
        echo -e "${RED}Erro: ANDROID_HOME não está definido.${NC}"
        exit 1
    fi
    EMULATOR_CMD="$ANDROID_HOME/emulator/emulator"
else
    EMULATOR_CMD="emulator"
fi

# Função para verificar se o emulador está rodando
check_emulator_running() {
    adb devices | grep -q "emulator.*device$"
}

# Função para aguardar o emulador ficar pronto
wait_for_emulator() {
    echo -e "${YELLOW}Aguardando o emulador ficar pronto...${NC}"
    local max_attempts=60
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if check_emulator_running; then
            # Aguardar o boot completo
            echo -e "${YELLOW}Aguardando boot completo...${NC}"
            adb wait-for-device
            sleep 5
            
            # Verificar se o sistema está pronto
            local boot_completed=$(adb shell getprop sys.boot_completed | tr -d '\r')
            if [ "$boot_completed" = "1" ]; then
                echo -e "${GREEN}Emulador pronto!${NC}"
                return 0
            fi
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    
    echo -e "${RED}Timeout: Emulador não ficou pronto a tempo.${NC}"
    return 1
}

# Determinar qual AVD usar
if [ -z "$1" ]; then
    echo "AVDs disponíveis:"
    $EMULATOR_CMD -list-avds
    echo ""
    
    # Tentar pegar o primeiro AVD disponível
    FIRST_AVD=$($EMULATOR_CMD -list-avds | head -n1)
    
    if [ -z "$FIRST_AVD" ]; then
        echo -e "${RED}Erro: Nenhum AVD encontrado. Crie um emulador no Android Studio primeiro.${NC}"
        exit 1
    fi
    
    echo -e "${YELLOW}Usando AVD: $FIRST_AVD${NC}"
    AVD_NAME="$FIRST_AVD"
else
    AVD_NAME="$1"
fi

# Verificar se há dispositivo conectado
if check_emulator_running; then
    echo -e "${YELLOW}Emulador já está rodando.${NC}"
    echo -e "${YELLOW}Matando emulador para fazer wipe data...${NC}"
    adb emu kill
    sleep 3
fi

# Iniciar emulador com wipe data
echo -e "${YELLOW}Iniciando emulador com wipe data: $AVD_NAME${NC}"
echo -e "${YELLOW}Isso vai limpar todos os dados do emulador...${NC}"
$EMULATOR_CMD -avd "$AVD_NAME" -wipe-data &
EMULATOR_PID=$!

# Aguardar o emulador ficar pronto
if ! wait_for_emulator; then
    echo -e "${RED}Falha ao iniciar emulador.${NC}"
    kill $EMULATOR_PID 2>/dev/null || true
    exit 1
fi

echo ""
echo -e "${GREEN}=== Compilando projeto ===${NC}"

# Verificar se gradlew existe e tem permissão de execução
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}Erro: gradlew não encontrado!${NC}"
    echo "Criando Gradle wrapper..."
    # Tentar criar wrapper se possível
    if command -v gradle &> /dev/null; then
        gradle wrapper --gradle-version 8.2
    else
        echo -e "${RED}Gradle não está instalado. Por favor, instale o Gradle ou use o Android Studio.${NC}"
        exit 1
    fi
fi

if [ ! -x "./gradlew" ]; then
    chmod +x ./gradlew
fi

# Limpar build anterior
echo "Limpando build anterior..."
./gradlew clean > /dev/null 2>&1 || true

# Compilar APK
echo "Compilando APK..."
if ./gradlew assembleDebug; then
    echo -e "${GREEN}Build concluído com sucesso!${NC}"
else
    echo -e "${RED}Erro na compilação!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}=== Instalando no emulador ===${NC}"

# Desinstalar versão anterior (se existir)
echo "Removendo instalação anterior (se existir)..."
adb uninstall com.heimdall.device > /dev/null 2>&1 || true

# Instalar APK
echo "Instalando APK..."
if ./gradlew installDebug; then
    echo -e "${GREEN}Instalação concluída!${NC}"
else
    echo -e "${RED}Erro na instalação!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}=== Configurando Device Owner ===${NC}"

# Aguardar um pouco para garantir que a instalação foi concluída
sleep 3

# Configurar como Device Owner
echo "Configurando Heimdall como Device Owner..."
DEVICE_OWNER_CMD="adb shell dpm set-device-owner com.heimdall.device/.receiver.HeimdallDeviceAdminReceiver"

if eval "$DEVICE_OWNER_CMD"; then
    echo -e "${GREEN}Device Owner configurado com sucesso!${NC}"
    
    # Verificar se foi configurado corretamente
    sleep 2
    if adb shell dpm list-owners | grep -q "com.heimdall.device"; then
        echo -e "${GREEN}Verificação: Heimdall é Device Owner${NC}"
    else
        echo -e "${YELLOW}AVISO: Não foi possível verificar Device Owner status${NC}"
    fi
else
    echo -e "${RED}Erro ao configurar Device Owner!${NC}"
    echo -e "${YELLOW}Possíveis causas:${NC}"
    echo "  - O dispositivo já tem uma conta de usuário configurada"
    echo "  - Já existe um Device Owner configurado"
    echo "  - O dispositivo não está em estado de fábrica"
    echo ""
    echo "Certifique-se de que o wipe data foi executado corretamente."
fi

echo ""
echo -e "${GREEN}=== Iniciando aplicação ===${NC}"

# Aguardar um pouco antes de iniciar
sleep 2

# Iniciar a aplicação
echo "Abrindo Heimdall..."
adb shell am start -n com.heimdall.device/.MainActivity

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Aplicação iniciada!${NC}"
else
    echo -e "${YELLOW}AVISO: Não foi possível iniciar a aplicação automaticamente.${NC}"
    echo "Tente abrir manualmente no emulador."
fi

echo ""
echo -e "${GREEN}=== Concluído! ===${NC}"
echo ""
echo "Para ver os logs em tempo real, execute:"
echo "  adb logcat | grep Heimdall"
echo ""
echo "Para ver logs do arquivo local:"
echo "  adb shell run-as com.heimdall.device cat files/heimdall_logs/heimdall.log"
echo ""

