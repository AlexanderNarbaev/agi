#!/bin/bash
# Direct Minecraft 1.20.4 launch with authlib-injector (no HMCL needed)
set -e

MC_DIR="$HOME/.minecraft"
VER="1.20.4"
NATIVE_DIR="$MC_DIR/versions/$VER/natives"
AUTH_JAR="$HOME/.local/share/matrix-auth/authlib-injector.jar"
AUTH_URL="http://127.0.0.1:25567/api/authlib-injector"

# Build classpath from all libraries
CP=""
VER_JSON="$MC_DIR/versions/$VER/$VER.json"
for lib in $(python3 -c "
import json, os, sys
v = json.load(open('$VER_JSON'))
rules = v.get('libraries', [])
for r in rules:
    if 'rules' in r:
        # Check OS rules - skip macOS-specific on Linux
        skip = False
        for rule in r['rules']:
            if 'os' in rule:
                os_name = rule['os'].get('name', '')
                if os_name == 'osx' or os_name == 'windows':
                    skip = (rule['action'] == 'allow')
                if os_name == 'linux':
                    skip = (rule['action'] == 'disallow')
        if skip:
            continue
    name = r['name']
    # Convert maven coords to path
    parts = name.split(':')
    group = parts[0].replace('.', '/')
    artifact = parts[1]
    version = parts[2]
    path = os.path.expanduser(f'~/.minecraft/libraries/{group}/{artifact}/{version}/{artifact}-{version}.jar')
    if os.path.exists(path):
        print(path, end=':')
" 2>/dev/null); do :; done

# Add client jar
CLIENT_JAR="$MC_DIR/versions/$VER/$VER.jar"
if [ ! -f "$CLIENT_JAR" ]; then
    echo "Client jar not found: $CLIENT_JAR — run HMCL first to download it"
    exit 1
fi

# Extract natives
mkdir -p "$NATIVE_DIR"
python3 -c "
import json, os, subprocess, glob
v = json.load(open('$VER_JSON'))
for r in v.get('libraries', []):
    if 'natives' in r:
        name = r['name']
        parts = name.split(':')
        group = parts[0].replace('.', '/')
        artifact = parts[1]
        ver = parts[2]
        native_jar = os.path.expanduser(f'~/.minecraft/libraries/{group}/{artifact}/{ver}/{artifact}-{ver}-natives-linux.jar')
        if os.path.exists(native_jar) and not os.listdir('$NATIVE_DIR'):
            subprocess.run(['unzip', '-o', '-q', native_jar, '-d', '$NATIVE_DIR'], check=True)
" 2>/dev/null

echo "=== MATRIX Direct Launch ==="
echo "Starting Minecraft 1.20.4 with offline auth..."

cd "$MC_DIR"

java \
    -Xmx2G \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseG1GC \
    -XX:G1NewSizePercent=20 \
    -XX:G1ReservePercent=20 \
    -XX:MaxGCPauseMillis=50 \
    -XX:G1HeapRegionSize=32M \
    -Djava.library.path="$NATIVE_DIR" \
    -Dminecraft.launcher.brand=matrix \
    -Dminecraft.launcher.version=2.1.0 \
    -javaagent:"$AUTH_JAR"="$AUTH_URL" \
    -Dauthlibinjector.mojangNamespace=disabled \
    -Dauthlibinjector.profileKey=disabled \
    -Dauthlibinjector.usernameCheck=disabled \
    -Dauthlibinjector.mojangAntiFeatures=disabled \
    -cp "${CP}${CLIENT_JAR}" \
    net.minecraft.client.main.Main \
    --username MatrixAgent \
    --version "MATRIX 1.20.4" \
    --gameDir "$MC_DIR" \
    --assetsDir "$MC_DIR/assets" \
    --assetIndex "$(python3 -c "import json; print(json.load(open('$VER_JSON'))['assetIndex']['id'])")" \
    --uuid "a10c708c08845a73b6afbdd7162236ae" \
    --accessToken "matrix-token-a10c708c" \
    --clientId matrix \
    --xuid 0 \
    --userType mojang \
    --versionType "MATRIX"
