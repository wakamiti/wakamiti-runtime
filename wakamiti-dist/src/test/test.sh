#!/bin/bash

if [ -z "$TRG_DIR" ]; then
  echo "TRG_DIR is not set"
  exit 2
fi

if [ -z "$1" ]; then
    echo "Directory not provided"
    exit 1
fi

cd "$1" || { echo "Failed to cd into $1"; exit 1; }

TESTS=("version" "help")
max_errorlevel=0

for n in "${TESTS[@]}"; do
  echo -n "--- $n: "
  "$TRG_DIR/bin/waka" $n > $TRG_DIR/$n.log 2>&1
  errorlevel=$?
  if [ $errorlevel -eq 0 ]; then
    echo "SUCCESS"
  else
    echo "ERROR"
    cat $TRG_DIR/$n.log
  fi
  if [ $errorlevel -gt $max_errorlevel ]; then
    max_errorlevel=$errorlevel
  fi
done

if [ $max_errorlevel -eq 0 ]; then
    echo "Result: SUCCESS"
else
    echo "Result: ERROR"
    echo - cat $TRG_DIR/wakamitid.log
    cat $TRG_DIR/wakamitid.log
    echo - ls $TRG_DIR/lib
    ls $TRG_DIR/lib
fi
exit $max_errorlevel
