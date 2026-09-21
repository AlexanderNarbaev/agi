#!/bin/bash
echo "=== MATRIX Minecraft ==="

# Start auth mock
kill $(lsof -ti:25567) 2>/dev/null
python3 ~/.local/share/matrix-auth/yggdrasil-mock.py 25567 &
sleep 1

# Start Paper server
if ! lsof -i:25565 &>/dev/null; then
    echo "[1/2] Starting Paper server..."
    cd ~/Projects/agi/minecraft-server
    java -Xmx2G -Xms1G -jar paper.jar --nogui > /dev/null 2>&1 &
    for i in $(seq 1 30); do
        lsof -i:25565 &>/dev/null && break
        sleep 1
    done
    echo "  Server: localhost:25565"
else
    echo "[1/2] Server already running"
fi

echo "[2/2] Launching Minecraft (direct mode)..."

# Check if client jar exists
if [ ! -f ~/.minecraft/versions/1.20.4/1.20.4.jar ]; then
    echo ""
    echo "Minecraft 1.20.4 not downloaded. Opening HMCL to download..."
    echo "In HMCL: install 1.20.4, then close HMCL and re-run matrix-minecraft"
    java -jar ~/.local/bin/hmcl.jar
    exit 0
fi

~/.local/bin/mc-direct
