#!/bin/bash
# Direct Minecraft 1.20.4 launch with authlib-injector (no HMCL needed)

MC_DIR="$HOME/.minecraft"
VER="1.20.4"
NATIVE_DIR="$MC_DIR/versions/$VER/natives"
AUTH_JAR="$HOME/.local/share/matrix-auth/authlib-injector.jar"
AUTH_URL="http://127.0.0.1:25567/api/authlib-injector"
VER_JSON="$MC_DIR/versions/$VER/$VER.json"
CLIENT_JAR="$MC_DIR/versions/$VER/$VER.jar"

if [ ! -f "$CLIENT_JAR" ]; then
    echo "Client jar not found: $CLIENT_JAR"
    exit 1
fi

# Extract natives
mkdir -p "$NATIVE_DIR"
python3 -c "
import json, os, subprocess, glob
v = json.load(open(\"$VER_JSON\"))
native_jars = set()
for r in v.get('libraries', []):
    name = r['name']
    if 'lwjgl' not in name.lower():
        continue
    parts = name.split(':')
    if len(parts) < 3:
        continue
    group = parts[0].replace('.', '/')
    artifact = parts[1]
    ver = parts[2]
    # Find native jars with -natives-linux suffix
    lib_dir = os.path.expanduser(f'~/.minecraft/libraries/{group}/{artifact}/{ver}')
    if os.path.isdir(lib_dir):
        for f in os.listdir(lib_dir):
            if f.endswith('-natives-linux.jar'):
                native_jars.add(os.path.join(lib_dir, f))
for jar in sorted(native_jars):
    subprocess.run(['unzip', '-o', '-q', jar, '-d', \"$NATIVE_DIR\"], check=True)
" 2>/dev/null

# Build classpath
CP=$(python3 -c "
import json, os
v = json.load(open(\"$VER_JSON\"))
paths = []
for r in v.get('libraries', []):
    if 'rules' in r:
        skip = False
        for rule in r['rules']:
            if 'os' in rule:
                os_name = rule['os'].get('name', '')
                if os_name in ('osx', 'windows'):
                    skip = (rule['action'] == 'allow')
                if os_name == 'linux':
                    skip = (rule['action'] == 'disallow')
        if skip:
            continue
    name = r['name']
    parts = name.split(':')
    group = parts[0].replace('.', '/')
    artifact = parts[1]
    version = parts[2]
    path = os.path.expanduser(f'~/.minecraft/libraries/{group}/{artifact}/{version}/{artifact}-{version}.jar')
    if os.path.exists(path):
        paths.append(path)
print(':'.join(paths))
" 2>/dev/null)

# Get asset index
ASSET_INDEX=$(python3 -c "
import json
v = json.load(open(\"$VER_JSON\"))
print(v['assetIndex']['id'])
" 2>/dev/null)

echo "=== MATRIX Direct Launch ==="
echo "Starting Minecraft 1.20.4 with offline auth..."
echo "Classpath entries: $(echo $CP | tr ':' '\n' | wc -l)"
echo "Asset index: $ASSET_INDEX"

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
    -cp "${CP}:${CLIENT_JAR}" \
    net.minecraft.client.main.Main \
    --username MatrixAgent \
    --version "MATRIX 1.20.4" \
    --gameDir "$MC_DIR" \
    --assetsDir "$MC_DIR/assets" \
    --assetIndex "$ASSET_INDEX" \
    --uuid a10c708c08845a73b6afbdd7162236ae \
    --accessToken matrix-token-a10c708c \
    --clientId matrix \
    --xuid 0 \
    --userType mojang \
    --versionType "MATRIX"
