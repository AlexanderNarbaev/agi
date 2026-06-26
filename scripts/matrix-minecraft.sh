#!/bin/bash
# MATRIX Minecraft — запуск сервера и клиента

echo "=== MATRIX Minecraft Launcher ==="
echo ""

# 1. Start Paper server if not running
if ! lsof -i:25565 &>/dev/null; then
    echo "[1/2] Starting Paper server..."
    cd ~/Projects/agi/minecraft-server
    java -Xmx2G -Xms1G -jar paper.jar --nogui &
    echo "  Waiting for server to start..."
    for i in $(seq 1 30); do
        if lsof -i:25565 &>/dev/null; then
            echo "  Server ready on localhost:25565"
            break
        fi
        sleep 1
    done
else
    echo "[1/2] Server already running on localhost:25565"
fi

echo "[2/2] Launching Prism Launcher..."
echo "  Instance: MATRIX 1.20.4"
echo "  Account: MatrixAgent (offline)"
echo "  Server: localhost"
echo ""
echo "  In Minecraft: Multiplayer → Direct Connect → localhost"
echo "  Commands: /matrix start | /matrix status | /matrix train"
echo ""

~/.local/bin/prismlauncher
