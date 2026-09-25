#!/usr/bin/env bash
# Stop the MATRIX mind gateway.
if [ -f .gateway.pid ]; then
    PID=$(cat .gateway.pid)
    echo "Stopping gateway pid=$PID..."
    kill $PID 2>/dev/null || true
    rm .gateway.pid
    echo "stopped."
else
    echo "no .gateway.pid found; nothing to stop"
fi
