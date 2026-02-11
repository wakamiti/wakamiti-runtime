#!/bin/bash

if [ -z "$TRG_DIR" ]; then
  echo "TRG_DIR is not set"
  exit 2
fi

LOG="$TRG_DIR/wakamitid.log"
PIDFILE="$TRG_DIR/wakamitid.pid"

rm -f "$LOG"
rm -f "$PIDFILE"

"$TRG_DIR/bin/wakamitid" > "$LOG" 2>&1 &
echo $! > "$PIDFILE"

while [ ! -f "$LOG" ]; do
  sleep 1
done

tail -f "$LOG" &
TAIL_PID=$!

TIMEOUT=30
COUNT=0

while [ $COUNT -lt $TIMEOUT ]; do
  if grep -q "Server started on" "$LOG"; then
    kill $TAIL_PID >/dev/null 2>&1
    wait $TAIL_PID 2>/dev/null
    exit 0
  fi
  sleep 1
  COUNT=$((COUNT+1))
done

kill $TAIL_PID >/dev/null 2>&1
echo "Timeout waiting for server start"
exit 1
