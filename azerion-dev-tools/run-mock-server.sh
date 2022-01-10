#!/usr/bin/env bash

TASK="dev"
if [ "$1" = "debug" ]; then
  TASK="debug"
fi

npm run $TASK
