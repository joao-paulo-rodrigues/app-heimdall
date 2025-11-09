#!/bin/bash

# Script para compilar e instalar o Heimdall no emulador

echo "Building Heimdall Android app..."

cd "$(dirname "$0")"

# Limpar build anterior
./gradlew clean

# Compilar APK
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo "Installing on connected device/emulator..."
    ./gradlew installDebug
    
    if [ $? -eq 0 ]; then
        echo "Installation successful!"
        echo "Launching app..."
        adb shell am start -n com.heimdall.device/.MainActivity
        echo "App launched!"
        echo ""
        echo "To view logs:"
        echo "  adb logcat | grep Heimdall"
    else
        echo "Installation failed. Make sure a device/emulator is connected."
    fi
else
    echo "Build failed!"
    exit 1
fi


