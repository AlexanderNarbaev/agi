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
    java -Xmx2G -Xms1G -jar paper.jar --nogui &
    for i in $(seq 1 30); do
        lsof -i:25565 &>/dev/null && break
        sleep 1
    done
fi
echo "  Server: localhost:25565"

echo "[2/2] Launching HMCL with authlib-injector..."
echo "  Auth bypass: active"
echo "  In HMCL: Add Offline Account → 1.20.4 → Launch → localhost"
echo "  /matrix start | /matrix status | /matrix train"
echo ""

cd ~/Projects/agi/minecraft-server
java \
    -javaagent:"$HOME/.local/share/matrix-auth/authlib-injector.jar"="http://127.0.0.1:25567/api/authlib-injector" \
    -Dauthlibinjector.mojangNamespace=disabled \
    -Dauthlibinjector.profileKey=disabled \
    -Dauthlibinjector.usernameCheck=disabled \
    -Dauthlibinjector.mojangAntiFeatures=disabled \
    -jar "$HOME/.local/bin/hmcl.jar"
