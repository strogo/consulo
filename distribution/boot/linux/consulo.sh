#!/bin/sh

ROOT_DIR="$(dirname "$0")"

while true ; do
  BUILD_DIR=`ls -1v platform | tail -n 1`

  CONSULO_HOME="$ROOT_DIR/platform/$BUILD_DIR"

  export CONSULO_HOME

  export ROOT_DIR

  . "$CONSULO_HOME/bin/launcher.sh"

  echo "->CONSULO_HOME="$CONSULO_HOME
  echo "->MAIN_CLASS_NAME="$MAIN_CLASS_NAME
  echo "->ALL_JVM_ARGS="$ALL_JVM_ARGS

  eval "$JDK/bin/java" $ALL_JVM_ARGS -Djb.restart.code=88 $MAIN_CLASS_NAME "$@"

  test $? -ne 88 && break
done