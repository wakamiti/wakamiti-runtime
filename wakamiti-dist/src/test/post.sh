#!/bin/bash

if [ -z "$TRG_DIR" ]; then
  echo "TRG_DIR is not set"
  exit 2
fi

PIDFILE="$TRG_DIR/wakamitid.pid"

if [ -f "$PIDFILE" ]; then
  PID=$(cat "$PIDFILE")
  echo "killing task PID: $PID"
  kill -9 "$PID" >/dev/null 2>&1
  echo "server stopped"
fi
